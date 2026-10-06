#version 330
uniform sampler2D Sampler0;
layout(std140) uniform MapParams { vec4 Region; };
in vec3 localPosition;
in vec2 texCoord;
in vec3 tint;
in vec2 light;
in float opacity;
flat in int cutoff;
out vec4 fragColor;
void main() {
    ivec2 cell = ivec2(clamp(floor(localPosition.xz / 16.0), vec2(0.0), vec2(1.0)));
    if ((int(Region.x) & (1 << (cell.x + cell.y * 2))) == 0) discard;
    vec4 pixel = texture(Sampler0, texCoord) * vec4(tint, opacity);
    float threshold = cutoff == 128 ? 0.5 : cutoff == 26 ? 0.1 : 0.0;
    if (pixel.a < threshold) discard;
    float level = mix(light.x, max(light.y, light.x), Region.y);
    fragColor = vec4(pixel.rgb * (level / 15.0), pixel.a);
}
