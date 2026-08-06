package com.sighs.apricityui.instance.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.SlotLayout;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.datasource.DataSourceFactory;
import com.sighs.apricityui.instance.element.Container.ContainerDeclaration;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.instance.screen.ApricityContainerMenu;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Screen 网络请求处理器。
 * 使用声明式数据流：容器声明 → DataSourceFactory → SlotLayout → Menu。
 */
public final class ApricityScreenNetworkHandler {

    /**
     * 客户端请求打开 Screen（发送网络包到服务端）。
     */
    public static void requestOpenScreen(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ApricityNetwork.sendToServer(new OpenScreenRequestPacket(path));
    }

    /**
     * 客户端请求关闭 Screen。
     */
    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ApricityNetwork.sendToServer(new CloseContainerRequestPacket());
    }

    /**
     * 服务端 API 入口：根据容器声明列表打开 Screen。
     */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations) {
        openScreen(player, templatePath, declarations, Map.of());
    }

    /**
     * 服务端 API 入口（带额外参数）：根据容器声明列表和参数映射打开 Screen。
     */
    public static void openScreen(ServerPlayer player,
                                  String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById) {
        if (player == null) return;

        String normalizedPath = NormalizeUtil.normalizeTemplatePath(templatePath);
        if (normalizedPath == null) {
            ApricityUI.LOGGER.warn("Open screen ignored: invalid template path={}", templatePath);
            return;
        }

        if (declarations == null || declarations.isEmpty()) {
            SlotLayout layout = SlotLayout.createUiOnly(normalizedPath);
            openScreenFromServer(player, layout, Map.of(), null);
            return;
        }

        Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, argsById);
        if (sources == null) return;

        SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources);
        if (layout == null) return;

        openScreenFromServer(player, layout, sources, null);
    }

    /**
     * 处理客户端发来的 OpenScreenRequest 网络包。
     */
    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            String normalizedPath = NormalizeUtil.normalizeTemplatePath(packet.templatePath());
            if (normalizedPath == null) {
                ApricityUI.LOGGER.warn("Open screen request ignored: invalid path={}", packet.templatePath());
                return;
            }

            List<ContainerDeclaration> declarations = packet.containers();
            if (declarations == null || declarations.isEmpty()) {
                SlotLayout layout = SlotLayout.createUiOnly(normalizedPath);
                openScreenFromServer(player, layout, Map.of(), null);
                return;
            }

            Map<String, ContainerDataSource> sources = resolveDataSources(player, declarations, Map.of());
            if (sources == null) return;

            SlotLayout layout = buildSlotLayout(normalizedPath, declarations, sources);
            if (layout == null) return;

            openScreenFromServer(player, layout, sources, null);
        });
        context.setPacketHandled(true);
    }

    /**
     * 处理客户端发来的关闭容器请求。
     */
    public static void handleCloseContainerRequest(CloseContainerRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (player.containerMenu instanceof ApricityContainerMenu) {
                player.closeContainer();
            }
        });
        context.setPacketHandled(true);
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
            // 非玩家容器的容量 0 表示由数据源自动推导，不能提前跳过。
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

    private static void openScreenFromServer(ServerPlayer player,
                                             SlotLayout layout,
                                             Map<String, ContainerDataSource> containerSources,
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
                        player),
                titleComponent
        ), layout::write);
    }
}
