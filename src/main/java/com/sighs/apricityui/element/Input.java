package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.Locale;

@ElementRegister(Input.TAG_NAME)
public class Input extends AbstractText {
    public static final String TAG_NAME = "INPUT";

    private enum Mode {
        TEXT,
        BUTTON,
        CHECKBOX,
        RADIO,
        FILE
    }

    public Input(Document document) {
        super(document, TAG_NAME);

        this.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent)) return;
            if (isDisabled()) return;
            Mode mode = getMode();
            if (mode == Mode.CHECKBOX) {
                setChecked(!isChecked());
                triggerChangeEvent();
            } else if (mode == Mode.RADIO && !isChecked()) {
                setChecked(true);
                triggerChangeEvent();
            } else if (mode == Mode.FILE) {
                openFileDialog();
            } else if (mode == Mode.BUTTON && "submit".equalsIgnoreCase(getAttribute("type"))) {
                submitEnclosingForm();
            }
        });
    }

    private Mode getMode() {
        String type = getAttribute("type");
        if (type == null || type.isBlank()) return Mode.TEXT;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "button", "submit", "reset" -> Mode.BUTTON;
            case "checkbox" -> Mode.CHECKBOX;
            case "radio" -> Mode.RADIO;
            case "file" -> Mode.FILE;
            default -> Mode.TEXT;
        };
    }

    @Override
    public boolean canEditText() {
        return getMode() == Mode.TEXT;
    }

    @Override
    public void click() {
        super.click();
        if (isDisabled()) return;
        if (getMode() == Mode.FILE) {
            openFileDialog();
        } else if (getMode() == Mode.BUTTON && "submit".equalsIgnoreCase(getAttribute("type"))) {
            submitEnclosingForm();
        }
    }

    @Override
    public boolean canSelectText() {
        return getMode() == Mode.TEXT && super.canSelectText();
    }

    private void triggerChangeEvent() {
        Event inputEvent = new Event(this, "input", true);
        Event.markTrustedFromCurrentDispatch(inputEvent);
        Event.tiggerEvent(inputEvent);

        Event changeEvent = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(changeEvent);
        Event.tiggerEvent(changeEvent);
    }

    private void openFileDialog() {
        String accept = getAttribute("accept");
        String pattern = accept != null && accept.toLowerCase(Locale.ROOT).contains("html") ? "*.html" : "*.*";
        String description = "*.html".equals(pattern) ? "HTML files" : "Files";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8(pattern)).flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Choose file", "", filters, description, false);
            if (selected == null || selected.isBlank()) return;
            setValue(selected);
            setAttribute("value", selected);
            triggerChangeEvent();
        } catch (Exception ignored) {
        }
    }

    public boolean handleSpaceKey() {
        if (isDisabled()) return false;
        Mode mode = getMode();
        if (mode == Mode.CHECKBOX) {
            setChecked(!isChecked());
            triggerChangeEvent();
            return true;
        }
        if (mode == Mode.RADIO) {
            if (!isChecked()) {
                setChecked(true);
                triggerChangeEvent();
            }
            return true;
        }
        return false;
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);

        Mode mode = getMode();
        if (mode == Mode.CHECKBOX || mode == Mode.RADIO) {
            if (phase == Base.RenderPhase.BODY) {
                drawCheckableInput(poseStack, rectRenderer, mode);
            }
            return;
        }

        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(poseStack);
        if (mode == Mode.BUTTON) {
            drawButtonInput(poseStack, rectRenderer);
            return;
        }
        drawTextInput(poseStack, rectRenderer);
    }

    private void drawButtonInput(PoseStack poseStack, Rect rectRenderer) {
        String label = value == null || value.isBlank() ? getAttribute("value") : value;
        if (label == null || label.isBlank()) {
            label = "button";
        }
        Text text = Text.of(this);
        text.content = label;
        text.color = new Color(Text.getFontColor(this));
        Position contentPos = rectRenderer.getContentPosition();
        FontDrawer.drawFont(poseStack, text, new Position(contentPos.x, singleLineDrawY(rectRenderer, text)));
    }

    private void drawCheckableInput(PoseStack poseStack, Rect rectRenderer, Mode mode) {
        Box box = rectRenderer.box;
        Background background = rectRenderer.background;

        float outerX = (float) (rectRenderer.position.x + box.getMarginLeft());
        float outerY = (float) (rectRenderer.position.y + box.getMarginTop());
        float outerW = (float) box.elementSize().width();
        float outerH = (float) box.elementSize().height();
        float controlSize = Math.max(0f, Math.min(outerW, outerH));
        float x = outerX + (outerW - controlSize) * 0.5f;
        float y = outerY + (outerH - controlSize) * 0.5f;

        float borderWidth = Math.max(1f, Math.min(2f, controlSize / 8f));
        float radius = mode == Mode.RADIO ? controlSize * 0.5f : Math.min(controlSize * 0.25f, 4f);

        int backgroundColor = "unset".equals(background.color)
                ? new Color("#133043E6").getValue()
                : new Color(background.color).getValue();
        int borderColor = resolveCheckableBorderColor(box);

        Graph.beginBatch();
        Graph.drawUnifiedRoundedRect(
                poseStack.last().pose(),
                x,
                y,
                controlSize,
                controlSize,
                uniformRadii(radius),
                backgroundColor
        );
        Graph.drawComplexRoundedBorder(
                poseStack.last().pose(),
                x,
                y,
                controlSize,
                controlSize,
                uniformRadii(radius),
                new float[]{borderWidth, borderWidth, borderWidth, borderWidth},
                new int[]{borderColor, borderColor, borderColor, borderColor}
        );

        if (isChecked()) {
            int indicatorColor = new Color(Text.getFontColor(this)).getValue();
            if (mode == Mode.RADIO) {
                float dotSize = Math.max(4f, controlSize * 0.42f);
                float dotX = x + (controlSize - dotSize) * 0.5f;
                float dotY = y + (controlSize - dotSize) * 0.5f;
                Graph.drawUnifiedRoundedRect(
                        poseStack.last().pose(),
                        dotX,
                        dotY,
                        dotSize,
                        dotSize,
                        uniformRadii(dotSize * 0.5f),
                        indicatorColor
                );
            } else {
                drawCheckboxMark(poseStack, x, y, controlSize, indicatorColor);
            }
        }
    }

    private void drawCheckboxMark(PoseStack poseStack, float x, float y, float size, int color) {
        float stroke = Math.max(1f, size * 0.14f);
        float leftX = x + size * 0.24f;
        float midX = x + size * 0.43f;
        float rightX = x + size * 0.76f;
        float topY = y + size * 0.28f;
        float midY = y + size * 0.52f;
        float bottomY = y + size * 0.74f;

        Graph.drawFillRect(poseStack.last().pose(), leftX, midY, leftX + stroke, bottomY, color);
        Graph.drawFillRect(poseStack.last().pose(), leftX + stroke, midY, midX, midY + stroke, color);
        Graph.drawFillRect(poseStack.last().pose(), midX, topY, midX + stroke, bottomY, color);
        Graph.drawFillRect(poseStack.last().pose(), midX + stroke, topY + stroke, rightX, topY + stroke * 2f, color);
    }

    private int resolveCheckableBorderColor(Box box) {
        Box.SideBorder top = box.getBorderTopSide();
        if (top.size() > 0) {
            return top.color().getValue();
        }
        return new Color(Text.getFontColor(this)).getValue();
    }

    private float[] uniformRadii(float radius) {
        return new float[]{radius, radius, radius, radius};
    }

    private void drawTextInput(PoseStack poseStack, Rect rectRenderer) {
        String textToShow = getRenderText();
        boolean isPlaceholder = textToShow.isEmpty() && !placeholder.isEmpty();
        String renderContent = isPlaceholder ? placeholder : textToShow;

        if (renderContent.isEmpty() && !Element.isElementFocusing(this)) return;

        Text text = Text.of(this);
        text.content = renderContent;
        text.color = isPlaceholder ? new Color("#888888") : new Color(Text.getFontColor(this));

        Position contentPos = rectRenderer.getContentPosition();
        float drawX = (float) (contentPos.x - scrollLeft);
        float drawY = (float) singleLineDrawY(rectRenderer, text);

        if (!isPlaceholder) {
            drawSingleLineSelection(poseStack, rectRenderer, textToShow, drawY, text.lineHeight);
        }
        if (!isPlaceholder && hasSelection() && canSelectText()) {
            int min = Math.max(0, Math.min(selMin(), textToShow.length()));
            int max = Math.max(0, Math.min(selMax(), textToShow.length()));
            String before = textToShow.substring(0, min);
            String selected = textToShow.substring(min, max);
            String after = textToShow.substring(max);

            float segmentX = drawX;
            if (!before.isEmpty()) {
                text.content = before;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
                segmentX += (float) com.sighs.apricityui.style.Size.measureText(this, before);
            }
            if (!selected.isEmpty()) {
                text.content = selected;
                text.color = new Color("#FFFFFF");
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
                segmentX += (float) com.sighs.apricityui.style.Size.measureText(this, selected);
            }
            if (!after.isEmpty()) {
                text.content = after;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
            }
        } else {
            FontDrawer.drawFont(poseStack, text, new Position(drawX, drawY));
        }
        drawSingleLineCursor(poseStack, textToShow, drawX, drawY, (float) text.lineHeight);
    }

    private double singleLineDrawY(Rect rectRenderer, Text text) {
        if (rectRenderer == null || text == null) return 0.0d;
        Position contentPos = rectRenderer.getContentPosition();
        double contentHeight = Math.max(0.0d, rectRenderer.box.innerSize().height());
        return contentPos.y + (contentHeight - text.lineHeight) / 2.0d;
    }
}
