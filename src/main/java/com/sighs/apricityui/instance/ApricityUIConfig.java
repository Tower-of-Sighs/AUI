package com.sighs.apricityui.instance;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ApricityUIConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue debugAutoReload;
        public final ModConfigSpec.BooleanValue aiAutoScreenshot;

        private Client(ModConfigSpec.Builder builder) {
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
