package com.sighs.apricityui.instance.dom.expander;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.dom.ExpandContext;
import com.sighs.apricityui.instance.element.Recipe;
import com.sighs.apricityui.instance.element.Slot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 在文档刷新阶段触发 recipe DOM 预览槽位生成。
 */
public final class RecipeExpander {
    private RecipeExpander() {
    }

    public static void expand(Document document, ExpandContext context) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Recipe recipe)) continue;
            expandSingleRecipe(document, recipe);
        }
    }

    private static void expandSingleRecipe(Document document, Recipe recipe) {
        boolean changed = recipe.clearGeneratedRecipeSlots();

        RecipeResolver.DeclaredType declaredType = RecipeResolver.DeclaredType.fromRaw(recipe.getAttribute("type"));
        if (declaredType == null) {
            String message = "Recipe preview skipped: missing/invalid type";
            ApricityUI.LOGGER.warn("{}, type={}", message, recipe.getAttribute("type"));
            changed |= setAttributeIfChanged(recipe, "data-recipe-type", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-layout", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", message);
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        changed |= setAttributeIfChanged(recipe, "data-recipe-type", declaredType.id());

        ResourceLocation recipeId = recipe.parseRecipeIdFromInnerText();
        if (recipeId == null) {
            String message = "Recipe preview skipped: invalid recipe id in innerText";
            ApricityUI.LOGGER.warn("{}, innerText={}", message, recipe.innerText);
            changed |= setAttributeIfChanged(recipe, "data-recipe-layout", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", message);
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        RecipeResolver.ResolveResult preview = RecipeResolver.resolve(recipeId, declaredType);
        changed |= setAttributeIfChanged(
                recipe,
                "data-recipe-layout",
                preview.layoutKind().name().toLowerCase(Locale.ROOT)
        );

        if (preview.absoluteEntries().isEmpty() && preview.listEntries().isEmpty()) {
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", preview.message());
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        changed |= setAttributeIfChanged(recipe, "data-recipe-error", "");

        EnumMap<RecipeResolver.PreviewRole, Integer> roleOrderCursor = new EnumMap<>(RecipeResolver.PreviewRole.class);
        int appendedCount = 0;
        for (RecipeResolver.PreviewEntry entry : preview.absoluteEntries()) {
            appendPreviewSlot(document, recipe, entry, nextRoleIndex(roleOrderCursor, entry.role()), "absolute");
            appendedCount++;
        }
        if (preview.layoutKind() == RecipeResolver.LayoutKind.STONECUTTING) {
            int visibleCount = Math.min(RecipeResolver.STONECUTTING_LIST_VISIBLE_ROWS, preview.listEntries().size());
            for (int index = 0; index < visibleCount; index++) {
                RecipeResolver.PreviewEntry entry = preview.listEntries().get(index);
                appendPreviewSlot(document, recipe, entry, nextRoleIndex(roleOrderCursor, entry.role()), "list");
                appendedCount++;
            }
        }
        if (appendedCount > 0) {
            changed = true;
        }
        if (changed) {
            document.markDirty(recipe, Drawer.RELAYOUT);
        }
    }

    private static int nextRoleIndex(
            EnumMap<RecipeResolver.PreviewRole, Integer> roleOrderCursor,
            RecipeResolver.PreviewRole role
    ) {
        int next = roleOrderCursor.getOrDefault(role, 0);
        roleOrderCursor.put(role, next + 1);
        return next;
    }

    private static String toRoleClass(RecipeResolver.PreviewRole role) {
        if (role == RecipeResolver.PreviewRole.FUEL) return "aui-recipe-fuel";
        if (role == RecipeResolver.PreviewRole.OUTPUT) return "aui-recipe-output";
        return "aui-recipe-input";
    }

    private static void appendPreviewSlot(
            Document document,
            Recipe recipe,
            RecipeResolver.PreviewEntry entry,
            int roleIndex,
            String group
    ) {
        if (entry == null) return;
        Slot slot = new Slot(document);
        String roleName = entry.role().name().toLowerCase(Locale.ROOT);
        slot.applyRecipeSlotMeta(
                "recipe-slot recipe-slot-" + roleName
                        + " aui-recipe-slot "
                        + toRoleClass(entry.role())
                        + " aui-recipe-" + roleName,
                "recipe-slot"
        );
        slot.setAttributesBatch(Map.of(
                "data-role", roleName,
                "data-i", String.valueOf(Math.max(0, roleIndex)),
                "data-group", group == null ? "absolute" : group,
                "interactive", "0",
                "pointer", "0",
                "style", "--aui-slot-interactive:0;"
        ), true);
        slot.innerText = entry.slotExpression();
        recipe.append(slot);
    }

    private static boolean setAttributeIfChanged(Recipe recipe, String key, String value) {
        String normalized = value == null ? "" : value;
        if (Objects.equals(recipe.getAttribute(key), normalized)) return false;
        recipe.setAttribute(key, normalized);
        return true;
    }
}
