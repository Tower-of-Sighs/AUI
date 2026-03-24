package com.sighs.apricityui.instance.dom.expander;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Recipe;
import com.sighs.apricityui.instance.element.Slot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将 container 的自动槽位前移到 Document 刷新阶段生成。
 */
public final class ContainerExpander {
    private static final String GENERATED_CONTAINER_AUTO = "container-auto";
    private static final int PLAYER_AUTO_SLOT_COUNT = 36;

    public static void expand(Document document) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Container container)) continue;
            if (!isBindableContainer(container)) continue;
            expandSingleContainer(document, container);
        }
    }

    private static void expandSingleContainer(Document document, Container container) {
        List<Slot> ownedSlots = collectOwnedSlots(document, container);
        if (ownedSlots.isEmpty()) {
            Integer declaredSize = parsePositiveInt(container.getAttribute("size"));
            String bindType = normalize(container.getAttribute("bind"));
            if (declaredSize != null) {
                appendAutoSlots(document, container, declaredSize, false);
            } else if ("player".equals(bindType)) {
                appendAutoSlots(document, container, PLAYER_AUTO_SLOT_COUNT, true);
            }
            ownedSlots = collectOwnedSlots(document, container);
        }

        normalizeSlotIndices(ownedSlots, document, container);
        injectDefaultColumnsIfNeeded(container, ownedSlots.size());
    }

    private static void appendAutoSlots(Document document, Container container, int count, boolean playerAuto) {
        int safeCount = Math.max(0, count);
        for (int index = 0; index < safeCount; index++) {
            Slot slot = new Slot(document);
            LinkedHashMap<String, String> attrs = new LinkedHashMap<>();
            attrs.put("index", String.valueOf(index));
            attrs.put("slot-index", String.valueOf(index));
            attrs.put("data-generated", GENERATED_CONTAINER_AUTO);
            if (playerAuto) {
                attrs.put("part", index < 27 ? "inv" : "hotbar");
            }
            slot.setAttributesBatch(attrs, true);
            container.append(slot);
        }
    }

    private static List<Slot> collectOwnedSlots(Document document, Container container) {
        ArrayList<Slot> result = new ArrayList<>();
        if (document == null || container == null) return result;
        for (Element element : document.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            if (slot.findAncestor(Recipe.class) != null) continue;
            Container owner = slot.findAncestor(Container.class);
            if (owner != container) continue;
            result.add(slot);
        }
        return result;
    }

    private static void normalizeSlotIndices(List<Slot> slots, Document document, Container container) {
        if (slots == null || slots.isEmpty()) return;
        Set<Integer> usedIndices = new HashSet<>();
        ArrayList<Slot> missing = new ArrayList<>();
        for (Slot slot : slots) {
            int slotIndex = slot.getSlotIndex();
            if (slotIndex >= 0) {
                usedIndices.add(slotIndex);
                ensureIndexAttributes(slot, slotIndex);
            } else {
                missing.add(slot);
            }
        }
        int nextIndex = 0;
        for (Slot slot : missing) {
            while (usedIndices.contains(nextIndex)) nextIndex++;
            int assigned = nextIndex;
            usedIndices.add(assigned);
            ensureIndexAttributes(slot, assigned);
            ApricityUI.LOGGER.warn(
                    "ContainerExpander assigned implicit slot index, template={}, containerId={}, assignedIndex={}",
                    document == null ? "" : document.getPath(),
                    container == null ? "" : container.getAttribute("id"),
                    assigned
            );
        }
    }

    private static void ensureIndexAttributes(Slot slot, int index) {
        if (slot == null || index < 0) return;
        String target = String.valueOf(index);
        if (!target.equals(slot.getAttribute("index"))) {
            slot.setAttribute("index", target);
        }
        if (!target.equals(slot.getAttribute("slot-index"))) {
            slot.setAttribute("slot-index", target);
        }
    }

    private static void injectDefaultColumnsIfNeeded(Container container, int slotCount) {
        if (container == null || slotCount <= 0) return;
        boolean hasGridTemplateColumns = hasCustomValue(container.getComputedStyle().gridTemplateColumns);
        boolean hasContainerColumns = hasCustomValue(container.getCustomProperty("--aui-container-columns"));
        if (hasGridTemplateColumns || hasContainerColumns) return;

        int effectiveColumns = Math.max(1, Math.min(9, slotCount));
        String mergedStyle = appendStyle(
                container.getAttribute("style"),
                "--aui-container-columns-effective:%d;grid-template-columns:%d;".formatted(effectiveColumns, effectiveColumns)
        );
        container.setAttribute("style", mergedStyle);
    }

    private static boolean isBindableContainer(Container container) {
        if (container == null) return false;
        String bind = normalize(container.getAttribute("bind"));
        if (!bind.isBlank()) return true;
        Boolean primary = parseBooleanLike(container.getAttribute("primary"));
        return primary != null && primary;
    }

    private static String appendStyle(String inlineStyle, String declaration) {
        String base = inlineStyle == null ? "" : inlineStyle.trim();
        String patch = declaration == null ? "" : declaration.trim();
        if (patch.isBlank()) return base;
        if (!patch.endsWith(";")) patch += ";";
        if (base.isBlank()) return patch;
        if (!base.endsWith(";")) base += ";";
        return base + patch;
    }

    private static boolean hasCustomValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && !"unset".equals(normalized) && !"auto".equals(normalized);
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static Boolean parseBooleanLike(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "unset".equals(normalized) || "auto".equals(normalized)) return null;
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> true;
            case "0", "false", "no", "off", "disabled", "none" -> false;
            default -> null;
        };
    }
}
