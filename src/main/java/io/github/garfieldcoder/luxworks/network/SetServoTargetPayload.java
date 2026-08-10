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
public record SetServoTargetPayload(
        BlockPos blockPos,
        float yaw,
        float pitch,
        float red,
        float green,
        float blue,
        float intensity,
        float range,
        float innerAngle,
        float outerAngle
)
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
                        buffer.writeFloat(payload.red);
                        buffer.writeFloat(payload.green);
                        buffer.writeFloat(payload.blue);
                        buffer.writeFloat(payload.intensity);
                        buffer.writeFloat(payload.range);
                        buffer.writeFloat(payload.innerAngle);
                        buffer.writeFloat(payload.outerAngle);
                    },
                    buffer -> new SetServoTargetPayload(
                            buffer.readBlockPos(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetServoTargetPayload payload, IPayloadContext context) {
        if (!Float.isFinite(payload.yaw) || !Float.isFinite(payload.pitch)
                || !Float.isFinite(payload.red) || !Float.isFinite(payload.green) || !Float.isFinite(payload.blue)) {
            return;
        }
        if (!Float.isFinite(payload.intensity) || !Float.isFinite(payload.range)
                || !Float.isFinite(payload.innerAngle) || !Float.isFinite(payload.outerAngle)) {
            return;
        }
        if (context.player().distanceToSqr(Vec3.atCenterOf(payload.blockPos)) > 64.0) {
            return;
        }
        if (context.player().level().getBlockEntity(payload.blockPos) instanceof SpotlightBlockEntity spotlight) {
            spotlight.setControls(
                    payload.yaw,
                    payload.pitch,
                    payload.red,
                    payload.green,
                    payload.blue,
                    payload.intensity,
                    payload.range,
                    payload.innerAngle,
                    payload.outerAngle
            );
        }
    }
}
