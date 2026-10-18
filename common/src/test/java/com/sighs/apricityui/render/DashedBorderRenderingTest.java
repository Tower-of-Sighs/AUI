package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashedBorderRenderingTest {
    private final List<float[]> vertices = new ArrayList<>();

    private boolean covered(float x, float y) {
        for (int i = 0; i + 2 < vertices.size(); i += 3) {
            float[] a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            if (Math.abs(area) < 0.0001) continue;
            float u = ((b[0] - x) * (c[1] - y) - (b[1] - y) * (c[0] - x)) / area;
            float v = ((c[0] - x) * (a[1] - y) - (c[1] - y) * (a[0] - x)) / area;
            float w = 1 - u - v;
            if (u >= -0.0001 && v >= -0.0001 && w >= -0.0001) return true;
        }
        return false;
    }

    private void render(String style) throws Exception {
        vertices.clear();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex":
                            if ((int) args[8] > 0) vertices.add(new float[]{(float) args[2], (float) args[3]});
                            return null;
                        case "getProjectionMatrix": return Class.forName("org.joml.Matrix4f").getConstructor().newInstance();
                        case "getGLVersionString": return "";
                        case "isOnRenderThread": return true;
                        case "recordRenderCall": ((Runnable) args[0]).run(); return null;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                }));
        try {
            Size.setViewportOverride(1000, 800);
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");
            Element element = document.createElement("div");
            element.setAttribute("style", "width:120px;height:60px;" + style);
            document.body.appendChild(element);
            document.commitRenderState();
            Rect rect = Rect.of(element);
            assertEquals(style.contains("dashed") ? "dashed" : style.contains("double") ? "double" : "solid",
                    rect.box.getBorderTopSide().type());
            Class<?> pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Rect.class.getMethod("drawBorder", pose).invoke(rect, pose.getConstructor().newInstance());
            Graph.endBatch();
            float x = (float) (rect.position.x + rect.box.getMarginLeft());
            float y = (float) (rect.position.y + rect.box.getMarginTop());
            for (float[] vertex : vertices) { vertex[0] -= x; vertex[1] -= y; }
        } finally {
            Graph.endBatch();
            Size.clearViewportOverride();
            AuiServices.setRender(previous);
        }
    }

    @Test
    void t11TwoPixelDashedBorderHasPaintAndGapsOnEverySide() throws Exception {
        render("border:2px dashed #2c2c2c;");
        assertTrue(covered(20.5f, 0.5f));
        int topPaint = 0, topGaps = 0, leftPaint = 0, leftGaps = 0;
        for (int i = 10; i < 50; i++) {
            if (covered(i + 0.25f, 0.5f)) topPaint++; else topGaps++;
            if (covered(0.5f, i + 0.25f)) leftPaint++; else leftGaps++;
        }
        assertTrue(topPaint > 10 && topGaps > 10, "Dashed top must not become a solid strip");
        assertTrue(leftPaint > 10 && leftGaps > 10, "Dashed left must not become a solid strip");
        assertFalse(covered(30, 30));
    }

    @Test
    void roundedDashedBorderRetainsGapsAndHollowInterior() throws Exception {
        render("border:2px dashed #2c2c2c;border-radius:12px;");
        int gaps = 0;
        for (int i = 20; i < 100; i++) if (!covered(i + 0.25f, 0.5f)) gaps++;
        assertTrue(gaps > 20, "Rounded borders also need real unpainted gaps");
        assertFalse(covered(0.25f, 0.25f));
        assertFalse(covered(30, 30));
    }

    @Test
    void bottomAndRightAlsoHaveGaps() throws Exception {
        render("border:2px dashed #2c2c2c;");
        int bottomPaint = 0, bottomGaps = 0, rightPaint = 0, rightGaps = 0;
        for (int i = 10; i < 50; i++) {
            if (covered(i + 0.25f, 63.5f)) bottomPaint++; else bottomGaps++;
            if (covered(123.5f, i + 0.25f)) rightPaint++; else rightGaps++;
        }
        assertTrue(bottomPaint > 10 && bottomGaps > 10);
        assertTrue(rightPaint > 10 && rightGaps > 10);
    }

    @Test
    void mixedSolidAndAbsentSidesAreNotTurnedIntoDashes() throws Exception {
        render("border:2px dashed #2c2c2c;border-bottom:4px solid #ff0000;border-right:0 solid #000000;");
        for (int i = 10; i < 100; i++) assertTrue(covered(i + 0.25f, 65.5f));
        for (int i = 10; i < 50; i++) assertFalse(covered(119.5f, i + 0.25f));
    }

    @Test
    void doubleBorderLeavesTransparentMiddleOnAllSides() throws Exception {
        render("border:4px double #2c2c2c;");
        for (float depth : new float[]{0.5f, 3.5f}) {
            assertTrue(covered(30, depth));
            assertTrue(covered(depth, 30));
            assertTrue(covered(30, 68 - depth));
            assertTrue(covered(128 - depth, 30));
        }
        assertFalse(covered(30, 2));
        assertFalse(covered(2, 30));
        assertFalse(covered(30, 66));
        assertFalse(covered(126, 30));
        assertFalse(covered(30, 30));
    }

    @Test
    void roundedDoubleBorderRetainsTwoStripes() throws Exception {
        render("border:6px double #2c2c2c;border-radius:18px;");
        assertFalse(covered(0.25f, 0.25f));
        assertTrue(covered(40, 1));
        assertFalse(covered(40, 3));
        assertTrue(covered(40, 5));
        assertFalse(covered(30, 30));
    }

    @Test
    void doubleBorderDoesNotSplitAdjacentSolidSide() throws Exception {
        render("border:4px double #2c2c2c;border-bottom:4px solid red;");
        assertFalse(covered(30, 2));
        assertTrue(covered(30, 66));
    }

    @Test
    void thinDoubleBorderFallsBackToSolid() throws Exception {
        render("border:2px double #2c2c2c;");
        assertTrue(covered(30, 0.5f));
        assertTrue(covered(30, 1.5f));
    }

    @Test
    void solidBorderStillHasContinuousStrips() throws Exception {
        render("border:2px solid #2c2c2c;");
        for (int i = 10; i < 100; i++) assertTrue(covered(i + 0.25f, 0.5f));
    }
}
