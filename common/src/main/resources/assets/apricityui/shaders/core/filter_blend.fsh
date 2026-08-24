#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float BlendMode;

in vec2 texCoord;
out vec4 fragColor;

float lum(vec3 c) {
    return dot(c, vec3(0.3, 0.59, 0.11));
}

float sat(vec3 c) {
    return max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
}

vec3 clipColor(vec3 c) {
    float l = lum(c);
    float n = min(c.r, min(c.g, c.b));
    float x = max(c.r, max(c.g, c.b));
    if (n < 0.0 && l > n) c = vec3(l) + (c - vec3(l)) * l / (l - n);
    if (x > 1.0 && x > l) c = vec3(l) + (c - vec3(l)) * (1.0 - l) / (x - l);
    return clamp(c, vec3(0.0), vec3(1.0));
}

vec3 setLum(vec3 c, float l) {
    return clipColor(c + vec3(l - lum(c)));
}

vec3 setSat(vec3 c, float s) {
    float mn = min(c.r, min(c.g, c.b));
    float mx = max(c.r, max(c.g, c.b));
    if (mx <= mn) return vec3(0.0);
    float scale = s / (mx - mn);
    vec3 outColor;
    // Ordered branches avoid equality checks against computed min/max values.
    if (c.r <= c.g) {
        if (c.g <= c.b) outColor = vec3(0.0, (c.g - mn) * scale, s);
        else if (c.r <= c.b) outColor = vec3(0.0, s, (c.b - mn) * scale);
        else outColor = vec3((c.r - mn) * scale, s, 0.0);
    } else {
        if (c.r <= c.b) outColor = vec3((c.r - mn) * scale, 0.0, s);
        else if (c.g <= c.b) outColor = vec3(s, 0.0, (c.b - mn) * scale);
        else outColor = vec3(s, (c.g - mn) * scale, 0.0);
    }
    return outColor;
}

float softLight(float cb, float cs) {
    if (cs <= 0.5) return cb - (1.0 - 2.0 * cs) * cb * (1.0 - cb);
    float d = cb <= 0.25 ? ((16.0 * cb - 12.0) * cb + 4.0) * cb : sqrt(cb);
    return cb + (2.0 * cs - 1.0) * (d - cb);
}

float blendChannel(int mode, float cb, float cs) {
    if (mode == 0) return cs;
    if (mode == 1) return cb * cs;
    if (mode == 2) return 1.0 - (1.0 - cb) * (1.0 - cs);
    if (mode == 3) return cb <= 0.5 ? 2.0 * cb * cs : 1.0 - 2.0 * (1.0 - cb) * (1.0 - cs);
    if (mode == 4) return min(cb, cs);
    if (mode == 5) return max(cb, cs);
    if (mode == 6) return cs >= 1.0 ? 1.0 : min(1.0, cb / max(0.000001, 1.0 - cs));
    if (mode == 7) return cs <= 0.0 ? 0.0 : 1.0 - min(1.0, (1.0 - cb) / cs);
    if (mode == 8) return cs <= 0.5 ? 2.0 * cb * cs : 1.0 - 2.0 * (1.0 - cb) * (1.0 - cs);
    if (mode == 9) return softLight(cb, cs);
    if (mode == 10) return abs(cb - cs);
    if (mode == 11) return cb + cs - 2.0 * cb * cs;
    return cs;
}

vec3 blendRgb(int mode, vec3 cb, vec3 cs) {
    if (mode == 12) return setLum(setSat(cs, sat(cb)), lum(cb));
    if (mode == 13) return setLum(setSat(cb, sat(cs)), lum(cb));
    if (mode == 14) return setLum(cs, lum(cb));
    if (mode == 15) return setLum(cb, lum(cs));
    return vec3(blendChannel(mode, cb.r, cs.r),
                blendChannel(mode, cb.g, cs.g),
                blendChannel(mode, cb.b, cs.b));
}

void main() {
    vec4 sourceRaw = texture(Sampler0, texCoord);
    vec4 backdropRaw = texture(Sampler1, texCoord);
    // AUI's regular translucent passes store premultiplied RGB in the target
    // (the filter shader emits straight RGB and the source-alpha blend state
    // performs the multiplication). Blend functions, however, are defined on
    // straight colors, so recover them before evaluating B(Cb, Cs).
    vec4 source = vec4(sourceRaw.a > 0.000001 ? sourceRaw.rgb / sourceRaw.a : vec3(0.0), sourceRaw.a);
    vec4 backdrop = vec4(backdropRaw.a > 0.000001 ? backdropRaw.rgb / backdropRaw.a : vec3(0.0), backdropRaw.a);
    int mode = int(BlendMode + 0.5);

    if (mode == 16) {
        float alpha = min(1.0, source.a + backdrop.a);
        vec3 premul = min(source.rgb * source.a + backdrop.rgb * backdrop.a, vec3(1.0));
        fragColor = alpha > 0.000001 ? vec4(premul, alpha) : vec4(0.0);
        return;
    }

    float as = clamp(source.a, 0.0, 1.0);
    float ab = clamp(backdrop.a, 0.0, 1.0);
    float ao = as + ab * (1.0 - as);
    if (ao <= 0.000001) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 blended = blendRgb(mode, clamp(backdrop.rgb, vec3(0.0), vec3(1.0)),
            clamp(source.rgb, vec3(0.0), vec3(1.0)));
    vec3 premul = as * (1.0 - ab) * source.rgb
            + as * ab * blended
            + (1.0 - as) * ab * backdrop.rgb;
    // The blend pipeline uses one/zero factors and therefore writes the
    // premultiplied result directly into the parent target.
    fragColor = vec4(clamp(premul, vec3(0.0), vec3(1.0)), ao);
}
