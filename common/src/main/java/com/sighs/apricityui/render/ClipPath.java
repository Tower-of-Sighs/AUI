package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.Size;

import org.joml.Matrix4f;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClipPath {
    private static final Pattern FUNC_PATTERN = Pattern.compile("([a-z-]+)\\((.*)\\)");

    public static void drawToStencil(Matrix4f mat, float x, float y, float w, float h, String clipPathValue) {
        if (clipPathValue == null || clipPathValue.equals("none")) return;

        Matcher matcher = FUNC_PATTERN.matcher(clipPathValue.trim());
        if (!matcher.find()) return;

        String type = matcher.group(1);
        String args = matcher.group(2);

        switch (type) {
            case "polygon" -> drawPolygon(mat, x, y, w, h, args);
            case "circle" -> drawCircle(mat, x, y, w, h, args);
            case "ellipse" -> drawEllipse(mat, x, y, w, h, args);
            case "inset" -> drawInset(mat, x, y, w, h, args);
        }
    }

    private static void drawPolygon(Matrix4f mat, float x, float y, float w, float h, String args) {
        // CSS Shapes 1 语法：polygon( <fill-rule>? [ <length-percentage> <length-percentage> ]# )
        // fill-rule 影响填充结果，本实现统一按 nonzero 处理（见 coverage 文档的 clip-path 条目）。
        String body = stripFillRule(args);
        String[] points = body.split("\\s*,\\s*");
        if (points.length < 3) return;

        int count = points.length;
        float[] px = new float[count];
        float[] py = new float[count];
        for (int i = 0; i < count; i++) {
            String[] coords = points[i].trim().split("\\s+");
            if (coords.length < 2) return;
            px[i] = x + parseLength(coords[0], w);
            py[i] = y + parseLength(coords[1], h);
        }

        // 扇心必须落在形内才能只覆盖内部一次。质心只在凸多边形有保证，凹多边形会
        // 把凹口也填上；改用耳切剖分，对任意简单多边形都与 nonzero 填充一致。
        emitPolygonTriangles(mat, px, py);
    }

    /** 去掉可选的 fill-rule 前缀（nonzero / evenodd），返回剩余的点列表。 */
    private static String stripFillRule(String args) {
        String body = args.trim();
        for (String rule : new String[]{"nonzero", "evenodd"}) {
            if (body.regionMatches(true, 0, rule, 0, rule.length())) {
                body = body.substring(rule.length()).trim();
                if (body.startsWith(",")) body = body.substring(1).trim();
                return body;
            }
        }
        return body;
    }

    /**
     * 耳切（ear clipping）三角剖分。
     *
     * <p>输出三角形并集恰好等于多边形内部、且互不重叠，因此逐像素只被覆盖一次——
     * 这正是 Mask 的 stencil 计数方案（INCR 后按 EQUAL 深度选中）所需的前提。
     * 凹多边形用质心扇形会把凹口的多余区域一并覆盖，故必须做真正的三角剖分。</p>
     */
    private static void emitPolygonTriangles(Matrix4f mat, float[] px, float[] py) {
        int n = px.length;
        if (n < 3) return;

        // 统一成逆时针，凸性判定无需再带方向分支。
        if (signedArea(px, py) < 0.0f) {
            for (int i = 0, j = n - 1; i < j; i++, j--) {
                float tx = px[i]; px[i] = px[j]; px[j] = tx;
                float ty = py[i]; py[i] = py[j]; py[j] = ty;
            }
        }

        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;

        int remaining = n;
        int safety = n * n + 8; // 退化输入的保护，避免死循环
        while (remaining > 3 && safety-- > 0) {
            int ear = findEar(px, py, idx, remaining);
            if (ear < 0) break; // 退化/自交：停止剖分，宁可少填也不填出错区域

            int prev = idx[(ear - 1 + remaining) % remaining];
            int cur = idx[ear];
            int next = idx[(ear + 1) % remaining];
            emitTriangle(mat, px, py, prev, cur, next);

            for (int j = ear; j < remaining - 1; j++) idx[j] = idx[j + 1];
            remaining--;
        }

        if (remaining == 3) {
            emitTriangle(mat, px, py, idx[0], idx[1], idx[2]);
        }
    }

    /** 找到一个可裁的耳顶点下标；找不到返回 -1。 */
    private static int findEar(float[] px, float[] py, int[] idx, int remaining) {
        for (int i = 0; i < remaining; i++) {
            int prev = idx[(i - 1 + remaining) % remaining];
            int cur = idx[i];
            int next = idx[(i + 1) % remaining];
            if (cross(px, py, prev, cur, next) <= 0.0f) continue; // 凹顶点不是耳
            if (containsAnyVertex(px, py, idx, remaining, i, prev, cur, next)) continue;
            return i;
        }
        return -1;
    }

    /** 三角形内（含边上）是否还有其它多边形顶点。 */
    private static boolean containsAnyVertex(float[] px, float[] py, int[] idx, int remaining,
                                             int earPos, int a, int b, int c) {
        int prevPos = (earPos - 1 + remaining) % remaining;
        int nextPos = (earPos + 1) % remaining;
        for (int i = 0; i < remaining; i++) {
            if (i == earPos || i == prevPos || i == nextPos) continue;
            int p = idx[i];
            if (pointInTriangle(px[p], py[p], px[a], py[a], px[b], py[b], px[c], py[c])) return true;
        }
        return false;
    }

    private static void emitTriangle(Matrix4f mat, float[] px, float[] py, int a, int b, int c) {
        Graph.vtx(Base.getMesh(), mat, px[a], py[a], 0xFFFFFFFF);
        Graph.vtx(Base.getMesh(), mat, px[b], py[b], 0xFFFFFFFF);
        Graph.vtx(Base.getMesh(), mat, px[c], py[c], 0xFFFFFFFF);
    }

    private static float cross(float[] px, float[] py, int a, int b, int c) {
        return (px[b] - px[a]) * (py[c] - py[a]) - (py[b] - py[a]) * (px[c] - px[a]);
    }

    private static boolean pointInTriangle(float px, float py,
                                          float ax, float ay, float bx, float by,
                                          float cx, float cy) {
        float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean hasNeg = d1 < 0.0f || d2 < 0.0f || d3 < 0.0f;
        boolean hasPos = d1 > 0.0f || d2 > 0.0f || d3 > 0.0f;
        return !(hasNeg && hasPos);
    }

    private static float signedArea(float[] px, float[] py) {
        float area = 0.0f;
        for (int i = 0; i < px.length; i++) {
            int j = (i + 1) % px.length;
            area += px[i] * py[j] - px[j] * py[i];
        }
        return area * 0.5f;
    }

    private static void drawCircle(Matrix4f mat, float x, float y, float w, float h, String args) {
        // 简化解析：[radius] [at pos pos]
        // CSS Shapes 1：省略半径时取 closest-side，即圆心到引用框最近边的距离
        // （圆心居中时等于 min(w, h) / 2，而不是外接圆半径）。
        float[] center = parsePosition(args, x, y, w, h);
        float closestSide = Math.max(0.0f, Math.min(
                Math.min(center[0] - x, x + w - center[0]),
                Math.min(center[1] - y, y + h - center[1])));
        float r = parseLength(args.split(" at ")[0], closestSide);
        Graph.addEllipseGeometry(Base.getMesh(), mat, center[0], center[1], r, r, 0xFFFFFFFF);
    }

    private static void drawEllipse(Matrix4f mat, float x, float y, float w, float h, String args) {
        // CSS Shapes 1：省略半径时 rx / ry 各自取该轴上的 closest-side。
        String[] parts = args.split(" at ");
        String[] radii = parts[0].trim().split("\\s+");
        float[] center = parsePosition(args, x, y, w, h);
        float closestX = Math.max(0.0f, Math.min(center[0] - x, x + w - center[0]));
        float closestY = Math.max(0.0f, Math.min(center[1] - y, y + h - center[1]));
        float rx = parseLength(radii[0], closestX);
        float ry = radii.length > 1 ? parseLength(radii[1], closestY) : closestY;
        Graph.addEllipseGeometry(Base.getMesh(), mat, center[0], center[1], rx, ry, 0xFFFFFFFF);
    }

    private static void drawInset(Matrix4f mat, float x, float y, float w, float h, String args) {
        String[] parts = args.split(" round ")[0].trim().split("\\s+");
        float t = parseLength(parts[0], h);
        float r = parts.length > 1 ? parseLength(parts[1], w) : t;
        float b = parts.length > 2 ? parseLength(parts[2], h) : t;
        float l = parts.length > 3 ? parseLength(parts[3], w) : r;

        Graph.addRect(Base.getMesh(), mat, x + l, y + t, x + w - r, y + h - b, 0xFFFFFFFF);
    }

    private static float[] parsePosition(String args, float x, float y, float w, float h) {
        if (!args.contains(" at ")) return new float[]{x + w / 2, y + h / 2};
        String[] pos = args.split(" at ")[1].trim().split("\\s+");
        return new float[]{x + parseLength(pos[0], w), y + parseLength(pos[1], h)};
    }

    private static float parseLength(String val, float ref) {
        Double resolved = Size.tryResolveLength(val, ref);
        return resolved == null ? 0 : resolved.floatValue();
    }
}