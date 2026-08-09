package io.github.garfieldcoder.luxworks.light;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Resolves fixtures already positioned in normal world coordinates.
 */
public final class StaticLightTransformResolver implements LightTransformResolver<StaticLightSource> {
    private static final float MODEL_FORWARD_X = 0.0F;
    private static final float MODEL_FORWARD_Y = 0.0F;
    private static final float MODEL_FORWARD_Z = 1.0F;

    @Override
    public LightTransform resolve(StaticLightSource source) {
        Direction facing = source.facing();
        Vec3 forward = new Vec3(
                facing.getStepX(),
                facing.getStepY(),
                facing.getStepZ()
        ).normalize();
        return resolve(source.blockPos(), forward);
    }

    public LightTransform resolve(net.minecraft.core.BlockPos blockPos, Vec3 forward) {
        forward = forward.normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(
                MODEL_FORWARD_X,
                MODEL_FORWARD_Y,
                MODEL_FORWARD_Z,
                (float) forward.x,
                (float) forward.y,
                (float) forward.z
        );

        return new LightTransform(Vec3.atCenterOf(blockPos), rotation, forward);
    }
}
