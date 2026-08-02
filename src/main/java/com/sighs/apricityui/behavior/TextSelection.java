package com.sighs.apricityui.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Text;

import java.util.List;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.util.TextMetrics;
import com.sighs.apricityui.dom.TextNode;

public final class TextSelection {
    private final Element owner;
    private int start = 0;
    private int end = 0;
    private int anchor = 0;
    private boolean selecting = false;
    private String normalizedCache = null;
    private String normalizedSource = null;
    private String normalizedWhiteSpace = null;

    public TextSelection(Element owner) {
        this.owner = owner;
    }

    public void addEventListeners() {
        owner.addInternalEventListener("mousedown", event -> {
            if (!(event instanceof com.sighs.apricityui.event.MouseEvent mouseEvent)) return;
            if (!canSelectInnerText()) return;
            if (owner.document != null) {
                owner.document.clearAllTextSelectionsExcept(owner);
            }

            if (Interaction.isUserSelectAll(owner)) {
                selectAllInnerText();
                selecting = false;
                setFocusedForTextSelection();
                return;
            }

            locateTextCursor(mouseEvent.offsetX);
            if (mouseEvent.shiftKey) {
                start = anchor;
                end = getTextCursor();
            } else {
                anchor = getTextCursor();
                clearTextSelection();
            }
            selecting = true;
            setFocusedForTextSelection();
        });

        owner.addInternalEventListener("mousemove", event -> {
            if (!(event instanceof com.sighs.apricityui.event.MouseEvent mouseEvent)) return;
            if (!canSelectInnerText()) return;
            if (!selecting || owner.document.getPressedElement() != owner) return;

            locateTextCursor(mouseEvent.offsetX);
            start = anchor;
            end = getTextCursor();
            owner.addDirtyFlags(Drawer.REPAINT);
        });

        owner.addInternalEventListener("mouseup", event -> selecting = false);
    }

    public boolean hasInnerTextSelection() {
        return start != end;
    }

    public String getSelectedInnerText() {
        if (!canSelectInnerText()) return "";
        String content = getSelectableInnerText();
        if (content.isEmpty()) return "";
        int min = Math.max(0, Math.min(start, end));
        int max = Math.min(content.length(), Math.max(start, end));
        if (min >= max) return "";
        return content.substring(min, max);
    }

    public void selectAllInnerText() {
        if (!canSelectInnerText()) return;
        String content = getSelectableInnerText();
        anchor = 0;
        start = 0;
        end = content.length();
        owner.addDirtyFlags(Drawer.REPAINT);
    }

    public void clearTextSelection() {
        int cursor;
        try {
            cursor = getTextCursor();
        } catch (NoClassDefFoundError error) {
            cursor = 0;
        }
        start = cursor;
        end = cursor;
        owner.addDirtyFlags(Drawer.REPAINT);
    }

    public boolean canSelectInnerText() {
        if (owner instanceof com.sighs.apricityui.element.AbstractText) return false;
        if (!owner.children.isEmpty()) return false;
        if (getRawSelectableTextSource().isEmpty()) return false;
        return Interaction.isUserSelectable(owner);
    }

