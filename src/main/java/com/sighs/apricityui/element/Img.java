package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;

@ElementRegister(Img.TAG_NAME)
public class Img extends Element {
    public static final String TAG_NAME = "IMG";

    public Img(Document document) {
        super(document, TAG_NAME);
    }


    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                ImageDrawer.draw(poseStack, this, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }
}
