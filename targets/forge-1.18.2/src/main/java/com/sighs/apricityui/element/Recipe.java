package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.dom.expander.RecipeExpander;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@ElementRegister(Recipe.TAG_NAME)
public class Recipe extends MinecraftElement implements BodyRenderNodeProvider {
    public static final String TAG_NAME = "RECIPE";
    public final RecipeExpander.PreviewState previewState = new RecipeExpander.PreviewState();
    private List<Node> sourceRenderChildren;
    private List<Node> previewRenderChildren = List.of();

    public Recipe(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void setTextContent(String value) {
        super.setTextContent(value);
        previewState.invalidate();
    }

    @Override
    public void tick() {
        super.tick();
        previewState.update(this, System.nanoTime() / 1_000_000L, false);
    }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(new RenderNode.ElementBackgroundNode(this));
    }

    @Override
    public List<Node> getRenderChildNodes() {
        List<Node> nodes = super.getRenderChildNodes();
        if (nodes != sourceRenderChildren) {
            sourceRenderChildren = nodes;
            previewRenderChildren = nodes.stream().filter(node -> !(node instanceof TextNode)).toList();
        }
        return previewRenderChildren;
    }

    public String getOutputItemExpression() {
        StringBuilder text = new StringBuilder();
        for (Node child : childNodes) {
            if (child instanceof TextNode textNode) text.append(textNode.getTextContent());
        }
        return text.isEmpty() ? innerText : text.toString();
    }

    public ResourceLocation parseOutputItemId() {
        String normalized = normalizeOutputItemLiteral(getOutputItemExpression());
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

    public static String normalizeOutputItemLiteral(String raw) {
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
