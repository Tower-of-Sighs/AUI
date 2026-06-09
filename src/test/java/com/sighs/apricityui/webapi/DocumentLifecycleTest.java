package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DocumentLifecycleTest {
    @Test
    void htmlTempleRoundTripSupportsDocumentOwnedMarkupParsing() {
        HTML.putTemple("test://doc-template", """
                <body data-page="alpha">
                  <main id="app"><span>ok</span></main>
                </body>
                """);

        Document document = new Document("test://doc-template", false);
        Element root = HTML.create(document, "test://doc-template");

        assertNotNull(root);
        assertEquals("BODY", root.getNodeName());
        assertEquals("alpha", root.getAttribute("data-page"));
        assertEquals("MAIN", root.getFirstElementChild().getNodeName());
        assertEquals("app", root.getFirstElementChild().getAttribute("id"));
    }

    @Test
    void lifecycleEventsDispatchAgainstCurrentBodyWhenDocumentIsActive() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        AtomicInteger domReadyCalls = new AtomicInteger();
        AtomicInteger loadCalls = new AtomicInteger();

        document.body.addEventListener("DOMContentLoaded", event -> domReadyCalls.incrementAndGet());
        document.body.addEventListener("load", event -> loadCalls.incrementAndGet());

        invokeLifecycle(document, "enterInteractive");
        invokeFireLifecycleEvent(document, "DOMContentLoaded", false);
        invokeLifecycle(document, "enterComplete");
        invokeFireLifecycleEvent(document, "load", false);

        assertEquals(1, domReadyCalls.get());
        assertEquals(1, loadCalls.get());
        assertEquals("complete", document.getReadyState());
    }

    @Test
    void disposedDocumentSuppressesLifecycleDispatch() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        AtomicInteger calls = new AtomicInteger();
        document.body.addEventListener("load", event -> calls.incrementAndGet());

        invokeLifecycle(document, "disposeLifecycle");
        invokeFireLifecycleEvent(document, "load", false);

        assertEquals(0, calls.get());
        assertTrue(document.isDisposed());
    }

    private static void invokeLifecycle(Document document, String methodName) throws Exception {
        Method method = Document.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(document);
    }

    private static void invokeFireLifecycleEvent(Document document, String type, boolean bubbles) throws Exception {
        Method method = Document.class.getDeclaredMethod("fireLifecycleEvent", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(document, type, bubbles);
    }
}
