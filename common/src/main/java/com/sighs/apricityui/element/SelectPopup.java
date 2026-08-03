package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.ui.Tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Top-level native popup used by {@link Select}; OPTION nodes remain DOM data nodes. */
final class SelectPopup {
    private static final double VIEWPORT_GAP = 4;
    private static final double MIN_POPUP_WIDTH = 80;
    private static final double ROW_HEIGHT = 28;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int Z_INDEX = 10000;

    private static final String PANEL_STYLE =
            "position:fixed;z-index:" + Z_INDEX + ";box-sizing:border-box;background:#ffffff;border:1px solid #767676;" +
                    "border-radius:2px;box-shadow:0 2px 6px rgba(0,0,0,0.28);overflow-x:hidden;overflow-y:auto;" +
                    "padding:2px 0;color:#000000;pointer-events:auto;";
    private static final String ROW_STYLE =
            "box-sizing:border-box;display:flex;align-items:center;width:100%;min-height:28px;padding:4px 8px;" +
                    "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;cursor:default;user-select:none;";
    private static final String GROUP_STYLE =
            "box-sizing:border-box;width:100%;min-height:24px;padding:5px 8px 3px;white-space:nowrap;overflow:hidden;" +
                    "text-overflow:ellipsis;color:#555555;background:#f5f5f5;font-weight:600;user-select:none;";

    private static SelectPopup activePopup;

    private final Select select;
    private final Document document;
    private final List<Element> options;
    private final List<Element> rows = new ArrayList<>();
    private Element panel;
    private final Consumer<Event> outsideMouseListener = this::handleOutsidePointer;
    private final Consumer<Event> outsideContextMenuListener = this::handleOutsidePointer;
    private int activeIndex;
    private boolean closed;

    private SelectPopup(Select select) {
        this.select = select;
        this.document = select.document;
        this.options = List.copyOf(select.getOptions());
        this.activeIndex = initialActiveIndex();
    }

    static synchronized SelectPopup open(Select select) {
        if (activePopup != null) {
            if (activePopup.select == select && activePopup.isOpen()) return activePopup;
            activePopup.close();
        }
        SelectPopup popup = new SelectPopup(select);
        try (Document.ContextScope ignored = Document.withContext(popup.document)) {
            popup.mount();
        }
        if (popup.isOpen()) activePopup = popup;
        return popup;
    }

    boolean isOpen() {
        return !closed && panel != null && panel.isConnected() && select.isConnected();
    }

    int getActiveIndex() {
        return activeIndex;
    }

