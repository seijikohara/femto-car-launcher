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
    vec4 head;        // rgb = comet-head mix target (white on dark, ink on light)
} pc;

layout(location = 0) out vec4 outColor;

void main() {
    // Draw-on: everything ahead of the playhead is not yet revealed. Chrome
    // (grid) carries distFrac = -1, so it is always < progress and stays lit.
    if (vDistFrac > pc.progress) {
        discard;
    }

    // Comet head: a bright band just behind the playhead reads as "now". The mix
    // target is theme-dependent (white on the dark scene, ink on the light one).
    float nowBand = smoothstep(pc.progress - 0.035, pc.progress, vDistFrac);
    vec3 col = mix(vColor, pc.head.rgb, nowBand * 0.9);

    // Trail: the drawn tail dims slightly with age so the head leads the eye.
    // Chrome (distFrac < 0) is unaffected — its band never overlaps the tail.
    float trail = vDistFrac < 0.0 ? 1.0 : (0.5 + 0.5 * smoothstep(pc.progress - 0.55, pc.progress, vDistFrac));

    // Depth fog fades distant geometry into the backdrop. It is applied as a
    // coverage term (not a colour multiply) so it fades toward whichever backdrop
    // the active pipeline blends against — additive-onto-black on the dark scene,
    // alpha-over-onto-light on the light scene.
    float fog = clamp(1.0 - (vViewDepth - 2.0) / 5.0, 0.12, 1.0);

    // Premultiplied-alpha output: rgb is scaled by coverage, alpha is coverage.
    // The dark pipeline blends src=ONE / dst=ONE (additive glow); the light
    // pipeline blends src=ONE / dst=ONE_MINUS_SRC_ALPHA (over). Both consume this
    // one premultiplied colour, so the scene is drawn once and blended two ways.
    float cov = trail * fog;
    outColor = vec4(col * cov, cov);
}
