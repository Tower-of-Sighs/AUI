package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import net.neoforged.fml.ModList;

/**
 * KubeJS availability probe for the 1.21.1 script bridge.
 *
 * <p>The 1.21.1 bridge ({@link ApricityJS}) runs page scripts on KubeJS's
 * bundled Rhino, so KubeJS is the runtime prerequisite: without it there is no
 * Rhino, and {@code ApricityJS} itself cannot even be class-loaded because its
 * fields and method signatures reference Rhino types. This class is
 * intentionally free of any Rhino/KubeJS references so it can be loaded in a
 * KubeJS-less environment.</p>
 *
 * <p>Every {@code ApricityJS} entry point must therefore be guarded by
 * {@link #loaded()} so the JVM never touches {@code ApricityJS} when KubeJS is
 * absent — pages still render and only their scripts are skipped.</p>
 */
public final class KubeJSSupport {
    private static volatile Boolean loaded;

    private KubeJSSupport() {
    }

    public static boolean loaded() {
        Boolean available = loaded;
        if (available == null) {
            available = probe();
            loaded = available;
            if (!available) {
                ApricityUI.LOGGER.warn("[AUI JS] KubeJS not loaded; page scripts are skipped (pages still render)");
            }
        }
        return available;
    }

    private static boolean probe() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (LinkageError | RuntimeException unavailableForgeRuntime) {
            return false;
        }
    }
}
