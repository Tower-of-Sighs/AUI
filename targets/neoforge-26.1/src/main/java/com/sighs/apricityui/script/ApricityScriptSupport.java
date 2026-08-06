package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;

/**
 * Rhino availability probe for the 26.1 script bridge.
 *
 * <p>This class is intentionally free of any Rhino references so it can be
 * loaded when no Rhino runtime is on the classpath (26.1 has no KubeJS, so a
 * production instance without the Rhino mod provides nothing). {@link ApricityJS}
 * references Rhino types in method bodies, which means the JVM cannot even
 * <i>load</i> that class without Rhino — every call site must therefore be
 * guarded by {@link #rhinoAvailable()} so {@code ApricityJS} is never touched
 * in a Rhino-less environment. Pages still render and interact normally; only
 * their scripts are skipped.</p>
 */
public final class ApricityScriptSupport {
    private static volatile Boolean rhinoAvailable;

    private ApricityScriptSupport() {
    }

    public static boolean rhinoAvailable() {
        Boolean available = rhinoAvailable;
        if (available == null) {
            available = probe();
            rhinoAvailable = available;
            if (!available) {
                ApricityUI.LOGGER.warn("[AUI JS] Rhino not present; page scripts are skipped (pages still render)");
            }
        }
        return available;
    }

    private static boolean probe() {
        try {
            Class.forName("dev.latvian.mods.rhino.Context", false, ApricityScriptSupport.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
