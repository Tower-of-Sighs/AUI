package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID)
public class Span extends Element {
    public static final String TAG_NAME = "SPAN";

    static {
        Element.register(TAG_NAME, ((document, string) -> new Span(document)));
    }

    public Span(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + innerText + ")";
    }
}
