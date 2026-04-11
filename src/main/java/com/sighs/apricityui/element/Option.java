package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;

@ElementRegister(Option.TAG_NAME)
public class Option extends Element {
    public static final String TAG_NAME = "OPTION";

    public Option(Document document) {
        super(document, TAG_NAME);
        addEventListener("mousedown", _ -> {
            if (parentElement != null) parentElement.setAttribute("value", getAttribute("value"));
            document.clearFocus();
        });
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        double offsetX = 0;
        if (parentElement != null) offsetX = Box.of(parentElement).getPaddingLeft();
        rectRenderer.position = rectRenderer.position.add(new Position(-offsetX, 0));
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                FontDrawer.drawFont(poseStack, Text.of(this), rectRenderer.getContentPosition());
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }
}
