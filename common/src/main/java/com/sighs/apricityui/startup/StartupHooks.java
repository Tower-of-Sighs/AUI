package com.sighs.apricityui.startup;

import com.sighs.apricityui.spi.AuiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StartupHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");
    private static final AtomicBoolean DISPATCHED = new AtomicBoolean();
    private static final List<Method> HOOKS = new ArrayList<>();

    private static volatile StartupContext CONTEXT;

    private StartupHooks() {
    }

    public static StartupContext context() {
        StartupContext existing = CONTEXT;
        if (existing != null) {
            return existing;
        }
        synchronized (StartupHooks.class) {
            if (CONTEXT == null) {
                CONTEXT = new StartupContext(System.nanoTime());
            }
            return CONTEXT;
        }
    }

    public static void dispatch() {
        if (!DISPATCHED.compareAndSet(false, true)) {
            return;
        }

        com.sighs.apricityui.webview.StartupLoadingOverlay.runOnceOnFirstDraw(StartupHooks::raiseScriptStartup);
        final long startedNanos = System.nanoTime();
        discover();
        if (HOOKS.isEmpty()) {
            LOGGER.info("startup hooks: none registered");
            return;
        }
        final StartupContext context = context();
        int invoked = 0;
        for (Method hook : HOOKS) {
            try {
                hook.invoke(null, context);
                invoked++;
            } catch (InvocationTargetException failed) {
                LOGGER.error("@StartupHook {} threw", describe(hook), failed.getCause());
            } catch (Throwable failed) {
                LOGGER.error("@StartupHook {} could not run", describe(hook), failed);
            }
        }
        LOGGER.info("startup hooks: {}/{} invoked in {} ms", invoked, HOOKS.size(),
                (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static void raiseScriptStartup() {
        try {
            AuiServices.script().startupPhase(context());
        } catch (Throwable failure) {
            LOGGER.error("startup script event failed", failure);
        }
    }

    private static void discover() {
        try {
            AuiServices.client().scanAnnotationStaticMethods(StartupHook.class, StartupHooks::register, () -> {
            });
        } catch (Throwable failure) {
            LOGGER.warn("startup hook scan failed: {}", failure.toString());
        }

        HOOKS.sort(Comparator.<Method>comparingInt(StartupHooks::priorityOf).reversed()
                .thenComparing(hook -> hook.getDeclaringClass().getName())
                .thenComparing(Method::getName));
    }

    private static synchronized void register(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            LOGGER.error("@StartupHook {} must be static", describe(method));
            return;
        }
        final Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 1 || !parameters[0].isAssignableFrom(StartupContext.class)) {
            LOGGER.error("@StartupHook {} must take exactly one StartupContext parameter", describe(method));
            return;
        }
        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {
        }
        if (!HOOKS.contains(method)) {
            HOOKS.add(method);
        }
    }

    private static int priorityOf(Method method) {
        final StartupHook hook = method.getAnnotation(StartupHook.class);
        return hook == null ? 0 : hook.priority();
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