    void close() {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            if (closed) return;
            closed = true;
            document.removeEventListener("mousedown", outsideMouseListener, true);
            document.removeEventListener("contextmenu", outsideContextMenuListener, true);
            Tooltip.hide(document);
            if (panel != null) panel.remove();
            panel = null;
            rows.clear();
            synchronized (SelectPopup.class) {
                if (activePopup == this) activePopup = null;
            }
            markDirty();
            select.onPopupClosed(this);
        }
    }

    void move(int delta) {
        if (options.isEmpty()) return;
        int direction = delta < 0 ? -1 : 1;
        int count = Math.max(1, Math.abs(delta));
        int next = activeIndex;
        for (int step = 0; step < count; step++) {
            int candidate = findEnabled(next, direction, false);
            if (candidate < 0) break;
            next = candidate;
        }
        setActiveIndex(next, true);
    }

    void moveToBoundary(boolean end) {
        int index = findEnabled(end ? options.size() : -1, end ? -1 : 1, false);
        if (index >= 0) setActiveIndex(index, true);
    }

    void setActiveIndex(int index, boolean reveal) {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            if (index < 0 || index >= options.size() || options.get(index).isOptionEffectivelyDisabled()) return;
            int previous = activeIndex;
            activeIndex = index;
            if (previous >= 0 && previous < rows.size()) applyRowStyle(previous);
            if (activeIndex < rows.size()) applyRowStyle(activeIndex);
            if (reveal) {
                FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
                    try (Document.ContextScope callbackContext = Document.withContext(document)) {
                        if (isOpen()) revealActiveRow();
                        return true;
                    }
                });
            }
        }
    }

    boolean commitActive() {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            if (activeIndex < 0 || activeIndex >= options.size()) return false;
            if (options.get(activeIndex).isOptionEffectivelyDisabled()) return false;
            select.commitUserSelection(activeIndex);
            if (!select.isMultiple()) close();
            else refreshRows();
            return true;
        }
    }

    private void mount() {
        if (document == null || document.body == null || options.isEmpty()) return;
        Tooltip.hide(document);
        Element.DOMRect initialAnchor = readAnchorRect();
        panel = element("DIV", "aui-select-popup");
        panel.setTopLayer(true);
        panel.setAttribute("role", "listbox");
        panel.setAttribute("aria-multiselectable", Boolean.toString(select.isMultiple()));
        panel.addEventListener("mousedown", event -> {
            event.stopPropagation();
            document.setFocusedElement(select);
        });
        panel.addEventListener("click", Event::stopPropagation);
        panel.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
        });

        Element previousGroup = null;
        for (int i = 0; i < options.size(); i++) {
            Element group = optionGroup(options.get(i));
            if (group != null && group != previousGroup) appendGroup(group);
            appendOption(i);
            previousGroup = group;
        }
        document.body.append(panel);
        document.addEventListener("mousedown", outsideMouseListener, true);
        document.addEventListener("contextmenu", outsideContextMenuListener, true);
        positionPanel(initialAnchor);
        markDirty();

        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            try (Document.ContextScope callbackContext = Document.withContext(document)) {
                if (!isOpen()) return true;
                positionPanel();
                revealActiveRow();
                markDirty();
                return true;
            }
        });
    }

    private void handleOutsidePointer(Event event) {
        if (!isOpen() || event == null) return;
        if (event.target == panel || event.target == select) return;
        if (event.target instanceof Element target && (panel.contains(target) || select.contains(target))) return;
        close();
    }

    private void appendOption(int index) {
        Element option = options.get(index);
        Element row = element("DIV", "aui-select-option");
        row.setAttribute("role", "option");
        row.setAttribute("data-option-index", Integer.toString(index));
        row.setAttribute("aria-selected", Boolean.toString(option.isSelected()));
        row.setAttribute("aria-disabled", Boolean.toString(option.isOptionEffectivelyDisabled()));
        row.setTextContent(option.getOptionLabel());
        String tooltipKey = option.getAttribute("data-tooltip-key");
        if (tooltipKey != null && !tooltipKey.isBlank()) {
            row.setAttribute("data-tooltip-key", tooltipKey);
            Tooltip.bindTranslation(row, tooltipKey);
        }
        row.addEventListener("mouseenter", event -> setActiveIndex(index, false));
        row.addEventListener("mousedown", event -> {
            event.preventDefault();
            event.stopPropagation();
            document.setFocusedElement(select);
        });
        if (!option.isOptionEffectivelyDisabled()) {
            row.addEventListener("click", event -> {
                event.preventDefault();
                event.stopPropagation();
                setActiveIndex(index, false);
                commitActive();
            });
        }
        rows.add(row);
        applyRowStyle(index);
        panel.append(row);
    }

    private void appendGroup(Element group) {
        Element header = element("DIV", "aui-select-optgroup");
        header.setAttribute("role", "presentation");
        header.setTextContent(group.getAttribute("label"));
        header.setAttribute("style", GROUP_STYLE + inheritedFontStyle(group));
        panel.append(header);
    }

    private void refreshRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setAttribute("aria-selected", Boolean.toString(options.get(i).isSelected()));
            applyRowStyle(i);
        }
    }

    private void applyRowStyle(int index) {
        if (index < 0 || index >= rows.size()) return;
        Element option = options.get(index);
        boolean disabled = option.isOptionEffectivelyDisabled();
        boolean highlighted = index == activeIndex;
        boolean selected = option.isSelected();
        Style optionStyle = option.getComputedStyle();
        // The popup is a native light surface, not a painted descendant of the
        // closed SELECT control. Inherited SELECT foreground colors therefore
        // must not leak onto its white option surface. An OPTION may still
        // override the system foreground with its own author declaration.
        String foreground = highlighted
                ? "#ffffff"
                : hasAuthorOptionColor(option)
                ? inherited(optionStyle.color, "#000000")
                : "#000000";
        String background = highlighted
                ? "#1967d2"
                : selected ? "#e8f0fe" : inherited(optionStyle.backgroundColor, "transparent");
        String state = "color:" + foreground + ";background-color:" + background + ";"
                + (disabled ? "opacity:0.45;" : "opacity:1;");
        String groupIndent = optionGroup(option) == null ? "" : "padding-left:20px;";
        rows.get(index).setAttribute("style", ROW_STYLE + groupIndent + inheritedFontStyle(option) + state);
    }

    private static boolean hasAuthorOptionColor(Element option) {
        if (option == null) return false;
        if (option.cssCache.containsKey("color")) return true;
        String inlineColor = option.getStyle().color;
        return inlineColor != null && !inlineColor.isBlank() && !"unset".equalsIgnoreCase(inlineColor);
    }

    private void positionPanel() {
        if (panel == null) return;
        positionPanel(readAnchorRect());
    }

    /**
     * Popup placement is a synchronous geometry read. Input events can arrive
     * before the normal frame tick has flushed pending style/layout work, so
     * make that work visible before falling back to the renderer's committed
     * rectangle cache.
     */
    private Element.DOMRect readAnchorRect() {
        if (document != null && document.isActive()) {
            document.commitPendingStyleRecalcForRender();
            // Headless documents used by API tests do not have a paint list or
            // the Minecraft text runtime. A real document has a paint list;
            // only force its pending layout commit on that path.
            if (!document.getPaintList().isEmpty() && document.hasPendingRenderState()) {
                document.commitRenderState();
            }
        }
        return select.getBoundingClientRect();
    }

    private void positionPanel(Element.DOMRect anchor) {
        if (panel == null || anchor == null) return;
        double viewportWidth = document.getViewport().layoutWidth();
        double viewportHeight = document.getViewport().layoutHeight();
        double width = Math.max(MIN_POPUP_WIDTH, anchor.width);
        width = Math.min(width, Math.max(1, viewportWidth - VIEWPORT_GAP * 2));
        double desiredHeight = Math.min(options.size(), MAX_VISIBLE_ROWS) * ROW_HEIGHT + 6;
        double below = Math.max(0, viewportHeight - anchor.bottom - VIEWPORT_GAP);
        double above = Math.max(0, anchor.top - VIEWPORT_GAP);
        boolean openBelow = below >= Math.min(desiredHeight, 96) || below >= above;
        double available = Math.max(ROW_HEIGHT + 2, openBelow ? below : above);
        double maxHeight = Math.min(desiredHeight, available);
        double actualHeight = Math.min(desiredHeight, maxHeight);
        double left = clamp(anchor.left, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportWidth - width - VIEWPORT_GAP));
        double top = openBelow ? anchor.bottom : anchor.top - actualHeight;
        top = clamp(top, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewportHeight - actualHeight - VIEWPORT_GAP));
        panel.setAttribute("style", PANEL_STYLE
                + "left:" + px(left) + ";top:" + px(top) + ";width:" + px(width) + ";max-height:" + px(maxHeight) + ";"
                + inheritedFontStyle(select));
    }

    private void revealActiveRow() {
        if (panel == null || activeIndex < 0 || activeIndex >= rows.size()) return;
        Element.DOMRect panelRect = panel.getBoundingClientRect();
        Element.DOMRect rowRect = rows.get(activeIndex).getBoundingClientRect();
        double nextScroll = panel.getTargetScrollTop();
        if (rowRect.top < panelRect.top) nextScroll -= panelRect.top - rowRect.top;
        else if (rowRect.bottom > panelRect.bottom) nextScroll += rowRect.bottom - panelRect.bottom;
        if (Double.compare(nextScroll, panel.getTargetScrollTop()) != 0) panel.setScrollTop(nextScroll);
    }

    private int initialActiveIndex() {
        int selected = select.getSelectedIndex();
        if (selected >= 0 && selected < options.size() && !options.get(selected).isOptionEffectivelyDisabled()) return selected;
        return findEnabled(-1, 1, false);
    }

    private int findEnabled(int from, int direction, boolean wrap) {
        if (options.isEmpty()) return -1;
        int index = from;
        for (int checked = 0; checked < options.size(); checked++) {
            index += direction;
            if (wrap) {
                if (index < 0) index = options.size() - 1;
                if (index >= options.size()) index = 0;
            } else if (index < 0 || index >= options.size()) {
                return -1;
            }
            if (!options.get(index).isOptionEffectivelyDisabled()) return index;
        }
        return -1;
    }

    private Element element(String tag, String classes) {
        Element element = Element.init(document.createElement(tag));
        if (classes != null && !classes.isBlank()) element.setAttribute("class", classes);
        return element;
    }

    private static Element optionGroup(Element option) {
        Element parent = option == null ? null : option.parentElement;
        return parent != null && "OPTGROUP".equalsIgnoreCase(parent.tagName) ? parent : null;
    }

    private void markDirty() {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        }
    }

    private static String inheritedFontStyle(Element element) {
        Style style = element.getComputedStyle();
        return css("font-family", style.fontFamily)
                + css("font-size", style.fontSize)
                + css("font-weight", style.fontWeight)
                + css("font-style", style.fontStyle)
                + css("line-height", style.lineHeight)
                + css("letter-spacing", style.letterSpacing);
    }

    private static String css(String property, String value) {
        if (value == null || value.isBlank() || "unset".equalsIgnoreCase(value)) return "";
        return property + ":" + value + ";";
    }

    private static String inherited(String value, String fallback) {
        return value == null || value.isBlank() || "unset".equalsIgnoreCase(value) ? fallback : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.2fpx", value);
    }
}
