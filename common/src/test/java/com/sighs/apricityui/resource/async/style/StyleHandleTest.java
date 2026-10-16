package com.sighs.apricityui.resource.async.style;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleHandleTest {
    @Test
    void removedRuntimeSheetDoesNotReuseOrderWhileOlderLoadIsPending() {
        StyleHandle handle = new StyleHandle(UUID.randomUUID(), 0);
        handle.putCssEntry(0, new StyleHandle.CssEntry("default.css", ""));
        int first = handle.nextOrder();
        handle.putCssEntry(first, new StyleHandle.CssEntry("first.css", ""));
        handle.removeCssEntry(first);

        int second = handle.nextOrder();
        handle.putCssEntry(second, new StyleHandle.CssEntry("second.css", ".box { color: blue; }"));
        handle.removeCssEntry(first);

        assertEquals(2, second);
        assertEquals("second.css", handle.snapshotCssEntries().get(1).getValue().contextPath());
    }

    @Test
    void registeredTemplateSlotsPrecedeRuntimeSheetsEvenWhenLoading() {
        StyleHandle handle = new StyleHandle(UUID.randomUUID(), 0);
        handle.putCssEntry(0, new StyleHandle.CssEntry("global.css", ""));
        handle.putCssEntry(1, new StyleHandle.CssEntry("template.css", ""));

        int runtimeOrder = handle.nextOrder();
        handle.putCssEntry(runtimeOrder, new StyleHandle.CssEntry("runtime.css", ""));
        handle.putCssEntry(1, new StyleHandle.CssEntry("template.css", ".box { color: red; }"));

        assertEquals(2, runtimeOrder);
        assertEquals("runtime.css", handle.snapshotCssEntries().get(2).getValue().contextPath());
    }
}
