package com.sighs.apricityui.task;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

public class ClientScheduler {
    // 创建全局唯一的守护线程 Timer
    private static final Timer TIMER = new Timer("ApricityUI-Timer", true);

    @FunctionalInterface
    public interface Cancellable {
        boolean cancel();
    }

    // 让 Task 继承 TimerTask 以便直接投递给 Timer
    private static class Task extends TimerTask implements Cancellable {
        private final Consumer<Cancellable> action;
        private final boolean isRepeat;

        public Task(Consumer<Cancellable> action, boolean isRepeat) {
            this.action = action;
            this.isRepeat = isRepeat;
        }

        @Override
        public void run() {
            try {
                // 执行逻辑
                action.accept(this);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 如果不是循环任务，执行完后自动清理（TimerTask 内部逻辑）
            if (!isRepeat) {
                this.cancel();
            }
        }
    }

    /**
     * 客户端延时 (setTimeout)
     *
     * @param ms     延迟毫秒数 (1秒 = 1000ms)
     * @param action 回调
     */
    public static Cancellable setTimeout(int ms, Consumer<Cancellable> action) {
        Task task = new Task(action, false);
        TIMER.schedule(task, ms);
        return task;
    }

    /**
     * 客户端循环 (setInterval)
     *
     * @param ms     间隔毫秒数
     * @param action 回调
     */
    public static Cancellable setInterval(int ms, Consumer<Cancellable> action) {
        Task task = new Task(action, true);
        // 使用 scheduleAtFixedRate 保证频率稳定
        TIMER.scheduleAtFixedRate(task, ms, ms);
        return task;
    }
}