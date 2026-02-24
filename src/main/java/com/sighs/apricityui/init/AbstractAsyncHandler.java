package com.sighs.apricityui.init;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// 用于异步加载资源
public abstract class AbstractAsyncHandler<TApplyTask> {
    private static final Map<String, AbstractAsyncHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    private final String id;
    private final int applyBudgetPerTick;
    private final long applyTimeBudgetNs;
    private final AtomicLong generation = new AtomicLong(1L);

    private final ConcurrentLinkedQueue<TApplyTask> applyQueue = new ConcurrentLinkedQueue<>();
    private final ThreadPoolExecutor workers;

    protected AbstractAsyncHandler(String id, int maxQueueSize, int applyBudgetPerTick, long applyTimeBudgetNs, String workerThreadName) {
        this.id = id;
        this.applyBudgetPerTick = Math.max(1, applyBudgetPerTick);
        this.applyTimeBudgetNs = Math.max(100_000L, applyTimeBudgetNs);
        int workerCount = Math.max(2, java.lang.Runtime.getRuntime().availableProcessors() / 2);
        this.workers = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(16, maxQueueSize)),
                createThreadFactory(workerThreadName),
                new ThreadPoolExecutor.AbortPolicy()
        );
        register(this);
    }

    public final String id() {
        return id;
    }

    protected final long currentGeneration() {
        return generation.get();
    }

    protected final void enqueueApplyTask(TApplyTask task) {
        if (task == null) return;
        applyQueue.offer(task);
    }

    protected final void submitWorker(Runnable task, Consumer<RejectedExecutionException> onRejected) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException ex) {
            if (onRejected != null) onRejected.accept(ex);
        }
    }

    public final void tickApplyQueue() {
        long startNs = System.nanoTime();
        int processed = 0;
        while (processed < applyBudgetPerTick) {
            if (System.nanoTime() - startNs >= applyTimeBudgetNs) break;
            TApplyTask task = applyQueue.poll();
            if (task == null) break;
            processed++;
            applyOnMainThread(task, currentGeneration());
        }
    }

    public final void clearAndBumpGeneration() {
        long nextGeneration = generation.incrementAndGet();
        onBeforeClear(nextGeneration);

        TApplyTask task;
        while ((task = applyQueue.poll()) != null) {
            onDiscardApplyTask(task);
        }

        workers.getQueue().clear();
        onAfterClear(nextGeneration);
    }

    protected abstract void applyOnMainThread(TApplyTask task, long currentGeneration);

    protected void onBeforeClear(long nextGeneration) {}

    protected void onAfterClear(long nextGeneration) {}

    protected void onDiscardApplyTask(TApplyTask task) {}

    public static void register(AbstractAsyncHandler<?> handler) {
        if (handler == null) return;
        HANDLERS.put(handler.id(), handler);
    }

    public static void unregister(String id) {
        if (id == null || id.isBlank()) return;
        HANDLERS.remove(id);
    }

    public static void tickAll() {
        for (AbstractAsyncHandler<?> handler : HANDLERS.values()) {
            handler.tickApplyQueue();
        }
    }

    public static void clearAllAndBumpGeneration() {
        for (AbstractAsyncHandler<?> handler : HANDLERS.values()) {
            handler.clearAndBumpGeneration();
        }
    }

    private static ThreadFactory createThreadFactory(String workerThreadName) {
        AtomicInteger index = new AtomicInteger(0);
        return runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName(workerThreadName + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public enum AsyncState {
        NEW,
        LOADING,
        APPLYING,
        READY,
        FAILED,
        STALE
    }
}
