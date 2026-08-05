package com.sighs.apricityui.ui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reusable cursor-following tooltip owned by the built-in UI layer. */
public final class Tooltip {
    private static final double VIEWPORT_GAP = 6;
    private static final int Z_INDEX = 11000;
    private static final String BASE_STYLE =
            "position:fixed;z-index:" + Z_INDEX + ";display:inline-block;box-sizing:border-box;pointer-events:none;" +
                    "padding:8px 10px;min-width:40px;background:#ffffff;color:#1a1a1a;" +
                    "border:2px solid #1a1a1a;border-left:3px solid #8b5cf6;" +
                    "box-shadow:4px 4px 0 rgba(139,92,246,0.25);" +
                    "font-family:'Microsoft YaHei',sans-serif;font-size:11px;line-height:15px;" +
                    "font-weight:600;letter-spacing:0;white-space:normal;overflow-wrap:break-word;";

    private static Tooltip activeTooltip;

    public record Options(String className, String style, double offsetX, double offsetY, double maxWidth) {
        public static Options defaults() {
            return new Options("aui-tooltip", "", 14, 18, 320);
        }

        private Options normalize() {
            Options defaults = defaults();
            return new Options(
                    className == null || className.isBlank() ? defaults.className() : className.trim(),
                    style == null ? "" : style,
                    Double.isFinite(offsetX) ? offsetX : defaults.offsetX(),
                    Double.isFinite(offsetY) ? offsetY : defaults.offsetY(),
                    Double.isFinite(maxWidth) && maxWidth > 0 ? maxWidth : defaults.maxWidth()
            );
        }
    }

    /** Removable event binding returned by {@link #bind}. */
    public static final class Binding implements AutoCloseable {
        private final Element target;
        private final Consumer<Event> enterListener;
        private final Consumer<Event> moveListener;
        private final Consumer<Event> leaveListener;
        private boolean closed;

        private Binding(Element target, Supplier<String> text, Options options) {
            this.target = target;
            enterListener = event -> showForEvent(target, text, null, options, event);
            moveListener = event -> showForEvent(target, text, null, options, event);
            leaveListener = event -> hideOwnedBy(target);
            target.addEventListener("mouseenter", enterListener);
            target.addEventListener("mousemove", moveListener);
            target.addEventListener("mouseleave", leaveListener);
        }

        private Binding(Element target, String translationKey, Options options) {
            this.target = target;
            enterListener = event -> showForEvent(target, null, translationKey, options, event);
            moveListener = event -> showForEvent(target, null, translationKey, options, event);
            leaveListener = event -> hideOwnedBy(target);
            target.addEventListener("mouseenter", enterListener);
            target.addEventListener("mousemove", moveListener);
            target.addEventListener("mouseleave", leaveListener);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            target.removeEventListener("mouseenter", enterListener);
            target.removeEventListener("mousemove", moveListener);
            target.removeEventListener("mouseleave", leaveListener);
            hideOwnedBy(target);
        }
    }

    private final Document document;
    private final Options options;
    private final Element owner;
    private final String text;
    private final String translationKey;
    private final Size measuredSize;
    private Element element;
    private Position pointer;
    private boolean closed;

    private Tooltip(Document document, Element owner, Position pointer, String text, String translationKey, Options options) {
        this.document = document;
        this.owner = owner;
        this.pointer = pointer == null ? Position.ZERO : pointer;
        this.options = (options == null ? Options.defaults() : options).normalize();
        this.text = text == null ? "" : text;
        this.translationKey = translationKey == null ? "" : translationKey;
        this.measuredSize = estimateSize(this.translationKey.isBlank() ? this.text : this.translationKey, this.options.maxWidth());
        mount();
    }

    public static Tooltip show(Document document, Position pointer, String text) {
        return show(document, pointer, text, Options.defaults());
    }

    public static synchronized Tooltip show(Document document, Position pointer, String text, Options options) {
        return replace(document, null, pointer, text, null, options);
    }

    public static synchronized Tooltip showTranslation(Document document, Position pointer, String translationKey, Options options) {
        return replace(document, null, pointer, null, translationKey, options);
    }

    public static Binding bind(Element target, String text) {
        return bind(target, () -> text, Options.defaults());
    }

    public static Binding bind(Element target, Supplier<String> text) {
        return bind(target, text, Options.defaults());
    }

    public static Binding bind(Element target, Supplier<String> text, Options options) {
        if (target == null) throw new IllegalArgumentException("Tooltip target cannot be null");
        Supplier<String> safeText = text == null ? () -> "" : text;
        return new Binding(target, safeText, options == null ? Options.defaults() : options);
    }

    public static Binding bindTranslation(Element target, String translationKey) {
        return bindTranslation(target, translationKey, Options.defaults());
    }

    public static Binding bindTranslation(Element target, String translationKey, Options options) {
        String key = translationKey == null ? "" : translationKey;
        if (target == null) throw new IllegalArgumentException("Tooltip target cannot be null");
        return new Binding(target, key, options == null ? Options.defaults() : options);
    }

    public static synchronized void hide() {
        if (activeTooltip != null) activeTooltip.close();
    }

    public static synchronized void hide(Document document) {
        if (activeTooltip != null && activeTooltip.document == document) activeTooltip.close();
    }

