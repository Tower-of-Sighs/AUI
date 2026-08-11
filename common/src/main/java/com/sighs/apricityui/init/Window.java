package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.canvas.CanvasImageBitmap;
import com.sighs.apricityui.canvas.CanvasImageSupport;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.canvas.OffscreenCanvas;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.task.ClientScheduler;
import com.sighs.apricityui.util.AuiLog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.sighs.apricityui.util.BrowserLocation;
import com.sighs.apricityui.util.LocalStorage;
import com.sighs.apricityui.util.SimpleJsonParser;
import com.sighs.apricityui.util.Storage;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.style.Style;

public class Window {
    public static final Window window = new Window();
    public final LocalStorage localStorage = new LocalStorage();
    public final SessionStorage sessionStorage = new SessionStorage();
    private final Map<String, CopyOnWriteArrayList<Event.ListenerRecord>> listeners = new ConcurrentHashMap<>();
    private final Map<Integer, ClientScheduler.Cancellable> animationFrames = new ConcurrentHashMap<>();
    private final AtomicInteger nextAnimationFrameId = new AtomicInteger(1);
    private final Performance performance = new Performance();
    private final Console console = new Console();
    private final CopyOnWriteArrayList<ResizeObserver> resizeObservers = new CopyOnWriteArrayList<>();

    public ClientScheduler.Cancellable setTimeout(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setTimeout(delay, runnable);
    }

    public ClientScheduler.Cancellable setInterval(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setInterval(delay, runnable);
    }

    public void clearTimeout(Object handle) {
        cancelScheduled(handle);
    }

    public void clearInterval(Object handle) {
        cancelScheduled(handle);
    }

    public OffscreenCanvas createOffscreenCanvas(int width, int height) {
        return new OffscreenCanvas(width, height);
    }

    public DOMMatrix createDOMMatrix() {
        return new DOMMatrix();
    }

    public DOMMatrix createDOMMatrix(Object init) {
        return new DOMMatrix(init);
    }

    public CanvasImageBitmap createImageBitmap(Object source) {
        return new CanvasImageBitmap(CanvasImageSupport.resolveImageSource(source));
    }

    public CanvasImageBitmap createImageBitmap(Object source, int sx, int sy, int sw, int sh) {
        CanvasImageBitmap bitmap = createImageBitmap(source);
        if (bitmap == null || bitmap.isClosed()) return bitmap;
        return bitmap.crop(sx, sy, sw, sh);
    }

    public ImageBitmapPromise createImageBitmapAsync(Object source) {
        return new ImageBitmapPromise(() -> createImageBitmap(source));
    }

    public ImageBitmapPromise createImageBitmapAsync(Object source, int sx, int sy, int sw, int sh) {
        return new ImageBitmapPromise(() -> createImageBitmap(source, sx, sy, sw, sh));
    }

    public double getInnerWidth() {
        return Size.getWindowSize().width();
    }

    public double getInnerHeight() {
        return Size.getWindowSize().height();
    }

