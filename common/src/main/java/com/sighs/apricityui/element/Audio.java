package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.media.AudioEngine;
import com.sighs.apricityui.media.AudioPlayer;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;

/**
 * `<audio>` 元素：HTMLAudioElement 语义的 DOM 包装。
 * 播放状态机在 {@link AudioPlayer}（纯 Java，headless 可测），本类负责：
 * src/preload/autoplay/loop/muted 属性反射、DOM 事件桥（播放器事件 →
 * Document 事件流，内联 onxxx 经 Element.installInlineEventHandlers 自动绑定）、
 * 文档级注册（AudioEngine，随文档关闭自动停止）。
 */
@ElementRegister(Audio.TAG_NAME)
public class Audio extends Element {
    public static final String TAG_NAME = "AUDIO";

    private final AudioPlayer player;
    private String observedSrc = null;
    private boolean observedAttrsInstalled;
    private boolean progressDragging;
    /** 拖拽中的视觉进度（每帧直读光标更新）；松手时落定到播放器并清空。 */
    private Double dragFraction = null;

    public Audio(Document document) {
        super(document, TAG_NAME);
        player = new AudioPlayer(this::dispatchMediaEvent);
        AudioEngine.register(document, player);
        installControlsListeners();
    }

    /** new Audio(src) 的游离实例也走这里：挂到 context document 注册表，不入 DOM 树。 */
    public AudioPlayer getPlayer() {
        return player;
    }

    @Override
    public void tick() {
        super.tick();
        syncFromAttributes();
        // 拖拽中的实际 seek 在 tick（安全上下文）落定：视觉进度每帧直读光标
        // （drawControls 的 dragFraction），避免在绘制期派发 seeking/seeked 事件。
        if (progressDragging && dragFraction != null) seekToFraction(dragFraction);
    }

    /**
     * 把 HTML 属性反射到播放器（浏览器语义：属性即 IDL 默认值）。
     * 游离实例（new Audio() 不入树）不会 tick，因此 play()/load() 也会调用本方法。
     */
    private void syncFromAttributes() {
        if (!observedAttrsInstalled) {
            observedAttrsInstalled = true;
            if (hasAttribute("preload")) player.setPreload(getAttribute("preload"));
            player.setAutoplay(hasAttribute("autoplay"));
            player.setLoop(hasAttribute("loop"));
            player.setMuted(hasAttribute("muted"));
        }

        String src = getAttribute("src");
        String resolved = src == null || src.isBlank() || document == null
                ? ""
                : Loader.resolve(document.getPath(), src);
        if (!resolved.equals(observedSrc == null ? "" : observedSrc)) {
            observedSrc = resolved;
            player.setSrc(resolved);
        }
    }

    private void dispatchMediaEvent(String type) {
        Event event = new Event(this, type, null, false);
        event.bubbles = false;
        Event.tiggerEvent(event);
    }

    // --------------------------------------------------------------
    // HTMLAudioElement IDL（供 JS 桥 / KubeJS 直调）

    public void load() {
        syncFromAttributes();
        player.load();
    }

    public AudioPlayer.AudioPlayPromise play() {
        syncFromAttributes();
        return player.play();
    }

    public void pause() {
        player.pause();
    }

    public double getCurrentTime() {
        return player.getCurrentTime();
    }

    public void setCurrentTime(double seconds) {
        player.setCurrentTime(seconds);
    }

    public double getDuration() {
        return player.getDuration();
    }

    public double getVolume() {
        return player.getVolume();
    }

    public void setVolume(double volume) {
        player.setVolume(volume);
    }

    public boolean isMuted() {
        return player.isMuted();
    }

    public void setMuted(boolean muted) {
        player.setMuted(muted);
    }

    public boolean isLoop() {
        return player.isLoop();
    }

