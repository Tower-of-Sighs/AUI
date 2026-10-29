#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    // mask-mode: luminance 的 dst-in 合成：混合态是 zero/srcalpha（见
    // pipeline/filter_mask_lum），mask 值取预乘 rgb 的亮度（= 颜色亮度 × alpha）。
    vec4 tex = texture(Sampler0, texCoord0);
    fragColor = vec4(0.0, 0.0, 0.0, dot(tex.rgb, vec3(0.2126, 0.7152, 0.0722)));
}
