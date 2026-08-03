package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Document;

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
}
