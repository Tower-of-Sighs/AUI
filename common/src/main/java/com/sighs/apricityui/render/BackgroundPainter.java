package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.style.Background;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BackgroundPainter {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_SIDE = 4096;
    private static final Map<Background, Plan> PLANS = new WeakHashMap<>();
    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(16, .75f, true);
    private static volatile long generation;
    private static long activeGeneration;
    private static AuiRenderService owner;
    private static Object activeShader;
    private static FboHandle emptySampler;
    private static long frame;
    private static long bytes;
    private static long hits;
    private static long builds;
    private static long shaderDraws;

    private BackgroundPainter() {
    }

    public static void invalidate() {
        generation++;
    }

    public static void beginFrame() {
        frame++;
        Iterator<Entry> entries = CACHE.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            if (frame - entry.lastFrame > 300) {
                release(entry);
                entries.remove();
            }
        }
    }

    public static boolean draw(Matrix4f matrix, float x, float y, float width, float height,
                               float[] radii, Background background) {
        if (!Boolean.parseBoolean(System.getProperty("apricityui.background.shader", "true"))) return false;
        if (!(width > 0 && height > 0) || !Float.isFinite(width + height)) return false;
        AuiRenderService render = AuiServices.render();
        Object shader = render.getBackgroundShader();
        if (shader == null || !supported(background)) return false;
        if (owner != render || activeShader != shader || activeGeneration != generation) {
            clear();
            owner = render;
            activeShader = shader;
            activeGeneration = generation;
        }
        long signature = signature(background);
        Plan plan = PLANS.get(background);
        if (plan == null || plan.signature != signature || plan.width != width || plan.height != height) {
            plan = new Plan(background, signature, width, height);
            PLANS.put(background, plan);
        }
        Key key = key(background, signature, matrix, width, height, radii);
        Entry entry = key == null || !Boolean.parseBoolean(System.getProperty("apricityui.background.cache", "true"))
                ? null : CACHE.get(key);
        Graph.endBatch();
        ImageDrawer.flushBatch();
        if (entry != null) {
            entry.lastFrame = frame;
            hits++;
        } else if (key != null && Boolean.parseBoolean(System.getProperty("apricityui.background.cache", "true"))) {
            if (key.equals(plan.previousKey)) {
                if (plan.lastFrame != frame) plan.stableFrames++;
            } else {
                plan.previousKey = key;
                plan.stableFrames = 1;
            }
            plan.lastFrame = frame;
            if (plan.stableFrames >= 2) entry = build(key, plan, radii, render, shader);
        }
        if (!ensureSampler(render)) return false;
        Base.beginRendering();
        try {
            render.setShader(shader);
            render.setShaderColor(1, 1, 1, 1);
            render.setBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (background.getLayers().size() > 1) {
                render.disableDepthTest();
                render.setDepthMask(false);
            }
            setupBox(render, width, height, radii);
            if (entry != null) {
                render.bindColorTexture(entry.target, 0);
                render.setShaderUniform4f("BackgroundInfo", -1, 0, 0, 0);
                quad(render, matrix, x, y, width, height);
            } else {
                render.bindColorTexture(emptySampler, 0);
                paint(render, plan, matrix, x, y);
            }
        } finally {
            if (Base.isDepthTestEnabled()) render.enableDepthTest();
            else render.disableDepthTest();
            render.setDepthMask(Base.isDepthTestEnabled());
            Base.finishRendering();
            Base.setPositionColorShader();
        }
        return true;
    }

    static boolean supported(Background background) {
        if (background.getLayers().isEmpty()) return false;
        boolean gradient = false;
        for (Background.Layer layer : background.getLayers()) {
            if (layer == null) continue;
            if (layer.imagePath != null && !"unset".equals(layer.imagePath)) return false;
            if (layer.gradient != null) {
                if (layer.gradient.stops().isEmpty() || layer.gradient.stops().size() > BackgroundUniforms.MAX_STOPS) return false;
                gradient = true;
            }
        }
        return gradient;
    }

    private static boolean ensureSampler(AuiRenderService render) {
        if (emptySampler != null) return true;
        Matrix4f projection = new Matrix4f(Base.getProjectionMatrix());
        try (AuiRenderService.RenderStateScope scope = render.pushBackgroundTarget(null)) {
            if (scope == null) return false;
            emptySampler = render.createOffscreenTarget(1, 1, false);
            render.bindWrite(emptySampler, true);
            render.clear(emptySampler, 0, 0, 0, 0);
            render.resolveTarget(emptySampler);
        } finally {
            Base.setProjectionMatrix(projection);
        }
        return true;
    }

    private static Entry build(Key key, Plan plan, float[] radii, AuiRenderService render, Object shader) {
        if (!ensureSampler(render)) return null;
        long required = (long) key.pixelWidth * key.pixelHeight * 4;
        trim(required);
        Matrix4f projection = new Matrix4f(Base.getProjectionMatrix());
        FboHandle target = null;
        boolean complete = false;
        try (AuiRenderService.RenderStateScope scope = render.pushBackgroundTarget(null)) {
            if (scope == null) return null;
            target = render.createOffscreenTarget(key.pixelWidth, key.pixelHeight, false);
            render.bindWrite(target, true);
            render.clear(target, 0, 0, 0, 0);
            render.bindWrite(target, true);
            Base.setProjectionMatrix(new Matrix4f().setOrtho(0, plan.width, plan.height, 0, -1, 1));
            render.disableDepthTest();
            render.setDepthMask(false);
            render.disableCull();
            render.enableBlend();
            render.setBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            render.setShader(shader);
            render.setShaderColor(1, 1, 1, 1);
            render.bindColorTexture(emptySampler, 0);
            setupBox(render, plan.width, plan.height, radii);
            paint(render, plan, new Matrix4f(), 0, 0);
            render.resolveTarget(target);
            complete = true;
        } finally {
            Base.setProjectionMatrix(projection);
            if (!complete && target != null) render.destroyBackgroundTarget(target);
        }
        Entry entry = new Entry(target, required, frame);
        CACHE.put(key, entry);
        bytes += required;
        builds++;
        return entry;
    }

    private static void setupBox(AuiRenderService render, float width, float height, float[] radii) {
        float[] r = Graph.expandRadii(radii);
        render.setShaderUniform4f("BackgroundBox", width, height, 0, 0);
        render.setShaderUniform4f("BackgroundRadiiX", r[0], r[2], r[4], r[6]);
        render.setShaderUniform4f("BackgroundRadiiY", r[1], r[3], r[5], r[7]);
    }

    private static void paint(AuiRenderService render, Plan plan, Matrix4f matrix, float x, float y) {
        if (plan.color != 0) {
            BackgroundUniforms.color(render, "BackgroundColor", plan.color);
            render.setShaderUniform4f("BackgroundInfo", 0, 0, 0, 0);
            quad(render, matrix, x, y, plan.width, plan.height);
        }
        for (int index = plan.layers.size() - 1; index >= 0; index--) {
            Layer layer = plan.layers.get(index);
            List<Gradient.Stop> stops = layer.gradient.stops();
            render.setShaderUniform4f("BackgroundTile", layer.tile.x(), layer.tile.y(), layer.tile.width(), layer.tile.height());
            render.setShaderUniform4f("BackgroundAxis", layer.axisX, layer.axisY, layer.axisLength, 0);
            render.setShaderUniform4f("BackgroundInfo", stops.size(), layer.gradient.repeating() ? 1 : 0,
                    layer.repeatX ? 1 : 0, layer.repeatY ? 1 : 0);
            for (int group = 0; group * 4 < stops.size(); group++) {
                int i = group * 4;
                render.setShaderUniform4f(BackgroundUniforms.NAMES[7 + group],
                        position(stops, i), position(stops, i + 1), position(stops, i + 2), position(stops, i + 3));
            }
            for (int i = 0; i < stops.size(); i++) {
                BackgroundUniforms.color(render, BackgroundUniforms.NAMES[11 + i], stops.get(i).color);
            }
            quad(render, matrix, x, y, plan.width, plan.height);
            shaderDraws++;
        }
    }

    private static float position(List<Gradient.Stop> stops, int index) {
        return index < stops.size() ? stops.get(index).position : 1;
    }

    private static void quad(AuiRenderService render, Matrix4f matrix, float x, float y, float w, float h) {
        MeshBuilder mesh = render.beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
        mesh.vertexUV(matrix, x, y + h, 0, 0, 1);
        mesh.vertexUV(matrix, x + w, y + h, 0, 1, 1);
        mesh.vertexUV(matrix, x + w, y, 0, 1, 0);
        mesh.vertexUV(matrix, x, y, 0, 0, 0);
        mesh.submit();
        RenderBatchStats.recordGraphFlush();
    }

    static Key key(Background background, long signature, Matrix4f matrix, float width, float height, float[] radii) {
        Matrix4f projection = Base.getProjectionMatrix();
        if (projection == null || !matrix.isAffine() || Math.abs(projection.m23()) > .000001f) return null;
        double sx = Math.hypot(projection.m00() * matrix.m00(), projection.m11() * matrix.m01());
        double sy = Math.hypot(projection.m00() * matrix.m10(), projection.m11() * matrix.m11());
        double pixelWidth = Math.ceil(width * sx * AuiServices.client().getWindowWidth() * .5);
        double pixelHeight = Math.ceil(height * sy * AuiServices.client().getWindowHeight() * .5);
        if (!Double.isFinite(pixelWidth + pixelHeight) || pixelWidth < 1 || pixelHeight < 1
                || pixelWidth > MAX_SIDE || pixelHeight > MAX_SIDE || pixelWidth * pixelHeight > MAX_BYTES / 8) return null;
        float[] r = Graph.expandRadii(radii);
        return new Key(signature, width, height, (int) pixelWidth, (int) pixelHeight,
                r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7]);
    }

    static long signature(Background background) {
        long value = mix(0xcbf29ce484222325L, background.color);
        for (Background.Layer layer : background.getLayers()) {
            if (layer == null) continue;
            value = mix(mix(mix(value, layer.size), layer.position), layer.repeat);
            if (layer.gradient == null) continue;
            value = mix(value, Float.floatToIntBits(layer.gradient.angle()));
            value = mix(value, layer.gradient.repeating() ? 1 : 0);
            for (Gradient.Stop stop : layer.gradient.stops()) {
                value = mix(mix(mix(value, Float.floatToIntBits(stop.position)), stop.color), stop.absolutePx ? 1 : 0);
            }
        }
        return value;
    }

    private static long mix(long value, String text) {
        if (text == null) return mix(value, -1);
        for (int i = 0; i < text.length(); i++) value = mix(value, text.charAt(i));
        return mix(value, text.length());
    }

    private static long mix(long value, int number) {
        return (value ^ number) * 0x100000001b3L;
    }

    private static void trim(long required) {
        Iterator<Entry> entries = CACHE.values().iterator();
        while (entries.hasNext() && (bytes + required > MAX_BYTES || CACHE.size() >= MAX_ENTRIES)) {
            release(entries.next());
            entries.remove();
        }
    }

    private static void release(Entry entry) {
        owner.destroyBackgroundTarget(entry.target);
        bytes -= entry.bytes;
    }

    public static void clear() {
        for (Entry entry : CACHE.values()) release(entry);
        CACHE.clear();
        PLANS.clear();
        if (emptySampler != null && owner != null) owner.destroyBackgroundTarget(emptySampler);
        emptySampler = null;
        bytes = 0;
    }

    public static long cacheHits() { return hits; }
    public static long cacheBuilds() { return builds; }
    public static long cacheBytes() { return bytes; }
    public static long gradientDraws() { return shaderDraws; }

    record Key(long signature, float width, float height, int pixelWidth, int pixelHeight,
               float tlx, float tly, float trx, float tryRadius, float brx, float bry, float blx, float bly) {
    }

    private static final class Entry {
        final FboHandle target;
        final long bytes;
        long lastFrame;

        Entry(FboHandle target, long bytes, long lastFrame) {
            this.target = target;
            this.bytes = bytes;
            this.lastFrame = lastFrame;
        }
    }

    private static final class Plan {
        final long signature;
        final float width;
        final float height;
        final int color;
        final List<Layer> layers = new ArrayList<>();
        Key previousKey;
        long lastFrame = Long.MIN_VALUE;
        int stableFrames;

        Plan(Background background, long signature, float width, float height) {
            this.signature = signature;
            this.width = width;
            this.height = height;
            color = "unset".equals(background.color) ? 0 : new Color(background.color).getValue();
            for (Background.Layer layer : background.getLayers()) {
                if (layer != null && layer.gradient != null) layers.add(new Layer(layer, width, height));
            }
        }
    }

    private static final class Layer {
        final ImageDrawer.GradientTile tile;
        final Gradient gradient;
        final float axisX;
        final float axisY;
        final float axisLength;
        final boolean repeatX;
        final boolean repeatY;

        Layer(Background.Layer layer, float width, float height) {
            tile = ImageDrawer.resolveGradientTile(layer, width, height);
            gradient = layer.gradient.scaledTo(tile.width(), tile.height());
            double radians = Math.toRadians(90f - gradient.angle());
            axisX = (float) Math.cos(radians);
            axisY = (float) -Math.sin(radians);
            axisLength = Math.max(.0001f, Math.abs(tile.width() * axisX) + Math.abs(tile.height() * axisY));
            String repeat = layer.repeat == null ? "" : layer.repeat.trim();
            repeatX = tile.repeats() && !"repeat-y".equalsIgnoreCase(repeat);
            repeatY = tile.repeats() && !"repeat-x".equalsIgnoreCase(repeat);
        }
    }
}
