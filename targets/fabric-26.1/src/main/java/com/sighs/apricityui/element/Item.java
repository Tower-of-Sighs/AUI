package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.stack.BuiltinStackTypes;
import com.sighs.apricityui.stack.GenericStackType;

@ElementRegister(Item.TAG_NAME)
public class Item extends GenericStackElement {
    public static final String TAG_NAME = "ITEM";

    public Item(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public GenericStackType<?> type() {
        return BuiltinStackTypes.ITEM;
    }
}
