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

/** Server-bound request to change a nearby spotlight's servo target. */
public record SetServoTargetPayload(BlockPos blockPos, float yaw, float pitch)
        implements CustomPacketPayload {
    public static final Type<SetServoTargetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "set_servo_target")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetServoTargetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.blockPos);
                        buffer.writeFloat(payload.yaw);
                        buffer.writeFloat(payload.pitch);
                    },
                    buffer -> new SetServoTargetPayload(
                            buffer.readBlockPos(),
                            buffer.readFloat(),
                            buffer.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetServoTargetPayload payload, IPayloadContext context) {
        if (!Float.isFinite(payload.yaw) || !Float.isFinite(payload.pitch)) {
            return;
        }
        if (context.player().distanceToSqr(Vec3.atCenterOf(payload.blockPos)) > 64.0) {
            return;
        }
        if (context.player().level().getBlockEntity(payload.blockPos) instanceof SpotlightBlockEntity spotlight) {
            spotlight.setServoTarget(payload.yaw, payload.pitch);
        }
    }
}
