package com.sighs.apricityui.canvas;

public class CanvasImageData {
    public final int width;
    public final int height;
    public final int[] data;

    public CanvasImageData(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.data = new int[this.width * this.height * 4];
    }
}
