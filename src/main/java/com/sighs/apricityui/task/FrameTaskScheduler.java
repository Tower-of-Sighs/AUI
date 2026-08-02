package com.sighs.apricityui.task;

import com.sighs.apricityui.ApricityUI;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public final class FrameTaskScheduler {
    private static final long DEFAULT_BUDGET_NS = 2_000_000L;
    private static final Queue<FrameTask> tasks = new ArrayDeque<>();
    private static final Queue<DeferredTask> deferredTasks = new PriorityQueue<>(Comparator.comparingLong(DeferredTask::targetFrame));
    private static long frameIndex;

    private FrameTaskScheduler() {
    }

    public static void schedule(FrameTask task) {
        if (task == null) return;
        tasks.add(task);
    }

    /**
     * Schedules work after the requested number of frame commits. This is useful when
     * a caller must make a DOM change visible before starting a synchronous operation.
     */
    public static void scheduleAfterFrames(int frames, FrameTask task) {
        if (task == null) return;
        long targetFrame = frameIndex + Math.max(1, frames);
        deferredTasks.add(new DeferredTask(targetFrame, task));
    }

    public static void tick() {
        frameIndex++;
        while (!deferredTasks.isEmpty() && deferredTasks.peek().targetFrame() <= frameIndex) {
            tasks.add(deferredTasks.poll().task());
        }
        if (tasks.isEmpty()) return;
        long deadlineNs = System.nanoTime() + DEFAULT_BUDGET_NS;
        while (!tasks.isEmpty()) {
            FrameTask task = tasks.peek();
            boolean done;
            try {
                done = task.runUntil(deadlineNs);
            } catch (Exception exception) {
                done = true;
                ApricityUI.LOGGER.error("[AUI Scheduler] frame task failed frame={}", frameIndex, exception);
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

    private record DeferredTask(long targetFrame, FrameTask task) {
    }
}
