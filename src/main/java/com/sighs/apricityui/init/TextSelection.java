package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;

import java.util.List;

final class TextSelection {
    private final Element owner;
    private int start = 0;
    private int end = 0;
    private int anchor = 0;
    private boolean selecting = false;
    private String normalizedCache = null;
    private String normalizedSource = null;
    private String normalizedWhiteSpace = null;

    TextSelection(Element owner) {
        this.owner = owner;
    }

    void addEventListeners() {
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

    boolean hasInnerTextSelection() {
        return start != end;
    }

    String getSelectedInnerText() {
        if (!canSelectInnerText()) return "";
        String content = getSelectableInnerText();
        if (content.isEmpty()) return "";
        int min = Math.max(0, Math.min(start, end));
        int max = Math.min(content.length(), Math.max(start, end));
        if (min >= max) return "";
        return content.substring(min, max);
    }

    void selectAllInnerText() {
        if (!canSelectInnerText()) return;
        String content = getSelectableInnerText();
        anchor = 0;
        start = 0;
        end = content.length();
        owner.addDirtyFlags(Drawer.REPAINT);
    }

    void clearTextSelection() {
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

    boolean canSelectInnerText() {
        if (owner instanceof com.sighs.apricityui.element.AbstractText) return false;
        if (owner.innerText == null || owner.innerText.isEmpty()) return false;
        if (!owner.children.isEmpty()) return false;
        return Interaction.isUserSelectable(owner);
    }

    void drawInnerTextSelection(PoseStack poseStack, Rect rectRenderer) {
        if (!canSelectInnerText() || !hasInnerTextSelection()) return;
        Text baseText = Text.of(owner);
        baseText.content = getSelectableInnerText();
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
        double baseY = contentPos.y + Element.computeVerticalOffset(baseText, contentHeight, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int drawStart = Math.max(min, lineStart);
            int drawEnd = Math.min(max, lineEnd);
            if (drawStart >= drawEnd) continue;

            double lineWidth = Text.measureLine(baseText, line);
            double drawX = contentPos.x + Element.computeAlignedX(baseText, contentWidth, lineWidth, i == 0);
            double startX = measureTextSegmentWidth(line.substring(0, drawStart - lineStart)) - owner.scrollLeft;
            double endX = measureTextSegmentWidth(line.substring(0, drawEnd - lineStart)) - owner.scrollLeft;
            float x0 = (float) (drawX + startX);
            float x1 = (float) (drawX + endX);
            float y0 = (float) (baseY + i * baseText.lineHeight);
            float y1 = y0 + (float) baseText.lineHeight;
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Text.getSelectionColor(owner));
        }
    }

    void drawInnerText(PoseStack poseStack, Rect rectRenderer) {
        Text text = Text.of(owner);
        Position contentPos = rectRenderer.getContentPosition();
        text.content = getSelectableInnerText();
        text.color = new Color(Text.getFontColor(owner));

        if (text.content == null || text.content.isEmpty()) return;

        double contentWidth = Box.of(owner).innerSize().width();
        double contentHeight = Box.of(owner).innerSize().height();
        Text.WrappedText wrapped = Text.wrapCached(owner, text);
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        double textHeight = wrapped.height(text.lineHeight);
        double drawY = contentPos.y + Element.computeVerticalOffset(text, contentHeight, textHeight);
        boolean drawSelectionText = canSelectInnerText() && hasInnerTextSelection();
        int min = Math.max(0, Math.min(start, end));
        int max = Math.min(text.content.length(), Math.max(start, end));
        Position linePos = new Position(0, 0);
        Text runText = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + Element.computeAlignedX(text, contentWidth, lineWidth, i == 0);
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
                Element.copyTextForRun(text, runText);
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
        String raw = owner.innerText == null ? "" : owner.innerText;
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

    private double measureTextSegmentWidth(String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text base = Text.of(owner);
        Text copy = Element.cloneTextForSegment(base, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }
}
