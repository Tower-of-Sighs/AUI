package com.sighs.apricityui.instance;

import cc.sighs.oelib.config.ConfigAccess;
import cc.sighs.oelib.config.ConfigRecordCodecBuilder;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import cc.sighs.oelib.config.ConfigManager;
import net.minecraft.resources.ResourceLocation;

public record ApricityUIConfig(
        boolean debugAutoReload,
        boolean aiAutoScreenshot
) {
    private static final String MODID = "apricityui";

    public static final ConfigUnit<ApricityUIConfig> UNIT = ConfigRecordCodecBuilder.createClient(
            new ResourceLocation(MODID, "client"),
            instance -> instance.group(
                    ConfigField.bool("autoReload")
                            .defaultValue(false)
                            .tooltip()
                            .comment("Enable dev auto-reload when local files change.")
                            .forGetter(ApricityUIConfig::debugAutoReload),
                    ConfigField.bool("aiAutoScreenshot")
                            .defaultValue(false)
                            .tooltip()
                            .comment("Enable AI helper screenshots (1 per second, keep latest 3) under screenshots/aui.")
                            .forGetter(ApricityUIConfig::aiAutoScreenshot)
            ).apply(instance, ApricityUIConfig::new),
            meta -> meta
                    .directory(MODID)
                    .fileName("client")
                    .format(ConfigStorageFormat.TOML)
    );

    public static final ConfigAccess<ApricityUIConfig> ACCESS = new ConfigAccess<>(UNIT);

    public static ApricityUIConfig get() {
        return UNIT.get();
    }

    public static void register() {
        ConfigManager.registerClient(UNIT);
    }
}