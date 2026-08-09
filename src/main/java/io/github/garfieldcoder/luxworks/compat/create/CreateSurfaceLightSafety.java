package io.github.garfieldcoder.luxworks.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Conservative receiver guard for Veil deferred lights around Flywheel-rendered
 * Create contraptions.
 *
 * <p>Flywheel does not populate Veil's albedo and normal buffers for contraption
 * geometry. Until Luxworks owns a compatible receiver pass, allowing a Veil area
 * light volume to overlap a contraption can project stale world-buffer samples
 * over it and make solid faces appear transparent.</p>
 */
public final class CreateSurfaceLightSafety {
    private CreateSurfaceLightSafety() {
    }

    public static boolean overlapsContraption(LightTransform transform, LightState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || state.range() <= 0.0F) {
            return false;
        }

        Vec3 origin = transform.worldPosition();
        Vec3 forward = transform.forward().normalize();
        double halfAngle = Math.toRadians(state.outerAngleDegrees() * 0.5);
        double range = Math.min(state.range(), 32.0F);

        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof AbstractContraptionEntity contraption
                    && contraption.isReadyForRender()
                    && coneMayOverlap(origin, forward, halfAngle, range, contraption.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private static boolean coneMayOverlap(
            Vec3 origin,
            Vec3 forward,
            double halfAngle,
            double range,
            AABB bounds
    ) {
        Vec3 center = bounds.getCenter();
        double radius = 0.5 * Math.sqrt(
                bounds.getXsize() * bounds.getXsize()
                        + bounds.getYsize() * bounds.getYsize()
                        + bounds.getZsize() * bounds.getZsize()
        );
        Vec3 toCenter = center.subtract(origin);
        double distance = toCenter.length();
        if (distance - radius > range) {
            return false;
        }
        if (distance <= radius || distance < 1.0E-6) {
            return true;
        }

        double angularRadius = Math.asin(Math.clamp(radius / distance, 0.0, 1.0));
        double expandedHalfAngle = Math.min(Math.PI, halfAngle + angularRadius);
        double directionDot = forward.dot(toCenter.scale(1.0 / distance));
        return directionDot >= Math.cos(expandedHalfAngle);
    }
}
