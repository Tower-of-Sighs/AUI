package com.sighs.apricityui.ui;

import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Reusable Java-owned context menu modeled after devtools/resource2.html.
 * The caller supplies only menu content; the component owns structure,
 * positioning, hover motion, dismissal and visual styling.
 */
public final class ContextMenu {
    private static final double VIEWPORT_GAP = 4;
    private static final double DEFAULT_MIN_WIDTH = 200;
    private static final int Z_INDEX = 9500;

    private static final String BACKDROP_STYLE =
            "position:fixed;inset:0;z-index:" + Z_INDEX + ";background:transparent;pointer-events:auto;";
    private static final String MENU_BASE_STYLE =
            "position:fixed;min-width:200px;max-width:360px;box-sizing:border-box;" +
                    "background:#ffffff;border:2px solid #1a1a1a;" +
                    "padding:4px 0;box-shadow:6px 6px 0 rgba(0,0,0,0.15);" +
                    "font-family:'Microsoft YaHei',sans-serif;color:#1a1a1a;overflow:hidden;" +
                    "transform-origin:center;transition:opacity 0.15s cubic-bezier(0.4,0,0.2,1)," +
                    "transform 0.15s cubic-bezier(0.4,0,0.2,1);";
    private static final String HEADER_STYLE =
            "padding:6px 16px 8px;font-family:'Microsoft YaHei',sans-serif;font-size:10px;line-height:14px;" +
                    "color:#8b5cf6;letter-spacing:2px;" +
                    "text-transform:uppercase;border-bottom:1px solid #e0e0e0;margin-bottom:4px;font-weight:600;" +
                    "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0;max-width:100%;";
    private static final String SEPARATOR_STYLE =
            "height:1px;background:#e0e0e0;margin:4px 12px;";
    private static final String ITEM_BASE_STYLE =
            "padding:8px 16px;font-size:12px;line-height:16px;" +
                    "font-weight:600;letter-spacing:1px;" +
                    "text-transform:uppercase;display:flex;align-items:center;gap:12px;color:#1a1a1a;" +
                    "position:relative;border-left:3px solid transparent;overflow:hidden;" +
                    "transition:color 0.12s ease,border-color 0.12s ease;";
    private static final String ITEM_ENABLED_STYLE = ITEM_BASE_STYLE + "cursor:pointer;";
    private static final String ITEM_DISABLED_STYLE = ITEM_BASE_STYLE + "opacity:0.42;cursor:default;";
    private static final String FILL_BASE_STYLE =
            "position:absolute;left:0;top:0;width:0;height:100%;background:#8b5cf6;z-index:0;" +
                    "transition:width 0.2s cubic-bezier(0.4,0,0.2,1);";
    private static final String ICON_STYLE =
            "position:relative;z-index:1;width:14px;height:14px;flex:0 0 14px;display:flex;" +
                    "align-items:center;justify-content:center;";
    private static final String LABEL_STYLE =
            "position:relative;z-index:1;font-family:'Microsoft YaHei',sans-serif;" +
                    "white-space:nowrap;";
    private static final String SHORTCUT_STYLE =
            "position:relative;z-index:1;margin-left:auto;padding-left:16px;font-family:'Microsoft YaHei',sans-serif;" +
                    "min-width:72px;text-align:right;font-size:10px;line-height:14px;" +
                    "color:#999999;font-weight:400;letter-spacing:0.5px;white-space:nowrap;";
    private static final String SHORTCUT_HOVER_STYLE =
            "position:relative;z-index:1;margin-left:auto;padding-left:16px;font-family:'Microsoft YaHei',sans-serif;" +
                    "min-width:72px;text-align:right;font-size:10px;line-height:14px;" +
                    "color:rgba(255,255,255,0.7);font-weight:400;letter-spacing:0.5px;" +
                    "white-space:nowrap;";

    private static ContextMenu activeMenu;

    public enum ItemType {
        HEADER,
        ACTION,
        SEPARATOR
    }

    public record Item(ItemType type, String label, String icon, String shortcut,
                       Runnable action, boolean enabled, boolean danger) {
        public static Item header(String label) {
            return new Item(ItemType.HEADER, safe(label), "", "", null, false, false);
        }

        public static Item separator() {
            return new Item(ItemType.SEPARATOR, "", "", "", null, false, false);
        }

        public static Item action(String label, Runnable action) {
            return action(label, "", "", action);
        }

        public static Item action(String label, String icon, Runnable action) {
            return action(label, icon, "", action);
        }

        public static Item action(String label, String icon, String shortcut, Runnable action) {
            return new Item(ItemType.ACTION, safe(label), safe(icon), safe(shortcut), action, true, false);
        }

        public Item disabled() {
            return new Item(type, label, icon, shortcut, action, false, danger);
        }

        public Item dangerous() {
            return new Item(type, label, icon, shortcut, action, enabled, true);
        }
    }

