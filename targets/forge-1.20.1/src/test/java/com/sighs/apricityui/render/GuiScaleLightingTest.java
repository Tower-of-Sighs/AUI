package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Native types are resolved at runtime, as in the existing target render tests. */
class GuiScaleLightingTest {
    private static Object pose() throws Exception {
        return Class.forName("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
    }

    private static void scale2D(Object pose, float x, float y) throws Exception {
        PoseMatrices.class.getMethod("scale2D", pose.getClass(), float.class, float.class).invoke(null, pose, x, y);
    }

    private static float[] matrix(Object pose, boolean normal) throws Exception {
        Object entry = pose.getClass().getMethod("last").invoke(pose);
        Object matrix = entry.getClass().getMethod(normal ? "normal" : "pose").invoke(entry);
        float[] values = new float[normal ? 9 : 16];
        matrix.getClass().getMethod("get", float[].class).invoke(matrix, (Object) values);
        return values;
    }

    @Test
    void viewportZoomChangesGeometryButNotLightingOrDepth() throws Exception {
        for (float zoom : new float[]{0.25F, 0.5F, 0.6F, 0.9F, 1F, 1.4F, 3F}) {
            Object pose = pose();
            pose.getClass().getMethod("translate", double.class, double.class, double.class)
                    .invoke(pose, 20D, 40D, 150D);
            float[] expected = matrix(pose, false);
            float[] normal = matrix(pose, true);
            for (int i = 0; i < 8; i++) expected[i] *= zoom;
            scale2D(pose, zoom, zoom);
            assertArrayEquals(expected, matrix(pose, false), 1e-6F, "geometry at zoom " + zoom);
            assertArrayEquals(normal, matrix(pose, true), 1e-6F, "lighting at zoom " + zoom);
        }
    }

    @Test
    void nestedViewportAndIconScalesPreserveExistingNormal() throws Exception {
        Object pose = pose();
        pose.getClass().getMethod("scale", float.class, float.class, float.class).invoke(pose, 2F, 3F, 4F);
        float[] normal = matrix(pose, true);
        float[] geometry = matrix(pose, false);
        pose.getClass().getMethod("pushPose").invoke(pose);
        scale2D(pose, 0.5F, 0.5F);
        scale2D(pose, 2.6F, 1.7F);
        assertArrayEquals(normal, matrix(pose, true), 1e-6F, "preserve existing normals, do not reset them");
        pose.getClass().getMethod("popPose").invoke(pose);
        assertArrayEquals(geometry, matrix(pose, false), 1e-6F);
        assertArrayEquals(normal, matrix(pose, true), 1e-6F);
    }
}
