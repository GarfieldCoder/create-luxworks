package io.github.garfieldcoder.luxworks.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.phys.Vec3;

/** Keeps Luxworks rendering and ray queries in Create's same interpolated frame. */
public final class CreateInterpolatedTransform {
    private CreateInterpolatedTransform() {
    }

    public static Vec3 toGlobalVector(
            AbstractContraptionEntity entity,
            Vec3 localPosition,
            float partialTick
    ) {
        float alpha = clampedPartialTick(partialTick);
        Vec3 previous = entity.toGlobalVector(localPosition, alpha, true);
        Vec3 current = entity.toGlobalVector(localPosition, alpha, false);
        return previous.lerp(current, alpha);
    }

    public static Vec3 toLocalVector(
            AbstractContraptionEntity entity,
            Vec3 worldPosition,
            float partialTick
    ) {
        float alpha = clampedPartialTick(partialTick);
        Vec3 previous = entity.toLocalVector(worldPosition, alpha, true);
        Vec3 current = entity.toLocalVector(worldPosition, alpha, false);
        return previous.lerp(current, alpha);
    }

    private static float clampedPartialTick(float partialTick) {
        return Float.isFinite(partialTick) ? Math.clamp(partialTick, 0.0F, 1.0F) : 0.0F;
    }
}
