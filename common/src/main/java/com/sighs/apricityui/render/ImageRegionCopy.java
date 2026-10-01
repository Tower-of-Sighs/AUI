package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 跨图拷贝像素区域的版本隔离层。
 *
 * <p>MC 1.19.3 起 {@code NativeImage} 才有跨图 {@code copyRect(NativeImage, ...)} 重载；
 * 1.18.2/1.19.2 只有同图内的 8 参重载，且像素读写方法名也不同（1.21.5 起
 * {@code setPixelRGBA} 变成 {@code setPixelABGR}）。字体图集的绘制代码只调本类，需要
 * 逐像素实现的 target 在 {@code prepareCommonSources} 里排除本文件并给出同名版本。</p>
 */
public final class ImageRegionCopy {
    private ImageRegionCopy() {
    }

    public static void copy(NativeImage source, NativeImage target,
                            int srcX, int srcY, int dstX, int dstY,
                            int width, int height, boolean flipX, boolean flipY) {
        source.copyRect(target, srcX, srcY, dstX, dstY, width, height, flipX, flipY);
    }
}
