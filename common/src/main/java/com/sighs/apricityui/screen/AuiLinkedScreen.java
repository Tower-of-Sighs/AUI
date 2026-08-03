package com.sighs.apricityui.screen;

import com.sighs.apricityui.init.Document;

/**
 * Marker for screens that own a WebUI {@link Document}.
 *
 * <p>Both the loader-neutral {@code ApricityScreen} and the loader-specific
 * container screen implement this, so common DevTools code can retrieve the
 * linked document without referencing the loader screen classes.</p>
 */
public interface AuiLinkedScreen {
    Document getLinkedDocument();
}
