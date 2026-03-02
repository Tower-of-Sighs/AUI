package com.sighs.apricityui.element;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.StringUtils;

import java.util.Locale;

@ElementRegister(Input.TAG_NAME)
public class Input extends AbstractText {
    public static final String TAG_NAME = "INPUT";

    private enum Mode {
        TEXT,
        CHECKBOX,
        RADIO
    }

    public Input(Document document) {
        super(document, TAG_NAME);

        this.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent)) return;
            Mode mode = getMode();
            if (mode == Mode.CHECKBOX) {
                setChecked(!isChecked());
                triggerChangeEvent();
            } else if (mode == Mode.RADIO && !isChecked()) {
                clearRadioGroupChecked();
                setChecked(true);
                triggerChangeEvent();
            }
        });
    }

    private Mode getMode() {
        String type = getAttribute("type");
        if (StringUtils.isNullOrEmptyEx(type)) return Mode.TEXT;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "checkbox":
                return Mode.CHECKBOX;
            case "radio":
                return Mode.RADIO;
            default:
                return Mode.TEXT;
        }
    }

    @Override
    public boolean canEditText() {
        return getMode() == Mode.TEXT;
    }

    private boolean isChecked() {
        if (!hasAttribute("checked")) return false;
        String value = getAttribute("checked");
        if (StringUtils.isNullOrEmptyEx(value)) return true;
        return !("false".equalsIgnoreCase(value) || "0".equals(value));
    }

    private void setChecked(boolean checked) {
        if (checked) setAttribute("checked", "");
        else removeAttribute("checked");
    }

    private void clearRadioGroupChecked() {
        String group = getAttribute("name");
        if (StringUtils.isNullOrEmptyEx(group)) return;
        for (Element element : document.getElements()) {
            if (element == this) continue;
            if (element instanceof Input) {
                Input input = (Input) element;
                if (input.getMode() == Mode.RADIO && group.equals(input.getAttribute("name"))) {
                    input.setChecked(false);
                }
            }
        }
    }

    private void triggerChangeEvent() {
        Event.tiggerEvent(new Event(this, "change", null, true));
    }

    public boolean handleSpaceKey() {
        Mode mode = getMode();
        if (mode == Mode.CHECKBOX) {
            setChecked(!isChecked());
            triggerChangeEvent();
            return true;
        }
        if (mode == Mode.RADIO) {
            if (!isChecked()) {
                clearRadioGroupChecked();
                setChecked(true);
                triggerChangeEvent();
            }
            return true;
        }
        return false;
    }

    @Override
    public void drawPhase(MatrixStack stack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(stack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(stack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(stack);
        Mode mode = getMode();
        if (mode == Mode.CHECKBOX || mode == Mode.RADIO) {
            drawCheckableInput(stack, rectRenderer, mode);
            return;
        }
        drawTextInput(stack, rectRenderer);
    }

    private void drawCheckableInput(MatrixStack stack, Rect rectRenderer, Mode mode) {
        if (!isChecked()) return;
        Text text = Text.of(this);
        text.content = mode == Mode.RADIO ? "●" : "✓";
        text.color = new Color(Style.getFontColor(this));
        FontDrawer.drawFont(stack, text, rectRenderer.getContentPosition());
    }

    private void drawTextInput(MatrixStack stack, Rect rectRenderer) {
        String textToShow = getRenderText();
        boolean isPlaceholder = textToShow.isEmpty() && !placeholder.isEmpty();
        String renderContent = isPlaceholder ? placeholder : textToShow;

        if (renderContent.isEmpty() && !Element.isElementFocusing(this)) return;

        Text text = Text.of(this);
        text.content = renderContent;
        text.color = isPlaceholder ? new Color("#888888") : new Color(Style.getFontColor(this));

        Position contentPos = rectRenderer.getContentPosition();
        float drawX = (float) (contentPos.x - scrollLeft);
        float drawY = (float) contentPos.y;

        if (!isPlaceholder) {
            drawSingleLineSelection(stack, rectRenderer, textToShow, text.lineHeight);
        }

        FontDrawer.drawFont(stack, text, new Position(drawX, drawY));
        drawSingleLineCursor(stack, textToShow, drawX, drawY, (float) text.lineHeight);
    }
}
