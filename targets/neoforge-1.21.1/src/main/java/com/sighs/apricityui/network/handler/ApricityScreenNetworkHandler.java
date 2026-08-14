package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotFilterSelector;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.datasource.DataSourceFactory;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.ResolvedSlotFiltersPacket;
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
     * 服务端 PendingMenu 入口：仅服务端保存 selector→FilterUtil；客户端只能收到 selector 元数据。
     */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  Map<SlotFilterSelector, FilterUtil> filtersBySelector) {
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

        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources);
        Map<SlotFilterSelector, FilterUtil> declaredFilters = sanitizeDeclaredFilters(
                normalizedPath, layout, filtersBySelector);
        layout = new SlotLayout(layout.templatePath(), layout.containers(), selectorMetadata(declaredFilters));
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
            SlotLayout layout = SlotLayout.createUiOnly(normalizedPath);
            openScreenFromServer(player, layout, Map.of(), Map.of(), null);
            return;
        }

        Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, Map.of());
        if (sources == null) return;

        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources);
        if (layout == null) return;

        openScreenFromServer(player, layout, sources, Map.of(), null);
    }

    /** 处理客户端完成 selector 解析后回传的本地槽位索引。 */
    public static void handleResolvedSlotFilters(ResolvedSlotFiltersPacket packet, INetworkContext context) {
        if (!(context.sender() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ApricityContainerMenu menu)) return;
        if (menu.containerId != packet.menuId()) {
            ApricityUI.LOGGER.warn("Resolved slot filters ignored: menu id mismatch expected={} received={}",
                    menu.containerId, packet.menuId());
            return;
        }
        menu.installResolvedFilter(packet.containerId(), packet.selector(), packet.localIndices());
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
            Map<String, ContainerDataSource> sources
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
        if (primaryContainerId.isEmpty() && !declarations.isEmpty()) {
            primaryContainerId = declarations.get(0).id();
        }

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
            if (source != null) {
                resolvedCapacity = Math.max(resolvedCapacity, source.capacity());
            }
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
                continue;
            }

            int baseIndex = customBaseById.getOrDefault(containerId, 0);
            int capacity = customCapacityById.getOrDefault(containerId, 0);
            entries.add(new SlotLayout.ContainerEntry(containerId, bindType, baseIndex, capacity, primary));
        }

        return new SlotLayout(templatePath, entries);
    }

    private static Map<SlotFilterSelector, FilterUtil> sanitizeDeclaredFilters(
            String path, SlotLayout layout, Map<SlotFilterSelector, FilterUtil> filtersBySelector) {
        if (filtersBySelector == null || filtersBySelector.isEmpty()) return Map.of();
        LinkedHashMap<SlotFilterSelector, FilterUtil> valid = new LinkedHashMap<>();
        for (Map.Entry<SlotFilterSelector, FilterUtil> entry : filtersBySelector.entrySet()) {
            SlotFilterSelector key = entry.getKey();
            FilterUtil filter = entry.getValue();
            if (key == null || !key.isValid() || filter == null) {
                warnInvalidFilter(path, key, "INVALID_DECLARATION");
                continue;
            }
            SlotLayout.ContainerEntry container = layout.findContainer(key.containerId());
            if (container == null) {
                warnInvalidFilter(path, key, "UNKNOWN_CONTAINER");
                continue;
            }
            if (ContainerBindType.isPlayer(container.bindType())) {
                warnInvalidFilter(path, key, "PLAYER_CONTAINER");
                continue;
            }
            valid.merge(key, filter, FilterUtil::and);
        }
        return valid.isEmpty() ? Map.of() : Map.copyOf(valid);
    }

    private static Map<String, List<String>> selectorMetadata(Map<SlotFilterSelector, FilterUtil> filtersBySelector) {
        if (filtersBySelector == null || filtersBySelector.isEmpty()) return Map.of();
        LinkedHashMap<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (SlotFilterSelector key : filtersBySelector.keySet()) {
            grouped.computeIfAbsent(key.containerId(), ignored -> new LinkedHashSet<>()).add(key.selector());
        }
        LinkedHashMap<String, List<String>> metadata = new LinkedHashMap<>();
        grouped.forEach((containerId, selectors) -> metadata.put(containerId, List.copyOf(selectors)));
        return metadata;
    }

    private static void warnInvalidFilter(String path, SlotFilterSelector key, String reason) {
        ApricityUI.LOGGER.warn(
                "Open screen filter ignored: path={} / container={} / selector={} / reason={}",
                path,
                key == null ? "null" : key.containerId(),
                key == null ? "null" : key.selector(),
                reason
        );
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             SlotLayout layout,
                                             Map<String, ContainerDataSource> containerSources,
                                             Map<SlotFilterSelector, FilterUtil> declaredFilters,
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
                        declaredFilters == null ? Map.of() : declaredFilters,
                        player),
                titleComponent
        ), layout::write);
    }
}
