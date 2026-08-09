package io.github.garfieldcoder.luxworks.network;

import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-bound request to stop tracking while preserving the current aim. */
public record ClearSavedTargetPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<ClearSavedTargetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "clear_saved_target")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearSavedTargetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.blockPos),
                    buffer -> new ClearSavedTargetPayload(buffer.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearSavedTargetPayload payload, IPayloadContext context) {
        if (context.player().distanceToSqr(Vec3.atCenterOf(payload.blockPos)) > 64.0) {
            return;
        }
        if (context.player().level().getBlockEntity(payload.blockPos) instanceof SpotlightBlockEntity spotlight) {
            spotlight.clearSavedTarget();
        }
    }
}
