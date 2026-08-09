package io.github.garfieldcoder.luxworks.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import io.github.garfieldcoder.luxworks.registry.LuxworksBlocks;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.world.level.Level;

/** Registration boundary for the pinned Create contraption API. */
public final class CreateCompat {
    private CreateCompat() {
    }

    public static void register() {
        MovementBehaviour.REGISTRY.register(
                LuxworksBlocks.DEBUG_LIGHT.get(),
                new CreateSpotlightMovementBehaviour()
        );
    }

    /** True while Create is rendering a captured block entity inside a contraption. */
    public static boolean isVirtualContraptionLevel(Level level) {
        return level instanceof VirtualRenderWorld;
    }
}
