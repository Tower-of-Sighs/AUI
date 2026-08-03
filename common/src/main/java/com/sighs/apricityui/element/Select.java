package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.layout.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.sighs.apricityui.parser.Color;

@ElementRegister(Select.TAG_NAME)
public class Select extends Element {
    public static final String TAG_NAME = "SELECT";
    private static final long TYPEAHEAD_TIMEOUT_NS = 700_000_000L;

    private SelectPopup popup;
    private String typeahead = "";
    private long lastTypeaheadNs;

    public Select(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                Base.offsetPaintDepth(poseStack, 0.16f);
                Text text = Text.of(this);
                text.content = selectedLabel();
                com.sighs.apricityui.layout.Position contentPosition = rectRenderer.getContentPosition();
                double contentHeight = Math.max(0, rectRenderer.box.innerSize().height());
                double drawY = contentPosition.y + (contentHeight - text.lineHeight) / 2.0d;
                FontDrawer.drawFont(poseStack, text,
                        new com.sighs.apricityui.layout.Position(contentPosition.x, drawY));
                if (showsNativeArrow()) drawNativeArrow(poseStack, rectRenderer);
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    public Size getIntrinsicSize() {
        Text text = Text.of(this);
        String label = selectedLabel();
        double nativeLineHeight = Text.calculateLineHeight(text.fontSize, "normal");
        return new Size(Size.measureText(this, label) + 20, Math.max(0, nativeLineHeight));
    }

    private void drawNativeArrow(PoseStack poseStack, Rect rectRenderer) {
        double right = rectRenderer.position.x + rectRenderer.box.elementSize().width()
                - rectRenderer.box.getBorderRight() - rectRenderer.box.getPaddingRight();
        double centerY = rectRenderer.position.y + rectRenderer.box.getMarginTop()
                + rectRenderer.box.elementSize().height() / 2.0d;
        float x = (float) (right - 7);
        float y = (float) (centerY - 2);
        int color = new com.sighs.apricityui.parser.Color(isDisabled() ? "#797A7D" : "#D8D8D8").getValue();
        Graph.drawFillRect(poseStack.last().pose(), x, y, x + 7, y + 1, color);
        Graph.drawFillRect(poseStack.last().pose(), x + 1, y + 1, x + 6, y + 2, color);
        Graph.drawFillRect(poseStack.last().pose(), x + 2, y + 2, x + 5, y + 3, color);
        Graph.drawFillRect(poseStack.last().pose(), x + 3, y + 3, x + 4, y + 4, color);
    }

    boolean showsNativeArrow() {
        return !"none".equalsIgnoreCase(getComputedStyle().appearance)
                && !"false".equalsIgnoreCase(getAttribute("data-native-arrow"));
    }

    private String selectedLabel() {
        int selectedIndex = getSelectedIndex();
        List<Element> options = getOptions();
        return selectedIndex >= 0 && selectedIndex < options.size()
                ? options.get(selectedIndex).getOptionLabel()
                : "";
    }

    public boolean isPopupOpen() {
        return popup != null && popup.isOpen();
    }

    public void openPopup() {
        if (isDisabled() || getOptions().isEmpty() || isPopupOpen()) return;
        popup = SelectPopup.open(this);
    }

    public void closePopup() {
        if (popup != null) popup.close();
    }

    @Override
    public void handleClickDefault() {
        if (isDisabled()) return;
        if (isPopupOpen()) closePopup();
        else openPopup();
    }

    public boolean handleKeyDownDefault(KeyEvent event) {
        if (event == null || isDisabled()) return false;
        String key = event.key;
        if ("Tab".equals(key) && isPopupOpen()) {
            closePopup();
            return false;
        }
        if ("Escape".equals(key)) {
            if (!isPopupOpen()) return false;
            closePopup();
            return true;
        }
        if ("Enter".equals(key) || " ".equals(key)) {
            if (!isPopupOpen()) {
                openPopup();
                return true;
            }
            return popup.commitActive();
        }
        if ("ArrowDown".equals(key) || "ArrowUp".equals(key)) {
            int direction = "ArrowDown".equals(key) ? 1 : -1;
            if (event.altKey && !isPopupOpen()) {
                openPopup();
            } else if (isPopupOpen()) {
                popup.move(direction);
            } else {
                moveClosedSelection(direction, false);
            }
            return true;
        }
        if ("Home".equals(key) || "End".equals(key)) {
            boolean end = "End".equals(key);
            if (isPopupOpen()) popup.moveToBoundary(end);
            else moveClosedSelectionToBoundary(end);
            return true;
        }
        if ("PageUp".equals(key) || "PageDown".equals(key)) {
            int distance = "PageDown".equals(key) ? 10 : -10;
            if (isPopupOpen()) popup.move(distance);
            else moveClosedSelection(distance, false);
            return true;
        }
        if (!event.controlKey && !event.altKey && !event.metaKey && isPrintableKey(key)) {
            return handleTypeahead(key);
        }
        return false;
    }

    void onPopupClosed(SelectPopup candidate) {
        if (popup == candidate) popup = null;
    }

    @Override
    public void onDisconnectedFromDocument() {
        closePopup();
    }

    @Override
    public void setDisabled(boolean disabled) {
        super.setDisabled(disabled);
        if (disabled) closePopup();
    }

    void commitUserSelection(int index) {
        List<Element> options = getOptions();
        if (index < 0 || index >= options.size()) return;
        Element option = options.get(index);
        if (option.isOptionEffectivelyDisabled()) return;
        List<Element> previous = selectedIdentitySnapshot();
        if (isMultiple()) option.setSelected(!option.isSelected());
        else option.setSelected(true);
        dispatchUserSelectionChangeEvents(previous);
    }

    private void moveClosedSelection(int distance, boolean wrap) {
        List<Element> options = getOptions();
        if (options.isEmpty()) return;
        int direction = distance < 0 ? -1 : 1;
        int remaining = Math.max(1, Math.abs(distance));
        int index = getSelectedIndex();
        for (int step = 0; step < remaining; step++) {
            int next = findEnabledOption(options, index, direction, wrap);
            if (next < 0) break;
            index = next;
        }
        if (index >= 0 && index != getSelectedIndex()) commitUserSelection(index);
    }

    private void moveClosedSelectionToBoundary(boolean end) {
        List<Element> options = getOptions();
        int index = findEnabledOption(options, end ? options.size() : -1, end ? -1 : 1, false);
        if (index >= 0 && index != getSelectedIndex()) commitUserSelection(index);
    }

    private boolean handleTypeahead(String key) {
        long now = System.nanoTime();
        if (now - lastTypeaheadNs > TYPEAHEAD_TIMEOUT_NS) typeahead = "";
        lastTypeaheadNs = now;
        String token = key.toLowerCase(Locale.ROOT);
        typeahead += token;

        List<Element> options = getOptions();
        int start = isPopupOpen() ? popup.getActiveIndex() : getSelectedIndex();
        int match = findPrefixMatch(options, typeahead, start);
        if (match < 0 && typeahead.length() > 1) {
            typeahead = token;
            match = findPrefixMatch(options, typeahead, start);
        }
        if (match < 0) return false;
        if (isPopupOpen()) popup.setActiveIndex(match, true);
        else if (match != getSelectedIndex()) commitUserSelection(match);
        return true;
    }

    private int findPrefixMatch(List<Element> options, String prefix, int start) {
        if (options.isEmpty()) return -1;
        for (int offset = 1; offset <= options.size(); offset++) {
            int index = Math.floorMod(start + offset, options.size());
            Element option = options.get(index);
            if (!option.isOptionEffectivelyDisabled()
                    && option.getOptionLabel().toLowerCase(Locale.ROOT).startsWith(prefix)) return index;
        }
        return -1;
    }

    private static int findEnabledOption(List<Element> options, int from, int direction, boolean wrap) {
        if (options.isEmpty()) return -1;
        int index = from;
        for (int checked = 0; checked < options.size(); checked++) {
            index += direction;
            if (wrap) index = Math.floorMod(index, options.size());
            else if (index < 0 || index >= options.size()) return -1;
            if (!options.get(index).isOptionEffectivelyDisabled()) return index;
        }
        return -1;
    }

    private static boolean isPrintableKey(String key) {
        return key != null && key.codePointCount(0, key.length()) == 1 && !Character.isISOControl(key.codePointAt(0));
    }

    private List<Element> selectedIdentitySnapshot() {
        return new ArrayList<>(getSelectedOptions());
    }

    private void dispatchUserSelectionChangeEvents(List<Element> previousSelection) {
        if (Objects.equals(previousSelection, getSelectedOptions())) return;

        Event inputEvent = new Event(this, "input", true);
        Event.markTrustedFromCurrentDispatch(inputEvent);
        Event.tiggerEvent(inputEvent);

        Event changeEvent = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(changeEvent);
        Event.tiggerEvent(changeEvent);
    }
}
