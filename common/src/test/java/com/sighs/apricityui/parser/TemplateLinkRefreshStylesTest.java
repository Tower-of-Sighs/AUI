package com.sighs.apricityui.parser;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleHandle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateLinkRefreshStylesTest {
    @Test
    void refreshStylesDoesNotRestoreRemovedTemplateLinkOrItsCascadeSlot() throws ReflectiveOperationException {
        String path = "test://template-link-refresh.html";
        HTML.putTemple(path, "<html><head><style>.target { color: green; }</style>"
                + "<link rel=stylesheet href=removed.css>"
                + "<link rel=stylesheet href=kept.css></head><body><p class=target>text</p></body></html>");
        Document document = new Document(path, false);
        HTML.DocumentRoot root = HTML.create(document, path);
        assertNotNull(root);
        document.documentElement = root.documentElement();
        document.head = root.head();
        document.body = root.body();

        Element removed = root.head().getFirstElementChild();
        Element kept = removed.getNextElementSibling();
        assertEquals("LINK", kept.tagName);
        StyleHandle original = handleFor(document);
        assertEquals(2, original.templateLinkOrders().size());
        int removedOrder = original.templateLinkOrders().get(0);
        original.putCssEntry(removedOrder, new StyleHandle.CssEntry("removed.css", ".target { color: red; }"));
        StyleAsyncHandler.INSTANCE.handleViewportChange(document);
        assertEquals("red", document.CSSCache.get(".target").get("color").value());

        root.head().removeChild(removed);
        assertFalse(original.hasCssEntry(removedOrder));
        assertEquals("green", document.CSSCache.get(".target").get("color").value());

        document.refreshStyles();

        StyleHandle refreshed = handleFor(document);
        assertNotSame(original, refreshed);
        assertEquals(1, refreshed.templateLinkOrders().size(), "only the connected template link should be reloaded");
        assertEquals(original.snapshotCssEntries().size(), refreshed.snapshotCssEntries().size(),
                "the removed link must not reclaim a slot after refresh");
        assertEquals("green", document.CSSCache.get(".target").get("color").value());
        int keptOrder = refreshed.templateLinkOrders().get(0);
        assertTrue(refreshed.hasCssEntry(keptOrder));
        root.head().removeChild(kept);
        assertFalse(refreshed.hasCssEntry(keptOrder),
                "surviving link must still own its refreshed slot");

        document.refreshStyles();
        StyleHandle withoutLinks = handleFor(document);
        assertTrue(withoutLinks.templateLinkOrders().isEmpty());
        assertEquals("green", document.CSSCache.get(".target").get("color").value());
    }

    private static StyleHandle handleFor(Document document) throws ReflectiveOperationException {
        Field field = StyleAsyncHandler.class.getDeclaredField("HANDLES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, StyleHandle> handles = (Map<UUID, StyleHandle>) field.get(null);
        StyleHandle handle = handles.get(document.getUuid());
        assertNotNull(handle);
        return handle;
    }
}
