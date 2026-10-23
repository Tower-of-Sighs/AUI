package com.sighs.apricityui.util;

import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;

/**
 * Built-in Markdown rendering, backed by Txtmark 0.13 (Apache-2.0).
 *
 * <p>Uses Txtmark's extended profile so fenced code blocks, anchors and
 * paragraph-interrupting lists behave as authors expect. The library is merged
 * into the mod jar by {@code gradle/target-conventions/publish.gradle}, so no
 * extra mod or runtime dependency is required.</p>
 *
 * <p>Txtmark passes raw HTML through by default. {@link #render(String)} is for
 * trusted content (bundled docs, local files). {@link #renderSafe(String)}
 * enables Txtmark's safe mode, but that mode only escapes a small blacklist of
 * container tags (script/iframe/object/applet/frame/head/html/body); it does
 * <b>not</b> remove inline event handlers such as {@code onerror} or
 * {@code javascript:} URLs. It is defense in depth, not an HTML sanitizer —
 * untrusted Markdown must be sanitized by the caller before it reaches an AUI
 * document.</p>
 */
public final class Markdown {
    private static final Configuration EXTENDED = Configuration.builder()
            .forceExtentedProfile()
            .build();
    private static final Configuration EXTENDED_SAFE = Configuration.builder()
            .forceExtentedProfile()
            .enableSafeMode()
            .build();

    private Markdown() {
    }

    /**
     * Renders trusted Markdown to HTML. Raw HTML in the source is passed
     * through unchanged.
     */
    public static String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        return Processor.process(markdown, EXTENDED);
    }

    /**
     * Renders Markdown with Txtmark's safe mode. Only the blacklisted container
     * tags listed above are escaped; this is not a sanitizer.
     */
    public static String renderSafe(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        return Processor.process(markdown, EXTENDED_SAFE);
    }
}
