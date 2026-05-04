package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.instance.element.Container.ContainerDeclaration;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    /**
     * 服务端打开 Screen（带容器声明）。
     */
    public static void openScreen(ServerPlayer player, String path, List<ContainerDeclaration> declarations) {
        ApricityScreenNetworkHandler.openScreen(player, path, declarations);
    }

    /**
     * 服务端打开纯 UI Screen（无容器）。
     */
    public static void openScreen(ServerPlayer player, String path) {
        ApricityScreenNetworkHandler.openScreen(player, path, List.of());
    }
}
