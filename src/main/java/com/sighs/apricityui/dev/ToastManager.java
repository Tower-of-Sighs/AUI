package com.sighs.apricityui.dev;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.resource.HTML;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ToastManager {
    private static final String DOC_PATH = "__runtime/aui-toast-overlay__.html";
    private static final String LIST_ID = "aui-toast-list";
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final Map<String, ToastRef> ACTIVE = new ConcurrentHashMap<>();

    private static final String DOC_TEMPLATE = """
            <body style="transform:translateZ(10000px);position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;">
              <div id="aui-toast-list" style="position:fixed;top:12px;right:12px;display:flex;flex-direction:column;align-items:flex-end;gap:8px;max-width:38%;pointer-events:none;"></div>
            </body>
            """;

    private static final String ITEM_BASE_STYLE =
            "pointer-events:auto;min-width:180px;max-width:420px;padding:8px 12px;" +
                    "border-radius:8px;border:1px solid #3d4a5f;background-color:#1e293b;color:#f8fafc;" +
                    "box-shadow:0 4px 16px rgba(0,0,0,0.25);font-size:13px;line-height:18px;overflow:hidden;";

    private ToastManager() {
    }

    public static String show(String message) {
        return show(message, ToastOptions.defaults());
    }

    public static String show(String message, int durationMs) {
        return show(message, ToastOptions.defaults().withDurationMs(durationMs));
    }

    public static String show(String message, ToastOptions options) {
        String content = (message == null || message.isBlank()) ? " " : message.trim();
        ToastOptions safe = options == null ? ToastOptions.defaults() : options.normalize();
        Overlay overlay = ensureOverlay();
        if (overlay == null || overlay.list() == null) return "";

        String id = "toast-" + SEQ.getAndIncrement();
        Element item = overlay.document().createElement("div");
        item.setAttribute("id", id);
        item.innerText = content;
        item.setAttribute("style", buildItemStyle(safe));
        if (safe.dismissOnClick()) {
            item.addEventListener("click", _ -> dismiss(id));
        }
        overlay.list().prepend(item);
        ACTIVE.put(id, new ToastRef(overlay.document().getUuid(), id));

        if (safe.durationMs() > 0) {
            Window.window.setTimeout(_ -> dismiss(id), safe.durationMs());
        }
        return id;
    }

    public static void dismiss(String id) {
        if (id == null || id.isBlank()) return;
        ToastRef ref = ACTIVE.remove(id);
        if (ref == null) return;
        Document document = Document.getByUUID(ref.documentId().toString());
        if (document == null) return;
        Element item = document.getElementById(ref.elementId());
        if (item != null) item.remove();
    }

    public static void clear() {
        for (String id : ACTIVE.keySet()) {
            dismiss(id);
        }
        ACTIVE.clear();
    }

    private static Overlay ensureOverlay() {
        Document document;
        var docs = Document.get(DOC_PATH);
        if (!docs.isEmpty()) {
            document = docs.getFirst();
        } else {
            HTML.putTemple(DOC_PATH, DOC_TEMPLATE);
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

    private record Overlay(Document document, Element list) {
    }

    private record ToastRef(UUID documentId, String elementId) {
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
