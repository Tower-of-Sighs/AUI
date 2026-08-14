package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.datasource.DataSourceFactory;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.SelectorFilterIndicesPacket;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Screen 网络请求处理器。
 * 使用声明式数据流：容器声明 → DataSourceFactory → SlotLayout → Menu。
 */
public final class ApricityScreenNetworkHandler {
    /** 服务端 API 入口：根据容器声明列表打开 Screen。 */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations) {
        openScreen(player, templatePath, declarations, Map.of(), Map.of());
    }

    /** 服务端 API 入口（带额外参数）：根据容器声明列表和参数映射打开 Screen。 */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById) {
        openScreen(player, templatePath, declarations, argsById, Map.of());
    }

    /**
     * 服务端 PendingMenu 入口。selector 与 FilterUtil 均仅由服务端声明；客户端只接收 selector 元数据。
     */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  Map<String, Map<String, FilterUtil>> filtersBySelector) {
        if (player == null) return;

        String normalizedPath = NormalizeUtil.normalizeTemplatePath(templatePath);
        if (normalizedPath == null) {
            ApricityUI.LOGGER.warn("Open screen ignored: invalid template path={}", templatePath);
            return;
        }

        if (declarations == null || declarations.isEmpty()) {
            openScreenFromServer(player, SlotLayout.createUiOnly(normalizedPath), Map.of(), Map.of(), null);
            return;
        }

        Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, argsById);
        if (sources == null) return;

        Map<String, Map<String, FilterUtil>> declaredFilters = sanitizeDeclaredFilters(
                normalizedPath, declarations, filtersBySelector
        );
        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources, selectorMetadata(declaredFilters));
        if (layout == null) return;

        openScreenFromServer(player, layout, sources, declaredFilters, null);
    }

    /** 处理客户端发来的 OpenScreenRequest 网络包。客户端请求不携带过滤规则。 */
    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, INetworkContext context) {
        if (!(context.sender() instanceof ServerPlayer player)) return;

        String normalizedPath = NormalizeUtil.normalizeTemplatePath(packet.templatePath());
        if (normalizedPath == null) {
            ApricityUI.LOGGER.warn("Open screen request ignored: invalid path={}", packet.templatePath());
            return;
        }

        List<ContainerDeclaration> declarations = packet.containers();
        if (declarations == null || declarations.isEmpty()) {
            openScreenFromServer(player, SlotLayout.createUiOnly(normalizedPath), Map.of(), Map.of(), null);
            return;
        }

        Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, Map.of());
        if (sources == null) return;

        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources, Map.of());
        if (layout == null) return;
        openScreenFromServer(player, layout, sources, Map.of(), null);
    }

    /** 服务端根据当前打开的菜单及其保存的 selector 声明安装本地索引过滤器。 */
    public static void handleSelectorFilterIndices(SelectorFilterIndicesPacket packet, INetworkContext context) {
        if (!(context.sender() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ApricityContainerMenu menu)) {
            ApricityUI.LOGGER.warn("Selector filter ignored: reason=NO_APRICITY_MENU");
            return;
        }
        if (menu.containerId != packet.menuId()) {
            ApricityUI.LOGGER.warn("Selector filter ignored: menu={} / received={} / reason=MENU_MISMATCH",
                    menu.containerId, packet.menuId());
            return;
        }
        if (packet.localIndices().size() > 512) {
            ApricityUI.LOGGER.warn("Selector filter ignored: menu={} / container={} / selector={} / reason=TOO_MANY_INDICES",
                    packet.menuId(), packet.containerId(), packet.selector());
            return;
        }
        if (!menu.installSelectorFilter(packet.containerId(), packet.selector(), packet.localIndices())) {
            ApricityUI.LOGGER.warn("Selector filter ignored: menu={} / container={} / selector={} / reason=INVALID_DECLARATION_OR_INDEX",
                    packet.menuId(), packet.containerId(), packet.selector());
        }
    }

    /** 处理客户端发来的关闭容器请求。 */
    public static void handleCloseContainerRequest(CloseContainerRequestPacket packet, INetworkContext context) {
        if (!(context.sender() instanceof ServerPlayer player)) return;
        if (player.containerMenu instanceof ApricityContainerMenu) {
            player.closeContainer();
        }
    }

    private static Map<String, ContainerDataSource> resolveDataSources(
            ServerPlayer player,
            List<ContainerDeclaration> declarations,
            Map<String, Map<String, String>> argsById
    ) {
        LinkedHashMap<String, ContainerDataSource> sources = new LinkedHashMap<>();
        Map<String, Map<String, String>> safeArgs = argsById == null ? Map.of() : argsById;

        for (ContainerDeclaration decl : declarations) {
            if (decl == null) continue;
            ContainerBindType bindType = decl.bindType();
            if (bindType == ContainerBindType.PLAYER) continue;
            Map<String, String> args = safeArgs.getOrDefault(decl.id(), Map.of());

            try {
                ContainerDataSource dataSource = DataSourceFactory.resolve(
                        player, decl.id(), bindType, args, decl.capacity()
                );
                if (dataSource == null) {
                    ApricityUI.LOGGER.warn(
                            "Open container failed: bindType={} / container={} / reason=UNRESOLVED_BINDING",
                            bindType.id(), decl.id()
                    );
                    return null;
                }
                sources.put(decl.id(), dataSource);
            } catch (Exception exception) {
                ApricityUI.LOGGER.warn("Open container failed: bindType={} / container={} / reason={}",
                        bindType.id(), decl.id(), exception.getMessage());
                return null;
            }
        }
        return sources;
    }

    private static SlotLayout buildSlotLayout(
            String templatePath,
            List<ContainerDeclaration> declarations,
            Map<String, ContainerDataSource> sources,
            Map<String, List<String>> selectorMetadata
    ) {
        if (declarations == null || declarations.isEmpty()) {
            return SlotLayout.createUiOnly(templatePath);
        }

        String primaryContainerId = "";
        for (ContainerDeclaration decl : declarations) {
            if (decl.primary()) {
                primaryContainerId = decl.id();
                break;
            }
        }
        if (primaryContainerId.isEmpty()) primaryContainerId = declarations.get(0).id();

        int customCursor = 0;
        int playerPoolCapacity = 0;
        LinkedHashMap<String, Integer> customBaseById = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> customCapacityById = new LinkedHashMap<>();
        for (ContainerDeclaration decl : declarations) {
            ContainerBindType bindType = decl.bindType();
            int requiredCapacity = decl.capacity();
            if (bindType == ContainerBindType.PLAYER) {
                playerPoolCapacity = Math.max(playerPoolCapacity,
                        Math.min(ContainerBindType.PLAYER_SLOT_COUNT, requiredCapacity));
                continue;
            }
            int resolvedCapacity = requiredCapacity;
            ContainerDataSource source = sources.get(decl.id());
            if (source != null) resolvedCapacity = Math.max(resolvedCapacity, source.capacity());
            customBaseById.put(decl.id(), customCursor);
            customCapacityById.put(decl.id(), Math.max(0, resolvedCapacity));
            customCursor += Math.max(0, resolvedCapacity);
        }

        int playerBaseIndex = customCursor;
        ArrayList<SlotLayout.ContainerEntry> entries = new ArrayList<>(declarations.size());
        for (ContainerDeclaration decl : declarations) {
            String containerId = decl.id();
            ContainerBindType bindType = decl.bindType();
            boolean primary = containerId.equals(primaryContainerId);
            if (bindType == ContainerBindType.PLAYER) {
                int capacity = Math.min(playerPoolCapacity, Math.max(0, decl.capacity()));
                entries.add(new SlotLayout.ContainerEntry(containerId, bindType, playerBaseIndex, capacity, primary));
            } else {
                entries.add(new SlotLayout.ContainerEntry(containerId, bindType,
                        customBaseById.getOrDefault(containerId, 0),
                        customCapacityById.getOrDefault(containerId, 0), primary));
            }
        }
        return new SlotLayout(templatePath, entries, selectorMetadata);
    }

    private static Map<String, Map<String, FilterUtil>> sanitizeDeclaredFilters(
            String path,
            List<ContainerDeclaration> declarations,
            Map<String, Map<String, FilterUtil>> raw
    ) {
        if (raw == null || raw.isEmpty()) return Map.of();
        LinkedHashMap<String, ContainerBindType> types = new LinkedHashMap<>();
        for (ContainerDeclaration declaration : declarations) {
            if (declaration != null) types.put(declaration.id(), declaration.bindType());
        }

        LinkedHashMap<String, Map<String, FilterUtil>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, FilterUtil>> container : raw.entrySet()) {
            String containerId = container.getKey();
            ContainerBindType type = types.get(containerId);
            if (containerId == null || containerId.isBlank() || type == null || ContainerBindType.isPlayer(type)) {
                ApricityUI.LOGGER.warn("Open screen filter ignored: path={} / container={} / reason=INVALID_CONTAINER",
                        path, containerId);
                continue;
            }
            if (container.getValue() == null) continue;
            LinkedHashMap<String, FilterUtil> selectors = new LinkedHashMap<>();
            for (Map.Entry<String, FilterUtil> declaration : container.getValue().entrySet()) {
                String selector = declaration.getKey();
                if (selector == null || selector.isBlank() || declaration.getValue() == null) {
                    ApricityUI.LOGGER.warn("Open screen filter ignored: path={} / container={} / selector={} / reason=INVALID_DECLARATION",
                            path, containerId, selector);
                    continue;
                }
                selectors.merge(selector, declaration.getValue(), FilterUtil::and);
            }
            if (!selectors.isEmpty()) result.put(containerId, Map.copyOf(selectors));
        }
        return Map.copyOf(result);
    }

    private static Map<String, List<String>> selectorMetadata(Map<String, Map<String, FilterUtil>> declarations) {
        if (declarations.isEmpty()) return Map.of();
        LinkedHashMap<String, List<String>> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, FilterUtil>> entry : declarations.entrySet()) {
            metadata.put(entry.getKey(), List.copyOf(new LinkedHashSet<>(entry.getValue().keySet())));
        }
        return Map.copyOf(metadata);
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             SlotLayout layout,
                                             Map<String, ContainerDataSource> containerSources,
                                             Map<String, Map<String, FilterUtil>> declaredFiltersBySelector,
                                             String titleLiteral) {
        if (player == null || layout == null) return;
        Component titleComponent = (titleLiteral == null || titleLiteral.isBlank())
                ? Component.empty()
                : Component.literal(titleLiteral);

        player.openMenu(new SimpleMenuProvider(
                (menuContainerId, playerInventory, ignoredPlayer) -> new ApricityContainerMenu(
                        menuContainerId,
                        playerInventory,
                        layout,
                        containerSources,
                        Map.of(),
                        declaredFiltersBySelector == null ? Map.of() : declaredFiltersBySelector,
                        player),
                titleComponent
        ), layout::write);
    }
}
