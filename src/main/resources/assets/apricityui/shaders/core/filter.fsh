#version 150

uniform sampler2D Sampler0;
uniform vec2 InSize;
uniform float BlurRadius;
uniform float Brightness;
uniform float Grayscale;
uniform float Invert;
uniform float HueRotate;
uniform float Opacity;

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
    if (BlurRadius >= 1) {
        ivec2 texSize = textureSize(Sampler0, 0);
        vec2 texelSize = vec2(1.0 / texSize.x, 1.0 / texSize.y);

        int radius = int(BlurRadius);
        float sigma = float(radius) / 3.0;
        float twoSigmaSq = 2.0 * sigma * sigma;

        vec3 blurSumLinear = vec3(0.0);
        float totalAlphaWeight = 0.0;
        float totalWeight = 0.0;

        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                vec2 offset = vec2(x, y) * texelSize;
                vec4 sampleCol = texture(Sampler0, texCoord + offset);

                vec3 linearColor = pow(sampleCol.rgb, vec3(2.2));
                float weight = exp(-float(x*x + y*y) / twoSigmaSq);

                blurSumLinear += linearColor * sampleCol.a * weight;
                totalAlphaWeight += sampleCol.a * weight;
                totalWeight += weight;
            }
        }

        if (totalAlphaWeight > 0.0) {
            color.rgb = pow(blurSumLinear / totalAlphaWeight, vec3(1.0/2.2));
        } else {
            color.rgb = vec3(0.0);
        }

        color.a = totalAlphaWeight / totalWeight;
    } else {
        color = rawColor;
    }

    if (color.a > 0.001) {
        color.rgb /= color.a;
    }

    // 亮度、灰度、反色、色相旋转
    color.rgb *= Brightness;
    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(gray), Grayscale);
    color.rgb = mix(color.rgb, 1.0 - color.rgb, Invert);

    if (abs(HueRotate) > 0.1) {
        color.rgb = applyHue(color.rgb, HueRotate);
    }

    float finalAlpha = color.a * Opacity;

    // 如果最终透明度太低再丢弃，提高性能
    if (finalAlpha <= 0.001) discard;

    fragColor = vec4(color.rgb, finalAlpha);
}