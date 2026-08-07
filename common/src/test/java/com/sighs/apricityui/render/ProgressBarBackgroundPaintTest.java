package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the ore-theme progress bar paint path headlessly: a filled
 * `.progress-bar` inside an `overflow:hidden` track must emit its background
 * vertices through the render service and have them submitted.
 *
 * Minecraft/JOML types are only present on the test runtime classpath, so they
 * are accessed reflectively (matching how other common tests stay MC-free at
 * compile time).
 */
class ProgressBarBackgroundPaintTest {

    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPoseStack() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void drawBody(Rect rect, Size size) {
        try {
            Class<?> poseStackClass = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Method drawBody = Rect.class.getMethod("drawBody", poseStackClass, Size.class);
            drawBody.invoke(rect, newPoseStack(), size);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Recording {
        final Map<Object, List<int[]>> meshVertices = new IdentityHashMap<>();
        final List<List<int[]>> submissions = new ArrayList<>();

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "beginMesh": {
                                Object token = new Object();
                                meshVertices.put(token, new ArrayList<>());
                                return MeshBuilder.of(token);
                            }
                            case "emitVertex": {
                                Object mesh = args[0];
                                float x = (float) args[2];
                                float y = (float) args[3];
                                int r = (int) args[5];
                                int g = (int) args[6];
                                int b = (int) args[7];
                                int a = (int) args[8];
                                List<int[]> verts = meshVertices.get(mesh);
                                if (verts != null) verts.add(new int[]{r, g, b, a, Math.round(x), Math.round(y)});
                                return null;
                            }
                            case "submitMesh": {
                                List<int[]> verts = meshVertices.get(args[0]);
                                submissions.add(verts == null ? List.of() : List.copyOf(verts));
                                return null;
                            }
                            case "getProjectionMatrix":
                                return newMatrix4f();
                            case "getGLVersionString":
                                return "";
                            case "isOnRenderThread":
                                return true;
                            case "recordRenderCall":
                                ((Runnable) args[0]).run();
                                return null;
                            case "beginTextureBatch":
                                return new Object();
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return false;
                                if (rt == int.class) return 0;
                                if (rt == float.class) return 0f;
                                return null;
                        }
                    });
        }

        long countColor(int r, int g, int b) {
            return meshVertices.values().stream().flatMap(List::stream)
                    .filter(v -> v[0] == r && v[1] == g && v[2] == b && v[3] > 0)
                    .count();
        }

        long countSubmittedColor(int r, int g, int b) {
            return submissions.stream().flatMap(List::stream)
                    .filter(v -> v[0] == r && v[1] == g && v[2] == b && v[3] > 0)
                    .count();
        }
    }

    @Test
    void progressBarBackgroundVerticesAreEmittedAndSubmitted() {
        Recording recording = new Recording();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width: 300px; height: 200px;");
            Element track = new Element(document, "div");
            track.setAttribute("style", "height:24px;padding:3px;border:3px solid #1e1e1f;"
                    + "background:#252628;box-shadow:inset 0 3px #151516;overflow:hidden;");
            Element fill = new Element(document, "div");
            fill.setAttribute("style", "height:100%;min-width:0;width:82%;background:#3c8527;"
                    + "box-shadow:inset 0 -4px #1d4d13, inset 2px 2px rgba(255,255,255,0.2);");
            track.appendChild(fill);
            document.body.appendChild(track);

            document.commitRenderState();
            Rect rect = Rect.of(fill);

            System.out.println("[progress-test] background.color=" + rect.background.color);
            System.out.println("[progress-test] bodyRectPos=" + rect.getBodyRectPosition()
                    + " bodyRectSize=" + rect.getBodyRectSize());
            System.out.println("[progress-test] insetShadows="
                    + rect.box.shadows.stream().filter(s -> s.inset()).count());

            drawBody(rect, rect.getBodyRectSize());
            Graph.endBatch();

            System.out.println("[progress-test] totalMeshes=" + recording.meshVertices.size()
                    + " submissions=" + recording.submissions.size());
            System.out.println("[progress-test] greenVerts=" + recording.countColor(60, 133, 39)
                    + " greenSubmitted=" + recording.countSubmittedColor(60, 133, 39));

            assertEquals("#3c8527", rect.background.color, "background color must resolve");
            assertTrue(recording.countColor(60, 133, 39) > 0,
                    "the fill background must emit green vertices");
            assertTrue(recording.countSubmittedColor(60, 133, 39) > 0,
                    "the fill background mesh must be submitted");
        } finally {
            AuiServices.setRender(previous);
        }
    }

    @Test
    void trackBackgroundAlsoEmits() {
        Recording recording = new Recording();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width: 300px; height: 200px;");
            Element track = new Element(document, "div");
            track.setAttribute("style", "height:24px;padding:3px;border:3px solid #1e1e1f;"
                    + "background:#252628;box-shadow:inset 0 3px #151516;overflow:hidden;");
            document.body.appendChild(track);

            document.commitRenderState();
            Rect rect = Rect.of(track);

            drawBody(rect, rect.getBodyRectSize());
            Graph.endBatch();

            assertFalse(recording.meshVertices.isEmpty(), "the track background must emit vertices");
            assertTrue(recording.countColor(37, 38, 40) > 0,
                    "the track background must emit #252628 vertices");
        } finally {
            AuiServices.setRender(previous);
        }
    }
}
