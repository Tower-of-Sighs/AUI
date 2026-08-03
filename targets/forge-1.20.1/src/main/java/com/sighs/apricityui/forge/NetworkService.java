package com.sighs.apricityui.forge;

import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.handler.PendingMenu;
import com.sighs.apricityui.spi.AuiNetworkService;
import com.sighs.apricityui.spi.AuiPendingMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Forge implementation of {@link AuiNetworkService}, delegating to the loader's
 * network layer ({@link PendingMenu} / {@link ApricityScreenNetworkHandler}).
 */
public final class NetworkService implements AuiNetworkService {
    public static final NetworkService INSTANCE = new NetworkService();

    private NetworkService() {
    }

    @Override
    public AuiPendingMenu pendingMenu(ServerPlayer player, String templatePath) {
        PendingMenu menu = new PendingMenu(player, templatePath);
        return binder -> menu.bind(builder -> binder.accept(builder));
    }

    @Override
    public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        ApricityScreenNetworkHandler.openScreen(player, templatePath, declarations);
    }
}
