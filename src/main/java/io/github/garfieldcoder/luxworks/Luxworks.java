package io.github.garfieldcoder.luxworks;

import com.mojang.logging.LogUtils;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlockEntities;
import io.github.garfieldcoder.luxworks.network.LuxworksNetworking;
import io.github.garfieldcoder.luxworks.compat.create.CreateCompat;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Main entry point for Create: Luxworks.
 */
@Mod(Luxworks.MOD_ID)
public final class Luxworks {
    public static final String MOD_ID = "luxworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Luxworks(IEventBus modEventBus, ModContainer modContainer) {
        LuxworksBlocks.register(modEventBus);
        LuxworksBlockEntities.register(modEventBus);
        modEventBus.addListener(Luxworks::addCreativeTabContents);
        modEventBus.addListener(LuxworksNetworking::register);
        modEventBus.addListener(Luxworks::commonSetup);
        LOGGER.info("Initializing Create: Luxworks");
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LuxworksBlocks.DEBUG_LIGHT_ITEM);
            event.accept(LuxworksBlocks.DEBUG_TARGETING_STICK);
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CreateCompat::register);
    }

}
