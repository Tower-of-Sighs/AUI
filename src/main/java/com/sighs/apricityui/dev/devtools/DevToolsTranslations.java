package com.sighs.apricityui.dev.devtools;

import net.minecraft.network.chat.Component;

final class DevToolsTranslations {
    private DevToolsTranslations() {
    }

    static String translate(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
