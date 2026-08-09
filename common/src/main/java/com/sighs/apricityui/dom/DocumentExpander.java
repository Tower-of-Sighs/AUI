package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

/**
 * One-time document expansion entry point run after a document refresh.
 *
 * <p>The concrete loader-side implementation applies container and recipe
 * expansion (recipe expansion requires the Minecraft recipe manager, so it
 * lives in the loader). Registered through
 * {@link com.sighs.apricityui.spi.AuiServices}.</p>
 */
public interface DocumentExpander {
    void apply(Document document);

    /**
     * Validates loader-specific DOM hierarchy before a connected runtime insertion.
     * Implementations may replace incompatible siblings before the insertion.
     */
    default void validateRuntimeInsertion(Document document, Node parent, Node child) {
    }

    /**
     * Normalizes loader-specific child constraints after a connected fragment insertion.
     */
    default void normalizeRuntimeChildren(Document document, Node parent) {
    }

    /**
     * Restores loader-specific required children after a connected runtime removal.
     */
    default void restoreRequiredContent(Document document, Node parent) {
    }
}
