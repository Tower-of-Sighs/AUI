package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.canvas.CanvasImageBitmap;
import com.sighs.apricityui.canvas.CanvasImageSupport;
import com.sighs.apricityui.canvas.BrowserImage;
import com.sighs.apricityui.canvas.CanvasBlob;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.canvas.OffscreenCanvas;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.script.ecmascript.EcmaProxyObject;
import com.sighs.apricityui.script.ecmascript.EcmaEventListener;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.ClientScheduler;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.Callable;
import dev.latvian.mods.rhino.util.HideFromJS;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.sighs.apricityui.util.BrowserLocation;
import com.sighs.apricityui.util.LocalStorage;
import com.sighs.apricityui.util.SimpleJsonParser;
import com.sighs.apricityui.util.Storage;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.ScriptEventListeners;
import com.sighs.apricityui.style.Style;

public class Window implements com.sighs.apricityui.script.host.AuiScriptHost {
    public static final Window window = new Window();
    public final LocalStorage localStorage = new LocalStorage();
    public final SessionStorage sessionStorage = new SessionStorage();
    private final Map<String, CopyOnWriteArrayList<Event.ListenerRecord>> listeners = new ConcurrentHashMap<>();
    private final ScriptEventListeners scriptListeners = new ScriptEventListeners(new ScriptEventListeners.Target() {
        @Override
        public void add(String type, Consumer<Event> listener, boolean capture, boolean once) {
            Window.this.addEventListener(type, listener, capture, once);
        }

        @Override
        public void remove(String type, Consumer<Event> listener, boolean capture) {
            Window.this.removeEventListener(type, listener, capture);
        }
    });
    private final Map<Integer, AnimationFrame> animationFrames = new ConcurrentHashMap<>();
    private final AtomicInteger nextAnimationFrameId = new AtomicInteger(1);
    private final Performance performance = new Performance();
    private volatile long animationTimeMillis = (long) performance.now();
    private volatile boolean animationTimelinePaused;
    private final Console console = new Console();
    private final CopyOnWriteArrayList<ResizeObserver> resizeObservers = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Microtask> microtasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean microtaskDrainScheduled = new AtomicBoolean();
    private final ThreadLocal<Integer> scriptTaskDepth = ThreadLocal.withInitial(() -> 0);
    private final Map<String, CanvasBlob> objectUrls = new ConcurrentHashMap<>();

    public ClientScheduler.Cancellable setTimeout(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        Document document = Document.getContextDocument();
        long generation = document == null ? -1L : document.getRefreshGeneration();
        return ClientScheduler.setTimeout(delay, handle -> {
            if (!isScheduledDocumentValid(document, generation)) return;
            try (Document.ContextScope ignored = Document.withContext(document)) {
                runnable.accept(handle);
            }
        });
    }

    public ClientScheduler.Cancellable setInterval(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        Document document = Document.getContextDocument();
        long generation = document == null ? -1L : document.getRefreshGeneration();
        return ClientScheduler.setInterval(delay, handle -> {
            if (!isScheduledDocumentValid(document, generation)) {
                handle.cancel();
                return;
            }
            try (Document.ContextScope ignored = Document.withContext(document)) {
                runnable.accept(handle);
            }
        });
    }

    public void queueMicrotask(Consumer<Object> callback) {
        if (callback == null) return;
        Document document = Document.getContextDocument();
        long generation = document == null ? -1L : document.getRefreshGeneration();
        microtasks.add(new Microtask(document, generation, callback));
        if (scriptTaskDepth.get() == 0) scheduleMicrotaskDrain();
    }

    public void beginScriptTask() {
        scriptTaskDepth.set(scriptTaskDepth.get() + 1);
    }

    public void endScriptTask() {
        int depth = scriptTaskDepth.get();
        if (depth > 1) {
            scriptTaskDepth.set(depth - 1);
            return;
        }
        scriptTaskDepth.remove();
        drainMicrotasks();
    }

    public EcmaProxyObject createProxy(Scriptable target, Scriptable handler) {
        if (target == null || handler == null) {
            throw new IllegalArgumentException("Proxy target and handler must be JavaScript objects");
        }
        return new EcmaProxyObject(target, handler);
    }

