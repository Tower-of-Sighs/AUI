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

    private StartupHooks() {
    }

    /**
     * Runs every {@link StartupHook}, highest priority first. Idempotent: a second call does
     * nothing, so a loader that bootstraps twice cannot run a hook twice.
     */
    public static void dispatch() {
        if (!DISPATCHED.compareAndSet(false, true)) {
            return;
        }
        final long startedNanos = System.nanoTime();
        discover();
        if (HOOKS.isEmpty()) {
            LOGGER.info("startup hooks: none registered");
            return;
        }
        final StartupContext context = new StartupContext(startedNanos);
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
