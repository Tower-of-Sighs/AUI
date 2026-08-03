package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.ui.ContextMenu;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuTest {
    @Test
    void buildsReferenceMenuAndClosesAfterAction() {
        Size.setViewportOverride(320, 200);
        Document document = TestDocumentFactory.createDocument();
        AtomicBoolean called = new AtomicBoolean();
        ContextMenu menu = ContextMenu.show(document, new Position(310, 190), List.of(
                ContextMenu.Item.header("TEST"),
                ContextMenu.Item.action("OPEN", ContextMenu.Icons.OPEN, "ENTER", () -> called.set(true)),
                ContextMenu.Item.separator(),
                ContextMenu.Item.action("DELETE", ContextMenu.Icons.DELETE, "DEL", () -> {}).dangerous()
        ));
        try {
            FrameTaskScheduler.tick();

            Element root = document.querySelector(".aui-context-menu-backdrop");
            Element rendered = document.querySelector(".ctx-menu");
            assertNotNull(root);
            assertNotNull(rendered);
            assertTrue(root.isTopLayer());
            assertEquals(1, document.querySelectorAll(".ctx-header").size());
            assertEquals(2, document.querySelectorAll(".ctx-item").size());
            assertEquals(1, document.querySelectorAll(".ctx-sep").size());
            String menuStyle = rendered.getAttribute("style");
            assertTrue(menuStyle.contains("left:4.00px"), menuStyle);
            assertTrue(menuStyle.contains("top:4.00px"), menuStyle);

            document.querySelector(".ctx-item").click();
            assertTrue(called.get());
            assertFalse(menu.isOpen());
        } finally {
            ContextMenu.closeActive();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void childHitChangesKeepHoverAndActivateTheMenuRow() {
        Size.setViewportOverride(320, 200);
        Document document = TestDocumentFactory.createDocument();
        AtomicBoolean called = new AtomicBoolean();
        ContextMenu menu = ContextMenu.show(document, new Position(20, 20), List.of(
                ContextMenu.Item.action("OPEN", ContextMenu.Icons.OPEN, "ENTER", () -> called.set(true))
        ));
        try {
            FrameTaskScheduler.tick();

            Element row = document.querySelector(".ctx-item");
            Element icon = document.querySelector(".ctx-icon");
            Element label = document.querySelector(".ctx-label");
            Element fill = document.querySelector(".ctx-item-fill");
            assertNotNull(row);
            assertNotNull(icon);
            assertNotNull(label);
            assertNotNull(fill);

            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(24, 24), -1, false), document, icon);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(36, 24), -1, false), document, label);
            assertTrue(row.isHover);
            assertTrue(row.getAttribute("style").contains("color:#ffffff"));
            assertTrue(fill.getAttribute("style").contains("width:100%"));

            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(24, 24), 0, false), document, icon);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(36, 24), 0, false), document, label);

            assertTrue(called.get());
            assertFalse(menu.isOpen());
        } finally {
            ContextMenu.closeActive();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void longHeaderStaysOnOneLineAndUsesEllipsis() {
        Size.setViewportOverride(640, 360);
        Document document = TestDocumentFactory.createDocument();
        String longName = "THIS_IS_A_VERY_LONG_RESOURCE_FILE_NAME_THAT_MUST_NOT_WRAP.html";
        ContextMenu menu = ContextMenu.show(document, new Position(20, 20), List.of(
                ContextMenu.Item.header(longName),
                ContextMenu.Item.action("OPEN", ContextMenu.Icons.OPEN, () -> {})
        ));
        try {
            FrameTaskScheduler.tick();

            Element rendered = document.querySelector(".ctx-menu");
            Element header = document.querySelector(".ctx-header");
            Element action = document.querySelector(".ctx-item");
            assertNotNull(rendered);
            assertNotNull(header);
            assertNotNull(action);

            String headerStyle = header.getAttribute("style");
            assertTrue(headerStyle.contains("white-space:nowrap"), headerStyle);
            assertTrue(headerStyle.contains("overflow:hidden"), headerStyle);
            assertTrue(headerStyle.contains("text-overflow:ellipsis"), headerStyle);
            assertTrue(headerStyle.contains("max-width:100%"), headerStyle);
            assertTrue(header.getBoundingClientRect().height < 30,
                    "long menu header must remain a single line");
            assertTrue(action.getBoundingClientRect().y >= header.getBoundingClientRect().bottom,
                    "menu item must remain below the single-line header");
            assertTrue(rendered.getBoundingClientRect().width <= 360.01,
                    "long header must not widen the menu without bound");
        } finally {
            menu.close();
            document.remove();
            Size.clearViewportOverride();
        }
    }
}
