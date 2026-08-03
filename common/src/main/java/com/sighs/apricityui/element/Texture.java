package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.spi.TextureKey;
import com.sighs.apricityui.parser.CSS;

/**
 * Renders a texture already managed by Minecraft's texture manager.
 * The {@code src} attribute is a {@link ResourceLocation}; authors must provide
 * the element's rendered width and height through CSS.
 */
@ElementRegister(Texture.TAG_NAME)
public class Texture extends Element {
    public static final String TAG_NAME = "TEXTURE";

    private String observedSrc = "";
    private TextureKey textureLocation;

    public Texture(Document document) {
        super(document, TAG_NAME);
    }

    public TextureKey getTextureLocation() {
        syncSource();
        return textureLocation;
    }

    public String getCurrentSrc() {
        TextureKey location = getTextureLocation();
        return location == null ? "" : location.toString();
    }

    @Override
    protected void onInitFromDom(Element origin) {
        syncSource();
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        if ("src".equals(name)) syncSource();
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        if ("src".equals(name)) syncSource();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawTexture(poseStack, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    private void drawTexture(PoseStack poseStack, Rect rectRenderer) {
        TextureKey location = getTextureLocation();
        if (location == null) return;

        Position position = rectRenderer.getBodyRectPosition();
        Size size = rectRenderer.getBodyRectSize();
        if (size.width() <= 0 || size.height() <= 0) return;

        ImageDrawer.draw(
                poseStack,
                location,
                (float) position.x,
                (float) position.y,
                (float) size.width(),
                (float) size.height(),
                "true".equals(getAttribute("blur"))
        );
    }

    private void syncSource() {
        String src = getAttribute("src").trim();
        if (src.equals(observedSrc)) return;
        observedSrc = src;
        textureLocation = com.sighs.apricityui.spi.AuiServices.resources().tryParseTextureKey(src);
    }
}
