package com.sighs.apricityui.instance.container.schema;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContainerSchema {
    public static final class Descriptor {
        public static final String DEFAULT_PARTITION_KEY = "c0";
        public static final int PLAYER_SLOT_COUNT = 36;
        @Getter
        private final String menuKey;
        @Getter
        private final String templatePath;
        @Getter
        private final String primaryPartitionKey;
        private final LinkedHashMap<String, BindType> bindMapping;
        private final LinkedHashMap<String, List<Integer>> slotMapping;
        private final LinkedHashMap<String, Map<Integer, SlotVisualProfile>> slotVisualMapping;

        public Descriptor(String menuKey, String templatePath, Map<String, List<Integer>> slotMapping) {
            this(menuKey, templatePath, resolveDefaultPrimaryPartitionKey(slotMapping), createDefaultBindMapping(slotMapping, BindType.PLAYER), slotMapping, new HashMap<>());
        }

        public Descriptor(String menuKey, String templatePath, Map<String, BindType> bindMapping, Map<String, List<Integer>> slotMapping) {
            this(menuKey, templatePath, resolveDefaultPrimaryPartitionKey(slotMapping), bindMapping, slotMapping, new HashMap<>());
        }

        public Descriptor(String menuKey, String templatePath, String primaryPartitionKey, Map<String, BindType> bindMapping, Map<String, List<Integer>> slotMapping) {
            this(menuKey, templatePath, primaryPartitionKey, bindMapping, slotMapping, new HashMap<>());
        }

        public Descriptor(String menuKey,
                          String templatePath,
                          String primaryPartitionKey,
                          Map<String, BindType> bindMapping,
                          Map<String, List<Integer>> slotMapping,
                          Map<String, Map<Integer, SlotVisualProfile>> slotVisualMapping) {
            this.menuKey = menuKey;
            this.templatePath = templatePath;
            this.bindMapping = new LinkedHashMap<>();
            this.slotMapping = new LinkedHashMap<>();
            this.slotVisualMapping = new LinkedHashMap<>();
            String resolvedPrimaryPartitionKey = primaryPartitionKey;
            if (slotMapping == null) slotMapping = new HashMap<>();
            if (bindMapping == null) bindMapping = new HashMap<>();
            if (slotVisualMapping == null) slotVisualMapping = new HashMap<>();
            Map<String, BindType> finalBindMapping = bindMapping;
            Map<String, Map<Integer, SlotVisualProfile>> finalSlotVisualMapping = slotVisualMapping;
            slotMapping.forEach((containerId, slots) -> {
                if (StringUtils.isNullOrEmptyEx(containerId)) return;
                ArrayList<Integer> safeSlots = new ArrayList<>();
                if (slots != null) for (Integer slot : slots) {
                    if (slot != null && slot >= 0) safeSlots.add(slot);
                }
                BindType bindType = finalBindMapping.get(containerId);
                if (bindType == null) bindType = BindType.PLAYER;
                this.bindMapping.put(containerId, bindType);
                this.slotMapping.put(containerId, Collections.unmodifiableList(safeSlots));

                Map<Integer, SlotVisualProfile> rawContainerVisuals = finalSlotVisualMapping.get(containerId);
                if (rawContainerVisuals == null || rawContainerVisuals.isEmpty()) return;
                LinkedHashMap<Integer, SlotVisualProfile> safeVisuals = new LinkedHashMap<>();
                for (Map.Entry<Integer, SlotVisualProfile> visualEntry : rawContainerVisuals.entrySet()) {
                    Integer localSlotIndex = visualEntry.getKey();
                    SlotVisualProfile profile = visualEntry.getValue();
                    if (localSlotIndex == null || localSlotIndex < 0 || profile == null) continue;
                    if (!safeSlots.contains(localSlotIndex)) continue;
                    safeVisuals.put(localSlotIndex, profile);
                }
                if (!safeVisuals.isEmpty()) {
                    this.slotVisualMapping.put(containerId, Collections.unmodifiableMap(safeVisuals));
                }
            });
            if (StringUtils.isNullOrEmptyEx(resolvedPrimaryPartitionKey) || !this.slotMapping.containsKey(resolvedPrimaryPartitionKey)) {
                resolvedPrimaryPartitionKey = resolveDefaultPrimaryPartitionKey(this.slotMapping);
            }
            this.primaryPartitionKey = resolvedPrimaryPartitionKey;
        }

        public static Descriptor read(PacketBuffer buf) {
            String menuKey = buf.readUtf();
            String templatePath = buf.readUtf();
            String primaryPartitionKey = buf.readUtf();
            int containerCount = buf.readVarInt();
            LinkedHashMap<String, BindType> binds = new LinkedHashMap<>();
            LinkedHashMap<String, List<Integer>> mapping = new LinkedHashMap<>();
            for (int i = 0; i < containerCount; i++) {
                String containerId = buf.readUtf();
                BindType bindType = BindType.fromRaw(buf.readUtf());
                if (bindType == null) {
                    ApricityUI.LOGGER.warn("Descriptor decode failed: invalid bindType, container={}", containerId);
                    return new Descriptor(menuKey, templatePath, new HashMap<>(), new HashMap<>());
                }
                int slotCount = buf.readVarInt();
                ArrayList<Integer> slots = new ArrayList<>(slotCount);
                for (int j = 0; j < slotCount; j++) {
                    slots.add(buf.readVarInt());
                }
                binds.put(containerId, bindType);
                mapping.put(containerId, slots);
            }
            LinkedHashMap<String, Map<Integer, SlotVisualProfile>> slotVisualMapping = new LinkedHashMap<>();
            // 仅新协议：视觉映射段为强制字段，客户端/服务端需同版本。
            int visualContainerCount = buf.readVarInt();
            for (int i = 0; i < visualContainerCount; i++) {
                String containerId = buf.readUtf();
                int visualCount = buf.readVarInt();
                LinkedHashMap<Integer, SlotVisualProfile> visuals = new LinkedHashMap<>();
                for (int j = 0; j < visualCount; j++) {
                    int localSlotIndex = buf.readVarInt();
                    SlotVisualProfile profile = SlotVisualProfile.read(buf);
                    if (profile != null) visuals.put(localSlotIndex, profile);
                }
                if (!visuals.isEmpty()) {
                    slotVisualMapping.put(containerId, visuals);
                }
            }

            Descriptor descriptor = createSanitized(menuKey, templatePath, primaryPartitionKey, binds, mapping, slotVisualMapping);
            if (descriptor != null) return descriptor;
            return new Descriptor(menuKey, templatePath, new HashMap<>(), new HashMap<>());
        }

        public static Descriptor createSanitized(String menuKey, String templatePath, Map<String, BindType> bindMapping, Map<String, List<Integer>> slotMapping) {
            return createSanitized(menuKey, templatePath, resolveDefaultPrimaryPartitionKey(slotMapping), bindMapping, slotMapping, new HashMap<>());
        }

        public static Descriptor createSanitized(String menuKey,
                                                 String templatePath,
                                                 Map<String, BindType> bindMapping,
                                                 Map<String, List<Integer>> slotMapping,
                                                 Map<String, Map<Integer, SlotVisualProfile>> slotVisualMapping) {
            return createSanitized(menuKey, templatePath, resolveDefaultPrimaryPartitionKey(slotMapping), bindMapping, slotMapping, slotVisualMapping);
        }

        public static Descriptor createSanitized(String menuKey, String templatePath, String primaryPartitionKey, Map<String, BindType> bindMapping, Map<String, List<Integer>> slotMapping) {
            return createSanitized(menuKey, templatePath, primaryPartitionKey, bindMapping, slotMapping, new HashMap<>());
        }

        public static Descriptor createSanitized(String menuKey,
                                                 String templatePath,
                                                 String primaryPartitionKey,
                                                 Map<String, BindType> bindMapping,
                                                 Map<String, List<Integer>> slotMapping,
                                                 Map<String, Map<Integer, SlotVisualProfile>> slotVisualMapping) {
            MappingSanitizer.SanitizedMapping sanitized = MappingSanitizer.sanitize(bindMapping, slotMapping);
            if (sanitized == null) return null;
            String normalizedPrimaryPartitionKey = MenuKeyResolver.normalizeContainerId(primaryPartitionKey);
            LinkedHashMap<String, Map<Integer, SlotVisualProfile>> sanitizedVisualMapping =
                    sanitizeSlotVisualMapping(sanitized.slotMapping(), slotVisualMapping);
            return new Descriptor(menuKey, templatePath, normalizedPrimaryPartitionKey, sanitized.bindMapping(), sanitized.slotMapping(), sanitizedVisualMapping);
        }

        public static Descriptor createPlayerInventory(String menuKey, String templatePath) {
            String partitionKey = DEFAULT_PARTITION_KEY;
            return new Descriptor(menuKey, templatePath,
                    partitionKey,
                    new HashMap<String, BindType>() {{
                        put(partitionKey, BindType.PLAYER);
                    }},
                    new HashMap<String, List<Integer>>() {{
                        put(partitionKey, createPlayerSlots());
                    }});
        }

        public static Descriptor createUiOnly(String templatePath) {
            return new Descriptor("apricityui:ui_only", templatePath, new HashMap<>());
        }

        public static List<Integer> createPlayerSlots() {
            ArrayList<Integer> slots = new ArrayList<>(PLAYER_SLOT_COUNT);
            for (int index = 0; index < PLAYER_SLOT_COUNT; index++) {
                slots.add(index);
            }
            return slots;
        }

        public static int requiredPoolSize(List<Integer> localSlots) {
            int max = -1;
            if (localSlots != null) {
                for (Integer localSlot : localSlots) {
                    if (localSlot == null) continue;
                    if (localSlot > max) max = localSlot;
                }
            }
            return Math.max(0, max + 1);
        }

        public static BindType resolveBindType(String rawBindType) {
            return BindType.fromRaw(rawBindType);
        }

        public static boolean isPlayerBind(BindType bindType) {
            return bindType == BindType.PLAYER;
        }

        public static boolean isEntityBind(BindType bindType) {
            return bindType == BindType.ENTITY;
        }

        public static boolean isBlockEntityBind(BindType bindType) {
            return bindType == BindType.BLOCK_ENTITY;
        }

        public static boolean isSavedDataBind(BindType bindType) {
            return bindType == BindType.SAVED_DATA;
        }

        public static boolean isVirtualUiBind(BindType bindType) {
            return bindType == BindType.VIRTUAL_UI;
        }

        private static LinkedHashMap<String, BindType> createDefaultBindMapping(Map<String, List<Integer>> slotMapping, BindType bindType) {
            LinkedHashMap<String, BindType> result = new LinkedHashMap<>();
            if (slotMapping == null) return result;
            slotMapping.forEach((containerId, slots) -> {
                if (StringUtils.isNullOrEmptyEx(containerId)) return;
                result.put(containerId, bindType);
            });
            return result;
        }

        private static String resolveDefaultPrimaryPartitionKey(Map<String, List<Integer>> slotMapping) {
            if (slotMapping == null || slotMapping.isEmpty()) return DEFAULT_PARTITION_KEY;
            for (String key : slotMapping.keySet()) {
                if (StringUtils.isNotNullOrEmptyEx(key)) return key;
            }
            return DEFAULT_PARTITION_KEY;
        }

        private static LinkedHashMap<String, Map<Integer, SlotVisualProfile>> sanitizeSlotVisualMapping(
                Map<String, List<Integer>> sanitizedSlotMapping,
                Map<String, Map<Integer, SlotVisualProfile>> rawSlotVisualMapping
        ) {
            LinkedHashMap<String, Map<Integer, SlotVisualProfile>> result = new LinkedHashMap<>();
            if (sanitizedSlotMapping == null || sanitizedSlotMapping.isEmpty()) return result;
            if (rawSlotVisualMapping == null || rawSlotVisualMapping.isEmpty()) return result;

            for (Map.Entry<String, List<Integer>> entry : sanitizedSlotMapping.entrySet()) {
                String containerId = entry.getKey();
                if (StringUtils.isNullOrEmptyEx(containerId)) continue;

                Map<Integer, SlotVisualProfile> rawVisuals = rawSlotVisualMapping.get(containerId);
                if (rawVisuals == null || rawVisuals.isEmpty()) continue;

                HashSet<Integer> validLocalSlots = new HashSet<>(entry.getValue());
                LinkedHashMap<Integer, SlotVisualProfile> sanitizedVisuals = new LinkedHashMap<>();
                for (Map.Entry<Integer, SlotVisualProfile> rawVisualEntry : rawVisuals.entrySet()) {
                    Integer localSlotIndex = rawVisualEntry.getKey();
                    SlotVisualProfile profile = rawVisualEntry.getValue();
                    if (localSlotIndex == null || localSlotIndex < 0 || profile == null) continue;
                    if (!validLocalSlots.contains(localSlotIndex)) continue;
                    sanitizedVisuals.put(localSlotIndex, profile);
                }
                if (!sanitizedVisuals.isEmpty()) {
                    result.put(containerId, sanitizedVisuals);
                }
            }
            return result;
        }

        public Map<String, BindType> getBindMapping() {
            return Collections.unmodifiableMap(bindMapping);
        }

        public BindType getContainerBindType(String containerId) {
            return bindMapping.get(containerId);
        }

        public Map<String, List<Integer>> getSlotMapping() {
            return Collections.unmodifiableMap(slotMapping);
        }

        public Map<String, Map<Integer, SlotVisualProfile>> getSlotVisualMapping() {
            return Collections.unmodifiableMap(slotVisualMapping);
        }

        public Map<Integer, SlotVisualProfile> getContainerSlotVisuals(String containerId) {
            Map<Integer, SlotVisualProfile> visualMapping = slotVisualMapping.get(containerId);
            if (visualMapping == null) return Collections.emptyMap();
            return visualMapping;
        }

        public SlotVisualProfile getContainerSlotVisual(String containerId, int localSlotIndex) {
            if (localSlotIndex < 0) return null;
            Map<Integer, SlotVisualProfile> visualMapping = slotVisualMapping.get(containerId);
            if (visualMapping == null) return null;
            return visualMapping.get(localSlotIndex);
        }

        public List<Integer> getContainerSlots(String containerId) {
            List<Integer> slots = slotMapping.get(containerId);
            if (slots == null) return Collections.emptyList();
            return slots;
        }

        public boolean hasContainer(String containerId) {
            return slotMapping.containsKey(containerId);
        }

        public List<String> getContainerIds() {
            return new ArrayList<>(slotMapping.keySet());
        }

        public boolean isUiOnly() {
            return slotMapping.isEmpty();
        }

        public void write(PacketBuffer buf) {
            buf.writeUtf(menuKey);
            buf.writeUtf(templatePath == null ? "" : templatePath);
            buf.writeUtf(primaryPartitionKey == null ? DEFAULT_PARTITION_KEY : primaryPartitionKey);
            buf.writeVarInt(slotMapping.size());
            slotMapping.forEach((containerId, slots) -> {
                buf.writeUtf(containerId);
                BindType bindType = getContainerBindType(containerId);
                buf.writeUtf(bindType == null ? "" : bindType.id());
                buf.writeVarInt(slots.size());
                for (Integer slot : slots) {
                    buf.writeVarInt(slot);
                }
            });
            // 仅新协议：始终写入视觉映射段，避免旧协议分支漂移。
            buf.writeVarInt(slotVisualMapping.size());
            slotVisualMapping.forEach((containerId, visuals) -> {
                buf.writeUtf(containerId);
                buf.writeVarInt(visuals.size());
                visuals.forEach((localSlotIndex, profile) -> {
                    buf.writeVarInt(localSlotIndex);
                    profile.write(buf);
                });
            });
        }

        public Descriptor withTemplatePath(String templatePathOverride) {
            String resolvedTemplatePath = StringUtils.isNullOrEmptyEx(templatePathOverride) ? templatePath : templatePathOverride;
            return new Descriptor(menuKey, resolvedTemplatePath, primaryPartitionKey, bindMapping, slotMapping, slotVisualMapping);
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class SlotVisualProfile {
            private Integer slotSize;
            private Boolean disabled;
            private Boolean acceptPointer;
            private Boolean renderBackground;
            private Boolean renderItem;
            private Float iconScale;
            private Integer padding;
            private Integer zIndex;
            private String extraClasses;

            @Getter
            @Accessors(fluent = true)
            @AllArgsConstructor
            public static class RenderRule {
                private boolean renderBackground;
                private boolean renderItem;
            }

            private static final int FLAG_SLOT_SIZE = 1;
            private static final int FLAG_DISABLED = 1 << 1;
            private static final int FLAG_POINTER = 1 << 2;
            private static final int FLAG_RENDER_BG = 1 << 3;
            private static final int FLAG_RENDER_ITEM = 1 << 4;
            private static final int FLAG_ICON_SCALE = 1 << 5;
            private static final int FLAG_PADDING = 1 << 6;
            private static final int FLAG_Z_INDEX = 1 << 7;
            private static final int FLAG_EXTRA_CLASSES = 1 << 8;

            public static SlotVisualProfile fromSlotAttributes(Map<String, String> attributes) {
                if (attributes == null || attributes.isEmpty()) return null;

                Integer slotSize = parsePositiveInt(firstNonBlank(attributes, "size", "slot-size"));
                Boolean disabled = parseBoolean(attributes.get("disabled"));
                Boolean acceptPointer = parsePointerFlag(firstNonBlank(
                        attributes,
                        "pointer",
                        "accept-pointer",
                        "acceptpointer",
                        "hit"
                ));
                RenderRule renderRule = parseRenderRule(firstNonBlank(attributes, "render"));
                Boolean renderBackground = renderRule == null ? null : renderRule.renderBackground();
                Boolean renderItem = renderRule == null ? null : renderRule.renderItem();
                Float iconScale = parsePositiveFloat(firstNonBlank(attributes, "icon-scale", "iconscale", "scale"));
                Integer padding = parseNonNegativeInt(firstNonBlank(attributes, "padding"));
                Integer zIndex = parseSignedInt(firstNonBlank(attributes, "z-index", "zindex", "z"));
                String extraClasses = normalizeBlankToNull(attributes.get("class"));

                if (slotSize == null
                        && disabled == null
                        && acceptPointer == null
                        && renderBackground == null
                        && renderItem == null
                        && iconScale == null
                        && padding == null
                        && zIndex == null
                        && extraClasses == null) {
                    return null;
                }

                return new SlotVisualProfile(
                        slotSize,
                        disabled,
                        acceptPointer,
                        renderBackground,
                        renderItem,
                        iconScale,
                        padding,
                        zIndex,
                        extraClasses
                );
            }

            public static SlotVisualProfile read(PacketBuffer buf) {
                int flags = buf.readVarInt();
                Integer slotSize = (flags & FLAG_SLOT_SIZE) != 0 ? buf.readVarInt() : null;
                Boolean disabled = (flags & FLAG_DISABLED) != 0 ? buf.readBoolean() : null;
                Boolean acceptPointer = (flags & FLAG_POINTER) != 0 ? buf.readBoolean() : null;
                Boolean renderBackground = (flags & FLAG_RENDER_BG) != 0 ? buf.readBoolean() : null;
                Boolean renderItem = (flags & FLAG_RENDER_ITEM) != 0 ? buf.readBoolean() : null;
                Float iconScale = (flags & FLAG_ICON_SCALE) != 0 ? buf.readFloat() : null;
                Integer padding = (flags & FLAG_PADDING) != 0 ? buf.readVarInt() : null;
                Integer zIndex = (flags & FLAG_Z_INDEX) != 0 ? buf.readInt() : null;
                String extraClasses = (flags & FLAG_EXTRA_CLASSES) != 0 ? normalizeBlankToNull(buf.readUtf()) : null;
                return new SlotVisualProfile(slotSize, disabled, acceptPointer, renderBackground, renderItem, iconScale, padding, zIndex, extraClasses);
            }

            public void write(PacketBuffer buf) {
                int flags = 0;
                if (slotSize != null) flags |= FLAG_SLOT_SIZE;
                if (disabled != null) flags |= FLAG_DISABLED;
                if (acceptPointer != null) flags |= FLAG_POINTER;
                if (renderBackground != null) flags |= FLAG_RENDER_BG;
                if (renderItem != null) flags |= FLAG_RENDER_ITEM;
                if (iconScale != null) flags |= FLAG_ICON_SCALE;
                if (padding != null) flags |= FLAG_PADDING;
                if (zIndex != null) flags |= FLAG_Z_INDEX;
                if (StringUtils.isNotNullOrEmptyEx(extraClasses)) flags |= FLAG_EXTRA_CLASSES;
                buf.writeVarInt(flags);
                if (slotSize != null) buf.writeVarInt(Math.max(1, slotSize));
                if (disabled != null) buf.writeBoolean(disabled);
                if (acceptPointer != null) buf.writeBoolean(acceptPointer);
                if (renderBackground != null) buf.writeBoolean(renderBackground);
                if (renderItem != null) buf.writeBoolean(renderItem);
                if (iconScale != null) buf.writeFloat(Math.max(0.01F, iconScale));
                if (padding != null) buf.writeVarInt(Math.max(0, padding));
                if (zIndex != null) buf.writeInt(zIndex);
                if (StringUtils.isNotNullOrEmptyEx(extraClasses)) buf.writeUtf(extraClasses);
            }

            public int resolveSlotSize(int fallback) {
                if (slotSize == null || slotSize <= 0) return fallback;
                return slotSize;
            }

            public boolean resolveDisabled(boolean fallback) {
                if (disabled == null) return fallback;
                return disabled;
            }

            public boolean resolveAcceptPointer(boolean fallback) {
                if (acceptPointer == null) return fallback;
                return acceptPointer;
            }

            public boolean resolveRenderBackground(boolean fallback) {
                if (renderBackground == null) return fallback;
                return renderBackground;
            }

            public boolean resolveRenderItem(boolean fallback) {
                if (renderItem == null) return fallback;
                return renderItem;
            }

            public float resolveIconScale(float fallback) {
                if (iconScale == null || iconScale <= 0.0F) return fallback;
                return iconScale;
            }

            public int resolvePadding(int fallback) {
                if (padding == null) return fallback;
                return Math.max(0, padding);
            }

            public int resolveZIndex(int fallback) {
                if (zIndex == null) return fallback;
                return zIndex;
            }

            public String resolveExtraClasses(String fallback) {
                if (StringUtils.isNullOrEmptyEx(extraClasses)) return fallback;
                return extraClasses;
            }

            private static Boolean parseBoolean(String raw) {
                if (StringUtils.isNullOrEmptyEx(raw)) return null;
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(normalized) || "1".equals(normalized) || "on".equals(normalized) || "yes".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized) || "0".equals(normalized) || "off".equals(normalized) || "no".equals(normalized)) {
                    return false;
                }
                return null;
            }

            public static Boolean parsePointerFlag(String raw) {
                if (StringUtils.isNullOrEmptyEx(raw)) return null;
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                if ("auto".equals(normalized)) return null;
                if ("true".equals(normalized)
                        || "1".equals(normalized)
                        || "on".equals(normalized)
                        || "yes".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized)
                        || "0".equals(normalized)
                        || "none".equals(normalized)
                        || "off".equals(normalized)
                        || "no".equals(normalized)) {
                    return false;
                }
                return null;
            }

            public static RenderRule parseRenderRule(String raw) {
                if (StringUtils.isNullOrEmptyEx(raw)) return null;
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                switch (normalized) {
                    case "none":
                        return new RenderRule(false, false);
                    case "item":
                        return new RenderRule(false, true);
                    case "bg":
                    case "background":
                        return new RenderRule(true, false);
                    default:
                        return parseRenderRuleByTokens(normalized);
                }
            }

            private static RenderRule parseRenderRuleByTokens(String normalized) {
                String[] tokens = normalized.split("[\\s,|+/]+");
                LinkedHashSet<String> normalizedTokens = new LinkedHashSet<>();
                for (String token : tokens) {
                    if (StringUtils.isNullOrEmptyEx(token)) continue;
                    normalizedTokens.add(token.trim());
                }
                if (normalizedTokens.isEmpty()) return null;

                boolean renderBackground = false;
                boolean renderItem = false;
                for (String token : normalizedTokens) {
                    if ("bg".equals(token) || "background".equals(token)) {
                        renderBackground = true;
                    } else if ("item".equals(token)) {
                        renderItem = true;
                    }
                }

                if (renderBackground || renderItem) {
                    return new RenderRule(renderBackground, renderItem);
                }
                if (normalizedTokens.contains("none")) {
                    return new RenderRule(false, false);
                }
                return null;
            }

            public static Integer parseSignedInt(String raw) {
                if (StringUtils.isNullOrEmptyEx(raw)) return null;
                try {
                    return Integer.parseInt(raw.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            public static Integer parsePositiveInt(String raw) {
                Integer parsed = parseSignedInt(raw);
                if (parsed == null || parsed <= 0) return null;
                return parsed;
            }

            public static Integer parseNonNegativeInt(String raw) {
                Integer parsed = parseSignedInt(raw);
                if (parsed == null || parsed < 0) return null;
                return parsed;
            }

            public static Float parsePositiveFloat(String raw) {
                if (StringUtils.isNullOrEmptyEx(raw)) return null;
                try {
                    float parsed = Float.parseFloat(raw.trim());
                    if (parsed <= 0.0F) return null;
                    return parsed;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            private static String normalizeBlankToNull(String raw) {
                if (StringUtils.isNullOrEmpty(raw)) return null;
                String normalized = raw.trim();
                return StringUtils.isNullOrEmptyEx(normalized) ? null : normalized;
            }

            private static String firstNonBlank(Map<String, String> attributes, String... keys) {
                if (attributes == null || attributes.isEmpty() || keys == null || keys.length == 0) return null;
                for (String key : keys) {
                    if (StringUtils.isNullOrEmptyEx(key)) continue;
                    String value = attributes.get(key);
                    if (StringUtils.isNullOrEmptyEx(value)) continue;
                    return value;
                }
                return null;
            }
        }

        public enum BindType {
            PLAYER("player"),
            ENTITY("entity"),
            BLOCK_ENTITY("block_entity"),
            SAVED_DATA("saved_data"),
            VIRTUAL_UI("__virtual_ui");

            private static final Map<String, BindType> BY_ID = new HashMap<>();

            static {
                for (BindType value : values()) {
                    BY_ID.put(value.id, value);
                }
            }

            private final String id;

            BindType(String id) {
                this.id = id;
            }

            public static BindType fromRaw(String rawBindType) {
                if (StringUtils.isNullOrEmptyEx(rawBindType)) return null;
                String bindType = rawBindType.trim().toLowerCase(Locale.ROOT);
                return BY_ID.get(bindType);
            }

            public String id() {
                return id;
            }
        }
    }

    public static final class MenuKeyResolver {
        private static final Pattern CONTAINER_ID_PATTERN = Pattern.compile("^[a-z0-9_./-]+$");

        public static String normalizeTemplatePath(String rawTemplatePath) {
            if (StringUtils.isNullOrEmpty(rawTemplatePath)) return null;
            String path = rawTemplatePath.trim().replace('\\', '/');
            if (StringUtils.isNullOrEmpty(path)) return null;

            if (path.startsWith("./")) path = path.substring(2);
            if (path.startsWith("/")) path = path.substring(1);
            if (path.startsWith("apricity/")) path = path.substring("apricity/".length());
            if (path.contains("..")) return null;
            if (!path.endsWith(".html")) return null;

            String[] segments = path.split("/");
            if (segments.length < 2) return null;
            return path;
        }

        public static String normalizeContainerId(String rawContainerId) {
            if (StringUtils.isNullOrEmptyEx(rawContainerId)) return null;
            String containerId = rawContainerId.trim().toLowerCase(Locale.ROOT);
            if (!CONTAINER_ID_PATTERN.matcher(containerId).matches()) return null;
            return containerId;
        }

        public static ResourceLocation resolveMenuKey(String templatePath) {
            String normalizedTemplatePath = normalizeTemplatePath(templatePath);
            if (normalizedTemplatePath == null) return null;

            String[] segments = normalizedTemplatePath.split("/");
            String namespace = segments[0].toLowerCase(Locale.ROOT);
            String fileName = segments[segments.length - 1];
            if (!fileName.endsWith(".html")) return null;

            String screenName = fileName.substring(0, fileName.length() - 5).toLowerCase(Locale.ROOT);
            String key = namespace + ":" + screenName;
            return ResourceLocation.tryParse(key);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class OpenRequest {
        private String templatePath;
        private ResourceLocation menuKey;

        public static OpenRequest fromRaw(String rawTemplatePath) {
            String normalizedTemplatePath = MenuKeyResolver.normalizeTemplatePath(rawTemplatePath);
            if (normalizedTemplatePath == null) return null;

            ResourceLocation menuKey = MenuKeyResolver.resolveMenuKey(normalizedTemplatePath);
            if (menuKey == null) return null;

            return new OpenRequest(normalizedTemplatePath, menuKey);
        }

        public static OpenRequest fromDescriptor(Descriptor descriptor) {
            if (descriptor == null) return null;

            String normalizedTemplatePath = MenuKeyResolver.normalizeTemplatePath(descriptor.getTemplatePath());
            if (normalizedTemplatePath == null) return null;

            ResourceLocation menuKey = ResourceLocation.tryParse(descriptor.getMenuKey());
            if (menuKey == null) return null;

            return new OpenRequest(normalizedTemplatePath, menuKey);
        }
    }

    public static final class MappingSanitizer {
        public static SanitizedMapping sanitize(Map<String, Descriptor.BindType> rawBindMapping, Map<String, List<Integer>> rawSlotMapping) {
            LinkedHashMap<String, Descriptor.BindType> bindMapping = new LinkedHashMap<>();
            LinkedHashMap<String, List<Integer>> slotMapping = new LinkedHashMap<>();
            if (rawSlotMapping == null) return new SanitizedMapping(bindMapping, slotMapping);

            boolean[] invalidFound = {false};
            rawSlotMapping.forEach((rawContainerId, rawSlots) -> {
                if (invalidFound[0]) return;
                if (StringUtils.isNullOrEmptyEx(rawContainerId)) return;

                String normalizedContainerId = MenuKeyResolver.normalizeContainerId(rawContainerId);
                if (normalizedContainerId == null || slotMapping.containsKey(normalizedContainerId)) return;

                Descriptor.BindType bindType = resolveRawBindType(rawBindMapping, rawContainerId, normalizedContainerId);
                if (bindType == null) {
                    invalidFound[0] = true;
                    return;
                }

                LinkedHashSet<Integer> deduplicated = new LinkedHashSet<>();
                if (rawSlots != null) {
                    for (Integer slot : rawSlots) {
                        if (slot == null || slot < 0) continue;
                        deduplicated.add(slot);
                    }
                }
                bindMapping.put(normalizedContainerId, bindType);
                slotMapping.put(normalizedContainerId, new ArrayList<>(deduplicated));
            });

            if (invalidFound[0]) return null;
            return new SanitizedMapping(bindMapping, slotMapping);
        }

        private static Descriptor.BindType resolveRawBindType(Map<String, Descriptor.BindType> rawBindMapping, String rawContainerId, String normalizedContainerId) {
            if (rawBindMapping == null) return null;
            Descriptor.BindType bindType = rawBindMapping.get(rawContainerId);
            if (bindType != null) return bindType;
            return rawBindMapping.get(normalizedContainerId);
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class SanitizedMapping {
            private LinkedHashMap<String, Descriptor.BindType> bindMapping;
            private LinkedHashMap<String, List<Integer>> slotMapping;
        }
    }

    public static final class TemplateAnalyzer {
        private static final Pattern TAG_PATTERN = Pattern.compile("<!--.*?-->|</?[^>]+>", Pattern.DOTALL);
        private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^([\\w-]+)");
        private static final Pattern ATTRIBUTE_PATTERN =
                Pattern.compile("([\\w-:]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?");

        public static Descriptor analyzeTemplate(String rawTemplatePath) {
            OpenRequest request = OpenRequest.fromRaw(rawTemplatePath);
            if (request == null) return null;

            AnalyzeResult result = buildContainerMapping(request.templatePath());
            if (!result.valid()) return null;
            return Descriptor.createSanitized(
                    request.menuKey().toString(),
                    request.templatePath(),
                    result.primaryPartitionKey(),
                    result.bindMapping(),
                    result.slotMapping(),
                    result.slotVisualMapping());
        }

        public static Descriptor analyzePlayerInventory(String rawTemplatePath) {
            return analyzeTemplate(rawTemplatePath);
        }

        public static String resolvePrimaryContainerTitleLiteral(String rawTemplatePath) {
            OpenRequest request = OpenRequest.fromRaw(rawTemplatePath);
            if (request == null) return null;

            String rawHtml = HTML.getTemple(request.templatePath());
            if (StringUtils.isNullOrEmptyEx(rawHtml)) return null;

            List<ContainerDraft> drafts = parseContainerDrafts(rawHtml, request.templatePath());
            String primaryPartitionKey = resolvePrimaryPartitionKey(request.templatePath(), drafts);
            if (primaryPartitionKey == null) return null;

            for (ContainerDraft draft : drafts) {
                if (!primaryPartitionKey.equals(draft.partitionKey)) continue;
                if (StringUtils.isNullOrEmptyEx(draft.titleLiteral)) return null;
                return draft.titleLiteral.trim();
            }
            return null;
        }

        private static AnalyzeResult buildContainerMapping(String templatePath) {
            String rawHtml = HTML.getTemple(templatePath);
            if (StringUtils.isNullOrEmptyEx(rawHtml)) {
                ApricityUI.LOGGER.warn("Template analysis failed: HTML content not found, path={}", templatePath);
                return AnalyzeResult.invalid();
            }

            List<ContainerDraft> drafts = parseContainerDrafts(rawHtml, templatePath);
            LinkedHashMap<String, Descriptor.BindType> rawBindMapping = new LinkedHashMap<>();
            LinkedHashMap<String, List<Integer>> rawSlotMapping = new LinkedHashMap<>();
            LinkedHashMap<String, Map<Integer, Descriptor.SlotVisualProfile>> rawSlotVisualMapping = new LinkedHashMap<>();
            String primaryPartitionKey = resolvePrimaryPartitionKey(templatePath, drafts);
            if (primaryPartitionKey == null) return AnalyzeResult.invalid();

            for (ContainerDraft draft : drafts) {
                if (!draft.virtualContainer && draft.bindType == null) {
                    String rawBindType = draft.rawBindType == null ? "" : draft.rawBindType;
                    ApricityUI.LOGGER.warn("Template analysis failed: invalid bindType, path={}, partition={}, bind={}",
                            templatePath, draft.partitionKey, rawBindType);
                    return AnalyzeResult.invalid();
                }

                if (draft.virtualContainer) {
                    rawBindMapping.put(draft.partitionKey, Descriptor.BindType.VIRTUAL_UI);
                    rawSlotMapping.put(draft.partitionKey, Collections.emptyList());
                    rawSlotVisualMapping.put(draft.partitionKey, new HashMap<>());
                    if (draft.ignoredBoundSlotCount > 0) {
                        ApricityUI.LOGGER.info(
                                "Template analysis: virtual container ignored bound slots, path={}, partition={}, ignored={}",
                                templatePath,
                                draft.partitionKey,
                                draft.ignoredBoundSlotCount
                        );
                    }
                    continue;
                }

                LinkedHashSet<Integer> deduplicatedSlots = new LinkedHashSet<>();
                if (!draft.explicitSlots.isEmpty()) {
                    for (Integer localSlotIndex : draft.explicitSlots) {
                        if (localSlotIndex == null || localSlotIndex < 0) {
                            ApricityUI.LOGGER.warn("Template analysis skipped: invalid slot-index, path={}, partition={}, slot={}",
                                    templatePath, draft.partitionKey, localSlotIndex);
                            continue;
                        }
                        deduplicatedSlots.add(localSlotIndex);
                    }
                } else {
                    int requiredCount = Math.max(0, draft.slotCount);
                    for (int index = 0; index < requiredCount; index++) {
                        deduplicatedSlots.add(index);
                    }
                }

                rawBindMapping.put(draft.partitionKey, draft.bindType);
                ArrayList<Integer> sanitizedSlots = new ArrayList<>(deduplicatedSlots);
                rawSlotMapping.put(draft.partitionKey, sanitizedSlots);
                rawSlotVisualMapping.put(draft.partitionKey, draft.exportSlotVisualProfiles(sanitizedSlots));
            }

            MappingSanitizer.SanitizedMapping sanitized = MappingSanitizer.sanitize(rawBindMapping, rawSlotMapping);
            if (sanitized == null) {
                ApricityUI.LOGGER.warn("Template analysis failed: bind/slot mapping sanitize failed, path={}", templatePath);
                return AnalyzeResult.invalid();
            }

            return AnalyzeResult.valid(primaryPartitionKey, sanitized.bindMapping(), sanitized.slotMapping(), rawSlotVisualMapping);
        }

        private static List<ContainerDraft> parseContainerDrafts(String rawHtml, String templatePath) {
            ArrayList<ContainerDraft> drafts = new ArrayList<>();
            Deque<ContainerParseFrame> containerStack = new ArrayDeque<>();
            int topLevelContainerIndex = 0;
            Matcher matcher = TAG_PATTERN.matcher(rawHtml);
            int previousTokenEnd = 0;

            while (matcher.find()) {
                String token = matcher.group();
                ContainerParseFrame activeTopLevelFrame = resolveActiveTopLevelFrame(containerStack);
                if (activeTopLevelFrame != null && activeTopLevelFrame.isCapturingTitle() && matcher.start() > previousTokenEnd) {
                    activeTopLevelFrame.appendTitleText(rawHtml.substring(previousTokenEnd, matcher.start()));
                }

                if (token.startsWith("<!--")) {
                    previousTokenEnd = matcher.end();
                    continue;
                }

                boolean closingTag = token.startsWith("</");
                if (closingTag) {
                    String closingTagName = extractClosingTagName(token);
                    if ("container".equals(closingTagName) && !containerStack.isEmpty()) {
                        ContainerParseFrame closingFrame = containerStack.peek();
                        if (closingFrame != null && closingFrame.isTopLevel()) {
                            closingFrame.finishTitleCapture();
                        }
                        containerStack.pop();

                        ContainerParseFrame parentTopLevelFrame = resolveActiveTopLevelFrame(containerStack);
                        if (parentTopLevelFrame != null) {
                            parentTopLevelFrame.onDescendantClose();
                        }
                    } else if (activeTopLevelFrame != null) {
                        activeTopLevelFrame.onDescendantClose();
                    }
                    previousTokenEnd = matcher.end();
                    continue;
                }

                boolean selfClosing = token.endsWith("/>");
                TagData tagData = parseTag(token, selfClosing);
                if (tagData == null) {
                    previousTokenEnd = matcher.end();
                    continue;
                }

                ContainerParseFrame currentTopLevelFrame = resolveActiveTopLevelFrame(containerStack);
                if (currentTopLevelFrame != null) {
                    currentTopLevelFrame.onDescendantOpen(selfClosing);
                }

                if ("container".equals(tagData.tagName())) {
                    boolean isTopLevelContainer = containerStack.isEmpty();
                    ContainerDraft draft = null;

                    if (isTopLevelContainer) {
                        String partitionKey = resolvePartitionKey(topLevelContainerIndex++);
                        boolean primary = isPrimaryContainer(tagData.attributes());
                        String rawBindType = tagData.attributes().get("bind");
                        boolean virtualContainer = isVirtualContainer(rawBindType);
                        Descriptor.BindType bindType = virtualContainer ? Descriptor.BindType.VIRTUAL_UI : resolveBindType(rawBindType);
                        Integer slotCount = resolveAutoSlotCount(tagData.attributes(), bindType);
                        draft = new ContainerDraft(
                                partitionKey,
                                primary,
                                virtualContainer,
                                rawBindType,
                                bindType,
                                slotCount == null ? 0 : slotCount);
                        drafts.add(draft);
                    }

                    ContainerParseFrame containerFrame = new ContainerParseFrame(isTopLevelContainer, draft);
                    containerStack.push(containerFrame);

                    if (selfClosing && !containerStack.isEmpty()) {
                        ContainerParseFrame closingFrame = containerStack.pop();
                        if (closingFrame != null && closingFrame.isTopLevel()) {
                            closingFrame.finishTitleCapture();
                        }
                        ContainerParseFrame parentTopLevelFrame = resolveActiveTopLevelFrame(containerStack);
                        if (parentTopLevelFrame != null) {
                            parentTopLevelFrame.onDescendantClose();
                        }
                    }
                    previousTokenEnd = matcher.end();
                    continue;
                }

                if ("slot".equals(tagData.tagName()) && !containerStack.isEmpty()) {
                    ContainerParseFrame currentFrame = containerStack.peek();
                    if (currentFrame != null && currentFrame.isTopLevel() && !drafts.isEmpty() && isBoundSlot(tagData.attributes())) {
                        ContainerDraft draft = drafts.get(drafts.size() - 1);
                        int repeatCount = parseRepeatCount(tagData.attributes().get("repeat"));
                        Integer slotIndex = parseSlotIndex(tagData.attributes().get("slot-index"));
                        if (draft.virtualContainer) {
                            draft.recordIgnoredBoundSlots(repeatCount);
                            ApricityUI.LOGGER.warn(
                                    "Template analysis: bound slot ignored in virtual container, path={}, partition={}, slot-index={}, repeat={}, mode={}",
                                    templatePath,
                                    draft.partitionKey,
                                    slotIndex == null ? "<implicit>" : slotIndex,
                                    repeatCount,
                                    tagData.attributes().getOrDefault("mode", "<default>")
                            );
                        } else {
                            int startIndex = slotIndex == null ? draft.allocateImplicitStart(repeatCount) : slotIndex;
                            draft.advanceImplicitCursor(startIndex + repeatCount);
                            Descriptor.SlotVisualProfile slotVisualProfile = Descriptor.SlotVisualProfile.fromSlotAttributes(tagData.attributes());
                            for (int offset = 0; offset < repeatCount; offset++) {
                                int localSlotIndex = startIndex + offset;
                                draft.explicitSlots.add(localSlotIndex);
                                draft.recordSlotVisualProfile(localSlotIndex, slotVisualProfile);
                            }
                        }
                    }
                }

                if (selfClosing && currentTopLevelFrame != null) {
                    currentTopLevelFrame.onDescendantClose();
                }
                previousTokenEnd = matcher.end();
            }

            if (previousTokenEnd < rawHtml.length()) {
                ContainerParseFrame activeTopLevelFrame = resolveActiveTopLevelFrame(containerStack);
                if (activeTopLevelFrame != null && activeTopLevelFrame.isCapturingTitle()) {
                    activeTopLevelFrame.appendTitleText(rawHtml.substring(previousTokenEnd));
                    activeTopLevelFrame.finishTitleCapture();
                }
            }

            return drafts;
        }

        private static ContainerParseFrame resolveActiveTopLevelFrame(Deque<ContainerParseFrame> containerStack) {
            if (containerStack == null || containerStack.isEmpty()) return null;
            for (ContainerParseFrame frame : containerStack) {
                if (frame != null && frame.isTopLevel()) {
                    return frame;
                }
            }
            return null;
        }

        private static String extractClosingTagName(String token) {
            String content = token.substring(2, token.length() - 1).trim().toLowerCase(Locale.ROOT);
            int blankIndex = content.indexOf(' ');
            return blankIndex >= 0 ? content.substring(0, blankIndex) : content;
        }

        private static TagData parseTag(String token, boolean selfClosing) {
            String content = token.substring(1, token.length() - (selfClosing ? 2 : 1)).trim();
            Matcher tagNameMatcher = TAG_NAME_PATTERN.matcher(content);
            if (!tagNameMatcher.find()) return null;

            String tagName = tagNameMatcher.group(1).toLowerCase(Locale.ROOT);
            String attrSection = content.substring(tagNameMatcher.end()).trim();
            LinkedHashMap<String, String> attributes = parseAttributes(attrSection);
            return new TagData(tagName, attributes);
        }

        private static LinkedHashMap<String, String> parseAttributes(String attrSection) {
            LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
            if (StringUtils.isNullOrEmptyEx(attrSection)) return attributes;

            Matcher attrMatcher = ATTRIBUTE_PATTERN.matcher(attrSection);
            while (attrMatcher.find()) {
                String key = attrMatcher.group(1).toLowerCase(Locale.ROOT);
                String value = attrMatcher.group(2);
                if (value == null) value = "";
                else value = value.replaceAll("^['\"]|['\"]$", "");
                attributes.put(key, value);
            }
            return attributes;
        }

        private static String resolvePartitionKey(int index) {
            return "c" + Math.max(0, index);
        }

        private static boolean isPrimaryContainer(Map<String, String> attributes) {
            String rawPrimary = attributes.get("primary");
            if (rawPrimary == null) return false;
            return "true".equals(rawPrimary.trim().toLowerCase(Locale.ROOT));
        }

        private static String resolvePrimaryPartitionKey(String templatePath, List<ContainerDraft> drafts) {
            if (drafts == null || drafts.isEmpty()) {
                // 允许纯 UI 模板（无顶层 container），回退为 ui-only 分区。
                ApricityUI.LOGGER.info("Template analysis fallback to ui-only: no bindable top-level <container>, path={}", templatePath);
                return Descriptor.DEFAULT_PARTITION_KEY;
            }

            String primaryPartitionKey = null;
            for (ContainerDraft draft : drafts) {
                if (!draft.primary) continue;
                if (primaryPartitionKey != null) {
                    ApricityUI.LOGGER.warn("Template analysis failed: multiple primary containers found, path={}, first={}, second={}",
                            templatePath, primaryPartitionKey, draft.partitionKey);
                    return null;
                }
                primaryPartitionKey = draft.partitionKey;
            }

            if (primaryPartitionKey == null) {
                return drafts.get(0).partitionKey;
            }
            return primaryPartitionKey;
        }

        private static Descriptor.BindType resolveBindType(String rawBind) {
            return Descriptor.resolveBindType(rawBind);
        }

        private static boolean isVirtualContainer(String rawBind) {
            return StringUtils.isNullOrEmptyEx(rawBind);
        }

        private static Integer resolveAutoSlotCount(Map<String, String> attributes, Descriptor.BindType bindType) {
            if (Descriptor.isPlayerBind(bindType)) {
                return Descriptor.PLAYER_SLOT_COUNT;
            }
            return 0;
        }

        private static boolean isBoundSlot(Map<String, String> attributes) {
            String rawMode = attributes.get("mode");
            if (StringUtils.isNullOrEmptyEx(rawMode)) return true;
            return "bound".equals(rawMode.trim().toLowerCase(Locale.ROOT));
        }

        private static int parseRepeatCount(String rawRepeat) {
            Integer parsed = parsePositive(rawRepeat);
            if (parsed == null) return 1;
            return Math.max(1, parsed);
        }

        private static Integer parseSlotIndex(String rawSlotIndex) {
            if (StringUtils.isNullOrEmptyEx(rawSlotIndex)) return null;
            try {
                return Integer.parseInt(rawSlotIndex.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static Integer parsePositive(String raw) {
            if (StringUtils.isNullOrEmptyEx(raw)) return null;
            try {
                int value = Integer.parseInt(raw.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        private static class TagData {
            private String tagName;
            private Map<String, String> attributes;
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        private static class AnalyzeResult {
            private boolean valid;
            private String primaryPartitionKey;
            private LinkedHashMap<String, Descriptor.BindType> bindMapping;
            private LinkedHashMap<String, List<Integer>> slotMapping;
            private LinkedHashMap<String, Map<Integer, Descriptor.SlotVisualProfile>> slotVisualMapping;

            private static AnalyzeResult invalid() {
                return new AnalyzeResult(false, null, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
            }

            private static AnalyzeResult valid(String primaryPartitionKey,
                                               LinkedHashMap<String, Descriptor.BindType> bindMapping,
                                               LinkedHashMap<String, List<Integer>> slotMapping,
                                               LinkedHashMap<String, Map<Integer, Descriptor.SlotVisualProfile>> slotVisualMapping) {
                return new AnalyzeResult(true, primaryPartitionKey, bindMapping, slotMapping, slotVisualMapping);
            }
        }

        private static class ContainerDraft {
            private final String partitionKey;
            private final boolean primary;
            private final boolean virtualContainer;
            private final String rawBindType;
            private final Descriptor.BindType bindType;
            private final int slotCount;
            private String titleLiteral;
            private final LinkedHashSet<Integer> explicitSlots = new LinkedHashSet<>();
            private final LinkedHashMap<Integer, Descriptor.SlotVisualProfile> slotVisualProfiles = new LinkedHashMap<>();
            private int nextImplicitLocalIndex = 0;
            private int ignoredBoundSlotCount = 0;

            private ContainerDraft(String partitionKey,
                                   boolean primary,
                                   boolean virtualContainer,
                                   String rawBindType,
                                   Descriptor.BindType bindType,
                                   int slotCount) {
                this.partitionKey = partitionKey;
                this.primary = primary;
                this.virtualContainer = virtualContainer;
                this.rawBindType = rawBindType;
                this.bindType = bindType;
                this.slotCount = Math.max(0, slotCount);
                this.titleLiteral = null;
            }

            private void setTitleLiteral(String titleLiteral) {
                this.titleLiteral = titleLiteral == null ? null : titleLiteral.trim();
            }

            private int allocateImplicitStart(int count) {
                int start = Math.max(0, nextImplicitLocalIndex);
                nextImplicitLocalIndex = start + Math.max(1, count);
                return start;
            }

            private void advanceImplicitCursor(int candidate) {
                if (candidate > nextImplicitLocalIndex) {
                    nextImplicitLocalIndex = candidate;
                }
            }

            private void recordIgnoredBoundSlots(int count) {
                ignoredBoundSlotCount += Math.max(1, count);
            }

            private void recordSlotVisualProfile(int localSlotIndex, Descriptor.SlotVisualProfile profile) {
                if (localSlotIndex < 0 || profile == null) return;
                slotVisualProfiles.put(localSlotIndex, profile);
            }

            private Map<Integer, Descriptor.SlotVisualProfile> exportSlotVisualProfiles(List<Integer> includedSlots) {
                if (includedSlots == null || includedSlots.isEmpty()) return new HashMap<>();
                if (slotVisualProfiles.isEmpty()) return new HashMap<>();

                LinkedHashMap<Integer, Descriptor.SlotVisualProfile> result = new LinkedHashMap<>();
                for (Integer localSlotIndex : includedSlots) {
                    if (localSlotIndex == null || localSlotIndex < 0) continue;
                    Descriptor.SlotVisualProfile profile = slotVisualProfiles.get(localSlotIndex);
                    if (profile == null) continue;
                    result.put(localSlotIndex, profile);
                }
                if (result.isEmpty()) return new HashMap<>();
                return result;
            }
        }

        private static class ContainerParseFrame {
            private final boolean topLevel;
            private final ContainerDraft draft;
            private int descendantDepth = 0;
            private boolean firstChildResolved = false;
            private boolean titleCaptureActive = false;
            private int titleCaptureDepth = -1;
            private final StringBuilder titleBuffer = new StringBuilder();

            private ContainerParseFrame(boolean topLevel, ContainerDraft draft) {
                this.topLevel = topLevel;
                this.draft = draft;
            }

            private boolean isTopLevel() {
                return topLevel;
            }

            private boolean isCapturingTitle() {
                return topLevel && titleCaptureActive;
            }

            private void onDescendantOpen(boolean selfClosing) {
                if (!topLevel) return;

                if (!firstChildResolved && descendantDepth == 0) {
                    firstChildResolved = true;
                    if (!selfClosing) {
                        titleCaptureActive = true;
                        titleCaptureDepth = descendantDepth + 1;
                    }
                }

                if (!selfClosing) {
                    descendantDepth++;
                }
            }

            private void onDescendantClose() {
                if (!topLevel) return;
                if (descendantDepth > 0) {
                    descendantDepth--;
                }
                if (titleCaptureActive && descendantDepth < titleCaptureDepth) {
                    finishTitleCapture();
                }
            }

            private void appendTitleText(String text) {
                if (!isCapturingTitle()) return;
                if (StringUtils.isNullOrEmpty(text)) return;
                titleBuffer.append(text);
            }

            private void finishTitleCapture() {
                if (!topLevel) return;
                if (titleCaptureActive) {
                    if (draft != null) {
                        String captured = titleBuffer.toString();
                        draft.setTitleLiteral(StringUtils.isNullOrEmptyEx(captured) ? null : captured.trim());
                    }
                    titleCaptureActive = false;
                    titleCaptureDepth = -1;
                }
            }
        }
    }
}
