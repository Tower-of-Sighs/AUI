package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.element.Container.ContainerDeclaration;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.handler.PendingMenu;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    /**
     * 服务端创建带容器绑定的菜单 Screen。
     * <p>
     * 使用示例（KJS）：
     * <pre>
     * ApricityUI.menu(player, "test/test.html").bind(b => b.blockEntity(pos).player())
     * </pre>
     */
    public static PendingMenu menu(ServerPlayer player, String path) {
        return new PendingMenu(player, path);
    }

    /**
     * 服务端打开 Screen（带容器声明）。
     *
     * @deprecated 使用 {@link #menu(ServerPlayer, String)} 替代
     */
    @Deprecated
    public static void openScreen(ServerPlayer player, String path, List<ContainerDeclaration> declarations) {
        ApricityScreenNetworkHandler.openScreen(player, path, declarations);
    }

    /**
     * 服务端打开纯 UI Screen（无容器）。
     *
     * @deprecated 使用 {@link #menu(ServerPlayer, String)} 替代
     */
    @Deprecated
    public static void openScreen(ServerPlayer player, String path) {
        ApricityScreenNetworkHandler.openScreen(player, path, List.of());
    }
}
