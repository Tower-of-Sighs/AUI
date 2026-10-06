#version 150
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec4 Normal;
out vec3 localPosition;
out vec2 texCoord;
out vec3 tint;
out vec2 light;
out float opacity;
flat out int cutoff;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPosition = Position;
    texCoord = UV0;
    tint = Color.rgb;
    light = vec2(UV2.x & 15, UV2.y & 15);
    opacity = float((UV2.x >> 4) & 255) / 255.0;
    cutoff = (UV2.y >> 4) & 255;
}

