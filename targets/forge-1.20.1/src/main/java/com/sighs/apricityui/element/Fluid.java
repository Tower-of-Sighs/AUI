package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;

@ElementRegister(Fluid.TAG_NAME)
public final class Fluid extends Stack {
    public static final String TAG_NAME = "FLUID";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Fluid(document));
    }

    public Fluid(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public boolean accepts(GenericKey key) {
        return key instanceof FluidKey;
    }

    @Override
    protected GenericStack parseLocalStack(String expression) {
        return GenericStackExpressionCompiler.parse(expression, "fluid", parseAmountAttribute());
    }
}
