package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.instance.network.handler.PendingMenu;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.server.level.ServerPlayer;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    /**
     * 服务端创建带容器绑定的菜单 Screen。
     */
    public static PendingMenu menu(ServerPlayer player, String path) {
        return new PendingMenu(player, path);
    }

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        ApricityScreenNetworkHandler.openScreen(player, path, plan);
    }

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }
}
