package com.sighs.apricityui.canvas;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class CanvasImageBitmap implements AutoCloseable {
    private BufferedImage image;

    public CanvasImageBitmap(BufferedImage image) {
        this.image = image;
    }

    BufferedImage image() {
        return image;
    }

    public int getWidth() {
        return image == null ? 0 : image.getWidth();
    }

    public int getHeight() {
        return image == null ? 0 : image.getHeight();
    }

    public boolean isClosed() {
        return image == null;
    }

    public CanvasImageBitmap crop(int sx, int sy, int sw, int sh) {
        if (image == null || sw <= 0 || sh <= 0) return new CanvasImageBitmap(null);
        BufferedImage cropped = new BufferedImage(Math.max(1, sw), Math.max(1, sh), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(image, 0, 0, sw, sh, sx, sy, sx + sw, sy + sh, null);
        } finally {
            g.dispose();
        }
        return new CanvasImageBitmap(cropped);
    }

    @Override
    public void close() {
        image = null;
    }
}
