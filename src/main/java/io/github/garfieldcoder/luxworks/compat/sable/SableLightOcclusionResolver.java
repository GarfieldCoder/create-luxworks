package io.github.garfieldcoder.luxworks.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Performs one ray query against both the ordinary world and Sable sublevels. */
public final class SableLightOcclusionResolver {
    private SableLightOcclusionResolver() {
    }

    public static double clipDistance(Level level, Vec3 start, Vec3 end, Entity source) {
        double nearest = start.distanceTo(end);
        BlockHitResult worldHit = clip(level, start, end, source);
        if (worldHit.getType() == HitResult.Type.BLOCK) {
            nearest = start.distanceTo(worldHit.getLocation());
        }

        BoundingBox3d rayBounds = new BoundingBox3d(start, end).expand(0.01);
        for (SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, rayBounds)) {
            Vec3 localStart = subLevel.logicalPose().transformPositionInverse(start);
            Vec3 localEnd = subLevel.logicalPose().transformPositionInverse(end);
            BlockHitResult localHit = clip(level, localStart, localEnd, source);
            if (localHit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            Vec3 worldHitPosition = subLevel.logicalPose().transformPosition(localHit.getLocation());
            nearest = Math.min(nearest, start.distanceTo(worldHitPosition));
        }
        return nearest;
    }

    private static BlockHitResult clip(Level level, Vec3 start, Vec3 end, Entity source) {
        return level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        ));
    }
}
