package io.github.garfieldcoder.luxworks.registry;

import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.content.block.DebugLightBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LuxworksBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Luxworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Luxworks.MOD_ID);

    public static final DeferredBlock<DebugLightBlock> DEBUG_LIGHT = BLOCKS.registerBlock(
            "debug_light",
            DebugLightBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> DEBUG_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(
            "debug_light",
            DEBUG_LIGHT,
            new Item.Properties()
    );

    private LuxworksBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
