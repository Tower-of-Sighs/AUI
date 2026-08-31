package com.sighs.apricityui.render;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontDrawerDynamicTextSchedulingTest {
    private ManualExecutor executor;
    private Executor previousExecutor;
    private AuiClientService previousClient;
    private AtomicInteger defaultFontDraws;
    private Input owner;
    private Element staticOwner;

    @BeforeEach
    void setUp() {
        previousExecutor = FontDrawer.installRasterExecutorForTesting(executor = new ManualExecutor());
        FontDrawer.clearCache();
        previousClient = AuiServices.client();
        defaultFontDraws = new AtomicInteger();
        AuiServices.setClient((AuiClientService) Proxy.newProxyInstance(
                AuiClientService.class.getClassLoader(),
                new Class<?>[]{AuiClientService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("drawDefaultFont")) defaultFontDraws.incrementAndGet();
                    return method.invoke(previousClient, args);
                }));
        owner = new Input(new Document("test://dynamic-font", false));
        staticOwner = owner.document.createElement("span");
    }

    @AfterEach
    void tearDown() {
        AuiServices.setClient(previousClient);
        FontDrawer.clearCache();
        FontDrawer.installRasterExecutorForTesting(previousExecutor);
    }

    @Test
    void dynamicMissQueuesAsyncWithoutDefaultFontFallback() {
        draw("value-0");

        assertEquals(1, executor.size());
        assertEquals(0, defaultFontDraws.get());
        assertTrue(FontDrawer.dynamicActiveForTesting(owner));
    }

    @Test
    void textNodeMutationMarksItsGenericElementAsDynamic() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        TextNode node = new TextNode(document, "old");
        element.appendChild(node);
        document.body.appendChild(element);
        node.setTextContent("new");

        assertTrue(FontDrawer.dynamicOwnerForTesting(element));
    }

    @Test
    void elementTextContentMutationMarksOwnerBeforeFirstCustomFontDraw() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        element.setTextContent("initial mount");
        assertFalse(FontDrawer.dynamicOwnerForTesting(element));

        document.body.appendChild(element);
        element.setTextContent("new");

        assertTrue(FontDrawer.dynamicOwnerForTesting(element));
    }

    @Test
    void dynamicOwnerCoalescesLatestRequestAndRejectsStaleCompletion() {
        draw("value-0");
        draw("value-1");
        draw("value-2");

        assertEquals(1, executor.size());
        assertTrue(FontDrawer.dynamicLatestKeyForTesting(owner).contains("value-2"));

        executor.runNext();
        draw("value-3");
        FontDrawer.drainCompletedRasters();

        assertNull(FontDrawer.dynamicPublishedKeyForTesting(owner));
        assertFalse(FontDrawer.dynamicActiveForTesting(owner));
        assertEquals(0, defaultFontDraws.get());

        draw("value-3");
        assertEquals(1, executor.size());
        executor.runNext();
        FontDrawer.drainCompletedRasters();
        assertTrue(FontDrawer.dynamicPublishedKeyForTesting(owner).contains("value-3"));

        draw("value-4");
        assertEquals(1, executor.size());
        executor.runNext();
        FontDrawer.drainCompletedRasters();

        assertTrue(FontDrawer.dynamicPublishedKeyForTesting(owner).contains("value-4"));
        assertEquals(1, FontDrawer.dynamicReplacementCountForTesting(owner));
    }

    @Test
    void pendingDynamicValueKeepsTheLastPublishedTextureVisible() {
        draw("value-0");
        executor.runNext();
        FontDrawer.drainCompletedRasters();

        FontDrawer.FontEntry previous = draw("value-1");

        assertNotNull(previous);
        assertTrue(FontDrawer.dynamicPublishedKeyForTesting(owner).contains("value-0"));
        assertEquals(1, executor.size());
    }

    @Test
    void completedStaticRasterStaysPendingUntilRenderThreadPublication() {
        assertNull(drawStatic("static-label"));
        assertEquals(1, executor.size());

        executor.runNext();
        assertNull(drawStatic("static-label"));
        assertEquals(0, executor.size(), "completed-but-unpublished key was queued twice");

        FontDrawer.drainCompletedRasters();
        assertNotNull(drawStatic("static-label"));
        assertEquals(0, executor.size());
    }

    @Test
    void dynamicRasterIsNotBlockedByTheStaticPageBacklog() {
        ManualExecutor staticExecutor = new ManualExecutor();
        ManualExecutor dynamicExecutor = new ManualExecutor();
        Executor previous = FontDrawer.installRasterExecutorsForTesting(staticExecutor, dynamicExecutor);
        try {
            assertNull(drawStatic("page-backlog"));
            assertNull(draw("urgent-pop"));
            assertEquals(1, staticExecutor.size());
            assertEquals(1, dynamicExecutor.size());

            dynamicExecutor.runNext();
            FontDrawer.drainCompletedRasters();

            assertTrue(FontDrawer.dynamicPublishedKeyForTesting(owner).contains("urgent-pop"));
            assertEquals(1, staticExecutor.size(), "static backlog should remain queued");
        } finally {
            FontDrawer.installRasterExecutorForTesting(previous);
        }
    }

    private FontDrawer.FontEntry draw(String value) {
        Text text = Text.of(owner);
        text.fontFamily = "sans-serif";
        text.fontSize = 16;
        text.fontWeight = 400;
        text.lineHeight = 20;
        text.content = value;
        return FontDrawer.requestDynamicTextForTesting(text, new Position(10, 20));
    }

    private FontDrawer.FontEntry drawStatic(String value) {
        Text text = Text.of(staticOwner);
        text.fontFamily = "sans-serif";
        text.fontSize = 16;
        text.fontWeight = 400;
        text.lineHeight = 20;
        text.content = value;
        return FontDrawer.requestStaticTextForTesting(text, new Position(10, 20));
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            Runnable task = tasks.pollFirst();
            assertTrue(task != null, "expected a queued raster task");
            task.run();
        }
    }
}
