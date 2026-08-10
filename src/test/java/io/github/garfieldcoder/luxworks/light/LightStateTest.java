package io.github.garfieldcoder.luxworks.light;

import java.util.UUID;

public final class LightStateTest {
    private static final UUID ID = UUID.fromString("d1bfa8d7-9f6e-4e4b-bcad-cb01567c1b6e");

    private LightStateTest() {
    }

    public static void main(String[] args) {
        defaultsAreUsableAndStable();
        unsafeNumericValuesAreSanitized();
        colorChannelsAreNormalized();
        normalizedChannelsPackIntoRgb();
        coneAnglesStayWithinRenderableRange();
    }

    private static void normalizedChannelsPackIntoRgb() {
        assertEquals(0xFF8000, LightState.rgbFromNormalized(1.0F, 0.5F, 0.0F));
        assertEquals(0x00FFFF, LightState.rgbFromNormalized(-1.0F, 2.0F, 1.0F));
    }

    private static void coneAnglesStayWithinRenderableRange() {
        LightState state = new LightState(ID, true, 0xFFFFFF, 1.0F, 16.0F, 140.0F, 170.0F);

        assertEquals(45.0F, state.innerAngleDegrees());
        assertEquals(45.0F, state.outerAngleDegrees());
    }

    private static void defaultsAreUsableAndStable() {
        LightState state = LightState.defaults(ID);

        assertEquals(ID, state.id());
        check(state.enabled(), "default light should be enabled");
        assertEquals(0xFFDE78, state.colorRgb());
        assertEquals(1.0F, state.intensity());
        assertEquals(16.0F, state.range());
        assertEquals(12.0F, state.innerAngleDegrees());
        assertEquals(18.0F, state.outerAngleDegrees());
    }

    private static void unsafeNumericValuesAreSanitized() {
        LightState state = new LightState(
                ID,
                true,
                0x12ABCDEF,
                -2.0F,
                Float.NaN,
                80.0F,
                30.0F
        );

        assertEquals(0xABCDEF, state.colorRgb());
        assertEquals(0.0F, state.intensity());
        assertEquals(0.0F, state.range());
        assertEquals(30.0F, state.innerAngleDegrees());
        assertEquals(30.0F, state.outerAngleDegrees());
    }

    private static void colorChannelsAreNormalized() {
        LightState state = new LightState(ID, true, 0x804020, 1.0F, 1.0F, 1.0F, 2.0F);

        assertEquals(128.0F / 255.0F, state.red());
        assertEquals(64.0F / 255.0F, state.green());
        assertEquals(32.0F / 255.0F, state.blue());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertEquals(float expected, float actual) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
