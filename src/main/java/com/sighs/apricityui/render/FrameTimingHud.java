package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.instance.ApricityUIConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.Locale;

public final class FrameTimingHud {
    private static final int SAMPLE_COUNT = 120;
    private static final long[] SAMPLES = new long[SAMPLE_COUNT];
    private static int sampleIndex = 0;
    private static int sampleSize = 0;

    private FrameTimingHud() {
    }

    public static void record(long elapsedNs) {
        if (!isEnabled()) {
            clear();
            return;
        }
        if (elapsedNs <= 0) return;
        SAMPLES[sampleIndex] = elapsedNs;
        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;
        if (sampleSize < SAMPLE_COUNT) {
            sampleSize++;
        }
    }

    public static void draw(PoseStack poseStack) {
        if (!isEnabled()) return;
        if (poseStack == null) return;
        if (sampleSize == 0) return;

        long min = Long.MAX_VALUE;
        long max = 0L;
        long sum = 0L;
        for (int i = 0; i < sampleSize; i++) {
            long value = SAMPLES[i];
            if (value <= 0) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        if (min == Long.MAX_VALUE) return;

        double avg = (double) sum / sampleSize;
        String text = String.format(
                Locale.ROOT,
                "max %.2f ms  min %.2f ms  avg %.2f ms",
                toMillis(max),
                toMillis(min),
                toMillis(avg)
        );
        Minecraft minecraft = Minecraft.getInstance();
        Mask.resetDepth();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        int width = minecraft.font.width(text) + 8;
        poseStack.pushPose();
        poseStack.translate(0, 0, 0);
        drawRectNoDepth(poseStack.last().pose(), 2, 2, 2 + width, 16, 0xCC000000);
        RenderSystem.disableDepthTest();
        minecraft.font.drawInBatch(
                Component.literal(text).getVisualOrderText(),
                6,
                6,
                0xFF00FF66,
                false,
                poseStack.last().pose(),
                minecraft.renderBuffers().bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                0,
                15728880
        );
        minecraft.renderBuffers().bufferSource().endBatch();
        poseStack.popPose();
        RenderSystem.enableDepthTest();
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static double toMillis(double nanos) {
        return nanos / 1_000_000.0d;
    }

    private static boolean isEnabled() {
        try {
            return ApricityUIConfig.CLIENT.frameTimingHud.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void clear() {
        if (sampleSize == 0 && sampleIndex == 0) return;
        Arrays.fill(SAMPLES, 0L);
        sampleIndex = 0;
        sampleSize = 0;
    }

    private static void drawRectNoDepth(Matrix4f matrix, float x0, float y0, float x1, float y1, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x0, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}
