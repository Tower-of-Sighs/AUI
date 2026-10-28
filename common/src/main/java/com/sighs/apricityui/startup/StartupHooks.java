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

/**
 * Finds and runs the {@link StartupHook} methods.
 *
 * <p>Dispatch happens once, from each loader's client bootstrap, immediately before the
 * startup page's browser is started: the page a hook configures is therefore the page the
 * browser opens, with no navigation or flicker afterwards. Discovery goes through the
 * loader's annotation scanner, so hooks can live in any mod's package.</p>
 *
 * <p>Nothing here can fail the launch: a scan that does not work simply finds no hooks, and
 * every hook runs inside its own try/catch.</p>
 */
public final class StartupHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");
    private static final AtomicBoolean DISPATCHED = new AtomicBoolean();
    private static final List<Method> HOOKS = new ArrayList<>();
    /** Created on first use so hooks and non-annotated callers share one context. */
    private static volatile StartupContext CONTEXT;

    private StartupHooks() {
    }

    /**
     * The context every hook receives, for callers that are not annotated.
     *
     * <p>A KubeJS client script cannot carry a Java annotation, so it reaches the same API
     * through {@code ApricityUI.startup()} (which returns this), or directly from Java with
     * {@code StartupHooks.context()}. The instance is shared with the hooks, so a script that
     * runs before dispatch configures the page the browser opens, and one that runs later
     * navigates the live view.</p>
     */
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

    /**
     * Runs every {@link StartupHook}, highest priority first. Idempotent: a second call does
     * nothing, so a loader that bootstraps twice cannot run a hook twice.
     */
    public static void dispatch() {
        if (!DISPATCHED.compareAndSet(false, true)) {
            return;
        }
        // The script platform's own startup event fires on the loading screen's first frame:
        // the mod-construction point this runs at is earlier than any startup_scripts.
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

    /**
     * Raises the script platform's startup event with the shared context.
     *
     * <p>Runs on the render thread from the loading screen's first frame, so a script that
     * listens can install a page before anything is shown (the browser is already running,
     * so that page is a navigation — a script cannot be the very first page, unlike a
     * {@code @StartupHook}).</p>
     */
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
        // Highest priority first; the name breaks ties so the order is identical for every
        // launch, which matters when two hooks both want to set the page.
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
