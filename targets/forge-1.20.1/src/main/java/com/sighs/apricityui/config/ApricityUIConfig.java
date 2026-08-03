package com.sighs.apricityui.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ApricityUIConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    private static final AtomicBoolean CLIENT_RELOAD_PENDING = new AtomicBoolean();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue debugAutoReload;
        public final ForgeConfigSpec.BooleanValue aiAutoScreenshot;
        public final ForgeConfigSpec.BooleanValue frameTimingHud;
        public final ForgeConfigSpec.BooleanValue remoteDebug;
        public final ForgeConfigSpec.BooleanValue resourceManagerWorldWindow;
        public final ForgeConfigSpec.BooleanValue viewportZoomPassThrough;
        public final ForgeConfigSpec.DoubleValue worldWindowDepthOffsetScale;
        public final ForgeConfigSpec.IntValue worldWindowMaxDisplayDistance;
        public final ForgeConfigSpec.BooleanValue worldWindowLodEnabled;
        public final ForgeConfigSpec.IntValue worldWindowFullDetailDistance;
        public final ForgeConfigSpec.IntValue worldWindowReducedDetailDistance;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("debug");
            debugAutoReload = builder
                    .comment("Enable dev auto-reload when local files change.")
                    .define("autoReload", false);
            aiAutoScreenshot = builder
                    .comment("Enable AI helper screenshots (1 per second, keep latest 3) under screenshots/aui.")
                    .define("aiAutoScreenshot", false);
            frameTimingHud = builder
                    .comment("Show the AUI per-frame timing monitor in the top-left corner.")
                    .define("frameTimingHud", false);
            remoteDebug = builder
                    .comment("Enable the loopback-only Apricity external debugger on port 25321.")
                    .define("remoteDebug", !FMLEnvironment.production);
            resourceManagerWorldWindow = builder
                    .comment("Open the debug resource manager as a world window while in-game.")
                    .define("resourceManagerWorldWindow", false);
            builder.pop();

            builder.push("input");
            viewportZoomPassThrough = builder
                    .comment("Allow Ctrl+mouse-wheel viewport zoom to pass through persistent overlays that do not intercept mouse events.")
                    .define("viewportZoomPassThrough", true);
            builder.pop();

            builder.push("worldWindow");
            worldWindowDepthOffsetScale = builder
                    .comment("Scale applied to WorldWindow's distance-based depth offset.")
                    .defineInRange("depthOffsetScale", 0.01d, 0.0d, 1.0d);
            worldWindowMaxDisplayDistance = builder
                    .comment("Default maximum camera distance for WorldWindow rendering and interaction. Integer.MAX_VALUE means unlimited.")
                    .defineInRange("maxDisplayDistance", 128, 0, Integer.MAX_VALUE);
            worldWindowLodEnabled = builder
                    .comment("Enable distance-based level-of-detail rendering for WorldWindow by default.")
                    .define("lodEnabled", false);
            worldWindowFullDetailDistance = builder
                    .comment("WorldWindow distance up to which automatic LOD keeps full detail.")
                    .defineInRange("fullDetailDistance", 16, 0, Integer.MAX_VALUE);
            worldWindowReducedDetailDistance = builder
                    .comment("WorldWindow distance up to which automatic LOD keeps reduced detail.")
                    .defineInRange("reducedDetailDistance", 48, 0, Integer.MAX_VALUE);
            builder.pop();
        }

        public float worldWindowDepthOffsetScale() {
            return worldWindowDepthOffsetScale.get().floatValue();
        }

        public int worldWindowMaxDisplayDistance() {
            return worldWindowMaxDisplayDistance.get();
        }

        public boolean worldWindowLodEnabled() {
            return worldWindowLodEnabled.get();
        }

        public int worldWindowFullDetailDistance() {
            return worldWindowFullDetailDistance.get();
        }

        public int worldWindowReducedDetailDistance() {
            return worldWindowReducedDetailDistance.get();
        }
    }

    private ApricityUIConfig() {
    }

    /**
     * Marks a Forge config reload for processing on the client thread. Forge's file watcher
     * invokes reload listeners from its watcher thread, while a few runtime side effects need
     * to be applied by the Minecraft client.
     */
    public static void markClientReloadPending() {
        CLIENT_RELOAD_PENDING.set(true);
    }

    public static boolean consumeClientReloadPending() {
        return CLIENT_RELOAD_PENDING.compareAndSet(true, false);
    }
}
