package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.element.Span;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import net.minecraft.util.text.TranslationTextComponent;

@ElementRegister(Translation.TAG_NAME)
public class Translation extends Span {
    public static final String TAG_NAME = "TRANSLATION";

    public Translation(Document document) {
        super(document);
    }

    @Override
    public void drawPhase(MatrixStack stack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW: {
                rectRenderer.drawShadow(stack);
            }
            break;
            case BODY: {
                rectRenderer.drawBody(stack);
                Text text = Text.of(this);
                text.content = new TranslationTextComponent(text.content).getString();
                FontDrawer.drawFont(stack, text, rectRenderer.getContentPosition());
            }
            break;
            case BORDER: {
                rectRenderer.drawBorder(stack);
            }
            break;
        }
    }
}
