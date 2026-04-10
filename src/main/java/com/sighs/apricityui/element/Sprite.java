package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.style.Animation;

import java.util.*;
import java.util.regex.Pattern;

@ElementRegister(Sprite.TAG_NAME)
public class Sprite extends Div {
    public static final String TAG_NAME = "SPRITE";
    private static final Set<String> MANAGED_STYLE_KEYS = Set.of(
            "background-image", "background-repeat", "background-position", "background-size",
            "animation", "animation-name", "animation-duration", "animation-delay",
            "animation-iteration-count", "animation-direction", "animation-fill-mode",
            "animation-timing-function", "animation-play-state"
    );
    private static final Set<String> SPRITE_ATTRS = Set.of(
            "src", "steps", "direction", "duration", "loop", "steps-mode", "autoplay",
            "initialframe", "fit"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\+?([0-9]*\\.?[0-9]+)(s|ms)$");
    private static final String DEFAULT_DURATION = "1s";

    private boolean internalStyleSync = false;
    private String userInlineStyle = "";
    private String managedInlineStyle = "";

    static {
        Element.register(TAG_NAME, (document, _) -> new Sprite(document));
    }

    // true 表示等待异步图片尺寸就绪后再重建 sprite 动画。
    private boolean frameMetricsPending = false;
    private String pendingFrameMetricsSrc = "";
    // 避免同一尺寸异常日志反复刷屏。
    private final Set<String> invalidMetricsWarnings = new HashSet<>();

    public Sprite(Document document) {
        super(document);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        userInlineStyle = sanitizeUserStyle(getAttribute("style"));
        rebuildSpriteRuntime();
        applyManagedStyle();
    }

    @Override
    public void setAttribute(String name, String value) {
        if (internalStyleSync) {
            super.setAttribute(name, value);
            return;
        }

        String key = normalizeAttr(name);
        if ("style".equals(key)) {
            userInlineStyle = sanitizeUserStyle(value);
            rebuildSpriteRuntime();
            applyManagedStyle();
            return;
        }

        super.setAttribute(name, value);
        if (shouldRebuildForAttr(key)) {
            rebuildSpriteRuntime();
            applyManagedStyle();
        }
    }

    @Override
    public void removeAttribute(String name) {
        if (internalStyleSync) {
            super.removeAttribute(name);
            return;
        }

        String key = normalizeAttr(name);
        if ("style".equals(key)) {
            userInlineStyle = "";
            rebuildSpriteRuntime();
            applyManagedStyle();
            return;
        }

        super.removeAttribute(name);
        if (shouldRebuildForAttr(key)) {
            rebuildSpriteRuntime();
            applyManagedStyle();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!frameMetricsPending || pendingFrameMetricsSrc.isBlank()) return;

        var handle =
                ImageAsyncHandler.INSTANCE
                        .request(pendingFrameMetricsSrc, this, false);
        if (!isHandleReady(handle)) return;

        clearPendingFrameMetrics();
        rebuildSpriteRuntime();
        applyManagedStyle();
    }

    private boolean shouldRebuildForAttr(String key) {
        return "class".equals(key) || SPRITE_ATTRS.contains(key);
    }

    private void rebuildSpriteRuntime() {
        String resolvedSrc = resolveSpriteSource();
        if (resolvedSrc.isEmpty()) {
            clearPendingFrameMetrics();
            managedInlineStyle = "";
            return;
        }

        SpriteSpec.Direction direction = parseDirection(getAttr("direction"));
        int initialFrame = parseNonNegativeInt(getAttr("initialFrame"), 0);
        SpriteSpec.FitMode fitMode = parseFitMode(getAttr("fit"));

        int steps = parsePositiveInt(getAttr("steps"), -1);
        if (steps <= 0) {
            clearPendingFrameMetrics();
            managedInlineStyle = buildStaticManagedStyle(resolvedSrc, fitMode, direction, initialFrame, null);
            return;
        }

        FrameMetrics frameMetrics = resolveFrameMetrics(resolvedSrc, steps, direction);
        if (frameMetrics == null) {
            managedInlineStyle = buildStaticManagedStyle(resolvedSrc, fitMode, direction, initialFrame, null);
            return;
        }

        int clampedInitial = Math.min(initialFrame, Math.max(steps - 1, 0));
        SpriteSpec spec = new SpriteSpec(
                resolvedSrc,
                steps,
                direction,
                parseDuration(getAttr("duration")),
                parseLoop(getAttr("loop")),
                parseStepsMode(getAttr("steps-mode")),
                parseAutoplay(getAttr("autoplay")),
                frameMetrics.frameW(),
                frameMetrics.frameH(),
                clampedInitial,
                fitMode
        );

        Style baseStyle = buildBaseStyleWithoutManaged();
        managedInlineStyle = buildManagedStyle(spec, baseStyle);
    }

    // 将「用户样式 + 托管样式」合并后写回 style 属性。
    private void applyManagedStyle() {
        String mergedStyle = mergeStyle(userInlineStyle, managedInlineStyle);
        String currentStyle = getAttribute("style");
        if (Objects.equals(currentStyle, mergedStyle)) return;

        internalStyleSync = true;
        try {
            super.setAttribute("style", mergedStyle);
        } finally {
            internalStyleSync = false;
        }
    }

    // 计算基础样式（CSS 命中 + 用户 inline，不含 Sprite 托管键）。
    private Style buildBaseStyleWithoutManaged() {
        Style base = new Style();
        cssCache.forEach(base::update);
        base.merge(userInlineStyle);
        return base;
    }

    // 生成动画模式的托管样式。
    // 注意：duration 仅写入 sprite 这一段动画，外部 animation 段保留原值。
    private String buildManagedStyle(SpriteSpec spec, Style baseStyle) {
        LinkedHashMap<String, String> managed = new LinkedHashMap<>();
        managed.put("background-image", toCssUrl(spec.src()));
        managed.put("background-repeat", "no-repeat");
        managed.put("background-position", frameOffset(spec.direction(), spec.initialFrame(), spec.frameW(), spec.frameH()));
        managed.put("background-size", toBackgroundSize(spec.fit()));

        if (!spec.autoplay()) {
            return toStyleString(managed);
        }

        String spriteName = SpriteKeyframesRegistrar.ensureRegistered(document, spec);
        String spriteTiming = "steps(" + spec.steps() + ", " + spec.stepsMode().cssValue() + ")";
        String spriteAnimationSegment = spriteName + " " + spec.duration() + " " + spriteTiming + " " + spec.loop();
        String externalAnimation = valid(baseStyle.animation) ? baseStyle.animation.trim() : "";
        managed.put("animation", externalAnimation.isEmpty()
                ? spriteAnimationSegment
                : spriteAnimationSegment + ", " + externalAnimation);
        return toStyleString(managed);
    }

    private String buildStaticManagedStyle(String resolvedSrc, SpriteSpec.FitMode fitMode, SpriteSpec.Direction direction,
                                           int initialFrame, FrameMetrics frameMetrics) {
        LinkedHashMap<String, String> managed = new LinkedHashMap<>();
        managed.put("background-image", toCssUrl(resolvedSrc));
        managed.put("background-repeat", "no-repeat");
        managed.put("background-size", toBackgroundSize(fitMode));

        if (frameMetrics != null && initialFrame > 0) {
            managed.put("background-position", frameOffset(direction, initialFrame, frameMetrics.frameW(), frameMetrics.frameH()));
        } else {
            managed.put("background-position", "0px 0px");
        }
        return toStyleString(managed);
    }

    // 通过异步图片句柄推导帧尺寸；纹理未就绪时返回 null 并等待后续 tick 重建。
    private FrameMetrics resolveFrameMetrics(String resolvedSrc, int steps, SpriteSpec.Direction direction) {
        var handle =
                ImageAsyncHandler.INSTANCE
                        .request(resolvedSrc, this, false);
        if (!isHandleReady(handle)) {
            markPendingFrameMetrics(resolvedSrc);
            return null;
        }

        clearPendingFrameMetrics();
        int textureW = handle.texture().width();
        int textureH = handle.texture().height();
        if (textureW <= 0 || textureH <= 0) {
            warnInvalidFrameMetrics("图片尺寸非法", resolvedSrc, steps, direction, textureW, textureH);
            return null;
        }

        int frameW;
        int frameH;
        switch (direction) {
            case DOWN, UP -> {
                frameW = textureW;
                frameH = textureH / steps;
            }
            default -> {
                frameW = textureW / steps;
                frameH = textureH;
            }
        }

        if (frameW <= 0 || frameH <= 0) {
            warnInvalidFrameMetrics("推导后的帧尺寸非法", resolvedSrc, steps, direction, textureW, textureH);
            return null;
        }
        return new FrameMetrics(frameW, frameH);
    }

    private static boolean isHandleReady(com.sighs.apricityui.resource.async.image.ImageHandle handle) {
        return handle != null
                && handle.state() == com.sighs.apricityui.init.AbstractAsyncHandler.AsyncState.READY
                && handle.texture() != null;
    }

    private void markPendingFrameMetrics(String resolvedSrc) {
        frameMetricsPending = true;
        pendingFrameMetricsSrc = resolvedSrc;
    }

    private void clearPendingFrameMetrics() {
        frameMetricsPending = false;
        pendingFrameMetricsSrc = "";
    }

    private void warnInvalidFrameMetrics(String reason, String resolvedSrc, int steps,
                                         SpriteSpec.Direction direction, int textureW, int textureH) {
        String key = resolvedSrc + "|" + steps + "|" + direction + "|" + textureW + "x" + textureH + "|" + reason;
        if (!invalidMetricsWarnings.add(key)) return;
        ApricityUI.LOGGER.warn(
                "Sprite 帧尺寸推导失败：{}，src={}，steps={}，direction={}，texture={}x{}",
                reason, resolvedSrc, steps, direction, textureW, textureH
        );
    }

    // 解析并规范化 src（相对路径 -> 文档上下文绝对路径）。
    private String resolveSpriteSource() {
        String src = getAttr("src");
        if (src.isBlank()) return "";
        return Loader.resolve(document.getPath(), src);
    }

    // 读取属性值：优先精确命中，其次做「去横杠+小写」宽松匹配。
    private String getAttr(String name) {
        Map<String, String> attrs = getAttributes();
        String direct = attrs.getOrDefault(name, "");
        if (!direct.isBlank()) return direct.trim();

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            if (normalizeAttr(entry.getKey()).equals(normalizeAttr(name))) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && !"unset".equals(value);
    }

    // duration 支持 CSS 时间值；非法时回退默认 1s。
    private static String parseDuration(String raw) {
        if (!valid(raw)) return DEFAULT_DURATION;
        String duration = raw.trim().toLowerCase(Locale.ROOT);
        if (TIME_PATTERN.matcher(duration).matches()) return duration;
        return DEFAULT_DURATION;
    }

    // loop 支持 infinite 或正整数次数。
    private static String parseLoop(String raw) {
        if (!valid(raw)) return "infinite";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("infinite".equals(value)) return "infinite";
        int count = parsePositiveInt(value, -1);
        return count > 0 ? String.valueOf(count) : "infinite";
    }

    // 帧推进方向：决定 background-position 的偏移方向。
    private static SpriteSpec.Direction parseDirection(String raw) {
        if (!valid(raw)) return SpriteSpec.Direction.RIGHT;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> SpriteSpec.Direction.LEFT;
            case "up" -> SpriteSpec.Direction.UP;
            case "down" -> SpriteSpec.Direction.DOWN;
            default -> SpriteSpec.Direction.RIGHT;
        };
    }

