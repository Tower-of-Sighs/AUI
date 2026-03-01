package com.sighs.apricityui.instance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.ShaderInstance;
import net.minecraft.resources.IResourceManager;

public class ShaderRegistry {
    private static ShaderInstance filterShader;
    private static boolean initialized;
    private static boolean loadAttempted;

    public static void init() {
        initialized = true;
    }

    private static void loadShader() {
        if (loadAttempted) return;
        loadAttempted = true;
        try {
            IResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            filterShader = new ShaderInstance(resourceManager, "apricityui:filter");
        } catch (Exception e) {
            filterShader = null;
        }
    }

    public static ShaderInstance getFilterShader() {
        if (!initialized) return null;
        if (filterShader == null && !loadAttempted) {
            loadShader();
        }
        return filterShader;
    }
}
