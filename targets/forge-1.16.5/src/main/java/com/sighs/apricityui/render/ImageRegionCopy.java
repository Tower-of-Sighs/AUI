package com.sighs.apricityui.render;

import net.minecraft.client.renderer.texture.NativeImage;
public final class ImageRegionCopy {
    private ImageRegionCopy() {
    }

    public static void copy(NativeImage source, NativeImage target,
                            int srcX, int srcY, int dstX, int dstY,
                            int width, int height, boolean flipX, boolean flipY) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int targetCol = flipX ? width - 1 - col : col;
                int targetRow = flipY ? height - 1 - row : row;
                int pixel = source.getPixelRGBA(srcX + col, srcY + row);
                target.setPixelRGBA(dstX + targetCol, dstY + targetRow, pixel);
            }
        }
    }
}
