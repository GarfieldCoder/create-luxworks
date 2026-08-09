package io.github.garfieldcoder.luxworks.servo;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Converts fixture-local servo angles into a normalized local beam direction. */
public final class ServoDirectionResolver {
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private ServoDirectionResolver() {
    }

    public static Vec3 resolve(Direction mountingForward, ServoState state) {
        Vec3 baseForward = new Vec3(
                mountingForward.getStepX(),
                mountingForward.getStepY(),
                mountingForward.getStepZ()
        ).normalize();
        Vec3 yawed = rotateAroundAxis(baseForward, UP, Math.toRadians(-state.currentYaw()));
        Vec3 right = yawed.cross(UP).normalize();
        return rotateAroundAxis(yawed, right, Math.toRadians(state.currentPitch())).normalize();
    }

    public static ServoTarget targetForDirection(Direction mountingForward, Vec3 requestedDirection) {
        return ServoAimMath.targetForDirection(
                mountingForward.getStepX(),
                mountingForward.getStepZ(),
                requestedDirection.x,
                requestedDirection.y,
                requestedDirection.z
        );
    }

    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(axis.cross(vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0 - cosine)));
    }
}
