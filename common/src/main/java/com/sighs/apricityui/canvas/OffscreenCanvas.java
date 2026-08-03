package com.sighs.apricityui.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.render.Base;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class OffscreenCanvas extends Canvas {
    public OffscreenCanvas(int width, int height) {
        super(null);
        setWidth(width);
        setHeight(height);
    }

    public CanvasImageBitmap transferToImageBitmap() {
        BufferedImage surface = getSurface();
        BufferedImage snapshot = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = snapshot.createGraphics();
        try {
            Canvas.applyGraphicsDefaults(g);
            g.drawImage(surface, 0, 0, null);
        } finally {
            g.dispose();
        }
        getContext("2d").clear();
        return new CanvasImageBitmap(snapshot);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
    }
}
