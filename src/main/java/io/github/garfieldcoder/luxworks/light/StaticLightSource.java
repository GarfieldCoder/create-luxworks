package io.github.garfieldcoder.luxworks.light;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * The local information needed to resolve a stationary directional fixture.
 */
public record StaticLightSource(BlockPos blockPos, Direction facing) {
    public StaticLightSource {
        Objects.requireNonNull(blockPos, "blockPos");
        Objects.requireNonNull(facing, "facing");
    }
}
