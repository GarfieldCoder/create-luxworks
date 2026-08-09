package io.github.garfieldcoder.luxworks;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Main entry point for Create: Luxworks.
 */
@Mod(Luxworks.MOD_ID)
public final class Luxworks {
    public static final String MOD_ID = "luxworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Luxworks(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Create: Luxworks");
    }
}
