package com.sighs.apricityui.instance.container.runtime;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Selector;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.instance.container.layout.SlotLayoutMath;
import com.sighs.apricityui.instance.container.layout.SlotLayoutPlacement;
import com.sighs.apricityui.instance.container.layout.SlotLayoutPlanner;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.instance.container.visual.SlotVisualRules;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class ContainerEngine {
    public static final class Binding {
        @Getter
        private final String containerId;
        private final Container container;
        private final LinkedHashMap<Integer, Slot> slots = new LinkedHashMap<>();

        public Binding(String containerId, Container container) {
            this.containerId = containerId;
            this.container = container;
        }

        public Container getContainerElement() {
            return container;
        }

        public Map<Integer, Slot> getSlots() {
            return slots;
        }

        public void putSlot(int slotIndex, Slot slot) {
            slots.put(slotIndex, slot);
        }
    }

    public record MenuAdapter(ApricityContainerMenu menu) {
        public ContainerSchema.Descriptor getDescriptor() {
            return menu.getDescriptor();
        }

        public boolean hasContainer(String containerId) {
            return menu.hasContainer(containerId);
        }

        public Integer resolveGlobalSlotIndex(String containerId, int localSlotIndex) {
            return menu.resolveGlobalSlotIndex(containerId, localSlotIndex);
        }

        public List<SlotRef> getContainerSlotRefs(String containerId) {
            List<ApricityContainerMenu.ContainerSlotRef> refs = menu.getContainerSlotRefs(containerId);
            ArrayList<SlotRef> copied = new ArrayList<>(refs.size());
            for (ApricityContainerMenu.ContainerSlotRef ref : refs) {
                copied.add(new SlotRef(ref.localSlotIndex(), ref.globalSlotIndex()));
            }
            return copied;
        }

        public boolean hasSlot(int slotIndex) {
            return slotIndex >= 0 && slotIndex < menu.slots.size();
        }

        public net.minecraft.world.inventory.Slot getSlot(int slotIndex) {
            return hasSlot(slotIndex) ? menu.slots.get(slotIndex) : null;
        }

        public record SlotRef(int localSlotIndex, int globalSlotIndex) {
        }
    }

    public static class Controller {
        private static final int DEFAULT_SLOT_PIXEL_SIZE = readIntProperty("apricityui.slot.pixel-size", 16, 8, 64);
        private static final int DEFAULT_SLOT_GAP = readIntProperty("apricityui.slot.gap", 2, 0, 32);
        private static final String CONTAINER_METRICS_CACHE_KEY = "container-engine:metrics";
        private final ApricityContainerScreen screen;
        private final MenuAdapter menuAdapter;
        private final SlotLayoutPlanner slotLayoutPlanner = new SlotLayoutPlanner();
        private final LinkedHashMap<String, Binding> bindings = new LinkedHashMap<>();
        private final HashMap<UUID, String> containerPartitionByUuid = new HashMap<>();
        private final HashMap<Integer, Slot> slotElementByGlobalIndex = new HashMap<>();
        private final LinkedHashMap<String, BindingRuntimeCache> bindingRuntimeCaches = new LinkedHashMap<>();
        private final LinkedHashMap<Integer, SlotVisualState> slotVisualStates = new LinkedHashMap<>();
        private List<SlotVisualState> slotVisualStateSnapshot = List.of();
        private boolean slotVisualStateSnapshotDirty = true;
        private final HashMap<String, Boolean> backgroundReadableCache = new HashMap<>();
        private Document boundDocument = null;
        @Setter
        private int hoveredSlot = -1;
        private int focusedSlot = -1;
        private boolean layoutDirty = true;
        private long layoutEpoch = 0L;

        public Controller(ApricityContainerScreen screen, MenuAdapter menuAdapter) {
            this.screen = screen;
            this.menuAdapter = menuAdapter;
        }

        private static int readIntProperty(String key, int fallback, int min, int max) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < min || parsed > max) return fallback;
                return parsed;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        public void bind(Document document) {
            resetBindings();
            if (document == null) return;
            boundDocument = document;
            Slot.cleanupRuntimeGeneratedSlots(document);

            Set<Integer> globallyBoundSlots = new HashSet<>();
            List<Element> elements = document.getElements();
            LinkedHashMap<Container, String> containerPartitionKeys = new LinkedHashMap<>();
            containerPartitionByUuid.clear();
            String primaryPartitionKey = null;
            int partitionIndex = 0;
            int primaryCount = 0;
            String firstPartitionKey = null;

            for (Element element : elements) {
                if (!(element instanceof Container container)) continue;
                if (container.hasAncestor(Container.class)) {
                    ApricityUI.LOGGER.warn("Detected nested <container>; inner container has been disabled.");
                    continue;
                }
                String partitionKey = resolvePartitionKey(partitionIndex++);
                if (firstPartitionKey == null) firstPartitionKey = partitionKey;
                if (container.isPrimaryContainer()) {
                    primaryCount++;
                    if (primaryPartitionKey == null) primaryPartitionKey = partitionKey;
                }
                containerPartitionKeys.put(container, partitionKey);
                containerPartitionByUuid.put(container.uuid, partitionKey);
                bindings.put(partitionKey, new Binding(partitionKey, container));
            }

            if (!containerPartitionKeys.isEmpty()) {
                if (primaryCount > 1) {
                    ApricityUI.LOGGER.warn("Bind failed: template contains multiple primary containers, actual={}", primaryCount);
                    resetBindings();
                    return;
                }
                if (primaryPartitionKey == null) primaryPartitionKey = firstPartitionKey;
                String expectedPrimaryPartitionKey = menuAdapter.getDescriptor().getPrimaryPartitionKey();
                if (!expectedPrimaryPartitionKey.equals(primaryPartitionKey)) {
                    ApricityUI.LOGGER.warn("Bind failed: primary container partition mismatch, client={}, server={}",
                            primaryPartitionKey, expectedPrimaryPartitionKey);
                    resetBindings();
                    return;
                }
            }

            LinkedHashMap<String, Integer> nextImplicitLocalIndexByPartition = new LinkedHashMap<>();
            for (String partitionKey : bindings.keySet()) {
                nextImplicitLocalIndexByPartition.put(partitionKey, 0);
            }

            for (Element element : elements) {
                if (!(element instanceof Slot templateSlot)) continue;
                if (!templateSlot.isBoundMode()) continue;
                if (templateSlot.isRuntimeRepeatProxy()) continue;

                int repeatCount = Math.max(1, templateSlot.getRepeatCount());
                int baseLocalSlotIndex = templateSlot.getSlotIndex();
                for (int repeatIndex = 0; repeatIndex < repeatCount; repeatIndex++) {
                    int localSlotIndex = baseLocalSlotIndex < 0 ? -1 : baseLocalSlotIndex + repeatIndex;
                    Slot slot = repeatIndex == 0
                            ? templateSlot
                            : templateSlot.createRuntimeRepeatSlotProxy(localSlotIndex, repeatIndex);

                    Container ownerContainer = slot.findAncestor(Container.class);
                    if (ownerContainer == null) {
                        ApricityUI.LOGGER.warn("<slot> has no owning <container>, skipped: {}", localSlotIndex);
                        continue;
                    }

                    String partitionKey = containerPartitionByUuid.get(ownerContainer.uuid);
                    Binding binding = bindings.get(partitionKey);
                    if (binding == null) {
                        ApricityUI.LOGGER.warn("Slot bind failed: partition unavailable, partition={} / local={}", partitionKey, localSlotIndex);
                        continue;
                    }

                    if (localSlotIndex < 0) {
                        localSlotIndex = nextImplicitLocalIndexByPartition.getOrDefault(partitionKey, 0);
                        slot.setAttribute("slot-index", String.valueOf(localSlotIndex));
                    }
                    int nextLocal = Math.max(nextImplicitLocalIndexByPartition.getOrDefault(partitionKey, 0), localSlotIndex + 1);
                    nextImplicitLocalIndexByPartition.put(partitionKey, nextLocal);

                    if (!menuAdapter.getDescriptor().isUiOnly() && !menuAdapter.hasContainer(partitionKey)) {
                        ApricityUI.LOGGER.warn("Partition not present in server descriptor, skipped: {}", partitionKey);
                        continue;
                    }

                    Integer globalSlotIndex = menuAdapter.resolveGlobalSlotIndex(partitionKey, localSlotIndex);
                    if (!menuAdapter.getDescriptor().isUiOnly() && globalSlotIndex == null) {
                        ContainerSchema.Descriptor.BindType bindType = menuAdapter.getDescriptor().getContainerBindType(partitionKey);
                        if (ContainerSchema.Descriptor.isVirtualUiBind(bindType)) {
                            ApricityUI.LOGGER.warn(
                                    "Virtual container bound slot ignored: partition={} / local={} / slot={}",
                                    partitionKey,
                                    localSlotIndex,
                                    slot
                            );
                        } else {
                            ApricityUI.LOGGER.warn("slot-index not mapped in server descriptor, skipped: {} / local={}", partitionKey, localSlotIndex);
                        }
                        continue;
                    }
                    if (globalSlotIndex == null) {
                        continue;
                    }

                    if (binding.getSlots().containsKey(globalSlotIndex)) {
                        ApricityUI.LOGGER.warn("Duplicate slot-index within partition, skipped: {} / global={}", partitionKey, globalSlotIndex);
                        continue;
                    }
                    if (globallyBoundSlots.contains(globalSlotIndex)) {
                        ApricityUI.LOGGER.warn("Duplicate slot-index globally, skipped: {}", globalSlotIndex);
                        continue;
                    }
                    if (!menuAdapter.hasSlot(globalSlotIndex)) {
                        ApricityUI.LOGGER.warn("slot-index out of menu range, skipped: {}", globalSlotIndex);
                        continue;
                    }

                    slot.bindToSlot(this, partitionKey, globalSlotIndex);
                    binding.putSlot(globalSlotIndex, slot);
                    slotElementByGlobalIndex.put(globalSlotIndex, slot);
                    globallyBoundSlots.add(globalSlotIndex);
                }
            }

            injectImplicitPlayerSlots(globallyBoundSlots);
            rebuildBindingRuntimeCaches();
            markLayoutDirty(DirtyReason.BIND);
            syncMenuSlotPositionsIfDirty();
        }

        private String resolvePartitionKey(int index) {
            return "c" + Math.max(0, index);
        }

        public void markLayoutDirty(DirtyReason reason) {
            layoutDirty = true;
        }

        public boolean syncMenuSlotPositionsIfDirty() {
            if (!layoutDirty) return false;
            syncMenuSlotPositionsInternal(false);
            layoutDirty = false;
            layoutEpoch++;
            return true;
        }

        public void syncMenuSlotPositions() {
            syncMenuSlotPositionsInternal(true);
            layoutDirty = false;
            layoutEpoch++;
        }

        private void syncMenuSlotPositionsInternal(boolean forceRebuildRuntimeCache) {
            for (Binding binding : bindings.values()) {
                syncRealSlotPositions(binding, forceRebuildRuntimeCache);
            }
        }

        private void syncRealSlotPositions(Binding binding, boolean forceRebuildRuntimeCache) {
            BindingRuntimeCache runtimeCache = bindingRuntimeCaches.get(binding.getContainerId());
            if (forceRebuildRuntimeCache || runtimeCache == null || !isBindingRuntimeCacheValid(binding, runtimeCache)) {
                runtimeCache = buildBindingRuntimeCache(binding);
                bindingRuntimeCaches.put(binding.getContainerId(), runtimeCache);
            }

            if (runtimeCache.anchorToSortedGlobalSlots().isEmpty()) return;
            for (Map.Entry<Element, List<Integer>> groupedEntry : runtimeCache.anchorToSortedGlobalSlots().entrySet()) {
                ArrayList<Map.Entry<Integer, Slot>> slotEntries = new ArrayList<>(groupedEntry.getValue().size());
                for (Integer slotIndex : groupedEntry.getValue()) {
                    Slot slot = binding.getSlots().get(slotIndex);
                    if (slot != null) {
                        slotEntries.add(new AbstractMap.SimpleEntry<>(slotIndex, slot));
                    }
                }
                if (slotEntries.isEmpty()) continue;
                // container 声明的 layout 语义应覆盖其内部所有 bound slot 分组，
                // 不应仅在 slot 直接挂在 container 下时生效。
                Container.SlotLayoutSpec effectiveSpec = binding.getContainerElement().getEffectiveSlotLayoutSpec();
                boolean compactDisabledSlot = effectiveSpec != null;
                applyPlannedLayout(binding, groupedEntry.getKey(), slotEntries, effectiveSpec, compactDisabledSlot);
            }
        }

        private void injectImplicitPlayerSlots(Set<Integer> globallyBoundSlots) {
            for (Binding binding : bindings.values()) {
                if (!binding.getSlots().isEmpty()) continue;
                if (!ContainerSchema.Descriptor.isPlayerBind(menuAdapter.getDescriptor().getContainerBindType(binding.getContainerId()))) {
                    continue;
                }

                List<MenuAdapter.SlotRef> candidateSlots = new ArrayList<>(menuAdapter.getContainerSlotRefs(binding.getContainerId()));
                candidateSlots.sort(Comparator.comparingInt(MenuAdapter.SlotRef::localSlotIndex));
                for (MenuAdapter.SlotRef slotRef : candidateSlots) {
                    int globalSlotIndex = slotRef.globalSlotIndex();
                    if (!menuAdapter.hasSlot(globalSlotIndex) || globallyBoundSlots.contains(globalSlotIndex)) continue;

                    String part = slotRef.localSlotIndex() < 27 ? "inv" : "hotbar";
                    Slot autoSlot = createImplicitPlayerSlot(binding.getContainerElement(), slotRef.localSlotIndex(), part);
                    autoSlot.bindToSlot(this, binding.getContainerId(), globalSlotIndex);
                    binding.putSlot(globalSlotIndex, autoSlot);
                    slotElementByGlobalIndex.put(globalSlotIndex, autoSlot);
                    globallyBoundSlots.add(globalSlotIndex);
                }
            }
        }

        private Slot createImplicitPlayerSlot(Container container, int localSlotIndex, String part) {
            Slot autoSlot = new Slot(container.document);
            autoSlot.applyImplicitPlayerMeta(localSlotIndex, part);
            container.append(autoSlot);
            autoSlot.cssCache = Selector.matchCSS(autoSlot);
            return autoSlot;
        }

        private Element resolveAnchor(Slot slotElement, Container container) {
            return slotElement.resolveLayoutAnchor(container);
        }

        private void rebuildBindingRuntimeCaches() {
            bindingRuntimeCaches.clear();
            slotElementByGlobalIndex.clear();
            for (Binding binding : bindings.values()) {
                bindingRuntimeCaches.put(binding.getContainerId(), buildBindingRuntimeCache(binding));
                slotElementByGlobalIndex.putAll(binding.getSlots());
            }
        }

        private BindingRuntimeCache buildBindingRuntimeCache(Binding binding) {
            ArrayList<Map.Entry<Integer, Slot>> slotEntries = new ArrayList<>(binding.getSlots().entrySet());
            slotEntries.sort(Map.Entry.comparingByKey());

            LinkedHashMap<Element, ArrayList<Integer>> grouped = new LinkedHashMap<>();
            for (Map.Entry<Integer, Slot> entry : slotEntries) {
                Element anchor = resolveAnchor(entry.getValue(), binding.getContainerElement());
                grouped.computeIfAbsent(anchor, key -> new ArrayList<>()).add(entry.getKey());
            }

            LinkedHashMap<Element, List<Integer>> groupedImmutable = new LinkedHashMap<>();
            for (Map.Entry<Element, ArrayList<Integer>> entry : grouped.entrySet()) {
                groupedImmutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new BindingRuntimeCache(binding.getSlots().size(), groupedImmutable);
        }

        private boolean isBindingRuntimeCacheValid(Binding binding, BindingRuntimeCache cache) {
            if (cache == null) return false;
            if (cache.slotCount() != binding.getSlots().size()) return false;

            int resolvedCount = 0;
            for (Map.Entry<Element, List<Integer>> group : cache.anchorToSortedGlobalSlots().entrySet()) {
                Element cachedAnchor = group.getKey();
                for (Integer globalSlotIndex : group.getValue()) {
                    Slot slot = binding.getSlots().get(globalSlotIndex);
                    if (slot == null) return false;
                    Element liveAnchor = resolveAnchor(slot, binding.getContainerElement());
                    if (liveAnchor != cachedAnchor) {
                        return false;
                    }
                    resolvedCount++;
                }
            }
            return resolvedCount == binding.getSlots().size();
        }

        private LayoutArea resolveLayoutArea(Container container, Element anchor, int slotPixelSize) {
            SlotLayoutMath.ContentBox containerContentBox = SlotLayoutMath.resolveContentBox(container);
            SlotLayoutMath.ContentBox anchorContentBox = SlotLayoutMath.resolveContentBox(anchor);

            double contentX = anchorContentBox.x();
            double contentY = resolveNonSlotOccupiedStartY(anchor, anchorContentBox.y());
            double contentWidth = anchorContentBox.width();
            double contentHeight = anchorContentBox.height();

            boolean widthUnset = "unset".equals(anchor.getComputedStyle().width);
            boolean heightUnset = "unset".equals(anchor.getComputedStyle().height);
            double availableWidth = containerContentBox.x() + containerContentBox.width() - contentX;
            double availableHeight = containerContentBox.y() + containerContentBox.height() - contentY;

            if (widthUnset && availableWidth > 0) {
                contentWidth = Math.max(contentWidth, availableWidth);
            }
            if (heightUnset && availableHeight > 0) {
                contentHeight = Math.max(contentHeight, availableHeight);
            }

            if (contentWidth < slotPixelSize) contentWidth = slotPixelSize;
            if (contentHeight < slotPixelSize) contentHeight = slotPixelSize;
            return new LayoutArea(contentX, contentY, contentWidth, contentHeight);
        }

        private void applyPlannedLayout(
                Binding binding,
                Element anchor,
                List<Map.Entry<Integer, Slot>> slotEntries,
                Container.SlotLayoutSpec slotLayoutSpec,
                boolean compactDisabledSlot
        ) {
            if (slotEntries.isEmpty()) return;

            Container container = binding.getContainerElement();
            ContainerMetricsCache metricsCache = resolveContainerMetrics(container, slotEntries);
            int slotPixelSize = metricsCache.slotPixelSize();
            LayoutArea area = resolveLayoutArea(container, anchor, slotPixelSize);
            int uniformGap = metricsCache.gap();

            ArrayList<SlotLayoutPlanner.SlotLayoutEntry> layoutEntries = new ArrayList<>(slotEntries.size());
            for (Map.Entry<Integer, Slot> entry : slotEntries) {
                Slot slotElement = entry.getValue();
                boolean disabled = slotElement.isDisabled() || container.isDisabled();
                layoutEntries.add(new SlotLayoutPlanner.SlotLayoutEntry(entry.getKey(), slotElement, disabled));
            }

            SlotLayoutPlanner.SlotLayoutContext context = new SlotLayoutPlanner.SlotLayoutContext(
                    screen.getGuiLeft(),
                    screen.getGuiTop(),
                    area.contentX(),
                    area.contentY(),
                    area.contentWidth(),
                    area.contentHeight(),
                    slotPixelSize,
                    uniformGap,
                    slotLayoutSpec,
                    compactDisabledSlot
            );
            SlotLayoutPlanner.SlotLayoutResult result;
            if (shouldUsePlayerPresetLayout(container, slotLayoutSpec)) {
                result = planPlayerPresetLayout(layoutEntries, context);
            } else {
                result = slotLayoutPlanner.plan(layoutEntries, context);
            }

            for (Map.Entry<Integer, Slot> entry : slotEntries) {
                SlotLayoutPlacement placement = result.get(entry.getKey());
                boolean disabled = entry.getValue().isDisabled() || container.isDisabled();
                applySlotPlacement(entry, placement, slotPixelSize, disabled, metricsCache.defaultBackgroundPath());
            }
        }

        private void applySlotPlacement(
                Map.Entry<Integer, Slot> entry,
                SlotLayoutPlacement placement,
                int fallbackSlotSize,
                boolean disabled,
                String defaultBackgroundPath
        ) {
            Slot slotElement = entry.getValue();
            ContainerSchema.Descriptor.SlotVisualProfile visualProfile = resolveDescriptorSlotVisualProfile(slotElement);
            boolean resolvedDisabled = visualProfile == null ? disabled : visualProfile.resolveDisabled(disabled);
            if (placement == null || placement.hidden()) {
                int hiddenSlotSize = visualProfile == null ? fallbackSlotSize : visualProfile.resolveSlotSize(fallbackSlotSize);
                hideRealSlotEntryLayout(entry, hiddenSlotSize, resolvedDisabled);
                slotElement.applyRuntimeLayout(-10000, -10000, hiddenSlotSize, true);
                removeSlotVisualState(entry.getKey());
                return;
            }

            int slotSizeFallback = visualProfile == null ? placement.slotSize() : visualProfile.resolveSlotSize(placement.slotSize());
            int resolvedSlotSize = slotElement.resolveSlotSizeHint(slotSizeFallback);
            applyRealSlotEntryLayout(entry, placement.menuX(), placement.menuY(), resolvedSlotSize, resolvedDisabled);
            slotElement.applyRuntimeLayout(placement.screenX(), placement.screenY(), resolvedSlotSize, false);
            updateSlotVisualState(entry.getKey(), slotElement, visualProfile, resolvedSlotSize, resolvedDisabled, defaultBackgroundPath);
        }

        private void applyRealSlotEntryLayout(Map.Entry<Integer, Slot> entry, int x, int y, int slotSize, boolean disabled) {
            net.minecraft.world.inventory.Slot slot = menuAdapter.getSlot(entry.getKey());
            if (slot == null) return;
            SlotAccessor slotAccessor = (SlotAccessor) slot;
            slotAccessor.setX(x);
            slotAccessor.setY(y);
            if (slot instanceof SlotRuntimeAccess runtimeAccess) {
                runtimeAccess.apricityui$setSlotSize(slotSize);
                runtimeAccess.apricityui$setUiDisabled(disabled);
            }
        }

        private void hideRealSlotEntryLayout(Map.Entry<Integer, Slot> entry, int fallbackSlotSize, boolean disabled) {
            net.minecraft.world.inventory.Slot slot = menuAdapter.getSlot(entry.getKey());
            if (slot == null) return;
            SlotAccessor slotAccessor = (SlotAccessor) slot;
            slotAccessor.setX(-10000);
            slotAccessor.setY(-10000);
            if (slot instanceof SlotRuntimeAccess runtimeAccess) {
                runtimeAccess.apricityui$setSlotSize(fallbackSlotSize);
                runtimeAccess.apricityui$setUiDisabled(disabled);
            }
        }

        private int resolveSlotPixelSize(Container container, List<Map.Entry<Integer, Slot>> slotEntries) {
            int fromAttribute = container.resolveSlotSizePx(DEFAULT_SLOT_PIXEL_SIZE);

            if (!slotEntries.isEmpty()) {
                Style style = slotEntries.get(0).getValue().getComputedStyle();
                int fromStyle = Math.max(Size.parse(style.width), Size.parse(style.height));
                if (fromStyle > 0) return fromStyle;
            }

            Slot probe = new Slot(container.document);
            probe.parentElement = container;
            probe.cssCache = Selector.matchCSS(probe);
            Style probeStyle = probe.getComputedStyle();
            int fromProbe = Math.max(Size.parse(probeStyle.width), Size.parse(probeStyle.height));
            if (fromProbe > 0) return fromProbe;

            return fromAttribute > 0 ? fromAttribute : DEFAULT_SLOT_PIXEL_SIZE;
        }

        private ContainerMetricsCache resolveContainerMetrics(Container container, List<Map.Entry<Integer, Slot>> slotEntries) {
            String fingerprint = buildContainerMetricsFingerprint(container, slotEntries);
            ContainerMetricsCache cache = null;
            Object cachedValue = container.getRuntimeCache(CONTAINER_METRICS_CACHE_KEY);
            if (cachedValue instanceof ContainerMetricsCache containerCache) {
                cache = containerCache;
            }
            if (cache != null && fingerprint.equals(cache.fingerprint())) {
                return cache;
            }

            int gap = container.resolveSlotGapPx(DEFAULT_SLOT_GAP);
            int slotPixelSize = resolveSlotPixelSize(container, slotEntries);
            String defaultBackgroundPath = resolveContainerDefaultBackgroundPath(container);

            ContainerMetricsCache resolved = new ContainerMetricsCache(fingerprint, slotPixelSize, gap, defaultBackgroundPath);
            container.putRuntimeCache(CONTAINER_METRICS_CACHE_KEY, resolved);
            return resolved;
        }

        private String buildContainerMetricsFingerprint(Container container, List<Map.Entry<Integer, Slot>> slotEntries) {
            String firstSlotWidth = "";
            String firstSlotHeight = "";
            if (!slotEntries.isEmpty()) {
                Style style = slotEntries.get(0).getValue().getComputedStyle();
                firstSlotWidth = style == null ? "" : String.valueOf(style.width);
                firstSlotHeight = style == null ? "" : String.valueOf(style.height);
            }
            Style containerStyle = container.getComputedStyle();
            String containerWidth = containerStyle == null ? "" : String.valueOf(containerStyle.width);
            String containerHeight = containerStyle == null ? "" : String.valueOf(containerStyle.height);
            return String.join("|",
                    String.valueOf(container.getAttribute("slot-size")),
                    String.valueOf(container.getAttribute("slot-gap")),
                    containerWidth,
                    containerHeight,
                    firstSlotWidth,
                    firstSlotHeight
            );
        }

        private String resolveContainerDefaultBackgroundPath(Container container) {
            Slot probe = new Slot(container.document);
            probe.parentElement = container;
            probe.cssCache = Selector.matchCSS(probe);
            Background probeBackground = Background.of(probe);
            String resolved = normalizeReadableBackgroundPath(probeBackground == null ? null : probeBackground.imagePath);
            return resolved == null ? "unset" : resolved;
        }

        private double resolveNonSlotOccupiedStartY(Element anchor, double currentContentY) {
            double occupiedBottom = currentContentY;
            for (Element child : anchor.children) {
                if (child instanceof Slot slot && slot.isBoundMode()) continue;
                Style style = child.getComputedStyle();
                if (style == null) continue;
                if ("none".equals(style.display)) continue;
                if ("absolute".equals(style.position) || "fixed".equals(style.position)) continue;

                Position position = Position.of(child);
                Box box = Box.of(child);
                Size size = Size.of(child);
                double childTop = position.y + box.getMarginTop();
                double childBottom = childTop + size.height() + box.getMarginBottom();
                if (childBottom > occupiedBottom) {
                    occupiedBottom = childBottom;
                }
            }
            return Math.max(currentContentY, occupiedBottom);
        }

        public void handleSlotMouseDown(Slot slot, MouseEvent mouseEvent) {
            if (slot.isDisabled()) {
                mouseEvent.stopPropagation();
                return;
            }
            focusedSlot = slot.getBoundSlotIndex();
        }

        public void handleSlotMouseUp(Slot slot, MouseEvent mouseEvent) {
            if (slot.isDisabled()) {
                mouseEvent.stopPropagation();
                return;
            }
        }

        public void clearHoveredSlot(int slotIndex) {
            if (hoveredSlot == slotIndex) hoveredSlot = -1;
        }

        private boolean shouldUsePlayerPresetLayout(Container container, Container.SlotLayoutSpec slotLayoutSpec) {
            if (!container.isPlayerBinding()) return false;
            if (container.isPlayerLayoutPreset()) return true;
            return !container.hasExplicitLayout();
        }

        private SlotLayoutPlanner.SlotLayoutResult planPlayerPresetLayout(List<SlotLayoutPlanner.SlotLayoutEntry> entries, SlotLayoutPlanner.SlotLayoutContext context) {
            SlotLayoutPlanner.SlotLayoutResult result = new SlotLayoutPlanner.SlotLayoutResult();
            if (entries.isEmpty()) return result;

            int slotSize = Math.max(1, context.slotPixelSize());
            int gap = Math.max(0, context.gap());
            int hotbarTopOffset = 3 * (slotSize + gap) + 4;

            ArrayList<SlotLayoutPlanner.SlotLayoutEntry> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingInt(entry -> {
                int local = entry.slotElement().getSlotIndex();
                return local >= 0 ? local : Integer.MAX_VALUE;
            }));

            int fallbackVisualIndex = 0;
            for (SlotLayoutPlanner.SlotLayoutEntry entry : sorted) {
                if (entry.disabled()) {
                    result.put(entry.globalSlotIndex(), SlotLayoutPlacement.hiddenGrid());
                    if (!context.compactDisabledSlot()) fallbackVisualIndex++;
                    continue;
                }

                int invIndex = entry.slotElement().getSlotIndex();
                int column;
                int rowTop;
                if (invIndex >= 0 && invIndex < 27) {
                    column = invIndex % 9;
                    rowTop = (invIndex / 9) * (slotSize + gap);
                } else if (invIndex >= 27 && invIndex < 36) {
                    column = invIndex - 27;
                    rowTop = hotbarTopOffset;
                } else {
                    column = fallbackVisualIndex % 9;
                    rowTop = (fallbackVisualIndex / 9) * (slotSize + gap);
                }

                int screenX = (int) Math.round(context.contentX() + column * (slotSize + gap));
                int screenY = (int) Math.round(context.contentY() + rowTop);
                int menuX = screenX - context.guiLeft();
                int menuY = screenY - context.guiTop();
                result.put(entry.globalSlotIndex(), SlotLayoutPlacement.grid(screenX, screenY, menuX, menuY, slotSize));
                fallbackVisualIndex++;
            }
            return result;
        }

        public List<SlotVisualState> getSlotVisualStates() {
            if (slotVisualStateSnapshotDirty) {
                slotVisualStateSnapshot = List.copyOf(slotVisualStates.values());
                slotVisualStateSnapshotDirty = false;
            }
            return slotVisualStateSnapshot;
        }

        public Slot getBoundSlotElement(int globalSlotIndex) {
            return slotElementByGlobalIndex.get(globalSlotIndex);
        }

        public boolean ensureBound(Document document) {
            if (document == null) {
                close();
                return false;
            }
            if (bindings.isEmpty()) {
                bind(document);
                return true;
            }
            if (needsRebind(document)) {
                bind(document);
                return true;
            }
            return false;
        }

        private boolean needsRebind(Document document) {
            if (document == null) return true;
            if (boundDocument != document) return true;
            if (document.body == null) return true;

            List<Element> liveElements = document.getElements();
            HashSet<UUID> liveIds = new HashSet<>(liveElements.size());
            for (Element element : liveElements) {
                if (element != null) liveIds.add(element.uuid);
            }

            for (Binding binding : bindings.values()) {
                Container container = binding.getContainerElement();
                if (container == null || !liveIds.contains(container.uuid)) return true;
                for (Slot slot : binding.getSlots().values()) {
                    if (slot == null) return true;
                    if (slot.document != document) return true;
                    if (slot.isRuntimeRepeatProxy()) continue;
                    if (!liveIds.contains(slot.uuid)) return true;
                }
            }
            return false;
        }

        private void updateSlotVisualState(
                int globalSlotIndex,
                Slot slotElement,
                ContainerSchema.Descriptor.SlotVisualProfile visualProfile,
                int slotSize,
                boolean disabled,
                String defaultBackgroundPath
        ) {
            boolean acceptPointer = visualProfile == null || visualProfile.resolveAcceptPointer(true);
            Boolean runtimePointer = SlotVisualRules.parsePointerFlag(resolveFirstNonBlankSlotAttribute(
                    slotElement,
                    "pointer",
                    "accept-pointer",
                    "acceptpointer",
                    "acceptPointer",
                    "hit"
            ));
            if (runtimePointer != null) {
                acceptPointer = runtimePointer;
            }
            slotElement.isPointerEnabled = acceptPointer;
            boolean renderBackground = visualProfile == null || visualProfile.resolveRenderBackground(true);
            boolean renderItem = visualProfile == null || visualProfile.resolveRenderItem(true);
            SlotVisualRules.RenderRule runtimeRenderRule = SlotVisualRules.parseRenderRule(resolveFirstNonBlankSlotAttribute(slotElement, "render"));
            if (runtimeRenderRule != null) {
                renderBackground = runtimeRenderRule.renderBackground();
                renderItem = runtimeRenderRule.renderItem();
            }

            float iconScale = visualProfile == null ? 1.0F : visualProfile.resolveIconScale(1.0F);
            Float runtimeIconScale = SlotVisualRules.parsePositiveFloat(resolveFirstNonBlankSlotAttribute(
                    slotElement,
                    "icon-scale",
                    "iconscale",
                    "iconScale",
                    "scale"
            ));
            if (runtimeIconScale != null) {
                iconScale = runtimeIconScale;
            }

            int padding = visualProfile == null ? 0 : visualProfile.resolvePadding(0);
            Integer runtimePadding = SlotVisualRules.parseNonNegativeInt(resolveFirstNonBlankSlotAttribute(slotElement, "padding"));
            if (runtimePadding != null) {
                padding = runtimePadding;
            }

            int zIndex = visualProfile == null ? 0 : visualProfile.resolveZIndex(0);
            Integer runtimeZIndex = SlotVisualRules.parseSignedInt(resolveFirstNonBlankSlotAttribute(
                    slotElement,
                    "z-index",
                    "zindex",
                    "zIndex",
                    "z"
            ));
            if (runtimeZIndex != null) {
                zIndex = runtimeZIndex;
            }

            String extraClasses = visualProfile == null ? null : visualProfile.resolveExtraClasses(null);
            String runtimeClasses = resolveFirstNonBlankSlotAttribute(slotElement, "class");
            if (runtimeClasses != null) {
                extraClasses = runtimeClasses;
            }
            String backgroundPath = resolveSlotBackgroundPath(slotElement, defaultBackgroundPath);
            slotVisualStates.put(globalSlotIndex, new SlotVisualState(
                    globalSlotIndex,
                    slotSize,
                    false,
                    disabled,
                    acceptPointer,
                    renderBackground,
                    renderItem,
                    iconScale,
                    padding,
                    zIndex,
                    backgroundPath,
                    extraClasses
            ));
            slotVisualStateSnapshotDirty = true;
        }

        private String resolveFirstNonBlankSlotAttribute(Slot slotElement, String... keys) {
            if (slotElement == null || keys == null || keys.length == 0) return null;
            for (String key : keys) {
                if (key == null || key.isBlank()) continue;
                String value = slotElement.getAttribute(key);
                if (value == null || value.isBlank()) continue;
                return value;
            }
            return null;
        }

        private ContainerSchema.Descriptor.SlotVisualProfile resolveDescriptorSlotVisualProfile(Slot slotElement) {
            if (slotElement == null || !slotElement.hasValidBinding()) return null;
            String containerId = slotElement.getBoundContainerId();
            if (containerId == null || containerId.isBlank()) return null;
            int localSlotIndex = slotElement.getSlotIndex();
            if (localSlotIndex < 0) return null;
            return menuAdapter.getDescriptor().getContainerSlotVisual(containerId, localSlotIndex);
        }

        private void removeSlotVisualState(int globalSlotIndex) {
            if (slotVisualStates.remove(globalSlotIndex) != null) {
                slotVisualStateSnapshotDirty = true;
            }
        }

        private String resolveSlotBackgroundPath(Slot slotElement, String defaultBackgroundPath) {
            String resolved = normalizeReadableBackgroundPath(slotElement.getBackgroundImageCandidate());
            if (resolved != null) return resolved;
            return defaultBackgroundPath == null ? "unset" : defaultBackgroundPath;
        }

        private String normalizeReadableBackgroundPath(String rawPath) {
            if (rawPath == null || rawPath.isBlank() || "unset".equals(rawPath)) return null;
            if (isBackgroundReadable(rawPath)) return rawPath;
            return null;
        }

        private boolean isBackgroundReadable(String path) {
            Boolean cached = backgroundReadableCache.get(path);
            if (cached != null) return cached;

            if (Loader.isRemotePath(path)) {
                backgroundReadableCache.put(path, true);
                return true;
            }

            boolean readable;
            try (InputStream ignored = Loader.getResourceStream(path)) {
                readable = ignored != null;
            } catch (IOException ignored) {
                readable = false;
            }
            backgroundReadableCache.put(path, readable);
            return readable;
        }

        private void resetBindings() {
            for (Binding binding : bindings.values()) {
                binding.getContainerElement().removeRuntimeCache(CONTAINER_METRICS_CACHE_KEY);
                for (Slot slot : binding.getSlots().values()) {
                    slot.unbindFromSlot();
                }
            }
            bindings.clear();
            containerPartitionByUuid.clear();
            slotElementByGlobalIndex.clear();
            bindingRuntimeCaches.clear();
            slotVisualStates.clear();
            slotVisualStateSnapshot = List.of();
            slotVisualStateSnapshotDirty = true;
            backgroundReadableCache.clear();
            hoveredSlot = -1;
            focusedSlot = -1;
            layoutDirty = true;
        }

        public void close() {
            resetBindings();
            boundDocument = null;
        }

        public int getFocusedSlot() {
            return focusedSlot;
        }

        public enum DirtyReason {
            BIND,
            SCREEN_INIT,
            DOCUMENT_RELAYOUT,
            RECIPE_PREVIEW,
            EXTERNAL
        }

        private record BindingRuntimeCache(
                int slotCount,
                LinkedHashMap<Element, List<Integer>> anchorToSortedGlobalSlots
        ) {
        }

        private record ContainerMetricsCache(
                String fingerprint, int slotPixelSize, int gap,
                String defaultBackgroundPath
        ) {
        }

        public record SlotVisualState(
                int globalSlotIndex,
                int slotSize,
                boolean hidden,
                boolean disabled,
                boolean acceptPointer,
                boolean renderBackground,
                boolean renderItem,
                float iconScale,
                int padding,
                int zIndex,
                String backgroundPath,
                String extraClasses
        ) {
        }

        private record LayoutArea(double contentX, double contentY, double contentWidth, double contentHeight) {
        }
    }
}
