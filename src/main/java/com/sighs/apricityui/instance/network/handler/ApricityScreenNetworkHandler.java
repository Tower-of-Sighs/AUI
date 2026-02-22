package com.sighs.apricityui.instance.network.handler;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.container.bind.ApricityDataSourceResolver;
import com.sighs.apricityui.instance.container.bind.ApricityMenuSlotSource;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.LinkedHashMap;
import java.util.List;
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

        ContainerSchema.OpenRequest request = ContainerSchema.OpenRequest.fromRaw(path);
        if (request == null) return;

        ContainerSchema.Descriptor analyzedDescriptor = ContainerSchema.TemplateAnalyzer.analyzeTemplate(request.templatePath());
        if (analyzedDescriptor == null) return;

        ContainerSchema.Descriptor boundDescriptor = applyBindPlan(analyzedDescriptor, plan);
        if (boundDescriptor == null) return;

        Map<String, ApricityMenuSlotSource> containerSources = resolveContainerSources(player, boundDescriptor, plan);
        if (containerSources == null) return;

        openScreenFromServer(player, boundDescriptor, containerSources);
    }

    public static void handleOpenScreenRequest(OpenScreenRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ContainerSchema.OpenRequest request = ContainerSchema.OpenRequest.fromRaw(packet.templatePath());
            if (request == null) return;

            ContainerSchema.Descriptor descriptor = ContainerSchema.TemplateAnalyzer.analyzeTemplate(request.templatePath());
            if (descriptor == null) return;

            Map<String, ApricityMenuSlotSource> containerSources = resolveContainerSources(player, descriptor, null);
            if (containerSources == null) return;

            openScreenFromServer(player, descriptor, containerSources);
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

    private static ContainerSchema.Descriptor applyBindPlan(ContainerSchema.Descriptor descriptor, OpenBindPlan plan) {
        if (descriptor == null || plan == null) return descriptor;

        LinkedHashMap<String, ContainerSchema.Descriptor.BindType> bindMapping = new LinkedHashMap<>(descriptor.getBindMapping());
        List<String> containerIds = descriptor.getContainerIds();
        String primaryContainerId = descriptor.getPrimaryPartitionKey();

        OpenBindPlan.BindingSpec primaryBinding = plan.primaryBinding();
        if (primaryBinding != null && primaryContainerId != null && bindMapping.containsKey(primaryContainerId)) {
            bindMapping.put(primaryContainerId, primaryBinding.bindType());
        }

        plan.indexBindings().forEach((index, bindingSpec) -> {
            if (index == null || bindingSpec == null) return;
            if (index < 0 || index >= containerIds.size()) return;
            bindMapping.put(containerIds.get(index), bindingSpec.bindType());
        });

        return ContainerSchema.Descriptor.createSanitized(
                descriptor.getMenuKey(),
                descriptor.getTemplatePath(),
                descriptor.getPrimaryPartitionKey(),
                bindMapping,
                descriptor.getSlotMapping(),
                descriptor.getSlotVisualMapping());
    }

    private static Map<String, ApricityMenuSlotSource> resolveContainerSources(ServerPlayer player,
                                                                               ContainerSchema.Descriptor descriptor,
                                                                               OpenBindPlan plan) {
        LinkedHashMap<String, ApricityMenuSlotSource> sources = new LinkedHashMap<>();
        List<String> containerIds = descriptor.getContainerIds();
        String primaryContainerId = descriptor.getPrimaryPartitionKey();

        for (int index = 0; index < containerIds.size(); index++) {
            String containerId = containerIds.get(index);
            ContainerSchema.Descriptor.BindType bindType = descriptor.getContainerBindType(containerId);
            if (ContainerSchema.Descriptor.isPlayerBind(bindType)) continue;
            if (ContainerSchema.Descriptor.isVirtualUiBind(bindType)) continue;

            int requiredSlotCount = ContainerSchema.Descriptor.requiredPoolSize(descriptor.getContainerSlots(containerId));
            if (requiredSlotCount <= 0) continue;

            Map<String, String> args = resolveArgs(plan, containerId, primaryContainerId, index);
            try {
                ApricityMenuSlotSource source = ApricityDataSourceResolver.resolve(player, bindType, args, requiredSlotCount);
                if (source == null) return null;
                sources.put(containerId, source);
            } catch (Exception exception) {
                ApricityUI.LOGGER.warn("Open container failed: bindType={} / container={} / reason={}",
                        bindType == null ? "<null>" : bindType.id(), containerId, exception.getMessage());
                return null;
            }
        }

        return sources;
    }

    private static Map<String, String> resolveArgs(OpenBindPlan plan,
                                                   String containerId,
                                                   String primaryContainerId,
                                                   int containerIndex) {
        if (plan == null) return Map.of();

        OpenBindPlan.BindingSpec indexedBinding = plan.bindingForIndex(containerIndex);
        if (indexedBinding != null) return indexedBinding.args();

        OpenBindPlan.BindingSpec primaryBinding = plan.primaryBinding();
        if (primaryBinding != null && containerId != null && containerId.equals(primaryContainerId)) {
            return primaryBinding.args();
        }

        return Map.of();
    }

    private static void openScreenFromServer(ServerPlayer player,
                                             ContainerSchema.Descriptor descriptor,
                                             Map<String, ApricityMenuSlotSource> containerSources) {
        if (player == null || descriptor == null) return;

        String titleLiteral = ContainerSchema.TemplateAnalyzer.resolvePrimaryContainerTitleLiteral(descriptor.getTemplatePath());
        Component titleComponent = (titleLiteral == null || titleLiteral.isBlank())
                ? Component.empty()
                : Component.literal(titleLiteral);

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (menuContainerId, playerInventory, ignoredPlayer) -> new ApricityContainerMenu(
                        menuContainerId,
                        playerInventory,
                        descriptor,
                        containerSources,
                        player),
                titleComponent
        ), descriptor::write);
    }
}