    // steps-mode：默认 end。
    private static SpriteSpec.StepsMode parseStepsMode(String raw) {
        if (!valid(raw)) return SpriteSpec.StepsMode.END;
        return "start".equalsIgnoreCase(raw.trim())
                ? SpriteSpec.StepsMode.START
                : SpriteSpec.StepsMode.END;
    }

    // autoplay：false/0/no/off 视为关闭。
    private static boolean parseAutoplay(String raw) {
        if (!valid(raw)) return true;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return !("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value));
    }

    // fit 模式映射到 background-size。
    private static SpriteSpec.FitMode parseFitMode(String raw) {
        if (!valid(raw)) return SpriteSpec.FitMode.NONE;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "contain" -> SpriteSpec.FitMode.CONTAIN;
            case "cover" -> SpriteSpec.FitMode.COVER;
            case "stretch" -> SpriteSpec.FitMode.STRETCH;
            default -> SpriteSpec.FitMode.NONE;
        };
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseNonNegativeInt(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(parsed, 0);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // 计算某一帧对应的背景偏移。
    private static String frameOffset(SpriteSpec.Direction direction, int frameIndex, int frameW, int frameH) {
        int x = 0;
        int y = 0;
        int safeIndex = Math.max(frameIndex, 0);

        switch (direction) {
            case RIGHT -> x = -safeIndex * frameW;
            case LEFT -> x = safeIndex * frameW;
            case DOWN -> y = -safeIndex * frameH;
            case UP -> y = safeIndex * frameH;
        }
        return x + "px " + y + "px";
    }

    private static String toBackgroundSize(SpriteSpec.FitMode fit) {
        return switch (fit) {
            case CONTAIN -> "contain";
            case COVER -> "cover";
            case STRETCH -> "100% 100%";
            case NONE -> "auto";
        };
    }

    // 统一 url 输出格式：远程保持原样，本地路径补前导 /。
    private static String toCssUrl(String resolvedSrc) {
        if (Loader.isRemotePath(resolvedSrc)) return "url(\"" + resolvedSrc + "\")";
        return "url(\"/" + resolvedSrc + "\")";
    }

    // 清理用户 style 中由 Sprite 托管的键，避免双写冲突。
    private static String sanitizeUserStyle(String rawStyle) {
        LinkedHashMap<String, String> declarations = parseStyle(rawStyle);
        for (String key : MANAGED_STYLE_KEYS) {
            declarations.remove(key);
        }
        return toStyleString(declarations);
    }

    // 用户样式与托管样式合并（托管优先覆盖同名键）。
    private static String mergeStyle(String userStyle, String managedStyle) {
        LinkedHashMap<String, String> result = parseStyle(userStyle);
        result.putAll(parseStyle(managedStyle));
        return toStyleString(result);
    }

    // 把 style 字符串解析成有序 KV，便于后续覆盖和回写。
    private static LinkedHashMap<String, String> parseStyle(String rawStyle) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (rawStyle == null || rawStyle.isBlank()) return result;

        String[] entries = rawStyle.split(";");
        for (String entry : entries) {
            String part = entry.trim();
            if (part.isEmpty()) continue;

            int colonIndex = part.indexOf(':');
            if (colonIndex <= 0 || colonIndex >= part.length() - 1) continue;
            String key = part.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(colonIndex + 1).trim();
            if (key.isEmpty() || value.isEmpty()) continue;
            result.put(key, value);
        }
        return result;
    }

    // 序列化 style KV 为行内样式文本。
    private static String toStyleString(Map<String, String> declarations) {
        if (declarations == null || declarations.isEmpty()) return "";
        StringBuilder style = new StringBuilder();
        for (Map.Entry<String, String> entry : declarations.entrySet()) {
            style.append(entry.getKey()).append(":").append(entry.getValue()).append(";");
        }
        return style.toString();
    }

    // 属性名标准化：去掉 '-' 并转小写，便于兼容不同写法。
    private static String normalizeAttr(String name) {
        if (name == null) return "";
        return name.replace("-", "").toLowerCase(Locale.ROOT);
    }

    // 异步纹理就绪后推导出的单帧尺寸。
    private record FrameMetrics(int frameW, int frameH) {
    }

    // Sprite 解析后的配置快照。
    private record SpriteSpec(
            String src,
            int steps,
            Direction direction,
            String duration,
            String loop,
            StepsMode stepsMode,
            boolean autoplay,
            int frameW,
            int frameH,
            int initialFrame,
            FitMode fit
    ) {
        // 图集帧排列方向。
        private enum Direction {
            RIGHT,
            LEFT,
            UP,
            DOWN
        }

        // steps() 的模式参数。
        private enum StepsMode {
            START,
            END;

            String cssValue() {
                return this == START ? "start" : "end";
            }
        }

        // 背景适配模式。
        private enum FitMode {
            NONE,
            CONTAIN,
            COVER,
            STRETCH
        }
    }

    // 用于复用 keyframes 的唯一键（不包含 stepsMode 以提高复用率）。
    private record SpriteKey(
            String src,
            int steps,
            SpriteSpec.Direction direction,
            int frameW,
            int frameH
    ) {
    }

    // Document 级缓存：同一文档中相同 SpriteKey 只注册一次 keyframes。
    private static class SpriteKeyframesCache {
        private static final Map<Document, SpriteKeyframesCache> DOCUMENT_CACHES =
                Collections.synchronizedMap(new WeakHashMap<>());

        private final Map<SpriteKey, String> keyToName = new HashMap<>();
        private final Map<SpriteKey, Boolean> injected = new HashMap<>();

        static SpriteKeyframesCache of(Document document) {
            return DOCUMENT_CACHES.computeIfAbsent(document, ignored -> new SpriteKeyframesCache());
        }

        synchronized String resolveName(SpriteKey key) {
            return keyToName.computeIfAbsent(key, SpriteKeyframesCache::buildName);
        }

        // true 表示本次首次注入，需要实际写入 keyframes。
        synchronized boolean markInjectedIfNeeded(SpriteKey key) {
            if (Boolean.TRUE.equals(injected.get(key))) return false;
            injected.put(key, true);
            return true;
        }

        private static String buildName(SpriteKey key) {
            String raw = key.src() + "|" + key.steps() + "|" + key.direction() + "|" + key.frameW() + "|" + key.frameH();
            return "aui_sprite_" + Integer.toUnsignedString(raw.hashCode(), 16);
        }
    }

    // 负责把 SpriteSpec 转换为 keyframes，并写入 Animation 注册表。
    private static final class SpriteKeyframesRegistrar {
        private static String ensureRegistered(Document document, SpriteSpec spec) {
            SpriteKey key = new SpriteKey(spec.src(), spec.steps(), spec.direction(), spec.frameW(), spec.frameH());
            SpriteKeyframesCache cache = SpriteKeyframesCache.of(document);
            String name = cache.resolveName(key);
            if (cache.markInjectedIfNeeded(key)) {
                register(name, spec);
            }
            return name;
        }

        // 只生成 0% -> 100% 两帧，具体离散由 steps() timing-function 控制。
        private static void register(String animationName, SpriteSpec spec) {
            int dx = 0;
            int dy = 0;
            int travelX = spec.steps() * spec.frameW();
            int travelY = spec.steps() * spec.frameH();

            switch (spec.direction()) {
                case RIGHT -> dx = -travelX;
                case LEFT -> dx = travelX;
                case DOWN -> dy = -travelY;
                case UP -> dy = travelY;
            }

            Map<String, String> start = new HashMap<>();
            start.put("background-position", "0px 0px");
            Map<String, String> end = new HashMap<>();
            end.put("background-position", dx + "px " + dy + "px");

            Animation.registerKeyframe(animationName, 0.0, start);
            Animation.registerKeyframe(animationName, 100.0, end);
        }
    }
}
