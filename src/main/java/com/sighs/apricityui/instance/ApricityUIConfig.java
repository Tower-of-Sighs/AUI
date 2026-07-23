package com.sighs.apricityui.instance;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class ApricityUIConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

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
        }
    }

    private ApricityUIConfig() {
    }
}
