package io.github.garfieldcoder.luxworks.content.blockentity;

import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlockEntities;
import io.github.garfieldcoder.luxworks.servo.ServoState;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoTarget;
import io.github.garfieldcoder.luxworks.compat.sable.SableLightTargetResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-owned persistent settings for a spotlight fixture.
 */
public final class SpotlightBlockEntity extends BlockEntity {
    private static final String STATE_TAG = "light_state";
    private static final String ID_TAG = "id";
    private static final String ENABLED_TAG = "enabled";
    private static final String COLOR_TAG = "color_rgb";
    private static final String INTENSITY_TAG = "intensity";
    private static final String RANGE_TAG = "range";
    private static final String INNER_ANGLE_TAG = "inner_angle";
    private static final String OUTER_ANGLE_TAG = "outer_angle";
    private static final String SERVO_TAG = "servo_state";
    private static final String CURRENT_YAW_TAG = "current_yaw";
    private static final String CURRENT_PITCH_TAG = "current_pitch";
    private static final String TARGET_YAW_TAG = "target_yaw";
    private static final String TARGET_PITCH_TAG = "target_pitch";
    private static final String MAX_VELOCITY_TAG = "max_angular_velocity";
    private static final String SAVED_TARGET_TAG = "saved_target";
    private static final float SECONDS_PER_TICK = 1.0F / 20.0F;

    private LightState lightState = LightState.defaults(UUID.randomUUID());
    private ServoState servoState = ServoState.defaults();
    private ServoState previousServoState = servoState;
    private BlockPos savedTarget;

    public SpotlightBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(LuxworksBlockEntities.SPOTLIGHT.get(), blockPos, blockState);
    }

    public LightState getLightState() {
        return lightState;
    }

    public void setLightState(LightState requestedState) {
        lightState = new LightState(
                lightState.id(),
                requestedState.enabled(),
                requestedState.colorRgb(),
                requestedState.intensity(),
                requestedState.range(),
                requestedState.innerAngleDegrees(),
                requestedState.outerAngleDegrees()
        );
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ServoState getServoState() {
        return servoState;
    }

    public ServoState getInterpolatedServoState(float partialTick) {
        return servoState.interpolateFrom(previousServoState, partialTick);
    }

    public void setServoTarget(float yaw, float pitch) {
        savedTarget = null;
        servoState = servoState.withTarget(yaw, pitch);
        markChangedAndSync();
    }

    public void setSavedTarget(BlockPos target) {
        savedTarget = target.immutable();
        updateServoTargetFromSavedTarget();
        markChangedAndSync();
    }

    public void clearSavedTarget() {
        savedTarget = null;
        markChangedAndSync();
    }

    @Nullable
    public BlockPos getSavedTarget() {
        return savedTarget;
    }

    public static void tick(Level level, SpotlightBlockEntity spotlight) {
        if (spotlight.savedTarget != null) {
            spotlight.updateServoTargetFromSavedTarget();
        }
        spotlight.previousServoState = spotlight.servoState;
        ServoState advanced = spotlight.servoState.advance(SECONDS_PER_TICK);
        if (advanced.equals(spotlight.servoState)) {
            return;
        }
        spotlight.servoState = advanced;
        if (!level.isClientSide) {
            spotlight.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STATE_TAG, writeState(lightState));
        tag.put(SERVO_TAG, writeServoState(servoState));
        if (savedTarget != null) {
            tag.putLong(SAVED_TARGET_TAG, savedTarget.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(STATE_TAG, CompoundTag.TAG_COMPOUND)) {
            lightState = readState(tag.getCompound(STATE_TAG), lightState.id());
        }
        if (tag.contains(SERVO_TAG, CompoundTag.TAG_COMPOUND)) {
            servoState = readServoState(tag.getCompound(SERVO_TAG));
            previousServoState = servoState;
        }
        savedTarget = tag.contains(SAVED_TARGET_TAG) ? BlockPos.of(tag.getLong(SAVED_TARGET_TAG)) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static CompoundTag writeState(LightState state) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_TAG, state.id());
        tag.putBoolean(ENABLED_TAG, state.enabled());
        tag.putInt(COLOR_TAG, state.colorRgb());
        tag.putFloat(INTENSITY_TAG, state.intensity());
        tag.putFloat(RANGE_TAG, state.range());
        tag.putFloat(INNER_ANGLE_TAG, state.innerAngleDegrees());
        tag.putFloat(OUTER_ANGLE_TAG, state.outerAngleDegrees());
        return tag;
    }

    private static LightState readState(CompoundTag tag, UUID fallbackId) {
        LightState defaults = LightState.defaults(fallbackId);
        return new LightState(
                tag.hasUUID(ID_TAG) ? tag.getUUID(ID_TAG) : fallbackId,
                tag.contains(ENABLED_TAG) ? tag.getBoolean(ENABLED_TAG) : defaults.enabled(),
                tag.contains(COLOR_TAG) ? tag.getInt(COLOR_TAG) : defaults.colorRgb(),
                tag.contains(INTENSITY_TAG) ? tag.getFloat(INTENSITY_TAG) : defaults.intensity(),
                tag.contains(RANGE_TAG) ? tag.getFloat(RANGE_TAG) : defaults.range(),
                tag.contains(INNER_ANGLE_TAG) ? tag.getFloat(INNER_ANGLE_TAG) : defaults.innerAngleDegrees(),
                tag.contains(OUTER_ANGLE_TAG) ? tag.getFloat(OUTER_ANGLE_TAG) : defaults.outerAngleDegrees()
        );
    }

    private static CompoundTag writeServoState(ServoState state) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(CURRENT_YAW_TAG, state.currentYaw());
        tag.putFloat(CURRENT_PITCH_TAG, state.currentPitch());
        tag.putFloat(TARGET_YAW_TAG, state.targetYaw());
        tag.putFloat(TARGET_PITCH_TAG, state.targetPitch());
        tag.putFloat(MAX_VELOCITY_TAG, state.maxAngularVelocity());
        return tag;
    }

    private static ServoState readServoState(CompoundTag tag) {
        ServoState defaults = ServoState.defaults();
        return new ServoState(
                tag.contains(CURRENT_YAW_TAG) ? tag.getFloat(CURRENT_YAW_TAG) : defaults.currentYaw(),
                tag.contains(CURRENT_PITCH_TAG) ? tag.getFloat(CURRENT_PITCH_TAG) : defaults.currentPitch(),
                tag.contains(TARGET_YAW_TAG) ? tag.getFloat(TARGET_YAW_TAG) : defaults.targetYaw(),
                tag.contains(TARGET_PITCH_TAG) ? tag.getFloat(TARGET_PITCH_TAG) : defaults.targetPitch(),
                tag.contains(MAX_VELOCITY_TAG) ? tag.getFloat(MAX_VELOCITY_TAG) : defaults.maxAngularVelocity()
        );
    }

    private void markChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void updateServoTargetFromSavedTarget() {
        if (level == null || savedTarget == null) {
            return;
        }
        Vec3 localTarget = SableLightTargetResolver.resolveWorldPositionInFixtureFrame(
                level,
                worldPosition,
                Vec3.atCenterOf(savedTarget)
        );
        ServoTarget target = ServoDirectionResolver.targetForDirection(
                getBlockState().getValue(io.github.garfieldcoder.luxworks.content.block.DebugLightBlock.FACING),
                localTarget.subtract(Vec3.atCenterOf(worldPosition))
        );
        servoState = servoState.withTarget(target.yaw(), target.pitch());
    }
}
