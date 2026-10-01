package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.dom.expander.ContainerExpander;
import com.sighs.apricityui.dom.expander.RecipeExpander;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

public final class FabricDocumentExpander implements DocumentExpander {
    @Override
    public void apply(Document document) {
        if (document == null) return;
        String templatePath = document.getPath();
        try {
            SlotContentRules.normalizeTemplate(document);
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("SlotContentRules normalization failed, template={}", templatePath, exception);
        }
        try {
            ContainerExpander.expand(document);
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("ContainerExpander failed, template={}", templatePath, exception);
        }
        try {
            RecipeExpander.expand(document);
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("RecipeExpander failed, template={}", templatePath, exception);
        }
    }

    @Override
    public void validateRuntimeInsertion(Document document, Node parent, Node child) {
        SlotContentRules.validateRuntimeInsertion(parent, child);
    }

    @Override
    public void normalizeRuntimeChildren(Document document, Node parent) {
        SlotContentRules.normalizeRuntimeChildren(parent);
    }

    @Override
    public void restoreRequiredContent(Document document, Node parent) {
        SlotContentRules.restoreRequiredContent(parent);
    }
}
