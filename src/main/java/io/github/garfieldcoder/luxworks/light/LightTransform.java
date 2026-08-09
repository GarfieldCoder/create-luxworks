package io.github.garfieldcoder.luxworks.light;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Objects;

/**
 * A resolved fixture pose in world space. Rendering code consumes this value
 * without needing to know whether the fixture is static or moving.
 */
public record LightTransform(Vec3 worldPosition, Quaternionfc worldRotation, Vec3 forward) {
    private static final double UNIT_TOLERANCE = 1.0E-6;

    public LightTransform {
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(worldRotation, "worldRotation");
        Objects.requireNonNull(forward, "forward");

        if (!isFinite(worldPosition) || !isFinite(forward)) {
            throw new IllegalArgumentException("Light transform vectors must be finite");
        }
        if (Math.abs(forward.lengthSqr() - 1.0) > UNIT_TOLERANCE) {
            throw new IllegalArgumentException("Light transform forward vector must be normalized");
        }

        worldRotation = new Quaternionf(worldRotation).normalize();
    }

    @Override
    public Quaternionfc worldRotation() {
        return new Quaternionf(worldRotation);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
