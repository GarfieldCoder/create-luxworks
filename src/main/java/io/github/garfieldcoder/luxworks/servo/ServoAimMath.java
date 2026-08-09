package io.github.garfieldcoder.luxworks.servo;

/** Dependency-free inverse aiming math used by the Minecraft adapter. */
public final class ServoAimMath {
    private ServoAimMath() {
    }

    public static ServoTarget targetForDirection(
            double baseForwardX,
            double baseForwardZ,
            double requestedX,
            double requestedY,
            double requestedZ
    ) {
        double length = Math.sqrt(requestedX * requestedX + requestedY * requestedY + requestedZ * requestedZ);
        if (!Double.isFinite(length) || length == 0.0) {
            return new ServoTarget(0.0F, 0.0F);
        }

        double x = requestedX / length;
        double y = requestedY / length;
        double z = requestedZ / length;
        double horizontalLength = Math.hypot(x, z);
        float yaw = 0.0F;
        if (horizontalLength > 1.0E-12) {
            double horizontalX = x / horizontalLength;
            double horizontalZ = z / horizontalLength;
            double sine = horizontalZ * baseForwardX - horizontalX * baseForwardZ;
            double cosine = baseForwardX * horizontalX + baseForwardZ * horizontalZ;
            yaw = (float) Math.toDegrees(Math.atan2(sine, cosine));
        }
        float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(y, -1.0, 1.0)));
        return new ServoTarget(yaw, pitch);
    }
}
