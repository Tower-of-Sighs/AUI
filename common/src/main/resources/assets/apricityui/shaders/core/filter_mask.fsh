#version 150

uniform sampler2D Sampler0;

uniform float MaskLuminance;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // CSS mask 的 dst-in 合成：混合态是 zero/srcalpha（见 filter_mask.json），
    // 目标像素 rgb/a 统一乘以 mask 值；源 rgb 被 zero 因子丢弃，输出什么无所谓。
    // mask-mode: luminance 时 mask 值取预乘 rgb 的亮度（= 颜色亮度 × alpha），
    // 否则取 alpha 通道。
    vec4 tex = texture(Sampler0, texCoord);
    float maskValue = MaskLuminance > 0.5
            ? dot(tex.rgb, vec3(0.2126, 0.7152, 0.0722))
            : tex.a;
    fragColor = vec4(0.0, 0.0, 0.0, maskValue);
}
