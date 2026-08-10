package io.github.garfieldcoder.luxworks.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Raycasts ordinary world-space light samples through captured Create blocks. */
public final class CreateLightOcclusionResolver {
    private CreateLightOcclusionResolver() {
    }

    public static double clipDistance(
            Level level,
            Vec3 start,
            Vec3 end,
            Entity source,
            float partialTick
    ) {
        double nearest = start.distanceTo(end);
        if (Minecraft.getInstance().level == null) {
            return nearest;
        }
        for (var candidate : Minecraft.getInstance().level.entitiesForRendering()) {
            if (!(candidate instanceof AbstractContraptionEntity contraption)
                    || !contraption.isReadyForRender()) {
                continue;
            }
            Vec3 localStart = CreateInterpolatedTransform.toLocalVector(contraption, start, partialTick);
            Vec3 localEnd = CreateInterpolatedTransform.toLocalVector(contraption, end, partialTick);
            var localBounds = contraption.getContraption().bounds.inflate(0.01);
            if (!localBounds.contains(localStart) && localBounds.clip(localStart, localEnd).isEmpty()) {
                continue;
            }
            for (var entry : contraption.getContraption().getBlocks().entrySet()) {
                BlockPos localPos = entry.getKey();
                VoxelShape shape = entry.getValue().state().getCollisionShape(level, localPos);
                if (shape.isEmpty()) {
                    continue;
                }
                BlockHitResult localHit = shape.clip(localStart, localEnd, localPos);
                if (localHit == null || localHit.getType() != HitResult.Type.BLOCK) {
                    continue;
                }
                Vec3 worldHit = CreateInterpolatedTransform.toGlobalVector(
                        contraption, localHit.getLocation(), partialTick
                );
                double distance = start.distanceTo(worldHit);
                if (distance < nearest) {
                    nearest = distance;
                }
            }
        }
        return nearest;
    }
}
