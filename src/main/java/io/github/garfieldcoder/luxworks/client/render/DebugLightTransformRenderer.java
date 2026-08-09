package io.github.garfieldcoder.luxworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTransformResolver;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.UUID;

/**
 * Phase 0 visualization for the resolved transform of the targeted debug light.
 */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class DebugLightTransformRenderer {
    private static final double LINE_LENGTH = 3.0;

    private DebugLightTransformRenderer() {
    }

    @SubscribeEvent
    public static void renderResolvedDirection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }

        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = minecraft.level.getBlockState(blockPos);
        if (!blockState.is(LuxworksBlocks.DEBUG_LIGHT)) {
            return;
        }

        Direction facing = blockState.getValue(DebugLightBlock.FACING);
        LightState lightState = resolveLightState(minecraft, blockPos);
        if (!lightState.enabled() || lightState.intensity() <= 0.0F || lightState.range() <= 0.0F) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ServoState servoState = resolveServoState(minecraft, blockPos, partialTick);
        Vec3 localForward = ServoDirectionResolver.resolve(facing, servoState);
        LightTransform transform = SableLightTransformResolver.resolve(
                minecraft.level,
                blockPos,
                localForward,
                partialTick
        );
        drawDirectionLine(event, transform);
    }

    private static LightState resolveLightState(Minecraft minecraft, BlockPos blockPos) {
        if (minecraft.level.getBlockEntity(blockPos) instanceof SpotlightBlockEntity spotlight) {
            return spotlight.getLightState();
        }

        // Existing Phase 0 worlds may contain debug lights created before the
        // block gained a block entity. Keep those fixtures visible until they
        // are replaced and receive persistent state.
        return LightState.defaults(new UUID(0L, blockPos.asLong()));
    }

    private static ServoState resolveServoState(Minecraft minecraft, BlockPos blockPos, float partialTick) {
        if (minecraft.level.getBlockEntity(blockPos) instanceof SpotlightBlockEntity spotlight) {
            return spotlight.getInterpolatedServoState(partialTick);
        }
        return ServoState.defaults();
    }

    private static void drawDirectionLine(RenderLevelStageEvent event, LightTransform transform) {
        Vec3 start = transform.worldPosition().subtract(event.getCamera().getPosition());
        Vec3 end = start.add(transform.forward().scale(LINE_LENGTH));

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        addLineVertex(lines, pose, start, transform.forward());
        addLineVertex(lines, pose, end, transform.forward());
        buffers.endBatch(RenderType.lines());
    }

    private static void addLineVertex(VertexConsumer lines, PoseStack.Pose pose, Vec3 position, Vec3 normal) {
        lines.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(255, 214, 64, 255)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
