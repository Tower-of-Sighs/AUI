package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.Span;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.style.Text;
import net.minecraft.network.chat.TranslatableComponent;
import com.sighs.apricityui.parser.HTML;

@ElementRegister(Translation.TAG_NAME)
public class Translation extends Span {
    public static final String TAG_NAME = "TRANSLATION";

    public Translation(Document document) {
        super(document);
        // Keep the registered tag name so HTML's closing-tag parser can match </translation>.
        this.tagName = TAG_NAME;
    }

    public String getTranslatedText() {
        // 1.18.2 没有 Component.translatable（1.19 才把 TranslatableComponent 的工厂方法搬到
        // Component 上，同时删掉了 TranslatableComponent 这个类），所以这里用 1.18.2 的写法。
        // 无参数的 TranslatableComponent#getString() 就是一次 Language.getOrDefault 查询，
        // 语义与高版本的 Component.translatable(...).getString() 完全一致。
        return new TranslatableComponent(super.getTextContent()).getString();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        if (NormalFlow.isInlineTextPaintedByAncestor(this)) return;
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
