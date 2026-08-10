package io.github.garfieldcoder.luxworks.client.render;

import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.compat.create.CreateLightOcclusionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoState;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightRayResolver;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightOcclusionResolver;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTransformResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Coarse CPU light-space visibility sampler for the Phase 1 prototype. */
public final class BeamOcclusionSampler {
    public static final int SEGMENTS = 16;
    public static final double[] RING_FRACTIONS = {0.0, 0.2, 0.5, 0.75, 1.0};
    public static final double START_OFFSET = 0.60;
    private static final double SOURCE_EXIT_EPSILON = 1.0E-4;

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
                double hitDistance = clipDistance(spotlight, start, direction, range, partialTick);
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
                double hitDistance = clipDistance(spotlight, start, direction, range, partialTick);
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

    private static double clipDistance(
            SpotlightBlockEntity spotlight,
            Vec3 renderStart,
            Vec3 direction,
            double range,
            float partialTick
    ) {
        Vec3 queryStart = renderStart;
        if (!SableLightTransformResolver.isInSubLevel(spotlight.getLevel(), spotlight.getBlockPos())) {
            queryStart = advancePastEmitterCell(spotlight.getBlockPos(), renderStart, direction);
        }
        double skippedDistance = renderStart.distanceTo(queryStart);
        double queryRange = Math.max(0.0, range - skippedDistance);
        Vec3 end = queryStart.add(direction.scale(queryRange));
        double hitDistance = SableLightOcclusionResolver.clipDistance(
                spotlight.getLevel(), queryStart, end, Minecraft.getInstance().player
        );
        hitDistance = Math.min(hitDistance, CreateLightOcclusionResolver.clipDistance(
                spotlight.getLevel(), queryStart, end, Minecraft.getInstance().player, partialTick
        ));
        return Math.min(range, skippedDistance + hitDistance);
    }

    static Vec3 advancePastEmitterCell(BlockPos blockPos, Vec3 start, Vec3 direction) {
        double minimumX = blockPos.getX();
        double minimumY = blockPos.getY();
        double minimumZ = blockPos.getZ();
        double maximumX = minimumX + 1.0;
        double maximumY = minimumY + 1.0;
        double maximumZ = minimumZ + 1.0;
        if (start.x < minimumX || start.x > maximumX
                || start.y < minimumY || start.y > maximumY
                || start.z < minimumZ || start.z > maximumZ) {
            return start;
        }

        double exitX = distanceToBoundary(start.x, direction.x, minimumX, maximumX);
        double exitY = distanceToBoundary(start.y, direction.y, minimumY, maximumY);
        double exitZ = distanceToBoundary(start.z, direction.z, minimumZ, maximumZ);
        double exitDistance = Math.min(exitX, Math.min(exitY, exitZ));
        if (!Double.isFinite(exitDistance) || exitDistance < 0.0) {
            return start;
        }
        return start.add(direction.scale(exitDistance + SOURCE_EXIT_EPSILON));
    }

    private static double distanceToBoundary(
            double position,
            double direction,
            double minimum,
            double maximum
    ) {
        if (direction > 1.0E-9) {
            return (maximum - position) / direction;
        }
        if (direction < -1.0E-9) {
            return (minimum - position) / direction;
        }
        return Double.POSITIVE_INFINITY;
    }
}
