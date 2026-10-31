package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class BackgroundUniforms {
    public static final int MAX_STOPS = 16;
    public static final int VECTOR_COUNT = 27;
    public static final String[] NAMES = names();
    private static final Map<String, Integer> OFFSETS = offsets();
    private final float[] values = new float[VECTOR_COUNT * 4];

    private static String[] names() {
        String[] names = new String[VECTOR_COUNT];
        String[] base = {"BackgroundBox", "BackgroundTile", "BackgroundInfo", "BackgroundAxis",
                "BackgroundRadiiX", "BackgroundRadiiY", "BackgroundColor"};
        System.arraycopy(base, 0, names, 0, base.length);
        for (int i = 0; i < 4; i++) names[7 + i] = "BackgroundPositions" + i;
        for (int i = 0; i < MAX_STOPS; i++) names[11 + i] = "BackgroundColor" + i;
        return names;
    }

    private static Map<String, Integer> offsets() {
        Map<String, Integer> offsets = new HashMap<>();
        for (int i = 0; i < NAMES.length; i++) offsets.put(NAMES[i], i * 4);
        return offsets;
    }

    public boolean set(String name, float a, float b, float c, float d) {
        Integer offset = OFFSETS.get(name);
        if (offset == null) return false;
        values[offset] = a;
        values[offset + 1] = b;
        values[offset + 2] = c;
        values[offset + 3] = d;
        return true;
    }

    public void write(ByteBuffer buffer) {
        for (float value : values) buffer.putFloat(value);
    }

    public static void color(AuiRenderService render, String name, int color) {
        render.setShaderUniform4f(name, (color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
    }
}
