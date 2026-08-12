package io.github.garfieldcoder.luxworks.compat.dh;

import io.github.garfieldcoder.luxworks.Luxworks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Entry point for optional Distant Horizons integration. All classes that
 * reference DH API types live behind this presence check so the base mod
 * never classloads them (and therefore never crashes) when DH is absent.
 */
@EventBusSubscriber(modid = Luxworks.MOD_ID, value = Dist.CLIENT)
public final class DistantHorizonsCompat {
    private static final String DH_MOD_ID = "distanthorizons";

    private DistantHorizonsCompat() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded(DH_MOD_ID)) {
            return;
        }
        event.enqueueWork(DistantHorizonsShadowPassGuard::register);
    }
}
