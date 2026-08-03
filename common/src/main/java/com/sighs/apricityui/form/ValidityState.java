package com.sighs.apricityui.form;

/** A small DOM-compatible snapshot of a form control's constraint state. */
public final class ValidityState {
    public final boolean badInput;
    public final boolean customError;
    public final boolean patternMismatch;
    public final boolean rangeOverflow;
    public final boolean rangeUnderflow;
    public final boolean stepMismatch;
    public final boolean tooLong;
    public final boolean tooShort;
    public final boolean typeMismatch;
    public final boolean valueMissing;
    public final boolean valid;

    public ValidityState(boolean badInput, boolean customError, boolean patternMismatch,
                         boolean rangeOverflow, boolean rangeUnderflow, boolean stepMismatch,
                         boolean tooLong, boolean tooShort, boolean typeMismatch,
                         boolean valueMissing, boolean valid) {
        this.badInput = badInput;
        this.customError = customError;
        this.patternMismatch = patternMismatch;
        this.rangeOverflow = rangeOverflow;
        this.rangeUnderflow = rangeUnderflow;
        this.stepMismatch = stepMismatch;
        this.tooLong = tooLong;
        this.tooShort = tooShort;
        this.typeMismatch = typeMismatch;
        this.valueMissing = valueMissing;
        this.valid = valid;
    }

    public boolean isBadInput() { return badInput; }
    public boolean isCustomError() { return customError; }
    public boolean isPatternMismatch() { return patternMismatch; }
    public boolean isRangeOverflow() { return rangeOverflow; }
    public boolean isRangeUnderflow() { return rangeUnderflow; }
    public boolean isStepMismatch() { return stepMismatch; }
    public boolean isTooLong() { return tooLong; }
    public boolean isTooShort() { return tooShort; }
    public boolean isTypeMismatch() { return typeMismatch; }
    public boolean isValueMissing() { return valueMissing; }
    public boolean isValid() { return valid; }
}
