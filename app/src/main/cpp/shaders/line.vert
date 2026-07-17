#version 450

// One vertex of a GL_LINES list: position, speed colour, and the cumulative
// distance fraction that drives the draw-on reveal (-1 for always-on chrome
// such as the ground grid).
layout(location = 0) in vec3 inPos;
layout(location = 1) in vec3 inColor;
layout(location = 2) in float inDistFrac;

layout(push_constant) uniform Push {
    mat4 mvp;
    float progress;   // draw-on playhead in [0, 1]
    float time;       // seconds, for the head shimmer
    float aspect;
    float pad;
    vec4 head;        // rgb = comet-head mix target (theme-dependent); a unused
} pc;

layout(location = 0) out vec3 vColor;
layout(location = 1) out float vDistFrac;
layout(location = 2) out float vViewDepth;

void main() {
    vec4 clip = pc.mvp * vec4(inPos, 1.0);
    gl_Position = clip;
    vColor = inColor;
    vDistFrac = inDistFrac;
    // Perspective w is the view-space forward distance; used for depth fog.
    vViewDepth = clip.w;
}
