package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.stack.BuiltinStackTypes;
import com.sighs.apricityui.stack.GenericStackType;

@ElementRegister(Fluid.TAG_NAME)
public final class Fluid extends GenericStackElement {
    public static final String TAG_NAME = "FLUID";

    public Fluid(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public GenericStackType<?> type() {
        return BuiltinStackTypes.FLUID;
    }
}
