#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    // Keep the PIP texture's premultiplied color and alpha unchanged. The
    // destination is cleared and the copy pipeline has no blend function.
    fragColor = texture(Sampler0, texCoord0);
}
