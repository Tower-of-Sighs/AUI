#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(std140) uniform FilterParams {
    vec4 FilterScalars;
    vec4 FilterOpacity;
    vec4 ShadowOffsetUv;
    vec4 GuiInSize;
    vec4 ShadowColorValue;
    vec4 ClipRectValue;
    vec4 ClipRadiiValue;
    vec4 BlurDirection;
    vec4 FilterColorMatrix;
};

#define Direction BlurDirection.xy
#define Radius FilterOpacity.w

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    float sigma = max(0.5, Radius / 3.0);
    float divisor = 2.0 * sigma * sigma;
    vec4 sum = vec4(0.0);
    float total = 0.0;

    // A fixed upper bound is friendly to GLSL 1.50 drivers. Radius is kept
    // below this limit by adaptive downsampling on the CPU.
    for (int i = -32; i <= 32; i++) {
        if (abs(float(i)) > Radius) continue;
        float weight = exp(-float(i * i) / divisor);
        sum += texture(Sampler0, texCoord0 + Direction * float(i)) * weight;
        total += weight;
    }
    fragColor = total > 0.0 ? sum / total : texture(Sampler0, texCoord0);
}
