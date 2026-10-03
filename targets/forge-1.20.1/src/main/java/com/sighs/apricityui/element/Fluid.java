package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.stack.FluidStackType;
import com.sighs.apricityui.stack.GenericStackType;

@GenericStackElementType(FluidStackType.class)
@ElementRegister(Fluid.TAG_NAME)
public final class Fluid extends TypedGenericStackElement {
    public static final String TAG_NAME = "FLUID";

    public Fluid(Document document, GenericStackType<?> type) {
        super(document, TAG_NAME, type);
    }
}

