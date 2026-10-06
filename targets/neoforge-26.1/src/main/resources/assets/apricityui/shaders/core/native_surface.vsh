#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
uniform sampler2D SurfaceHeight;
layout(std140) uniform SurfaceParams { vec4 Grid; vec4 CoverageRegion; vec4 SurfaceOrigin; };
out vec3 localPosition;
const ivec2 CORNERS[6] = ivec2[6](ivec2(0, 0), ivec2(0, 1), ivec2(1, 1), ivec2(0, 0), ivec2(1, 1), ivec2(1, 0));
float heightAt(ivec2 cell) {
    uvec4 bytes = uvec4(round(texelFetch(SurfaceHeight, cell, 0) * 255.0));
    return uintBitsToFloat(bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u));
}
float cornerHeight(ivec2 corner) {
    ivec2 last = ivec2(Grid.xy) - 1;
    float sum = 0.0;
    for (int z = -1; z <= 0; z++) {
        for (int x = -1; x <= 0; x++) {
            sum += heightAt(clamp(corner + ivec2(x, z), ivec2(0), last));
        }
    }
    return sum * 0.25;
}
void main() {
    int cell = gl_VertexID / 6;
    ivec2 corner = ivec2(cell % int(Grid.x), cell / int(Grid.x)) + CORNERS[gl_VertexID % 6];
    localPosition = vec3(float(corner.x) * Grid.z, cornerHeight(corner), float(corner.y) * Grid.z) + SurfaceOrigin.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(localPosition, 1.0);
}
