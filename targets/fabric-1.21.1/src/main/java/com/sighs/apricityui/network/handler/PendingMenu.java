package com.sighs.apricityui.network.handler;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * 待打开的菜单 Screen，通过 {@link #bind(Consumer)} 声明绑定关系并立即打开。
 * <p>
 * 使用示例：
 * <pre>
 * ApricityUI.menu(player, "test/test.html").bind(b -> b.blockEntity(pos).player());
 * </pre>
 */
public final class PendingMenu {

    private final ServerPlayer player;
    private final String templatePath;

    public PendingMenu(ServerPlayer player, String templatePath) {
        this.player = player;
        this.templatePath = templatePath;
    }

    /**
     * 声明容器绑定关系并立即打开 Screen。
     *
     * @param configurator 绑定配置回调，通过 {@link BindingBuilder} 链式声明数据源
     */
    public void bind(Consumer<BindingBuilder> configurator) {
        BindingBuilder builder = new BindingBuilder();
        if (configurator != null) {
            configurator.accept(builder);
        }
        ApricityScreenNetworkHandler.openScreen(
                player,
                templatePath,
                builder.declarations(),
                builder.argsById()
        );
    }
}
