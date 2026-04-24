package com.sighs.apricityui.instance;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class BASparkOverlay {
    private static final String DOC_PATH = "__runtime/baspark-canvas-overlay__.html";
    private static final String CANVAS_ID = "baspark-canvas";
    private static final String DOC_TEMPLATE = """
            <body style="position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;">
              <canvas id="baspark-canvas" style="position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;"></canvas>
            </body>
            """;

    private static final double BASE_FRAME_MS = 1000.0 / 60.0;
    private static final double MAX_DELTA_MS = 100.0;
    private static final double TRAIL_STEP = 2.0;
    private static final int MAX_TRAIL_POINTS = 16;
    private static final double EFFECT_RENDER_SCALE = 0.3;

    private static final List<WaveState> WAVES = new ArrayList<>();
    private static final List<SparkState> SPARKS = new ArrayList<>();
    private static final List<TrailPoint> TRAIL = new ArrayList<>();
    private static final List<WaveState> WAVE_POOL = new ArrayList<>();
    private static final List<SparkState> SPARK_POOL = new ArrayList<>();

    private static boolean mouseDown = false;
    private static Position lastTrailPos = null;
    private static long lastFrameMs = 0L;
    private static double effectScale = 1.5;
    private static double effectOpacity = 1.0;
    private static double effectSpeed = 1.0;
    private static String effectColor = "45,175,255";
    private static boolean alwaysTrailEnabled = false;

    private BASparkOverlay() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseInputEvent event) {
        if (event.getButton() != 0) return;

        Position pos = Client.getMousePositionDirectly();
        if (event.getAction() == InputConstants.PRESS) {
            mouseDown = true;
            lastTrailPos = pos;
            if (pos != null) {
                spawnBurst(pos.x, pos.y);
            }
            return;
        }

        if (event.getAction() == InputConstants.RELEASE) {
            mouseDown = false;
            lastTrailPos = null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (Minecraft.getInstance().getWindow() == null) return;

        Document document = ensureOverlayLoaded();
        Canvas canvas = resolveCanvas(document);
        if (canvas == null) return;

        updateOverlayLayout(document, canvas);
        if (lastFrameMs == 0L) {
            lastFrameMs = System.nanoTime();
        }
    }

    public static void renderFrame() {
        if (Minecraft.getInstance().getWindow() == null) return;

        Document document = ensureOverlayLoaded();
        Canvas canvas = resolveCanvas(document);
        if (canvas == null) return;

        updateOverlayLayout(document, canvas);

        long now = System.nanoTime();
        if (lastFrameMs == 0L) {
            lastFrameMs = now;
        }
        double deltaMs = Math.min((now - lastFrameMs) / 1_000_000.0, MAX_DELTA_MS);
        lastFrameMs = now;
        double frameScale = (deltaMs / BASE_FRAME_MS) * effectSpeed;

        updateTrailFromCursor();
        updateState(frameScale);
        render(canvas);
    }

    private static Document ensureOverlayLoaded() {
        List<Document> docs = Document.get(DOC_PATH);
        if (!docs.isEmpty()) {
            Document document = docs.get(0);
            document.setReloadPersistent(true);
            return document;
        }

        HTML.putTemple(DOC_PATH, DOC_TEMPLATE);
        Document document = Document.create(DOC_PATH);
        if (document != null) {
            document.setReloadPersistent(true);
        }
        return document;
    }

    private static Canvas resolveCanvas(Document document) {
        if (document == null) return null;
        var element = document.getElementById(CANVAS_ID);
        if (element instanceof Canvas canvas) {
            return canvas;
        }
        return null;
    }

    private static void updateOverlayLayout(Document document, Canvas canvas) {
        if (document == null || document.body == null || canvas == null) return;

        Size size = Client.getWindowSize();
        double dpr = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        int logicalWidth = Math.max(1, (int) Math.round(size.width()));
        int logicalHeight = Math.max(1, (int) Math.round(size.height()));
        int pixelWidth = Math.max(1, (int) Math.round(logicalWidth * dpr));
        int pixelHeight = Math.max(1, (int) Math.round(logicalHeight * dpr));

        document.body.setAttribute("style",
                "position:fixed;left:0;top:0;width:" + logicalWidth + "px;height:" + logicalHeight + "px;pointer-events:none;");

        canvas.setAttribute("style",
                "position:fixed;left:0;top:0;width:" + logicalWidth + "px;height:" + logicalHeight + "px;pointer-events:none;");
        if (canvas.getWidth() != pixelWidth) {
            canvas.setWidth(pixelWidth);
        }
        if (canvas.getHeight() != pixelHeight) {
            canvas.setHeight(pixelHeight);
        }
        canvas.getContext("2d").setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    private static void updateTrailFromCursor() {
        Position pos = Client.getMousePositionDirectly();
        if (pos == null) return;
        if (!mouseDown && !alwaysTrailEnabled) return;

        if (lastTrailPos == null) {
            lastTrailPos = pos;
            return;
        }

        double dx = pos.x - lastTrailPos.x;
        double dy = pos.y - lastTrailPos.y;
        double distance = Math.hypot(dx, dy);
        if (distance <= TRAIL_STEP) return;

        TRAIL.add(new TrailPoint(pos.x, pos.y, 1.0));
        if (TRAIL.size() > MAX_TRAIL_POINTS) {
            TRAIL.remove(0);
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            spawnDriftSpark(pos.x, pos.y);
        }
        lastTrailPos = pos;
    }

    private static void updateState(double frameScale) {
        for (Iterator<TrailPoint> iterator = TRAIL.iterator(); iterator.hasNext(); ) {
            TrailPoint point = iterator.next();
            double fade = alwaysTrailEnabled ? 0.085 : (mouseDown ? 0.085 : 0.18);
            point.life -= fade * frameScale;
            if (point.life <= 0.0) {
                iterator.remove();
            }
        }

        for (Iterator<WaveState> iterator = WAVES.iterator(); iterator.hasNext(); ) {
            WaveState wave = iterator.next();
            wave.life += frameScale;
            wave.ring.life += frameScale;
            wave.ring.ang -= wave.ring.rs * frameScale;
            if (wave.life >= wave.max && wave.ring.life >= wave.ring.maxLife) {
                WAVE_POOL.add(wave);
                iterator.remove();
            }
        }

        for (Iterator<SparkState> iterator = SPARKS.iterator(); iterator.hasNext(); ) {
            SparkState spark = iterator.next();
            spark.x += spark.vx * frameScale;
            spark.y += spark.vy * frameScale;
            spark.vx *= Math.pow(spark.f, frameScale);
            spark.vy *= Math.pow(spark.f, frameScale);
            spark.rot += spark.rs * frameScale;
            spark.a -= spark.fadeRate * frameScale;
            if (spark.a <= 0.0) {
                SPARK_POOL.add(spark);
                iterator.remove();
            }
        }
    }

    private static void render(Canvas canvas) {
        Canvas.CanvasRenderingContext2D ctx = canvas.getContext("2d");
        if (ctx == null) return;

        ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (WAVES.isEmpty() && SPARKS.isEmpty() && TRAIL.isEmpty()) return;

        ctx.setGlobalCompositeOperation("lighter");
        renderTrail(ctx);
        renderWaves(ctx);
        renderSparks(ctx);
        ctx.setGlobalCompositeOperation("source-over");
    }

    private static void renderTrail(Canvas.CanvasRenderingContext2D ctx) {
        if (TRAIL.size() < 2) return;

        double visualScale = visualScale();
        TrailPoint first = TRAIL.get(0);
        TrailPoint last = TRAIL.get(TRAIL.size() - 1);

        Canvas.CanvasLinearGradient glowGradient = ctx.createLinearGradient(first.x, first.y, last.x, last.y);
        glowGradient.addColorStop(0, "rgba(" + effectColor + ",0.10)");
        glowGradient.addColorStop(0.35, "rgba(" + effectColor + ",0.18)");
        glowGradient.addColorStop(1, "rgba(" + effectColor + ",0.30)");

        Canvas.CanvasLinearGradient coreGradient = ctx.createLinearGradient(first.x, first.y, last.x, last.y);
        coreGradient.addColorStop(0, "rgba(" + effectColor + ",0.16)");
        coreGradient.addColorStop(0.55, "rgba(" + effectColor + ",0.70)");
        coreGradient.addColorStop(1, "rgba(" + effectColor + ",0.98)");

        ctx.setLineWidth(7.0 * visualScale);
        ctx.setStrokeStyle(glowGradient);
        ctx.setShadowColor("rgba(" + effectColor + ",0.45)");
        ctx.setShadowBlur(4.5 * visualScale);
        ctx.setShadowOffsetX(0);
        ctx.setShadowOffsetY(0);
        strokeSmoothTrailPath(ctx);

        ctx.setShadowColor("transparent");
        ctx.setShadowBlur(0);

        ctx.setLineWidth(4.4 * visualScale);
        ctx.setStrokeStyle(coreGradient);
        strokeSmoothTrailPath(ctx);
    }

    private static void strokeSmoothTrailPath(Canvas.CanvasRenderingContext2D ctx) {
        if (TRAIL.size() < 2) return;

        ctx.beginPath();
        TrailPoint first = TRAIL.get(0);
        ctx.moveTo(first.x, first.y);

        if (TRAIL.size() == 2) {
            TrailPoint end = TRAIL.get(1);
            ctx.lineTo(end.x, end.y);
            ctx.stroke();
            return;
        }

        for (int i = 1; i < TRAIL.size() - 1; i++) {
            TrailPoint current = TRAIL.get(i);
            TrailPoint next = TRAIL.get(i + 1);
            double midX = (current.x + next.x) * 0.5;
            double midY = (current.y + next.y) * 0.5;
            ctx.quadraticCurveTo(current.x, current.y, midX, midY);
        }

        TrailPoint penultimate = TRAIL.get(TRAIL.size() - 2);
        TrailPoint last = TRAIL.get(TRAIL.size() - 1);
        ctx.quadraticCurveTo(penultimate.x, penultimate.y, last.x, last.y);
        ctx.stroke();
    }

    private static void renderWaves(Canvas.CanvasRenderingContext2D ctx) {
        double visualScale = visualScale();
        for (WaveState wave : WAVES) {
            double progress = clamp01(wave.life / wave.max);
            double ease = 1.0 - Math.pow(1.0 - progress, 3.0);
            wave.r = 26.0 * visualScale * ease;
            double alpha = clamp01(1.0 - progress);

            if (alpha > 0.0) {
                ctx.beginPath();
                ctx.arc(wave.x, wave.y, wave.r, 0, Math.PI * 2.0, false);
                ctx.setFillStyle("rgba(" + effectColor + "," + format(alphaValue(alpha)) + ")");
                ctx.fill();
            }

            double ringProgress = clamp01(wave.ring.life / wave.ring.maxLife);
            for (RingSegment segment : wave.ring.segs) {
                double shrink = Math.max(0.0, 1.0 - ringProgress);
                double length = segment.len * shrink;
                double start = wave.ring.ang + segment.off;
                ctx.beginPath();
                ctx.arc(wave.x, wave.y, wave.r + 3.0 * visualScale, start, start + length, false);
                ctx.setLineWidth(3.7 * 0.8 * visualScale);
                ctx.setStrokeStyle("rgba(245,248,252," + format(alphaValue(1.0 - ringProgress)) + ")");
                ctx.stroke();
            }
        }
    }

    private static void renderSparks(Canvas.CanvasRenderingContext2D ctx) {
        for (SparkState spark : SPARKS) {
            ctx.save();
            ctx.translate(spark.x, spark.y);
            ctx.rotate(spark.rot);
            ctx.beginPath();
            ctx.moveTo(0, -spark.s);
            ctx.lineTo(spark.s * 0.6, spark.s * 0.6);
            ctx.lineTo(-spark.s * 0.6, spark.s * 0.6);
            ctx.closePath();
            ctx.setFillStyle("rgba(255,255,255," + format(alphaValue(spark.a)) + ")");
            ctx.fill();
            ctx.restore();
        }
    }

    private static void spawnBurst(double x, double y) {
        WaveState wave;
        if (!WAVE_POOL.isEmpty()) {
            wave = WAVE_POOL.remove(WAVE_POOL.size() - 1);
            wave.x = x;
            wave.y = y;
            wave.life = 0.0;
            wave.max = 18.0;
            wave.r = 0.0;
            wave.ring.ang = randomAngle();
            wave.ring.life = 0.0;
        } else {
            wave = new WaveState(x, y);
        }
        WAVES.add(wave);

        int particleCount = 4;
        double visualScale = visualScale();
        double speedAdjust = visualScale / 1.5;
        for (int i = 0; i < particleCount; i++) {
            double angle = randomAngle();
            double speed = (4.8 + ThreadLocalRandom.current().nextDouble() * 2.0) * speedAdjust;
            SparkState spark = obtainSpark();
            spark.x = x;
            spark.y = y;
            spark.vx = Math.cos(angle) * speed;
            spark.vy = Math.sin(angle) * speed;
            spark.rot = randomAngle();
            spark.rs = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.28;
            spark.s = (4.0 + ThreadLocalRandom.current().nextDouble() * 3.0) * visualScale;
            spark.a = 1.0;
            spark.f = 0.9;
            spark.fadeRate = 0.032;
            SPARKS.add(spark);
        }
    }

    private static void spawnDriftSpark(double x, double y) {
        double angle = randomAngle();
        double visualScale = visualScale();
        double speedAdjust = visualScale / 1.5;
        SparkState spark = obtainSpark();
        spark.x = x + Math.cos(angle) * 10.0 * visualScale;
        spark.y = y + Math.sin(angle) * 10.0 * visualScale;
        spark.vx = Math.cos(angle) * 1.3 * speedAdjust;
        spark.vy = Math.sin(angle) * 1.3 * speedAdjust;
        spark.rot = randomAngle();
        spark.rs = 0.16;
        spark.s = 9.0 * visualScale;
        spark.a = 0.7;
        spark.f = 0.95;
        spark.fadeRate = 0.026;
        SPARKS.add(spark);
    }

    private static SparkState obtainSpark() {
        if (!SPARK_POOL.isEmpty()) {
            return SPARK_POOL.remove(SPARK_POOL.size() - 1);
        }
        return new SparkState();
    }

    private static double alphaValue(double value) {
        return clamp01(value * effectOpacity);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double randomAngle() {
        return ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
    }

    public static void setColor(String rgbString) {
        if (rgbString == null || rgbString.isBlank()) return;
        effectColor = rgbString;
    }

    public static void setEffectSettings(double scale, double opacity, double speed) {
        effectScale = Math.max(0.5, Math.min(3.0, scale));
        effectOpacity = Math.max(0.1, Math.min(1.0, opacity));
        effectSpeed = Math.max(0.2, Math.min(3.0, speed));
    }

    public static void setAlwaysTrailEnabled(boolean enabled) {
        alwaysTrailEnabled = enabled;
    }

    private static double visualScale() {
        return effectScale * EFFECT_RENDER_SCALE;
    }

    private static final class TrailPoint {
        private final double x;
        private final double y;
        private double life;

        private TrailPoint(double x, double y, double life) {
            this.x = x;
            this.y = y;
            this.life = life;
        }
    }

    private static final class WaveState {
        private double x;
        private double y;
        private double life;
        private double max = 18.0;
        private double r;
        private final RingState ring = new RingState();

        private WaveState(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class RingState {
        private double ang = randomAngle();
        private final RingSegment[] segs = new RingSegment[]{
                new RingSegment(-0.25 * Math.PI, 1.15 * Math.PI),
                new RingSegment(0.0, 1.15 * Math.PI),
                new RingSegment(0.25 * Math.PI, 1.15 * Math.PI)
        };
        private double life;
        private double maxLife = 30.0;
        private double rs = 0.08;
    }

    private record RingSegment(double off, double len) {
    }

    private static final class SparkState {
        private double x;
        private double y;
        private double vx;
        private double vy;
        private double rot;
        private double rs;
        private double s;
        private double a;
        private double f;
        private double fadeRate;
    }
}
