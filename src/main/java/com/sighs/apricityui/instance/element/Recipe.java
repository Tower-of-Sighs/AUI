package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@ElementRegister(Recipe.TAG_NAME)
public class Recipe extends MinecraftElement {
    public static final String TAG_NAME = "RECIPE";

    public Recipe(Document document) {
        super(document, TAG_NAME);
    }

    public ResourceLocation parseRecipeIdFromInnerText() {
        String normalized = normalizeRecipeIdLiteral(innerText);
        if (normalized.isBlank()) return null;
        return ResourceLocation.tryParse(normalized);
    }

    public boolean clearGeneratedRecipeSlots() {
        boolean changed = false;
        List<Element> childrenSnapshot = new ArrayList<>(children);
        for (Element child : childrenSnapshot) {
            String generatedTag = child.getAttribute("data-generated");
            if (generatedTag == null || generatedTag.isBlank()) continue;
            if (!generatedTag.startsWith("recipe")) continue;
            child.remove();
            changed = true;
        }
        return changed;
    }

    public static String normalizeRecipeIdLiteral(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim();
        if (normalized.isBlank()) return "";
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            boolean quoted = (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (quoted) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        return normalized;
    }
}
