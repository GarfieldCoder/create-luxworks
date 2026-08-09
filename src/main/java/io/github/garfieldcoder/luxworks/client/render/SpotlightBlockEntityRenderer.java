package io.github.garfieldcoder.luxworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.compat.veil.VeilDebugBeamRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlockEntities;

/**
 * Temporary articulated fixture built from ordinary block-model cuboids.
 *
 * <p>The yoke consumes yaw while the lamp consumes yaw and pitch. Both use
 * the same interpolated servo state as the beam renderer, keeping the visible
 * mechanism and optical direction together while the final art is absent.</p>
 */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class SpotlightBlockEntityRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    private final Minecraft minecraft = Minecraft.getInstance();

    public SpotlightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LuxworksBlockEntities.SPOTLIGHT.get(), SpotlightBlockEntityRenderer::new);
    }

    @Override
    public void render(
            SpotlightBlockEntity spotlight,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        long startedAt = System.nanoTime();
        Direction facing = spotlight.getBlockState().getValue(DebugLightBlock.FACING);
        var servo = spotlight.getInterpolatedServoState(partialTick);
        var lightState = spotlight.getLightState();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.25, 0.5);
        poseStack.mulPose(Axis.YP.rotation(yawForFacing(facing)));
        poseStack.mulPose(Axis.YP.rotationDegrees(-servo.currentYaw()));

        // Rotating pedestal and U-shaped yaw yoke.
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.0F, 0.02F, 0.0F, 0.42F, 0.10F, 0.42F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                -0.27F, 0.34F, 0.0F, 0.10F, 0.62F, 0.18F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.27F, 0.34F, 0.0F, 0.10F, 0.62F, 0.18F, packedLight);

        // The lamp pitches around the axle passing through the yoke arms.
        poseStack.translate(0.0, 0.34, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-servo.currentPitch()));
        renderCuboid(poseStack, buffers, Blocks.IRON_BLOCK.defaultBlockState(),
                0.0F, 0.0F, 0.03F, 0.42F, 0.42F, 0.58F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                0.0F, 0.0F, -0.28F, 0.46F, 0.46F, 0.08F, packedLight);
        renderCuboid(poseStack, buffers, Blocks.SEA_LANTERN.defaultBlockState(),
                0.0F, 0.0F, 0.34F, 0.34F, 0.34F, 0.06F, LightTexture.FULL_BRIGHT);
        boolean beamRendered = lightState.enabled()
                && lightState.intensity() > 0.0F
                && lightState.range() > 0.0F
                && VeilDebugBeamRenderer.renderLocal(poseStack, buffers, lightState);
        poseStack.popPose();

        LightRenderMetrics.record(
                System.nanoTime() - startedAt,
                beamRendered ? 1 : 0,
                beamRendered ? VeilDebugBeamRenderer.VERTEX_COUNT : 0
        );
    }

    private void renderCuboid(
            PoseStack poseStack,
            MultiBufferSource buffers,
            BlockState material,
            float centerX,
            float centerY,
            float centerZ,
            float sizeX,
            float sizeY,
            float sizeZ,
            int packedLight
    ) {
        poseStack.pushPose();
        poseStack.translate(centerX - sizeX * 0.5F, centerY - sizeY * 0.5F, centerZ - sizeZ * 0.5F);
        poseStack.scale(sizeX, sizeY, sizeZ);
        minecraft.getBlockRenderer().renderSingleBlock(
                material,
                poseStack,
                buffers,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static float yawForFacing(Direction facing) {
        return (float) Math.atan2(facing.getStepX(), facing.getStepZ());
    }
}
