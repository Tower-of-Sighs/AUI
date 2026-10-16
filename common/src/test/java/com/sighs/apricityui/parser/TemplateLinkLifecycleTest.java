package com.sighs.apricityui.parser;

import com.sighs.apricityui.dom.DocumentRegistry;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleHandle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateLinkLifecycleTest {
    @Test
    void templateHeadLinkRemainsInDomAndRemovingItDropsItsStylesheet() throws ReflectiveOperationException {
        String path = "test://template-link-lifecycle.html";
        HTML.putTemple(path, "<html><head><link id=theme rel=stylesheet href=theme.css></head><body><p class=target>text</p></body></html>");
        Document document = new Document(path, false);
        HTML.DocumentRoot root = HTML.create(document, path);
        assertNotNull(root);
        document.documentElement = root.documentElement();
        document.head = root.head();
        document.body = root.body();

        Element link = root.head().getFirstElementChild();
        assertNotNull(link, "the template link must survive normalization and parsing");
        assertEquals("LINK", link.tagName);
        assertEquals("theme", link.getAttribute("id"));
        Field field = StyleAsyncHandler.class.getDeclaredField("HANDLES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, StyleHandle> handles = (Map<UUID, StyleHandle>) field.get(null);
        StyleHandle handle = handles.get(document.getUuid());
        assertNotNull(handle);
        assertEquals(1, handle.templateLinkOrders().size());
        int order = handle.templateLinkOrders().get(0);
        assertTrue(handle.hasCssEntry(order));
        handle.putCssEntry(order, new StyleHandle.CssEntry("theme.css", ".target { color: red; }"));
        StyleAsyncHandler.INSTANCE.handleViewportChange(document);
        assertTrue(document.CSSCache.containsKey(".target"));

        root.head().removeChild(link);
        assertFalse(link.isConnected());
        assertFalse(handle.hasCssEntry(order), "removal must free the parsed template sheet's slot");
        assertFalse(document.CSSCache.containsKey(".target"), "removed sheet must leave the cascade");

        // Simulate a previously queued template CSS result arriving after removeChild.
        Class<?> taskType = Class.forName("com.sighs.apricityui.resource.async.style.StyleAsyncHandler$ApplyTask");
        Class<?> cssTaskType = Class.forName("com.sighs.apricityui.resource.async.style.StyleAsyncHandler$CssTask");
        Constructor<?> constructor = cssTaskType.getDeclaredConstructor(
                StyleHandle.class, int.class, String.class, String.class, List.class, Element.class);
        constructor.setAccessible(true);
        Object delayed = constructor.newInstance(handle, order, "theme.css", ".target { color: blue; }", List.of(), null);
        Method apply = StyleAsyncHandler.class.getDeclaredMethod("applyOnMainThread", taskType, long.class);
        apply.setAccessible(true);
        DocumentRegistry.getAll().add(document);
        try {
            apply.invoke(StyleAsyncHandler.INSTANCE, delayed, handle.generation());
            assertFalse(handle.hasCssEntry(order), "late template load must not resurrect the removed slot");
            assertFalse(document.CSSCache.containsKey(".target"));
        } finally {
            DocumentRegistry.getAll().remove(document);
        }
    }
}
