package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.form.FormDataEntry;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FormCompatibilityTest {
    @Test
    void formAssociationLabelsAndFieldsetDisabledInheritance() {
        Document document = TestDocumentFactory.createDocument();
        Element form = new Element(document, "form");
        form.setAttribute("id", "settings");
        Element fieldset = new Element(document, "fieldset");
        fieldset.setDisabled(true);
        Element legend = new Element(document, "legend");
        Input inLegend = input(document, "inLegend", "x");
        legend.appendChild(inLegend);
        Input blocked = input(document, "blocked", "x");
        fieldset.appendChild(legend);
        fieldset.appendChild(blocked);
        form.appendChild(fieldset);

        Input external = input(document, "external", "y");
        external.setAttribute("form", "settings");
        Element label = new Element(document, "label");
        label.setAttribute("for", "external-id");
        external.setAttribute("id", "external-id");
        document.body.appendChild(form);
        document.body.appendChild(external);
        document.body.appendChild(label);

        assertSame(form, external.getForm());
        assertSame(external, label.getLabeledControl());
        assertEquals(List.of(label), external.getLabels());
        assertFalse(inLegend.isDisabled());
        assertTrue(blocked.isDisabled());
    }

    @Test
    void validationCoversRequiredPatternRangeAndCustomError() {
        Document document = TestDocumentFactory.createDocument();
        Input number = input(document, "amount", "3");
        number.setAttribute("type", "number");
        number.setAttribute("required", "");
        number.setAttribute("min", "4");
        number.setAttribute("step", "2");
        document.body.appendChild(number);

        assertFalse(number.isValid());
        assertTrue(number.getValidity().rangeUnderflow());
        number.setValue("6");
        assertTrue(number.isValid());
        number.setCustomValidity("blocked");
        assertFalse(number.checkValidity());
        assertEquals("blocked", number.getValidationMessage());
        number.setCustomValidity("");
        number.setValue("");
        assertTrue(number.getValidity().valueMissing());
    }

    @Test
    void requestSubmitUsesValidationAndResetRestoresDefaults() {
        Document document = TestDocumentFactory.createDocument();
        Element form = new Element(document, "form");
        Input required = input(document, "name", "initial");
        required.setAttribute("required", "");
        Input submit = input(document, "go", "Go");
        submit.setAttribute("type", "submit");
        TextArea area = new HeadlessTextArea(document);
        area.setTextContent("description");
        form.appendChild(required);
        form.appendChild(area);
        form.appendChild(submit);
        document.body.appendChild(form);

        AtomicInteger submitEvents = new AtomicInteger();
        form.addEventListener("submit", event -> submitEvents.incrementAndGet());
        required.setValue("");
        assertFalse(form.requestSubmit(submit));
        assertEquals(0, submitEvents.get());
        required.setValue("changed");
        area.setValue("edited");
        assertTrue(form.requestSubmit(submit));
        assertEquals(1, submitEvents.get());
        assertTrue(form.reset());
        assertEquals("initial", required.getValue());
        assertEquals("description", area.getValue());
    }

    @Test
    void formDataFollowsSubmitCancellationAndSelectRequiredValue() {
        Document document = TestDocumentFactory.createDocument();
        Element form = new Element(document, "form");
        Select select = new Select(document);
        select.setAttribute("name", "kind");
        select.setAttribute("required", "");
        select.appendChild(option(document, "Choose", "", true));
        form.appendChild(select);
        document.body.appendChild(form);

        AtomicInteger formDataEvents = new AtomicInteger();
        form.addEventListener("formdata", event -> formDataEvents.incrementAndGet());
        assertFalse(form.requestSubmit());
        assertEquals(0, formDataEvents.get());

        select.setValue("ready");
        Option ready = option(document, "Ready", "ready", true);
        select.appendChild(ready);
        assertTrue(form.requestSubmit());
        assertEquals(1, formDataEvents.get());
        assertEquals(1, form.getFormLength());
    }

    @Test
    void formDataUsesSuccessfulControlsAndSelectedOptions() {
        Document document = TestDocumentFactory.createDocument();
        Element form = new Element(document, "form");
        Input text = input(document, "alpha", "1");
        Input unchecked = input(document, "skip", "x");
        unchecked.setAttribute("type", "checkbox");
        Input checked = input(document, "flag", "yes");
        checked.setAttribute("type", "checkbox");
        checked.setChecked(true);
        Select select = new Select(document);
        select.setAttribute("name", "beta");
        select.setMultiple(true);
        select.appendChild(option(document, "X", "x", true));
        select.appendChild(option(document, "Y", "y", true));
        form.appendChild(text);
        form.appendChild(unchecked);
        form.appendChild(checked);
        form.appendChild(select);
        document.body.appendChild(form);

        List<FormDataEntry> entries = form.getFormDataEntries();
        assertEquals(List.of("alpha=1", "flag=yes", "beta=x", "beta=y"),
                entries.stream().map(entry -> entry.name() + "=" + entry.value()).toList());
    }

    @Test
    void specializedInputTypesExposeValueAsNumberAndRangeStepping() {
        Document document = TestDocumentFactory.createDocument();
        Input date = input(document, "day", "2024-01-02");
        date.setAttribute("type", "date");
        assertEquals(1_704_153_600_000d, date.getValueAsNumber(), 1d);

        Input dateTime = input(document, "moment", "2024-01-02T03:04:05");
        dateTime.setAttribute("type", "datetime-local");
        assertEquals(1_704_164_645_000d, dateTime.getValueAsNumber(), 1d);
        dateTime.setValueAsNumber(1_704_164_646_000d);
        assertEquals("2024-01-02T03:04:06", dateTime.getValue());

        Input range = new Input(document);
        range.setAttribute("name", "volume");
        range.setAttribute("type", "range");
        assertEquals("50.0", range.getValue());
        range.stepUp();
        assertEquals("51", range.getValue());
    }

    @Test
    void rangePointerUpdatesDuringDragAndCommitsOnRelease() {
        Document document = TestDocumentFactory.createDocument();
        Input range = new Input(document) {
            @Override
            public DOMRect getBoundingClientRect() {
                return new DOMRect(10, 10, 100, 8);
            }
        };
        range.setAttribute("type", "range");
        range.setAttribute("min", "0");
        range.setAttribute("max", "100");
        range.setAttribute("step", "10");
        range.setValue("0");
        document.body.appendChild(range);

        AtomicInteger inputEvents = new AtomicInteger();
        AtomicInteger changeEvents = new AtomicInteger();
        range.addEventListener("input", event -> inputEvents.incrementAndGet());
        range.addEventListener("change", event -> changeEvents.incrementAndGet());

        MouseEvent down = new MouseEvent("mousedown", new Position(60, 14), 0, false);
        MouseEvent.dispatchToTarget(down, document, range);
        assertEquals("50", range.getValue());

        MouseEvent move = new MouseEvent("mousemove", new Position(100, 14), 0, false);
        MouseEvent.dispatchToTarget(move, document, range);
        assertEquals("90", range.getValue());

        MouseEvent up = new MouseEvent("mouseup", new Position(100, 14), 0, false);
        MouseEvent.dispatchToTarget(up, document, range);
        assertTrue(inputEvents.get() >= 2);
        assertEquals(1, changeEvents.get());
    }

    @Test
    void numberInputWheelAndSpinnerUseStepAndRespectBounds() {
        Document document = TestDocumentFactory.createDocument();
        Input number = new Input(document) {
            @Override
            public DOMRect getBoundingClientRect() {
                return new DOMRect(10, 10, 100, 24);
            }
        };
        number.setAttribute("type", "number");
        number.setValue("5");
        number.setAttribute("min", "1");
        number.setAttribute("max", "9");
        number.setAttribute("step", "2");
        document.body.appendChild(number);

        MouseEvent wheelUp = new MouseEvent("wheel", new Position(0, 0), -1, false);
        wheelUp.deltaY = -50;
        wheelUp.scrollDelta = -50;
        wheelUp.cancelable = true;
        MouseEvent.dispatchToTarget(wheelUp, document, number);
        assertTrue(wheelUp.defaultPrevented);
        assertTrue(wheelUp.isNativeConsumed());
        assertEquals("7", number.getValue());

        MouseEvent spinnerUp = new MouseEvent("mousedown", new Position(105, 12), 0, false);
        assertTrue(number.handleNumberSpinner(spinnerUp));
        assertEquals("9", number.getValue());

        MouseEvent spinnerDown = new MouseEvent("mousedown", new Position(105, 30), 0, false);
        assertTrue(number.handleNumberSpinner(spinnerDown));
        assertEquals("7", number.getValue());

        MouseEvent wheelDown = new MouseEvent("wheel", new Position(0, 0), -1, false);
        wheelDown.deltaY = 50;
        wheelDown.scrollDelta = 50;
        wheelDown.cancelable = true;
        MouseEvent.dispatchToTarget(wheelDown, document, number);
        assertEquals("5", number.getValue());

        number.setAttribute("readonly", "readonly");
        MouseEvent readOnlyWheel = new MouseEvent("wheel", new Position(0, 0), -1, false);
        readOnlyWheel.deltaY = -50;
        readOnlyWheel.scrollDelta = -50;
        readOnlyWheel.cancelable = true;
        assertFalse(number.handleNumberWheel(readOnlyWheel));
        assertEquals("5", number.getValue());
        assertFalse(readOnlyWheel.defaultPrevented);
    }

    @Test
    void numberInputWithoutMinCanStepBelowZero() {
        Document document = TestDocumentFactory.createDocument();
        Input number = new Input(document);
        number.setAttribute("type", "number");
        number.setValue("0");
        document.body.appendChild(number);

        MouseEvent wheelDown = new MouseEvent("wheel", new Position(0, 0), -1, false);
        wheelDown.deltaY = 50;
        wheelDown.scrollDelta = 50;
        wheelDown.cancelable = true;

        assertTrue(number.handleNumberWheel(wheelDown));
        assertEquals("-1", number.getValue());
    }

    @Test
    void numberSpinnerHitAreaMatchesContentBoxWithPadding() {
        Document document = TestDocumentFactory.createDocument();
        Input number = new Input(document) {
            @Override
            public DOMRect getBoundingClientRect() {
                return new DOMRect(10, 10, 100, 34);
            }
        };
        number.setAttribute("type", "number");
        number.setAttribute("style", "border: 1px solid #000; padding: 7px 9px;");
        number.setValue("5");
        document.body.appendChild(number);

        // The icon center is inside the content-box spinner, but outside the old outer-edge hit area.
        MouseEvent spinnerIcon = new MouseEvent("mousedown", new Position(91, 21), 0, false);
        assertTrue(number.handleNumberSpinner(spinnerIcon));
        assertEquals("6", number.getValue());
    }

    @Test
    void colorInputUsesColorControlSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Input color = input(document, "accent", "#6fffe9");
        color.setAttribute("type", "color");
        document.body.appendChild(color);

        assertFalse(color.canEditText());
        assertFalse(color.canSelectText());
        assertEquals("#6fffe9", color.getValue());
    }

    @Test
    void selectionDirectionAndExplicitLabelsFollowDomRules() {
        Document document = TestDocumentFactory.createDocument();
        HeadlessTextArea text = new HeadlessTextArea(document);
        text.setValue("abcdef");
        text.setSelectionRange(1, 4, "backward");
        assertEquals(1, text.getSelectionStart());
        assertEquals(4, text.getSelectionEnd());
        assertEquals("backward", text.getSelectionDirection());

        Element wrapper = new Element(document, "label");
        wrapper.setAttribute("for", "other");
        Input target = input(document, "target", "x");
        target.setAttribute("id", "target");
        wrapper.appendChild(target);
        Input other = input(document, "other", "y");
        other.setAttribute("id", "other");
        document.body.appendChild(wrapper);
        document.body.appendChild(other);
        assertTrue(target.getLabels().isEmpty());
        assertEquals(List.of(wrapper), other.getLabels());
    }

    @Test
    void detachedFormsExposeLocalControlsAndFieldsets() {
        Document document = TestDocumentFactory.createDocument();
        Element form = new Element(document, "form");
        Element fieldset = new Element(document, "fieldset");
        Input child = input(document, "inside", "value");
        fieldset.appendChild(child);
        form.appendChild(fieldset);
        assertEquals(List.of(fieldset, child), form.getFormControls());
    }

    @Test
    void radioGroupsDoNotCrossFormOwners() {
        Document document = TestDocumentFactory.createDocument();
        Element firstForm = new Element(document, "form");
        Element secondForm = new Element(document, "form");
        Input first = input(document, "choice", "a");
        first.setAttribute("type", "radio");
        Input second = input(document, "choice", "b");
        second.setAttribute("type", "radio");
        firstForm.appendChild(first);
        secondForm.appendChild(second);
        document.body.appendChild(firstForm);
        document.body.appendChild(secondForm);

        first.setChecked(true);
        second.setChecked(true);
        assertTrue(first.isChecked());
        assertTrue(second.isChecked());
    }

    private static Input input(Document document, String name, String value) {
        Input input = new Input(document);
        input.setAttribute("name", name);
        input.setDefaultValue(value);
        return input;
    }

    private static Option option(Document document, String label, String value, boolean selected) {
        Option option = new Option(document);
        option.setTextContent(label);
        option.setAttribute("value", value);
        option.setSelected(selected);
        return option;
    }

    private static final class HeadlessTextArea extends TextArea {
        private HeadlessTextArea(Document document) { super(document); }

        @Override
        protected void clampScroll() {
            addDirtyFlags(Drawer.REPAINT);
        }
    }
}
