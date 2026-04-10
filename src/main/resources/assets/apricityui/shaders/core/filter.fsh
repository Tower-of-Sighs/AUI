#version 330

uniform sampler2D InSampler;

layout(std140) uniform ApricityFilter {
    vec2 InSize;
    vec2 GuiSize;
    float BlurRadius;
    float Brightness;
    float Grayscale;
    float Invert;
    float HueRotate;
    float Opacity;
    vec2 ShadowOffset;
    float ShadowBlur;
    vec4 ShadowColor;
    float ForceAlpha;
    vec4 ClipRect;
    vec4 ClipRadii;
    float ClipEnabled;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 applyHue(vec3 color, float angle) {
    float h = angle * 0.01745329251;
    vec3 k = vec3(0.57735);
    float cosAngle = cos(h);
    return color * cosAngle + cross(k, color) * sin(h) + k * dot(k, color) * (1.0 - cosAngle);
}

void main() {
    if (ClipEnabled > 0.5) {
        vec2 pos = vec2(texCoord.x, 1.0 - texCoord.y) * GuiSize;
        vec2 rectPos = ClipRect.xy;
        vec2 rectSize = ClipRect.zw;
        vec2 local = pos - rectPos;

        if (local.x < 0.0 || local.y < 0.0 || local.x > rectSize.x || local.y > rectSize.y) {
            discard;
        }

        float tl = ClipRadii.x;
        float tr = ClipRadii.y;
        float br = ClipRadii.z;
        float bl = ClipRadii.w;
        float maxR = min(rectSize.x, rectSize.y) * 0.5;
        tl = min(tl, maxR);
        tr = min(tr, maxR);
        br = min(br, maxR);
        bl = min(bl, maxR);

        if (tl > 0.0 && local.x < tl && local.y < tl) {
            if (distance(local, vec2(tl, tl)) > tl) discard;
        }
        if (tr > 0.0 && local.x > rectSize.x - tr && local.y < tr) {
            if (distance(local, vec2(rectSize.x - tr, tr)) > tr) discard;
        }
        if (br > 0.0 && local.x > rectSize.x - br && local.y > rectSize.y - br) {
            if (distance(local, vec2(rectSize.x - br, rectSize.y - br)) > br) discard;
        }
        if (bl > 0.0 && local.x < bl && local.y > rectSize.y - bl) {
            if (distance(local, vec2(bl, rectSize.y - bl)) > bl) discard;
        }
    }

    vec4 rawColor = texture(InSampler, texCoord);
    if (ForceAlpha > 0.5) {
        rawColor.a = 1.0;
    }
    if (rawColor.a <= 0.001 && ShadowColor.a <= 0.001) discard;

    vec4 color;
    if (BlurRadius >= 1) {
        ivec2 texSize = textureSize(InSampler, 0);
        vec2 texelSize = vec2(1.0 / texSize.x, 1.0 / texSize.y);

        int radius = int(BlurRadius);
        float sigma = max(0.001, float(radius) / 3.0);
        float twoSigmaSq = 2.0 * sigma * sigma;

        vec3 blurSumLinear = vec3(0.0);
        float totalAlphaWeight = 0.0;
        float totalWeight = 0.0;

        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                vec2 offset = vec2(x, y) * texelSize;
                vec4 sampleCol = texture(InSampler, texCoord + offset);
                float sampleAlpha = (ForceAlpha > 0.5) ? 1.0 : sampleCol.a;

                vec3 linearColor = pow(sampleCol.rgb, vec3(2.2));
                float weight = exp(-float(x*x + y*y) / twoSigmaSq);

                blurSumLinear += linearColor * sampleAlpha * weight;
                totalAlphaWeight += sampleAlpha * weight;
                totalWeight += weight;
            }
        }

        if (totalAlphaWeight > 0.0) {
            color.rgb = pow(blurSumLinear / totalAlphaWeight, vec3(1.0/2.2));
        } else {
            color.rgb = vec3(0.0);
        }

        color.a = (totalWeight > 0.0) ? (totalAlphaWeight / totalWeight) : 0.0;
    } else {
        color = rawColor;
    }

    vec4 shadow = vec4(0.0);
    if (ShadowColor.a > 0.001) {
        ivec2 texSize = textureSize(InSampler, 0);
        vec2 texelSize = vec2(1.0 / texSize.x, 1.0 / texSize.y);
        vec2 shadowBaseUv = texCoord + vec2(-ShadowOffset.x, ShadowOffset.y) / InSize;

        float shadowAlpha = 0.0;
        if (ShadowBlur >= 1.0) {
            int radius = int(ShadowBlur);
            float sigma = max(0.001, float(radius) / 3.0);
            float twoSigmaSq = 2.0 * sigma * sigma;
            float totalWeight = 0.0;

            for (int x = -radius; x <= radius; ++x) {
                for (int y = -radius; y <= radius; ++y) {
                    vec2 offset = vec2(x, y) * texelSize;
                    vec4 sampleCol = texture(InSampler, shadowBaseUv + offset);
                    float sampleAlpha = (ForceAlpha > 0.5) ? 1.0 : sampleCol.a;
                    float weight = exp(-float(x*x + y*y) / twoSigmaSq);
                    shadowAlpha += sampleAlpha * weight;
                    totalWeight += weight;
                }
            }
            shadowAlpha = (totalWeight > 0.0) ? (shadowAlpha / totalWeight) : 0.0;
        } else {
            vec4 sampleCol = texture(InSampler, shadowBaseUv);
            shadowAlpha = (ForceAlpha > 0.5) ? 1.0 : sampleCol.a;
        }
        shadow = vec4(ShadowColor.rgb, ShadowColor.a * shadowAlpha);
    }

    if (color.a > 0.001) {
        color.rgb /= color.a;
    }

    color.rgb *= Brightness;
    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(color.rgb, vec3(gray), Grayscale);
    color.rgb = mix(color.rgb, 1.0 - color.rgb, Invert);

    if (abs(HueRotate) > 0.1) {
        color.rgb = applyHue(color.rgb, HueRotate);
    }

    float srcA = max(0.0, color.a * Opacity);
    float shA = max(0.0, shadow.a);
    float outA = srcA + shA * (1.0 - srcA);
    if (outA <= 0.001) discard;

    vec3 outPremul = color.rgb * srcA + shadow.rgb * shA * (1.0 - srcA);
    vec3 outRgb = outPremul / outA;
    fragColor = vec4(outRgb, outA);
}
