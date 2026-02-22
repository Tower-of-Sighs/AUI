package com.sighs.apricityui.resource.async.image;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片解码后的纯 CPU 数据，不能包含任何 GL/TextureManager 行为。
 */
public class DecodedImage implements AutoCloseable {
    private final NativeImage staticImage;
    private final List<NativeImage> frames;
    private final int[] frameDelaysMs;
    private final int width;
    private final int height;

    private DecodedImage(NativeImage staticImage, List<NativeImage> frames, int[] frameDelaysMs, int width, int height) {
        this.staticImage = staticImage;
        this.frames = frames;
        this.frameDelaysMs = frameDelaysMs;
        this.width = width;
        this.height = height;
    }

    public static DecodedImage ofStatic(NativeImage image) {
        if (image == null) return null;
        return new DecodedImage(image, List.of(), new int[0], image.getWidth(), image.getHeight());
    }

    public static DecodedImage ofAnimated(List<NativeImage> images, List<Integer> delaysMs) {
        if (images == null || images.isEmpty()) return null;
        List<NativeImage> frames = new ArrayList<>(images);
        int[] delays = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            int delay = (delaysMs != null && i < delaysMs.size()) ? delaysMs.get(i) : 100;
            delays[i] = Math.max(delay, 20);
        }
        NativeImage first = frames.get(0);
        return new DecodedImage(null, frames, delays, first.getWidth(), first.getHeight());
    }

    public boolean isAnimated() {
        return !frames.isEmpty();
    }

    public NativeImage getStaticImage() {
        return staticImage;
    }

    public List<NativeImage> getFrames() {
        return frames;
    }

    public int[] getFrameDelaysMs() {
        return frameDelaysMs;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void close() {
        if (staticImage != null) {
            staticImage.close();
        }
        for (NativeImage frame : frames) {
            frame.close();
        }
    }
}
