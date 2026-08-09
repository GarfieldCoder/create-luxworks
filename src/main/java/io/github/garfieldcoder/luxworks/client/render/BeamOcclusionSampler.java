package io.github.garfieldcoder.luxworks.client.render;

import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.compat.create.CreateLightOcclusionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoState;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightRayResolver;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightOcclusionResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Coarse CPU light-space visibility sampler for the Phase 1 prototype. */
public final class BeamOcclusionSampler {
    public static final int SEGMENTS = 16;
    public static final double[] RING_FRACTIONS = {0.0, 0.2, 0.5, 0.75, 1.0};
    private static final double START_OFFSET = 0.60;

    private BeamOcclusionSampler() {
    }

    public static BeamOcclusionProfile sample(
            SpotlightBlockEntity spotlight,
            ServoState servo,
            double range,
            double outerAngleDegrees
    ) {
        double[][] distances = new double[RING_FRACTIONS.length][SEGMENTS];
        if (spotlight.getLevel() == null || Minecraft.getInstance().player == null) {
            fill(distances, range);
            return new BeamOcclusionProfile(distances);
        }

        Vec3 forward = ServoDirectionResolver.resolve(
                spotlight.getBlockState().getValue(DebugLightBlock.FACING),
                servo
        );
        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = reference.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        Vec3 localStart = Vec3.atCenterOf(spotlight.getBlockPos()).add(forward.scale(START_OFFSET));
        Vec3 start = SableLightRayResolver.resolvePosition(
                spotlight.getLevel(),
                spotlight.getBlockPos(),
                localStart
        );
        double coneSlope = Math.tan(Math.toRadians(outerAngleDegrees * 0.5));
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        for (int ring = 0; ring < RING_FRACTIONS.length; ring++) {
            double radialSlope = coneSlope * RING_FRACTIONS[ring];
            if (radialSlope == 0.0) {
                Vec3 direction = SableLightRayResolver.resolveDirection(
                        spotlight.getLevel(),
                        spotlight.getBlockPos(),
                        forward
                );
                Vec3 end = start.add(direction.scale(range));
                double hitDistance = SableLightOcclusionResolver.clipDistance(
                        spotlight.getLevel(), start, end, Minecraft.getInstance().player
                );
                hitDistance = Math.min(hitDistance, CreateLightOcclusionResolver.clipDistance(
                        spotlight.getLevel(), start, end, Minecraft.getInstance().player, partialTick
                ));
                java.util.Arrays.fill(distances[ring], Math.max(0.0, hitDistance - 0.03));
                continue;
            }
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double angle = Math.PI * 2.0 * segment / SEGMENTS;
                Vec3 localDirection = forward
                        .add(right.scale(Math.cos(angle) * radialSlope))
                        .add(up.scale(Math.sin(angle) * radialSlope))
                        .normalize();
                Vec3 direction = SableLightRayResolver.resolveDirection(
                        spotlight.getLevel(),
                        spotlight.getBlockPos(),
                        localDirection
                );
                double hitDistance = SableLightOcclusionResolver.clipDistance(
                        spotlight.getLevel(),
                        start,
                        start.add(direction.scale(range)),
                        Minecraft.getInstance().player
                );
                hitDistance = Math.min(hitDistance, CreateLightOcclusionResolver.clipDistance(
                        spotlight.getLevel(),
                        start,
                        start.add(direction.scale(range)),
                        Minecraft.getInstance().player,
                        partialTick
                ));
                distances[ring][segment] = Math.max(0.0, hitDistance - 0.03);
            }
        }
        return new BeamOcclusionProfile(distances);
    }

    public static BeamOcclusionProfile sampleWorld(
            net.minecraft.world.level.Level level,
            Vec3 start,
            Vec3 forward,
            double range,
            double outerAngleDegrees
    ) {
        double[][] distances = new double[RING_FRACTIONS.length][SEGMENTS];
        if (Minecraft.getInstance().player == null) {
            fill(distances, range);
            return new BeamOcclusionProfile(distances);
        }

        Vec3 reference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = reference.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        double coneSlope = Math.tan(Math.toRadians(outerAngleDegrees * 0.5));
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        for (int ring = 0; ring < RING_FRACTIONS.length; ring++) {
            double radialSlope = coneSlope * RING_FRACTIONS[ring];
            if (radialSlope == 0.0) {
                Vec3 end = start.add(forward.scale(range));
                double hitDistance = SableLightOcclusionResolver.clipDistance(
                        level, start, end, Minecraft.getInstance().player
                );
                hitDistance = Math.min(hitDistance, CreateLightOcclusionResolver.clipDistance(
                        level, start, end, Minecraft.getInstance().player, partialTick
                ));
                java.util.Arrays.fill(distances[ring], Math.max(0.0, hitDistance - 0.03));
                continue;
            }
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double angle = Math.PI * 2.0 * segment / SEGMENTS;
                Vec3 direction = forward
                        .add(right.scale(Math.cos(angle) * radialSlope))
                        .add(up.scale(Math.sin(angle) * radialSlope))
                        .normalize();
                double hitDistance = SableLightOcclusionResolver.clipDistance(
                        level,
                        start,
                        start.add(direction.scale(range)),
                        Minecraft.getInstance().player
                );
                hitDistance = Math.min(hitDistance, CreateLightOcclusionResolver.clipDistance(
                        level,
                        start,
                        start.add(direction.scale(range)),
                        Minecraft.getInstance().player,
                        partialTick
                ));
                distances[ring][segment] = Math.max(0.0, hitDistance - 0.03);
            }
        }
        return new BeamOcclusionProfile(distances);
    }

    private static void fill(double[][] distances, double value) {
        for (double[] ring : distances) {
            java.util.Arrays.fill(ring, value);
        }
    }
}
