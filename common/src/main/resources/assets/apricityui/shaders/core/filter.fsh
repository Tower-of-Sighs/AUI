#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Brightness;
uniform float Grayscale;
uniform float Invert;
uniform float HueRotate;
uniform float Opacity;
uniform vec2 ShadowOffset;
uniform vec4 ShadowColor;
uniform float ForceAlpha;
uniform vec4 ClipRect;
uniform vec4 ClipRadii;
uniform float ClipEnabled;
uniform vec2 UvPerGuiPixel;

in vec2 texCoord;
in vec2 screenPos;
out vec4 fragColor;

vec3 applyHue(vec3 color, float angle) {
    float h = angle * 0.01745329251;
    vec3 k = vec3(0.57735);
    float cosAngle = cos(h);
    return color * cosAngle + cross(k, color) * sin(h) + k * dot(k, color) * (1.0 - cosAngle);
}

void main() {
    if (ClipEnabled > 0.5) {
        vec2 pos = screenPos;
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

    vec4 rawColor = texture(Sampler0, texCoord);
    if (ForceAlpha > 0.5) {
        rawColor.a = 1.0;
    }
    if (rawColor.a <= 0.001 && ShadowColor.a <= 0.001) discard;

    // Blur is performed by filter_blur in horizontal and vertical passes.
    // Keeping it out of this composite shader avoids an O(radius^2) loop for
    // every output pixel.
    vec4 color = rawColor;

    vec4 shadow = vec4(0.0);
    if (ShadowColor.a > 0.001) {
        vec2 shadowBaseUv = texCoord + vec2(-ShadowOffset.x * UvPerGuiPixel.x,
                                             ShadowOffset.y * UvPerGuiPixel.y);
        vec4 sampleCol = texture(Sampler1, shadowBaseUv);
        float shadowAlpha = (ForceAlpha > 0.5) ? 1.0 : sampleCol.a;
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
