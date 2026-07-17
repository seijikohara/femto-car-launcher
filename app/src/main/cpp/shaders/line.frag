#version 450

layout(location = 0) in vec3 vColor;
layout(location = 1) in float vDistFrac;
layout(location = 2) in float vViewDepth;

layout(push_constant) uniform Push {
    mat4 mvp;
    float progress;
    float time;
    float aspect;
    float pad;
} pc;

layout(location = 0) out vec4 outColor;

void main() {
    // Draw-on: everything ahead of the playhead is not yet revealed. Chrome
    // (grid) carries distFrac = -1, so it is always < progress and stays lit.
    if (vDistFrac > pc.progress) {
        discard;
    }

    // Comet head: a bright band just behind the playhead reads as "now".
    float head = smoothstep(pc.progress - 0.035, pc.progress, vDistFrac);
    vec3 col = mix(vColor, vec3(1.0), head * 0.9);

    // Trail: the drawn tail dims slightly with age so the head leads the eye.
    // Chrome (distFrac < 0) is unaffected — its band never overlaps the tail.
    float trail = vDistFrac < 0.0 ? 1.0 : (0.5 + 0.5 * smoothstep(pc.progress - 0.55, pc.progress, vDistFrac));

    // Depth fog: fade distant geometry toward the black background so the
    // wireframe has real depth. The additive blend makes "toward black" a fade.
    float fog = clamp(1.0 - (vViewDepth - 2.0) / 5.0, 0.12, 1.0);

    outColor = vec4(col * trail * fog, 1.0);
}
