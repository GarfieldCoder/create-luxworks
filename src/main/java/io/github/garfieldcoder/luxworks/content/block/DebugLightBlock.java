package io.github.garfieldcoder.luxworks.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoTarget;
import net.minecraft.world.phys.Vec3;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTargetResolver;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Phase 0 placement fixture used to validate static and moving transforms.
 * It intentionally has no gameplay light behavior yet.
 */
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;

public final class DebugLightBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DebugLightBlock> CODEC = simpleCodec(DebugLightBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DebugLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new SpotlightBlockEntity(blockPos, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState blockState,
            Level level,
            BlockPos blockPos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(blockPos) instanceof SpotlightBlockEntity spotlight) {
            ServoTarget target = ServoDirectionResolver.targetForDirection(
                    blockState.getValue(FACING),
                    SableLightTargetResolver.resolveEntityEyeInFixtureFrame(level, blockPos, player)
                            .subtract(Vec3.atCenterOf(blockPos))
            );
            spotlight.setServoTarget(target.yaw(), target.pitch());
            player.displayClientMessage(Component.translatable("message.luxworks.aim_set"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> blockEntityType
    ) {
        if (blockEntityType != io.github.garfieldcoder.luxworks.registry.LuxworksBlockEntities.SPOTLIGHT.get()) {
            return null;
        }
        return (tickerLevel, blockPos, state, blockEntity) -> {
            if (blockEntity instanceof SpotlightBlockEntity spotlight) {
                SpotlightBlockEntity.tick(tickerLevel, spotlight);
            }
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

}
