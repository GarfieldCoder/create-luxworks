package io.github.garfieldcoder.luxworks.compat.veil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.AngularShadowMask;
import io.github.garfieldcoder.luxworks.client.render.BeamOcclusionProfile;
import io.github.garfieldcoder.luxworks.client.render.BeamOcclusionSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 1 Veil volume driven by a coarse CPU angular occlusion mask. This is
 * intentionally not the final beam renderer: it has no receiver-aware surface
 * lighting, scene-depth integration, filtered penumbra, or atmosphere.
 */
public final class VeilDebugBeamRenderer {
    private static final ResourceLocation RENDER_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "debug_beam");
    private static final ResourceLocation SURFACE_RENDER_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "surface_contact");
    private static final int SEGMENTS = BeamOcclusionSampler.SEGMENTS;
    public static final int VERTEX_COUNT = SEGMENTS * 3
            + (BeamOcclusionSampler.RING_FRACTIONS.length - 1) * SEGMENTS * 6;
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
        Vec3 start = transform.worldPosition()
                .add(forward.scale(BeamOcclusionSampler.START_OFFSET))
                .subtract(cameraPosition);
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
        }

        buffers.endBatch(renderType);
        return true;
    }

    /** Renders a +Z-facing cone inside an already-positioned fixture pose. */
    public static boolean renderLocal(
            PoseStack poseStack,
            MultiBufferSource buffers,
            LightState lightState,
            BeamOcclusionProfile occlusion
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
        AngularShadowMask shadowMask = occlusion.toShadowMask(range);
        Vec3 start = new Vec3(0.0, 0.0, BeamOcclusionSampler.START_OFFSET);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        double coneSlope = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5));

        int outerRing = BeamOcclusionSampler.RING_FRACTIONS.length - 1;
        double outerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[outerRing];
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int next = (segment + 1) % SEGMENTS;
            double distanceA = shadowMask.value(outerRing, segment) * range;
            double distanceB = shadowMask.value(outerRing, next) * range;
            Vec3 edgeA = clippedLocalEndpoint(
                    start, outerSlope, segment, distanceA
            );
            Vec3 edgeB = clippedLocalEndpoint(
                    start, outerSlope, next, distanceB
            );
            addVertex(vertices, pose, start, lightState, 55);
            addVertex(vertices, pose, edgeA, lightState, 3);
            addVertex(vertices, pose, edgeB, lightState, 3);
        }
        for (int ring = 1; ring < BeamOcclusionSampler.RING_FRACTIONS.length; ring++) {
            double innerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring - 1];
            double radialSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring];
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                double innerADistance = shadowMask.value(ring - 1, segment) * range;
                double innerBDistance = shadowMask.value(ring - 1, next) * range;
                double outerADistance = shadowMask.value(ring, segment) * range;
                double outerBDistance = shadowMask.value(ring, next) * range;
                Vec3 innerA = clippedLocalEndpoint(
                        start, innerSlope, segment, innerADistance
                );
                Vec3 innerB = clippedLocalEndpoint(
                        start, innerSlope, next, innerBDistance
                );
                Vec3 outerA = clippedLocalEndpoint(
                        start, radialSlope, segment, outerADistance
                );
                Vec3 outerB = clippedLocalEndpoint(
                        start, radialSlope, next, outerBDistance
                );
                int innerAlpha = 3;
                int outerAlpha = 3;
                if (coherentVolumeTriangle(range, innerADistance, outerADistance, outerBDistance)) {
                    addVertex(vertices, pose, innerA, lightState, innerAlpha);
                    addVertex(vertices, pose, outerA, lightState, outerAlpha);
                    addVertex(vertices, pose, outerB, lightState, outerAlpha);
                }
                if (coherentVolumeTriangle(range, innerADistance, outerBDistance, innerBDistance)) {
                    addVertex(vertices, pose, innerA, lightState, innerAlpha);
                    addVertex(vertices, pose, outerB, lightState, outerAlpha);
                    addVertex(vertices, pose, innerB, lightState, innerAlpha);
                }
            }
        }
        return true;
    }

    /** Adds a bounded contact glow only where neighboring occlusion samples hit a coherent surface. */
    public static boolean renderLocalSurface(
            PoseStack poseStack,
            MultiBufferSource buffers,
            LightState lightState,
            BeamOcclusionProfile occlusion
    ) {
        RenderType renderType = surfaceRenderType();
        if (renderType == null) {
            return false;
        }
        double range = Math.min(lightState.range(), MAX_PROTOTYPE_RANGE);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        Vec3 start = new Vec3(0.0, 0.0, BeamOcclusionSampler.START_OFFSET);
        double coneSlope = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5));
        emitLocalContactSurface(vertices, pose, start, coneSlope, range, lightState, occlusion);
        return true;
    }

    public static boolean renderWorldOccluded(
            PoseStack poseStack,
            LightState lightState,
            Vec3 worldPosition,
            Vec3 forward,
            Vec3 cameraPosition,
            BeamOcclusionProfile occlusion
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

        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = reference.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        Vec3 start = worldPosition.add(forward.scale(BeamOcclusionSampler.START_OFFSET)).subtract(cameraPosition);
        double range = Math.min(lightState.range(), MAX_PROTOTYPE_RANGE);
        AngularShadowMask shadowMask = occlusion.toShadowMask(range);
        double coneSlope = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5));

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        int outerRing = BeamOcclusionSampler.RING_FRACTIONS.length - 1;
        double outerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[outerRing];
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int next = (segment + 1) % SEGMENTS;
            double distanceA = shadowMask.value(outerRing, segment) * range;
            double distanceB = shadowMask.value(outerRing, next) * range;
            Vec3 edgeA = worldEndpoint(
                    start, forward, right, up, outerSlope, segment,
                    distanceA
            );
            Vec3 edgeB = worldEndpoint(
                    start, forward, right, up, outerSlope, next,
                    distanceB
            );
            addVertex(vertices, pose, start, lightState, 55);
            addVertex(vertices, pose, edgeA, lightState, 3);
            addVertex(vertices, pose, edgeB, lightState, 3);
        }
        for (int ring = 1; ring < BeamOcclusionSampler.RING_FRACTIONS.length; ring++) {
            double innerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring - 1];
            double radialSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring];
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                double innerADistance = shadowMask.value(ring - 1, segment) * range;
                double innerBDistance = shadowMask.value(ring - 1, next) * range;
                double outerADistance = shadowMask.value(ring, segment) * range;
                double outerBDistance = shadowMask.value(ring, next) * range;
                Vec3 innerA = worldEndpoint(
                        start, forward, right, up, innerSlope, segment,
                        innerADistance
                );
                Vec3 innerB = worldEndpoint(
                        start, forward, right, up, innerSlope, next,
                        innerBDistance
                );
                Vec3 outerA = worldEndpoint(
                        start, forward, right, up, radialSlope, segment,
                        outerADistance
                );
                Vec3 outerB = worldEndpoint(
                        start, forward, right, up, radialSlope, next,
                        outerBDistance
                );
                int innerAlpha = 3;
                int outerAlpha = 3;
                if (coherentVolumeTriangle(range, innerADistance, outerADistance, outerBDistance)) {
                    addVertex(vertices, pose, innerA, lightState, innerAlpha);
                    addVertex(vertices, pose, outerA, lightState, outerAlpha);
                    addVertex(vertices, pose, outerB, lightState, outerAlpha);
                }
                if (coherentVolumeTriangle(range, innerADistance, outerBDistance, innerBDistance)) {
                    addVertex(vertices, pose, innerA, lightState, innerAlpha);
                    addVertex(vertices, pose, outerB, lightState, outerAlpha);
                    addVertex(vertices, pose, innerB, lightState, innerAlpha);
                }
            }
        }
        buffers.endBatch(renderType);
        return true;
    }

    public static boolean renderWorldSurface(
            PoseStack poseStack,
            LightState lightState,
            Vec3 worldPosition,
            Vec3 forward,
            Vec3 cameraPosition,
            BeamOcclusionProfile occlusion
    ) {
        RenderType renderType = surfaceRenderType();
        if (renderType == null) {
            return false;
        }
        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = reference.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        Vec3 start = worldPosition.add(forward.scale(BeamOcclusionSampler.START_OFFSET)).subtract(cameraPosition);
        double range = Math.min(lightState.range(), MAX_PROTOTYPE_RANGE);
        double coneSlope = Math.tan(Math.toRadians(lightState.outerAngleDegrees() * 0.5));
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        emitWorldContactSurface(
                buffers.getBuffer(renderType), poseStack.last(), start, forward, right, up,
                coneSlope, range, lightState, occlusion
        );
        buffers.endBatch(renderType);
        return true;
    }

    private static void emitLocalContactSurface(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 start,
            double coneSlope,
            double range,
            LightState lightState,
            BeamOcclusionProfile occlusion
    ) {
        for (int ring = 1; ring < BeamOcclusionSampler.RING_FRACTIONS.length; ring++) {
            double innerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring - 1];
            double outerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring];
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                emitContactQuad(
                        vertices, pose, lightState, range,
                        clippedLocalEndpoint(start, innerSlope, segment, occlusion.distance(ring - 1, segment)),
                        clippedLocalEndpoint(start, outerSlope, segment, occlusion.distance(ring, segment)),
                        clippedLocalEndpoint(start, outerSlope, next, occlusion.distance(ring, next)),
                        clippedLocalEndpoint(start, innerSlope, next, occlusion.distance(ring - 1, next)),
                        occlusion.distance(ring - 1, segment), occlusion.distance(ring, segment),
                        occlusion.distance(ring, next), occlusion.distance(ring - 1, next),
                        contactAlpha(lightState, BeamOcclusionSampler.RING_FRACTIONS[ring - 1]),
                        contactAlpha(lightState, BeamOcclusionSampler.RING_FRACTIONS[ring])
                );
            }
        }
    }

    private static void emitWorldContactSurface(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 forward,
            Vec3 right,
            Vec3 up,
            double coneSlope,
            double range,
            LightState lightState,
            BeamOcclusionProfile occlusion
    ) {
        for (int ring = 1; ring < BeamOcclusionSampler.RING_FRACTIONS.length; ring++) {
            double innerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring - 1];
            double outerSlope = coneSlope * BeamOcclusionSampler.RING_FRACTIONS[ring];
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                emitContactQuad(
                        vertices, pose, lightState, range,
                        worldEndpoint(start, forward, right, up, innerSlope, segment,
                                occlusion.distance(ring - 1, segment)),
                        worldEndpoint(start, forward, right, up, outerSlope, segment,
                                occlusion.distance(ring, segment)),
                        worldEndpoint(start, forward, right, up, outerSlope, next,
                                occlusion.distance(ring, next)),
                        worldEndpoint(start, forward, right, up, innerSlope, next,
                                occlusion.distance(ring - 1, next)),
                        occlusion.distance(ring - 1, segment), occlusion.distance(ring, segment),
                        occlusion.distance(ring, next), occlusion.distance(ring - 1, next),
                        contactAlpha(lightState, BeamOcclusionSampler.RING_FRACTIONS[ring - 1]),
                        contactAlpha(lightState, BeamOcclusionSampler.RING_FRACTIONS[ring])
                );
            }
        }
    }

    private static void emitContactQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            LightState lightState,
            double range,
            Vec3 innerA,
            Vec3 outerA,
            Vec3 outerB,
            Vec3 innerB,
            double innerADistance,
            double outerADistance,
            double outerBDistance,
            double innerBDistance,
            int innerAlpha,
            int outerAlpha
    ) {
        if (coherentContact(range, innerADistance, outerADistance, outerBDistance)) {
            addSurfaceVertex(vertices, pose, innerA, lightState, innerAlpha);
            addSurfaceVertex(vertices, pose, outerA, lightState, outerAlpha);
            addSurfaceVertex(vertices, pose, outerB, lightState, outerAlpha);
        }
        if (coherentContact(range, innerADistance, outerBDistance, innerBDistance)) {
            addSurfaceVertex(vertices, pose, innerA, lightState, innerAlpha);
            addSurfaceVertex(vertices, pose, outerB, lightState, outerAlpha);
            addSurfaceVertex(vertices, pose, innerB, lightState, innerAlpha);
        }
    }

    private static boolean coherentContact(double range, double first, double second, double third) {
        double maximum = Math.max(first, Math.max(second, third));
        double minimum = Math.min(first, Math.min(second, third));
        double tolerance = Math.max(1.5, maximum * 0.20);
        return maximum < range - 0.10 && maximum - minimum <= tolerance;
    }

    private static boolean coherentVolumeTriangle(double range, double first, double second, double third) {
        double minimum = Math.min(first, Math.min(second, third));
        double maximum = Math.max(first, Math.max(second, third));
        return minimum < range - 0.10 && coherentVolumeDepths(range, minimum, maximum);
    }

    private static boolean coherentVolumeDepths(double range, double minimum, double maximum) {
        double tolerance = Math.max(2.0, Math.min(range * 0.35, maximum * 0.40));
        return maximum - minimum <= tolerance;
    }

    private static RenderType surfaceRenderType() {
        try {
            return VeilRenderType.get(SURFACE_RENDER_TYPE_ID);
        } catch (RuntimeException exception) {
            Luxworks.LOGGER.warn("Veil surface contact render type is unavailable", exception);
            return null;
        }
    }

    private static Vec3 worldEndpoint(
            Vec3 start,
            Vec3 forward,
            Vec3 right,
            Vec3 up,
            double radialSlope,
            int segment,
            double distance
    ) {
        double angle = Math.PI * 2.0 * segment / SEGMENTS;
        Vec3 direction = forward
                .add(right.scale(Math.cos(angle) * radialSlope))
                .add(up.scale(Math.sin(angle) * radialSlope))
                .normalize();
        return start.add(direction.scale(distance));
    }

    private static Vec3 clippedLocalEndpoint(Vec3 start, double radialSlope, int segment, double distance) {
        double angle = Math.PI * 2.0 * segment / SEGMENTS;
        Vec3 direction = new Vec3(
                Math.cos(angle) * radialSlope,
                Math.sin(angle) * radialSlope,
                1.0
        ).normalize();
        return start.add(direction.scale(distance));
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

    private static void addSurfaceVertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 position,
            LightState lightState,
            int baseAlpha
    ) {
        int red = Math.round(lightState.red() * 255.0F);
        int green = Math.round(lightState.green() * 255.0F);
        int blue = Math.round(lightState.blue() * 255.0F);
        int alpha = Math.clamp(Math.round(baseAlpha * lightState.intensity()), 0, 180);
        vertices.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }

    private static int contactAlpha(LightState lightState, double ringFraction) {
        return Math.round(72.0F * angularBrightness(lightState, ringFraction));
    }

    private static float angularBrightness(LightState lightState, double ringFraction) {
        double outerHalfAngle = Math.toRadians(lightState.outerAngleDegrees() * 0.5);
        double innerHalfAngle = Math.toRadians(lightState.innerAngleDegrees() * 0.5);
        if (outerHalfAngle <= innerHalfAngle + 1.0E-6) {
            return 1.0F;
        }
        double radialHalfAngle = Math.atan(Math.tan(outerHalfAngle) * ringFraction);
        if (radialHalfAngle <= innerHalfAngle) {
            return 1.0F;
        }
        return (float) Math.clamp(
                (outerHalfAngle - radialHalfAngle) / (outerHalfAngle - innerHalfAngle),
                0.0,
                1.0
        );
    }
}
