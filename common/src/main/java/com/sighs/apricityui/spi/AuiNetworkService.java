package com.sighs.apricityui.spi;

import com.sighs.apricityui.element.ContainerDeclaration;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Loader-side networking and server menu access.
 *
 * <p>Implemented by the Forge network layer and registered through
 * {@link AuiServices}. Common KJS bindings use this interface instead of the
 * loader's network classes directly.</p>
 */
public interface AuiNetworkService {
    /** Creates a pending container menu for the given player. */
    AuiPendingMenu pendingMenu(ServerPlayer player, String templatePath);

    /** Opens a screen for the player with the given container declarations. */
    void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations);
}
