package io.github.garfieldcoder.luxworks.compat.veil;

import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import io.github.garfieldcoder.luxworks.Luxworks;
import io.github.garfieldcoder.luxworks.light.LightState;
import io.github.garfieldcoder.luxworks.light.LightTransform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Owns a single cached light-space depth render (a real shadow map) used to
 * occlude the depth-volume spotlight beam against actual world geometry,
 * including cutout shapes such as fences, leaves, and glass panes that
 * Veil's coarse voxel shadow grid could not distinguish from air.
 *
 * <p>Scope is static main-level world geometry only: this renders through
 * {@link VeilLevelPerspectiveRenderer#render}, which drives the real
 * {@code LevelRenderer.renderLevel}, so it naturally covers vanilla's solid
 * and cutout block layers. Create contraptions and Sable sub-levels keep
 * their own independent CPU raycast occlusion resolvers and are untouched
 * here.</p>
 *
 * <p>Only one shadow map is kept warm at a time, matching the single named
 * framebuffer this class registers. If more than one fixture is in
 * depth-diagnostic mode simultaneously they share (and thrash) this one map;
 * that is an accepted Phase 1 limitation for what remains an opt-in
 * diagnostic path.</p>
 */
public final class SpotlightShadowMap {
    private static final ResourceLocation SHADOW_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(Luxworks.MOD_ID, "spotlight_shadow_map");
    /** Square resolution of the cached shadow depth render. Tune as needed. */
    private static final int SHADOW_MAP_SIZE = 512;
    private static final float NEAR_PLANE = 0.05F;
    /**
     * Generous margin added on top of the outer cone's full apex angle so
     * the whole beam stays inside the shadow frustum even given any
     * residual uncertainty in the exact perspective-camera handedness
     * convention, plus the small forward offset between this camera's
     * origin and the cone's visual apex. Can be tightened later once the
     * beam is confirmed to look correct in-game.
     */
    private static final float FOV_MARGIN_DEGREES = 6.0F;
    private static final float MAX_FOV_DEGREES = 170.0F;
    /**
     * Cadence of "the world may have changed" refreshes while the light
     * itself is stationary. Block placement/destruction in the beam shows up
     * within this many ticks. Light movement always refreshes immediately
     * (at most once per tick). A full nested level render 20x/s proved heavy
     * enough to destabilize at least one GPU driver when the light faced
     * dense terrain, so the idle cadence is deliberately slower.
     */
    private static final long IDLE_REFRESH_INTERVAL_TICKS = 4L;
    private static final double POSITION_EPSILON_SQ = 1.0E-6;
    private static final double DIRECTION_EPSILON_SQ = 1.0E-8;
    private static final double ANGLE_EPSILON = 1.0E-3;
    private static final double RANGE_EPSILON = 1.0E-3;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector3d LIGHT_ORIGIN_D = new Vector3d();
    private static final Quaternionf ORIENTATION = new Quaternionf();
    private static final Vector3f FORWARD_JOML = new Vector3f();
    private static final Vector3f UP_JOML = new Vector3f();

    private static boolean hasRendered;
    private static UUID lastLightId;
    private static Vec3 lastPosition = Vec3.ZERO;
    private static Vec3 lastForward = Vec3.ZERO;
    private static double lastRange;
    private static float lastOuterAngle;
    private static float lastFarPlane = 1.0F;
    private static long lastRenderedGameTime = Long.MIN_VALUE;
    private static boolean traced;

    private SpotlightShadowMap() {
    }

    /**
     * Ensures the cached shadow map is fresh for the given light, then
     * returns the light-space view-projection matrix to upload as the
     * depth-volume shader's {@code LightViewProjection} uniform.
     *
     * <p>Refresh policy: a moving or re-aiming light re-renders immediately
     * (throttled to once per game tick); a stationary light still refreshes
     * every {@link #IDLE_REFRESH_INTERVAL_TICKS} ticks, because world
     * geometry can change under it (blocks placed or destroyed in the beam)
     * and there is no cheap way to detect that from here. A pose-based
     * dirty check alone made the shadow freeze on whatever the world looked
     * like when the light last moved.</p>
     *
     * <p>Returns the previously cached matrix untouched (and renders
     * nothing) if a Veil perspective render is already in progress, to avoid
     * recursively re-entering the shadow-map render from inside itself.</p>
     */
    public static Matrix4fc ensure(
            LightTransform transform,
            LightState lightState,
            double range,
            DeltaTracker deltaTracker,
            long gameTime
    ) {
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            return VIEW_PROJECTION;
        }

        Vec3 forward = transform.forward();
        Vec3 lightOrigin = transform.worldPosition().add(forward.scale(0.34));
        boolean poseDirty = !hasRendered
                || !lightState.id().equals(lastLightId)
                || lightOrigin.distanceToSqr(lastPosition) > POSITION_EPSILON_SQ
                || forward.distanceToSqr(lastForward) > DIRECTION_EPSILON_SQ
                || Math.abs(range - lastRange) > RANGE_EPSILON
                || Math.abs(lightState.outerAngleDegrees() - lastOuterAngle) > ANGLE_EPSILON;
        boolean idleRefreshDue = gameTime - lastRenderedGameTime >= IDLE_REFRESH_INTERVAL_TICKS
                || gameTime < lastRenderedGameTime;

        if (hasRendered && gameTime == lastRenderedGameTime) {
            return VIEW_PROJECTION;
        }
        if (hasRendered && !poseDirty && !idleRefreshDue) {
            return VIEW_PROJECTION;
        }

        renderShadowMap(lightOrigin, forward, lightState, range, deltaTracker);

        lastLightId = lightState.id();
        lastPosition = lightOrigin;
        lastForward = forward;
        lastRange = range;
        lastOuterAngle = lightState.outerAngleDegrees();
        lastRenderedGameTime = gameTime;
        hasRendered = true;
        return VIEW_PROJECTION;
    }

    /** Near plane of the light-space projection, for depth linearization. */
    public static float nearPlane() {
        return NEAR_PLANE;
    }

    /** Far plane used by the most recent shadow render. */
    public static float farPlane() {
        return lastFarPlane;
    }

    private static void renderShadowMap(
            Vec3 lightOrigin,
            Vec3 forward,
            LightState lightState,
            double range,
            DeltaTracker deltaTracker
    ) {
        AdvancedFbo fbo = ensureFramebuffer();

        Vec3 upReference = Math.abs(forward.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        FORWARD_JOML.set((float) forward.x, (float) forward.y, (float) forward.z);
        UP_JOML.set((float) upReference.x, (float) upReference.y, (float) upReference.z);
        // Matches the calling convention verified against Veil 4.1.4 call
        // sites (e.g. Sable's sky-light shadow map and the Veil example
        // mod's mirror renderer): lookAlong(forward, up) produces the
        // cameraOrientation VeilLevelPerspectiveRenderer expects directly,
        // with no extra inversion.
        ORIENTATION.identity().lookAlong(FORWARD_JOML, UP_JOML);

        // outerAngleDegrees is already the cone's full apex angle (see
        // outerSlope = tan(outerAngleDegrees * 0.5) in VeilDebugBeamRenderer),
        // and JOML's perspective() fovy is likewise a full vertical angle,
        // so no extra doubling belongs here; doubling it previously made the
        // shadow frustum ~2x wider than the cone, wasting half the map's
        // texel density right at the fence/leaf edges it exists to sharpen.
        float fovDegrees = Math.min(lightState.outerAngleDegrees() + FOV_MARGIN_DEGREES, MAX_FOV_DEGREES);
        float far = (float) Math.max(range, NEAR_PLANE + 0.1);
        lastFarPlane = far;
        PROJECTION.identity().perspective((float) Math.toRadians(fovDegrees), 1.0F, NEAR_PLANE, far);

        LIGHT_ORIGIN_D.set(lightOrigin.x, lightOrigin.y, lightOrigin.z);
        float renderDistanceChunks = (float) Math.max(2.0, Math.ceil(far / 16.0) + 1.0);

        // Guarantee a depth = 1.0 (nothing) baseline before every render.
        // Repeated per-tick re-renders must never inherit stale depth from
        // the previous tick's geometry; Sable's sky-light shadow pass does
        // the same explicit clear before its perspective render.
        fbo.bind(false);
        fbo.clear();

        VeilLevelPerspectiveRenderer.render(
                fbo,
                VIEW.identity(),
                PROJECTION,
                LIGHT_ORIGIN_D,
                ORIENTATION,
                renderDistanceChunks,
                deltaTracker,
                false
        );

        // Veil's perspective renderer composes the final modelview as
        // (identity) * rotation(orientation) applied to camera-relative
        // (i.e. lightOrigin-relative) positions, exactly like a standard
        // shadow-map view matrix: LightViewProjection = Projection *
        // Rotation(orientation) * Translate(-lightOrigin).
        VIEW.rotation(ORIENTATION).translate(
                (float) -lightOrigin.x, (float) -lightOrigin.y, (float) -lightOrigin.z
        );
        PROJECTION.mul(VIEW, VIEW_PROJECTION);

        // Restore the real main framebuffer AND its viewport. Veil's
        // perspective renderer calls AdvancedFbo.unbind() while the window is
        // still resized to the shadow map's dimensions, so the GL viewport is
        // left at SHADOW_MAP_SIZE^2 when render() returns. Re-binding with
        // setViewport=true is mandatory here: with bind(false), the beam draw
        // that follows rasterized into a 512x512 corner of the screen, and
        // later render stages on the same frame inherited the bad viewport.
        AdvancedFbo.getMainFramebuffer().bind(true);

        if (!traced) {
            traced = true;
            Luxworks.LOGGER.info(
                    "Spotlight shadow-map trace: fbo={}x{}, fov={} deg, near={}, far={}, "
                            + "renderDistanceChunks={}, origin=({}, {}, {})",
                    fbo.getWidth(), fbo.getHeight(), fovDegrees, NEAR_PLANE, far,
                    renderDistanceChunks, lightOrigin.x, lightOrigin.y, lightOrigin.z
            );
        }
    }

    private static AdvancedFbo ensureFramebuffer() {
        FramebufferManager framebuffers = VeilRenderSystem.renderer().getFramebufferManager();
        AdvancedFbo existing = framebuffers.getFramebuffer(SHADOW_MAP_ID);
        if (existing != null
                && existing.getWidth() == SHADOW_MAP_SIZE
                && existing.getHeight() == SHADOW_MAP_SIZE
                && existing.isDepthTextureAttachment()
                && existing.isColorTextureAttachment(0)) {
            return existing;
        }
        if (existing != null) {
            framebuffers.removeFramebuffer(SHADOW_MAP_ID);
            existing.free();
        }

        // The color attachment must be a TEXTURE, not a renderbuffer: during
        // the nested perspective render Veil wraps the main render target
        // with this FBO, and mods that ask the main target for its color
        // texture id mid-pass (e.g. Distant Horizons' fade renderer) throw
        // "Color attachment 0 must be a texture attachment" on a
        // renderbuffer, spamming the log once per shadow refresh.
        AdvancedFbo created = AdvancedFbo.withSize(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
                .addColorTextureBuffer()
                .setDepthTextureBuffer()
                .setDebugLabel("Luxworks Spotlight Shadow Map")
                .build(true);
        framebuffers.setFramebuffer(SHADOW_MAP_ID, created);
        return created;
    }
}
