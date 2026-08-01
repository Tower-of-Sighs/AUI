package com.sighs.apricityui.test;

import org.junit.jupiter.api.Assumptions;

/** Runtime probes shared by tests that have an optional Minecraft client path. */
public final class TestRuntime {
    private TestRuntime() {
    }

    public static boolean isMinecraftClientInitialized() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            return instance != null;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    public static boolean isClassUsable(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Keeps client-only tests explicit in the normal JVM test report. A skipped
     * test is useful here only when its reason names the runtime capability it
     * needs; otherwise it is too easy to mistake an accidental skip for a pass.
     */
    public static void assumeMinecraftClient(String capability) {
        String detail = capability == null || capability.isBlank()
                ? "requires live Minecraft client"
                : "requires live Minecraft client: " + capability;
        Assumptions.assumeTrue(isMinecraftClientInitialized(), detail);
    }

    public static void assumeClassUsable(String className, String capability) {
        String detail = capability == null || capability.isBlank()
                ? "requires Minecraft runtime class: " + className
                : "requires Minecraft runtime class " + className + ": " + capability;
        Assumptions.assumeTrue(isClassUsable(className), detail);
    }
}
