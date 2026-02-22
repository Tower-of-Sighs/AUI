package com.sighs.apricityui.init;

import com.sighs.apricityui.task.ClientScheduler;

import java.util.function.Consumer;

public class Window {
    public static final Window window = new Window();
    public final LocalStorage localStorage = new LocalStorage();

    public ClientScheduler.Cancellable setTimeout(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setTimeout(delay, runnable);
    }

    public ClientScheduler.Cancellable setInterval(Consumer<ClientScheduler.Cancellable> runnable, int delay) {
        return ClientScheduler.setInterval(delay, runnable);
    }
}
