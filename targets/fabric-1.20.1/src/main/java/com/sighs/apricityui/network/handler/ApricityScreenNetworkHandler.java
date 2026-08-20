package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.datasource.DataSourceFactory;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.packet.ApplySlotFiltersRequestPacket;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ApricityScreenNetworkHandler {
    private ApricityScreenNetworkHandler() { }

    public static void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        openScreen(player, templatePath, declarations, Map.of(), Map.of());
    }

    public static void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations, Map<String, Map<String, String>> argsById) {
        openScreen(player, templatePath, declarations, argsById, Map.of());
    }

    /** 服务端 PendingMenu 入口：selector 和 FilterUtil 都只在服务端声明。 */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  Map<String, Map<String, FilterUtil>> filtersByContainerAndSelector) {
        if (player == null) return;
        String path = NormalizeUtil.normalizeTemplatePath(templatePath);
        if (path == null) return;
        List<ContainerDeclaration> safeDeclarations = declarations == null ? List.of() : declarations;
        Map<String, ContainerDataSource> sources = resolveSources(player, safeDeclarations, argsById);
        if (sources == null) return;
        Map<String, Map<String, FilterUtil>> declaredFilters = normalizeDeclaredFilters(path, safeDeclarations, filtersByContainerAndSelector);
        SlotLayout layout = buildLayout(path, safeDeclarations, sources, declaredFilters);
        openScreenFromServer(player, layout, sources, declaredFilters);
    }

    /** 客户端网络入口不接受过滤规则。 */
    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, INetworkContext context) {
        if (packet != null && context.sender() != null) openScreen(context.sender(), packet.templatePath(), packet.containers());
    }

    public static void handleCloseContainerRequest(CloseContainerRequestPacket packet, INetworkContext context) {
        ServerPlayer player = context.sender();
        if (player != null && player.containerMenu instanceof ApricityContainerMenu) player.closeContainer();
    }

    /**
     * 客户端仅回传服务端下发 selector 的本地索引；服务端从当前菜单中取得过滤声明并安装。
     */
    public static void handleApplySlotFiltersRequest(ApplySlotFiltersRequestPacket packet, INetworkContext context) {
        ServerPlayer player = context.sender();
        if (player == null || packet == null || !(player.containerMenu instanceof ApricityContainerMenu menu)) return;
        if (menu.containerId != packet.menuId()) {
            ApricityUI.LOGGER.warn("Slot filter mapping ignored: path={} / reason=MENU_ID_MISMATCH", menu.getTemplatePath());
            return;
        }

        for (ApplySlotFiltersRequestPacket.SelectorSlotMapping mapping : packet.mappings()) {
            String containerId = mapping.containerId();
            String selector = mapping.selector();
            SlotLayout.ContainerEntry entry = menu.getLayout().findContainer(containerId);
            if (entry == null) {
                warnMapping(menu.getTemplatePath(), containerId, selector, "UNKNOWN_CONTAINER");
                continue;
            }
            if (ContainerBindType.isPlayer(entry.bindType())) {
                warnMapping(menu.getTemplatePath(), containerId, selector, "PLAYER_CONTAINER");
                continue;
            }
            FilterUtil filter = menu.declaredFilter(containerId, selector);
            if (filter == null) {
                warnMapping(menu.getTemplatePath(), containerId, selector, "UNDECLARED_SELECTOR");
                continue;
            }
            for (Integer index : mapping.localIndices()) {
                if (index == null || index < 0 || index >= entry.capacity()) {
                    warnMapping(menu.getTemplatePath(), containerId, selector, "INVALID_INDEX");
                    continue;
                }
                if (!menu.installDeclaredFilter(containerId, index, filter)) {
                    warnMapping(menu.getTemplatePath(), containerId, selector, "FILTER_INSTALL_FAILED");
                }
            }
        }
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

    private static SlotLayout buildLayout(String path,
                                          List<ContainerDeclaration> declarations,
                                          Map<String, ContainerDataSource> sources,
                                          Map<String, Map<String, FilterUtil>> filtersByContainerAndSelector) {
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
        return new SlotLayout(path, entries, selectorsForClient(path, entries, filtersByContainerAndSelector));
    }

    private static Map<String, List<String>> selectorsForClient(String path,
                                                                 List<SlotLayout.ContainerEntry> entries,
                                                                 Map<String, Map<String, FilterUtil>> declarations) {
        if (declarations == null || declarations.isEmpty()) return Map.of();
        LinkedHashMap<String, List<String>> selectors = new LinkedHashMap<>();
        for (SlotLayout.ContainerEntry entry : entries) {
            Map<String, FilterUtil> filters = declarations.get(entry.id());
            if (filters == null || filters.isEmpty()) continue;
            if (ContainerBindType.isPlayer(entry.bindType())) {
                filters.keySet().forEach(selector -> warnMapping(path, entry.id(), selector, "PLAYER_CONTAINER"));
                continue;
            }
            ArrayList<String> validSelectors = new ArrayList<>();
            for (Map.Entry<String, FilterUtil> filter : filters.entrySet()) {
                if (filter.getKey() == null || filter.getKey().isBlank() || filter.getValue() == null) {
                    warnMapping(path, entry.id(), filter.getKey(), "INVALID_DECLARATION");
                    continue;
                }
                validSelectors.add(filter.getKey());
            }
            if (!validSelectors.isEmpty()) selectors.put(entry.id(), List.copyOf(new LinkedHashSet<>(validSelectors)));
        }
        return Map.copyOf(selectors);
    }

    private static Map<String, Map<String, FilterUtil>> normalizeDeclaredFilters(
            String path,
            List<ContainerDeclaration> declarations,
            Map<String, Map<String, FilterUtil>> filtersByContainerAndSelector) {
        if (declarations == null || declarations.isEmpty()
                || filtersByContainerAndSelector == null || filtersByContainerAndSelector.isEmpty()) return Map.of();
        LinkedHashMap<String, ContainerBindType> bindTypes = new LinkedHashMap<>();
        for (ContainerDeclaration declaration : declarations) {
            if (declaration != null) bindTypes.putIfAbsent(declaration.id(), declaration.bindType());
        }
        LinkedHashMap<String, Map<String, FilterUtil>> normalized = new LinkedHashMap<>();
        filtersByContainerAndSelector.forEach((containerId, filters) -> {
            if (containerId == null || containerId.isBlank() || filters == null || filters.isEmpty()) return;
            ContainerBindType bindType = bindTypes.get(containerId);
            if (bindType == null) {
                filters.keySet().forEach(selector -> warnMapping(path, containerId, selector, "UNKNOWN_CONTAINER"));
                return;
            }
            if (ContainerBindType.isPlayer(bindType)) {
                filters.keySet().forEach(selector -> warnMapping(path, containerId, selector, "PLAYER_CONTAINER"));
                return;
            }
            LinkedHashMap<String, FilterUtil> usable = new LinkedHashMap<>();
            filters.forEach((selector, filter) -> {
                if (selector == null || selector.isBlank() || filter == null) {
                    warnMapping(path, containerId, selector, "INVALID_DECLARATION");
                    return;
                }
                usable.put(selector, filter);
            });
            if (!usable.isEmpty()) normalized.put(containerId, Map.copyOf(usable));
        });
        return Map.copyOf(normalized);
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             SlotLayout layout,
                                             Map<String, ContainerDataSource> sources,
                                             Map<String, Map<String, FilterUtil>> declaredFilters) {
        if (layout == null) return;
        ExtendedScreenHandlerFactory factory = new ExtendedScreenHandlerFactory() {
            public void writeScreenOpeningData(ServerPlayer ignored, FriendlyByteBuf buf) { layout.write(buf); }
            public Component getDisplayName() { return Component.empty(); }
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player ignored) {
                return new ApricityContainerMenu(id, inventory, layout, sources, declaredFilters, player);
            }
        };
        player.openMenu(factory);
    }

    private static void warnMapping(String path, String containerId, String selector, String reason) {
        ApricityUI.LOGGER.warn("Slot filter mapping ignored: path={} / container={} / selector={} / reason={}", path, containerId, selector, reason);
    }
}
