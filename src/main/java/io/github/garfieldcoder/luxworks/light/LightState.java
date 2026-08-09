package io.github.garfieldcoder.luxworks.light;

import java.util.Objects;
import java.util.UUID;

/**
 * Loader- and renderer-neutral settings for one Luxworks light.
 *
 * <p>The state owns gameplay/configuration values, while {@link LightTransform}
 * owns the fixture's resolved world-space pose. Keeping the two separate lets
 * static and moving fixtures share the same renderer.</p>
 */
public record LightState(
        UUID id,
        boolean enabled,
        int colorRgb,
        float intensity,
        float range,
        float innerAngleDegrees,
        float outerAngleDegrees
) {
    public static final int DEFAULT_COLOR_RGB = 0xFFDE78;
    public static final float DEFAULT_INTENSITY = 1.0F;
    public static final float DEFAULT_RANGE = 16.0F;
    public static final float DEFAULT_INNER_ANGLE_DEGREES = 12.0F;
    public static final float DEFAULT_OUTER_ANGLE_DEGREES = 18.0F;
    private static final float MAX_CONE_ANGLE_DEGREES = Math.nextDown(180.0F);

    public LightState {
        id = Objects.requireNonNull(id, "id");
        colorRgb &= 0xFFFFFF;
        intensity = nonNegativeFinite(intensity);
        range = nonNegativeFinite(range);
        outerAngleDegrees = clampFinite(outerAngleDegrees, 0.0F, MAX_CONE_ANGLE_DEGREES);
        innerAngleDegrees = clampFinite(innerAngleDegrees, 0.0F, outerAngleDegrees);
    }

    public static LightState defaults(UUID id) {
        return new LightState(
                id,
                true,
                DEFAULT_COLOR_RGB,
                DEFAULT_INTENSITY,
                DEFAULT_RANGE,
                DEFAULT_INNER_ANGLE_DEGREES,
                DEFAULT_OUTER_ANGLE_DEGREES
        );
    }

    public float red() {
        return ((colorRgb >>> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((colorRgb >>> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (colorRgb & 0xFF) / 255.0F;
    }

    private static float nonNegativeFinite(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    private static float clampFinite(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.clamp(value, minimum, maximum);
    }
}
