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

class RuntimeStylesheetRefreshTest {
    @Test
    void insertedStyleSurvivesRefreshAndStopsApplyingAfterRemoval() {
        String path = "test://runtime-style-refresh.html";
        HTML.putTemple(path, "<html><head><style>.target { color: green; }</style></head>"
                + "<body><p class=target>text</p></body></html>");
        Document document = createDocument(path);
        Element target = document.body.getFirstElementChild();
        Element runtimeStyle = HTML.createElement(document, "<style>.target { color: red; }</style>");
        document.head.appendChild(runtimeStyle);
        assertColor(document, target, "red");

        document.refreshStyles();
        assertColor(document, target, "red");

        document.head.removeChild(runtimeStyle);
        assertColor(document, target, "green");
    }

    @Test
    void insertedLinkKeepsItsSlotAcrossRefreshAndReleasesItOnRemoval() throws ReflectiveOperationException {
        String path = "test://runtime-link-refresh.html";
        HTML.putTemple(path, "<html><head><style>.target { color: green; }</style></head>"
                + "<body><p class=target>text</p></body></html>");
        Document document = createDocument(path);
        Element target = document.body.getFirstElementChild();
        Element runtimeLink = HTML.createElement(document, "<link rel=stylesheet href=runtime.css>");
        document.head.appendChild(runtimeLink);
        StyleHandle before = handleFor(document);
        int previousOrder = before.snapshotCssEntries().stream()
                .filter(entry -> entry.getValue().contextPath().endsWith("runtime.css"))
                .findFirst().orElseThrow().getKey();
        before.putCssEntry(previousOrder, new StyleHandle.CssEntry(path, ".target { color: red; }"));
        StyleAsyncHandler.INSTANCE.handleViewportChange(document);
        assertColor(document, target, "red");

        document.refreshStyles();
        StyleHandle refreshed = handleFor(document);
        assertNotSame(before, refreshed);
        int refreshedOrder = refreshed.snapshotCssEntries().stream()
                .filter(entry -> entry.getValue().contextPath().endsWith("runtime.css"))
                .findFirst().orElseThrow().getKey();
        refreshed.putCssEntry(refreshedOrder, new StyleHandle.CssEntry(path, ".target { color: red; }"));
        StyleAsyncHandler.INSTANCE.handleViewportChange(document);
        assertColor(document, target, "red");

        document.head.removeChild(runtimeLink);
        assertFalse(refreshed.hasCssEntry(refreshedOrder));
        assertColor(document, target, "green");
    }

    private static Document createDocument(String path) {
        Document document = new Document(path, false);
        HTML.DocumentRoot root = HTML.create(document, path);
        assertNotNull(root);
        document.documentElement = root.documentElement();
        document.head = root.head();
        document.body = root.body();
        return document;
    }

    private static void assertColor(Document document, Element target, String color) {
        document.flushPendingStyleUpdates();
        assertEquals(color, target.getComputedStyle().color);
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
