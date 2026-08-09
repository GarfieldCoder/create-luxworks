package io.github.garfieldcoder.luxworks.compat.veil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.light.LightState;
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
    public static final int VERTEX_COUNT = SEGMENTS * 6;
    private static final double START_OFFSET = 0.52;
    private static final double MAX_PROTOTYPE_RANGE = 64.0;

    private VeilDebugBeamRenderer() {
    }

    public static boolean render(
            PoseStack poseStack,
            LightTransform transform,
            LightState lightState,
            Vec3 cameraPosition
    ) {
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
        double range = Math.min(lightState.range(), MAX_PROTOTYPE_RANGE);
        double endRadius = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5)) * range;
        Vec3 start = transform.worldPosition().add(forward.scale(START_OFFSET)).subtract(cameraPosition);
        Vec3 endCenter = start.add(forward.scale(range));
        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angleA = Math.PI * 2.0 * segment / SEGMENTS;
            double angleB = Math.PI * 2.0 * (segment + 1) / SEGMENTS;
            Vec3 edgeA = ringPoint(endCenter, right, up, angleA, endRadius);
            Vec3 edgeB = ringPoint(endCenter, right, up, angleB, endRadius);

            addVertex(vertices, pose, start, lightState, 120);
            addVertex(vertices, pose, edgeA, lightState, 5);
            addVertex(vertices, pose, edgeB, lightState, 5);
            addVertex(vertices, pose, start, lightState, 120);
            addVertex(vertices, pose, edgeB, lightState, 5);
            addVertex(vertices, pose, edgeA, lightState, 5);
        }

        buffers.endBatch(renderType);
        return true;
    }

    /** Renders a +Z-facing cone inside an already-positioned fixture pose. */
    public static boolean renderLocal(
            PoseStack poseStack,
            MultiBufferSource buffers,
            LightState lightState
    ) {
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

        double range = Math.min(lightState.range(), MAX_PROTOTYPE_RANGE);
        double endRadius = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5)) * range;
        Vec3 start = new Vec3(0.0, 0.0, 0.40);
        Vec3 endCenter = new Vec3(0.0, 0.0, 0.40 + range);
        Vec3 right = new Vec3(1.0, 0.0, 0.0);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angleA = Math.PI * 2.0 * segment / SEGMENTS;
            double angleB = Math.PI * 2.0 * (segment + 1) / SEGMENTS;
            addVertex(vertices, pose, start, lightState, 120);
            addVertex(vertices, pose, ringPoint(endCenter, right, up, angleA, endRadius), lightState, 5);
            addVertex(vertices, pose, ringPoint(endCenter, right, up, angleB, endRadius), lightState, 5);
            addVertex(vertices, pose, start, lightState, 120);
            addVertex(vertices, pose, ringPoint(endCenter, right, up, angleB, endRadius), lightState, 5);
            addVertex(vertices, pose, ringPoint(endCenter, right, up, angleA, endRadius), lightState, 5);
        }
        return true;
    }

    private static Vec3 ringPoint(Vec3 center, Vec3 right, Vec3 up, double angle, double radius) {
        return center
                .add(right.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius));
    }

    private static void addVertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 position,
            LightState lightState,
            int baseAlpha
    ) {
        int red = Math.round(lightState.red() * 255.0F);
        int green = Math.round(lightState.green() * 255.0F);
        int blue = Math.round(lightState.blue() * 255.0F);
        int alpha = Math.clamp(Math.round(baseAlpha * lightState.intensity()), 0, 255);
        vertices.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }
}
