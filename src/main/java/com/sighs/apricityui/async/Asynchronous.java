package com.sighs.apricityui.async;

import com.sighs.apricityui.registry.annotation.AsyncTask;
import com.sighs.apricityui.registry.annotation.AsyncTaskClass;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
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

// 注解驱动的统一异步委托器：负责 worker 调度、主线程 apply、清理与代际切换。
public final class Asynchronous {
    private static final Map<String, HandlerRuntime> RUNTIMES_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Object, HandlerRuntime> RUNTIMES_BY_HANDLER =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private Asynchronous() {}

    public static String register(Object handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler 不能为空");
        }
        HandlerRuntime existing = RUNTIMES_BY_HANDLER.get(handler);
        if (existing != null) return existing.id;

        AsyncTaskClass classMeta = handler.getClass().getAnnotation(AsyncTaskClass.class);
        if (classMeta == null) {
            throw new IllegalStateException("缺少 @AsyncTaskClass 注解: " + handler.getClass().getName());
        }

        HandlerRuntime runtime = buildRuntime(handler, classMeta);
        HandlerRuntime oldById = RUNTIMES_BY_ID.putIfAbsent(runtime.id, runtime);
        if (oldById != null && oldById.handler != handler) {
            throw new IllegalStateException("异步处理器 id 重复: " + runtime.id);
        }
        RUNTIMES_BY_HANDLER.put(handler, runtime);
        return runtime.id;
    }

    public static long currentGeneration(Object handler) {
        return runtimeOf(handler).generation.get();
    }

    public static void submitWorker(Object handler, String taskName, Object... args) {
        HandlerRuntime runtime = runtimeOf(handler);
        Method worker = runtime.workers.get(taskName);
        if (worker == null) {
            throw new IllegalStateException("未注册 WORKER 任务: " + runtime.id + "#" + taskName);
        }
        try {
            runtime.workersExecutor.execute(() -> runWorker(runtime, worker, args));
        } catch (RejectedExecutionException rejectedExecutionException) {
            notifyError(runtime, AsyncTaskRole.WORKER, rejectedExecutionException, pickContext(args));
        }
    }

    public static void enqueueApply(Object handler, Object task) {
        if (task == null) return;
        runtimeOf(handler).applyQueue.offer(task);
    }

    public static void tickAll() {
        for (HandlerRuntime runtime : RUNTIMES_BY_ID.values()) {
            tick(runtime);
        }
    }

    public static void clearAllAndBumpGeneration() {
        for (HandlerRuntime runtime : RUNTIMES_BY_ID.values()) {
            clearAndBumpGeneration(runtime);
        }
    }

    public static void clearAndBumpGeneration(Object handler) {
        clearAndBumpGeneration(runtimeOf(handler));
    }

    private static void tick(HandlerRuntime runtime) {
        long startNs = System.nanoTime();
        int processed = 0;
        while (processed < runtime.applyBudgetPerTick) {
            if (System.nanoTime() - startNs >= runtime.applyTimeBudgetNs) break;
            Object task = runtime.applyQueue.poll();
            if (task == null) break;
            processed++;
            runApply(runtime, task);
        }
    }

    private static void clearAndBumpGeneration(HandlerRuntime runtime) {
        long nextGeneration = runtime.generation.incrementAndGet();
        runOnClear(runtime, nextGeneration);

        Object task;
        while ((task = runtime.applyQueue.poll()) != null) {
            runDiscard(runtime, task);
        }

        runtime.workersExecutor.getQueue().clear();
    }

    private static HandlerRuntime runtimeOf(Object handler) {
        HandlerRuntime runtime = RUNTIMES_BY_HANDLER.get(handler);
        if (runtime == null) {
            throw new IllegalStateException("异步处理器未注册: " + handler.getClass().getName());
        }
        return runtime;
    }

    private static void runWorker(HandlerRuntime runtime, Method worker, Object[] args) {
        try {
            Object result = worker.invoke(runtime.handler, args);
            if (result != null) {
                runtime.applyQueue.offer(result);
            }
        } catch (Throwable throwable) {
            notifyError(runtime, AsyncTaskRole.WORKER, unwrapThrowable(throwable), pickContext(args));
        }
    }

    private static void runApply(HandlerRuntime runtime, Object task) {
        Method apply = runtime.applyMethod;
        if (apply == null) return;
        try {
            apply.invoke(runtime.handler, task, runtime.generation.get());
        } catch (Throwable throwable) {
            notifyError(runtime, AsyncTaskRole.APPLY, unwrapThrowable(throwable), task);
        }
    }

    private static void runDiscard(HandlerRuntime runtime, Object task) {
        Method discard = runtime.discardMethod;
        if (discard == null) return;
        try {
            discard.invoke(runtime.handler, task);
        } catch (Throwable throwable) {
            notifyError(runtime, AsyncTaskRole.DISCARD, unwrapThrowable(throwable), task);
        }
    }

    private static void runOnClear(HandlerRuntime runtime, long nextGeneration) {
        Method onClear = runtime.onClearMethod;
        if (onClear == null) return;
        try {
            onClear.invoke(runtime.handler, nextGeneration);
        } catch (Throwable throwable) {
            notifyError(runtime, AsyncTaskRole.ON_CLEAR, unwrapThrowable(throwable), nextGeneration);
        }
    }

    private static void notifyError(HandlerRuntime runtime, AsyncTaskRole role, Throwable error, Object context) {
        Method onError = runtime.onErrorMethod;
        if (onError == null) return;
        try {
            onError.invoke(runtime.handler, role, error, context);
        } catch (Throwable ignored) {
            // 错误回调内部异常直接吞掉，避免无限递归。
        }
    }

    private static Object pickContext(Object[] args) {
        if (args == null || args.length == 0) return null;
        if (args.length == 1) return args[0];
        return Arrays.asList(args);
    }

    private static Throwable unwrapThrowable(Throwable throwable) {
        if (throwable.getCause() != null) return throwable.getCause();
        return throwable;
    }

    private static HandlerRuntime buildRuntime(Object handler, AsyncTaskClass classMeta) {
        HandlerRuntime runtime = new HandlerRuntime();
        runtime.id = classMeta.id();
        runtime.handler = handler;
        runtime.applyBudgetPerTick = Math.max(1, classMeta.applyBudgetPerTick());
        runtime.applyTimeBudgetNs = Math.max(100_000L, classMeta.applyTimeBudgetNs());
        runtime.workersExecutor = new ThreadPoolExecutor(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(16, classMeta.maxQueueSize())),
                createThreadFactory(classMeta.workerThreadName()),
                new ThreadPoolExecutor.AbortPolicy()
        );

        for (Method method : handler.getClass().getDeclaredMethods()) {
            AsyncTask taskMeta = method.getAnnotation(AsyncTask.class);
            if (taskMeta == null) continue;
            method.setAccessible(true);
            switch (taskMeta.role()) {
                case WORKER -> {
                    if (taskMeta.value().isBlank()) {
                        throw new IllegalStateException("@AsyncTask(role=WORKER) 必须声明 value: " + method.getName());
                    }
                    runtime.workers.put(taskMeta.value(), method);
                }
                case APPLY -> runtime.applyMethod = method;
                case DISCARD -> runtime.discardMethod = method;
                case ON_CLEAR -> runtime.onClearMethod = method;
                case ON_ERROR -> runtime.onErrorMethod = method;
            }
        }
        return runtime;
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

    public enum AsyncTaskRole {
        WORKER,
        APPLY,
        DISCARD,
        ON_CLEAR,
        ON_ERROR
    }

    private static final class HandlerRuntime {
        private String id;
        private Object handler;
        private int applyBudgetPerTick;
        private long applyTimeBudgetNs;
        private ThreadPoolExecutor workersExecutor;
        private final AtomicLong generation = new AtomicLong(1L);
        private final ConcurrentLinkedQueue<Object> applyQueue = new ConcurrentLinkedQueue<>();
        private final Map<String, Method> workers = new ConcurrentHashMap<>();
        private Method applyMethod;
        private Method discardMethod;
        private Method onClearMethod;
        private Method onErrorMethod;
    }
}
