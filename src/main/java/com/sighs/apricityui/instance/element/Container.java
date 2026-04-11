package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.datasource.NeoForgeItemHandlerDataSource;
import com.sighs.apricityui.instance.container.datasource.PlayerInventoryDataSource;
import com.sighs.apricityui.instance.container.datasource.SavedDataDataSource;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import java.util.*;

@ElementRegister(Container.TAG_NAME)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    public Container(Document document) {
        super(document, TAG_NAME);
    }

    public int resolveSlotSizePx(int fallback) {
        int safeFallback = Math.max(1, fallback);
        String rawSlotSize = getAttribute("slot-size");
        int parsedSize = Size.parse(rawSlotSize);
        return parsedSize > 0 ? parsedSize : safeFallback;
    }

    public static TemplateSpec compileTemplate(String rawTemplatePath) {
        String templatePath = NormalizeUtil.normalizeTemplatePath(rawTemplatePath);
        if (templatePath == null) return null;

        Element root = parseTemplateRoot(templatePath);
        if (root == null) {
            ApricityUI.LOGGER.warn("Template compile failed: HTML content not found, path={}", templatePath);
            return null;
        }

        List<ContainerDraft> drafts = parseContainerDrafts(root);
        if (drafts.isEmpty()) {
            return new TemplateSpec(templatePath, "", List.of());
        }

        String primaryContainerId = resolveTemplatePrimaryContainerId(drafts);
        ArrayList<TemplateSpec.ContainerSpec> containers = new ArrayList<>(drafts.size());
        for (ContainerDraft draft : drafts) {
            int declaredCapacity = draft.maxSlotIndex() + 1;
            int playerCapacity = draft.bindType() == ContainerBindType.PLAYER
                    ? ContainerBindType.PLAYER_SLOT_COUNT
                    : 0;
            int requiredCapacity = Math.max(Math.max(draft.declaredSize(), declaredCapacity), playerCapacity);
            containers.add(new TemplateSpec.ContainerSpec(
                    draft.id(),
                    draft.bindType(),
                    draft.id().equals(primaryContainerId),
                    requiredCapacity,
                    draft.title()
            ));
        }

        return new TemplateSpec(templatePath, primaryContainerId, containers);
    }

    public static boolean hasBindingDataSource(ContainerBindType bindType) {
        return bindType != null && bindType != ContainerBindType.VIRTUAL_UI;
    }

    public static ContainerDataSource resolveBinding(
            ServerPlayer player,
            String containerId,
            ContainerBindType bindType,
            Map<String, String> args,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy
    ) {
        if (player == null) {
            return null;
        }
        if (bindType == null) {
            return null;
        }
        if (!hasBindingDataSource(bindType)) {
            return null;
        }

        String normalizedContainerId = containerId == null ? "" : containerId.trim();
        int normalizedRequiredCapacity = Math.max(0, requiredCapacity);
        OpenBindPlan.ResizePolicy normalizedResizePolicy = resizePolicy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : resizePolicy;

        LinkedHashMap<String, String> normalizedArgs = new LinkedHashMap<>();
        if (args != null) {
            args.forEach((key, value) -> {
                if (key == null) return;
                String normalizedKey = key.trim();
                if (normalizedKey.isEmpty()) return;
                normalizedArgs.put(normalizedKey, value == null ? "" : value);
            });
        }
        Map<String, String> safeArgs = Map.copyOf(normalizedArgs);

        try {
            ContainerDataSource dataSource = switch (bindType) {
                case PLAYER -> new PlayerInventoryDataSource(player);
                case SAVED_DATA -> resolveSavedData(
                        player,
                        bindType,
                        safeArgs,
                        normalizedRequiredCapacity,
                        normalizedResizePolicy
                );
                case BLOCK_ENTITY -> resolveBlockEntity(player, bindType, safeArgs);
                case ENTITY -> resolveEntity(player, bindType, safeArgs);
                case VIRTUAL_UI -> null;
            };
            if (dataSource == null) {
                return null;
            }
            return ensureCapacity(
                    normalizedContainerId,
                    bindType,
                    normalizedRequiredCapacity,
                    normalizedResizePolicy,
                    dataSource
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private static ContainerDataSource ensureCapacity(
            String containerId,
            ContainerBindType bindType,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy,
            ContainerDataSource dataSource
    ) {
        if (requiredCapacity <= 0) return dataSource;

        int capacity = dataSource.capacity();
        if (capacity >= requiredCapacity) {
            return dataSource;
        }

        if (dataSource.supportsResize()) {
            int resizedCapacity = dataSource.resize(requiredCapacity, resizePolicy);
            if (resizedCapacity >= requiredCapacity) {
                return dataSource;
            }
            ApricityUI.LOGGER.warn(
                    "Bind resolve failed: container={} bindType={} reason={} detail={}",
                    containerId,
                    bindType.id(),
                    "INSUFFICIENT_CAPACITY",
                    "resize failed: required=" + requiredCapacity + ", actual=" + resizedCapacity
            );
            return null;
        }

        ApricityUI.LOGGER.warn(
                "Bind resolve failed: container={} bindType={} reason={} detail={}",
                containerId,
                bindType.id(),
                "INSUFFICIENT_CAPACITY",
                "required=" + requiredCapacity + ", actual=" + capacity
        );
        return null;
    }

    private static ContainerDataSource resolveSavedData(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy
    ) {
        String dataName = normalizeText(args.get("dataName"), "apricityui_saved");
        String inventoryKey = normalizeText(args.get("inventoryKey"), player.getStringUUID());
        int declaredSlotCount = parsePositiveInt(args.get("slotCount"), Math.max(1, requiredCapacity));
        int initialCapacity = Math.max(1, Math.max(declaredSlotCount, requiredCapacity));

        ApricitySavedData savedData = ApricitySavedData.get(player.level().getServer(), dataName);
        ItemStacksResourceHandler handler = savedData.getOrCreate(inventoryKey, initialCapacity, resizePolicy);
        return new SavedDataDataSource(bindType, savedData, inventoryKey, handler);
    }

    private static ContainerDataSource resolveBlockEntity(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args
    ) {
        Integer x = parseRequiredInt(args, "x");
        Integer y = parseRequiredInt(args, "y");
        Integer z = parseRequiredInt(args, "z");
        if (x == null || y == null || z == null) {
            return null;
        }

        Direction side = parseDirection(args.get("side"));
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = player.level();
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null || handler.size() <= 0) {
            return null;
        }

        Class<?> expectedType = blockEntity.getClass();
        BlockPos immutablePos = pos.immutable();
        return new NeoForgeItemHandlerDataSource(
                bindType,
                handler,
                currentPlayer -> {
                    if (currentPlayer == null) return false;
                    ServerLevel currentLevel = currentPlayer.level();
                    if (!currentLevel.hasChunkAt(immutablePos)) return false;
                    BlockEntity current = currentLevel.getBlockEntity(immutablePos);
                    return expectedType.isInstance(current);
                }
        );
    }

    private static ContainerDataSource resolveEntity(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args
    ) {
        UUID uuid = parseRequiredUuid(args, "uuid");
        if (uuid == null) return null;

        Entity target = findEntityByUuid(player, uuid);
        if (!(target instanceof LivingEntity livingEntity)) {
            return null;
        }
        ResourceHandler<ItemResource> handler = livingEntity.getCapability(Capabilities.Item.ENTITY);
        if (handler == null || handler.size() <= 0) {
            return null;
        }

        Class<?> expectedType = livingEntity.getClass();
        return new NeoForgeItemHandlerDataSource(
                bindType,
                handler,
                currentPlayer -> {
                    Entity current = findEntityByUuid(currentPlayer, uuid);
                    return expectedType.isInstance(current);
                }
        );
    }

    private static Entity findEntityByUuid(ServerPlayer player, UUID uuid) {
        if (player == null || uuid == null) return null;
        MinecraftServer server = player.level().getServer();
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static String normalizeText(String raw, String fallback) {
        if (raw == null) return fallback;
        String normalized = raw.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static Element parseTemplateRoot(String templatePath) {
        String rawHtml = HTML.getTemple(templatePath);
        if (rawHtml == null || rawHtml.isBlank()) return null;

        Document document = new Document(templatePath, false);
        Element root = HTML.create(document, templatePath);
        if (root == null) return null;
        return Element.init(root);
    }

    private static List<ContainerDraft> parseContainerDrafts(Element root) {
        ArrayList<ContainerDraft> drafts = new ArrayList<>();
        int[] topLevelIndex = {0};
        collectTemplateDrafts(root, null, drafts, topLevelIndex);
        return drafts;
    }

    private static void collectTemplateDrafts(Element element,
                                              ContainerDraft activeTopLevel,
                                              List<ContainerDraft> drafts,
                                              int[] topLevelIndex) {
        if (element == null) return;
        if (element instanceof ApricityRecipe) return;

        ContainerDraft currentTopLevel = activeTopLevel;
        if (element instanceof Container container && activeTopLevel == null) {
            currentTopLevel = createContainerDraft(container, topLevelIndex[0]);
            drafts.add(currentTopLevel);
            topLevelIndex[0]++;
        }

        if (element instanceof ApricitySlot slot && currentTopLevel != null) {
            currentTopLevel.consumeSlot(slot);
        }

        for (Element child : element.children) {
            collectTemplateDrafts(child, currentTopLevel, drafts, topLevelIndex);
        }
    }

    private static ContainerDraft createContainerDraft(Container container, int topLevelIndex) {
        String containerId = resolveTemplateContainerId(container.getAttribute("id"), topLevelIndex);
        boolean primary = parseBooleanLike(container.getAttribute("primary"));
        ContainerBindType bindType = resolveTemplateBindType(container.getAttribute("bind"), containerId);
        int declaredSize = parseTemplatePositiveInt(container.getAttribute("size"), 0);
        String title = container.getAttribute("title");
        return new ContainerDraft(containerId, bindType, primary, declaredSize, title);
    }

    private static String resolveTemplatePrimaryContainerId(List<ContainerDraft> drafts) {
        for (ContainerDraft draft : drafts) {
            if (draft.primary()) return draft.id();
        }
        return drafts.isEmpty() ? "" : drafts.getFirst().id();
    }

    private static String resolveTemplateContainerId(String rawId, int index) {
        String normalized = normalizeTemplateContainerId(rawId);
        if (normalized != null && !normalized.isBlank()) return normalized;
        return "c" + Math.max(0, index);
    }

    private static String normalizeTemplateContainerId(String rawContainerId) {
        String containerId = NormalizeUtil.normalizeContainerId(rawContainerId);
        if (containerId == null) return null;
        if (!containerId.matches("^[a-z0-9_./-]+$")) return null;
        return containerId;
    }

    private static boolean parseBooleanLike(String raw) {
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> true;
            default -> false;
        };
    }

    private static ContainerBindType resolveTemplateBindType(String rawBindType, String containerId) {
        if (rawBindType == null || rawBindType.isBlank()) {
            return ContainerBindType.PLAYER;
        }
        ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
        if (bindType != null) return bindType;
        ApricityUI.LOGGER.warn("Template compile: invalid bindType='{}', fallback player, container={}", rawBindType, containerId);
        return ContainerBindType.PLAYER;
    }

    private static int parseTemplatePositiveInt(String rawNumber, int fallback) {
        if (rawNumber == null || rawNumber.isBlank()) return Math.max(0, fallback);
        try {
            int parsed = Integer.parseInt(rawNumber.trim());
            return parsed > 0 ? parsed : Math.max(0, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return Math.max(1, fallback);
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : Math.max(1, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(1, fallback);
        }
    }

    private static Integer parseRequiredInt(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static UUID parseRequiredUuid(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Direction.byName(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static final class TemplateSpec {
        private final String templatePath;
        private final String primaryContainerId;
        private final List<ContainerSpec> containers;
        private final List<String> containerIds;
        private final Map<String, ContainerSpec> containersById;

        public TemplateSpec(String templatePath, String primaryContainerId, List<ContainerSpec> containers) {
            this.templatePath = templatePath == null ? "" : templatePath.trim();
            this.primaryContainerId = primaryContainerId == null ? "" : primaryContainerId.trim();

            ArrayList<ContainerSpec> normalizedContainers = new ArrayList<>();
            if (containers != null) {
                for (ContainerSpec container : containers) {
                    if (container == null) continue;
                    normalizedContainers.add(container);
                }
            }
            this.containers = List.copyOf(normalizedContainers);

            ArrayList<String> normalizedIds = new ArrayList<>(this.containers.size());
            LinkedHashMap<String, ContainerSpec> normalizedById = new LinkedHashMap<>();
            for (ContainerSpec container : this.containers) {
                normalizedIds.add(container.id());
                normalizedById.put(container.id(), container);
            }
            this.containerIds = List.copyOf(normalizedIds);
            this.containersById = Map.copyOf(normalizedById);
        }

        public String templatePath() {
            return templatePath;
        }

        public String primaryContainerId() {
            return primaryContainerId;
        }

        public List<ContainerSpec> containers() {
            return containers;
        }

        public List<String> containerIds() {
            return containerIds;
        }

        public ContainerSpec findContainer(String containerId) {
            String normalized = NormalizeUtil.normalizeContainerId(containerId);
            if (normalized == null) return null;
            return containersById.get(normalized);
        }

        public Map<String, ContainerSpec> containersById() {
            return containersById;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateSpec that)) return false;
            return templatePath.equals(that.templatePath)
                    && primaryContainerId.equals(that.primaryContainerId)
                    && containers.equals(that.containers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templatePath, primaryContainerId, containers);
        }

        @Override
        public String toString() {
            return "TemplateSpec[" +
                    "templatePath=" + templatePath +
                    ", primaryContainerId=" + primaryContainerId +
                    ", containers=" + containers +
                    ']';
        }

        public record ContainerSpec(
                String id,
                ContainerBindType bindType,
                boolean primary,
                int requiredCapacity,
                String title
        ) {
            public ContainerSpec {
                id = NormalizeUtil.normalizeContainerId(Objects.requireNonNull(id, "container id cannot be null"));
                if (id == null) {
                    throw new IllegalArgumentException("container id cannot be blank");
                }
                bindType = Objects.requireNonNull(bindType, "bindType cannot be null");
                requiredCapacity = Math.max(0, requiredCapacity);
                title = title == null ? "" : title.trim();
            }
        }
    }

    private static final class ContainerDraft {
        private final String id;
        private final ContainerBindType bindType;
        private final boolean primary;
        private final int declaredSize;
        private final String title;
        private int nextImplicitIndex = 0;
        private int maxSlotIndex = -1;

        private ContainerDraft(String id,
                               ContainerBindType bindType,
                               boolean primary,
                               int declaredSize,
                               String title) {
            this.id = id;
            this.bindType = bindType;
            this.primary = primary;
            this.declaredSize = Math.max(0, declaredSize);
            this.title = title == null ? "" : title.trim();
        }

        private String id() {
            return id;
        }

        private ContainerBindType bindType() {
            return bindType;
        }

        private boolean primary() {
            return primary;
        }

        private int declaredSize() {
            return declaredSize;
        }

        private String title() {
            return title;
        }

        private int maxSlotIndex() {
            return Math.max(-1, maxSlotIndex);
        }

        private void consumeSlot(ApricitySlot slot) {
            if (slot == null || bindType == ContainerBindType.VIRTUAL_UI) return;

            int repeat = Math.max(1, slot.getRepeatCount());
            int parsedSlotIndex = slot.getSlotIndex();
            int start = parsedSlotIndex < 0 ? nextImplicitIndex : parsedSlotIndex;
            int endIndex = start + repeat - 1;
            if (endIndex > maxSlotIndex) {
                maxSlotIndex = endIndex;
            }
            int candidate = start + repeat;
            if (candidate > nextImplicitIndex) {
                nextImplicitIndex = candidate;
            }
        }
    }
}
