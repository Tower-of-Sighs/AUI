package com.sighs.apricityui.resource.async.image;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DecodedImage implements AutoCloseable {
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
        ArrayList<NativeImage> copied = new ArrayList<>(images.size());
        for (NativeImage frame : images) {
            if (frame == null) continue;
            copied.add(frame);
        }
        if (copied.isEmpty()) return null;

        int[] delays = new int[copied.size()];
        for (int i = 0; i < copied.size(); i++) {
            int delay = 100;
            if (delaysMs != null && i < delaysMs.size() && delaysMs.get(i) != null) {
                delay = delaysMs.get(i);
            }
            delays[i] = Math.max(20, delay);
        }

        NativeImage first = copied.getFirst();
        return new DecodedImage(null, Collections.unmodifiableList(copied), delays, first.getWidth(), first.getHeight());
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
