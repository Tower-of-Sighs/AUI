package com.sighs.apricityui.webapi;

import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.util.LocalStorage;
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

        Event resizeEvent = window.createEvent("resize", false);
        assertTrue(window.dispatchEvent(resizeEvent));
        assertEquals(1, resizeCalls.get());
        assertEquals(0, customCalls.get());
        assertSame(window, resizeEvent.target);
        assertNull(resizeEvent.currentTarget);

        Event custom = window.createEvent("custom", true);
        assertTrue(window.dispatchEvent(custom));
        assertEquals(1, customCalls.get());

        window.removeEventListener("resize", resizeListener);
        window.removeEventListener("custom", customListener, true);

        assertTrue(window.dispatchEvent(window.createEvent("resize", false)));
        assertTrue(window.dispatchEvent(window.createEvent("custom", false)));
    }

    @Test
    void eventFactoriesPopulateBrowserStyleFields() {
        Window window = new Window();

        Event event = window.createEvent("submit", true);
        assertEquals("submit", event.type);
        assertTrue(event.bubbles);
        assertFalse(event.cancelable);
        assertFalse(event.isTrusted);
        assertTrue(event.timeStamp > 0);

        Event.CustomEvent custom = window.createCustomEvent("ready", List.of("ok"), false);
        assertEquals("ready", custom.type);
        assertEquals(List.of("ok"), custom.detail);
        assertFalse(custom.bubbles);
        assertFalse(custom.isTrusted);

        Window.WindowMouseEvent mouse = window.createMouseEvent("click", 10, 20, 0);
        assertEquals(10, mouse.clientX);
        assertEquals(20, mouse.clientY);
        assertEquals(10, mouse.pageX);
        assertEquals(20, mouse.pageY);
        assertEquals(0, mouse.button);
        assertTrue(mouse.bubbles);
        assertSame(window, mouse.target);
        assertFalse(mouse.isTrusted);

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
    void windowEventSystemSupportsPhaseOnceAndImmediateStop() {
        Window window = new Window();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger onceCalls = new AtomicInteger();
        AtomicReference<Event> seen = new AtomicReference<>();

        window.addEventListener("custom", event -> {
            Event typed = (Event) event;
            seen.set(typed);
            calls.incrementAndGet();
            assertSame(window, typed.target);
            assertSame(window, typed.currentTarget);
            assertEquals(Event.AT_TARGET, typed.eventPhase);
            typed.stopImmediatePropagation();
        }, true);
        window.addEventListener("custom", event -> calls.incrementAndGet());
        window.addEventListener("custom", event -> onceCalls.incrementAndGet(), false, true);

        Event first = window.createEvent("custom", true);
        assertTrue(window.dispatchEvent(first));
        assertEquals(1, calls.get());
        assertEquals(0, onceCalls.get());
        assertNotNull(seen.get());
        assertEquals(Event.NONE, first.eventPhase);
        assertNull(first.currentTarget);

        Event second = window.createEvent("custom", true);
        assertTrue(window.dispatchEvent(second));
        assertEquals(2, calls.get());
        assertEquals(0, onceCalls.get());
    }

    @Test
    void windowEventReflectsReturnValueAndCancelBubble() {
        Window window = new Window();
        AtomicInteger calls = new AtomicInteger();

        window.addEventListener("custom", event -> {
            Event typed = (Event) event;
            calls.incrementAndGet();
            assertTrue(typed.returnValue);
            assertFalse(typed.cancelBubble);
            typed.preventDefault();
            typed.stopPropagation();
            assertFalse(typed.returnValue);
            assertTrue(typed.cancelBubble);
        });

        Event event = window.createEvent("custom", true);
        event.cancelable = true;
        assertFalse(window.dispatchEvent(event));
        assertEquals(1, calls.get());
        assertFalse(event.returnValue);
        assertTrue(event.cancelBubble);
    }

    @Test
    void windowOnceListenerRunsOnlyOnceWhenNotStopped() {
        Window window = new Window();
        AtomicInteger onceCalls = new AtomicInteger();

        window.addEventListener("custom", event -> onceCalls.incrementAndGet(), false, true);

        assertTrue(window.dispatchEvent(window.createEvent("custom", true)));
        assertTrue(window.dispatchEvent(window.createEvent("custom", true)));
        assertEquals(1, onceCalls.get());
    }

    @Test
    void performanceAndResizeEventBehaveLikeWindowUtilities() {
        Window window = new Window();
        double start = window.getPerformance().now();
        double end = window.getPerformance().now();
        assertTrue(end >= start);

        AtomicInteger resizeCalls = new AtomicInteger();
        window.addEventListener("resize", event -> {
            Event typed = (Event) event;
            resizeCalls.incrementAndGet();
            assertTrue(typed.isTrusted);
        });
        window.fireResizeEvent();
        assertEquals(1, resizeCalls.get());
    }

    @Test
    void windowDispatchEventReturnsFalseOnlyWhenPreventDefaultCancels() {
        Window window = new Window();
        Consumer<Object> listener = event -> ((Event) event).preventDefault();
        window.addEventListener("submit", listener);

        Event canceled = window.createEvent("submit", true);
        canceled.cancelable = true;
        assertFalse(window.dispatchEvent(canceled));

        Event notCancelable = window.createEvent("submit", true);
        assertTrue(window.dispatchEvent(notCancelable));

        window.removeEventListener("submit", listener);
        assertTrue(window.dispatchEvent(window.createEvent("missing", false)));
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
        // rAF is a 16ms timer; a generous timeout absorbs scheduler latency under load.
        assertTrue(frameLatch.await(2, TimeUnit.SECONDS));

        CountDownLatch canceledLatch = new CountDownLatch(1);
        int canceledId = window.requestAnimationFrame(timestamp -> canceledLatch.countDown());
        window.cancelAnimationFrame(canceledId);
        // Observe across several rAF periods: a canceled frame must not fire.
        assertFalse(canceledLatch.await(200, TimeUnit.MILLISECONDS));

        CountDownLatch fetchLatch = new CountDownLatch(1);
        AtomicReference<Object> fetchError = new AtomicReference<>();
        window.fetch("", "test://doc").catchError(error -> {
            fetchError.set(error);
            fetchLatch.countDown();
        });

        assertTrue(fetchLatch.await(2, TimeUnit.SECONDS));
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
        // Wait (poll) until the interval has fired at least once, so the cancel is not
        // racing the first tick on a loaded machine.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (intervalCalls.get() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(intervalCalls.get() >= 1);
        window.clearInterval(interval);
        // Let any in-flight callback drain, then verify the count stays frozen: a canceled
        // interval must not keep firing.
        Thread.sleep(150);
        int baseline = intervalCalls.get();
        Thread.sleep(150);
        assertEquals(baseline, intervalCalls.get());
    }

    @Test
    void fetchResponseExposesBrowserStyleBodyReaders() {
        byte[] bytes = "{\"ok\":true,\"count\":2}".getBytes(StandardCharsets.UTF_8);
        Window.FetchResponse response = new Window.FetchResponse("test://data.json", 200, bytes);

        assertTrue(response.isOk());
        assertEquals(200, response.status());
        assertEquals("test://data.json", response.url());
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
        assertSame(target, observed.get().get(0).target());
        assertEquals(25, observed.get().get(0).contentRect().width);
        assertEquals(20, observed.get().get(0).contentRect().height);

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
