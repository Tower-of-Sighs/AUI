package com.sighs.apricityui.instance.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.container.bind.BindRequest;
import com.sighs.apricityui.instance.container.bind.BindResolver;
import com.sighs.apricityui.instance.container.bind.BindResult;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.layout.MenuLayoutSpec;
import com.sighs.apricityui.instance.container.template.TemplateCompiler;
import com.sighs.apricityui.instance.container.template.TemplateSpec;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.resource.HTML;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApricityScreenNetworkHandler {
    private static final Pattern TAG_PATTERN =
            Pattern.compile("<!--.*?-->|</?[a-zA-Z][a-zA-Z0-9:-]*(?:\\s+[^<>]*?)?/?>", Pattern.DOTALL);
    private static final Pattern TAG_NAME_PATTERN =
            Pattern.compile("<\\s*([a-zA-Z][a-zA-Z0-9:-]*)");
    private static final Pattern ATTR_PATTERN =
            Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:\\-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");

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

        openScreenFromServer(player, layoutSpec, containerSources);
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

            openScreenFromServer(player, layoutSpec, containerSources);
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
                    containerSpec.declaredSize(),
                    containerSpec.explicitIndices()
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
                BindRequest request = new BindRequest(
                        player,
                        containerId,
                        bindType,
                        args,
                        requiredSlotCount,
                        resizePolicy
                );
                BindResult result = BindResolver.resolve(request);
                if (!result.isSuccess()) {
                    ApricityUI.LOGGER.warn(
                            "Open container failed: bindType={} / container={} / reason={} / detail={}",
                            bindType.id(),
                            containerId,
                            result.failureReason(),
                            result.failureDetail()
                    );
                    return null;
                }
                sources.put(containerId, result.dataSource());
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
                                             Map<String, ContainerDataSource> containerSources) {
        if (player == null || layoutSpec == null) return;

        String titleLiteral = resolvePrimaryContainerTitleLiteral(layoutSpec.templatePath(), layoutSpec.primaryContainerId());
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

    private static String resolvePrimaryContainerTitleLiteral(String rawTemplatePath, String primaryContainerId) {
        String templatePath = normalizeTemplatePath(rawTemplatePath);
        if (templatePath == null) return null;

        String rawHtml = HTML.getTemple(templatePath);
        if (rawHtml == null || rawHtml.isBlank()) return null;

        String normalizedPrimaryId = normalizeContainerId(primaryContainerId);
        String firstTopLevelTitle = null;
        ArrayDeque<Boolean> containerDepth = new ArrayDeque<>();
        Matcher matcher = TAG_PATTERN.matcher(rawHtml);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("<!--")) continue;

            boolean closingTag = token.startsWith("</");
            String tagName = extractTagName(token);
            if (!"container".equals(tagName)) continue;

            if (closingTag) {
                if (!containerDepth.isEmpty()) {
                    containerDepth.pop();
                }
                continue;
            }

            boolean topLevel = containerDepth.isEmpty();
            Map<String, String> attributes = parseAttributes(token);
            if (topLevel) {
                String title = attributes.get("title");
                if (title != null && !title.isBlank()) {
                    if (firstTopLevelTitle == null) {
                        firstTopLevelTitle = title.trim();
                    }
                    String containerId = normalizeContainerId(attributes.get("id"));
                    if (normalizedPrimaryId != null && normalizedPrimaryId.equals(containerId)) {
                        return title.trim();
                    }
                }
            }

            containerDepth.push(Boolean.TRUE);
            if (token.endsWith("/>") && !containerDepth.isEmpty()) {
                containerDepth.pop();
            }
        }

        if (normalizedPrimaryId == null || normalizedPrimaryId.isBlank()) {
            return firstTopLevelTitle;
        }
        return null;
    }

    private static String extractTagName(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.startsWith("</")) {
            String middle = token.substring(2, token.length() - 1).trim();
            int split = middle.indexOf(' ');
            String rawTag = split < 0 ? middle : middle.substring(0, split);
            return rawTag.toLowerCase(Locale.ROOT);
        }
        Matcher matcher = TAG_NAME_PATTERN.matcher(token);
        if (!matcher.find()) return "";
        String rawTag = matcher.group(1);
        if (rawTag == null || rawTag.isBlank()) return "";
        return rawTag.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> parseAttributes(String token) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        if (token == null || token.isBlank()) return attributes;

        Matcher nameMatcher = TAG_NAME_PATTERN.matcher(token);
        if (!nameMatcher.find()) return attributes;
        int start = nameMatcher.end();
        int end = token.endsWith("/>") ? token.length() - 2 : token.length() - 1;
        if (end < start) return attributes;

        String attrsPart = token.substring(start, end);
        Matcher matcher = ATTR_PATTERN.matcher(attrsPart);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || key.isBlank()) continue;
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            String value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            if (value == null) value = matcher.group(5);
            if (value == null) value = "";
            attributes.put(normalizedKey, value.trim());
        }
        return attributes;
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
