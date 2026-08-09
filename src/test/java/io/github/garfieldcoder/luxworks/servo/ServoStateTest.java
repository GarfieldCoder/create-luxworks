package io.github.garfieldcoder.luxworks.servo;

public final class ServoStateTest {
    private static final float EPSILON = 0.0001F;

    private ServoStateTest() {
    }

    public static void main(String[] args) {
        sanitizesAnglesAndSpeed();
        choosesShortestYawPath();
        limitsCombinedAngularTravel();
        reachesTargetWithoutOvershooting();
        convertsDirectionToFixtureAngles();
        interpolatesAcrossWrappedYaw();
    }

    private static void sanitizesAnglesAndSpeed() {
        ServoState state = new ServoState(540.0F, -120.0F, -540.0F, 120.0F, -1.0F);

        assertNear(-180.0F, state.currentYaw());
        assertNear(-90.0F, state.currentPitch());
        assertNear(-180.0F, state.targetYaw());
        assertNear(90.0F, state.targetPitch());
        assertNear(0.0F, state.maxAngularVelocity());
    }

    private static void choosesShortestYawPath() {
        ServoState state = new ServoState(170.0F, 0.0F, -170.0F, 0.0F, 10.0F).advance(1.0F);

        assertNear(-180.0F, state.currentYaw());
    }

    private static void limitsCombinedAngularTravel() {
        ServoState state = new ServoState(0.0F, 0.0F, 90.0F, 90.0F, 90.0F).advance(1.0F);

        float traveled = (float) Math.hypot(state.currentYaw(), state.currentPitch());
        assertNear(90.0F, traveled);
        check(!state.isAtTarget(EPSILON), "diagonal movement must respect the combined speed limit");
    }

    private static void reachesTargetWithoutOvershooting() {
        ServoState state = new ServoState(0.0F, 0.0F, 15.0F, -10.0F, 90.0F).advance(1.0F);

        assertNear(15.0F, state.currentYaw());
        assertNear(-10.0F, state.currentPitch());
        check(state.isAtTarget(EPSILON), "servo should stop at its target");
    }

    private static void convertsDirectionToFixtureAngles() {
        ServoTarget target = ServoAimMath.targetForDirection(0.0, -1.0, 1.0, 1.0, -1.0);

        assertNear(45.0F, target.yaw());
        assertNear(35.26439F, target.pitch());
    }

    private static void interpolatesAcrossWrappedYaw() {
        ServoState previous = new ServoState(179.0F, 0.0F, -170.0F, 20.0F, 90.0F);
        ServoState current = new ServoState(-179.0F, 10.0F, -170.0F, 20.0F, 90.0F);
        ServoState halfway = current.interpolateFrom(previous, 0.5F);

        assertNear(-180.0F, halfway.currentYaw());
        assertNear(5.0F, halfway.currentPitch());
    }

    private static void assertNear(float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
