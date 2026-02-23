package com.sighs.apricityui.render;


import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.vector.Matrix4f;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClipPath {
    private static final Pattern FUNC_PATTERN = Pattern.compile("([a-z-]+)\\((.*)\\)");

    public static void drawToStencil(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, String clipPathValue) {
        if (clipPathValue == null || clipPathValue.equals("none")) return;

        Matcher matcher = FUNC_PATTERN.matcher(clipPathValue.trim());
        if (!matcher.find()) return;

        String type = matcher.group(1);
        String args = matcher.group(2);

        switch (type) {
            case "polygon":
                drawPolygon(buf, mat, x, y, w, h, args);
                break;
            case "circle":
                drawCircle(buf, mat, x, y, w, h, args);
                break;
            case "ellipse":
                drawEllipse(buf, mat, x, y, w, h, args);
                break;
            case "inset":
                drawInset(buf, mat, x, y, w, h, args);
                break;
        }
    }

    private static void drawPolygon(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, String args) {
        String[] points = args.split("\\s*,\\s*");
        if (points.length < 3) return;

        float[] px = new float[points.length];
        float[] py = new float[points.length];
        float cx = 0, cy = 0;

        for (int i = 0; i < points.length; i++) {
            String[] coords = points[i].trim().split("\\s+");
            px[i] = x + parseLength(coords[0], w);
            py[i] = y + parseLength(coords[1], h);
            cx += px[i];
            cy += py[i];
        }
        cx /= points.length;
        cy /= points.length;

        for (int i = 0; i < points.length; i++) {
            Graph.vtx(buf, mat, cx, cy, 0xFFFFFFFF);
            Graph.vtx(buf, mat, px[i], py[i], 0xFFFFFFFF);
            Graph.vtx(buf, mat, px[(i + 1) % points.length], py[(i + 1) % points.length], 0xFFFFFFFF);
        }
    }

    private static void drawCircle(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, String args) {
        // 简化解析：[radius] [at pos pos]
        float r = parseLength(args.split(" at ")[0], (float) Math.sqrt(w * w + h * h) / 1.4142f);
        float[] center = parsePosition(args, x, y, w, h);
        Graph.addEllipseGeometry(buf, mat, center[0], center[1], r, r, 0xFFFFFFFF);
    }

    private static void drawEllipse(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, String args) {
        String[] parts = args.split(" at ");
        String[] radii = parts[0].trim().split("\\s+");
        float rx = parseLength(radii[0], w);
        float ry = radii.length > 1 ? parseLength(radii[1], h) : rx;
        float[] center = parsePosition(args, x, y, w, h);
        Graph.addEllipseGeometry(buf, mat, center[0], center[1], rx, ry, 0xFFFFFFFF);
    }

    private static void drawInset(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, String args) {
        String[] parts = args.split(" round ")[0].trim().split("\\s+");
        float t = parseLength(parts[0], h);
        float r = parts.length > 1 ? parseLength(parts[1], w) : t;
        float b = parts.length > 2 ? parseLength(parts[2], h) : t;
        float l = parts.length > 3 ? parseLength(parts[3], w) : r;

        Graph.addRect(buf, mat, x + l, y + t, x + w - r, y + h - b, 0xFFFFFFFF);
    }

    private static float[] parsePosition(String args, float x, float y, float w, float h) {
        if (!args.contains(" at ")) return new float[]{x + w / 2, y + h / 2};
        String[] pos = args.split(" at ")[1].trim().split("\\s+");
        return new float[]{x + parseLength(pos[0], w), y + parseLength(pos[1], h)};
    }

    private static float parseLength(String val, float ref) {
        val = val.trim();
        if (val.endsWith("%")) return Float.parseFloat(val.substring(0, val.length() - 1)) / 100f * ref;
        if (val.endsWith("px")) return Float.parseFloat(val.substring(0, val.length() - 2));
        try {
            return Float.parseFloat(val);
        } catch (Exception e) {
            return 0;
        }
    }
}