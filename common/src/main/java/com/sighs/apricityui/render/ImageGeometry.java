package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.Layout;

import java.util.Locale;

public final class ImageGeometry {
    private ImageGeometry() {
    }

    public static Rect resolveObjectFitRect(String fitValue, String positionValue,
                                            float boxX, float boxY, float boxW, float boxH,
                                            int intrinsicW, int intrinsicH) {
        if (boxW <= 0 || boxH <= 0 || intrinsicW <= 0 || intrinsicH <= 0) {
            return new Rect(boxX, boxY, Math.max(0, boxW), Math.max(0, boxH));
        }

        String fit = fitValue == null ? "fill" : fitValue.trim().toLowerCase(Locale.ROOT);
        float drawW = boxW;
        float drawH = boxH;
        float intrinsicWidth = intrinsicW;
        float intrinsicHeight = intrinsicH;

        switch (fit) {
            case "contain" -> {
                float scale = Math.min(boxW / intrinsicWidth, boxH / intrinsicHeight);
                drawW = intrinsicWidth * scale;
                drawH = intrinsicHeight * scale;
            }
            case "cover" -> {
                float scale = Math.max(boxW / intrinsicWidth, boxH / intrinsicHeight);
                drawW = intrinsicWidth * scale;
                drawH = intrinsicHeight * scale;
            }
            case "none" -> {
                drawW = intrinsicWidth;
                drawH = intrinsicHeight;
            }
            case "scale-down" -> {
                float scale = Math.min(boxW / intrinsicWidth, boxH / intrinsicHeight);
                if (scale < 1f) {
                    drawW = intrinsicWidth * scale;
                    drawH = intrinsicHeight * scale;
                } else {
                    drawW = intrinsicWidth;
                    drawH = intrinsicHeight;
                }
            }
            case "fill" -> {
                drawW = boxW;
                drawH = boxH;
            }
            default -> {
                drawW = boxW;
                drawH = boxH;
            }
        }

        float[] offset = parseObjectPosition(positionValue, boxW, boxH, drawW, drawH);
        return new Rect(boxX + offset[0], boxY + offset[1], drawW, drawH);
    }

    private static float[] parseObjectPosition(String value, float boxW, float boxH, float objectW, float objectH) {
        String normalized = value == null || value.isBlank() || "unset".equalsIgnoreCase(value.trim())
                ? "50% 50%"
                : value.trim().toLowerCase(Locale.ROOT);
        String[] parts = Layout.splitTopLevelWhitespace(normalized).toArray(String[]::new);
        String xToken = parts.length > 0 ? parts[0] : "50%";
        String yToken = parts.length > 1 ? parts[1] : "50%";

        if (parts.length == 1 && isVerticalPositionKeyword(xToken)) {
            yToken = xToken;
            xToken = "50%";
        } else if (parts.length == 1 && isHorizontalPositionKeyword(xToken)) {
            yToken = "50%";
        }

        float freeX = boxW - objectW;
        float freeY = boxH - objectH;
        return new float[]{
                resolveObjectPositionToken(xToken, freeX, true),
                resolveObjectPositionToken(yToken, freeY, false)
        };
    }

    private static float resolveObjectPositionToken(String token, float freeSpace, boolean horizontal) {
        if (token == null || token.isBlank()) return freeSpace * 0.5f;
        String value = token.trim().toLowerCase(Locale.ROOT);
        if ((horizontal && "left".equals(value)) || (!horizontal && "top".equals(value))) return 0;
        if ("center".equals(value)) return freeSpace * 0.5f;
        if ((horizontal && "right".equals(value)) || (!horizontal && "bottom".equals(value))) return freeSpace;
        if (value.endsWith("%")) {
            try {
                return freeSpace * Float.parseFloat(value.substring(0, value.length() - 1).trim()) / 100f;
            } catch (NumberFormatException ignored) {
                return freeSpace * 0.5f;
            }
        }
        String raw = value.endsWith("px") ? value.substring(0, value.length() - 2).trim() : value;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return freeSpace * 0.5f;
        }
    }

    private static boolean isHorizontalPositionKeyword(String token) {
        return "left".equals(token) || "right".equals(token) || "center".equals(token);
    }

    private static boolean isVerticalPositionKeyword(String token) {
        return "top".equals(token) || "bottom".equals(token) || "center".equals(token);
    }

    public record Rect(float x, float y, float width, float height) {
    }
}
