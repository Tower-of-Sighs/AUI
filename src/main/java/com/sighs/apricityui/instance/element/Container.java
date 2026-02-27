package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Container.TAG_NAME)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    public Container(Document document) {
        super(document, TAG_NAME);
    }

    public int resolveSlotSizePx(int fallback) {
        int safeFallback = Math.max(1, fallback);
        String rawSlotSize = getAttribute("slot-size");
        int parsedSize = com.sighs.apricityui.style.Size.parse(rawSlotSize);
        return parsedSize > 0 ? parsedSize : safeFallback;
    }
}
