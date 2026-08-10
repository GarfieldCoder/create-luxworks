package io.github.garfieldcoder.luxworks.client;

import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.client.screen.SpotlightControlScreen;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client-only interception that opens the non-container spotlight panel. */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class SpotlightClientInteraction {
    private SpotlightClientInteraction() {
    }

    @SubscribeEvent
    public static void openControls(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().isEmpty()
                && !event.getEntity().isShiftKeyDown()
                && event.getLevel().getBlockState(event.getPos()).is(LuxworksBlocks.DEBUG_LIGHT)
                && event.getLevel().getBlockEntity(event.getPos()) instanceof SpotlightBlockEntity spotlight) {
            var servo = spotlight.getServoState();
            var light = spotlight.getLightState();
            Minecraft.getInstance().setScreen(new SpotlightControlScreen(
                    event.getPos(),
                    servo.targetYaw(),
                    servo.targetPitch(),
                    light.red(),
                    light.green(),
                    light.blue(),
                    light.intensity(),
                    light.range(),
                    light.innerAngleDegrees(),
                    light.outerAngleDegrees(),
                    spotlight.getSavedTarget()
            ));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
