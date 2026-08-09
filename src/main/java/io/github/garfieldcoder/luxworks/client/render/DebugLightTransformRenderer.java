package io.github.garfieldcoder.luxworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTransformResolver;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
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
        LightTransform transform = SableLightTransformResolver.resolve(
                minecraft.level,
                blockPos,
                facing,
                event.getPartialTick().getGameTimeDeltaPartialTick(false)
        );
        drawDirectionLine(event, transform);
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
