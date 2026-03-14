package com.sighs.apricityui.instance;

import net.minecraftforge.common.ForgeConfigSpec;

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

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("debug");
            debugAutoReload = builder
                    .comment("Enable dev auto-reload when local files change.")
                    .define("autoReload", false);
            aiAutoScreenshot = builder
                    .comment("Enable AI helper screenshots (1 per second, keep latest 3) under screenshots/aui.")
                    .define("aiAutoScreenshot", false);
            builder.pop();
        }
    }

    private ApricityUIConfig() {
    }
}
