package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;
import lombok.Getter;
import lombok.Setter;

@ElementRegister(Input.TAG_NAME)
@Getter
@Setter
public class Input extends Element {
    public static final String TAG_NAME = "INPUT";
    private String placeholder = "";
    private int maxLength = 256;
    private int cursor = 0;
    private long lastBlinkTime = 0;

    public Input(Document document) {
        super(document, TAG_NAME);
        this.addEventListener("mousedown", event -> {
            if (event instanceof MouseEvent mouseEvent) {
                this.locateCursor(mouseEvent.offsetX);
            }
        });
    }

    private void locateCursor(double mouseOffsetX) {
        Box box = Box.of(this);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double relativeX = mouseOffsetX - contentStartX + scrollLeft;

        String text = getRenderText();
        if (text.isEmpty()) {
            cursor = 0;
            return;
        }

        double currentWidth = 0;
        int newCursor = 0;

        // 简单线性扫描，对于极长文本可优化为二分查找
        for (int i = 0; i < text.length(); i++) {
            String charStr = String.valueOf(text.charAt(i));
            double charWidth = Size.measureText(this, charStr);

            // 如果鼠标落在字符中心点的左侧，则是当前索引；否则继续
            if (relativeX <= currentWidth + charWidth / 2.0) {
                break;
            }
            currentWidth += charWidth;
            newCursor++;
        }

        cursor = newCursor;
        clampScroll();
    }

    // 溢出滚动
    private void clampScroll() {
        String text = getRenderText();
        if (cursor > text.length()) cursor = text.length();
        if (cursor < 0) cursor = 0;

        String textBeforeCursor = text.substring(0, cursor);
        double cursorX = Size.measureText(this, textBeforeCursor);

        Size size = Size.getContentSize(this);
        double visibleWidth = size.width();

        if (cursorX < scrollLeft) setScrollLeft(cursorX);
        else if (cursorX > scrollLeft + visibleWidth) setScrollLeft(cursorX - visibleWidth + 2);

        this.scrollWidth = Size.measureText(this, text);

        this.addDirtyFlags(Drawer.REPAINT);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase == Base.RenderPhase.BODY) {
            rectRenderer.drawBody(poseStack);

            String textToShow = getRenderText();
            boolean isPlaceholder = textToShow.isEmpty() && !placeholder.isEmpty();
            String renderContent = isPlaceholder ? placeholder : textToShow;

            if (renderContent.isEmpty() && !Element.isElementFocusing(this)) return;

            Position pos = Position.of(this);
            Box box = Box.of(this);
            Text text = Text.of(this);

            text.content = isPlaceholder ? placeholder : textToShow;
            if (isPlaceholder) text.color = new Color("#888888");

            Position contentPos = rectRenderer.getContentPosition();

            float drawX = (float) (contentPos.x - scrollLeft);
            float drawY = (float) (contentPos.y);

            FontDrawer.drawFont(poseStack, text, new Position(drawX, drawY));

            if (Element.isElementFocusing(this)) {
                String textBefore = textToShow.substring(0, Math.min(cursor, textToShow.length()));
                double cursorXOffset = Size.measureText(this, textBefore);
                float renderX = (float) (drawX + cursorXOffset);
                float renderY = drawY;
                float height = (float) text.lineHeight;

                Graph.drawCursor(poseStack.last().pose(), renderX, renderY, height, Style.getFontColor(this), this.lastBlinkTime);
            }
        }
    }

    public void insertText(String str) {
        if (value.length() + str.length() <= maxLength) {
            if (cursor < 0) cursor = 0;
            if (cursor > value.length()) cursor = value.length();

            String before = value.substring(0, cursor);
            String after = value.substring(cursor);
            value = before + str + after;
            cursor += str.length();
            clampScroll();
        }
        getRenderer().text.clear();
    }

    public void moveCursor(int offset) {
        cursor += offset;
        cursor = Math.max(0, cursor);
        cursor = Math.min(value.length(), cursor);
        clampScroll();
    }

    public void sliceText(int start, int end) {
        if (start < 0) start = 0;
        if (end > value.length()) end = value.length();
        if (start >= end) return; // 无选中或非法范围

        String before = value.substring(0, start);
        String after = value.substring(end);
        value = before + after;
        cursor = start;
        clampScroll();
        getRenderer().text.clear();
    }

    private String getRenderText() {
        if (value == null) value = "";
        return "password".equals(this.getAttribute("type")) ? "*".repeat(value.length()) : value;
    }

    private String cachedValue = "";

    @Override
    public void tick() {
        if (!cachedValue.equals(value) && value != null) {
            cachedValue = value;
            getRenderer().text.clear();
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }
}