    public record Options(String className, String style, Runnable onClose) {
        public static Options defaults() {
            return new Options("ctx-menu aui-context-menu", "", null);
        }

        private Options normalize() {
            String classes = className == null || className.isBlank()
                    ? defaults().className()
                    : className.trim();
            return new Options(classes, safe(style), onClose);
        }
    }

    /** Icons copied from the resource2.html context-menu language. */
    public static final class Icons {
        public static final String OPEN = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M2 3h4l1.5 1.5H12v7H2V3zm1 1v6h8V5H7l-1.5-1.5H3z\"/></svg>";
        public static final String COPY = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><rect x=\"4\" y=\"4\" width=\"8\" height=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.2\"/><path d=\"M2 10V3h6v1H3v6H2z\"/></svg>";
        public static final String REFERENCE = "<svg viewBox=\"0 0 14 14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.2\"><path d=\"M5.5 8.5l3-3\"/><path d=\"M4.5 10.5H3a2.5 2.5 0 010-5h2M9.5 3.5H11a2.5 2.5 0 010 5H9\"/></svg>";
        public static final String PROPERTIES = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><circle cx=\"7\" cy=\"7\" r=\"5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.2\"/><path d=\"M7 4v3.5M7 9v.5\" stroke=\"currentColor\" stroke-width=\"1.5\" stroke-linecap=\"round\"/></svg>";
        public static final String NEW_FILE = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M3 1h5l3 3v8H3V1zm5 1v3h3M7 6v2H5v1h2v2h1V9h2V8H8V6H7z\"/></svg>";
        public static final String NEW_FOLDER = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M1 3h4.5L7 4.5H13v7H1V3zm6 3v1.5H5.5v1H7V10h1V8.5H9.5v-1H8V6H7z\"/></svg>";
        public static final String RENAME = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M10 1l3 3-8 8H2v-3l8-8zm-1 2L4 8v2h2l5-5-2-2z\"/></svg>";
        public static final String EDIT = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M10 1l3 3-8 8H2v-3l8-8zm-1.5 3L4 8.5V10h1.5L10 5.5 8.5 4z\"/></svg>";
        public static final String DELETE = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M4 2h6v1H4V2zM2 4h10v1H2V4zm1 2h8l-1 7H4L3 6zm3 1v5h1V7H6zm2 0v5h1V7H8z\"/></svg>";
        public static final String REFRESH = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M12 7a5 5 0 1 1-1.5-3.5L12 2v3.5H8.5l1.5-1.5A3.5 3.5 0 1 0 10.5 7H12z\"/></svg>";
        public static final String UP = "<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M2 9l5-5 5 5H2z\"/></svg>";

        private Icons() {
        }
    }

    private final Document document;
    private final Options options;
    private final Consumer<Event> keyListener = this::handleKey;
    private Element backdrop;
    private Element menu;
    private Position requestedPosition = Position.ZERO;
    private double menuX;
    private double menuY;
    private boolean visible;
    private boolean closed;

    private ContextMenu(Document document, Options options) {
        this.document = document;
        this.options = (options == null ? Options.defaults() : options).normalize();
    }

    public static ContextMenu show(Document document, Position position, List<Item> items) {
        return show(document, position, items, Options.defaults());
    }

    public static synchronized ContextMenu show(Document document, Position position,
                                                List<Item> items, Options options) {
        if (activeMenu != null) activeMenu.close();
        ContextMenu result = new ContextMenu(document, options);
        result.open(position == null ? Position.ZERO : position, items == null ? List.of() : items);
        if (result.isOpen()) activeMenu = result;
        return result;
    }

    public static synchronized void closeActive() {
        if (activeMenu != null) activeMenu.close();
    }

    public boolean isOpen() {
        return !closed && backdrop != null && backdrop.isConnected();
    }

    public void close() {
        if (closed) return;
        closed = true;
        document.removeEventListener("keydown", keyListener, true);
        if (backdrop != null) backdrop.remove();
        backdrop = null;
        menu = null;
        synchronized (ContextMenu.class) {
            if (activeMenu == this) activeMenu = null;
        }
        markDirty();
        if (options.onClose() != null) options.onClose().run();
    }

