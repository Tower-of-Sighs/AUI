#version 150

uniform sampler2D Sampler0;
uniform vec2 Direction;
uniform float Radius;

in vec2 texCoord;
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
        sum += texture(Sampler0, texCoord + Direction * float(i)) * weight;
        total += weight;
    }
    fragColor = total > 0.0 ? sum / total : texture(Sampler0, texCoord);
}
