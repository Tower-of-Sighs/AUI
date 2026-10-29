#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    // Keep the PIP texture's premultiplied color and alpha unchanged. The
    // destination is cleared and the copy pipeline has no blend function.
    fragColor = texture(Sampler0, texCoord0);
}
