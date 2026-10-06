#version 330
uniform sampler2D ColumnHeight;
uniform usampler2D ColumnRanges;
uniform usampler2D ColumnRuns;
uniform sampler2D Materials;
uniform sampler2D Sampler0;
layout(std140) uniform BlockSurfaceParams { vec4 Grid; vec4 Origin; vec4 Directions; vec4 RunShape; };
in vec3 tilePoint;
flat in int face;
flat in int extraTop;
out vec4 fragColor;
uint material(ivec2 cell, float y, int layer) {
    ivec2 p = cell + ivec2(4);
    if (any(lessThan(p,ivec2(0))) || any(greaterThanEqual(p,textureSize(ColumnRanges,0)))) return 0u;
    uvec2 range = texelFetch(ColumnRanges,p,0).rg;
    for (uint i=0u;i<range.y;i++) {
        uint index = range.x + range.y - 1u - i;
        uvec4 run = texelFetch(ColumnRuns,ivec2(int(index)%int(RunShape.x),int(index)/int(RunShape.x)),0);
        float top=float(int(run.y)-32769)+texelFetch(Materials,ivec2(2,int(run.z)),0).z;
        if (y>=float(int(run.x)-32768) && y<top && int(run.w)==layer) return run.z;
    }
    return 0u;
}
vec4 layerColor(uint id, int offset, vec2 point, vec2 dx, vec2 dy) {
    vec4 a=texelFetch(Materials,ivec2(face*6+offset,int(id)),0);
    vec4 b=texelFetch(Materials,ivec2(face*6+offset+1,int(id)),0);
    vec4 c=texelFetch(Materials,ivec2(face*6+offset+2,int(id)),0);
    if(c.y==0.0) return vec4(0.0);
    vec2 uv=a.xy+a.zw*point.x+b.xy*point.y;
    vec4 textureColor=textureGrad(Sampler0,uv,a.zw*dx.x+b.xy*dx.y,a.zw*dy.x+b.xy*dy.y);
    return textureColor*vec4(b.zw,c.xy);
}
void main() {
    vec2 normal=face==1 ? vec2(1,0) : face==2 ? vec2(-1,0) : face==3 ? vec2(0,1) : face==4 ? vec2(0,-1) : vec2(0);
    ivec2 cell=ivec2(floor(tilePoint.xz-normal*0.001));
    float y=tilePoint.y;
    if(face==0) y=texelFetch(ColumnHeight,cell+ivec2(4),0)[extraTop==1 ? 3 : int(Grid.w)]-0.001;
    uint id=material(cell,y,Grid.w==0.0 ? 0 : 1);
    if(id==0u) discard;
    if(face!=0) {
        ivec2 neighbor=cell+ivec2(normal);
        if(material(neighbor,y,0)!=0u || (Grid.w!=0.0 && material(neighbor,y,1)==id)) discard;
    }
    vec2 axes=face==0 ? tilePoint.xz : (face==1 || face==2) ? tilePoint.zy : tilePoint.xy;
    vec2 point=fract(axes), dx=dFdx(axes), dy=dFdy(axes);
    vec4 base=layerColor(id,0,point,dx,dy), overlay=layerColor(id,3,point,dx,dy);
    vec4 color=vec4(mix(base.rgb,overlay.rgb,overlay.a),max(base.a,overlay.a));
    if(color.a<0.01) discard;
    float shade=(face==0 ? 1.0 : face<=2 ? 0.80 : 0.68)*mix(0.55,1.0,clamp(Origin.w,0.0,1.0));
    fragColor=vec4(color.rgb*shade,Grid.w==0.0 ? 1.0 : color.a);
}