    public void drawInnerTextSelection(PoseStack poseStack, Rect rectRenderer) {
        if (!canSelectInnerText() || !hasInnerTextSelection()) return;
        Text baseText = selectableText();
        if (baseText.content.isEmpty()) return;
        Text.WrappedText wrapped = Text.wrapCached(owner, baseText);
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        int min = Math.max(0, Math.min(start, end));
        int max = Math.min(baseText.content.length(), Math.max(start, end));
        if (min >= max) return;

        Position contentPos = rectRenderer.getContentPosition();
        double contentWidth = Box.of(owner).innerSize().width();
        double contentHeight = Box.of(owner).innerSize().height();
        double textHeight = wrapped.height(baseText.lineHeight);
        double baseY = contentPos.y + TextMetrics.computeVerticalOffset(baseText, contentHeight, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int drawStart = Math.max(min, lineStart);
            int drawEnd = Math.min(max, lineEnd);
            if (drawStart >= drawEnd) continue;

            double lineWidth = Text.measureLine(baseText, line);
            double drawX = contentPos.x + TextMetrics.computeAlignedX(baseText, contentWidth, lineWidth, i == 0);
            double startX = measureTextSegmentWidth(line.substring(0, drawStart - lineStart)) - owner.scrollLeft;
            double endX = measureTextSegmentWidth(line.substring(0, drawEnd - lineStart)) - owner.scrollLeft;
            float x0 = (float) (drawX + startX);
            float x1 = (float) (drawX + endX);
            float y0 = (float) (baseY + i * baseText.lineHeight);
            float y1 = y0 + (float) baseText.lineHeight;
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Text.getSelectionColor(owner));
        }
    }

    public void drawInnerText(PoseStack poseStack, Rect rectRenderer) {
        Text text = selectableText();
        Position contentPos = rectRenderer.getContentPosition();
        text.color = new Color(Text.getFontColor(owner));

        if (text.content == null || text.content.isEmpty()) return;

        double contentWidth = Box.of(owner).innerSize().width();
        double contentHeight = Box.of(owner).innerSize().height();
        Text.WrappedText wrapped = Text.wrapCached(owner, text);
        List<String> lines = owner.resolveRenderedLines(text, contentWidth, contentHeight);
        int[] starts = wrapped.starts();
        double textHeight = Math.max(text.lineHeight, lines.size() * text.lineHeight);
        boolean flexLike = com.sighs.apricityui.layout.Layout.isFlexDisplay(owner.getComputedStyle().display)
                || com.sighs.apricityui.layout.Layout.isGridDisplay(owner.getComputedStyle().display);
        Position flexTextOffset = flexLike ? owner.getFlexTextOffset() : Position.ZERO;
        double drawY = contentPos.y + (flexLike ? flexTextOffset.y : TextMetrics.computeVerticalOffset(text, contentHeight, textHeight));
        boolean drawSelectionText = lines.equals(wrapped.lines()) && canSelectInnerText() && hasInnerTextSelection();
        int min = Math.max(0, Math.min(start, end));
        int max = Math.min(text.content.length(), Math.max(start, end));
        Position linePos = new Position(0, 0);
        Text runText = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + (flexLike
                    ? TextMetrics.computeFlexTextAlignedX(owner, text, contentWidth, lineWidth)
                    : TextMetrics.computeAlignedX(text, contentWidth, lineWidth, i == 0));
            double lineY = drawY + i * text.lineHeight;
            if (!drawSelectionText) {
                text.content = line;
                linePos.x = drawX - owner.scrollLeft;
                linePos.y = lineY;
                FontDrawer.drawFont(poseStack, text, linePos);
                continue;
            }

            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int segStart = Math.max(min, lineStart);
            int segEnd = Math.min(max, lineEnd);
            if (segStart >= segEnd) {
                text.content = line;
                linePos.x = drawX - owner.scrollLeft;
                linePos.y = lineY;
                FontDrawer.drawFont(poseStack, text, linePos);
                continue;
            }

            if (runText == null) {
                runText = new Text();
                TextMetrics.copyTextForRun(text, runText);
            }

            String before = line.substring(0, segStart - lineStart);
            String selected = line.substring(segStart - lineStart, segEnd - lineStart);
            String after = line.substring(segEnd - lineStart);
            double segmentX = drawX - owner.scrollLeft;
            if (!before.isEmpty()) {
                runText.content = before;
                runText.color = text.color;
                linePos.x = segmentX;
                linePos.y = lineY;
                FontDrawer.drawFont(poseStack, runText, linePos);
                segmentX += measureTextSegmentWidth(before);
            }
            if (!selected.isEmpty()) {
                runText.content = selected;
                runText.color = new Color("#FFFFFF");
                linePos.x = segmentX;
                linePos.y = lineY;
                FontDrawer.drawFont(poseStack, runText, linePos);
                segmentX += measureTextSegmentWidth(selected);
            }
            if (!after.isEmpty()) {
                runText.content = after;
                runText.color = text.color;
                linePos.x = segmentX;
                linePos.y = lineY;
                FontDrawer.drawFont(poseStack, runText, linePos);
            }
        }
    }

    private void setFocusedForTextSelection() {
        if (owner.document == null) return;
        owner.document.setFocusedElement(owner);
    }

    private int getTextCursor() {
        return Math.max(0, Math.min(end, getSelectableInnerText().length()));
    }

    private void locateTextCursor(double mouseOffsetX) {
        String content = getSelectableInnerText();
        if (content.isEmpty()) {
            end = 0;
            return;
        }

        if (Interaction.isUserSelectAll(owner)) {
            end = content.length();
            return;
        }

        Box box = Box.of(owner);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double relativeX = mouseOffsetX - contentStartX + owner.scrollLeft;
        double currentWidth = 0;
        int cursor = 0;
        for (int i = 0; i < content.length(); i++) {
            double charWidth = measureTextSegmentWidth(content.substring(i, i + 1));
            if (relativeX <= currentWidth + charWidth / 2.0) break;
            currentWidth += charWidth;
            cursor++;
        }
        end = cursor;
    }

    private String getSelectableInnerText() {
        Text text = Text.of(owner);
        String raw = getRawSelectableTextSource();
        String whiteSpace = text.whiteSpace;

        boolean sameSource = normalizedSource == raw;
        boolean sameWhiteSpace = (normalizedWhiteSpace == whiteSpace)
                || (normalizedWhiteSpace != null && normalizedWhiteSpace.equals(whiteSpace));
        if (normalizedCache != null && sameSource && sameWhiteSpace) {
            return normalizedCache;
        }

        String normalized = Text.normalizeWhiteSpaceContent(raw, whiteSpace);
        normalizedCache = normalized == null ? "" : normalized;
        normalizedSource = raw;
        normalizedWhiteSpace = whiteSpace;
        return normalizedCache;
    }

    private String getRawSelectableTextSource() {
        if (owner == null) return "";
        if (!owner.childNodes.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Node child : owner.childNodes) {
                if (child instanceof TextNode textNode) {
                    builder.append(textNode.getTextContent());
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        return owner.innerText == null ? "" : owner.innerText;
    }

    private double measureTextSegmentWidth(String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text base = Text.of(owner);
        Text copy = TextMetrics.cloneTextForSegment(base, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }

    private Text selectableText() {
        Text base = Text.of(owner);
        Text copy = new Text();
        copy.fontSize = base.fontSize;
        copy.fontWeight = base.fontWeight;
        copy.oblique = base.oblique;
        copy.strokeWidth = base.strokeWidth;
        copy.strokeColor = base.strokeColor;
        copy.color = base.color;
        copy.textDecoration = base.textDecoration;
        copy.fontFamily = base.fontFamily;
        copy.lineHeight = base.lineHeight;
        copy.direction = base.direction;
        copy.textAlign = base.textAlign;
        copy.verticalAlign = base.verticalAlign;
        copy.whiteSpace = base.whiteSpace;
        copy.fontMode = base.fontMode;
        copy.textIndent = base.textIndent;
        copy.letterSpacing = base.letterSpacing;
        copy.rasterBackgroundColor = base.rasterBackgroundColor;
        copy.content = getSelectableInnerText();
        return copy;
    }
}
