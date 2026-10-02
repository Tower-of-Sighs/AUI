package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.datasource.DataSourceFactory;
import com.sighs.apricityui.container.filter.ContainerSlotSelector;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.ResolveSlotFiltersPacket;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** 服务端 PendingMenu 入口：FilterUtil 仅保留在服务端，selector 只作为客户端 DOM 定位描述。 */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  Map<ContainerSlotSelector, FilterUtil> filtersBySelector) {
        if (player == null) return;

        String normalizedPath = NormalizeUtil.normalizeTemplatePath(templatePath);
        if (normalizedPath == null) {
            ApricityUI.LOGGER.warn("Open screen ignored: invalid template path={}", templatePath);
            return;
        }

        if (declarations == null || declarations.isEmpty()) {
            SlotLayout layout = SlotLayout.createUiOnly(normalizedPath);
            openScreenFromServer(player, layout, Map.of(), Map.of(), null);
            return;
        }

        Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, argsById);
        if (sources == null) return;

        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources, selectorsByContainer(filtersBySelector));
        if (layout == null) return;
        openScreenFromServer(player, layout, sources, filtersBySelector == null ? Map.of() : filtersBySelector, null);
    }

    /** 处理客户端发来的 OpenScreenRequest 网络包。客户端请求不携带过滤规则。 */
    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, INetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.sender();
            if (player == null) return;

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

            SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources, Map.of());
            if (layout == null) return;

            openScreenFromServer(player, layout, sources, Map.of(), null);
        });
    }

    /** 处理客户端发来的关闭容器请求。 */
    public static void handleCloseContainerRequest(CloseContainerRequestPacket packet, INetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.sender();
            if (player == null) return;

            if (player.containerMenu instanceof ApricityContainerMenu) {
                player.closeContainer();
            }
        });
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

        return new SlotLayout(templatePath, entries, selectorMetadata);
    }

    private static Map<String, List<String>> selectorsByContainer(Map<ContainerSlotSelector, FilterUtil> filtersBySelector) {
        if (filtersBySelector == null || filtersBySelector.isEmpty()) return Map.of();
        LinkedHashMap<String, List<String>> selectors = new LinkedHashMap<>();
        filtersBySelector.forEach((key, filter) -> {
            if (key == null || !key.isValid() || filter == null) return;
            selectors.computeIfAbsent(key.containerId(), ignored -> new ArrayList<>()).add(key.selector());
        });
        selectors.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(selectors);
    }

    /** Uses only the server-owned declaration map for filters; client sends indices only. */
    public static void handleResolveSlotFilters(ResolveSlotFiltersPacket packet, INetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.sender();
            if (player == null || packet == null || !(player.containerMenu instanceof ApricityContainerMenu menu)) return;
            if (menu.containerId != packet.menuId()) return;
            Map<ContainerSlotSelector, FilterUtil> declarations = menu.selectorFilters();
            if (declarations.isEmpty()) return;

            LinkedHashMap<String, Map<Integer, FilterUtil>> resolved = new LinkedHashMap<>();
            packet.localIndicesBySelector().forEach((containerId, selectors) -> {
                SlotLayout.ContainerEntry entry = menu.getLayout().findContainer(containerId);
                if (entry == null || ContainerBindType.isPlayer(entry.bindType()) || selectors == null) {
                    ApricityUI.LOGGER.warn(
                            "Open screen filter resolution ignored: path={} / container={} / reason=UNKNOWN_OR_PLAYER_CONTAINER",
                            menu.getTemplatePath(), containerId);
                    return;
                }
                selectors.forEach((selector, indices) -> {
                    FilterUtil filter = declarations.get(new ContainerSlotSelector(containerId, selector));
                    if (filter == null || indices == null) {
                        ApricityUI.LOGGER.warn(
                                "Open screen filter resolution ignored: path={} / container={} / selector={} / reason=UNDECLARED_SELECTOR",
                                menu.getTemplatePath(), containerId, selector);
                        return;
                    }
                    for (Integer localIndex : indices) {
                        if (localIndex == null || localIndex < 0 || localIndex >= entry.capacity()
                                || menu.resolveGlobalSlotIndex(containerId, localIndex) == null) {
                            ApricityUI.LOGGER.warn(
                                    "Open screen filter resolution ignored: path={} / container={} / selector={} / index={} / reason=INVALID_INDEX",
                                    menu.getTemplatePath(), containerId, selector, localIndex);
                            continue;
                        }
                        resolved.computeIfAbsent(entry.id(), ignored -> new LinkedHashMap<>())
                                .merge(localIndex, filter, FilterUtil::and);
                    }
                });
            });
            menu.installSlotFilters(resolved);
        });
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             SlotLayout layout,
                                             Map<String, ContainerDataSource> containerSources,
                                             Map<ContainerSlotSelector, FilterUtil> filtersBySelector,
                                             String titleLiteral) {
        if (player == null || layout == null) return;
        Component titleComponent = (titleLiteral == null || titleLiteral.isBlank())
                ? Component.empty()
                : Component.literal(titleLiteral);

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (menuContainerId, playerInventory, ignoredPlayer) -> new ApricityContainerMenu(
                        menuContainerId,
                        playerInventory,
                        layout,
                        containerSources,
                        filtersBySelector == null ? Map.of() : filtersBySelector,
                        player),
                titleComponent
        ), layout::write);
    }
}
