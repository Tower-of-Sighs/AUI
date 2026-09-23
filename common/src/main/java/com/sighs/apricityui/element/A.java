package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.spi.AuiServices;

import java.net.URI;

@ElementRegister(A.TAG_NAME)
public class A extends Element {
    public static final String TAG_NAME = "A";

    public A(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected boolean hasClickActivationBehavior() {
        String href = getAttribute("href");
        return href != null && !href.isBlank();
    }

    @Override
    public void handleClickDefault() {
        String href = getAttribute("href");
        if (href == null || href.isBlank()) return;
        try {
            AuiServices.client().openUri(new URI(href.trim()));
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("Failed to open href: {}", href, exception);
        }
    }
}
