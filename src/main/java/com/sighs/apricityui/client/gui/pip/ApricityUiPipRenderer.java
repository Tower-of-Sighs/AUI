package com.sighs.apricityui.client.gui.pip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

public final class ApricityUiPipRenderer extends PictureInPictureRenderer<ApricityUiPipRenderState> {
    public ApricityUiPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<ApricityUiPipRenderState> getRenderStateClass() {
        return ApricityUiPipRenderState.class;
    }

    @Override
    protected void renderToTexture(ApricityUiPipRenderState renderState, PoseStack ignored) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        // PictureInPictureRenderer 已经把投影矩阵配置好了，能和输出纹理尺寸匹配，还用了-1000 到 1000 的近/远平面（near/far）
        // 不能在这里覆盖，否则 GUI 中 z = 0 的几何体可能？会被裁剪
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();

            PoseStack poseStack = new PoseStack();
            poseStack.scale((float) guiScale, (float) guiScale, 1.0F);

            if (renderState.mode() == ApricityUiPipRenderState.Mode.UI) {
                Base.drawAllDocument(poseStack);
            } else if (renderState.mode() == ApricityUiPipRenderState.Mode.CURSOR) {
                Cursor.drawPseudoCursor(poseStack);
            }
        } finally {
            modelView.popMatrix();
        }
    }

    @Override
    protected String getTextureLabel() {
        return "apricityui";
    }

    @Override
    public void close() {
        super.close();
    }
}
