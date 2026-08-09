package io.github.garfieldcoder.luxworks.client.screen;

import io.github.garfieldcoder.luxworks.network.SetServoTargetPayload;
import io.github.garfieldcoder.luxworks.network.ClearSavedTargetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** First exact manual control surface for a spotlight servo. */
public final class SpotlightControlScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private final BlockPos blockPos;
    private final float initialYaw;
    private final float initialPitch;
    private final BlockPos savedTarget;
    private EditBox yawField;
    private EditBox pitchField;
    private Component validationMessage = Component.empty();

    public SpotlightControlScreen(BlockPos blockPos, float initialYaw, float initialPitch, BlockPos savedTarget) {
        super(Component.translatable("screen.luxworks.spotlight_controls"));
        this.blockPos = blockPos;
        this.initialYaw = initialYaw;
        this.initialPitch = initialPitch;
        this.savedTarget = savedTarget;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 82;

        yawField = new EditBox(font, left + 96, top + 24, 104, 20, Component.translatable("screen.luxworks.yaw"));
        yawField.setValue(formatAngle(initialYaw));
        pitchField = new EditBox(font, left + 96, top + 52, 104, 20, Component.translatable("screen.luxworks.pitch"));
        pitchField.setValue(formatAngle(initialPitch));
        addRenderableWidget(yawField);
        addRenderableWidget(pitchField);

        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.apply"), button -> apply())
                .bounds(left + 10, top + 112, 62, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.reset"), button -> reset())
                .bounds(left + 79, top + 112, 62, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.luxworks.clear_target"), button -> clearTarget())
                .bounds(left + 148, top + 112, 62, 20)
                .build());
        setInitialFocus(yawField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 82;
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.luxworks.yaw"), left + 20, top + 30, 0xD8D8D8);
        graphics.drawString(font, Component.translatable("screen.luxworks.pitch"), left + 20, top + 58, 0xD8D8D8);
        graphics.drawCenteredString(font, Component.translatable("screen.luxworks.angle_limits"), width / 2, top + 79, 0xA8A8A8);
        Component targetText = savedTarget == null
                ? Component.translatable("screen.luxworks.no_saved_target")
                : Component.translatable(
                        "screen.luxworks.saved_target",
                        savedTarget.getX(), savedTarget.getY(), savedTarget.getZ()
                );
        graphics.drawCenteredString(font, targetText, width / 2, top + 94, 0xD8D8D8);
        graphics.drawCenteredString(font, validationMessage, width / 2, top + 140, 0xFF7777);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void apply() {
        try {
            float yaw = Float.parseFloat(yawField.getValue().trim());
            float pitch = Float.parseFloat(pitchField.getValue().trim());
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new NumberFormatException();
            }
            PacketDistributor.sendToServer(new SetServoTargetPayload(blockPos, yaw, pitch));
            onClose();
        } catch (NumberFormatException ignored) {
            validationMessage = Component.translatable("screen.luxworks.invalid_angle");
        }
    }

    private void reset() {
        PacketDistributor.sendToServer(new SetServoTargetPayload(blockPos, 0.0F, 0.0F));
        onClose();
    }

    private void clearTarget() {
        PacketDistributor.sendToServer(new ClearSavedTargetPayload(blockPos));
        onClose();
    }

    private static String formatAngle(float angle) {
        return Float.toString(Math.round(angle * 10.0F) / 10.0F);
    }
}
