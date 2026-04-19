package com.sighs.apricityui.init;

import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.task.ClientScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Window {
    public static final Window window = new Window();
    public final LocalStorage localStorage = new LocalStorage();
    private final Map<String, List<WindowListener>> listeners = new ConcurrentHashMap<>();
    private final Map<Integer, ClientScheduler.Cancellable> animationFrames = new ConcurrentHashMap<>();
    private final AtomicInteger nextAnimationFrameId = new AtomicInteger(1);
    private final Performance performance = new Performance();

    public ClientScheduler.Cancellable setTimeout(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setTimeout(delay, runnable);
    }

    public ClientScheduler.Cancellable setInterval(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setInterval(delay, runnable);
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
}