    private void open(Position position, List<Item> items) {
        if (document == null || document.body == null) return;
        requestedPosition = position;

        backdrop = element("DIV", "aui-context-menu-backdrop");
        backdrop.setTopLayer(true);
        backdrop.setAttribute("style", BACKDROP_STYLE);
        backdrop.addEventListener("mousedown", Event::stopPropagation);
        backdrop.addEventListener("click", event -> {
            if (event.target == backdrop) close();
            event.stopPropagation();
        });
        backdrop.addEventListener("wheel", event -> {
            event.stopPropagation();
            close();
        });
        backdrop.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
            if (event.target == backdrop) close();
        });

        menu = element("DIV", options.className());
        menu.addEventListener("mousedown", Event::stopPropagation);
        menu.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
        });
        for (Item item : items) appendItem(item);
        if (menu.children.isEmpty()) return;

        backdrop.append(menu);
        document.body.append(backdrop);
        document.addEventListener("keydown", keyListener, true);
        positionMenu();
        markDirty();

        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            if (!isOpen()) return true;
            positionMenu();
            visible = true;
            applyMenuStyle();
            markDirty();
            return true;
        });
    }

    private void appendItem(Item item) {
        if (item == null || item.type() == null) return;
        switch (item.type()) {
            case HEADER -> appendHeader(item.label());
            case SEPARATOR -> menu.append(elementWithStyle("DIV", "ctx-sep", SEPARATOR_STYLE));
            case ACTION -> appendAction(item);
        }
    }

    private void appendHeader(String label) {
        Element header = elementWithStyle("DIV", "ctx-header", HEADER_STYLE);
        header.setTextContent(safe(label).toUpperCase(Locale.ROOT));
        menu.append(header);
    }

    private void appendAction(Item item) {
        String classes = "ctx-item" + (item.danger() ? " danger" : "") + (!item.enabled() ? " disabled" : "");
        Element row = elementWithStyle("DIV", classes, item.enabled() ? ITEM_ENABLED_STYLE : ITEM_DISABLED_STYLE);
        Element fill = elementWithStyle("DIV", "ctx-item-fill", fillStyle(item.danger(), false));
        row.append(fill);

        Element icon = elementWithStyle("SPAN", "ctx-icon", ICON_STYLE);
        if (!item.icon().isBlank()) {
            icon.setInnerHTML(item.icon());
            Element svg = icon.querySelector("svg");
            if (svg != null) svg.setAttribute("style", "width:14px;height:14px;display:block;");
        }
        row.append(icon);

        Element label = elementWithStyle("SPAN", "ctx-label", LABEL_STYLE);
        label.setTextContent(item.label());
        row.append(label);

        Element shortcut = null;
        if (!item.shortcut().isBlank()) {
            shortcut = elementWithStyle("SPAN", "ctx-shortcut", SHORTCUT_STYLE);
            shortcut.setTextContent(item.shortcut());
            row.append(shortcut);
        }

        if (item.enabled()) {
            Element finalShortcut = shortcut;
            row.addEventListener("mouseenter", event -> setHovered(row, fill, finalShortcut, item.danger(), true));
            row.addEventListener("mouseleave", event -> setHovered(row, fill, finalShortcut, item.danger(), false));
            row.addEventListener("click", event -> {
                event.stopPropagation();
                Runnable action = item.action();
                close();
                if (action != null) action.run();
            });
        }
        menu.append(row);
    }

    private void setHovered(Element row, Element fill, Element shortcut, boolean danger, boolean hovered) {
        if (!isOpen()) return;
        row.setAttribute("style", ITEM_ENABLED_STYLE + (hovered
                ? "color:#ffffff;border-left-color:" + (danger ? "#991b1b" : "#6d28d9") + ";"
                : ""));
        fill.setAttribute("style", fillStyle(danger, hovered));
        if (shortcut != null) shortcut.setAttribute("style", hovered ? SHORTCUT_HOVER_STYLE : SHORTCUT_STYLE);
        document.markDirty(row, Drawer.REPAINT | Drawer.COMMIT_LAYOUT);
    }

    private void positionMenu() {
        if (menu == null) return;
        Size measured = Size.of(menu);
        double width = Math.max(DEFAULT_MIN_WIDTH, measured.width());
        double height = Math.max(0, measured.height());
        double viewportWidth = document.getViewport().layoutWidth();
        double viewportHeight = document.getViewport().layoutHeight();
        menuX = clamp(requestedPosition.x, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportWidth - width - VIEWPORT_GAP));
        menuY = clamp(requestedPosition.y, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportHeight - height - VIEWPORT_GAP));
        applyMenuStyle();
    }

    private void applyMenuStyle() {
        if (menu == null) return;
        String state = visible ? "opacity:1;transform:scale(1);" : "opacity:0;transform:scale(0.95);";
        menu.setAttribute("style", MENU_BASE_STYLE + "left:" + px(menuX) + ";top:" + px(menuY) + ";" + state + options.style());
    }

    private void handleKey(Event event) {
        if (event instanceof KeyEvent keyEvent && "Escape".equals(keyEvent.key)) {
            event.preventDefault();
            close();
        }
    }

    private Element element(String tag, String classes) {
        Element element = Element.init(document.createElement(tag));
        if (classes != null && !classes.isBlank()) element.setAttribute("class", classes);
        return element;
    }

    private Element elementWithStyle(String tag, String classes, String style) {
        Element element = element(tag, classes);
        element.setAttribute("style", style);
        return element;
    }

    private void markDirty() {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }

    private static String fillStyle(boolean danger, boolean expanded) {
        return FILL_BASE_STYLE
                + "background:" + (danger ? "#dc2626" : "#8b5cf6") + ";"
                + (expanded ? "width:100%;" : "width:0;");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.2fpx", value);
    }
}