    public Consumer<Event> createEventListener(Object callback) {
        Consumer<Event> listener = AuiServices.script().createEventListener(callback);
        if (listener != null) return listener;
        return castCallback(createCallback(callback));
    }

    public Consumer<Object> createCallback(Object callback) {
        Consumer<Object> listener = AuiServices.script().createCallback(callback);
        if (listener != null) return listener;
        if (!(callback instanceof Callable callable) || !(callback instanceof Scriptable scriptable)) {
            throw new IllegalArgumentException("Callback must be a JavaScript function");
        }
        return new EcmaEventListener(callable, scriptable);
    }

    public Object wrapScriptHost(Object value) {
        return AuiServices.script().wrapHostObject(value);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Event> castCallback(Consumer<Object> callback) {
        return (Consumer<Event>) (Consumer<?>) callback;
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

    public BrowserImage createImage() {
        return new BrowserImage();
    }

    public CanvasBlob createBlob(String content, String type) {
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        return new CanvasBlob(bytes, type);
    }

    public String createObjectURL(CanvasBlob blob) {
        if (blob == null) throw new IllegalArgumentException("Blob is required");
        String url = "blob:apricityui/" + UUID.randomUUID();
        objectUrls.put(url, blob);
        return url;
    }

    public void revokeObjectURL(String url) {
        if (url != null) objectUrls.remove(url);
    }

    public CanvasBlob resolveObjectURL(String url) {
        return url == null ? null : objectUrls.get(url);
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

    @HideFromJS
    public void addEventListener(String type, Consumer<? super Event> listener) {
        addEventListener(type, listener, false);
    }

    @HideFromJS
    public void addEventListener(String type, Consumer<? super Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false);
    }

    @HideFromJS
    public void addEventListener(String type, Consumer<? super Event> listener, boolean useCapture, boolean once) {
        if (type == null || listener == null) return;
        Consumer<Event> wrapped = wrapWindowListener(listener);
        listeners.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(new Event.ListenerRecord(type, wrapped, useCapture, once, false));
    }

    public void addEventListener(String type, Object callback) {
        scriptListeners.add(type, callback, null);
    }

    public void addEventListener(String type, Object callback, Object options) {
        scriptListeners.add(type, callback, options);
    }

    @HideFromJS
    public void removeEventListener(String type, Consumer<? super Event> listener) {
        removeEventListener(type, listener, false);
    }

    @HideFromJS
    public void removeEventListener(String type, Consumer<? super Event> listener, boolean useCapture) {
        if (type == null || listener == null) return;
        CopyOnWriteArrayList<Event.ListenerRecord> typeListeners = listeners.get(type);
        if (typeListeners == null) return;
        typeListeners.removeIf(candidate ->
                candidate.useCapture() == useCapture && listener.equals(unwrapWindowListener(candidate.listener())));
    }

    public void removeEventListener(String type, Object callback) {
        scriptListeners.remove(type, callback, null);
    }

    public void removeEventListener(String type, Object callback, Object options) {
        scriptListeners.remove(type, callback, options);
    }

    public boolean supportsScriptEventListenerOptions() {
        return true;
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
        Document document = Document.getContextDocument();
        long generation = document == null ? -1L : document.getRefreshGeneration();
        int id = nextAnimationFrameId.getAndIncrement();
        animationFrames.put(id, new AnimationFrame(document, generation, callback));
        return id;
    }

    public void cancelAnimationFrame(int id) {
        animationFrames.remove(id);
    }

    public void fireAnimationFrame() {
        fireAnimationFrame(performance.now());
    }

    public void fireAnimationFrame(double timestamp) {
        if (animationTimelinePaused) return;
        if (!Double.isFinite(timestamp) || timestamp < 0.0) timestamp = performance.now();
        animationTimeMillis = (long) Math.floor(timestamp);
        ArrayList<Map.Entry<Integer, AnimationFrame>> ready = new ArrayList<>(animationFrames.entrySet());
        ready.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, AnimationFrame> entry : ready) {
            AnimationFrame frame = entry.getValue();
            if (!animationFrames.remove(entry.getKey(), frame)
                    || !isScheduledDocumentValid(frame.document(), frame.generation())) continue;
            try (Document.ContextScope ignored = Document.withContext(frame.document())) {
                beginScriptTask();
                try {
                    frame.callback().accept(timestamp);
                } catch (RuntimeException exception) {
                    ApricityUI.LOGGER.error("[AUI Scheduler] animation frame failed", exception);
                } finally {
                    endScriptTask();
                }
            }
        }
    }

    public long animationTimeMillis() {
        return animationTimeMillis;
    }

    @HideFromJS
    public void setAnimationTimeMillisForTesting(long timeMillis) {
        animationTimeMillis = timeMillis;
    }

    @HideFromJS
    public void setAnimationTimelinePausedForTesting(boolean paused) {
        animationTimelinePaused = paused;
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
        return new Event(null, type, bubbles);
    }

    public Event.CustomEvent createCustomEvent(String type, Object detail, boolean bubbles) {
        Event.CustomEvent event = new Event.CustomEvent(type, detail, bubbles);
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

    private static boolean isScheduledDocumentValid(Document document, long generation) {
        return document == null || (!document.isDisposed() && document.getRefreshGeneration() == generation);
    }

    private static Executor clientCallbackExecutor(Document document) {
        return command -> ClientScheduler.setTimeout(0, ignored -> {
            try (Document.ContextScope ignoredContext = Document.withContext(document)) {
                command.run();
            }
        });
    }

    private void scheduleMicrotaskDrain() {
        if (!microtaskDrainScheduled.compareAndSet(false, true)) return;
        ClientScheduler.setTimeout(0, ignored -> drainMicrotasks());
    }

    private void drainMicrotasks() {
        try {
            Microtask microtask;
            while ((microtask = microtasks.poll()) != null) {
                Document document = microtask.document();
                if (document != null && (document.isDisposed()
                        || document.getRefreshGeneration() != microtask.generation())) {
                    continue;
                }
                try {
                    try (Document.ContextScope ignored = Document.withContext(document)) {
                        microtask.callback().accept(null);
                    }
                } catch (RuntimeException exception) {
                    ApricityUI.LOGGER.error("[AUI Scheduler] microtask failed", exception);
                }
            }
        } finally {
            microtaskDrainScheduled.set(false);
            if (!microtasks.isEmpty()) scheduleMicrotaskDrain();
        }
    }

    private record Microtask(Document document, long generation, Consumer<Object> callback) {
    }

    private record AnimationFrame(Document document, long generation, Consumer<Double> callback) {
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
        private final Executor callbackExecutor;

        public FetchPromise(String url, String contextPath) {
            this.callbackExecutor = clientCallbackExecutor(Document.getContextDocument());
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
                future.thenAcceptAsync(onFulfilled, callbackExecutor);
            }
            return this;
        }

        public FetchPromise then(Consumer<FetchResponse> onFulfilled, Consumer<Object> onRejected) {
            future.whenCompleteAsync((response, throwable) -> {
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
            }, callbackExecutor);
            return this;
        }

        public FetchPromise catchError(Consumer<Object> onRejected) {
            if (onRejected != null) {
                future.exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                    return null;
                }, callbackExecutor);
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
        private final Executor callbackExecutor;

        public ImageBitmapPromise(java.util.concurrent.Callable<CanvasImageBitmap> task) {
            this.callbackExecutor = clientCallbackExecutor(Document.getContextDocument());
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
                future.thenAcceptAsync(onFulfilled, callbackExecutor);
            }
            return this;
        }

        public ImageBitmapPromise then(Consumer<CanvasImageBitmap> onFulfilled, Consumer<Object> onRejected) {
            future.whenCompleteAsync((bitmap, throwable) -> {
                if (throwable == null) {
                    if (onFulfilled != null) onFulfilled.accept(bitmap);
                    return;
                }
                if (onRejected != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                }
            }, callbackExecutor);
            return this;
        }

        public ImageBitmapPromise catchError(Consumer<Object> onRejected) {
            if (onRejected != null) {
                future.exceptionallyAsync(throwable -> {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    onRejected.accept(cause.getMessage());
                    return null;
                }, callbackExecutor);
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

    public static class Console implements com.sighs.apricityui.script.host.AuiScriptHost {
        private final Map<String, Long> timers = new ConcurrentHashMap<>();

        public void log(Object value) {
            ApricityUI.LOGGER.info(String.valueOf(value));
        }

        public Object debug;

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
