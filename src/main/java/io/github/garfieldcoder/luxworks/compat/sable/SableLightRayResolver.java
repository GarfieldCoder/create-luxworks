package io.github.garfieldcoder.luxworks.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Projects light-ray samples from a fixture's logical frame into world space. */
public final class SableLightRayResolver {
    private SableLightRayResolver() {
    }

    public static Vec3 resolvePosition(Level level, BlockPos fixturePos, Vec3 localPosition) {
        SubLevelAccess containing = SableCompanion.INSTANCE.getContaining(level, fixturePos);
        return containing == null ? localPosition : containing.logicalPose().transformPosition(localPosition);
    }

    public static Vec3 resolveDirection(Level level, BlockPos fixturePos, Vec3 localDirection) {
        SubLevelAccess containing = SableCompanion.INSTANCE.getContaining(level, fixturePos);
        return containing == null
                ? localDirection
                : containing.logicalPose().transformNormal(localDirection).normalize();
    }
}
