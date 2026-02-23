package com.sighs.apricityui.element;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID)
public class Div extends Element {
    public static final String TAG_NAME = "DIV";

    static {
        Element.register(TAG_NAME, (document, string) -> new Div(document));
    }

    public Div(Document document) {
        super(document, TAG_NAME);
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
            }
            break;
            case BORDER: {
                rectRenderer.drawBorder(stack);
            }
            break;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "(" + children.size() + ")";
    }
}
