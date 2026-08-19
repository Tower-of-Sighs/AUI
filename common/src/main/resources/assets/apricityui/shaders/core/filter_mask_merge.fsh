#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // mask-composite 的 merge blit 透传 shader：Porter-Duff 算子完全由
    // filter_mask_intersect/subtract/exclude.json 的混合态表达，这里原样输出
    // scratch FBO 的预乘颜色与 alpha。
    fragColor = texture(Sampler0, texCoord);
}
