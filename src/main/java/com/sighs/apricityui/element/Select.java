package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
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
                if (!children.isEmpty()) text.content = children.getFirst().innerText;
                for (Element child : children) {
                    if (getAttribute("value").equals(child.getAttribute("value"))) {
                        text.content = child.innerText;
                        break;
                    }
                }
                FontDrawer.drawFont(poseStack, text, rectRenderer.getContentPosition());
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }
}
