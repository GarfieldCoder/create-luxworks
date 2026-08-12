#include veil:space_helper

in vec3 proxyPositionVS;
flat in vec3 lightOriginVS;
flat in vec3 lightDirectionVS;

uniform sampler2D DepthSampler;
uniform sampler2D ShadowMap;
uniform mat4 LightViewProjection;
uniform vec2 ShadowNearFar;
uniform vec2 ScreenSize;
uniform float LightRange;
uniform float InnerSlope;
uniform float OuterSlope;
uniform float LightDensity;
uniform vec3 LightColor;
uniform vec3 LightOrigin;

out vec4 fragColor;

// Per-pixel jitter decorrelates sample positions between neighboring pixels,
// so fewer jittered steps look smoother than more aligned ones while costing
// fewer voxel traversals.
const int STEPS = 16;
// Bias in world units (blocks). The comparison below is done on linearized
// distances, so this stays a constant ~tenth of a block everywhere along
// the beam. A constant bias in raw hardware-depth space does NOT work here:
// with a 0.05 near plane, perspective depth compresses the entire beam into
// the last ~1% of the [0,1] depth range, so even a "small" 0.0015 raw bias
// equated to multiple full blocks of peter-panning at mid range, which
// showed up in-game as shadows offset/smeared along the beam axis.
const float SHADOW_BIAS_BLOCKS = 0.1;

// Compares a world-space ray-march sample against the cached spotlight
// shadow map instead of Veil's coarse voxel occlusion grid, so cutout
// shapes (fences, leaves, glass panes) occlude the beam correctly. Samples
// outside the shadow frustum are treated as unshadowed: the frustum is sized
// to comfortably cover the whole cone, so falling outside it should only
// happen at the extreme edge of the volume.
float spotlightShadowVisibility(vec3 samplePositionWS) {
    vec4 lightClip = LightViewProjection * vec4(samplePositionWS, 1.0);
    if (lightClip.w <= 0.0) {
        return 1.0;
    }
    vec3 lightNdc = lightClip.xyz / lightClip.w;
    vec2 shadowUv = lightNdc.xy * 0.5 + 0.5;
    if (any(lessThan(shadowUv, vec2(0.0))) || any(greaterThan(shadowUv, vec2(1.0)))) {
        return 1.0;
    }
    float storedDepth = texture(ShadowMap, shadowUv).r;
    // Linearize the stored hardware depth back to a view-space distance.
    // An empty texel (depth = 1.0) linearizes exactly to the far plane, so
    // beam samples out to full range still count as visible.
    float near = ShadowNearFar.x;
    float far = ShadowNearFar.y;
    float storedNdcZ = storedDepth * 2.0 - 1.0;
    float storedDistance = 2.0 * near * far / (far + near - storedNdcZ * (far - near));
    // For a perspective projection, clip.w is already the sample's own
    // view-space distance from the light; no second linearization needed.
    return lightClip.w - SHADOW_BIAS_BLOCKS <= storedDistance ? 1.0 : 0.0;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;
    float sceneDepth = texture(DepthSampler, screenUv).r;
    vec3 scenePositionVS = screenToViewSpace(screenUv, sceneDepth).xyz;
    vec3 rayDirection = normalize(screenToViewSpace(screenUv, 1.0).xyz);

    // Intersect the camera ray with a sphere enclosing the finite cone. This
    // supplies a stable entry/exit interval even when the camera is outside,
    // inside, or very close to the spotlight volume.
    float halfRange = LightRange * 0.5;
    vec3 volumeCenterVS = lightOriginVS + lightDirectionVS * halfRange;
    float endRadius = OuterSlope * LightRange;
    float volumeRadius = sqrt(halfRange * halfRange + endRadius * endRadius);
    vec3 cameraToCenter = -volumeCenterVS;
    float projectedCenter = dot(cameraToCenter, rayDirection);
    float discriminant = projectedCenter * projectedCenter
        - (dot(cameraToCenter, cameraToCenter) - volumeRadius * volumeRadius);
    if (discriminant <= 0.0) {
        discard;
    }

    float root = sqrt(discriminant);
    float rayStart = max(0.0, -projectedCenter - root);
    float rayEnd = min(-projectedCenter + root, length(scenePositionVS) - 0.01);
    if (rayEnd <= rayStart) {
        discard;
    }

    float stepLength = (rayEnd - rayStart) / float(STEPS);
    float accumulated = 0.0;
    // Interleaved gradient noise; hides banding from the low step count.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));

    for (int stepIndex = 0; stepIndex < STEPS; stepIndex++) {
        float distanceAlongRay = rayStart + (float(stepIndex) + jitter) * stepLength;
        vec3 samplePosition = rayDirection * distanceAlongRay;
        vec3 fromLight = samplePosition - lightOriginVS;
        float axial = dot(fromLight, lightDirectionVS);
        if (axial <= 0.0 || axial >= LightRange) {
            continue;
        }

        vec3 radialVector = fromLight - lightDirectionVS * axial;
        float radialSlope = length(radialVector) / max(axial, 0.0001);
        float angular = 1.0 - smoothstep(InnerSlope, OuterSlope, radialSlope);
        if (angular <= 0.001) {
            continue;
        }
        float longitudinal = 1.0 - smoothstep(LightRange * 0.72, LightRange, axial);
        // Query occlusion for every accepted sample: reusing the previous
        // sample's result smears shadow boundaries by a whole step.
        vec3 samplePositionWS = viewToWorldSpace(vec4(samplePosition, 1.0)).xyz;
        float visibility = spotlightShadowVisibility(samplePositionWS);
        if (visibility <= 0.0) {
            continue;
        }
        accumulated += angular * longitudinal * visibility * stepLength;
        if (accumulated * LightDensity >= 4.0) {
            break;
        }
    }

    float opacity = 1.0 - exp(-accumulated * LightDensity);
    if (opacity < 0.002) {
        discard;
    }
    // Cyan remains unmistakable while this production path is opt-in.
    fragColor = vec4(vec3(0.0, 1.0, 1.0) * opacity, opacity);
}
