package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    static {
        Element.register(TAG_NAME, (document, string) -> new Container(document));
    }

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
