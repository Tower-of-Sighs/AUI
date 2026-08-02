package com.sighs.apricityui.task;

import com.sighs.apricityui.ApricityUI;

import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

public class ClientScheduler {
    private static final Timer TIMER = new Timer("ApricityUI-Timer", true);

    @FunctionalInterface
    public interface Cancellable {
        boolean cancel();
    }

    private static final class Task extends TimerTask implements Cancellable {
        private final Consumer<Cancellable> action;
        private final boolean repeat;

        private Task(Consumer<Cancellable> action, boolean repeat) {
            this.action = action;
            this.repeat = repeat;
        }

        @Override
        public void run() {
            try {
                runOnClientThread(() -> action.accept(this));
            } catch (Exception e) {
                ApricityUI.LOGGER.error("[AUI Scheduler] client timer task failed repeat={}", repeat, e);
            }

            if (!repeat) {
                cancel();
            }
        }
    }

    public static Cancellable setTimeout(int ms, Consumer<Cancellable> action) {
        Task task = new Task(action, false);
        TIMER.schedule(task, ms);
        return task;
    }

    public static Cancellable setInterval(int ms, Consumer<Cancellable> action) {
        Task task = new Task(action, true);
        // Browser setInterval is fixed-delay: the next run is scheduled relative to the
        // previous run's completion. scheduleAtFixedRate would fire catch-up bursts when
        // the client thread is slow, which browsers never do and which breaks clearInterval
        // (a canceled interval could still fire once more to catch up).
        TIMER.schedule(task, ms, ms);
        return task;
    }

    private static void runOnClientThread(Runnable action) {
        if (action == null) return;
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = minecraftClass.getMethod("getInstance");
            Object minecraft = getInstance.invoke(null);
            if (minecraft != null) {
                Method execute = minecraftClass.getMethod("execute", Runnable.class);
                execute.invoke(minecraft, action);
                return;
            }
        } catch (Throwable ignored) {
        }
        action.run();
    }
}
