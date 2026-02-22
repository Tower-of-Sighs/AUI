package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID)
public class Body extends Div {
    public static final String TAG_NAME = "BODY";

    static {
        Element.register(TAG_NAME, (document, string) -> new Body(document));
    }

    public Body(Document document) {
        super(document);
        tagName = TAG_NAME;
    }
}
