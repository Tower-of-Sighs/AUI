package com.sighs.apricityui.form;

/**
 * A small DOM-compatible snapshot of a form control's constraint state.
 */
public record ValidityState(boolean badInput, boolean customError, boolean patternMismatch, boolean rangeOverflow,
                            boolean rangeUnderflow, boolean stepMismatch, boolean tooLong, boolean tooShort,
                            boolean typeMismatch, boolean valueMissing, boolean valid) {
}
