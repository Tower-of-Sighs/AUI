package com.sighs.apricityui.util;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.entity.player.ServerPlayerEntity;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    public static void openScreen(ServerPlayerEntity player, String path, OpenBindPlan plan) {
        ApricityScreenNetworkHandler.openScreen(player, path, plan);
    }
}
