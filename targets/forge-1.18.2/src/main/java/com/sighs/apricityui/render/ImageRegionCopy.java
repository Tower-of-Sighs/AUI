package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * {@link ImageRegionCopy} 的 1.18.2 实现。
 *
 * <p>跨图 {@code copyRect(NativeImage, ...)} 是 1.19.3 才加的重载，这里按原版实现用
 * {@code getPixelRGBA}/{@code setPixelRGBA} 逐像素自己做一遍，结果与跨图重载逐像素一致。</p>
 */
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
