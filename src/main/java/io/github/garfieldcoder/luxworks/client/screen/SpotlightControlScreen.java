package io.github.garfieldcoder.luxworks.client.screen;

import io.github.garfieldcoder.luxworks.network.ClearSavedTargetPayload;
import io.github.garfieldcoder.luxworks.network.SetServoTargetPayload;
import io.github.garfieldcoder.luxworks.light.LightState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.DoubleConsumer;

/** Exact text entry paired with quick sliders for spotlight controls. */
public final class SpotlightControlScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int SLIDER_X = 62;
    private static final int SLIDER_WIDTH = 176;
    private static final int FIELD_X = 246;
    private static final int FIELD_WIDTH = 74;

    private final BlockPos blockPos;
    private final float initialYaw;
    private final float initialPitch;
    private final float initialRed;
    private final float initialGreen;
    private final float initialBlue;
    private final float initialIntensity;
    private final float initialRange;
    private final float initialInnerAngle;
    private final float initialOuterAngle;
    private final BlockPos savedTarget;
    private PairedValueControl yawControl;
    private PairedValueControl pitchControl;
    private PairedValueControl redControl;
    private PairedValueControl greenControl;
    private PairedValueControl blueControl;
    private PairedValueControl intensityControl;
    private PairedValueControl rangeControl;
    private PairedValueControl innerAngleControl;
    private PairedValueControl outerAngleControl;
    private Component validationMessage = Component.empty();

    public SpotlightControlScreen(
            BlockPos blockPos,
            float initialYaw,
            float initialPitch,
            float initialRed,
            float initialGreen,
            float initialBlue,
            float initialIntensity,
            float initialRange,
            float initialInnerAngle,
            float initialOuterAngle,
            BlockPos savedTarget
    ) {
        super(Component.translatable("screen.luxworks.spotlight_controls"));
        this.blockPos = blockPos;
        this.initialYaw = initialYaw;
        this.initialPitch = initialPitch;
        this.initialRed = initialRed;
        this.initialGreen = initialGreen;
        this.initialBlue = initialBlue;
        this.initialIntensity = initialIntensity;
        this.initialRange = initialRange;
        this.initialInnerAngle = initialInnerAngle;
        this.initialOuterAngle = initialOuterAngle;
        this.savedTarget = savedTarget;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 154;

        yawControl = addControl(left, top + 24, initialYaw, -180.0, 180.0, 1);
        pitchControl = addControl(left, top + 48, initialPitch, -90.0, 90.0, 1);
        innerAngleControl = addControl(
                left, top + 72, initialInnerAngle, 0.0, LightState.MAX_CONE_ANGLE_DEGREES, 1
        );
        outerAngleControl = addControl(
                left, top + 96, initialOuterAngle, 0.0, LightState.MAX_CONE_ANGLE_DEGREES, 1
        );
        redControl = addControl(left, top + 128, initialRed, 0.0, 1.0, 3);
        greenControl = addControl(left, top + 152, initialGreen, 0.0, 1.0, 3);
        blueControl = addControl(left, top + 176, initialBlue, 0.0, 1.0, 3);
        intensityControl = addControl(left, top + 200, initialIntensity, 0.0, 4.0, 2);
        rangeControl = addControl(left, top + 224, initialRange, 0.0, 64.0, 1);

        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.apply"), button -> apply())
                .bounds(left + 45, top + 270, 75, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.reset"), button -> reset())
                .bounds(left + 128, top + 270, 75, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.clear_target"), button -> clearTarget())
                .bounds(left + 211, top + 270, 75, 20)
                .build());
        setInitialFocus(yawControl.field());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 154;
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        drawLabel(graphics, "screen.luxworks.yaw", left, top + 30, 0xD8D8D8);
        drawLabel(graphics, "screen.luxworks.pitch", left, top + 54, 0xD8D8D8);
        drawLabel(graphics, "screen.luxworks.inner_angle", left, top + 78, 0xD8D8D8);
        drawLabel(graphics, "screen.luxworks.outer_angle", left, top + 102, 0xD8D8D8);
        drawLabel(graphics, "screen.luxworks.red", left, top + 134, 0xFF7777);
        drawLabel(graphics, "screen.luxworks.green", left, top + 158, 0x77FF77);
        drawLabel(graphics, "screen.luxworks.blue", left, top + 182, 0x7777FF);
        drawLabel(graphics, "screen.luxworks.intensity", left, top + 206, 0xD8D8D8);
        drawLabel(graphics, "screen.luxworks.range", left, top + 230, 0xD8D8D8);
        Component targetText = savedTarget == null
                ? Component.translatable("screen.luxworks.no_saved_target")
                : Component.translatable(
                        "screen.luxworks.saved_target",
                        savedTarget.getX(), savedTarget.getY(), savedTarget.getZ()
                );
        graphics.drawCenteredString(font, targetText, width / 2, top + 254, 0xD8D8D8);
        graphics.drawCenteredString(font, validationMessage, width / 2, top + 296, 0xFF7777);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PairedValueControl addControl(
            int left,
            int y,
            double initialValue,
            double minimum,
            double maximum,
            int decimalPlaces
    ) {
        PairedValueControl control = new PairedValueControl(
                left + SLIDER_X,
                y,
                SLIDER_WIDTH,
                left + FIELD_X,
                FIELD_WIDTH,
                initialValue,
                minimum,
                maximum,
                decimalPlaces
        );
        addRenderableWidget(control.slider());
        addRenderableWidget(control.field());
        return control;
    }

    private void drawLabel(GuiGraphics graphics, String key, int left, int y, int color) {
        graphics.drawString(font, Component.translatable(key), left + 10, y, color);
    }

    private void apply() {
        try {
            float yaw = yawControl.readValue();
            float pitch = pitchControl.readValue();
            float red = redControl.readValue();
            float green = greenControl.readValue();
            float blue = blueControl.readValue();
            float intensity = intensityControl.readValue();
            float range = rangeControl.readValue();
            float innerAngle = innerAngleControl.readValue();
            float outerAngle = outerAngleControl.readValue();
            if (innerAngle > outerAngle) {
                throw new NumberFormatException();
            }
            PacketDistributor.sendToServer(new SetServoTargetPayload(
                    blockPos, yaw, pitch, red, green, blue,
                    intensity, range, innerAngle, outerAngle
            ));
            onClose();
        } catch (NumberFormatException ignored) {
            validationMessage = Component.translatable("screen.luxworks.invalid_controls");
        }
    }

    private void reset() {
        PacketDistributor.sendToServer(new SetServoTargetPayload(
                blockPos, 0.0F, 0.0F, initialRed, initialGreen, initialBlue,
                initialIntensity, initialRange, initialInnerAngle, initialOuterAngle
        ));
        onClose();
    }

    private void clearTarget() {
        PacketDistributor.sendToServer(new ClearSavedTargetPayload(blockPos));
        onClose();
    }

    private final class PairedValueControl {
        private final EditBox field;
        private final ValueSlider slider;
        private final double minimum;
        private final double maximum;
        private final int decimalPlaces;
        private boolean synchronizing;

        private PairedValueControl(
                int sliderX,
                int y,
                int sliderWidth,
                int fieldX,
                int fieldWidth,
                double initialValue,
                double minimum,
                double maximum,
                int decimalPlaces
        ) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.decimalPlaces = decimalPlaces;
            double clampedInitial = Math.clamp(initialValue, minimum, maximum);
            field = new EditBox(font, fieldX, y, fieldWidth, 20, Component.empty());
            slider = new ValueSlider(
                    sliderX,
                    y,
                    sliderWidth,
                    normalized(clampedInitial),
                    normalizedValue -> updateField(actual(normalizedValue))
            );
            field.setValue(format(clampedInitial));
            field.setResponder(this::updateSliderFromField);
        }

        private EditBox field() {
            return field;
        }

        private ValueSlider slider() {
            return slider;
        }

        private float readValue() {
            double value = Double.parseDouble(field.getValue().trim());
            if (!Double.isFinite(value) || value < minimum || value > maximum) {
                throw new NumberFormatException();
            }
            return (float) value;
        }

        private void updateField(double value) {
            if (synchronizing) {
                return;
            }
            synchronizing = true;
            field.setValue(format(value));
            synchronizing = false;
        }

        private void updateSliderFromField(String text) {
            if (synchronizing) {
                return;
            }
            try {
                double value = Double.parseDouble(text.trim());
                if (Double.isFinite(value) && value >= minimum && value <= maximum) {
                    synchronizing = true;
                    slider.setNormalizedValue(normalized(value));
                    synchronizing = false;
                }
            } catch (NumberFormatException ignored) {
                // Partial text such as "-" remains editable and is validated on Apply.
            }
        }

        private double normalized(double actualValue) {
            return (actualValue - minimum) / (maximum - minimum);
        }

        private double actual(double normalizedValue) {
            return minimum + normalizedValue * (maximum - minimum);
        }

        private String format(double value) {
            double scale = Math.pow(10.0, decimalPlaces);
            return Double.toString(Math.round(value * scale) / scale);
        }
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final DoubleConsumer onChanged;

        private ValueSlider(int x, int y, int width, double value, DoubleConsumer onChanged) {
            super(x, y, width, 20, Component.empty(), value);
            this.onChanged = onChanged;
            updateMessage();
        }

        private void setNormalizedValue(double value) {
            this.value = Math.clamp(value, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty());
        }

        @Override
        protected void applyValue() {
            onChanged.accept(value);
        }
    }
}
