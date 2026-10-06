#version 150
uniform sampler2D SurfaceColor;
uniform sampler2D Coverage;
uniform vec4 Grid;
uniform vec4 CoverageRegion;
uniform vec4 SurfaceOrigin;
in vec3 localPosition;
out vec4 fragColor;
void main() {
    vec2 coverageUV = (localPosition.xz + CoverageRegion.xy) / (vec2(textureSize(Coverage, 0)) * 16.0);
    if (all(greaterThanEqual(coverageUV, vec2(0.0))) && all(lessThan(coverageUV, vec2(1.0)))
            && texture(Coverage, coverageUV).r > 0.75) discard;
    vec4 color = texture(SurfaceColor, (localPosition.xz - SurfaceOrigin.xz) / (Grid.xy * Grid.z));
    if (color.a == 0.0) discard;
    vec3 normal = normalize(cross(dFdy(localPosition), dFdx(localPosition)));
    float shade = (0.72 + 0.28 * abs(normal.y)) * mix(0.55, 1.0, clamp(Grid.w, 0.0, 1.0));
    fragColor = vec4(color.rgb * shade, 1.0);
}
