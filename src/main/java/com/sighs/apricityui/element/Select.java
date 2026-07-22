package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;

@ElementRegister(Select.TAG_NAME)
public class Select extends Element {
    public static final String TAG_NAME = "SELECT";

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
                Text text = Text.of(this);
                int selectedIndex = getSelectedIndex();
                if (selectedIndex >= 0 && selectedIndex < children.size()) {
                    text.content = children.get(selectedIndex).innerText;
                } else if (!children.isEmpty()) {
                    text.content = children.get(0).innerText;
                }
                FontDrawer.drawFont(poseStack, text, rectRenderer.getContentPosition());
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
            case CONTENT -> {
            }
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    void dispatchUserValueChangeEvents(String previousValue) {
        String currentValue = getValue();
        if (java.util.Objects.equals(previousValue, currentValue)) return;

        Event inputEvent = new Event(this, "input", true);
        Event.markTrustedFromCurrentDispatch(inputEvent);
        Event.tiggerEvent(inputEvent);

        Event changeEvent = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(changeEvent);
        Event.tiggerEvent(changeEvent);
    }
}
