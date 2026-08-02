package com.sighs.apricityui.form;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.sighs.apricityui.style.ConstraintText;
import com.sighs.apricityui.init.Element;

/**
 * 表单控件的约束校验状态计算。从 Element 拆出：
 * constraintState/radioGroupHasChecked/supportsTextLengthAndPattern/collectElements/ValidationResult。
 * 只读 Element 的公开成员与传入的 customValidityMessage，无副作用。
 */
public final class ConstraintValidator {
    private ConstraintValidator() {
    }

    public static final class ValidationResult {
        boolean badInput;
        boolean customError;
        boolean patternMismatch;
        boolean rangeOverflow;
        boolean rangeUnderflow;
        boolean stepMismatch;
        boolean tooLong;
        boolean tooShort;
        boolean typeMismatch;
        boolean valueMissing;

        public void applyCustom(String message) {
            customError = message != null && !message.isBlank();
        }

        public void merge(ValidationResult other) {
            if (other == null) return;
            badInput |= other.badInput;
            customError |= other.customError;
            patternMismatch |= other.patternMismatch;
            rangeOverflow |= other.rangeOverflow;
            rangeUnderflow |= other.rangeUnderflow;
            stepMismatch |= other.stepMismatch;
            tooLong |= other.tooLong;
            tooShort |= other.tooShort;
            typeMismatch |= other.typeMismatch;
            valueMissing |= other.valueMissing;
        }

        public ValidityState toState() {
            boolean valid = !(badInput || customError || patternMismatch || rangeOverflow
                    || rangeUnderflow || stepMismatch || tooLong || tooShort
                    || typeMismatch || valueMissing);
            return new ValidityState(badInput, customError, patternMismatch, rangeOverflow,
                    rangeUnderflow, stepMismatch, tooLong, tooShort, typeMismatch, valueMissing, valid);
        }
    }

    public static ValidationResult state(Element element, String customValidityMessage) {
        ValidationResult result = new ValidationResult();
        if (!element.isWillValidate()) return result;
        String raw = element.getValue();
        String valueText = raw == null ? "" : raw;
        boolean empty = valueText.isEmpty();
        String type = ConstraintText.normalizedInputType(element);

        if (element.hasAttribute("required")) {
            boolean missing = empty;
            if ("checkbox".equals(type) || "radio".equals(type)) {
                missing = "radio".equals(type) ? !radioGroupHasChecked(element) : !element.isChecked();
            } else if ("SELECT".equalsIgnoreCase(element.tagName)) {
                List<Element> selected = element.getSelectedOptions();
                missing = selected.isEmpty()
                        || (!element.isMultiple() && selected.get(0).getOptionValue().isEmpty());
            }
            result.valueMissing = missing;
        }
        if (empty) {
            result.applyCustom(customValidityMessage);
            return result;
        }

        if ("email".equals(type)) {
            boolean valid = element.hasAttribute("multiple")
                    ? Arrays.stream(valueText.split(",", -1)).map(String::trim).allMatch(ConstraintText::isSimpleEmail)
                    : ConstraintText.isSimpleEmail(valueText);
            result.typeMismatch = !valid;
        } else if ("color".equals(type)) {
            result.typeMismatch = !valueText.matches("#[0-9a-fA-F]{6}");
        } else if ("url".equals(type)) {
            try {
                URI uri = new URI(valueText);
                result.typeMismatch = uri.getScheme() == null || uri.getScheme().isBlank();
            } catch (URISyntaxException ignored) {
                result.typeMismatch = true;
            }
        }

        Double number = ConstraintText.parseConstraintNumber(type, valueText);
        if (ConstraintText.isNumericType(type) && number == null) result.badInput = true;

        Double min = ConstraintText.parseConstraintNumber(type, element.getAttribute("min"));
        Double max = ConstraintText.parseConstraintNumber(type, element.getAttribute("max"));
        if (number != null && min != null) result.rangeUnderflow = number < min;
        if (number != null && max != null) result.rangeOverflow = number > max;

        if (number != null && ConstraintText.isNumericType(type)) {
            String stepText = element.hasAttribute("step") ? element.getAttribute("step") : ConstraintText.defaultStepText(type);
            if (!"any".equalsIgnoreCase(stepText)) {
                try {
                    double step = Double.parseDouble(stepText);
                    if (step > 0 && Double.isFinite(step)) {
                        double base = min != null ? min : ConstraintText.defaultStepBase(type);
                        result.stepMismatch = Math.abs((number - base) / step - Math.rint((number - base) / step)) > 1e-7;
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid step attributes are ignored by browsers.
                }
            }
        }

        if (element.hasAttribute("pattern") && supportsTextLengthAndPattern(element, type)) {
            try {
                result.patternMismatch = !Pattern.compile("(?:" + element.getAttribute("pattern") + ")").matcher(valueText).matches();
            } catch (PatternSyntaxException ignored) {
                result.patternMismatch = false;
            }
        }
        if (supportsTextLengthAndPattern(element, type)) {
            int minLength = ConstraintText.parseNonNegativeInt(element.getAttribute("minlength"));
            int maxLength = ConstraintText.parseNonNegativeInt(element.getAttribute("maxlength"));
            if (minLength >= 0) result.tooShort = valueText.length() < minLength;
            if (maxLength >= 0) result.tooLong = valueText.length() > maxLength;
        }
        result.applyCustom(customValidityMessage);
        return result;
    }

    public static boolean radioGroupHasChecked(Element element) {
        String name = element.getAttribute("name");
        Element form = element.getFormOwner();
        List<Element> candidates;
        if (form != null) {
            candidates = form.getFormControls();
        } else if (element.document != null) {
            candidates = element.document.getElements();
        } else {
            Element root = element;
            while (root.parentElement != null) root = root.parentElement;
            ArrayList<Element> local = new ArrayList<>();
            collectElements(root, local);
            candidates = local;
        }
        for (Element control : candidates) {
            if (control.getFormOwner() != form) continue;
            if ("INPUT".equalsIgnoreCase(control.tagName)
                    && "radio".equals(ConstraintText.normalizedInputType(control))
                    && Objects.equals(name, control.getAttribute("name"))
                    && !control.isDisabled()
                    && control.isChecked()) return true;
        }
        return false;
    }

    public static boolean supportsTextLengthAndPattern(Element element, String type) {
        if ("TEXTAREA".equalsIgnoreCase(element.tagName)) return true;
        if (!"INPUT".equalsIgnoreCase(element.tagName)) return false;
        return switch (type) {
            case "text", "search", "email", "url", "tel", "password" -> true;
            default -> false;
        };
    }

    public static void collectElements(Element root, List<Element> result) {
        if (root == null) return;
        result.add(root);
        for (Element child : root.children) collectElements(child, result);
    }
}
