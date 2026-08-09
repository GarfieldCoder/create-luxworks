package io.github.garfieldcoder.luxworks.compat.veil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal Phase 0 Veil volume. This is intentionally not the final beam
 * renderer: it has no shadows, depth clipping, surface light, or atmosphere.
 */
public final class VeilDebugBeamRenderer {
    private static final ResourceLocation RENDER_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "debug_beam");
    private static final int SEGMENTS = 16;
    public static final int VERTEX_COUNT = SEGMENTS * 3;
    private static final double START_OFFSET = 0.52;
    private static final double RANGE = 6.0;
    private static final double END_RADIUS = 1.7;

    private VeilDebugBeamRenderer() {
    }

    public static boolean render(PoseStack poseStack, LightTransform transform, Vec3 cameraPosition) {
        RenderType renderType;
        try {
            renderType = VeilRenderType.get(RENDER_TYPE_ID);
        } catch (RuntimeException exception) {
            Luxworks.LOGGER.warn("Veil debug beam render type is unavailable", exception);
            return false;
        }
        if (renderType == null) {
            return false;
        }

        Vec3 forward = transform.forward();
        Vec3 start = transform.worldPosition().add(forward.scale(START_OFFSET)).subtract(cameraPosition);
        Vec3 endCenter = start.add(forward.scale(RANGE));
        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angleA = Math.PI * 2.0 * segment / SEGMENTS;
            double angleB = Math.PI * 2.0 * (segment + 1) / SEGMENTS;
            Vec3 edgeA = ringPoint(endCenter, right, up, angleA);
            Vec3 edgeB = ringPoint(endCenter, right, up, angleB);

            addVertex(vertices, pose, start, 120);
            addVertex(vertices, pose, edgeA, 5);
            addVertex(vertices, pose, edgeB, 5);
        }

        buffers.endBatch(renderType);
        return true;
    }

    private static Vec3 ringPoint(Vec3 center, Vec3 right, Vec3 up, double angle) {
        return center
                .add(right.scale(Math.cos(angle) * END_RADIUS))
                .add(up.scale(Math.sin(angle) * END_RADIUS));
    }

    private static void addVertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 position, int alpha) {
        vertices.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(255, 222, 120, alpha);
    }
}
