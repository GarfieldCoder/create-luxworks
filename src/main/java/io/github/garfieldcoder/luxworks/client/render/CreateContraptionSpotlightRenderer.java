package io.github.garfieldcoder.luxworks.client.render;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.compat.veil.VeilDebugBeamRenderer;
import io.github.garfieldcoder.luxworks.compat.veil.VeilAreaLightManager;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
import io.github.garfieldcoder.luxworks.servo.ServoDirectionResolver;
import io.github.garfieldcoder.luxworks.servo.ServoState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Centralized Create contraption beam pass, independent of Flywheel actor visuals. */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class CreateContraptionSpotlightRenderer {
    private static final String LIGHT_STATE_TAG = "light_state";
    private static final String SERVO_STATE_TAG = "servo_state";
    private static final UUID FALLBACK_ID = new UUID(0L, 0L);
    /**
     * Veil's deferred area-light pass currently exposes missing/interior faces on
     * Flywheel-rendered Create contraptions. Keep it available for focused
     * diagnostics, but never enable the incompatible path by default.
     */
    private static final boolean ENABLE_EXPERIMENTAL_CREATE_SURFACE_LIGHT =
            Boolean.getBoolean("luxworks.experimentalCreateSurfaceLights");
    private static final Map<CacheKey, CachedOcclusion> OCCLUSION_CACHE = new HashMap<>();

    private CreateContraptionSpotlightRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            OCCLUSION_CACHE.clear();
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = minecraft.level.getGameTime();
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractContraptionEntity contraptionEntity)
                    || !contraptionEntity.isReadyForRender()) {
                continue;
            }
            for (var actor : contraptionEntity.getContraption().getActors()) {
                MovementContext context = actor.getRight();
                if (context == null || !context.state.is(LuxworksBlocks.DEBUG_LIGHT)
                        || context.blockEntityData == null) {
                    continue;
                }
                renderSpotlight(event, contraptionEntity, context, partialTick, gameTime);
            }
        }

        OCCLUSION_CACHE.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenTick > 20L);
    }

    private static void renderSpotlight(
            RenderLevelStageEvent event,
            AbstractContraptionEntity entity,
            MovementContext context,
            float partialTick,
            long gameTime
    ) {
        long startedAt = System.nanoTime();
        CompoundTag data = context.blockEntityData;
        LightState light = data.contains(LIGHT_STATE_TAG, CompoundTag.TAG_COMPOUND)
                ? SpotlightBlockEntity.readState(data.getCompound(LIGHT_STATE_TAG), FALLBACK_ID)
                : LightState.defaults(FALLBACK_ID);
        if (!light.enabled() || light.intensity() <= 0.0F || light.range() <= 0.0F) {
            VeilAreaLightManager.remove(light.id());
            return;
        }
        ServoState servo = data.contains(SERVO_STATE_TAG, CompoundTag.TAG_COMPOUND)
                ? SpotlightBlockEntity.readServoState(data.getCompound(SERVO_STATE_TAG))
                : ServoState.defaults();
        Vec3 localForward = ServoDirectionResolver.resolve(
                context.state.getValue(DebugLightBlock.FACING),
                servo
        );
        Vec3 worldForward = entity.applyRotation(localForward, partialTick).normalize();
        Vec3 localPosition = Vec3.atCenterOf(context.localPos);
        Vec3 previousPosition = entity.toGlobalVector(localPosition, partialTick, true);
        Vec3 currentPosition = entity.toGlobalVector(localPosition, partialTick, false);
        Vec3 worldPosition = previousPosition.lerp(currentPosition, partialTick);
        if (ENABLE_EXPERIMENTAL_CREATE_SURFACE_LIGHT) {
            Quaternionf worldRotation = new Quaternionf().rotationTo(
                    0.0F,
                    0.0F,
                    1.0F,
                    (float) worldForward.x,
                    (float) worldForward.y,
                    (float) worldForward.z
            );
            VeilAreaLightManager.update(
                    new LightTransform(worldPosition, worldRotation, worldForward),
                    light,
                    gameTime
            );
        } else {
            VeilAreaLightManager.remove(light.id());
        }
        Vec3 rayStart = worldPosition.add(worldForward.scale(0.60));
        double range = Math.min(light.range(), 64.0);

        CacheKey key = new CacheKey(entity.getUUID(), context.localPos.asLong());
        CachedOcclusion cached = OCCLUSION_CACHE.get(key);
        if (cached == null) {
            BeamOcclusionProfile sampled = BeamOcclusionSampler.sampleWorld(
                    Minecraft.getInstance().level,
                    rayStart,
                    worldForward,
                    range,
                    light.outerAngleDegrees()
            );
            cached = new CachedOcclusion(gameTime, gameTime, sampled, sampled);
            OCCLUSION_CACHE.put(key, cached);
        } else if (cached.sampledAtTick != gameTime) {
            BeamOcclusionProfile sampled = BeamOcclusionSampler.sampleWorld(
                    Minecraft.getInstance().level,
                    rayStart,
                    worldForward,
                    range,
                    light.outerAngleDegrees()
            );
            cached = new CachedOcclusion(
                    gameTime,
                    gameTime,
                    cached.currentProfile,
                    sampled
            );
            OCCLUSION_CACHE.put(key, cached);
        } else {
            cached = new CachedOcclusion(
                    cached.sampledAtTick,
                    gameTime,
                    cached.previousProfile,
                    cached.currentProfile
            );
            OCCLUSION_CACHE.put(key, cached);
        }
        BeamOcclusionProfile visibleProfile = BeamOcclusionProfile.interpolate(
                cached.previousProfile,
                cached.currentProfile,
                partialTick
        );
        boolean beamRendered = VeilDebugBeamRenderer.renderWorldOccluded(
                event.getPoseStack(),
                light,
                worldPosition,
                worldForward,
                event.getCamera().getPosition(),
                visibleProfile
        );
        LightRenderMetrics.record(
                System.nanoTime() - startedAt,
                beamRendered ? 1 : 0,
                beamRendered ? VeilDebugBeamRenderer.VERTEX_COUNT : 0
        );
    }

    private record CacheKey(UUID entityId, long localPosition) {
    }

    private record CachedOcclusion(
            long sampledAtTick,
            long lastSeenTick,
            BeamOcclusionProfile previousProfile,
            BeamOcclusionProfile currentProfile
    ) {
    }
}
