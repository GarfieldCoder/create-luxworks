package io.github.garfieldcoder.luxworks.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Converts world-space manual targets into a fixture's local Sable frame. */
public final class SableLightTargetResolver {
    private SableLightTargetResolver() {
    }

    public static Vec3 resolveEntityEyeInFixtureFrame(Level level, BlockPos fixturePos, Entity entity) {
        Vec3 worldEyePosition = SableCompanion.INSTANCE.getEyePositionInterpolated(entity, 1.0F);
        return resolveWorldPositionInFixtureFrame(level, fixturePos, worldEyePosition);
    }

    public static Vec3 resolveWorldPositionInFixtureFrame(Level level, BlockPos fixturePos, Vec3 worldPosition) {
        SubLevelAccess containing = SableCompanion.INSTANCE.getContaining(level, fixturePos);
        if (containing == null) {
            return worldPosition;
        }
        return containing.logicalPose().transformPositionInverse(worldPosition);
    }
}
