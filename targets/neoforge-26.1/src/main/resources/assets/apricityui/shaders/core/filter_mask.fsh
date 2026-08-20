#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    // CSS mask 的 dst-in 合成：pipeline 烘焙 zero/src-alpha 混合，
    // 目标像素 rgb/a 统一乘以 mask 的 alpha；源 rgb 被 zero 因子丢弃。
    fragColor = vec4(0.0, 0.0, 0.0, texture(Sampler0, texCoord0).a);
}
