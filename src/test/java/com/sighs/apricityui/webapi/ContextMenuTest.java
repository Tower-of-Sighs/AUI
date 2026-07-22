package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.FrameTaskScheduler;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.ui.menu.ContextMenu;
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
}
