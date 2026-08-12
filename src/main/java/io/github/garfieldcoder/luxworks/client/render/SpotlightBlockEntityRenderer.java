package io.github.garfieldcoder.luxworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.content.item.DebugTargetingStickItem;
import io.github.garfieldcoder.luxworks.compat.veil.VeilDebugBeamRenderer;
import io.github.garfieldcoder.luxworks.compat.veil.DepthAwareSpotlightRenderer;
import io.github.garfieldcoder.luxworks.compat.create.CreateCompat;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTransformResolver;
import io.github.garfieldcoder.luxworks.compat.veil.VeilAreaLightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlockEntities;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import java.util.WeakHashMap;

/**
 * Temporary articulated fixture built from ordinary block-model cuboids.
 *
 * <p>The yoke consumes yaw while the lamp consumes yaw and pitch. Both use
 * the same interpolated servo state as the beam renderer, keeping the visible
 * mechanism and optical direction together while the final art is absent.</p>
 */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class SpotlightBlockEntityRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final WeakHashMap<SpotlightBlockEntity, CachedOcclusion> occlusionCache = new WeakHashMap<>();

    public SpotlightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LuxworksBlockEntities.SPOTLIGHT.get(), SpotlightBlockEntityRenderer::new);
    }

    @Override
    public boolean shouldRenderOffScreen(SpotlightBlockEntity spotlight) {
        // The bounded fixture can leave the camera frustum while its long beam
        // still crosses the view. A dedicated light manager will replace this
        // broad prototype policy with cone/frustum culling and light budgets.
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(SpotlightBlockEntity spotlight) {
        // NeoForge culls block entities by this box before the vanilla
        // shouldRenderOffScreen hook. The prototype has no central light
        // manager yet, so keep loaded fixtures eligible while testing beams
        // that can cross the camera view from an off-screen emitter.
        return AABB.INFINITE;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void render(
            SpotlightBlockEntity spotlight,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        long startedAt = System.nanoTime();
        Direction facing = spotlight.getBlockState().getValue(DebugLightBlock.FACING);
        var servo = spotlight.getInterpolatedServoState(partialTick);
        var lightState = spotlight.getLightState();
        boolean depthDiagnostic = isDepthDiagnosticSelected(spotlight);
        LightTransform resolvedTransform = null;
        if (spotlight.getLevel() instanceof ClientLevel clientLevel
                && !CreateCompat.isVirtualContraptionLevel(clientLevel)
                && lightState.enabled()
                && lightState.intensity() > 0.0F) {
            var localForward = io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver.resolve(facing, servo);
            resolvedTransform = SableLightTransformResolver.resolve(
                    clientLevel,
                    spotlight.getBlockPos(),
                    localForward,
                    partialTick
            );
            // The depth-diagnostic beam now samples SpotlightShadowMap's real
            // light-space depth render instead of Veil's voxel occlusion
            // grid, so no camera-anchored keep-alive light is needed here
            // anymore; both modes go through the same experimental,
            // opt-in surface-light update.
            VeilAreaLightManager.update(
                    resolvedTransform, lightState, spotlight.getLevel().getGameTime()
            );
        } else {
            VeilAreaLightManager.remove(lightState.id());
        }
        BeamOcclusionProfile occlusion = depthDiagnostic
                ? null
                : resolveOcclusion(spotlight, servo, lightState.range(), lightState.outerAngleDegrees());

        poseStack.pushPose();
        poseStack.translate(0.5, 0.25, 0.5);
        poseStack.mulPose(Axis.YP.rotation(yawForFacing(facing)));
        poseStack.mulPose(Axis.YP.rotationDegrees(-servo.currentYaw()));

        // Rotating pedestal and U-shaped yaw yoke.
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.0F, 0.02F, 0.0F, 0.42F, 0.10F, 0.42F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                -0.27F, 0.34F, 0.0F, 0.10F, 0.62F, 0.18F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.27F, 0.34F, 0.0F, 0.10F, 0.62F, 0.18F, packedLight);

        // The lamp pitches around the axle passing through the yoke arms.
        poseStack.translate(0.0, 0.34, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-servo.currentPitch()));
        renderCuboid(poseStack, buffers, Blocks.IRON_BLOCK.defaultBlockState(),
                0.0F, 0.0F, 0.03F, 0.42F, 0.42F, 0.58F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.0F, 0.0F, -0.28F, 0.46F, 0.46F, 0.08F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.SEA_LANTERN.defaultBlockState(),
                0.0F, 0.0F, 0.34F, 0.34F, 0.34F, 0.06F, LightTexture.FULL_BRIGHT);
        boolean beamEligible = !CreateCompat.isVirtualContraptionLevel(spotlight.getLevel())
                && lightState.enabled()
                && lightState.intensity() > 0.0F
                && lightState.range() > 0.0F;
        boolean beamRendered;
        if (beamEligible && depthDiagnostic && resolvedTransform != null) {
            DepthAwareSpotlightRenderer.enqueue(resolvedTransform, lightState);
            beamRendered = true;
        } else {
            beamRendered = beamEligible
                    && VeilDebugBeamRenderer.renderLocal(poseStack, buffers, lightState, occlusion);
        }
        if (beamRendered && !depthDiagnostic) {
            VeilDebugBeamRenderer.renderLocalSurface(poseStack, buffers, lightState, occlusion);
        }
        poseStack.popPose();

        LightRenderMetrics.record(
                System.nanoTime() - startedAt,
                beamRendered ? 1 : 0,
                beamRendered
                        ? depthDiagnostic
                        ? VeilDebugBeamRenderer.DEPTH_DIAGNOSTIC_VERTEX_COUNT
                        : VeilDebugBeamRenderer.VERTEX_COUNT
                        : 0
        );
    }

    private boolean isDepthDiagnosticSelected(SpotlightBlockEntity spotlight) {
        // Persistent per-stick toggle (shift + right-click in air) rather
        // than a held key: holding shift blocked screenshots and was easy
        // to release accidentally while inspecting the beam.
        if (minecraft.player == null) {
            return false;
        }
        return isDiagnosticStick(minecraft.player.getMainHandItem(), spotlight)
                || isDiagnosticStick(minecraft.player.getOffhandItem(), spotlight);
    }

    private static boolean isDiagnosticStick(ItemStack stack, SpotlightBlockEntity spotlight) {
        return DebugTargetingStickItem.isBoundTo(stack, spotlight.getBlockPos())
                && DebugTargetingStickItem.isDepthDiagnosticEnabled(stack);
    }

    private void renderCuboid(
            PoseStack poseStack,
            MultiBufferSource buffers,
            BlockState material,
            float centerX,
            float centerY,
            float centerZ,
            float sizeX,
            float sizeY,
            float sizeZ,
            int packedLight
    ) {
        poseStack.pushPose();
        poseStack.translate(centerX - sizeX * 0.5F, centerY - sizeY * 0.5F, centerZ - sizeZ * 0.5F);
        poseStack.scale(sizeX, sizeY, sizeZ);
        minecraft.getBlockRenderer().renderSingleBlock(
                material,
                poseStack,
                buffers,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static float yawForFacing(Direction facing) {
        return (float) Math.atan2(facing.getStepX(), facing.getStepZ());
    }

    private BeamOcclusionProfile resolveOcclusion(
            SpotlightBlockEntity spotlight,
            io.github.garfieldcoder.luxworks.servo.ServoState servo,
            double range,
            double outerAngleDegrees
    ) {
        long gameTime = spotlight.getLevel() == null ? 0L : spotlight.getLevel().getGameTime();
        CachedOcclusion cached = occlusionCache.get(spotlight);
        if (cached == null || gameTime - cached.sampledAtTick >= 5L) {
            cached = new CachedOcclusion(
                    gameTime,
                    BeamOcclusionSampler.sample(spotlight, servo, Math.min(range, 64.0), outerAngleDegrees)
            );
            occlusionCache.put(spotlight, cached);
        }
        return cached.profile;
    }

    private record CachedOcclusion(long sampledAtTick, BeamOcclusionProfile profile) {
    }
}
