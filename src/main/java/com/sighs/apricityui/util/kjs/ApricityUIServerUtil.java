package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.server.level.ServerPlayer;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        ApricityScreenNetworkHandler.openScreen(player, path, plan);
    }
}