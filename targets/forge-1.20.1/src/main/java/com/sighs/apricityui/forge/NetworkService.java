package com.sighs.apricityui.forge;

import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.handler.PendingMenu;
import com.sighs.apricityui.spi.AuiNetworkService;
import com.sighs.apricityui.spi.AuiPendingMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;

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
        return new PendingMenuAdapter(player, templatePath);
    }

    /**
     * Concrete Forge-side adapter exposed to KubeJS/Rhino instead of a synthetic
     * lambda class. The common SPI remains loader-neutral.
     */
    public static final class PendingMenuAdapter implements AuiPendingMenu {
        private final PendingMenu delegate;

        public PendingMenuAdapter(ServerPlayer player, String templatePath) {
            this.delegate = new PendingMenu(player, templatePath);
        }

        @Override
        public void bind(Consumer<Object> binder) {
            if (binder == null) {
                delegate.bind(null);
                return;
            }
            delegate.bind(builder -> binder.accept(builder));
        }
    }

    @Override
    public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        ApricityScreenNetworkHandler.openScreen(player, templatePath, declarations);
    }
}
