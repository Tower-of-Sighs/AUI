#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
uniform sampler2D ColumnHeight;
layout(std140) uniform BlockSurfaceParams { vec4 Grid; vec4 Origin; vec4 Directions; vec4 RunShape; };
out vec3 tilePoint;
flat out int face;
flat out int extraTop;
const ivec2 CORNERS[6] = ivec2[6](ivec2(0,0),ivec2(0,1),ivec2(1,1),ivec2(0,0),ivec2(1,1),ivec2(1,0));
vec4 heights(ivec2 cell) { return texelFetch(ColumnHeight, clamp(cell + ivec2(4), ivec2(0), textureSize(ColumnHeight,0)-1),0); }
void main() {
    int columns = int(ceil(Grid.x / Grid.z));
    int vertices = Grid.w==0.0 ? 24 : Grid.w==2.0 ? 6 : 18;
    int cell = gl_VertexID / vertices;
    ivec2 p = ivec2(cell % columns, cell / columns) * int(Grid.z);
    ivec2 corner = CORNERS[gl_VertexID % 6];
    vec4 h = heights(p);
    float top = Grid.w == 0.0 ? h.x : Grid.w==2.0 ? h.z : h.y;
    float dx = min(Grid.z, Grid.x - float(p.x));
    float dz = min(Grid.z, Grid.y - float(p.y));
    int quad = gl_VertexID % vertices / 6;
    extraTop = quad==3 ? 1 : 0;
    if (quad == 0 || quad==3) {
        face = 0;
        tilePoint = vec3(float(p.x) + float(corner.x)*dx,quad==3 ? h.w : top,float(p.y) + float(corner.y)*dz);
    } else if (quad == 1) {
        bool positive = Directions.x > 0.0;
        vec4 neighbor = heights(p + ivec2(int(Directions.x)*int(Grid.z),0));
        float bottom = min(top, Grid.w == 0.0 ? neighbor.x : max(h.x,neighbor.y));
        face = positive ? 1 : 2;
        tilePoint = vec3(float(p.x)+(positive ? dx : 0.0),mix(bottom,top,float(corner.x)),float(p.y)+float(corner.y)*dz);
    } else {
        bool positive = Directions.y > 0.0;
        vec4 neighbor = heights(p + ivec2(0,int(Directions.y)*int(Grid.z)));
        float bottom = min(top, Grid.w == 0.0 ? neighbor.x : max(h.x,neighbor.y));
        face = positive ? 3 : 4;
        tilePoint = vec3(float(p.x)+float(corner.y)*dx,mix(bottom,top,float(corner.x)),float(p.y)+(positive ? dz : 0.0));
    }
    gl_Position = ProjMat * ModelViewMat * vec4(tilePoint + Origin.xyz,1.0);
}
