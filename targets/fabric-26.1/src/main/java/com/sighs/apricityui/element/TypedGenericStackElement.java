package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.stack.GenericStackType;

import java.util.Objects;

public abstract class TypedGenericStackElement extends GenericStackElement {
    private final GenericStackType<?> type;

    protected TypedGenericStackElement(Document document, String tagName, GenericStackType<?> type) {
        super(document, tagName);
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public final GenericStackType<?> type() {
        return type;
    }
}

