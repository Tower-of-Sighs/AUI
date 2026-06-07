package com.sighs.apricityui.init;

import com.google.gson.Gson;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.task.ClientScheduler;
import com.sighs.apricityui.style.Box;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Window {
    public static final Window window = new Window();
    public final LocalStorage localStorage = new LocalStorage();
    public final SessionStorage sessionStorage = new SessionStorage();
    private final Map<String, List<WindowListener>> listeners = new ConcurrentHashMap<>();
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

    public double getInnerWidth() {
        return Client.getWindowSize().width();
    }

    public double getInnerHeight() {
        return Client.getWindowSize().height();
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

    public Console getConsole() {
        return console;
    }

    public void addEventListener(String type, Consumer<Object> listener) {
        addEventListener(type, listener, false);
    }

    public void addEventListener(String type, Consumer<Object> listener, boolean useCapture) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, key -> new ArrayList<>()).add(new WindowListener(listener, useCapture));
    }

    public void removeEventListener(String type, Consumer<Object> listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, Consumer<Object> listener, boolean useCapture) {
        if (type == null || listener == null) return;
        List<WindowListener> typeListeners = listeners.get(type);
        if (typeListeners == null) return;
        typeListeners.removeIf(candidate -> candidate.listener.equals(listener) && candidate.useCapture == useCapture);
    }

    public boolean dispatchEvent(Object event) {
        String type = resolveEventType(event);
        if (type == null || type.isEmpty()) return false;
        List<WindowListener> typeListeners = listeners.get(type);
        if (typeListeners == null || typeListeners.isEmpty()) return false;
        List<WindowListener> snapshot = new ArrayList<>(typeListeners);
        for (WindowListener listener : snapshot) {
            listener.listener.accept(event);
        }
        return true;
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
        return new WindowMouseEvent(type, clientX, clientY, button);
    }

    public Event createEvent(String type, boolean bubbles) {
        Event event = new Event(null, type, null, false);
        event.bubbles = bubbles;
        return event;
    }

    public Event.CustomEvent createCustomEvent(String type, Object detail, boolean bubbles) {
        return new Event.CustomEvent(type, detail, bubbles);
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
        dispatchEvent(new WindowEvent("resize"));
    }

    private static String resolveEventType(Object event) {
        if (event instanceof WindowEvent windowEvent) return windowEvent.type;
        try {
            java.lang.reflect.Field field = event.getClass().getField("type");
            Object value = field.get(event);
            return value == null ? null : value.toString();
        } catch (Exception ignored) {
            return null;
        }
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

    private record WindowListener(Consumer<Object> listener, boolean useCapture) {
    }

    public static class Performance {
        public double now() {
            return System.nanoTime() / 1_000_000.0;
        }
    }

    public static class WindowEvent {
        public final String type;

        public WindowEvent(String type) {
            this.type = type;
        }
    }

    public static class WindowMouseEvent extends WindowEvent {
        public final double clientX;
        public final double clientY;
        public final double pageX;
        public final double pageY;
        public final boolean bubbles;
        public final int button;

        public WindowMouseEvent(String type, double clientX, double clientY, int button) {
            super(type);
            this.clientX = clientX;
            this.clientY = clientY;
            this.pageX = clientX;
            this.pageY = clientY;
            this.bubbles = true;
            this.button = button;
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
    }

    public static class FetchPromise {
        private final CompletableFuture<FetchResponse> future;

        public FetchPromise(String url, String contextPath) {
            this.future = CompletableFuture.supplyAsync(() -> {
                try {
                    return loadResponse(url, contextPath);
                } catch (IOException exception) {
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
                throw new IOException("fetch url is blank");
            }
            String resolved = Loader.resolve(contextPath == null ? "" : contextPath, rawUrl);
            byte[] bytes;
            if (Loader.isRemotePath(resolved)) {
                bytes = NetworkAsyncHandler.INSTANCE.fetchBytes(resolved);
            } else {
                try (InputStream stream = ClientLoader.getResourceStream(resolved)) {
                    if (stream == null) {
                        throw new IOException("resource not found: " + resolved);
                    }
                    bytes = stream.readAllBytes();
                }
            }
            return new FetchResponse(resolved, 200, bytes);
        }
    }

    public static class FetchResponse {
        private static final Gson GSON = new Gson();
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
            return GSON.fromJson(text(), Object.class);
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static class SessionStorage {
        private final LinkedHashMap<String, String> data = new LinkedHashMap<>();

        public String getItem(String key) {
            if (key == null || key.isBlank()) return null;
            return data.get(key);
        }

        public void setItem(String key, String value) {
            if (key == null || key.isBlank()) return;
            data.put(key, value == null ? "null" : value);
        }

        public void removeItem(String key) {
            if (key == null || key.isBlank()) return;
            data.remove(key);
        }

        public void clear() {
            data.clear();
        }

        public int getLength() {
            return data.size();
        }

        public String key(int index) {
            if (index < 0 || index >= data.size()) return null;
            return new ArrayList<>(data.keySet()).get(index);
        }
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
                if (element == null || element.document == null) continue;
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
