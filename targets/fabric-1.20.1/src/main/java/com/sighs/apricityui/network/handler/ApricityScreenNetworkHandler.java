package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.datasource.DataSourceFactory;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApricityScreenNetworkHandler {
    private ApricityScreenNetworkHandler() { }

    public static void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        openScreen(player, templatePath, declarations, Map.of());
    }

    public static void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations, Map<String, Map<String, String>> argsById) {
        if (player == null) return;
        String path = NormalizeUtil.normalizeTemplatePath(templatePath);
        if (path == null) return;
        List<ContainerDeclaration> safeDeclarations = declarations == null ? List.of() : declarations;
        Map<String, ContainerDataSource> sources = resolveSources(player, safeDeclarations, argsById);
        if (sources == null) return;
        SlotLayout layout = buildLayout(path, safeDeclarations, sources);
        openScreenFromServer(player, layout, sources);
    }

    public static void handleOpenScreenRequest(ServerPlayer player, OpenScreenRequestPacket packet) {
        if (packet != null) openScreen(player, packet.templatePath(), packet.containers());
    }

    public static void handleCloseContainerRequest(ServerPlayer player) {
        if (player != null && player.containerMenu instanceof ApricityContainerMenu) player.closeContainer();
    }

    private static Map<String, ContainerDataSource> resolveSources(ServerPlayer player, List<ContainerDeclaration> declarations, Map<String, Map<String, String>> argsById) {
        LinkedHashMap<String, ContainerDataSource> sources = new LinkedHashMap<>();
        Map<String, Map<String, String>> args = argsById == null ? Map.of() : argsById;
        for (ContainerDeclaration declaration : declarations) {
            if (declaration == null || declaration.bindType() == ContainerBindType.PLAYER) continue;
            try {
                ContainerDataSource source = DataSourceFactory.resolve(player, declaration.id(), declaration.bindType(), args.getOrDefault(declaration.id(), Map.of()), declaration.capacity());
                if (source == null) return null;
                sources.put(declaration.id(), source);
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.warn("Failed to resolve Fabric container {}", declaration.id(), exception);
                return null;
            }
        }
        return sources;
    }

    private static SlotLayout buildLayout(String path, List<ContainerDeclaration> declarations, Map<String, ContainerDataSource> sources) {
        if (declarations.isEmpty()) return SlotLayout.createUiOnly(path);
        String primary = declarations.stream().filter(ContainerDeclaration::primary).map(ContainerDeclaration::id).findFirst().orElse(declarations.get(0).id());
        int customCursor = 0;
        int playerCapacity = 0;
        Map<String, Integer> bases = new LinkedHashMap<>();
        Map<String, Integer> capacities = new LinkedHashMap<>();
        for (ContainerDeclaration declaration : declarations) {
            if (declaration.bindType() == ContainerBindType.PLAYER) {
                playerCapacity = Math.max(playerCapacity, Math.min(ContainerBindType.PLAYER_SLOT_COUNT, declaration.capacity()));
                continue;
            }
            int capacity = Math.max(declaration.capacity(), sources.get(declaration.id()).capacity());
            bases.put(declaration.id(), customCursor);
            capacities.put(declaration.id(), capacity);
            customCursor += capacity;
        }
        int playerBase = customCursor;
        ArrayList<SlotLayout.ContainerEntry> entries = new ArrayList<>();
        for (ContainerDeclaration declaration : declarations) {
            boolean primaryEntry = declaration.id().equals(primary);
            if (declaration.bindType() == ContainerBindType.PLAYER) {
                entries.add(new SlotLayout.ContainerEntry(declaration.id(), declaration.bindType(), playerBase, Math.min(playerCapacity, declaration.capacity()), primaryEntry));
            } else {
                entries.add(new SlotLayout.ContainerEntry(declaration.id(), declaration.bindType(), bases.get(declaration.id()), capacities.get(declaration.id()), primaryEntry));
            }
        }
        return new SlotLayout(path, entries);
    }

    private static void openScreenFromServer(ServerPlayer player, SlotLayout layout, Map<String, ContainerDataSource> sources) {
        if (layout == null) return;
        ExtendedScreenHandlerFactory factory = new ExtendedScreenHandlerFactory() {
            public void writeScreenOpeningData(ServerPlayer ignored, FriendlyByteBuf buf) { layout.write(buf); }
            public Component getDisplayName() { return Component.empty(); }
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player ignored) {
                return new ApricityContainerMenu(id, inventory, layout, sources, player);
            }
        };
        player.openMenu(factory);
    }
}
