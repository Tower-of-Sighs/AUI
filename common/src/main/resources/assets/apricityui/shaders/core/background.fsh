#version 150

uniform sampler2D Sampler0;
uniform vec4 BackgroundBox;
uniform vec4 BackgroundTile;
uniform vec4 BackgroundInfo;
uniform vec4 BackgroundAxis;
uniform vec4 BackgroundRadiiX;
uniform vec4 BackgroundRadiiY;
uniform vec4 BackgroundColor;
uniform vec4 BackgroundPositions0;
uniform vec4 BackgroundPositions1;
uniform vec4 BackgroundPositions2;
uniform vec4 BackgroundPositions3;
uniform vec4 BackgroundColor0;
uniform vec4 BackgroundColor1;
uniform vec4 BackgroundColor2;
uniform vec4 BackgroundColor3;
uniform vec4 BackgroundColor4;
uniform vec4 BackgroundColor5;
uniform vec4 BackgroundColor6;
uniform vec4 BackgroundColor7;
uniform vec4 BackgroundColor8;
uniform vec4 BackgroundColor9;
uniform vec4 BackgroundColor10;
uniform vec4 BackgroundColor11;
uniform vec4 BackgroundColor12;
uniform vec4 BackgroundColor13;
uniform vec4 BackgroundColor14;
uniform vec4 BackgroundColor15;

in vec2 backgroundCoord;
out vec4 fragColor;

float stopPosition(int index) {
    vec4 positions[4] = vec4[4](BackgroundPositions0, BackgroundPositions1,
            BackgroundPositions2, BackgroundPositions3);
    return positions[index / 4][index % 4];
}

bool outsideEllipse(vec2 edge, int corner) {
    vec2 radius = vec2(BackgroundRadiiX[corner], BackgroundRadiiY[corner]);
    if (radius.x <= 0.0 || radius.y <= 0.0) return false;
    if (edge.x >= radius.x || edge.y >= radius.y) return false;
    vec2 distance = (radius - edge) / radius;
    return dot(distance, distance) > 1.0;
}

bool outsideCorner(vec2 point) {
    vec2 opposite = BackgroundBox.xy - point;
    return outsideEllipse(point, 0)
            || outsideEllipse(vec2(opposite.x, point.y), 1)
            || outsideEllipse(opposite, 2)
            || outsideEllipse(vec2(point.x, opposite.y), 3);
}

void main() {
    vec2 point = backgroundCoord * BackgroundBox.xy;
    if (outsideCorner(point)) discard;
    int count = int(BackgroundInfo.x);
    if (count < 0) {
        vec4 color = texture(Sampler0, vec2(backgroundCoord.x, 1.0 - backgroundCoord.y));
        fragColor = color.a > 0.0 ? vec4(color.rgb / color.a, color.a) : vec4(0.0);
        return;
    }
    if (count == 0) {
        fragColor = BackgroundColor;
        return;
    }
    vec2 tilePoint = point - BackgroundTile.xy;
    if (BackgroundInfo.z > 0.5) tilePoint.x = mod(tilePoint.x, BackgroundTile.z);
    else if (tilePoint.x < 0.0 || tilePoint.x >= BackgroundTile.z) discard;
    if (BackgroundInfo.w > 0.5) tilePoint.y = mod(tilePoint.y, BackgroundTile.w);
    else if (tilePoint.y < 0.0 || tilePoint.y >= BackgroundTile.w) discard;
    float t = clamp(0.5 + dot(tilePoint - BackgroundTile.zw * 0.5, BackgroundAxis.xy)
            / BackgroundAxis.z, 0.0, 1.0);
    vec4 colors[16] = vec4[16](BackgroundColor0, BackgroundColor1, BackgroundColor2,
            BackgroundColor3, BackgroundColor4, BackgroundColor5, BackgroundColor6,
            BackgroundColor7, BackgroundColor8, BackgroundColor9, BackgroundColor10,
            BackgroundColor11, BackgroundColor12, BackgroundColor13, BackgroundColor14,
            BackgroundColor15);
    float first = stopPosition(0);
    float last = stopPosition(count - 1);
    if (BackgroundInfo.y > 0.5 && last - first > 0.0001) {
        t = first + mod(t - first, last - first);
    }
    vec4 color = colors[0];
    for (int i = 1; i < 16; i++) {
        if (i >= count) break;
        float next = stopPosition(i);
        if (t < next) {
            float previous = stopPosition(i - 1);
            color = mix(colors[i - 1], colors[i], clamp((t - previous) / max(next - previous, 0.000001), 0.0, 1.0));
            break;
        }
        color = colors[i];
    }
    fragColor = color;
}
