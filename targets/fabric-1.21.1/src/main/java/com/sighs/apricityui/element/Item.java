package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.ItemStackType;

@GenericStackElementType(ItemStackType.class)
@ElementRegister(Item.TAG_NAME)
public final class Item extends TypedGenericStackElement {
    public static final String TAG_NAME = "ITEM";

    public Item(Document document, GenericStackType<?> type) {
        super(document, TAG_NAME, type);
    }
}

