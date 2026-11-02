package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.util.common.NormalizeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 容器 DOM 元素，仅负责视图层职责。
 * 服务端数据源解析逻辑已迁移到 DataSourceFactory。
 */
@ElementRegister(Container.TAG_NAME)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    public Container(Document document) {
        super(document, TAG_NAME);
    }

    /**
     * 从已解析的 Document 中提取容器声明列表。
     * 用于客户端解析模板后，将容器信息随网络包发送到服务端。
     */
    public static List<ContainerDeclaration> extractDeclarations(Document document) {
        if (document == null) return List.of();

        ArrayList<ContainerDeclaration> declarations = new ArrayList<>();
        int[] topLevelIndex = {0};
        collectDeclarations(document, declarations, topLevelIndex);
        return List.copyOf(declarations);
    }

    private static void collectDeclarations(Document document, List<ContainerDeclaration> declarations, int[] topLevelIndex) {
        for (Element element : document.getElements()) {
            if (!(element instanceof Container container)) continue;
            // 只收集顶层容器（不嵌套在其他容器内）
            if (container.findAncestor(Container.class) != null) continue;

            String rawId = container.getAttribute("id");
            String containerId = resolveContainerId(rawId, topLevelIndex[0]);
            String rawBind = container.getAttribute("bind");
            ContainerBindType bindType = resolveBindType(rawBind);
            boolean primary = parseBooleanLike(container.getAttribute("primary"));
            int capacity = resolveCapacity(document, container);

            declarations.add(new ContainerDeclaration(containerId, bindType, capacity, primary));
            topLevelIndex[0]++;
        }
    }

    private static int resolveCapacity(Document document, Container container) {
        // 优先使用 size 属性声明
        int declaredSize = parsePositiveInt(container.getAttribute("size"), 0);

        // 统计子 Slot 数量
        int maxSlotIndex = -1;
        int nextImplicit = 0;
        for (Element element : document.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            if (slot.findAncestor(Recipe.class) != null) continue;
            Container owner = slot.findAncestor(Container.class);
            if (owner != container) continue;

            int repeat = Math.max(1, slot.getRepeatCount());
            int parsedIndex = slot.getSlotIndex();
            int start = parsedIndex < 0 ? nextImplicit : parsedIndex;
            int endIndex = start + repeat - 1;
            if (endIndex > maxSlotIndex) maxSlotIndex = endIndex;
            int candidate = start + repeat;
            if (candidate > nextImplicit) nextImplicit = candidate;
        }

        int slotDerivedCapacity = maxSlotIndex + 1;

        // 玩家容器默认 36 槽
        ContainerBindType bindType = resolveBindType(container.getAttribute("bind"));
        int playerCapacity = bindType == ContainerBindType.PLAYER ? ContainerBindType.PLAYER_SLOT_COUNT : 0;

        return Math.max(Math.max(declaredSize, slotDerivedCapacity), playerCapacity);
    }

    private static String resolveContainerId(String rawId, int index) {
        String normalized = NormalizeUtil.normalizeContainerId(rawId);
        if (normalized != null && !normalized.isBlank()) {
            if (normalized.matches("^[a-z0-9_./-]+$")) return normalized;
        }
        return "c" + Math.max(0, index);
    }

    private static ContainerBindType resolveBindType(String rawBindType) {
        if (rawBindType == null || rawBindType.isBlank()) return ContainerBindType.PLAYER;
        ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
        return bindType != null ? bindType : ContainerBindType.PLAYER;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return Math.max(0, fallback);
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : Math.max(0, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }

    private static boolean parseBooleanLike(String raw) {
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> true;
            default -> false;
        };
    }

    /**
     * 解析容器的槽位像素尺寸。
     */
    public int resolveSlotSizePx(int fallback) {
        int safeFallback = Math.max(1, fallback);
        String rawSlotSize = getAttribute("slot-size");
        int parsedSize = com.sighs.apricityui.layout.Size.parse(rawSlotSize);
        return parsedSize > 0 ? parsedSize : safeFallback;
    }

}
