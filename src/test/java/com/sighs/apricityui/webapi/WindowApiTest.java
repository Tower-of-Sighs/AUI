package com.sighs.apricityui.webapi;

import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.LocalStorage;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WindowApiTest {
    @Test
    void windowEventListenersDispatchByTypeAndCanBeRemoved() {
        Window window = new Window();
        AtomicInteger resizeCalls = new AtomicInteger();
        AtomicInteger customCalls = new AtomicInteger();

        Consumer<Object> resizeListener = event -> resizeCalls.incrementAndGet();
        Consumer<Object> customListener = event -> customCalls.incrementAndGet();

        window.addEventListener("resize", resizeListener);
        window.addEventListener("custom", customListener, true);

        assertTrue(window.dispatchEvent(new Window.WindowEvent("resize")));
        assertEquals(1, resizeCalls.get());
        assertEquals(0, customCalls.get());

        Event custom = window.createEvent("custom", true);
        assertTrue(window.dispatchEvent(custom));
        assertEquals(1, customCalls.get());

        window.removeEventListener("resize", resizeListener);
        window.removeEventListener("custom", customListener, true);

        assertFalse(window.dispatchEvent(new Window.WindowEvent("resize")));
        assertFalse(window.dispatchEvent(window.createEvent("custom", false)));
    }

    @Test
    void eventFactoriesPopulateBrowserStyleFields() {
        Window window = new Window();

        Event event = window.createEvent("submit", true);
        assertEquals("submit", event.type);
        assertTrue(event.bubbles);
        assertFalse(event.cancelable);

        Event.CustomEvent custom = window.createCustomEvent("ready", List.of("ok"), false);
        assertEquals("ready", custom.type);
        assertEquals(List.of("ok"), custom.detail);
        assertFalse(custom.bubbles);

        Window.WindowMouseEvent mouse = window.createMouseEvent("click", 10, 20, 0);
        assertEquals(10, mouse.clientX);
        assertEquals(20, mouse.clientY);
        assertEquals(10, mouse.pageX);
        assertEquals(20, mouse.pageY);
        assertEquals(0, mouse.button);
        assertTrue(mouse.bubbles);

        Window.WindowWheelEvent wheel = window.createWheelEvent("wheel", 1, 2, 3, 4, 0);
        assertEquals(3, wheel.deltaX);
        assertEquals(4, wheel.deltaY);
        assertEquals(0, wheel.deltaMode);

        Window.WindowPointerEvent pointer = window.createPointerEvent("pointerdown", 5, 6, 0, 7, "pen", false);
        assertEquals(7, pointer.pointerId);
        assertEquals("pen", pointer.pointerType);
        assertFalse(pointer.isPrimary);
    }

    @Test
    void performanceAndResizeEventBehaveLikeWindowUtilities() {
        Window window = new Window();
        double start = window.getPerformance().now();
        double end = window.getPerformance().now();
        assertTrue(end >= start);

        AtomicInteger resizeCalls = new AtomicInteger();
        window.addEventListener("resize", event -> resizeCalls.incrementAndGet());
        window.fireResizeEvent();
        assertEquals(1, resizeCalls.get());
    }

    @Test
    void windowLocationTracksActiveDocumentPath() {
        Document document = TestDocumentFactory.createDocument();
        Document.getAll().add(document);
        try {
            Window window = new Window();
            assertEquals("test://doc", window.getLocation().getHref());
        } finally {
            Document.remove(document.getUuid());
        }
    }

    @Test
    void sessionStorageMatchesWebStorageStyleOperations() {
        Window.SessionStorage storage = new Window.SessionStorage();

        assertNull(storage.getItem("missing"));
        storage.setItem("alpha", "1");
        storage.setItem("beta", null);

        assertEquals("1", storage.getItem("alpha"));
        assertEquals("null", storage.getItem("beta"));
        assertEquals(2, storage.getLength());
        assertEquals("alpha", storage.key(0));
        assertEquals("beta", storage.key(1));
        assertNull(storage.key(2));

        storage.removeItem("alpha");
        assertNull(storage.getItem("alpha"));
        assertEquals(1, storage.getLength());

        storage.clear();
        assertEquals(0, storage.getLength());
    }

    @Test
    void localStorageMatchesWebStorageStyleOperations() {
        LocalStorage storage = new LocalStorage();
        storage.clear();

        assertNull(storage.getItem("missing"));

        storage.setItem("alpha", "1");
        storage.setItem("beta", null);
        assertEquals("1", storage.getItem("alpha"));
        assertEquals("null", storage.getItem("beta"));
        assertEquals(2, storage.getLength());
        assertEquals("alpha", storage.key(0));
        assertEquals("beta", storage.key(1));

        storage.removeItem("alpha");
        assertNull(storage.getItem("alpha"));
        assertEquals(1, storage.getLength());

        storage.clear();
        assertEquals(0, storage.getLength());
    }

    @Test
    void requestAnimationFrameAndFetchPromiseExposeAsyncBrowserLikeBehavior() throws Exception {
        Window window = new Window();

        CountDownLatch frameLatch = new CountDownLatch(1);
        int frameId = window.requestAnimationFrame(timestamp -> {
            assertTrue(timestamp >= 0);
            frameLatch.countDown();
        });
        assertTrue(frameId > 0);
        assertTrue(frameLatch.await(300, TimeUnit.MILLISECONDS));

        CountDownLatch canceledLatch = new CountDownLatch(1);
        int canceledId = window.requestAnimationFrame(timestamp -> canceledLatch.countDown());
        window.cancelAnimationFrame(canceledId);
        assertFalse(canceledLatch.await(80, TimeUnit.MILLISECONDS));

        CountDownLatch fetchLatch = new CountDownLatch(1);
        AtomicReference<Object> fetchError = new AtomicReference<>();
        window.fetch("", "test://doc").catchError(error -> {
            fetchError.set(error);
            fetchLatch.countDown();
        });

        assertTrue(fetchLatch.await(300, TimeUnit.MILLISECONDS));
        assertNotNull(fetchError.get());
    }

    @Test
    void timeoutAndIntervalCanBeCanceledThroughWindowApi() throws Exception {
        Window window = new Window();

        AtomicInteger timeoutCalls = new AtomicInteger();
        Object timeout = window.setTimeout(handle -> timeoutCalls.incrementAndGet(), 30);
        window.clearTimeout(timeout);
        Thread.sleep(80);
        assertEquals(0, timeoutCalls.get());

        AtomicInteger intervalCalls = new AtomicInteger();
        Object interval = window.setInterval(handle -> intervalCalls.incrementAndGet(), 20);
        Thread.sleep(70);
        window.clearInterval(interval);
        int callsAfterCancel = intervalCalls.get();
        Thread.sleep(70);
        assertTrue(callsAfterCancel >= 1);
        assertEquals(callsAfterCancel, intervalCalls.get());
    }

    @Test
    void fetchResponseExposesBrowserStyleBodyReaders() {
        byte[] bytes = "{\"ok\":true,\"count\":2}".getBytes(StandardCharsets.UTF_8);
        Window.FetchResponse response = new Window.FetchResponse("test://data.json", 200, bytes);

        assertTrue(response.isOk());
        assertEquals(200, response.getStatus());
        assertEquals("test://data.json", response.getUrl());
        assertEquals("{\"ok\":true,\"count\":2}", response.text());

        Object json = response.json();
        assertInstanceOf(Map.class, json);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) json;
        assertEquals(Boolean.TRUE, map.get("ok"));
        assertEquals(2.0, map.get("count"));

        byte[] clone = response.bytes();
        clone[0] = 'X';
        assertEquals('{', response.bytes()[0]);
    }

    @Test
    void resizeObserverReportsOnlyActualSizeChangesAndSupportsUnobserve() {
        Document document = TestDocumentFactory.createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);
        setObservedSize(target, 10, 20);

        Window window = new Window();
        AtomicReference<List<Window.ResizeObserverEntry>> observed = new AtomicReference<>();
        AtomicInteger callbackCalls = new AtomicInteger();
        Window.ResizeObserver observer = window.createResizeObserver(entries -> {
            @SuppressWarnings("unchecked")
            List<Window.ResizeObserverEntry> cast = (List<Window.ResizeObserverEntry>) entries;
            observed.set(cast);
            callbackCalls.incrementAndGet();
        });

        observer.observe(target);
        window.tickResizeObservers();
        assertEquals(0, callbackCalls.get());

        setObservedSize(target, 25, 20);
        window.tickResizeObservers();
        assertEquals(1, callbackCalls.get());
        assertNotNull(observed.get());
        assertEquals(1, observed.get().size());
        assertSame(target, observed.get().get(0).target);
        assertEquals(25, observed.get().get(0).contentRect.width);
        assertEquals(20, observed.get().get(0).contentRect.height);

        window.tickResizeObservers();
        assertEquals(1, callbackCalls.get());

        observer.unobserve(target);
        setObservedSize(target, 40, 20);
        window.tickResizeObservers();
        assertEquals(1, callbackCalls.get());

        observer.observe(target);
        observer.disconnect();
        setObservedSize(target, 55, 20);
        window.tickResizeObservers();
        assertEquals(1, callbackCalls.get());
    }

    private static void setObservedSize(Element element, double width, double height) {
        Box box = new Box();
        box.element = element;
        element.getRenderer().box.set(box);
        element.getRenderer().size.set(new Size(width, height));
    }
}
