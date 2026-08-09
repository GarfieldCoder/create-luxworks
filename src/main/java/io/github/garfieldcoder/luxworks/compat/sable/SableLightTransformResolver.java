package io.github.garfieldcoder.luxworks.compat.sable;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.light.StaticLightSource;
import io.github.garfieldcoder.luxworks.light.StaticLightTransformResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Client-side Sable adapter. Dependency-specific types stay inside this
 * package and the renderer receives only a neutral {@link LightTransform}.
 */
public final class SableLightTransformResolver {
    private static final StaticLightTransformResolver STATIC_RESOLVER = new StaticLightTransformResolver();

    private SableLightTransformResolver() {
    }

    public static LightTransform resolve(
            ClientLevel level,
            BlockPos blockPos,
            Direction facing,
            float partialTick
    ) {
        Vec3 localForward = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return resolve(level, blockPos, localForward, partialTick);
    }

    public static LightTransform resolve(
            ClientLevel level,
            BlockPos blockPos,
            Vec3 localForward,
            float partialTick
    ) {
        LightTransform localTransform = STATIC_RESOLVER.resolve(blockPos, localForward);
        SubLevelAccess containing = SableCompanion.INSTANCE.getContaining(level, blockPos);
        if (!(containing instanceof ClientSubLevelAccess subLevel)) {
            return localTransform;
        }

        Pose3dc pose = subLevel.renderPose(partialTick);
        Vec3 worldPosition = pose.transformPosition(localTransform.worldPosition());
        Vec3 worldForward = pose.transformNormal(localTransform.forward()).normalize();
        Quaternionf worldRotation = new Quaternionf(pose.orientation())
                .mul(new Quaternionf(localTransform.worldRotation()))
                .normalize();

        return new LightTransform(worldPosition, worldRotation, worldForward);
    }
}
