package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.canvas.CanvasImageBitmap;
import com.sighs.apricityui.canvas.CanvasImageSupport;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.canvas.OffscreenCanvas;
import com.sighs.apricityui.render.CommittedGeometry;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.task.ClientScheduler;
import com.sighs.apricityui.util.AuiLog;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final CopyOnWriteArrayList<IntersectionObserver> intersectionObservers = new CopyOnWriteArrayList<>();
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

    /**
     * `new Audio(src)` 的 Java 侧工厂：创建挂到 context document 但不入 DOM 树的
     * Audio 元素（注册进 AudioEngine，随文档关闭自动停止；事件/addEventListener 走常规元素链路）。
     */
    public com.sighs.apricityui.element.Audio createAudio(Document document, String src) {
        com.sighs.apricityui.element.Audio audio = new com.sighs.apricityui.element.Audio(document);
        if (src != null && !src.isEmpty()) audio.setAttribute("src", src);
        return audio;
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

    public IntersectionObserver createIntersectionObserver(Consumer<Object> callback, Element root, String rootMargin, String thresholds) {
        Document ownerDocument = Document.getContextDocument();
        if (root != null) {
            if (ownerDocument != null && root.document != ownerDocument) {
                throw new IllegalArgumentException("IntersectionObserver root must belong to the current document");
            }
            ownerDocument = root.document;
        }
        if (ownerDocument == null || !ownerDocument.isActive()) {
            throw new IllegalStateException("IntersectionObserver must be created from an active document");
        }
        IntersectionObserver observer = new IntersectionObserver(
                callback,
                this,
                ownerDocument,
                root,
                IntersectionOptions.parse(rootMargin, thresholds)
        );
        registerIntersectionObserver(observer);
        return observer;
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

    /** Removes observers owned by a document before its DOM is rebuilt or disposed. */
    public void clearIntersectionObservers(Document document) {
        if (document == null) return;
        for (IntersectionObserver observer : intersectionObservers) {
            if (observer != null && observer.owns(document)) {
                observer.disposeForDocument();
            }
        }
    }

    private void cancelScheduled(Object handle) {
        if (handle instanceof ClientScheduler.Cancellable cancellable) {
            cancellable.cancel();
        }
    }

    /** Evaluates every observer before invoking any callback from this frame. */
    public void tickIntersectionObservers() {
        double time = performance.now();
        ArrayList<IntersectionDelivery> deliveries = new ArrayList<>();
        for (IntersectionObserver observer : intersectionObservers) {
            if (observer == null) continue;
            List<IntersectionObserverEntry> entries = observer.collectEntries(time);
            if (!entries.isEmpty()) {
                deliveries.add(new IntersectionDelivery(
                        observer.callback,
                        observer.document,
                        observer.documentGeneration,
                        entries
                ));
            }
        }
        for (IntersectionDelivery delivery : deliveries) {
            if (delivery.callback != null
                    && delivery.document != null
                    && delivery.document.isCurrentGeneration(delivery.documentGeneration)) {
                Document.runWithContext(delivery.document, () -> delivery.callback.accept(delivery.entries));
            }
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

    private void registerIntersectionObserver(IntersectionObserver observer) {
        if (observer != null && !intersectionObservers.contains(observer)) {
            intersectionObservers.add(observer);
        }
    }

    private void unregisterIntersectionObserver(IntersectionObserver observer) {
        if (observer != null) {
            intersectionObservers.remove(observer);
        }
    }

    private record IntersectionDelivery(Consumer<Object> callback,
                                        Document document,
                                        long documentGeneration,
                                        List<IntersectionObserverEntry> entries) {
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

    /** Immutable axis-aligned rectangle used by the internal observer state machine. */
    public record IntersectionRect(double x, double y, double width, double height) {
        public static final IntersectionRect ZERO = new IntersectionRect(0.0d, 0.0d, 0.0d, 0.0d);

        public IntersectionRect {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(width, "width");
            requireFinite(height, "height");
            if (width < 0.0d || height < 0.0d) {
                throw new IllegalArgumentException("Rectangle width and height must not be negative");
            }
        }

        public double right() {
            return x + width;
        }

        public double bottom() {
            return y + height;
        }

        public boolean hasArea() {
            return width > 0.0d && height > 0.0d;
        }

        public double area() {
            return hasArea() ? width * height : 0.0d;
        }

        public IntersectionRect expand(double top, double right, double bottom, double left) {
            requireFinite(top, "top");
            requireFinite(right, "right");
            requireFinite(bottom, "bottom");
            requireFinite(left, "left");
            return new IntersectionRect(
                    x - left,
                    y - top,
                    Math.max(0.0d, width + left + right),
                    Math.max(0.0d, height + top + bottom)
            );
        }

        /**
         * Calculates a closed-rectangle intersection. Shared edges and points
         * remain intersections with their original coordinate.
         */
        public static Intersection intersect(IntersectionRect first, IntersectionRect second) {
            if (first == null || second == null) return Intersection.NONE;
            double left = Math.max(first.x, second.x);
            double top = Math.max(first.y, second.y);
            double right = Math.min(first.right(), second.right());
            double bottom = Math.min(first.bottom(), second.bottom());
            if (left > right || top > bottom) return Intersection.NONE;
            return new Intersection(true, new IntersectionRect(left, top, right - left, bottom - top));
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }

        public record Intersection(boolean intersects, IntersectionRect rect) {
            private static final Intersection NONE = new Intersection(false, ZERO);

            public Intersection {
                rect = Objects.requireNonNull(rect, "rect");
            }
        }
    }

    /** Normalized root-margin and threshold configuration for an observer. */
    public static final class IntersectionOptions {
        private static final Pattern MARGIN_TOKEN = Pattern.compile(
                "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(px|%)",
                Pattern.CASE_INSENSITIVE
        );
        private static final RootMargin ZERO_MARGIN = new RootMargin(
                new MarginValue(0.0d, Unit.PX),
                new MarginValue(0.0d, Unit.PX),
                new MarginValue(0.0d, Unit.PX),
                new MarginValue(0.0d, Unit.PX)
        );
        private static final IntersectionOptions DEFAULTS = new IntersectionOptions(ZERO_MARGIN, List.of(0.0d));

        private final RootMargin rootMargin;
        private final List<Double> thresholds;

        public IntersectionOptions(String rootMargin, Collection<? extends Number> thresholds) {
            this(parseRootMargin(rootMargin), normalizeThresholds(thresholds));
        }

        private IntersectionOptions(RootMargin rootMargin, List<Double> thresholds) {
            this.rootMargin = rootMargin;
            this.thresholds = thresholds;
        }

        public static IntersectionOptions defaults() {
            return DEFAULTS;
        }

        /** Parses the scalar arguments supplied by the JavaScript bridge. */
        public static IntersectionOptions parse(String rootMargin, String thresholdValues) {
            return new IntersectionOptions(parseRootMargin(rootMargin), parseThresholds(thresholdValues));
        }

        /** Returns the four-value canonical root-margin string. */
        public String rootMargin() {
            return rootMargin.toString();
        }

        public List<Double> thresholds() {
            return thresholds;
        }

        /** Applies root margin; percentages on every side resolve against root width. */
        public IntersectionRect expandRootBounds(IntersectionRect rootBounds) {
            return rootBounds == null ? null : rootMargin.expand(rootBounds);
        }

        private static RootMargin parseRootMargin(String source) {
            if (source == null) return ZERO_MARGIN;
            String trimmed = source.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("rootMargin must contain one to four px or % values");
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length > 4) {
                throw new IllegalArgumentException("rootMargin must contain one to four values");
            }
            ArrayList<MarginValue> values = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                values.add(parseMarginValue(token));
            }
            return switch (values.size()) {
                case 1 -> new RootMargin(values.get(0), values.get(0), values.get(0), values.get(0));
                case 2 -> new RootMargin(values.get(0), values.get(1), values.get(0), values.get(1));
                case 3 -> new RootMargin(values.get(0), values.get(1), values.get(2), values.get(1));
                case 4 -> new RootMargin(values.get(0), values.get(1), values.get(2), values.get(3));
                default -> throw new IllegalArgumentException("rootMargin must contain one to four values");
            };
        }

        private static MarginValue parseMarginValue(String token) {
            Matcher matcher = MARGIN_TOKEN.matcher(token == null ? "" : token.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported rootMargin value: " + token);
            }
            double value;
            try {
                value = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid rootMargin value: " + token, exception);
            }
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("rootMargin values must be finite");
            }
            Unit unit = "%".equals(matcher.group(2).toLowerCase(java.util.Locale.ROOT)) ? Unit.PERCENT : Unit.PX;
            return new MarginValue(value == 0.0d ? 0.0d : value, unit);
        }

        private static List<Double> parseThresholds(String source) {
            if (source == null || source.isBlank()) return List.of(0.0d);
            String[] tokens = source.split(",", -1);
            ArrayList<Double> values = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                if (token.isBlank()) {
                    throw new IllegalArgumentException("threshold values must be numbers from 0 to 1");
                }
                try {
                    values.add(Double.parseDouble(token.trim()));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid threshold value: " + token, exception);
                }
            }
            return normalizeThresholds(values);
        }

        private static List<Double> normalizeThresholds(Collection<? extends Number> source) {
            if (source == null || source.isEmpty()) return List.of(0.0d);
            NavigableSet<Double> normalized = new TreeSet<>();
            for (Number number : source) {
                if (number == null) {
                    throw new IllegalArgumentException("threshold values must not be null");
                }
                double value = number.doubleValue();
                if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
                    throw new IllegalArgumentException("threshold values must be finite numbers from 0 to 1");
                }
                normalized.add(value == 0.0d ? 0.0d : value);
            }
            return List.copyOf(normalized);
        }

        private record RootMargin(MarginValue top, MarginValue right, MarginValue bottom, MarginValue left) {
            private IntersectionRect expand(IntersectionRect bounds) {
                double rootWidth = bounds.width();
                return bounds.expand(
                        top.resolve(rootWidth),
                        right.resolve(rootWidth),
                        bottom.resolve(rootWidth),
                        left.resolve(rootWidth)
                );
            }

            @Override
            public String toString() {
                return top + " " + right + " " + bottom + " " + left;
            }
        }

        private record MarginValue(double value, Unit unit) {
            private double resolve(double rootWidth) {
                return unit == Unit.PERCENT ? rootWidth * value / 100.0d : value;
            }

            @Override
            public String toString() {
                return format(value) + unit.suffix;
            }
        }

        private enum Unit {
            PX("px"),
            PERCENT("%");

            private final String suffix;

            Unit(String suffix) {
                this.suffix = suffix;
            }
        }

        private static String format(double value) {
            return BigDecimal.valueOf(value == 0.0d ? 0.0d : value).stripTrailingZeros().toPlainString();
        }
    }

    /** Immutable committed geometry supplied to the internal observer engine. */
    public record IntersectionSnapshot<T>(
            T target,
            double time,
            IntersectionRect rootBounds,
            IntersectionRect boundingClientRect,
            List<IntersectionRect> clipBounds,
            boolean eligible
    ) {
        public IntersectionSnapshot(T target,
                                    double time,
                                    IntersectionRect rootBounds,
                                    IntersectionRect boundingClientRect,
                                    List<IntersectionRect> clipBounds) {
            this(target, time, rootBounds, boundingClientRect, clipBounds, true);
        }

        public IntersectionSnapshot {
            target = Objects.requireNonNull(target, "target");
            if (!Double.isFinite(time)) time = 0.0d;
            boundingClientRect = Objects.requireNonNull(boundingClientRect, "boundingClientRect");
            if (clipBounds == null || clipBounds.isEmpty()) {
                clipBounds = List.of();
            } else {
                ArrayList<IntersectionRect> copied = new ArrayList<>(clipBounds.size());
                for (IntersectionRect clip : clipBounds) {
                    copied.add(Objects.requireNonNull(clip, "clipBounds must not contain null"));
                }
                clipBounds = List.copyOf(copied);
            }
        }
    }

    /** Immutable observer entry produced by {@link IntersectionObserverEngine}. */
    public record IntersectionEntryData<T>(
            T target,
            double time,
            IntersectionRect rootBounds,
            IntersectionRect boundingClientRect,
            IntersectionRect intersectionRect,
            boolean isIntersecting,
            double intersectionRatio
    ) {
        public IntersectionEntryData {
            target = Objects.requireNonNull(target, "target");
            if (!Double.isFinite(time)) time = 0.0d;
            boundingClientRect = Objects.requireNonNull(boundingClientRect, "boundingClientRect");
            intersectionRect = Objects.requireNonNull(intersectionRect, "intersectionRect");
            if (!Double.isFinite(intersectionRatio) || intersectionRatio < 0.0d || intersectionRatio > 1.0d) {
                throw new IllegalArgumentException("intersectionRatio must be a finite number from 0 to 1");
            }
        }
    }

    /** Stateful, runtime-neutral observer state machine used by {@link IntersectionObserver}. */
    public static final class IntersectionObserverEngine<T> {
        private final IntersectionOptions options;
        private final IdentityHashMap<T, TargetState> observed = new IdentityHashMap<>();
        private final ArrayList<T> observationOrder = new ArrayList<>();
        private final ArrayList<IntersectionEntryData<T>> records = new ArrayList<>();

        public IntersectionObserverEngine(IntersectionOptions options) {
            this.options = options == null ? IntersectionOptions.defaults() : options;
        }

        public IntersectionOptions options() {
            return options;
        }

        public synchronized void observe(T target) {
            if (target == null || observed.containsKey(target)) return;
            observed.put(target, new TargetState());
            observationOrder.add(target);
        }

        public synchronized void unobserve(T target) {
            if (target == null || observed.remove(target) == null) return;
            removeIdentity(observationOrder, target);
        }

        /** Clears registrations without discarding queued records or disabling future observation. */
        public synchronized void disconnect() {
            observed.clear();
            observationOrder.clear();
        }

        public synchronized boolean isObserved(T target) {
            return target != null && observed.containsKey(target);
        }

        public synchronized List<T> observedTargets() {
            return List.copyOf(observationOrder);
        }

        /** Evaluates all targets in deterministic observe order. */
        public synchronized void evaluate(Function<? super T, ? extends IntersectionSnapshot<T>> snapshots) {
            Objects.requireNonNull(snapshots, "snapshots");
            for (T target : List.copyOf(observationOrder)) {
                IntersectionSnapshot<T> snapshot = snapshots.apply(target);
                if (snapshot != null) evaluateSnapshot(snapshot);
            }
        }

        public synchronized void evaluate(IntersectionSnapshot<T> snapshot) {
            if (snapshot != null) evaluateSnapshot(snapshot);
        }

        /** Atomically returns and clears pending entries. */
        public synchronized List<IntersectionEntryData<T>> takeRecords() {
            if (records.isEmpty()) return List.of();
            List<IntersectionEntryData<T>> pending = List.copyOf(records);
            records.clear();
            return pending;
        }

        private void evaluateSnapshot(IntersectionSnapshot<T> snapshot) {
            TargetState state = observed.get(snapshot.target());
            if (state == null) return;

            Evaluation evaluation = evaluateGeometry(snapshot);
            int thresholdIndex = thresholdIndex(evaluation.ratio);
            if (!state.initialized
                    || state.isIntersecting != evaluation.isIntersecting
                    || state.thresholdIndex != thresholdIndex) {
                records.add(new IntersectionEntryData<>(
                        snapshot.target(),
                        snapshot.time(),
                        evaluation.rootBounds,
                        snapshot.boundingClientRect(),
                        evaluation.intersectionRect,
                        evaluation.isIntersecting,
                        evaluation.ratio
                ));
            }
            state.initialized = true;
            state.isIntersecting = evaluation.isIntersecting;
            state.thresholdIndex = thresholdIndex;
        }

        private Evaluation evaluateGeometry(IntersectionSnapshot<T> snapshot) {
            IntersectionRect rawRootBounds = snapshot.rootBounds();
            if (rawRootBounds == null) {
                return new Evaluation(null, IntersectionRect.ZERO, false, 0.0d);
            }
            IntersectionRect rootBounds = options.expandRootBounds(rawRootBounds);
            if (!snapshot.eligible()) {
                return new Evaluation(rootBounds, IntersectionRect.ZERO, false, 0.0d);
            }

            IntersectionRect.Intersection intersection = new IntersectionRect.Intersection(
                    true,
                    snapshot.boundingClientRect()
            );
            for (IntersectionRect clip : snapshot.clipBounds()) {
                intersection = IntersectionRect.intersect(intersection.rect(), clip);
                if (!intersection.intersects()) {
                    return new Evaluation(rootBounds, IntersectionRect.ZERO, false, 0.0d);
                }
            }
            intersection = IntersectionRect.intersect(intersection.rect(), rootBounds);
            boolean isIntersecting = intersection.intersects();
            IntersectionRect intersectionRect = isIntersecting ? intersection.rect() : IntersectionRect.ZERO;
            double targetArea = snapshot.boundingClientRect().area();
            double ratio = targetArea == 0.0d ? (isIntersecting ? 1.0d : 0.0d)
                    : clampRatio(intersectionRect.area() / targetArea);
            return new Evaluation(rootBounds, intersectionRect, isIntersecting, ratio);
        }

        private int thresholdIndex(double ratio) {
            List<Double> thresholds = options.thresholds();
            int index = 0;
            while (index < thresholds.size() && thresholds.get(index) <= ratio) {
                index++;
            }
            return index;
        }

        private static double clampRatio(double ratio) {
            if (!Double.isFinite(ratio) || ratio <= 0.0d) return 0.0d;
            return Math.min(1.0d, ratio);
        }

        private static <T> void removeIdentity(List<T> values, T target) {
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index) == target) {
                    values.remove(index);
                    return;
                }
            }
        }

        private static final class TargetState {
            private boolean initialized;
            private boolean isIntersecting;
            private int thresholdIndex = -1;
        }

        private record Evaluation(
                IntersectionRect rootBounds,
                IntersectionRect intersectionRect,
                boolean isIntersecting,
                double ratio
        ) {
        }
    }

    /** Runtime-facing observer, exposed to JavaScript through the global bridge. */
    public static final class IntersectionObserver {
        private final Consumer<Object> callback;
        private final Window owner;
        private final IntersectionOptions options;
        private final IntersectionObserverEngine<Element> engine;
        private Document document;
        private long documentGeneration;
        private Element root;

        private IntersectionObserver(Consumer<Object> callback,
                                     Window owner,
                                     Document document,
                                     Element root,
                                     IntersectionOptions options) {
            this.callback = callback;
            this.owner = owner;
            this.document = document;
            this.documentGeneration = document == null ? -1L : document.getRefreshGeneration();
            this.root = root;
            this.options = options == null ? IntersectionOptions.defaults() : options;
            this.engine = new IntersectionObserverEngine<>(this.options);
        }

        public Element getRoot() {
            return root;
        }

        public String getRootMargin() {
            return options.rootMargin();
        }

        public List<Double> getThresholds() {
            return options.thresholds();
        }

        public void observe(Element target) {
            if (target == null || !isCurrentDocument() || target.document != document || !target.isConnected()) return;
            engine.observe(target);
            owner.registerIntersectionObserver(this);
        }

        public void unobserve(Element target) {
            engine.unobserve(target);
            unregisterIfIdle();
        }

        public void disconnect() {
            engine.disconnect();
            owner.unregisterIntersectionObserver(this);
        }

        public List<IntersectionObserverEntry> takeRecords() {
            return toEntries(engine.takeRecords());
        }

        private boolean owns(Document candidate) {
            return document == candidate;
        }

        private void disposeForDocument() {
            engine.disconnect();
            engine.takeRecords();
            owner.unregisterIntersectionObserver(this);
            root = null;
            document = null;
            documentGeneration = -1L;
        }

        private List<IntersectionObserverEntry> collectEntries(double time) {
            if (!isCurrentDocument()) {
                disposeForDocument();
                return List.of();
            }
            for (Element target : engine.observedTargets()) {
                if (target == null || target.document != document || !target.isConnected()) {
                    engine.unobserve(target);
                }
            }
            if (engine.observedTargets().isEmpty()) {
                owner.unregisterIntersectionObserver(this);
                return List.of();
            }
            engine.evaluate(target -> captureIntersectionSnapshot(document, root, target, time));
            return toEntries(engine.takeRecords());
        }

        private boolean isCurrentDocument() {
            if (document == null || !document.isCurrentGeneration(documentGeneration)) return false;
            return root == null || root.document == document && root.isConnected();
        }

        private void unregisterIfIdle() {
            if (engine.observedTargets().isEmpty()) {
                owner.unregisterIntersectionObserver(this);
            }
        }

        private static List<IntersectionObserverEntry> toEntries(List<IntersectionEntryData<Element>> entries) {
            if (entries == null || entries.isEmpty()) return List.of();
            ArrayList<IntersectionObserverEntry> converted = new ArrayList<>(entries.size());
            for (IntersectionEntryData<Element> entry : entries) {
                if (entry != null) converted.add(new IntersectionObserverEntry(entry));
            }
            return converted;
        }
    }

    /** JavaScript-facing entry shape. */
    public static final class IntersectionObserverEntry {
        public final Element target;
        public final double time;
        public final Element.DOMRect rootBounds;
        public final Element.DOMRect boundingClientRect;
        public final Element.DOMRect intersectionRect;
        public final boolean isIntersecting;
        public final double intersectionRatio;

        private IntersectionObserverEntry(IntersectionEntryData<Element> entry) {
            this.target = entry.target();
            this.time = entry.time();
            this.rootBounds = toDomRect(entry.rootBounds());
            this.boundingClientRect = toDomRect(entry.boundingClientRect());
            this.intersectionRect = toDomRect(entry.intersectionRect());
            this.isIntersecting = entry.isIntersecting();
            this.intersectionRatio = entry.intersectionRatio();
        }
    }

    private static IntersectionSnapshot<Element> captureIntersectionSnapshot(Document document,
                                                                               Element root,
                                                                               Element target,
                                                                               double time) {
        if (document == null || !document.isActive() || target == null || target.document != document || !target.isConnected()) {
            return null;
        }
        List<RenderNode> paintOrder = document.getPaintList();
        RootGeometry rootGeometry = resolveIntersectionRootGeometry(document, root, paintOrder);
        if (rootGeometry == null) return null;

        CommittedGeometry.PaintClip targetPaintClip = CommittedGeometry.resolvePaintClip(target, paintOrder);
        IntersectionRect targetBounds = CommittedGeometry.borderBox(target);
        boolean eligible = rootGeometry.eligible
                && Interaction.isDisplayed(target)
                && targetPaintClip.painted()
                && targetBounds != null;
        if (targetBounds == null) targetBounds = IntersectionRect.ZERO;

        ArrayList<IntersectionRect> clips = new ArrayList<>();
        appendIntersectionClips(clips, rootGeometry.ancestorClips, null);
        appendIntersectionClips(clips, targetPaintClip.clips(), root);
        return new IntersectionSnapshot<>(target, time, rootGeometry.bounds, targetBounds, clips, eligible);
    }

    private static RootGeometry resolveIntersectionRootGeometry(Document document,
                                                                 Element root,
                                                                 List<RenderNode> paintOrder) {
        if (root == null) {
            ApricityViewport viewport = document.getViewport();
            return new RootGeometry(
                    new IntersectionRect(0.0d, 0.0d, viewport.layoutWidth(), viewport.layoutHeight()),
                    List.of(),
                    true
            );
        }
        if (root.document != document || !root.isConnected()) return null;
        CommittedGeometry.PaintClip rootPaintClip = CommittedGeometry.resolvePaintClip(root, paintOrder);
        IntersectionRect rootBounds = Interaction.clipsOverflow(root.getComputedStyle())
                ? CommittedGeometry.overflowClip(root)
                : CommittedGeometry.borderBox(root);
        if (rootBounds == null) rootBounds = IntersectionRect.ZERO;
        return new RootGeometry(rootBounds, rootPaintClip.clips(), Interaction.isDisplayed(root) && rootPaintClip.painted());
    }

    private static void appendIntersectionClips(List<IntersectionRect> output,
                                                List<Element> elements,
                                                Element excluded) {
        if (output == null || elements == null || elements.isEmpty()) return;
        for (Element element : elements) {
            if (element == null || element == excluded) continue;
            IntersectionRect bounds = CommittedGeometry.overflowClip(element);
            if (bounds != null) output.add(bounds);
        }
    }

    private static Element.DOMRect toDomRect(IntersectionRect rect) {
        return rect == null ? null : new Element.DOMRect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private record RootGeometry(IntersectionRect bounds, List<Element> ancestorClips, boolean eligible) {
        private RootGeometry {
            ancestorClips = ancestorClips == null ? List.of() : List.copyOf(ancestorClips);
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
