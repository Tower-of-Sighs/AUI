#version 150
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D SurfaceHeight;
uniform vec4 Grid;
uniform vec4 CoverageRegion;
uniform vec4 SurfaceOrigin;
out vec3 localPosition;
const ivec2 CORNERS[6] = ivec2[6](ivec2(0, 0), ivec2(0, 1), ivec2(1, 1), ivec2(0, 0), ivec2(1, 1), ivec2(1, 0));
float cornerHeight(ivec2 corner) {
    ivec2 last = ivec2(Grid.xy) - 1;
    float sum = 0.0;
    for (int z = -1; z <= 0; z++) {
        for (int x = -1; x <= 0; x++) {
            sum += texelFetch(SurfaceHeight, clamp(corner + ivec2(x, z), ivec2(0), last), 0).r;
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
