package com.sighs.apricityui.render;

public final class RenderBatchStats {
    private static int graphFlushes;
    private static int imageFlushes;
    private static int immediateImageFlushes;
    private static boolean frameActive;
    private static int frameGraphFlushes;
    private static int frameImageFlushes;
    private static int frameImmediateImageFlushes;
    private static int lastGraphFlushes;
    private static int lastImageFlushes;
    private static int lastImmediateImageFlushes;

    private RenderBatchStats() {
    }

    public static void beginDocument() {
        graphFlushes = 0;
        imageFlushes = 0;
        immediateImageFlushes = 0;
    }

    public static void beginFrame() {
        frameActive = true;
        frameGraphFlushes = 0;
        frameImageFlushes = 0;
        frameImmediateImageFlushes = 0;
    }

    public static void recordGraphFlush() {
        graphFlushes++;
        if (frameActive) frameGraphFlushes++;
    }

    public static void recordImageFlush() {
        imageFlushes++;
        if (frameActive) frameImageFlushes++;
    }

    public static void recordImmediateImageFlush() {
        immediateImageFlushes++;
        if (frameActive) frameImmediateImageFlushes++;
    }

    public static void endDocument() {
        if (frameActive) return;
        lastGraphFlushes = graphFlushes;
        lastImageFlushes = imageFlushes;
        lastImmediateImageFlushes = immediateImageFlushes;
    }

    public static void endFrame() {
        if (!frameActive) return;
        lastGraphFlushes = frameGraphFlushes;
        lastImageFlushes = frameImageFlushes;
        lastImmediateImageFlushes = frameImmediateImageFlushes;
        frameActive = false;
    }

    public static int lastGraphFlushes() {
        return lastGraphFlushes;
    }

    public static int lastImageFlushes() {
        return lastImageFlushes;
    }

    public static int lastImmediateImageFlushes() {
        return lastImmediateImageFlushes;
    }
}
