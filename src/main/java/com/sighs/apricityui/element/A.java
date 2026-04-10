package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import net.minecraft.util.Util;

import java.net.URI;

@ElementRegister(A.TAG_NAME)
public class A extends Element {
    public static final String TAG_NAME = "A";

    public A(Document document) {
        super(document, TAG_NAME);
        addEventListener("mouseup", event -> {
            String href = getAttribute("href");
            if (href == null || href.isBlank()) return;
            try {
                Util.getPlatform().openUri(new URI(href.trim()));
            } catch (Exception e) {
                ApricityUI.LOGGER.warn("Failed to open href: {}", href, e);
            }
        });
    }
}
