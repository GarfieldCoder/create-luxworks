package io.github.garfieldcoder.luxworks.registry;

import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.content.blockentity.SpotlightBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LuxworksBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Luxworks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpotlightBlockEntity>> SPOTLIGHT =
            BLOCK_ENTITY_TYPES.register(
                    "spotlight",
                    () -> BlockEntityType.Builder.of(SpotlightBlockEntity::new, LuxworksBlocks.DEBUG_LIGHT.get())
                            .build(null)
            );

    private LuxworksBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
