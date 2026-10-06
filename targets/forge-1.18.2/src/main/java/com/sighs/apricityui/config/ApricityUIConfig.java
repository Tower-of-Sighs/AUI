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
        public final ForgeConfigSpec.BooleanValue viewportZoomPassThrough;
        public final ForgeConfigSpec.BooleanValue blockMouseEventsWhenCursorHidden;
        public final ForgeConfigSpec.DoubleValue worldWindowDepthOffsetScale;
        public final ForgeConfigSpec.IntValue worldWindowMaxDisplayDistance;
        public final ForgeConfigSpec.BooleanValue worldWindowLodEnabled;
        public final ForgeConfigSpec.IntValue worldWindowFullDetailDistance;
        public final ForgeConfigSpec.IntValue worldWindowReducedDetailDistance;
        public final ForgeConfigSpec.BooleanValue initialCommitSliceEnabled;
        public final ForgeConfigSpec.DoubleValue initialCommitSliceMs;

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
            builder.pop();

            builder.push("input");
            viewportZoomPassThrough = builder
                    .comment("Allow Ctrl+mouse-wheel viewport zoom to pass through persistent overlays that do not intercept mouse events.")
                    .define("viewportZoomPassThrough", true);
            blockMouseEventsWhenCursorHidden = builder
                    .comment("When the game hides the mouse cursor (crosshair mode), overlay and screen documents receive no mouse events. World windows are unaffected.")
                    .define("blockMouseEventsWhenCursorHidden", true);
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

            builder.push("layout");
            initialCommitSliceEnabled = builder
                    .comment("Slice the first full geometry commit across frames within a per-frame time budget instead of committing everything in one go.")
                    .define("initialCommitSliceEnabled", true);
            initialCommitSliceMs = builder
                    .comment("Per-frame time budget in milliseconds for the sliced first full geometry commit. Only an upper bound; at least one element is always committed per frame.")
                    .defineInRange("initialCommitSliceMs", 16.0d, 0.5d, 64.0d);
            builder.pop();
        }

        public float worldWindowDepthOffsetScale() {
            return ApricityUIConfig.get(worldWindowDepthOffsetScale).floatValue();
        }

        public int worldWindowMaxDisplayDistance() {
            return ApricityUIConfig.get(worldWindowMaxDisplayDistance);
        }

        public boolean worldWindowLodEnabled() {
            return ApricityUIConfig.get(worldWindowLodEnabled);
        }

        public int worldWindowFullDetailDistance() {
            return ApricityUIConfig.get(worldWindowFullDetailDistance);
        }

        public int worldWindowReducedDetailDistance() {
            return ApricityUIConfig.get(worldWindowReducedDetailDistance);
        }
    }

    /**
     * Client ticks can run during the loading overlay before Forge loads the client config.
     * {@code ConfigValue.get()} throws in that window and kills the game.
     *
     * <p>1.18.2 的 {@code ConfigValue} 还没有 {@code getDefault()}，改从 spec 的默认值表取
     * （{@code getValues()} 就是 builder 建出来的那份默认值，未装载时也读得到）。</p>
     */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        return CLIENT_SPEC.isLoaded() ? value.get() : CLIENT_SPEC.getValues().get(value.getPath());
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
