package io.github.garfieldcoder.luxworks.light;

public final class AngularShadowMaskTest {
    private static final float EPSILON = 0.0001F;

    private AngularShadowMaskTest() {
    }

    public static void main(String[] args) {
        normalizesAndClampsDistances();
        wrapsAngularInterpolation();
        interpolatesBetweenRadialRings();
        supportsCenterSample();
        rejectsInvalidLayouts();
    }

    private static void normalizesAndClampsDistances() {
        AngularShadowMask mask = AngularShadowMask.fromDistances(
                new double[]{0.5, 1.0},
                new double[][]{{0.0, 5.0, 10.0, 15.0}, {2.5, 5.0, 7.5, 10.0}},
                10.0
        );
        assertNear(0.0F, mask.value(0, 0));
        assertNear(0.5F, mask.value(0, 1));
        assertNear(1.0F, mask.value(0, 2));
        assertNear(1.0F, mask.value(0, 3));
    }

    private static void wrapsAngularInterpolation() {
        AngularShadowMask mask = new AngularShadowMask(
                new double[]{1.0},
                new float[][]{{0.0F, 1.0F, 1.0F, 1.0F}}
        );
        assertNear(0.5F, mask.sample(1.0, -Math.PI / 4.0));
        assertNear(0.5F, mask.sample(1.0, Math.PI / 4.0));
    }

    private static void interpolatesBetweenRadialRings() {
        AngularShadowMask mask = new AngularShadowMask(
                new double[]{0.25, 1.0},
                new float[][]{{0.2F, 0.2F}, {0.8F, 0.8F}}
        );
        assertNear(0.2F, mask.sample(0.1, 0.0));
        assertNear(0.5F, mask.sample(0.625, 0.0));
        assertNear(0.8F, mask.sample(1.0, 0.0));
    }

    private static void supportsCenterSample() {
        AngularShadowMask mask = new AngularShadowMask(
                new double[]{0.0, 1.0},
                new float[][]{{0.25F, 0.25F}, {0.75F, 0.75F}}
        );
        assertNear(0.25F, mask.sample(0.0, 0.0));
        assertNear(0.5F, mask.sample(0.5, 0.0));
    }

    private static void rejectsInvalidLayouts() {
        try {
            new AngularShadowMask(new double[]{1.0}, new float[][]{{1.0F}});
            throw new AssertionError("expected invalid layout to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertNear(float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
