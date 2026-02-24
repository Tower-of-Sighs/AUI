#version 150

uniform sampler2D Sampler0;
uniform vec2 InSize;
uniform float BlurRadius;
uniform float Brightness;
uniform float Grayscale;
uniform float Invert;
uniform float HueRotate;

in vec2 texCoord;
out vec4 fragColor;

vec3 applyHue(vec3 color, float angle) {
    float h = angle * 0.01745329251;
    vec3 k = vec3(0.57735);
    float cosAngle = cos(h);
    return color * cosAngle + cross(k, color) * sin(h) + k * dot(k, color) * (1.0 - cosAngle);
}

void main() {
    vec4 rawColor = texture(Sampler0, texCoord);
    if (rawColor.a <= 0.001) discard;

    vec4 color;
    if (BlurRadius > 0.5) {
        ivec2 texSize = textureSize(Sampler0, 0);
        vec2 texelSize = vec2(1.0 / texSize.x, 1.0 / texSize.y);

        int radius = int(BlurRadius);
        float sigma = float(radius) / 3.0;
        float twoSigmaSq = 2.0 * sigma * sigma;

        vec3 blurSumLinear = vec3(0.0);
        float totalWeight = 0.0;

        // 二维高斯采样循环
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                vec2 offset = vec2(x, y) * texelSize;
                vec3 sampleColor = texture(Sampler0, texCoord + offset).rgb;

                // 转换到线性空间进行混合以获得更佳效果
                vec3 linearColor = pow(sampleColor, vec3(2.2));

                float distSq = float(x*x + y*y);
                float weight = exp(-distSq / twoSigmaSq);

                blurSumLinear += linearColor * weight;
                totalWeight += weight;
            }
        }
        // 归一化并转换回 sRGB 空间
        color.rgb = pow(blurSumLinear / totalWeight, vec3(1.0/2.2));
        color.a = rawColor.a;
    } else {
        color = rawColor;
    }

    // 亮度、灰度、反色、色相旋转
    color.rgb *= Brightness;
    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(gray), Grayscale);
    color.rgb = mix(color.rgb, 1.0 - color.rgb, Invert);

    if (abs(HueRotate) > 0.1) {
        color.rgb = applyHue(color.rgb, HueRotate);
    }

    fragColor = vec4(color.rgb, rawColor.a);
}