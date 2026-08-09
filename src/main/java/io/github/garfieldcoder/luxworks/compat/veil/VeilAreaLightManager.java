package io.github.garfieldcoder.luxworks.compat.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.compat.create.CreateSurfaceLightSafety;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the bounded, experimental Phase 1 Veil surface-light prototype. */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class VeilAreaLightManager {
    /**
     * Veil's deferred surface-light pass cannot safely shade geometry rendered outside Veil's
     * G-buffer, including the pinned Flywheel and Sable rendering paths. Keep it disabled for
     * normal play until Luxworks owns a receiver-aware masked lighting pass.
     */
    private static final boolean ENABLE_EXPERIMENTAL_SURFACE_LIGHTS =
            Boolean.getBoolean("luxworks.experimentalSurfaceLights");
    private static final Map<UUID, Entry> LIGHTS = new HashMap<>();

    private VeilAreaLightManager() {
    }

    public static void update(LightTransform transform, LightState state, long gameTime) {
        if (!ENABLE_EXPERIMENTAL_SURFACE_LIGHTS) {
            remove(state.id());
            return;
        }
        if (CreateSurfaceLightSafety.overlapsContraption(transform, state)) {
            remove(state.id());
            return;
        }
        Entry entry = LIGHTS.get(state.id());
        Snapshot snapshot = Snapshot.of(transform, state);
        if (entry == null || !entry.handle.isValid()) {
            AreaLightData data = new AreaLightData();
            apply(data, snapshot);
            LightRenderHandle<AreaLightData> handle = VeilRenderSystem.renderer()
                    .getLightRenderer()
                    .addLight(data);
            handle.markDirty();
            Luxworks.LOGGER.debug("Created Veil area light {}", state.id());
            LIGHTS.put(state.id(), new Entry(handle, snapshot, gameTime));
            return;
        }
        if (!entry.snapshot.equals(snapshot)) {
            apply(entry.handle.getLightData(), snapshot);
            entry.handle.markDirty();
        }
        LIGHTS.put(state.id(), new Entry(entry.handle, snapshot, gameTime));
    }

    public static void remove(UUID id) {
        Entry removed = LIGHTS.remove(id);
        if (removed != null) {
            removed.handle.free();
            Luxworks.LOGGER.debug("Removed Veil area light {}", id);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        LIGHTS.entrySet().removeIf(mapEntry -> {
            Entry entry = mapEntry.getValue();
            boolean expired = entry.lastSeenTick > gameTime || gameTime - entry.lastSeenTick > 20L;
            if (expired) {
                entry.handle.free();
                Luxworks.LOGGER.debug("Expired Veil area light {}", mapEntry.getKey());
            }
            return expired;
        });
    }

    private static void clear() {
        LIGHTS.values().forEach(entry -> entry.handle.free());
        LIGHTS.clear();
    }

    private static void apply(AreaLightData data, Snapshot snapshot) {
        data.getPosition().set(snapshot.x, snapshot.y, snapshot.z);
        data.getOrientation().set(snapshot.orientation);
        data.setSize(0.17, 0.17)
                .setAngle(snapshot.halfAngleRadians)
                .setDistance(snapshot.range)
                .setOcclusionEnabled(true)
                .setColor(snapshot.red, snapshot.green, snapshot.blue)
                .setBrightness(snapshot.brightness);
        data.markDirty();
    }

    private record Entry(
            LightRenderHandle<AreaLightData> handle,
            Snapshot snapshot,
            long lastSeenTick
    ) {
    }

    private record Snapshot(
            double x,
            double y,
            double z,
            Quaternionf orientation,
            float halfAngleRadians,
            float range,
            float red,
            float green,
            float blue,
            float brightness
    ) {
        private static Snapshot of(LightTransform transform, LightState state) {
            var forward = transform.forward();
            var position = transform.worldPosition().add(forward.scale(0.40));
            Quaternionf orientation = new Quaternionf().rotationTo(
                    (float) forward.x,
                    (float) forward.y,
                    (float) forward.z,
                    0.0F,
                    0.0F,
                    1.0F
            );
            return new Snapshot(
                    position.x,
                    position.y,
                    position.z,
                    orientation,
                    (float) Math.toRadians(state.outerAngleDegrees() * 0.5),
                    Math.min(state.range(), 32.0F),
                    state.red(),
                    state.green(),
                    state.blue(),
                    Math.max(0.0F, state.intensity())
            );
        }
    }
}
