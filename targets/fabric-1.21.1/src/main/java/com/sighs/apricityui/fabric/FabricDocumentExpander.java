package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.dom.expander.ContainerExpander;
import com.sighs.apricityui.dom.expander.RecipeExpander;
import com.sighs.apricityui.init.Document;

public final class FabricDocumentExpander implements DocumentExpander {
    public void apply(Document document) {
        if (document == null) return;
        try { ContainerExpander.expand(document); }
        catch (Exception exception) { ApricityUI.LOGGER.warn("Container expansion failed for {}", document.getPath(), exception); }
        try { RecipeExpander.expand(document); }
        catch (Exception exception) { ApricityUI.LOGGER.warn("Recipe expansion failed for {}", document.getPath(), exception); }
    }
}
