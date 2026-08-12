#include veil:space_helper

layout(location = 0) in vec3 Position;

uniform vec3 LightOrigin;
uniform vec3 LightDirection;

out vec3 proxyPositionVS;
flat out vec3 lightOriginVS;
flat out vec3 lightDirectionVS;

void main() {
    // Call the verified Veil helpers directly. Veil's function-like wrapper
    // macros did not expand correctly in the pinned 4.1.4 shader preprocessor.
    proxyPositionVS = worldToViewSpace(vec4(Position, 1.0)).xyz;
    lightOriginVS = worldToViewSpace(vec4(LightOrigin, 1.0)).xyz;
    vec3 lightForwardPointVS = worldToViewSpace(
        vec4(LightOrigin + LightDirection, 1.0)
    ).xyz;
    lightDirectionVS = normalize(lightForwardPointVS - lightOriginVS);
    gl_Position = worldToClipSpace(vec4(Position, 1.0));
}
