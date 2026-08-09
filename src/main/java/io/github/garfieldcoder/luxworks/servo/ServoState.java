package io.github.garfieldcoder.luxworks.servo;

/**
 * Immutable two-axis aiming state measured in degrees.
 *
 * <p>Yaw wraps through the shortest path. Pitch is limited to straight down
 * through straight up. The speed limit applies to combined angular travel,
 * so diagonal movement is not faster than movement on one axis.</p>
 */
public record ServoState(
        float currentYaw,
        float currentPitch,
        float targetYaw,
        float targetPitch,
        float maxAngularVelocity
) {
    public static final float DEFAULT_MAX_ANGULAR_VELOCITY = 90.0F;

    public ServoState {
        currentYaw = normalizeYaw(finiteOrZero(currentYaw));
        currentPitch = clampPitch(finiteOrZero(currentPitch));
        targetYaw = normalizeYaw(finiteOrZero(targetYaw));
        targetPitch = clampPitch(finiteOrZero(targetPitch));
        maxAngularVelocity = Math.max(0.0F, finiteOrZero(maxAngularVelocity));
    }

    public static ServoState defaults() {
        return new ServoState(0.0F, 0.0F, 0.0F, 0.0F, DEFAULT_MAX_ANGULAR_VELOCITY);
    }

    public ServoState withTarget(float yaw, float pitch) {
        return new ServoState(currentYaw, currentPitch, yaw, pitch, maxAngularVelocity);
    }

    public ServoState advance(float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0.0F || maxAngularVelocity <= 0.0F) {
            return this;
        }

        float yawDelta = shortestYawDelta(currentYaw, targetYaw);
        float pitchDelta = targetPitch - currentPitch;
        float distance = (float) Math.hypot(yawDelta, pitchDelta);
        if (distance == 0.0F) {
            return this;
        }

        float fraction = Math.min(1.0F, maxAngularVelocity * elapsedSeconds / distance);
        return new ServoState(
                currentYaw + yawDelta * fraction,
                currentPitch + pitchDelta * fraction,
                targetYaw,
                targetPitch,
                maxAngularVelocity
        );
    }

    public boolean isAtTarget(float toleranceDegrees) {
        float tolerance = Math.max(0.0F, finiteOrZero(toleranceDegrees));
        return Math.abs(shortestYawDelta(currentYaw, targetYaw)) <= tolerance
                && Math.abs(targetPitch - currentPitch) <= tolerance;
    }

    public ServoState interpolateFrom(ServoState previous, float partialTick) {
        float alpha = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0F, 1.0F) : 0.0F;
        float interpolatedYaw = previous.currentYaw
                + shortestYawDelta(previous.currentYaw, currentYaw) * alpha;
        float interpolatedPitch = previous.currentPitch
                + (currentPitch - previous.currentPitch) * alpha;
        return new ServoState(
                interpolatedYaw,
                interpolatedPitch,
                targetYaw,
                targetPitch,
                maxAngularVelocity
        );
    }

    public static float shortestYawDelta(float fromYaw, float toYaw) {
        return normalizeYaw(toYaw - fromYaw);
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized >= 180.0F) {
            normalized -= 360.0F;
        } else if (normalized < -180.0F) {
            normalized += 360.0F;
        }
        return normalized;
    }

    private static float clampPitch(float pitch) {
        return Math.clamp(pitch, -90.0F, 90.0F);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
