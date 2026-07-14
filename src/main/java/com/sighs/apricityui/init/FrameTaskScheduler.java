package com.sighs.apricityui.init;

import java.util.ArrayDeque;
import java.util.Queue;

public final class FrameTaskScheduler {
    private static final long DEFAULT_BUDGET_NS = 2_000_000L;
    private static final Queue<FrameTask> tasks = new ArrayDeque<>();

    private FrameTaskScheduler() {
    }

    public static void schedule(FrameTask task) {
        if (task == null) return;
        tasks.add(task);
    }

    public static void tick() {
        if (tasks.isEmpty()) return;
        long deadlineNs = System.nanoTime() + DEFAULT_BUDGET_NS;
        while (!tasks.isEmpty()) {
            FrameTask task = tasks.peek();
            boolean done;
            try {
                done = task.runUntil(deadlineNs);
            } catch (Exception exception) {
                done = true;
                exception.printStackTrace();
            }
            if (done) {
                tasks.poll();
            }
            if (System.nanoTime() >= deadlineNs) {
                break;
            }
        }
    }

    @FunctionalInterface
    public interface FrameTask {
        boolean runUntil(long deadlineNs);
    }
}