    public static synchronized void moveActive(Position pointer) {
        if (activeTooltip != null) activeTooltip.move(pointer);
    }

    /** Moves the active tooltip from Minecraft GUI coordinates into its document viewport. */
    public static synchronized void moveActiveFromScreen(Position screenPointer) {
        if (activeTooltip == null || screenPointer == null) return;
        Position documentPointer = activeTooltip.document == null
                ? screenPointer
                : activeTooltip.document.screenToDocumentPosition(screenPointer);
        activeTooltip.move(documentPointer);
    }

    public boolean isVisible() {
        return !closed && element != null && element.isConnected();
    }

    public void move(Position nextPointer) {
        if (!isVisible() || nextPointer == null) return;
        if (Double.compare(pointer.x, nextPointer.x) == 0
                && Double.compare(pointer.y, nextPointer.y) == 0) return;
        pointer = nextPointer;
        position();
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (element != null) element.remove();
        element = null;
        synchronized (Tooltip.class) {
            if (activeTooltip == this) activeTooltip = null;
        }
        markDirty(document == null ? null : document.body);
    }

    private void mount() {
        if (document == null || document.body == null || (text.isBlank() && translationKey.isBlank())) {
            closed = true;
            return;
        }
        element = Element.init(document.createElement("DIV"));
        element.setTopLayer(true);
        element.setAttribute("class", options.className());
        element.setAttribute("role", "tooltip");
        if (translationKey.isBlank()) {
            element.setTextContent(text);
        } else {
            Element translation = Element.init(document.createElement("TRANSLATION"));
            translation.setTextContent(translationKey);
            element.appendChild(translation);
        }
        document.body.append(element);
        applyStyle(pointer.x + options.offsetX(), pointer.y + options.offsetY());
        position();
        markDirty(element);
        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            if (isVisible()) position();
            return true;
        });
    }

    private void position() {
        if (element == null) return;
        Size measured = measuredSize;
        double viewportWidth = document.getViewport().layoutWidth();
        double viewportHeight = document.getViewport().layoutHeight();
        if (viewportWidth <= 1) viewportWidth = 1920;
        if (viewportHeight <= 1) viewportHeight = 1080;
        double width = Math.min(Math.max(0, measured.width()), options.maxWidth());
        double height = Math.max(0, measured.height());
        double left = pointer.x + options.offsetX();
        double top = pointer.y + options.offsetY();
        if (left + width + VIEWPORT_GAP > viewportWidth) left = pointer.x - width - options.offsetX();
        if (top + height + VIEWPORT_GAP > viewportHeight) top = pointer.y - height - options.offsetY();
        left = clamp(left, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportWidth - width - VIEWPORT_GAP));
        top = clamp(top, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportHeight - height - VIEWPORT_GAP));
        applyStyle(left, top);
        markDirty(element);
    }

    private void applyStyle(double left, double top) {
        element.setAttribute("style", BASE_STYLE + "width:" + px(measuredSize.width()) + ";max-width:"
                + px(options.maxWidth()) + ";left:" + px(left)
                + ";top:" + px(top) + ";" + options.style());
    }

    private static Size estimateSize(String text, double maxWidth) {
        double contentLimit = Math.max(20, maxWidth - 24);
        double totalWidth = 0;
        double longestLine = 0;
        int wrappedLines = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                longestLine = Math.max(longestLine, totalWidth);
                wrappedLines += Math.max(1, (int) Math.ceil(totalWidth / contentLimit));
                totalWidth = 0;
                continue;
            }
            totalWidth += isWideCodePoint(codePoint) ? 11 : 6.5;
        }
        longestLine = Math.max(longestLine, totalWidth);
        wrappedLines += Math.max(1, (int) Math.ceil(totalWidth / contentLimit));
        double width = Math.min(maxWidth, Math.max(40, longestLine + 24));
        return new Size(width, wrappedLines * 15 + 20);
    }

    private static boolean isWideCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static void showForEvent(Element owner, Supplier<String> supplier, String translationKey, Options options, Event event) {
        if (!(event instanceof MouseEvent mouseEvent) || owner == null || owner.document == null) return;
        String text = "";
        if (supplier != null) {
            try {
                text = supplier.get();
            } catch (RuntimeException ignored) {
                text = "";
            }
        }
        Position pointer = new Position(mouseEvent.clientX, mouseEvent.clientY);
        synchronized (Tooltip.class) {
            if (activeTooltip != null && activeTooltip.owner == owner && activeTooltip.isVisible()) {
                activeTooltip.move(pointer);
                return;
            }
            replace(owner.document, owner, pointer, text, translationKey, options);
        }
    }

    private static synchronized Tooltip replace(Document document, Element owner, Position pointer,
                                                String text, String translationKey, Options options) {
        if (activeTooltip != null) activeTooltip.close();
        Tooltip tooltip = new Tooltip(document, owner, pointer, text, translationKey, options);
        if (tooltip.isVisible()) activeTooltip = tooltip;
        return tooltip;
    }

    private static synchronized void hideOwnedBy(Element owner) {
        if (activeTooltip != null && activeTooltip.owner == owner) activeTooltip.close();
    }

    private static void markDirty(Element target) {
        if (target != null && target.document != null) {
            target.document.markDirty(target, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.2fpx", value);
    }
}