    public void setLoop(boolean loop) {
        player.setLoop(loop);
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public boolean isEnded() {
        return player.isEnded();
    }

    public boolean isSeeking() {
        return player.isSeeking();
    }

    public int getReadyState() {
        return player.getReadyState();
    }

    public int getNetworkState() {
        return player.getNetworkState();
    }

    /** 移除时停止播放并释放后端资源。 */
    @Override
    public void remove() {
        player.destroy();
        AudioEngine.unregister(document, player);
        super.remove();
    }

    // --------------------------------------------------------------
    // controls 控件 UI（MVP：播放/暂停键 + 进度条点击/拖动 seek + 时间文本）
    // 照 Input RANGE 模式自绘，不进布局树子元素；无 controls 属性时不绘制。

    static final int HIT_NONE = 0;
    static final int HIT_BUTTON = 1;
    static final int HIT_TRACK = 2;

    private static final float CONTROL_PAD = 4f;
    private static final float CONTROL_BUTTON = 20f;
    private static final float CONTROL_GAP = 6f;
    private static final float CONTROL_TIME_WIDTH = 62f;
    private static final float CONTROL_DEPTH_OFFSET = 0.20f;

    public boolean hasControls() {
        return hasAttribute("controls");
    }

    private void installControlsListeners() {
        addInternalEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouse) || !hasControls()) return;
            if (mouse.button != -1 && mouse.button != 0) return;
            Element.DOMRect rect = getBoundingClientRect();
            if (rect == null) return;
            int hit = hitTestControls(mouse.clientX, mouse.clientY, rect.x, rect.y, rect.width, rect.height);
            if (hit == HIT_BUTTON) {
                togglePlay();
            } else if (hit == HIT_TRACK) {
                progressDragging = true;
                dragFraction = trackFractionAt(mouse.clientX, rect);
                seekToFraction(dragFraction);
            }
        });
        addInternalEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouse) || !progressDragging) return;
            // mousemove 是 20Hz tick 派发的旧缓存坐标；有每帧直读路径时让它接管，
            // 否则旧坐标会把每帧算出的 dragFraction 覆盖回去，滑块来回跳。
            if (livePointerInDocument() != null) return;
            Element.DOMRect rect = getBoundingClientRect();
            if (rect == null) return;
            dragFraction = trackFractionAt(mouse.clientX, rect);
            seekToFraction(dragFraction);
        });
        addInternalEventListener("mouseup", event -> {
            if (!progressDragging) return;
            if (dragFraction != null) seekToFraction(dragFraction);
            progressDragging = false;
            dragFraction = null;
        });
        addInternalEventListener("blur", event -> {
            progressDragging = false;
            dragFraction = null;
        });
    }

    private void togglePlay() {
        if (player.isPaused()) play();
        else pause();
    }

    private double trackFractionAt(double clientX, Element.DOMRect rect) {
        double trackLeft = rect.x + CONTROL_PAD + controlButtonSize(rect.height) + CONTROL_GAP;
        double trackRight = rect.x + rect.width - CONTROL_PAD - CONTROL_TIME_WIDTH;
        return Math.max(0.0d, Math.min(1.0d, (clientX - trackLeft) / Math.max(1.0d, trackRight - trackLeft)));
    }

    private void seekToFraction(double fraction) {
        double duration = player.getDuration();
        if (!Double.isFinite(duration) || duration <= 0) return;
        player.setCurrentTime(fraction * duration);
    }

    /**
     * 屏幕文档每帧直读 GLFW 光标（GUI 坐标 → 文档坐标）：mousemove 事件走输入轮询
     * 缓存，频率低、坐标可能滞后，拖拽会卡顿。世界窗指针坐标系不同（返回 null，
     * 仍走事件路径）；headless 无窗口也返回 null。
     */
    private Position livePointerInDocument() {
        if (document == null || document.inWorld) return null;
        Position mouse = com.sighs.apricityui.spi.AuiServices.client().getMousePositionDirectly();
        if (mouse == null) return null;
        return document.screenToDocumentPosition(mouse);
    }

    private static float controlButtonSize(double height) {
        return (float) Math.min(CONTROL_BUTTON, Math.max(0.0d, height - 2.0 * CONTROL_PAD));
    }

    /** 命中区域换算（纯几何，headless 可测）：按钮优先，其次整条进度带，其余落空。 */
    static int hitTestControls(double x, double y, double rx, double ry, double rw, double rh) {
        float button = controlButtonSize(rh);
        double bx = rx + CONTROL_PAD;
        double by = ry + (rh - button) / 2.0d;
        if (x >= bx && x <= bx + button && y >= by && y <= by + button) return HIT_BUTTON;
        double trackLeft = rx + CONTROL_PAD + button + CONTROL_GAP;
        double trackRight = rx + rw - CONTROL_PAD - CONTROL_TIME_WIDTH;
        if (trackRight > trackLeft && x >= trackLeft && x <= trackRight && y >= ry && y <= ry + rh) {
            return HIT_TRACK;
        }
        return HIT_NONE;
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase != Base.RenderPhase.BODY) return;
        rectRenderer.drawBody(poseStack);
        if (hasControls()) drawControls(poseStack, rectRenderer);
    }

    private void drawControls(PoseStack poseStack, Rect rectRenderer) {
        Position content = rectRenderer.getContentPosition();
        double width = Math.max(0.0d, rectRenderer.box.innerSize().width());
        double height = Math.max(0.0d, rectRenderer.box.innerSize().height());
        float button = controlButtonSize(height);
        float bx = (float) content.x + CONTROL_PAD;
        float by = (float) (content.y + (height - button) / 2.0d);
        float centerY = (float) (content.y + height / 2.0d);
        float trackLeft = bx + button + CONTROL_GAP;
        float trackRight = (float) (content.x + width - CONTROL_PAD - CONTROL_TIME_WIDTH);
        if (trackRight < trackLeft) trackRight = trackLeft;

        // 菱形 + 紫白默认配色（accentColor 可覆盖紫）
        int accent = resolveAccentColor();
        int white = new Color("#FFFFFF").getValue();
        int trackBase = new Color("#DDD6FE").getValue();

        // 播放/暂停键：紫色菱形底 + 白色图标
        float buttonCx = bx + button / 2.0f;
        Graph.drawDiamond(poseStack.last().pose(), buttonCx, centerY, button * 1.25f, accent);
        Base.offsetPaintDepth(poseStack, CONTROL_DEPTH_OFFSET);
        if (player.isPaused()) drawPlayTriangle(poseStack, bx, by, button, white);
        else drawPauseBars(poseStack, bx, by, button, white);

        // 进度条：浅紫轨道（4.5px，加粗一半）+ 紫填充 + 菱形滑块。
        // 拖拽中每帧直读光标更新 dragFraction（丝滑），实际 seek 仍由事件路径/松手时落定。
        if (progressDragging) {
            Position live = livePointerInDocument();
            if (live != null) {
                dragFraction = Math.max(0.0d, Math.min(1.0d,
                        (live.x - trackLeft) / Math.max(1.0d, (double) trackRight - trackLeft)));
            }
        }
        double duration = player.getDuration();
        double fraction = dragFraction != null ? dragFraction
                : (Double.isFinite(duration) && duration > 0
                        ? Math.max(0.0d, Math.min(1.0d, player.getCurrentTime() / duration))
                        : 0.0d);
        Graph.drawFillRect(poseStack.last().pose(), trackLeft, centerY - 2.25f, trackRight, centerY + 2.25f, trackBase);
        if (fraction > 0) {
            Graph.drawFillRect(poseStack.last().pose(), trackLeft, centerY - 2.25f,
                    trackLeft + (float) ((trackRight - trackLeft) * fraction), centerY + 2.25f, accent);
        }
        Base.offsetPaintDepth(poseStack, CONTROL_DEPTH_OFFSET);
        float knobX = trackLeft + (float) ((trackRight - trackLeft) * fraction);
        Graph.drawDiamond(poseStack.last().pose(), knobX, centerY, 9f, accent);

        // 时间文本（拖拽中显示拖拽落点）
        double shownTime = dragFraction != null && Double.isFinite(duration) && duration > 0
                ? dragFraction * duration
                : player.getCurrentTime();
        Text text = Text.of(this);
        text.content = formatTime(shownTime) + " / " + formatTime(duration);
        text.color = new Color(Text.getFontColor(this));
        double textY = content.y + (height - text.lineHeight) / 2.0d;
        FontDrawer.drawFont(poseStack, text, new Position(trackRight + CONTROL_GAP, textY));
    }

    /** 右向播放三角：逐行矩形，中线最宽、上下收尖。 */
    private static void drawPlayTriangle(PoseStack poseStack, float bx, float by, float size, int color) {
        float triW = size * 0.42f;
        float triH = size * 0.52f;
        float x0 = bx + (size - triW) / 2.0f + size * 0.04f;
        float y0 = by + (size - triH) / 2.0f;
        int rows = Math.max(1, Math.round(triH));
        for (int row = 0; row < rows; row++) {
            float distanceFromCenter = Math.abs(row + 0.5f - rows / 2.0f) / (rows / 2.0f);
            float rowWidth = triW * (1.0f - distanceFromCenter);
            if (rowWidth <= 0) continue;
            Graph.drawFillRect(poseStack.last().pose(), x0, y0 + row, x0 + rowWidth, y0 + row + 1f, color);
        }
    }

    private static void drawPauseBars(PoseStack poseStack, float bx, float by, float size, int color) {
        float barW = size * 0.14f;
        float barH = size * 0.48f;
        float gap = size * 0.12f;
        float x0 = bx + (size - barW * 2 - gap) / 2.0f;
        float y0 = by + (size - barH) / 2.0f;
        Graph.drawFillRect(poseStack.last().pose(), x0, y0, x0 + barW, y0 + barH, color);
        Graph.drawFillRect(poseStack.last().pose(), x0 + barW + gap, y0, x0 + barW * 2 + gap, y0 + barH, color);
    }

    /** m:ss；未就绪（NaN/负值）显示 "--:--"。 */
    static String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "--:--";
        long total = Math.round(seconds);
        long minutes = total / 60;
        long secs = total % 60;
        return minutes + ":" + (secs < 10 ? "0" : "") + secs;
    }

    private int resolveAccentColor() {
        String accent = getComputedStyle().accentColor;
        if (accent == null || accent.isBlank() || "unset".equalsIgnoreCase(accent)
                || "auto".equalsIgnoreCase(accent)) {
            accent = "#8b5cf6"; // 默认紫（资源管理器同款）
        }
        return new Color(accent).getValue();
    }
}
