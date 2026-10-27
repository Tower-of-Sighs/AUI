package com.sighs.apricityui.resource.async.style;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ------------------------------------------------------------------
    // 跨 tick 攒批：多张表到齐后再统一重建/重放；字体失效并入同一批
    // ------------------------------------------------------------------

    @Test
    void cssPublishWaitsForInFlightSheetsUntilDeadlineOrDrained() {
        long now = 1_000_000_000L;
        long deadline = now + 250_000_000L;

        assertFalse(StyleAsyncHandler.mustPublishCssNow(true, deadline, now),
                "仍有表在路上时先攒着");
        assertFalse(StyleAsyncHandler.mustPublishCssNow(true, deadline, deadline - 1));
        assertTrue(StyleAsyncHandler.mustPublishCssNow(true, deadline, deadline),
                "超出等待上限必须发布，不能无限等");
        assertTrue(StyleAsyncHandler.mustPublishCssNow(false, deadline, now),
                "最后一张表落地后立即发布");
    }

    @Test
    void coalescerDrainsEachDocumentOnceAndCanDropOne() {
        StyleAsyncHandler.DocumentCoalescer coalescer = new StyleAsyncHandler.DocumentCoalescer();
        com.sighs.apricityui.init.Document document =
                com.sighs.apricityui.webapi.TestDocumentFactory.createDocument();
        try {
            coalescer.add(document);
            coalescer.add(document);
            coalescer.add(null);
            assertFalse(coalescer.isEmpty());
            java.util.List<com.sighs.apricityui.init.Document> batch = coalescer.drain();
            assertEquals(1, batch.size(), "同一批内重复到达只能重建/重排一次");
            assertSame(document, batch.get(0));
            assertTrue(coalescer.drain().isEmpty());

            coalescer.add(document);
            assertTrue(coalescer.remove(document), "字体失效并入样式发布时需要能单独摘除");
            assertTrue(coalescer.drain().isEmpty());
            assertFalse(coalescer.remove(document));
        } finally {
            document.remove();
        }
    }

    @Test
    void coalescerKeepsDistinctDocumentsAndDropsThemOnReload() {
        StyleAsyncHandler.DocumentCoalescer coalescer = new StyleAsyncHandler.DocumentCoalescer();
        com.sighs.apricityui.init.Document first =
                com.sighs.apricityui.webapi.TestDocumentFactory.createDocument();
        com.sighs.apricityui.init.Document second =
                com.sighs.apricityui.webapi.TestDocumentFactory.createDocument();
        try {
            coalescer.add(first);
            coalescer.add(second);
            coalescer.add(first);
            assertEquals(2, coalescer.drain().size());

            coalescer.add(first);
            coalescer.clear();
            assertTrue(coalescer.drain().isEmpty(), "资源重载后旧文档的待处理项必须丢弃");
        } finally {
            first.remove();
            second.remove();
        }
    }

    @Test
    void fontDecodeIsClaimedOncePerGenerationAndPath() {
        StyleAsyncHandler.FontDecodeCache cache = new StyleAsyncHandler.FontDecodeCache();
        StyleAsyncHandler.FontDecodeCache.Claim first = cache.claim(1L, "t01-ore/fonts/a.otf");
        StyleAsyncHandler.FontDecodeCache.Claim second = cache.claim(1L, "t01-ore/fonts/a.otf");
        assertTrue(first.owner(), "首个请求负责读取与解码");
        assertFalse(second.owner(), "同代际同路径共享同一个解码结果");
        assertSame(first.future(), second.future());

        java.awt.Font decoded = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 16);
        first.future().complete(decoded);
        StyleAsyncHandler.FontDecodeCache.Claim later = cache.claim(1L, "t01-ore/fonts/a.otf");
        assertFalse(later.owner());
        assertSame(decoded, later.future().join());

        assertTrue(cache.claim(2L, "t01-ore/fonts/a.otf").owner(), "资源代际变化后必须重新解码");
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void decodedFontFailurePropagatesToEveryWaiter() {
        StyleAsyncHandler.FontDecodeCache cache = new StyleAsyncHandler.FontDecodeCache();
        StyleAsyncHandler.FontDecodeCache.Claim owner = cache.claim(3L, "missing.otf");
        StyleAsyncHandler.FontDecodeCache.Claim waiter = cache.claim(3L, "missing.otf");
        IllegalStateException failure = new IllegalStateException("decode failed");
        owner.future().completeExceptionally(failure);
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class,
                () -> waiter.future().join()).getCause());
    }
}
