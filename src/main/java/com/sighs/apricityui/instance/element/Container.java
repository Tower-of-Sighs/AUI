package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.registry.annotation.ElementRegister;

import java.util.Locale;

@ElementRegister(Container.TAG_NAME)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    public Container(Document document) {
        super(document, TAG_NAME);
    }

    public boolean isPrimaryContainer() {
        String rawPrimary = getAttribute("primary");
        if (rawPrimary == null) return false;
        return "true".equals(rawPrimary.trim().toLowerCase(Locale.ROOT));
    }

    public String getTemplateOverride() {
        return getAttribute("template");
    }

    public String getFirstChildTitleLiteral() {
        for (Element child : children) {
            if (child == null) continue;
            String childText = child.innerText;
            if (childText == null || childText.isBlank()) return null;
            return childText.trim();
        }
        return null;
    }

    public boolean isDisabled() {
        return Boolean.parseBoolean(getAttribute("disabled"));
    }

    public ContainerSchema.Descriptor.BindType getBindType() {
        String rawBind = getAttribute("bind");
        if (rawBind == null || rawBind.isBlank()) return null;
        return ContainerSchema.Descriptor.resolveBindType(rawBind);
    }

    public boolean isPlayerBinding() {
        return ContainerSchema.Descriptor.isPlayerBind(getBindType());
    }

    public boolean isEntityBinding() {
        return ContainerSchema.Descriptor.isEntityBind(getBindType());
    }

    public boolean hasExplicitLayout() {
        String rawLayout = getAttribute("layout");
        return rawLayout != null && !rawLayout.isBlank();
    }

    public boolean isPlayerLayoutPreset() {
        String rawLayout = getAttribute("layout");
        if (rawLayout == null || rawLayout.isBlank()) return false;
        String normalized = rawLayout.trim().toLowerCase(Locale.ROOT);
        return "preset:player".equals(normalized) || "player".equals(normalized);
    }

    public SlotLayoutSpec getLayoutSpec() {
        String rawLayoutConfig = getAttribute("layout");
        if (rawLayoutConfig == null || rawLayoutConfig.isBlank()) return null;

        String text = rawLayoutConfig.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        if ("preset:player".equals(normalized) || "player".equals(normalized)) {
            return null;
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.isBlank()) return null;

        String[] parts = text.split(",");
        if (parts.length == 0 || parts.length > 3) return null;

        Integer count = parsePositive(parts[0]);
        if (count == null) return null;

        Integer rows = null;
        Integer columns = null;
        if (parts.length >= 2) {
            rows = parsePositive(parts[1]);
            if (rows == null) return null;
        }
        if (parts.length >= 3) {
            columns = parsePositive(parts[2]);
            if (columns == null) return null;
        }

        return new SlotLayoutSpec(count, rows, columns);
    }

    public SlotLayoutSpec getEffectiveSlotLayoutSpec() {
        SlotLayoutSpec declared = getLayoutSpec();
        if (declared != null) return declared;
        if (isPlayerBinding()) {
            return new SlotLayoutSpec(36, 4, 9);
        }
        return null;
    }

    public int resolveSlotGapPx(int fallback) {
        int safeFallback = Math.max(0, fallback);
        String rawGap = getAttribute("slot-gap");
        int parsedGap = com.sighs.apricityui.style.Size.parse(rawGap);
        return parsedGap < 0 ? safeFallback : parsedGap;
    }

    public int resolveSlotSizePx(int fallback) {
        int safeFallback = Math.max(1, fallback);
        String rawSlotSize = getAttribute("slot-size");
        int parsedSize = com.sighs.apricityui.style.Size.parse(rawSlotSize);
        return parsedSize > 0 ? parsedSize : safeFallback;
    }

    private Integer parsePositive(String raw) {
        if (raw == null) return null;
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record SlotLayoutSpec(int count, Integer rows, Integer columns) {
    }
}
