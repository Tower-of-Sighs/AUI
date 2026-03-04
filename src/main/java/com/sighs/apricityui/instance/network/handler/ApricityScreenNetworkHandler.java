package com.sighs.apricityui.instance.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.layout.MenuLayoutSpec;
import com.sighs.apricityui.instance.container.template.TemplateCompiler;
import com.sighs.apricityui.instance.container.template.TemplateSpec;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class ApricityScreenNetworkHandler {
    public static void requestOpenScreen(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ApricityNetwork.sendToServer(new OpenScreenRequestPacket(path));
    }

    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ApricityNetwork.sendToServer(new CloseContainerRequestPacket());
    }

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        if (player == null) return;

        TemplateSpec compiled = resolveTemplateSpec(path, plan);
        if (compiled == null) return;
        TemplateSpec boundTemplateSpec = applyBindPlan(compiled, plan);

        Map<String, ContainerDataSource> containerSources = resolveContainerSources(player, boundTemplateSpec, plan);
        if (containerSources == null) return;

        MenuLayoutSpec layoutSpec = buildLayoutSpec(boundTemplateSpec, plan, containerSources);
        if (layoutSpec == null) return;

        String titleLiteral = resolvePrimaryContainerTitleLiteral(boundTemplateSpec, layoutSpec.primaryContainerId());
        openScreenFromServer(player, layoutSpec, containerSources, titleLiteral);
    }

    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TemplateSpec compiled = resolveTemplateSpec(packet.templatePath(), null);
            if (compiled == null) return;
            TemplateSpec boundTemplateSpec = applyBindPlan(compiled, null);

            Map<String, ContainerDataSource> containerSources = resolveContainerSources(player, boundTemplateSpec, null);
            if (containerSources == null) return;

            MenuLayoutSpec layoutSpec = buildLayoutSpec(boundTemplateSpec, null, containerSources);
            if (layoutSpec == null) return;

            String titleLiteral = resolvePrimaryContainerTitleLiteral(boundTemplateSpec, layoutSpec.primaryContainerId());
            openScreenFromServer(player, layoutSpec, containerSources, titleLiteral);
        });
        context.setPacketHandled(true);
    }

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

    private static TemplateSpec resolveTemplateSpec(String rawTemplatePath, OpenBindPlan plan) {
        String planTemplatePath = plan == null ? null : normalizeTemplatePath(plan.templatePath());
        String requestTemplatePath = normalizeTemplatePath(rawTemplatePath);

        String selectedTemplatePath = planTemplatePath != null ? planTemplatePath : requestTemplatePath;
        if (selectedTemplatePath == null) {
            ApricityUI.LOGGER.warn("Open screen ignored: invalid template path, requestPath={}, planPath={}",
                    rawTemplatePath, plan == null ? "" : plan.templatePath());
            return null;
        }

        TemplateSpec compiled = TemplateCompiler.compile(selectedTemplatePath);
        if (compiled == null) {
            ApricityUI.LOGGER.warn("Open screen ignored: template compile failed, path={}", selectedTemplatePath);
        }
        return compiled;
    }

    private static TemplateSpec applyBindPlan(TemplateSpec templateSpec, OpenBindPlan plan) {
        if (templateSpec == null) return null;
        if (plan == null) return templateSpec;

        String primaryContainerId = resolvePrimaryContainerId(plan, templateSpec);
        ArrayList<TemplateSpec.ContainerSpec> containers = new ArrayList<>(templateSpec.containers().size());
        for (TemplateSpec.ContainerSpec containerSpec : templateSpec.containers()) {
            if (containerSpec == null) continue;
            ContainerBindType bindType = resolveBindType(containerSpec, plan);
            containers.add(new TemplateSpec.ContainerSpec(
                    containerSpec.id(),
                    bindType,
                    containerSpec.id().equals(primaryContainerId),
                    containerSpec.requiredCapacity(),
                    containerSpec.title()
            ));
        }
        return new TemplateSpec(templateSpec.templatePath(), primaryContainerId, containers);
    }

    private static ContainerBindType resolveBindType(TemplateSpec.ContainerSpec containerSpec, OpenBindPlan plan) {
        ContainerBindType bindType = containerSpec == null ? null : containerSpec.bindType();
        if (containerSpec != null && plan != null) {
            OpenBindPlan.ContainerOverride override = plan.container(containerSpec.id());
            if (override != null && override.bind() != null) {
                bindType = override.bind().bindType();
            }
        }
        return bindType == null ? ContainerBindType.PLAYER : bindType;
    }

    private static Map<String, ContainerDataSource> resolveContainerSources(
            ServerPlayer player,
            TemplateSpec templateSpec,
            OpenBindPlan plan) {
        LinkedHashMap<String, ContainerDataSource> sources = new LinkedHashMap<>();
        if (templateSpec == null) return sources;

        for (TemplateSpec.ContainerSpec containerSpec : templateSpec.containers()) {
            if (containerSpec == null) continue;
            String containerId = containerSpec.id();
            ContainerBindType bindType = resolveBindType(containerSpec, plan);
            if (bindType == ContainerBindType.PLAYER) continue;
            if (bindType == ContainerBindType.VIRTUAL_UI) continue;

            int requiredSlotCount = resolveRequiredSlotCount(containerSpec, plan);
            if (requiredSlotCount <= 0) continue;

            Map<String, String> args = resolveArgs(plan, containerId);
            OpenBindPlan.ResizePolicy resizePolicy = resolveResizePolicy(plan, containerId);
            try {
                ContainerDataSource dataSource = Container.resolveBinding(
                        player,
                        containerId,
                        bindType,
                        args,
                        requiredSlotCount,
                        resizePolicy
                );
                if (dataSource == null) {
                    ApricityUI.LOGGER.warn(
                            "Open container failed: bindType={} / container={} / reason=UNRESOLVED_BINDING",
                            bindType.id(),
                            containerId
                    );
                    return null;
                }
                sources.put(containerId, dataSource);
            } catch (Exception exception) {
                ApricityUI.LOGGER.warn("Open container failed: bindType={} / container={} / reason={}",
                        bindType.id(), containerId, exception.getMessage());
                return null;
            }
        }

        return sources;
    }

    private static Map<String, String> resolveArgs(OpenBindPlan plan, String containerId) {
        if (plan == null) return Map.of();
        OpenBindPlan.ContainerOverride override = plan.container(containerId);
        if (override != null && override.bind() != null) {
            return override.bind().args();
        }
        return Map.of();
    }

    private static int resolveRequiredSlotCount(TemplateSpec.ContainerSpec containerSpec, OpenBindPlan plan) {
        if (containerSpec == null) return 0;
        int required = Math.max(0, containerSpec.requiredCapacity());

        OpenBindPlan.ContainerOverride override = plan == null ? null : plan.container(containerSpec.id());
        if (override != null && override.capacity() != null) {
            OpenBindPlan.CapacityOverride capacityOverride = override.capacity();
            if (capacityOverride.exactCapacity() != null) {
                required = Math.max(0, capacityOverride.exactCapacity());
            } else if (capacityOverride.minCapacity() != null) {
                required = Math.max(required, capacityOverride.minCapacity());
            }
        }
        return Math.max(0, required);
    }

    private static OpenBindPlan.ResizePolicy resolveResizePolicy(OpenBindPlan plan, String containerId) {
        if (plan == null) return OpenBindPlan.ResizePolicy.KEEP_OVERFLOW;
        OpenBindPlan.ContainerOverride override = plan.container(containerId);
        if (override != null && override.capacity() != null && override.capacity().resizePolicy() != null) {
            return override.capacity().resizePolicy();
        }
        if (plan.options() != null && plan.options().defaultResizePolicy() != null) {
            return plan.options().defaultResizePolicy();
        }
        return OpenBindPlan.ResizePolicy.KEEP_OVERFLOW;
    }

    private static String resolvePrimaryContainerId(OpenBindPlan plan, TemplateSpec templateSpec) {
        if (templateSpec == null) return "";

        String override = plan == null ? "" : normalizeContainerId(plan.primaryContainerIdOverride());
        if (override != null && templateSpec.findContainer(override) != null) {
            return override;
        }

        String primaryFromTemplate = normalizeContainerId(templateSpec.primaryContainerId());
        if (primaryFromTemplate != null && templateSpec.findContainer(primaryFromTemplate) != null) {
            return primaryFromTemplate;
        }

        if (!templateSpec.containers().isEmpty()) {
            return templateSpec.containers().get(0).id();
        }
        return "";
    }

    private static MenuLayoutSpec buildLayoutSpec(
            TemplateSpec templateSpec,
            OpenBindPlan plan,
            Map<String, ContainerDataSource> containerSources) {
        if (templateSpec == null) return null;
        if (templateSpec.containers().isEmpty()) {
            return MenuLayoutSpec.createUiOnly(templateSpec.templatePath());
        }

        String primaryContainerId = resolvePrimaryContainerId(plan, templateSpec);
        LinkedHashMap<String, Integer> requiredCapacityById = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> customBaseById = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> customCapacityById = new LinkedHashMap<>();

        int customCursor = 0;
        int playerPoolCapacity = 0;
        for (TemplateSpec.ContainerSpec containerSpec : templateSpec.containers()) {
            if (containerSpec == null) continue;
            String containerId = containerSpec.id();
            ContainerBindType bindType = resolveBindType(containerSpec, plan);
            int requiredCapacity = resolveRequiredSlotCount(containerSpec, plan);
            requiredCapacityById.put(containerId, requiredCapacity);

            if (bindType == ContainerBindType.PLAYER) {
                playerPoolCapacity = Math.max(
                        playerPoolCapacity,
                        Math.min(ContainerBindType.PLAYER_SLOT_COUNT, requiredCapacity)
                );
                continue;
            }

            int resolvedCapacity = requiredCapacity;
            if (containerSources != null) {
                ContainerDataSource source = containerSources.get(containerId);
                if (source != null) {
                    resolvedCapacity = Math.max(resolvedCapacity, source.capacity());
                }
            }
            customBaseById.put(containerId, customCursor);
            customCapacityById.put(containerId, Math.max(0, resolvedCapacity));
            customCursor += Math.max(0, resolvedCapacity);
        }

        int playerBaseIndex = customCursor;
        ArrayList<MenuLayoutSpec.ContainerLayout> layouts = new ArrayList<>(templateSpec.containers().size());
        for (TemplateSpec.ContainerSpec containerSpec : templateSpec.containers()) {
            if (containerSpec == null) continue;

            String containerId = containerSpec.id();
            ContainerBindType bindType = resolveBindType(containerSpec, plan);
            boolean primary = containerId.equals(primaryContainerId);

            if (bindType == ContainerBindType.PLAYER) {
                int requiredCapacity = requiredCapacityById.getOrDefault(containerId, 0);
                int capacity = Math.min(playerPoolCapacity, Math.max(0, requiredCapacity));
                layouts.add(new MenuLayoutSpec.ContainerLayout(containerId, bindType, playerBaseIndex, capacity, primary));
                continue;
            }

            int baseIndex = customBaseById.getOrDefault(containerId, 0);
            int capacity = customCapacityById.getOrDefault(containerId, 0);
            layouts.add(new MenuLayoutSpec.ContainerLayout(containerId, bindType, baseIndex, capacity, primary));
        }

        return new MenuLayoutSpec(templateSpec.templatePath(), layouts);
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             MenuLayoutSpec layoutSpec,
                                             Map<String, ContainerDataSource> containerSources,
                                             String titleLiteral) {
        if (player == null || layoutSpec == null) return;
        Component titleComponent = (titleLiteral == null || titleLiteral.isBlank())
                ? Component.empty()
                : Component.literal(titleLiteral);

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (menuContainerId, playerInventory, ignoredPlayer) -> new ApricityContainerMenu(
                        menuContainerId,
                        playerInventory,
                        layoutSpec,
                        containerSources,
                        player),
                titleComponent
        ), layoutSpec::write);
    }

    private static String resolvePrimaryContainerTitleLiteral(TemplateSpec templateSpec, String primaryContainerId) {
        if (templateSpec == null) return null;
        String normalizedPrimaryId = normalizeContainerId(primaryContainerId);
        if (normalizedPrimaryId == null) return null;
        TemplateSpec.ContainerSpec primaryContainer = templateSpec.findContainer(normalizedPrimaryId);
        if (primaryContainer == null) return null;
        String title = primaryContainer.title();
        if (title == null || title.isBlank()) return null;
        return title.trim();
    }

    private static String normalizeTemplatePath(String rawTemplatePath) {
        if (rawTemplatePath == null) return null;
        String path = rawTemplatePath.trim().replace('\\', '/');
        if (path.isEmpty()) return null;

        if (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("apricity/")) path = path.substring("apricity/".length());
        if (path.contains("..")) return null;
        if (!path.endsWith(".html")) return null;

        String[] segments = path.split("/");
        if (segments.length < 2) return null;
        return path;
    }

    private static String normalizeContainerId(String containerId) {
        if (containerId == null) return null;
        String normalized = containerId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
