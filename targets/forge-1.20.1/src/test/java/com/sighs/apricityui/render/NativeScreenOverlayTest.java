package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeScreenOverlayTest {
    @Test
    void nativeDepthIsDiscardedOnceBeforeAllOverlaysButAuiOwnerDepthIsRetained() throws Exception {
        List<String> events = new ArrayList<>();
        List<Document> documents = new ArrayList<>();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "flushSharedBuffers": events.add("commit"); return null;
                        case "clearDepthBuffer": events.add("clear-depth"); return null;
                        case "pushFilterRenderState": events.add("document"); return AuiRenderService.RenderStateScope.NOOP;
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "beginTextureBatch": return new Object();
                        case "getProjectionMatrix": return Class.forName("org.joml.Matrix4f").getConstructor().newInstance();
                        case "getGLVersionString": return "";
                        case "isOnRenderThread": return true;
                        case "recordRenderCall": ((Runnable) args[0]).run(); return null;
                        default:
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) return false;
                            if (type == int.class) return 0;
                            if (type == float.class) return 0F;
                            return null;
                    }
                }));
        try {
            Class<?> poseType = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Object pose = poseType.getConstructor().newInstance();
            var draw = Base.class.getMethod("drawPersistentScreenDocuments", poseType, Document.class);
            document("ordinary", documents);
            Document world = document("world", documents);
            world.setReloadPersistent(true);
            Document manual = document("manual", documents);
            manual.setManuallyRendered(true);
            manual.setReloadPersistent(true);

            draw.invoke(null, pose, null);
            assertTrue(events.isEmpty(), "No eligible overlays must leave native depth and buffers untouched");

            Document first = document("first-overlay", documents);
            first.setReloadPersistent(true);
            Document second = document("second-overlay", documents);
            second.setReloadPersistent(true);
            events.clear();
            draw.invoke(null, pose, null);
            assertEquals(List.of("commit", "clear-depth", "document"), events.subList(0, 3));
            assertEquals(1L, events.stream().filter("clear-depth"::equals).count());
            assertEquals(2L, events.stream().filter("document"::equals).count());

            events.clear();
            draw.invoke(null, pose, first);
            assertFalse(events.contains("clear-depth"), "An AUI screen and its overlays share their depth ledger");
            assertEquals(1L, events.stream().filter("document"::equals).count());
        } finally {
            documents.forEach(Document::remove);
            AuiServices.setRender(previous);
        }
    }

    private static Document document(String name, List<Document> documents) {
        String path = "test://native-screen-" + name;
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = name.equals("world") ? Document.createInWorld(path) : Document.create(path);
        documents.add(document);
        return document;
    }
}
