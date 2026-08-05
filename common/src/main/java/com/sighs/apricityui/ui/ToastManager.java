package com.sighs.apricityui.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.task.FrameTaskScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ToastManager {
    private static final String DOC_PATH = "devtools/toast.html";
    private static final String LIST_ID = "aui-toast-list";
    private static final long EXIT_DURATION_NS = 180_000_000L;
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final Map<String, ToastRef> ACTIVE = new ConcurrentHashMap<>();

    private static final String ITEM_BASE_STYLE =
            "pointer-events:auto;display:flex;align-items:flex-start;gap:10px;width:100%;" +
                    "padding:10px 12px;border:2px solid #1a1a1a;border-left:6px solid #8b5cf6;" +
                    "background-color:#ffffff;color:#1a1a1a;box-shadow:4px 4px 0 #1a1a1a;" +
                    "font-family:'Microsoft YaHei',sans-serif;font-size:12px;font-weight:600;line-height:18px;" +
                    "letter-spacing:0.7px;text-transform:uppercase;overflow:hidden;";
    private static final String MARKER_STYLE =
            "flex:0 0 18px;width:18px;height:18px;background-color:#8b5cf6;color:#ffffff;" +
                    "font-size:12px;font-weight:700;line-height:18px;text-align:center;";
    private static final String CONTENT_STYLE =
            "display:flex;flex:1;min-width:0;flex-direction:column;gap:2px;";
    private static final String LABEL_STYLE =
            "color:#6d28d9;font-size:10px;font-weight:700;line-height:12px;letter-spacing:1px;";
    private static final String MESSAGE_STYLE =
            "color:#1a1a1a;font-size:12px;font-weight:600;line-height:18px;letter-spacing:0.4px;";
    private static final String CLOSE_STYLE =
            "flex:0 0 14px;width:14px;color:#999999;font-size:14px;font-weight:700;line-height:14px;text-align:center;";

    private ToastManager() {
    }

    public static String show(String message) {
        return show(message, ToastOptions.defaults());
    }

    public static String show(String message, int durationMs) {
        return show(message, ToastOptions.defaults().withDurationMs(durationMs));
    }

    public static String show(String message, ToastOptions options) {
        return show(message, null, options);
    }

    /** Shows a message that remains a live translation DOM node. */
    public static String showTranslation(String translationKey) {
        return show(null, translationKey, ToastOptions.defaults());
    }

    private static String show(String message, String translationKey, ToastOptions options) {
        String content = (message == null || message.isBlank()) ? " " : message.trim();
        ToastOptions safe = options == null ? ToastOptions.defaults() : options.normalize();
        Overlay overlay = ensureOverlay();
        if (overlay == null || overlay.list() == null) return "";

        String id = "toast-" + SEQ.getAndIncrement();
        Element item = Element.init(overlay.document().createElement("div"));
        item.setAttribute("id", id);
        item.setClassName("aui-toast");
        item.setAttribute("style", buildItemStyle(safe));
        item.append(createPart(overlay.document(), "span", "!", buildMarkerStyle(safe)));

        Element contentBox = Element.init(overlay.document().createElement("div"));
        contentBox.setAttribute("style", CONTENT_STYLE);
        contentBox.append(createPart(overlay.document(), "span", "AUI // NOTICE", LABEL_STYLE));
        Element messagePart = translationKey == null || translationKey.isBlank()
                ? createPart(overlay.document(), "span", content, MESSAGE_STYLE)
                : createTranslationMessagePart(overlay.document(), translationKey);
        contentBox.append(messagePart);
        item.append(contentBox);
        if (safe.dismissOnClick()) {
            Element close = createPart(overlay.document(), "span", "x", CLOSE_STYLE);
            close.addEventListener("click", event -> dismiss(id));
            item.append(close);
        }
        if (safe.dismissOnClick()) {
            item.addEventListener("click", event -> dismiss(id));
        }
        Element attachedItem = overlay.list().insertBefore(item, overlay.list().getFirstElementChild());
        if (attachedItem == null) return "";
        overlay.document().markDirty(overlay.document().body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        long expiresAtNs = safe.durationMs() <= 0
                ? Long.MAX_VALUE
                : System.nanoTime() + safe.durationMs() * 1_000_000L;
        ACTIVE.put(id, new ToastRef(attachedItem, expiresAtNs));
        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            ToastRef ref = ACTIVE.get(id);
            if (ref != null && !ref.leaving) {
                ref.item.setClassName("aui-toast aui-toast-visible");
                markDirty(ref.item.getOwnerDocument());
            }
            return true;
        });
        return id;
    }

    /** Runs on the client tick, so startup and reload do not depend on a background timer. */
    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        long now = System.nanoTime();
        for (Map.Entry<String, ToastRef> entry : ACTIVE.entrySet()) {
            ToastRef ref = entry.getValue();
            if (!ref.leaving && now >= ref.expiresAtNs) {
                beginDismiss(ref, now);
            } else if (ref.leaving && now >= ref.removeAtNs) {
                remove(entry.getKey(), ref);
            }
        }
    }

    public static void dismiss(String id) {
        if (id == null || id.isBlank()) return;
        ToastRef ref = ACTIVE.get(id);
        if (ref == null) return;
        beginDismiss(ref, System.nanoTime());
    }

    public static void clear() {
        for (Map.Entry<String, ToastRef> entry : ACTIVE.entrySet()) {
            remove(entry.getKey(), entry.getValue());
        }
        ACTIVE.clear();
    }

    private static void beginDismiss(ToastRef ref, long now) {
        if (ref == null || ref.leaving) return;
        ref.leaving = true;
        ref.removeAtNs = now + EXIT_DURATION_NS;
        ref.item.setClassName("aui-toast aui-toast-leaving");
        markDirty(ref.item.getOwnerDocument());
    }

    private static void remove(String id, ToastRef ref) {
        if (ref == null || !ACTIVE.remove(id, ref)) return;
        Element item = ref.item;
        if (item == null) return;
        Document owner = item.getOwnerDocument();
        item.remove();
        markDirty(owner);
    }

    private static Overlay ensureOverlay() {
        Document document = null;
        var docs = Document.get(DOC_PATH);
        if (!docs.isEmpty()) {
            document = docs.get(0);
        } else {
            document = Document.create(DOC_PATH);
            if (document != null) document.setReloadPersistent(true);
        }
        if (document == null) return null;
        document.setReloadPersistent(true);

        Element list = document.getElementById(LIST_ID);
        if (list == null) list = document.querySelector("#" + LIST_ID);
        if (list == null) return null;
        return new Overlay(document, list);
    }

    private static String buildItemStyle(ToastOptions options) {
        StringBuilder style = new StringBuilder(ITEM_BASE_STYLE);
        if (options.backgroundColor() != null && !options.backgroundColor().isBlank()) {
            style.append("background-color:").append(options.backgroundColor().trim()).append(';');
        }
        if (options.textColor() != null && !options.textColor().isBlank()) {
            style.append("color:").append(options.textColor().trim()).append(';');
        }
        if (options.borderColor() != null && !options.borderColor().isBlank()) {
            style.append("border:1px solid ").append(options.borderColor().trim()).append(';');
        }
        if (options.customStyle() != null && !options.customStyle().isBlank()) {
            String patch = options.customStyle().trim();
            style.append(patch);
            if (!patch.endsWith(";")) style.append(';');
        }
        return style.toString();
    }

    private static String buildMarkerStyle(ToastOptions options) {
        String color = options.borderColor() == null || options.borderColor().isBlank()
                ? "#8b5cf6"
                : options.borderColor().trim();
        return MARKER_STYLE + "background-color:" + color + ';';
    }

    private static Element createPart(Document document, String tagName, String text, String style) {
        Element element = Element.init(document.createElement(tagName));
        element.innerText = text;
        element.setAttribute("style", style);
        return element;
    }

    public static Element createTranslationMessagePart(Document document, String translationKey) {
        Element messagePart = createPart(document, "span", " ", MESSAGE_STYLE);
        Element translation = Element.init(document.createElement("TRANSLATION"));
        translation.setTextContent(translationKey == null ? "" : translationKey);
        messagePart.appendChild(translation);
        return messagePart;
    }

    private static void markDirty(Document document) {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }

    private record Overlay(Document document, Element list) {
    }

    private static final class ToastRef {
        private final Element item;
        private final long expiresAtNs;
        private boolean leaving;
        private long removeAtNs;

        private ToastRef(Element item, long expiresAtNs) {
            this.item = item;
            this.expiresAtNs = expiresAtNs;
        }
    }

    public record ToastOptions(
            int durationMs,
            boolean dismissOnClick,
            String backgroundColor,
            String textColor,
            String borderColor,
            String customStyle
    ) {
        public static ToastOptions defaults() {
            return new ToastOptions(2600, true, "", "", "", "");
        }

        public ToastOptions withDurationMs(int durationMs) {
            return new ToastOptions(durationMs, dismissOnClick, backgroundColor, textColor, borderColor, customStyle);
        }

        public ToastOptions normalize() {
            int safeDuration = Math.max(0, durationMs);
            return new ToastOptions(safeDuration, dismissOnClick, backgroundColor, textColor, borderColor, customStyle);
        }
    }
}
