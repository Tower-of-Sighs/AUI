package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Input extends AbstractTextElement {
    public static final String TAG_NAME = "INPUT";

    private enum Mode {
        TEXT,
        CHECKBOX,
        RADIO
    }

    static {
        Element.register(TAG_NAME, (document, string) -> new Input(document));
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
        if (type == null || type.isBlank()) return Mode.TEXT;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "checkbox" -> Mode.CHECKBOX;
            case "radio" -> Mode.RADIO;
            default -> Mode.TEXT;
        };
    }

    @Override
    public boolean canEditText() {
        return getMode() == Mode.TEXT;
    }

    private boolean isChecked() {
        if (!hasAttribute("checked")) return false;
        String value = getAttribute("checked");
        if (value == null || value.isBlank()) return true;
        return !("false".equalsIgnoreCase(value) || "0".equals(value));
    }

    private void setChecked(boolean checked) {
        if (checked) setAttribute("checked", "");
        else removeAttribute("checked");
    }

    private void clearRadioGroupChecked() {
        String group = getAttribute("name");
        if (group == null || group.isBlank()) return;
        for (Element element : document.getElements()) {
            if (element == this) continue;
            if (element instanceof Input input && input.getMode() == Mode.RADIO && group.equals(input.getAttribute("name"))) {
                input.setChecked(false);
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
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(poseStack);
        Mode mode = getMode();
        if (mode == Mode.CHECKBOX || mode == Mode.RADIO) {
            drawCheckableInput(poseStack, rectRenderer, mode);
            return;
        }
        drawTextInput(poseStack, rectRenderer);
    }

    private void drawCheckableInput(PoseStack poseStack, Rect rectRenderer, Mode mode) {
        if (!isChecked()) return;
        Text text = Text.of(this);
        text.content = mode == Mode.RADIO ? "●" : "✓";
        text.color = new Color(Style.getFontColor(this));
        FontDrawer.drawFont(poseStack, text, rectRenderer.getContentPosition());
    }

    private void drawTextInput(PoseStack poseStack, Rect rectRenderer) {
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
            drawSingleLineSelection(poseStack, rectRenderer, textToShow, text.lineHeight);
        }

        FontDrawer.drawFont(poseStack, text, new Position(drawX, drawY));
        drawSingleLineCursor(poseStack, textToShow, drawX, drawY, (float) text.lineHeight);
    }
}
