package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Span;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID)
public class Translation extends Span {
    public static final String TAG_NAME = "TRANSLATION";

    static {
        Element.register(TAG_NAME, ((document, string) -> new Span(document)));
    }

    public Translation(Document document) {
        super(document);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                Text text = Text.of(this);
                text.content = Component.translatable(text.content).getString();
                FontDrawer.drawFont(poseStack, text, rectRenderer.getContentPosition());
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
    }
}
