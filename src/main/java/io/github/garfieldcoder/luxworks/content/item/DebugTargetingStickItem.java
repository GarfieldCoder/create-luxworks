package io.github.garfieldcoder.luxworks.content.item;

import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Two-step development tool for binding a spotlight to a block target. */
public final class DebugTargetingStickItem extends Item {
    private static final String BOUND_SPOTLIGHT_TAG = "bound_spotlight";
    private static final double TARGETING_RANGE = 256.0;

    public DebugTargetingStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        if (context.getLevel().getBlockEntity(clickedPos) instanceof SpotlightBlockEntity) {
            CustomData.update(DataComponents.CUSTOM_DATA, context.getItemInHand(), tag ->
                    tag.putLong(BOUND_SPOTLIGHT_TAG, clickedPos.asLong())
            );
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.luxworks.targeting_stick_bound", formatPos(clickedPos)),
                        true
                );
            }
            return InteractionResult.SUCCESS;
        }

        return applyTarget(context.getLevel(), context.getPlayer(), context.getItemInHand(), clickedPos);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(TARGETING_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            applyTarget(level, player, stack, hit.getBlockPos());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static InteractionResult applyTarget(Level level, Player player, ItemStack stack, BlockPos clickedPos) {
        CompoundTag itemData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!itemData.contains(BOUND_SPOTLIGHT_TAG)) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.luxworks.targeting_stick_unbound"),
                        true
                );
            }
            return InteractionResult.FAIL;
        }

        BlockPos spotlightPos = BlockPos.of(itemData.getLong(BOUND_SPOTLIGHT_TAG));
        if (!(level.getBlockEntity(spotlightPos) instanceof SpotlightBlockEntity spotlight)) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.luxworks.targeting_stick_missing"),
                        true
                );
            }
            return InteractionResult.FAIL;
        }

        spotlight.setSavedTarget(clickedPos);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.luxworks.targeting_stick_target", formatPos(clickedPos)),
                    true
            );
        }
        return InteractionResult.SUCCESS;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
