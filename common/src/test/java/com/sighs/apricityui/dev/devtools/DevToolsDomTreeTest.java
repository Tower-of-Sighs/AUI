package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.layout.LayoutCommit;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sighs.apricityui.task.FrameTaskScheduler;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsDomTreeTest {
    @ParameterizedTest
    @ValueSource(ints = {36, 360, 3600})
    void expandingPlayerInventoryOnlyLaysOutVisibleRows(int slotCount) throws Exception {
        Size.setViewportOverride(1600, 900);
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://devtools-player-tree";
        StringBuilder slots = new StringBuilder();
        // The DOM produced by ContainerExpander, without a Minecraft runtime dependency.
        for (int index = 0; index < slotCount; index++) {
            slots.append("<slot index=\"").append(index).append("\" slot-index=\"").append(index)
                    .append("\" data-generated=\"container-auto\" part=\"")
                    .append(index < 27 ? "inv" : "hotbar")
                    .append("\"><item>minecraft:air</item></slot>");
        }
        HTML.putTemple(path, "<html><body><div class=\"group\"><div class=\"inv\">"
                + "<container id=\"player\" bind=\"player\" layout=\"preset:player\">"
                + slots + "</container></div></div></body></html>");
        Document target = Document.create(path);
        DevToolsController controller = new DevToolsController();
        try {
            Element player = target.querySelector("#player");
            assertTrue(controller.selectElement(player));
            Document tool = controller.getToolDocument();
            commit(tool, controller);
            Element tree = tool.querySelector("#domTree");
            // A fixed panel height makes the amount of work independent of inventory size.
            tree.setAttribute("style", "height:160px;flex:0 0 160px;");
            commit(tool, controller);
            Element playerRow = row(tool, player);
            Element bodyRow = row(tool, target.body);
            Element firstSlot = player.children.get(0);
            Element lastSlot = player.children.get(slotCount - 1);
            for (int iteration = 0; iteration < 3; iteration++) {
                long start = System.nanoTime();
                playerRow.children.get(0).click();
                long expanded = System.nanoTime();
                assertNull(row(tool, firstSlot), "The click must defer new row layout to budgeted frames");
                double worstFrame = 0;
                for (int frame = 0; frame < 16; frame++) {
                    var before = new java.util.HashSet<>(tree.querySelectorAll(".dom-node"));
                    long frameStart = System.nanoTime();
                    frame(tool, controller);
                    worstFrame = Math.max(worstFrame, (System.nanoTime() - frameStart) / 1e6);
                    long created = tree.querySelectorAll(".dom-node").stream().filter(view -> !before.contains(view)).count();
                    assertTrue(created <= 2, "At most two new rows may enter layout in one frame");
                }
                System.out.printf("%d slots click: %.1f ms, worst frame: %.1f ms%n", slotCount,
                        (expanded - start) / 1e6, worstFrame);
                assertSame(playerRow, row(tool, player), "Expansion must not rebuild existing rows");
                assertSame(bodyRow, row(tool, target.body));
                assertNotNull(row(tool, firstSlot));
                assertNull(row(tool, lastSlot), "Offscreen slots must not be created or laid out");
                assertTrue(tree.querySelectorAll(".dom-node").size() <= 15,
                        "Visible work must be bounded by viewport height, not the number of slots");
                assertCaretBesideLabel(playerRow);
                assertCaretBesideLabel(row(tool, firstSlot));
                playerRow.children.get(0).click();
                commit(tool, controller);
                assertTrue(controller.isCollapsed(player));
                assertNull(row(tool, firstSlot));
            }
            playerRow.children.get(0).click();
            commit(tool, controller);
            for (int offset : new int[]{400, 0, slotCount * 20 - 160, 200, 0}) {
                tree.scrollTop = offset;
                tree.targetScrollTop = offset;
                tree.getLayout().invalidateScrollVersion();
                tool.markDirty(tree, com.sighs.apricityui.render.Drawer.COMMIT_LAYOUT);
                frame(tool, controller);
                assertVisibleSlots(tool, tree, player);
            }
            // Several paint frames can pass between Minecraft's 20 Hz logical ticks.
            tree.setScrollTop(slotCount * 20 - 160);
            for (int paint = 0; paint < 12; paint++) {
                paint(tool, controller);
                assertVisibleSlots(tool, tree, player);
            }
            double[] scrollFrames = new double[120];
            for (int step = 0; step < scrollFrames.length; step++) {
                tree.scrollTop = (step % (Math.min(slotCount, 100) - 10)) * 20;
                tree.targetScrollTop = tree.scrollTop;
                tree.getLayout().invalidateScrollVersion();
                tool.markDirty(tree, com.sighs.apricityui.render.Drawer.COMMIT_LAYOUT);
                // Let the initial pool fill before asserting steady-state reuse under CPU load.
                if (step == 15) commit(tool, controller);
                var previousRows = new java.util.HashSet<>(tree.querySelectorAll(".dom-node"));
                var previousTokens = new java.util.HashSet<>(tree.querySelectorAll(".dom-content > span"));
                long panelLayout = tool.body.getLayout().layoutDependency();
                long frameStart = System.nanoTime();
                frame(tool, controller);
                scrollFrames[step] = (System.nanoTime() - frameStart) / 1e6;
                assertEquals(panelLayout, tool.body.getLayout().layoutDependency(),
                        "Scrolling tree rows must not invalidate the surrounding panels' layout");
                if (step > 15 && step < 25) {
                    assertEquals(previousRows, new java.util.HashSet<>(tree.querySelectorAll(".dom-node")),
                            "Continuous scrolling must reuse the existing row elements");
                    assertEquals(previousTokens, new java.util.HashSet<>(tree.querySelectorAll(".dom-content > span")),
                            "Scrolling slots must update attribute values without rebuilding syntax spans");
                }
                if (step == 20) {
                    commit(tool, controller);
                    // html, implicit head, body, group, inv and player precede the slots.
                    assertEquals(tree.getBoundingClientRect().y + 26,
                            row(tool, player.children.get(15)).getBoundingClientRect().y, 0.1,
                            "Recycled rows must move to the correct viewport position");
                }
            }
            java.util.Arrays.sort(scrollFrames);
            System.out.printf("%d slots scroll p50: %.1f ms, p95: %.1f ms, max: %.1f ms%n", slotCount,
                    scrollFrames[60], scrollFrames[114], scrollFrames[119]);
            tree.scrollTop = 0;
            tree.targetScrollTop = 0;
            commit(tool, controller);
            tree.scrollTop = slotCount * 20;
            tree.targetScrollTop = tree.scrollTop;
            frame(tool, controller);
            tree.scrollTop = 0;
            tree.targetScrollTop = 0;
            commit(tool, controller);
            assertNotNull(row(tool, firstSlot), "Returning before a scroll batch finishes must refill the old viewport");
            tree.scrollTop = slotCount * 20;
            tree.targetScrollTop = tree.scrollTop;
            commit(tool, controller);
            assertNotNull(row(tool, lastSlot), "Scrolling must materialize the last slot");
            assertNull(row(tool, firstSlot), "Rows leaving the viewport must be released");
            assertTrue(tree.querySelectorAll(".dom-node").size() <= 15);
            assertCaretBesideLabel(row(tool, lastSlot));
            lastSlot.setAttribute("data-updated", "fresh");
            controller.refresh();
            commit(tool, controller);
            assertTrue(row(tool, lastSlot).getTextContent().contains("fresh"));
            row(tool, lastSlot).children.get(0).click();
            commit(tool, controller);
            assertNotNull(row(tool, lastSlot.children.get(0)), "Nested expansion must still work");
            row(tool, lastSlot).click();
            assertSame(lastSlot, controller.selectedElement());
            assertNotNull(row(tool, lastSlot));
            // Cancel an in-progress expansion before its frame task starts.
            controller.toggleCollapsed(lastSlot);
            controller.toggleCollapsed(lastSlot);
            controller.toggleCollapsed(lastSlot);
            commit(tool, controller);
            assertNull(row(tool, lastSlot.children.get(0)));
            // Resize without changing the target tree; the next frame must fill the larger viewport.
            tree.setAttribute("style", "height:320px;flex:0 0 320px;");
            tree.scrollTop = 0;
            tree.targetScrollTop = 0;
            commit(tool, controller);
            assertTrue(tree.querySelectorAll(".dom-node").size() > 15);
            assertTrue(tree.querySelectorAll(".dom-node").size() <= 23);
            controller.toggle();
            FrameTaskScheduler.tick();
            assertFalse(tool.isActive(), "The viewport task must stop when DevTools closes");
        } finally {
            if (controller.isOpen()) controller.toggle();
            target.remove();
            Size.clearViewportOverride();
        }
    }

    private static void commit(Document tool, DevToolsController controller) {
        for (int frame = 0; frame < 16; frame++) frame(tool, controller);
    }

    private static void frame(Document tool, DevToolsController controller) {
        FrameTaskScheduler.tick();
        paint(tool, controller);
    }

    private static void paint(Document tool, DevToolsController controller) {
        com.sighs.apricityui.render.RectFrameCache.begin();
        com.sighs.apricityui.render.TransformFrameCache.begin();
        com.sighs.apricityui.layout.LayoutMeasureCache.begin();
        com.sighs.apricityui.style.StyleFrameCache.begin();
        try {
            tool.commitStyleRecalc();
            tool.commitRenderState();
            if (tool.stepScrollRender()) {
                com.sighs.apricityui.layout.LayoutCommit.commitScrollTranslation(tool, tool.getScrollShifts());
                tool.discardScrollShifts();
            }
            if (controller.prepareRender(tool)) {
                tool.commitPendingStyleRecalcForRender();
                tool.commitRenderState();
            }
        } finally {
            com.sighs.apricityui.style.StyleFrameCache.end();
            com.sighs.apricityui.layout.LayoutMeasureCache.end();
            com.sighs.apricityui.render.TransformFrameCache.end();
            com.sighs.apricityui.render.RectFrameCache.end();
        }
    }

    private static void assertCaretBesideLabel(Element row) {
        Element.DOMRect toggle = row.children.get(0).getBoundingClientRect();
        Element.DOMRect label = row.children.get(1).getBoundingClientRect();
        assertTrue(label.width > 0);
        assertEquals(label.x - 5.5, toggle.x + toggle.width / 2, 0.1,
                "The arrow must follow indentation and stay immediately before the label");
        assertEquals(label.y + 7.5, toggle.y + toggle.height / 2, 0.1);
    }

    private static void assertVisibleSlots(Document tool, Element tree, Element player) {
        int firstVisible = (int) Math.floor((tree.getScrollTop() - 6) / 20);
        int lastVisible = Math.min(player.children.size() + 5,
                (int) Math.floor((tree.getScrollTop() + 159 - 6) / 20));
        for (int index = Math.max(6, firstVisible); index <= lastVisible; index++) {
            Element visible = row(tool, player.children.get(index - 6));
            assertNotNull(visible, "Fast scrolling must fill every visible row before paint: " + index);
            assertFalse(visible.getAttribute("style").contains("visibility:hidden"));
            assertEquals(tree.getBoundingClientRect().y + 6 + index * 20 - tree.getScrollTop(),
                    visible.getBoundingClientRect().y, 0.1);
        }
    }

    private static Element row(Document tool, Element target) {
        return tool.querySelector("[data-node-id=\"" + target.uuid + "\"]");
    }
}
