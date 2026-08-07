package com.sighs.apricityui.fabric;

import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.handler.PendingMenu;
import com.sighs.apricityui.spi.AuiNetworkService;
import com.sighs.apricityui.spi.AuiPendingMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class FabricNetworkService implements AuiNetworkService {
    public static final FabricNetworkService INSTANCE = new FabricNetworkService();
    private FabricNetworkService() { }
    public AuiPendingMenu pendingMenu(ServerPlayer player, String templatePath) {
        PendingMenu menu = new PendingMenu(player, templatePath);
        return binder -> menu.bind(builder -> binder.accept(builder));
    }
    public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        ApricityScreenNetworkHandler.openScreen(player, templatePath, declarations);
    }
}
