package com.sighs.apricityui.client.gui.pip;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApricityUiPipSurfaceTest {
    @Test
    void divisibleFramebufferKeepsVanillaUvs() {
        var surface = new ApricityUiPipRenderer.Surface(1920, 1080, 3);
        assertEquals(640.0F, surface.guiWidth());
        assertEquals(360.0F, surface.guiHeight());
        assertEquals(0.0F, surface.u(0));
        assertEquals(1.0F, surface.u(640));
        assertEquals(1.0F, surface.v(0));
        assertEquals(0.0F, surface.v(360));
    }

    @Test
    void nonDivisibleFramebufferDoesNotStretchOrShiftPixelCenters() {
        // Covers every remainder on either axis, including a one-pixel surface.
        for (int scale = 1; scale <= 6; scale++) {
            verifyMapping(1, 1, scale);
            for (int width = 1278; width <= 1284; width++) {
                for (int height = 718; height <= 724; height++) {
                    verifyMapping(width, height, scale);
                }
            }
        }
    }

    @Test
    void offsetBlitRectangleSamplesItsOriginalFramebufferRegion() {
        var surface = new ApricityUiPipRenderer.Surface(1919, 1079, 3);
        int x0 = 7;
        int y0 = 11;
        int x1 = 640;
        int y1 = 360;
        for (int x : new int[]{x0 * 3, 1000, 1918}) {
            double guiX = (x + 0.5) / 3;
            double u = surface.u(x0) + (surface.u(x1) - surface.u(x0)) * (guiX - x0) / (x1 - x0);
            assertEquals(x + 0.5, u * 1919, 0.001);
        }
        for (int y : new int[]{y0 * 3, 600, 1078}) {
            double guiY = (y + 0.5) / 3;
            double v = surface.v(y0) + (surface.v(y1) - surface.v(y0)) * (guiY - y0) / (y1 - y0);
            assertEquals(1079 - y - 0.5, v * 1079, 0.001);
        }
    }

    @Test
    void zeroOverrideRemainsTheLiveMainTargetSentinel() throws Exception {
        // Minecraft types are supplied at test runtime, not test compile time.
        Class<?> targets = Class.forName("com.sighs.apricityui.render.OutputTargets");
        var explicitTarget = targets.getMethod("explicitTarget");
        var setCurrent = targets.getMethod("setCurrent", Class.forName("com.mojang.blaze3d.pipeline.RenderTarget"));
        Object previous = explicitTarget.invoke(null);
        try {
            setCurrent.invoke(null, new Object[]{null});
            org.junit.jupiter.api.Assertions.assertNull(explicitTarget.invoke(null));
        } finally {
            setCurrent.invoke(null, previous);
        }
    }

    private static void verifyMapping(int width, int height, int scale) {
        var surface = new ApricityUiPipRenderer.Surface(width, height, scale);
        int x1 = (width + scale - 1) / scale;
        int y1 = (height + scale - 1) / scale;
        assertTrue(surface.u(x1) >= 1.0F);
        assertTrue(surface.v(y1) <= 0.0F);
        // BlitRenderState puts (u0,v0) at the top-left, (u1,v1) at bottom-right.
        // Interpolating at physical pixel centers must address the same texel.
        for (int x : new int[]{0, width / 2, width - 1}) {
            double guiX = (x + 0.5) / scale;
            double u = surface.u(0) + (surface.u(x1) - surface.u(0)) * guiX / x1;
            assertEquals(x + 0.5, u * width, 0.001);
        }
        for (int y : new int[]{0, height / 2, height - 1}) {
            double guiY = (y + 0.5) / scale;
            double v = surface.v(0) + (surface.v(y1) - surface.v(0)) * guiY / y1;
            assertEquals(height - y - 0.5, v * height, 0.001);
        }
        Matrix4f projection = new Matrix4f().setOrtho(
                0, surface.guiWidth(), surface.guiHeight(), 0, -1024, 1024);
        // A GUI edge at y maps to the same bottom-origin coordinate Mask uses:
        // framebufferHeight - y * guiScale, without any rounded-height offset.
        float guiY = Math.min(17.25F, surface.guiHeight());
        Vector3f ndc = projection.transformPosition(new Vector3f(0, guiY, 0));
        assertEquals(height - guiY * scale, (ndc.y + 1.0) * height / 2.0, 0.001);
    }
}