    public double getDevicePixelRatio() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return 1.0;
        return minecraft.getWindow().getGuiScale();
    }

    public Performance getPerformance() {
        return performance;
    }

    public LocalStorage getLocalStorage() {
        return localStorage;
    }

    public SessionStorage getSessionStorage() {
        return sessionStorage;
    }

    /** 浏览器标准 window.getSelection() 的 AUI 桥：返回上下文文档的富文本选区。 */
    public com.sighs.apricityui.behavior.richtext.SelectionBridge getSelection() {
        com.sighs.apricityui.init.Document document = com.sighs.apricityui.init.Document.getContextDocument();
        if (document == null) {
            java.util.List<com.sighs.apricityui.init.Document> all = com.sighs.apricityui.init.Document.getAll();
            document = all.isEmpty() ? null : all.get(0);
        }
        return document == null ? null : new com.sighs.apricityui.behavior.richtext.SelectionBridge(document);
    }

    public Console getConsole() {
        return console;
    }

    public String getTestPromptResponse() {
        String propertyValue = System.getProperty("apricityui.test.promptResponse");
        if (propertyValue != null) return propertyValue;
        return System.getenv("APRICITYUI_TEST_PROMPT_RESPONSE");
    }

    public BrowserLocation getLocation() {
        for (Document document : Document.getAll()) {
            if (document != null && document.isActive()) {
                return document.getLocation();
            }
        }
        return new BrowserLocation("");
    }

    public void addEventListener(String type, Consumer<? super Event> listener) {
        addEventListener(type, listener, false);
    }

    public void addEventListener(String type, Consumer<? super Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false);
    }

    public void addEventListener(String type, Consumer<? super Event> listener, boolean useCapture, boolean once) {
        if (type == null || listener == null) return;
        Consumer<Event> wrapped = wrapWindowListener(listener);
        listeners.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(new Event.ListenerRecord(type, wrapped, useCapture, once, false));
    }

    public void removeEventListener(String type, Consumer<? super Event> listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, Consumer<? super Event> listener, boolean useCapture) {
        if (type == null || listener == null) return;
        CopyOnWriteArrayList<Event.ListenerRecord> typeListeners = listeners.get(type);
        if (typeListeners == null) return;
        typeListeners.removeIf(candidate ->
                candidate.useCapture() == useCapture && listener.equals(unwrapWindowListener(candidate.listener())));
    }

    public boolean dispatchEvent(Object event) {
        if (!(event instanceof Event windowEvent)) return false;
        String type = windowEvent.type;
        if (type == null || type.isEmpty()) return false;
        CopyOnWriteArrayList<Event.ListenerRecord> typeListeners = listeners.get(type);
        if (typeListeners == null || typeListeners.isEmpty()) return true;
        windowEvent.resetForDispatch(this);
        boolean consumed = false;

        for (Event.ListenerRecord listener : typeListeners) {
            if (windowEvent.isImmediatePropagationStopped()) break;
            if (listener.useCapture()) {
                consumed |= invokeWindowListener(type, listener, windowEvent, Event.AT_TARGET);
            }
        }

        if (!windowEvent.isImmediatePropagationStopped()) {
            for (Event.ListenerRecord listener : typeListeners) {
                if (windowEvent.isImmediatePropagationStopped()) break;
                if (!listener.useCapture()) {
                    consumed |= invokeWindowListener(type, listener, windowEvent, Event.AT_TARGET);
                }
            }
        }

        windowEvent.eventPhase = Event.NONE;
        windowEvent.currentTarget = null;
        return !windowEvent.defaultPrevented;
    }

    public int requestAnimationFrame(Consumer<Double> callback) {
        if (callback == null) return -1;
        int id = nextAnimationFrameId.getAndIncrement();
        ClientScheduler.Cancellable cancellable = ClientScheduler.setTimeout(16, handle -> {
            animationFrames.remove(id);
            callback.accept(performance.now());
        });
        animationFrames.put(id, cancellable);
        return id;
    }

    public void cancelAnimationFrame(int id) {
        ClientScheduler.Cancellable cancellable = animationFrames.remove(id);
        if (cancellable != null) {
            cancellable.cancel();
        }
    }

    public WindowMouseEvent createMouseEvent(String type, double clientX, double clientY, int button) {
        return new WindowMouseEvent(this, type, clientX, clientY, button);
    }

    public WindowWheelEvent createWheelEvent(String type, double clientX, double clientY, double deltaX, double deltaY, int deltaMode) {
        return new WindowWheelEvent(this, type, clientX, clientY, deltaX, deltaY, deltaMode);
    }

    public WindowPointerEvent createPointerEvent(String type, double clientX, double clientY, int button, int pointerId, String pointerType, boolean isPrimary) {
        return new WindowPointerEvent(this, type, clientX, clientY, button, pointerId, pointerType, isPrimary);
    }

    public Event createEvent(String type, boolean bubbles) {
        return new Event(this, type, bubbles);
    }

    public Event.CustomEvent createCustomEvent(String type, Object detail, boolean bubbles) {
        Event.CustomEvent event = new Event.CustomEvent(type, detail, bubbles);
        event.target = this;
        event.currentTarget = this;
        return event;
    }

    public CSSStyleDeclaration getComputedStyle(Element element) {
        return new CSSStyleDeclaration(element == null ? null : element.getComputedStyle());
    }

    public FetchPromise fetch(String url, String contextPath) {
        return new FetchPromise(url, contextPath);
    }

    public ResizeObserver createResizeObserver(Consumer<Object> callback) {
        ResizeObserver observer = new ResizeObserver(callback, this);
        resizeObservers.add(observer);
        return observer;
    }

    public void fireResizeEvent() {
        Event event = createEvent("resize", false);
        event.setTrusted(true);
        dispatchEvent(event);
    }

    private void cancelScheduled(Object handle) {
        if (handle instanceof ClientScheduler.Cancellable cancellable) {
            cancellable.cancel();
        }
    }

    public void tickResizeObservers() {
        for (ResizeObserver observer : resizeObservers) {
            if (observer == null) continue;
            observer.tick();
            if (observer.disconnected) {
                resizeObservers.remove(observer);
            }
        }
    }

    private static boolean invokeWindowListener(String type, Event.ListenerRecord listener, Event event, short phase) {
        if (listener == null || event == null) return false;
        event.currentTarget = event.target;
        event.eventPhase = phase;
        listener.listener().accept(event);
        if (listener.once() && event.target instanceof Window window) {
            Consumer<? super Event> original = unwrapWindowListener(listener.listener());
            if (original != null) {
                window.removeEventListener(type, original, listener.useCapture());
            } else {
                CopyOnWriteArrayList<Event.ListenerRecord> typeListeners = window.listeners.get(type);
                if (typeListeners != null) typeListeners.remove(listener);
            }
        }
        return !listener.internal();
    }

    private static Consumer<Event> wrapWindowListener(Consumer<? super Event> listener) {
        return new WindowListenerAdapter(listener);
    }

    private static Consumer<? super Event> unwrapWindowListener(Consumer<Event> listener) {
        if (listener instanceof WindowListenerAdapter adapter) {
            return adapter.delegate;
        }
        return null;
    }

    private static final class WindowListenerAdapter implements Consumer<Event> {
        private final Consumer<? super Event> delegate;

        private WindowListenerAdapter(Consumer<? super Event> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(Event event) {
            delegate.accept(event);
        }
    }

    public static class Performance {
        public double now() {
            return System.nanoTime() / 1_000_000.0;
        }
    }

    public static class WindowMouseEvent extends Event {
        public final double clientX;
        public final double clientY;
        public final double pageX;
        public final double pageY;
        public final int button;

        public WindowMouseEvent(Window target, String type, double clientX, double clientY, int button) {
            super(target, type, true);
            this.clientX = clientX;
            this.clientY = clientY;
            this.pageX = clientX;
            this.pageY = clientY;
            this.button = button;
        }
    }

    public static class WindowWheelEvent extends WindowMouseEvent {
        public final double deltaX;
        public final double deltaY;
        public final int deltaMode;

        public WindowWheelEvent(Window target, String type, double clientX, double clientY, double deltaX, double deltaY, int deltaMode) {
            super(target, type, clientX, clientY, -1);
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.deltaMode = deltaMode;
        }
    }

    public static class WindowPointerEvent extends WindowMouseEvent {
        public final int pointerId;
        public final String pointerType;
        public final boolean isPrimary;

        public WindowPointerEvent(Window target, String type, double clientX, double clientY, int button, int pointerId, String pointerType, boolean isPrimary) {
            super(target, type, clientX, clientY, button);
            this.pointerId = pointerId;
            this.pointerType = pointerType == null || pointerType.isBlank() ? "mouse" : pointerType;
            this.isPrimary = isPrimary;
        }
    }

    public static class CSSStyleDeclaration {
        private final Style style;

        public CSSStyleDeclaration(Style style) {
            this.style = style;
        }

        public String getPropertyValue(String name) {
            if (style == null) return "";
            String value = style.get(name);
            return value == null ? "" : value;
        }

        public String get(String name) {
            return getPropertyValue(name);
        }

        public String getFontSize() {
            return getPropertyValue("font-size");
        }

        public String getFontWeight() {
            return getPropertyValue("font-weight");
        }

        public String getLetterSpacing() {
            return getPropertyValue("letter-spacing");
        }

        public String getFontFamily() {
            return getPropertyValue("font-family");
        }

        public String getLineHeight() {
            return getPropertyValue("line-height");
        }

        public String getDisplay() {
            return getPropertyValue("display");
        }

        public String getColor() {
            return getPropertyValue("color");
        }
    }

    public static class FetchPromise {
        private final CompletableFuture<FetchResponse> future;

        public FetchPromise(String url, String contextPath) {
            this.future = CompletableFuture.supplyAsync(() -> {
                try {
                    return loadResponse(url, contextPath);
                } catch (IOException exception) {
                    ApricityUI.LOGGER.error(
                            "[AUI Fetch] request failed url={} context={}",
                            url,
                            contextPath,
                            exception
                    );
                    throw new RuntimeException(exception);
                }
            });
        }

        public FetchPromise then(Consumer<FetchResponse> onFulfilled) {
            if (onFulfilled != null) {
                future.thenAccept(onFulfilled);
            }
            return this;
        }

        public FetchPromise then(Consumer<FetchResponse> onFulfilled, Consumer<Object> onRejected) {
            future.whenComplete((response, throwable) -> {
                if (throwable == null) {
                    if (onFulfilled != null) {
                        onFulfilled.accept(response);
                    }
                    return;
                }
                if (onRejected != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                }
            });
            return this;
        }

        public FetchPromise catchError(Consumer<Object> onRejected) {
            if (onRejected != null) {
                future.exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                    return null;
                });
            }
            return this;
        }

        private static FetchResponse loadResponse(String rawUrl, String contextPath) throws IOException {
            if (rawUrl == null || rawUrl.isBlank()) {
                ApricityUI.LOGGER.error("[AUI Fetch] fetch URL is blank context={}", contextPath);
                throw new IOException("fetch url is blank");
            }
            String resolved = Loader.resolve(contextPath == null ? "" : contextPath, rawUrl);
            byte[] bytes;
            if (Loader.isRemotePath(resolved)) {
                bytes = NetworkAsyncHandler.INSTANCE.fetchBytes(resolved);
            } else {
                try (InputStream stream = ClientLoader.getResourceStream(resolved)) {
                    if (stream == null) {
                        ApricityUI.LOGGER.error("[AUI Fetch] local resource is missing resolved={}", resolved);
                        throw new IOException("resource not found: " + resolved);
                    }
                    bytes = stream.readAllBytes();
                }
            }
            return new FetchResponse(resolved, 200, bytes);
        }
    }

    public static class ImageBitmapPromise {
        private final CompletableFuture<CanvasImageBitmap> future;

        public ImageBitmapPromise(java.util.concurrent.Callable<CanvasImageBitmap> task) {
            this.future = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception exception) {
                    ApricityUI.LOGGER.error("[AUI Canvas] image bitmap task failed", exception);
                    throw new RuntimeException(exception);
                }
            });
        }

        public ImageBitmapPromise then(Consumer<CanvasImageBitmap> onFulfilled) {
            if (onFulfilled != null) {
                future.thenAccept(onFulfilled);
            }
            return this;
        }

        public ImageBitmapPromise then(Consumer<CanvasImageBitmap> onFulfilled, Consumer<Object> onRejected) {
            future.whenComplete((bitmap, throwable) -> {
                if (throwable == null) {
                    if (onFulfilled != null) onFulfilled.accept(bitmap);
                    return;
                }
                if (onRejected != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                }
            });
            return this;
        }

        public ImageBitmapPromise catchError(Consumer<Object> onRejected) {
            if (onRejected != null) {
                future.exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                    return null;
                });
            }
            return this;
        }
    }

    public static class FetchResponse {
        private final String url;
        private final int status;
        private final byte[] bytes;

        public FetchResponse(String url, int status, byte[] bytes) {
            this.url = url;
            this.status = status;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        public boolean isOk() {
            return status >= 200 && status < 300;
        }

        public int getStatus() {
            return status;
        }

        public String getUrl() {
            return url;
        }

        public String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public Object json() {
            try {
                return new SimpleJsonParser(text()).parse();
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error("[AUI Fetch] JSON parse failed url={} body={}", url, AuiLog.compact(text()), exception);
                throw exception;
            }
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static class SessionStorage extends Storage {
    }

    public static class Console {
        private final Map<String, Long> timers = new ConcurrentHashMap<>();

        public void log(Object value) {
            ApricityUI.LOGGER.info(String.valueOf(value));
        }

        public void warn(Object value) {
            ApricityUI.LOGGER.warn(String.valueOf(value));
        }

        public void error(Object value) {
            ApricityUI.LOGGER.error(String.valueOf(value));
        }

        public void time(String label) {
            String key = label == null ? "default" : label;
            timers.put(key, System.nanoTime());
        }

        public void timeEnd(String label) {
            String key = label == null ? "default" : label;
            Long started = timers.remove(key);
            if (started == null) return;
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            ApricityUI.LOGGER.info("{}: {}ms", key, String.format(java.util.Locale.ROOT, "%.3f", elapsedMs));
        }
    }

    public static class ResizeObserver {
        private final Consumer<Object> callback;
        private final Window owner;
        private final Map<Element, SizeSnapshot> observed = new ConcurrentHashMap<>();
        private volatile boolean disconnected = false;

        public ResizeObserver(Consumer<Object> callback, Window owner) {
            this.callback = callback;
            this.owner = owner;
        }

        public void observe(Element element) {
            if (element == null || disconnected) return;
            observed.put(element, SizeSnapshot.capture(element));
        }

        public void unobserve(Element element) {
            if (element == null) return;
            observed.remove(element);
        }

        public void disconnect() {
            disconnected = true;
            observed.clear();
            owner.resizeObservers.remove(this);
        }

        private void tick() {
            if (disconnected || callback == null || observed.isEmpty()) return;
            ArrayList<ResizeObserverEntry> entries = new ArrayList<>();
            for (Map.Entry<Element, SizeSnapshot> entry : observed.entrySet()) {
                Element element = entry.getKey();
                if (element == null || element.document == null || !element.document.isActive()) continue;
                SizeSnapshot previous = entry.getValue();
                SizeSnapshot current = SizeSnapshot.capture(element);
                if (!current.sameAs(previous)) {
                    observed.put(element, current);
                    entries.add(new ResizeObserverEntry(element, new ResizeObserverRect(
                            current.contentWidth,
                            current.contentHeight,
                            current.borderWidth,
                            current.borderHeight
                    )));
                }
            }
            if (!entries.isEmpty()) {
                callback.accept(entries);
            }
        }
    }

    public static class ResizeObserverEntry {
        public final Element target;
        public final ResizeObserverRect contentRect;

        public ResizeObserverEntry(Element target, ResizeObserverRect contentRect) {
            this.target = target;
            this.contentRect = contentRect;
        }
    }

    public static class ResizeObserverRect {
        public final double x = 0;
        public final double y = 0;
        public final double left = 0;
        public final double top = 0;
        public final double width;
        public final double height;
        public final double right;
        public final double bottom;
        public final double borderBoxWidth;
        public final double borderBoxHeight;

        public ResizeObserverRect(double width, double height, double borderBoxWidth, double borderBoxHeight) {
            this.width = width;
            this.height = height;
            this.right = width;
            this.bottom = height;
            this.borderBoxWidth = borderBoxWidth;
            this.borderBoxHeight = borderBoxHeight;
        }
    }

    private record SizeSnapshot(double contentWidth, double contentHeight, double borderWidth, double borderHeight) {
        private static SizeSnapshot capture(Element element) {
            Box box = Box.of(element);
            return new SizeSnapshot(
                    box.innerSize().width(),
                    box.innerSize().height(),
                    box.elementSize().width(),
                    box.elementSize().height()
            );
        }

        private boolean sameAs(SizeSnapshot other) {
            if (other == null) return false;
            return Math.abs(contentWidth - other.contentWidth) < 0.0001
                    && Math.abs(contentHeight - other.contentHeight) < 0.0001
                    && Math.abs(borderWidth - other.borderWidth) < 0.0001
                    && Math.abs(borderHeight - other.borderHeight) < 0.0001;
        }
    }
}
