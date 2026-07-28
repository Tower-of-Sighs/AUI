package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.Span;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import net.minecraft.network.chat.Component;

@ElementRegister(Translation.TAG_NAME)
public class Translation extends Span {
    public static final String TAG_NAME = "TRANSLATION";

    public Translation(Document document) {
        super(document);
        // Keep the registered tag name so HTML's closing-tag parser can match </translation>.
        this.tagName = TAG_NAME;
    }

    public String getTranslatedText() {
        return Component.translatable(super.getTextContent()).getString();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawStaticText(poseStack, rectRenderer, Text.of(this));
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
    }
}
