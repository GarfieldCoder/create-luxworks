package io.github.garfieldcoder.luxworks.compat.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import io.github.garfieldcoder.luxworks.Luxworks;

/**
 * Keeps Distant Horizons out of Luxworks' nested Veil perspective renders
 * (currently the spotlight shadow-map pass in
 * {@code io.github.garfieldcoder.luxworks.compat.veil.SpotlightShadowMap}).
 *
 * <p>DH hooks the level renderer directly, so without this guard every
 * shadow refresh re-drew DH's entire LOD terrain plus its fade
 * post-processing into a 512x512 depth target that only needs nearby
 * full-detail chunks. At up to 20 shadow refreshes per second that
 * multiplied whole-world LOD rendering many times per frame - useless work
 * for the shadow map (LODs beyond the beam's far plane can never occlude
 * it) and heavy enough to trip GPU driver watchdog resets. Iris cancels DH
 * the same way during its own shadow passes, so this is the
 * upstream-sanctioned mechanism, not a workaround.</p>
 *
 * <p>Only classloaded when DH is present; see {@link DistantHorizonsCompat}.</p>
 */
final class DistantHorizonsShadowPassGuard {
    private DistantHorizonsShadowPassGuard() {
    }

    static void register() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new CancelLodRenderInShadowPass());
        DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, new CancelShaderRenderInShadowPass());
        Luxworks.LOGGER.info(
                "Registered Distant Horizons render guards; DH will skip Luxworks shadow-map passes"
        );
    }

    /** Cancels DH's main LOD terrain render inside a Veil perspective pass. */
    public static final class CancelLodRenderInShadowPass extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
                event.cancelEvent();
            }
        }
    }

    /**
     * Cancels DH's shader-based post passes (fade renderers etc.) inside a
     * Veil perspective pass. These fire separately from the main LOD render
     * and were the source of the per-refresh fade-render work observed in
     * the logs, so they need their own cancellation.
     */
    public static final class CancelShaderRenderInShadowPass extends DhApiBeforeApplyShaderRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
                event.cancelEvent();
            }
        }
    }
}
