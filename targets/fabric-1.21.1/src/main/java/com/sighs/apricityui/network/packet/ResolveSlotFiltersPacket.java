package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-resolved DOM selector matches for one open container menu.
 * Filter rules themselves remain server-only.
 */
public record ResolveSlotFiltersPacket(int menuId, Map<String, Map<String, List<Integer>>> localIndicesBySelector)
        implements CustomPacketPayload {
    private static final int MAX_CONTAINERS = 32;
    private static final int MAX_SELECTORS_PER_CONTAINER = 64;
    private static final int MAX_INDICES_PER_SELECTOR = 256;
    private static final int MAX_TOKEN_LENGTH = 512;

    public static final Type<ResolveSlotFiltersPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ApricityUI.MODID, "resolve_slot_filters"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResolveSlotFiltersPacket> STREAM_CODEC =
            StreamCodec.of(ResolveSlotFiltersPacket::encode, ResolveSlotFiltersPacket::decode);

    public ResolveSlotFiltersPacket {
        localIndicesBySelector = copy(localIndicesBySelector);
    }

    public static void encode(ByteBuf buf, ResolveSlotFiltersPacket packet) {
        FriendlyByteBuf friendly = (FriendlyByteBuf) buf;
        friendly.writeVarInt(Math.max(0, packet.menuId));
        friendly.writeVarInt(packet.localIndicesBySelector.size());
        for (Map.Entry<String, Map<String, List<Integer>>> container : packet.localIndicesBySelector.entrySet()) {
            friendly.writeUtf(container.getKey());
            friendly.writeVarInt(container.getValue().size());
            for (Map.Entry<String, List<Integer>> selector : container.getValue().entrySet()) {
                friendly.writeUtf(selector.getKey());
                friendly.writeVarInt(selector.getValue().size());
                for (Integer localIndex : selector.getValue()) friendly.writeVarInt(Math.max(0, localIndex));
            }
        }
    }

    public static ResolveSlotFiltersPacket decode(ByteBuf buf) {
        FriendlyByteBuf friendly = (FriendlyByteBuf) buf;
        int menuId = friendly.readVarInt();
        int containerCount = boundedCount(friendly.readVarInt(), MAX_CONTAINERS, "container");
        LinkedHashMap<String, Map<String, List<Integer>>> resolved = new LinkedHashMap<>();
        for (int containerIndex = 0; containerIndex < containerCount; containerIndex++) {
            String containerId = friendly.readUtf(MAX_TOKEN_LENGTH);
            int selectorCount = boundedCount(friendly.readVarInt(), MAX_SELECTORS_PER_CONTAINER, "selector");
            LinkedHashMap<String, List<Integer>> selectors = new LinkedHashMap<>();
            for (int selectorIndex = 0; selectorIndex < selectorCount; selectorIndex++) {
                String selector = friendly.readUtf(MAX_TOKEN_LENGTH);
                int indexCount = boundedCount(friendly.readVarInt(), MAX_INDICES_PER_SELECTOR, "index");
                ArrayList<Integer> localIndices = new ArrayList<>(indexCount);
                for (int index = 0; index < indexCount; index++) localIndices.add(friendly.readVarInt());
                selectors.put(selector, localIndices);
            }
            resolved.put(containerId, selectors);
        }
        return new ResolveSlotFiltersPacket(menuId, resolved);
    }

    private static int boundedCount(int value, int limit, String field) {
        if (value < 0 || value > limit) {
            throw new IllegalArgumentException("Too many slot-filter " + field + " entries: " + value);
        }
        return value;
    }

    private static Map<String, Map<String, List<Integer>>> copy(Map<String, Map<String, List<Integer>>> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        LinkedHashMap<String, Map<String, List<Integer>>> result = new LinkedHashMap<>();
        raw.forEach((containerId, selectors) -> {
            if (containerId == null || containerId.isBlank() || selectors == null || selectors.isEmpty()) return;
            LinkedHashMap<String, List<Integer>> selectorCopy = new LinkedHashMap<>();
            selectors.forEach((selector, indices) -> {
                if (selector == null || selector.isBlank() || indices == null || indices.isEmpty()) return;
                selectorCopy.put(selector, List.copyOf(indices));
            });
            if (!selectorCopy.isEmpty()) result.put(containerId, Map.copyOf(selectorCopy));
        });
        return Map.copyOf(result);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
