package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.canvas.OreCanvasRenderer;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreCanvasRendererPerformanceTest {
    @Test
    void projectsTwoHundredCanvasNodesWithoutAnAlgorithmicStall() {
        assertProjectionCompletes(200, 5_000);
    }

    @Test
    void projectsFiveHundredCanvasNodesWithoutAnAlgorithmicStall() {
        assertProjectionCompletes(500, 8_000);
    }

    private static void assertProjectionCompletes(int nodeCount, long maximumMillis) {
        String path = "test://ore-canvas-performance-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body><div id=\"canvas\"></div></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            OreEditorProject project = new OreEditorProject();
            for (int index = 0; index < nodeCount; index++) project.root().add(new OreComponentNode("div", ""));
            Element canvas = document.querySelector("#canvas");
            OreCanvasRenderer renderer = new OreCanvasRenderer(document, canvas, ignored -> { });

            long started = System.nanoTime();
            renderer.render(project, null);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertEquals(nodeCount + 1, renderer.elements().size());
            assertTrue(elapsedMillis < maximumMillis,
                    nodeCount + "-node projection took " + elapsedMillis + "ms");
        } finally {
            document.remove();
        }
    }
}
