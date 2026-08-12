package io.github.garfieldcoder.luxworks.compat.veil;

import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;

/**
 * Experimental production-path spotlight pass.
 *
 * <p>The opaque-world depth is copied before any spotlight samples it, so
 * the shader never reads from a depth texture that is simultaneously attached
 * to its draw framebuffer. This pass runs immediately after block entities,
 * while the world render target and camera state are still active. The cone
 * geometry stays analytic and unchanged; visibility is determined in the
 * fragment shader.</p>
 */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class DepthAwareSpotlightRenderer {
    private static final ResourceLocation DEPTH_COPY_ID =
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "spotlight_scene_depth");
    private static final List<Request> QUEUED = new ArrayList<>();
    private static boolean warned;
    private static boolean enqueueTraced;
    private static boolean passTraced;

    private DepthAwareSpotlightRenderer() {
    }

    public static void enqueue(LightTransform transform, LightState lightState) {
        // A Veil perspective render (e.g. our own shadow-map pass, see
        // SpotlightShadowMap) re-fires block entity rendering from the
        // light's own camera. Without this guard, that recursive pass would
        // mutate QUEUED while the AFTER_BLOCK_ENTITIES handler below is
        // still iterating it, risking a ConcurrentModificationException.
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            return;
        }
        QUEUED.add(new Request(transform, lightState));
        if (!enqueueTraced) {
            enqueueTraced = true;
            Luxworks.LOGGER.info(
                    "Depth-volume trace: request queued, range={}, inner={}, outer={}, intensity={}",
                    lightState.range(), lightState.innerAngleDegrees(),
                    lightState.outerAngleDegrees(), lightState.intensity()
            );
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // This stage re-fires from the light's own camera while our shadow
        // map renders (see SpotlightShadowMap); skip that recursive firing
        // entirely so it neither reprocesses QUEUED nor fights over the main
        // framebuffer, which is temporarily wrapped by the perspective pass.
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            return;
        }
        if (QUEUED.isEmpty()) {
            return;
        }

        try {
            AdvancedFbo main = AdvancedFbo.getMainFramebuffer();
            AdvancedFbo sceneDepth = ensureDepthCopy(main);
            main.resolveToAdvancedFbo(sceneDepth, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
            main.bind(false);

            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            var deltaTracker = event.getPartialTick();
            int rendered = 0;
            for (Request request : QUEUED) {
                if (VeilDebugBeamRenderer.renderWorldSceneDepthVolume(
                        buffers,
                        request.transform,
                        request.lightState,
                        deltaTracker
                )) {
                    rendered++;
                }
            }
            if (!passTraced) {
                passTraced = true;
                Luxworks.LOGGER.info(
                        "Depth-volume trace: AFTER_BLOCK_ENTITIES consumed {} request(s), submitted {}, main={}x{}, depthCopy={}x{}",
                        QUEUED.size(), rendered, main.getWidth(), main.getHeight(),
                        sceneDepth.getWidth(), sceneDepth.getHeight()
                );
                VeilRenderSystem.printGlErrors("Luxworks depth-volume first draw");
            }
        } catch (RuntimeException exception) {
            if (!warned) {
                warned = true;
                Luxworks.LOGGER.warn("Depth-aware spotlight pass is unavailable", exception);
            }
        } finally {
            QUEUED.clear();
        }
    }

    private static AdvancedFbo ensureDepthCopy(AdvancedFbo main) {
        FramebufferManager framebuffers = VeilRenderSystem.renderer().getFramebufferManager();
        AdvancedFbo existing = framebuffers.getFramebuffer(DEPTH_COPY_ID);
        if (existing != null
                && existing.getWidth() == main.getWidth()
                && existing.getHeight() == main.getHeight()
                && existing.isDepthTextureAttachment()) {
            return existing;
        }
        if (existing != null) {
            framebuffers.removeFramebuffer(DEPTH_COPY_ID);
            existing.free();
        }

        AdvancedFbo created = AdvancedFbo.withSize(main.getWidth(), main.getHeight())
                .addColorRenderBuffer()
                .setDepthTextureBuffer()
                .setDebugLabel("Luxworks Spotlight Scene Depth")
                .build(true);
        framebuffers.setFramebuffer(DEPTH_COPY_ID, created);
        return created;
    }

    private record Request(LightTransform transform, LightState lightState) {
    }
}
