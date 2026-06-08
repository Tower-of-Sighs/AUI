package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import com.sighs.apricityui.style.Position;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ElementBindingTest {
    @Test
    void valueAndDefaultValueBehaveLikeSeparateCurrentAndDefaultState() {
        Document document = createDocument();
        Input input = new Input(document);

        input.setDefaultValue("seed");
        assertEquals("seed", input.getDefaultValue());
        assertEquals("seed", input.getAttribute("value"));
        assertEquals("seed", input.getValue());

        input.setValue("alpha");
        assertEquals("alpha", input.getValue());
        assertEquals("seed", input.getDefaultValue());
        assertEquals("seed", input.getAttribute("value"));

        input.setAttribute("value", "beta");
        assertEquals("alpha", input.getValue());
        assertEquals("beta", input.getDefaultValue());

        input.setPlaceholder("hint");
        assertEquals("hint", input.getPlaceholder());
        assertEquals("hint", input.getAttribute("placeholder"));

        input.setAttribute("placeholder", "other");
        assertEquals("other", input.getPlaceholder());
    }

    @Test
    void datasetAndToggleAttributeStayInSync() {
        Document document = createDocument();
        Element element = new Element(document, "div");

        element.getDataset().set("userId", "42");
        assertEquals("42", element.getDataset().get("userId"));
        assertEquals("42", element.getAttribute("data-user-id"));

        element.setAttribute("data-theme-mode", "dark");
        assertEquals("dark", element.getDataset().get("themeMode"));
        assertTrue(element.getDataset().has("themeMode"));

        assertTrue(element.toggleAttribute("disabled", true));
        assertTrue(element.isDisabled());
        assertTrue(element.hasAttribute("disabled"));

        assertFalse(element.toggleAttribute("disabled", false));
        assertFalse(element.isDisabled());
        assertFalse(element.hasAttribute("disabled"));
    }

    @Test
    void radioCheckedStateIsMutuallyExclusiveAcrossGroup() {
        Document document = createDocument();
        Input first = new Input(document);
        Input second = new Input(document);
        first.setAttribute("type", "radio");
        second.setAttribute("type", "radio");
        first.setAttribute("name", "mode");
        second.setAttribute("name", "mode");

        document.appendChild(first);
        document.appendChild(second);

        first.setChecked(true);
        assertTrue(first.isChecked());
        assertFalse(second.isChecked());

        second.setAttribute("checked", "");
        assertFalse(second.isChecked());
        assertTrue(first.isChecked());
        assertTrue(second.isDefaultChecked());
        assertFalse(first.isDefaultChecked());
    }

    @Test
    void selectOptionStateStaysInSyncAcrossValueSelectedAndSelectedIndex() {
        Document document = createDocument();
        Select select = new Select(document);
        Option first = new Option(document);
        Option second = new Option(document);

        first.innerText = "First";
        first.setAttribute("value", "a");
        second.innerText = "Second";
        second.setAttribute("value", "b");

        document.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);

        first.setAttribute("selected", "");
        assertEquals("a", select.getValue());
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());
        assertEquals(0, select.getSelectedIndex());

        select.setValue("b");
        assertFalse(first.isSelected());
        assertTrue(second.isSelected());
        assertEquals(1, select.getSelectedIndex());

        select.setSelectedIndex(0);
        assertEquals("a", select.getValue());
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());
    }

    @Test
    void defaultCheckedAndDefaultSelectedRemainSeparateFromCurrentState() {
        Document document = createDocument();
        Input input = new Input(document);
        input.setAttribute("type", "checkbox");

        input.setDefaultChecked(true);
        assertTrue(input.isDefaultChecked());
        assertTrue(input.isChecked());

        input.setChecked(false);
        assertFalse(input.isChecked());
        assertTrue(input.isDefaultChecked());
        assertTrue(input.hasAttribute("checked"));

        Select select = new Select(document);
        Option option = new Option(document);
        option.innerText = "One";
        option.setAttribute("value", "1");
        option.setDefaultSelected(true);
        document.appendChild(select);
        select.appendChild(option);

        assertTrue(option.isDefaultSelected());
        assertTrue(option.isSelected());

        option.setSelected(false);
        assertFalse(option.isSelected());
        assertTrue(option.isDefaultSelected());
        assertTrue(option.hasAttribute("selected"));
    }

    @Test
    void disabledBlocksClickDispatch() {
        Document document = createDocument();
        Element element = new Element(document, "button");
        AtomicInteger clicks = new AtomicInteger();
        element.addEventListener("click", event -> clicks.incrementAndGet());

        element.click();
        assertEquals(1, clicks.get());

        element.setDisabled(true);
        element.click();
        assertEquals(1, clicks.get());
    }

    @Test
    void documentEventApisForwardToBodyAndDispatchCorrectly() {
        Document document = createDocument();
        AtomicInteger customEvents = new AtomicInteger();
        Event seedEvent = new Event(null, "custom", null, false);

        document.addEventListener("custom", event -> customEvents.incrementAndGet());
        assertTrue(document.dispatchEvent(seedEvent));
        assertEquals(1, customEvents.get());
        assertSame(document.body, seedEvent.target);
        assertSame(document.body, seedEvent.currentTarget);

        document.removeEventListener("custom", event -> customEvents.incrementAndGet());
        Event secondEvent = new Event(null, "custom", null, false);
        assertTrue(document.dispatchEvent(secondEvent));
        assertEquals(2, customEvents.get());
    }

    @Test
    void documentCanRemoveRegisteredListener() {
        Document document = createDocument();
        AtomicInteger calls = new AtomicInteger();
        java.util.function.Consumer<Event> listener = event -> calls.incrementAndGet();

        document.addEventListener("custom", listener);
        assertTrue(document.dispatchEvent(new Event(null, "custom", null, false)));
        assertEquals(1, calls.get());

        document.removeEventListener("custom", listener);
        assertFalse(document.dispatchEvent(new Event(null, "custom", null, false)));
        assertEquals(1, calls.get());
    }

    @Test
    void movingAndReplacingNodesPreservesBindings() {
        Document document = createDocument();
        Element firstParent = new Element(document, "div");
        Element secondParent = new Element(document, "div");
        Element child = new Element(document, "div");

        document.appendChild(firstParent);
        document.appendChild(secondParent);

        child.setClassName("chip active");
        child.getDataset().set("state", "ready");
        firstParent.appendChild(child);

        secondParent.appendChild(child);
        assertSame(secondParent, child.getParentNode());
        assertEquals("chip active", child.getClassName());
        assertEquals("ready", child.getDataset().get("state"));
        assertTrue(child.getClassList().contains("active"));

        Element replacement = new Element(document, "div");
        replacement.setClassName("replacement");
        replacement.getDataset().set("state", "next");
        secondParent.replaceChild(replacement, child);

        assertSame(secondParent, replacement.getParentNode());
        assertEquals("replacement", replacement.getClassName());
        assertEquals("next", replacement.getDataset().get("state"));
        assertFalse(secondParent.contains(child));
    }

    @Test
    void selectStateSurvivesStructuralMoves() {
        Document document = createDocument();
        Select firstSelect = new Select(document);
        Select secondSelect = new Select(document);
        Option option = new Option(document);

        option.innerText = "Only";
        option.setAttribute("value", "only");
        option.setSelected(true);

        document.appendChild(firstSelect);
        document.appendChild(secondSelect);
        firstSelect.appendChild(option);

        assertEquals("only", firstSelect.getValue());
        assertTrue(option.isSelected());

        secondSelect.appendChild(option);
        assertSame(secondSelect, option.getParentNode());
        assertEquals("only", secondSelect.getValue());
        assertTrue(option.isSelected());
        assertEquals(0, secondSelect.getSelectedIndex());
    }

    @Test
    void documentLifecycleStateTransitionsStayExplicit() throws Exception {
        Document document = createDocument();

        invokeLifecycle(document, "beginRefreshLifecycle");
        assertEquals("loading", document.getReadyState());
        assertTrue(document.isActive());
        assertFalse(document.isDisposed());

        invokeLifecycle(document, "enterInteractive");
        assertEquals("interactive", document.getReadyState());

        invokeLifecycle(document, "enterComplete");
        assertEquals("complete", document.getReadyState());

        invokeLifecycle(document, "disposeLifecycle");
        assertTrue(document.isDisposed());
        assertFalse(document.isActive());
        assertEquals("complete", document.getReadyState());
    }

    @Test
    void refreshInvalidatesExistingMutationObservers() {
        Document document = createDocument();
        AtomicInteger calls = new AtomicInteger();
        Document.MutationObserver observer = document.createMutationObserver(records -> calls.incrementAndGet());
        observer.observe(document.body, false, true, false, false, false, false, null);

        document.queueMutation(Document.MutationRecord.attributes(document.body, "data-x", "before"));
        document.flushMutationObservers();
        assertEquals(1, calls.get());

        long previousGeneration = document.getRefreshGeneration();
        document.refresh();
        assertTrue(document.getRefreshGeneration() > previousGeneration);

        document.queueMutation(Document.MutationRecord.attributes(document.body, "data-y", "after"));
        document.flushMutationObservers();
        assertEquals(1, calls.get());
    }

    @Test
    void removedDocumentLeavesActiveLookupSet() {
        Document document = createDocument();
        Document.getAll().add(document);

        assertSame(document, Document.getByUUID(document.getUuid().toString()));
        assertEquals(1, Document.get(document.getPath()).size());

        Document.remove(document.getUuid());

        assertTrue(document.isDisposed());
        assertNull(Document.getByUUID(document.getUuid().toString()));
        assertTrue(Document.get(document.getPath()).isEmpty());
    }

    @Test
    void focusAndBlurUseUnifiedSingleTargetDispatch() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Input input = new Input(document);
        document.appendChild(parent);
        parent.appendChild(input);

        AtomicInteger parentFocusCalls = new AtomicInteger();
        AtomicInteger parentBlurCalls = new AtomicInteger();
        AtomicInteger inputFocusCalls = new AtomicInteger();
        AtomicInteger inputBlurCalls = new AtomicInteger();

        parent.addEventListener("focus", event -> parentFocusCalls.incrementAndGet());
        parent.addEventListener("blur", event -> parentBlurCalls.incrementAndGet());

        input.addEventListener("focus", event -> {
            inputFocusCalls.incrementAndGet();
            assertSame(input, event.target);
            assertSame(input, event.currentTarget);
            assertEquals("focus", event.type);
            assertFalse(event.bubbles);
        });
        input.addEventListener("blur", event -> {
            inputBlurCalls.incrementAndGet();
            assertSame(input, event.target);
            assertSame(input, event.currentTarget);
            assertEquals("blur", event.type);
            assertFalse(event.bubbles);
        });

        input.focus();
        assertSame(input, document.getFocusedElement());
        assertEquals(1, inputFocusCalls.get());
        assertEquals(0, parentFocusCalls.get());

        input.blur();
        assertNull(document.getFocusedElement());
        assertEquals(1, inputBlurCalls.get());
        assertEquals(0, parentBlurCalls.get());
    }

    @Test
    void textInputDispatchesInputOnEditAndChangeOnBlur() {
        Document document = createDocument();
        Input input = new HeadlessInput(document);
        document.appendChild(input);

        AtomicInteger inputEvents = new AtomicInteger();
        AtomicInteger changeEvents = new AtomicInteger();
        input.addEventListener("input", event -> inputEvents.incrementAndGet());
        input.addEventListener("change", event -> changeEvents.incrementAndGet());

        input.focus();
        input.insertText("a");
        input.insertText("b");
        assertEquals("ab", input.getValue());
        assertEquals(2, inputEvents.get());
        assertEquals(0, changeEvents.get());

        input.blur();
        assertEquals(1, changeEvents.get());
    }

    @Test
    void checkboxUserToggleDispatchesInputAndChange() {
        Document document = createDocument();
        Input checkbox = new Input(document);
        checkbox.setAttribute("type", "checkbox");
        document.appendChild(checkbox);

        AtomicInteger inputEvents = new AtomicInteger();
        AtomicInteger changeEvents = new AtomicInteger();
        checkbox.addEventListener("input", event -> inputEvents.incrementAndGet());
        checkbox.addEventListener("change", event -> changeEvents.incrementAndGet());

        assertTrue(checkbox.handleSpaceKey());
        assertTrue(checkbox.isChecked());
        assertEquals(1, inputEvents.get());
        assertEquals(1, changeEvents.get());
    }

    @Test
    void selectUserChangeDispatchesInputAndChange() {
        Document document = createDocument();
        Select select = new Select(document);
        Option first = new Option(document);
        Option second = new Option(document);

        first.innerText = "First";
        first.setAttribute("value", "a");
        second.innerText = "Second";
        second.setAttribute("value", "b");

        document.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);
        select.setValue("a");

        AtomicInteger inputEvents = new AtomicInteger();
        AtomicInteger changeEvents = new AtomicInteger();
        select.addEventListener("input", event -> inputEvents.incrementAndGet());
        select.addEventListener("change", event -> changeEvents.incrementAndGet());

        assertTrue(second.dispatchEvent(new Event(second, "mousedown", null, false)));
        assertEquals("b", select.getValue());
        assertEquals(1, inputEvents.get());
        assertEquals(1, changeEvents.get());
    }

    @Test
    void formSubmitIsCancelableAndBubbles() {
        Document document = createDocument();
        Element wrapper = new Element(document, "div");
        Element form = new Element(document, "form");
        document.appendChild(wrapper);
        wrapper.appendChild(form);

        AtomicInteger wrapperSubmitCalls = new AtomicInteger();
        AtomicInteger formSubmitCalls = new AtomicInteger();

        wrapper.addEventListener("submit", event -> wrapperSubmitCalls.incrementAndGet());
        form.addEventListener("submit", event -> {
            formSubmitCalls.incrementAndGet();
            assertSame(form, event.target);
            assertSame(form, event.currentTarget);
            assertTrue(event.bubbles);
            assertTrue(event.cancelable);
            event.preventDefault();
        });

        assertFalse(form.submit());
        assertEquals(1, formSubmitCalls.get());
        assertEquals(1, wrapperSubmitCalls.get());
    }

    @Test
    void submitButtonClickAndEnterKeyTriggerFormSubmit() {
        Document document = createDocument();
        Element form = new Element(document, "form");
        HeadlessInput textInput = new HeadlessInput(document);
        Input submitButton = new Input(document);
        submitButton.setAttribute("type", "submit");

        document.appendChild(form);
        form.appendChild(textInput);
        form.appendChild(submitButton);

        AtomicInteger submits = new AtomicInteger();
        form.addEventListener("submit", event -> submits.incrementAndGet());

        submitButton.click();
        assertEquals(1, submits.get());

        assertTrue(textInput.submitEnclosingForm());
        assertEquals(2, submits.get());
    }

    @Test
    void scrollToDispatchesNonBubblingScrollEvent() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element scroller = new HeadlessScroller(document);
        document.appendChild(parent);
        parent.appendChild(scroller);

        AtomicInteger targetScrollCalls = new AtomicInteger();
        AtomicInteger parentBubbleCalls = new AtomicInteger();
        AtomicInteger parentCaptureCalls = new AtomicInteger();

        scroller.addEventListener("scroll", event -> {
            targetScrollCalls.incrementAndGet();
            assertSame(scroller, event.target);
            assertSame(scroller, event.currentTarget);
            assertFalse(event.bubbles);
        });
        parent.addEventListener("scroll", event -> parentBubbleCalls.incrementAndGet());
        parent.addEventListener("scroll", event -> {
            parentCaptureCalls.incrementAndGet();
            assertSame(scroller, event.target);
            assertSame(parent, event.currentTarget);
        }, true);

        scroller.scrollTo(0, 24);
        assertEquals(1, targetScrollCalls.get());
        assertEquals(0, parentBubbleCalls.get());
        assertEquals(1, parentCaptureCalls.get());

        scroller.scrollTo(0, 24);
        assertEquals(1, targetScrollCalls.get());
        assertEquals(0, parentBubbleCalls.get());
        assertEquals(1, parentCaptureCalls.get());

        scroller.scrollBy(0, 10);
        assertEquals(2, targetScrollCalls.get());
        assertEquals(0, parentBubbleCalls.get());
        assertEquals(2, parentCaptureCalls.get());
    }

    @Test
    void mousePrimaryClickDispatchesClickAndDoubleClick() {
        Document document = createDocument();
        Element target = new Element(document, "button");
        document.appendChild(target);

        AtomicInteger clickCalls = new AtomicInteger();
        AtomicInteger dblclickCalls = new AtomicInteger();
        target.addEventListener("click", event -> {
            clickCalls.incrementAndGet();
            assertSame(target, event.target);
            assertSame(target, event.currentTarget);
        });
        target.addEventListener("dblclick", event -> {
            dblclickCalls.incrementAndGet();
            assertSame(target, event.target);
            assertSame(target, event.currentTarget);
        });

        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(0, 0), 0, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(0, 0), 0, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(0, 0), 0, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(0, 0), 0, false), document, target);

        assertEquals(2, clickCalls.get());
        assertEquals(1, dblclickCalls.get());
    }

    @Test
    void mouseRightButtonDispatchesContextMenuWithoutClick() {
        Document document = createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);

        AtomicInteger clickCalls = new AtomicInteger();
        AtomicInteger contextmenuCalls = new AtomicInteger();
        target.addEventListener("click", event -> clickCalls.incrementAndGet());
        target.addEventListener("contextmenu", event -> {
            contextmenuCalls.incrementAndGet();
            assertSame(target, event.target);
            assertSame(target, event.currentTarget);
            assertTrue(event.cancelable);
        });

        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(0, 0), 1, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(0, 0), 1, false), document, target);

        assertEquals(0, clickCalls.get());
        assertEquals(1, contextmenuCalls.get());
    }

    @Test
    void pointerCompatDispatchesForPrimaryMousePath() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element target = new Element(document, "div");
        document.appendChild(parent);
        parent.appendChild(target);

        AtomicInteger pointerDownCalls = new AtomicInteger();
        AtomicInteger pointerMoveCalls = new AtomicInteger();
        AtomicInteger pointerUpCalls = new AtomicInteger();
        AtomicInteger parentPointerDownCalls = new AtomicInteger();

        target.addEventListener("pointerdown", event -> {
            pointerDownCalls.incrementAndGet();
            MouseEvent pointerEvent = (MouseEvent) event;
            assertEquals("pointerdown", pointerEvent.type);
            assertEquals(1, pointerEvent.pointerId);
            assertEquals("mouse", pointerEvent.pointerType);
            assertTrue(pointerEvent.isPrimary);
        });
        target.addEventListener("pointermove", event -> pointerMoveCalls.incrementAndGet());
        target.addEventListener("pointerup", event -> pointerUpCalls.incrementAndGet());
        parent.addEventListener("pointerdown", event -> parentPointerDownCalls.incrementAndGet());

        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(0, 0), 0, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(4, 5), 0, false), document, target);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(4, 5), 0, false), document, target);

        assertEquals(1, pointerDownCalls.get());
        assertEquals(1, pointerMoveCalls.get());
        assertEquals(1, pointerUpCalls.get());
        assertEquals(1, parentPointerDownCalls.get());
    }

    @Test
    void pointerHoverCompatTracksEnterLeaveAndOverOut() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element first = new Element(document, "div");
        Element second = new Element(document, "div");
        document.appendChild(parent);
        parent.appendChild(first);
        parent.appendChild(second);

        AtomicInteger firstEnterCalls = new AtomicInteger();
        AtomicInteger firstLeaveCalls = new AtomicInteger();
        AtomicInteger parentEnterCalls = new AtomicInteger();
        AtomicInteger firstOverCalls = new AtomicInteger();
        AtomicInteger firstOutCalls = new AtomicInteger();

        first.addEventListener("pointerenter", event -> firstEnterCalls.incrementAndGet());
        first.addEventListener("pointerleave", event -> firstLeaveCalls.incrementAndGet());
        parent.addEventListener("pointerenter", event -> parentEnterCalls.incrementAndGet());
        first.addEventListener("pointerover", event -> firstOverCalls.incrementAndGet());
        first.addEventListener("pointerout", event -> firstOutCalls.incrementAndGet());

        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(0, 0), -1, false), document, first);
        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(1, 1), -1, false), document, second);

        assertEquals(1, firstEnterCalls.get());
        assertEquals(1, firstLeaveCalls.get());
        assertEquals(1, parentEnterCalls.get());
        assertEquals(1, firstOverCalls.get());
        assertEquals(1, firstOutCalls.get());
    }

    @Test
    void imgLoadEventDispatchesOncePerReadyTransitionAndResetsForNewSource() throws Exception {
        Document document = createDocument();
        Img img = new Img(document);
        document.appendChild(img);

        AtomicInteger loadCalls = new AtomicInteger();
        img.addEventListener("load", event -> {
            loadCalls.incrementAndGet();
            assertSame(img, event.target);
            assertFalse(event.bubbles);
        });

        ImageHandle firstHandle = new ImageHandle("test://img-a", document.getRefreshGeneration());
        firstHandle.markReady(null);

        invokeImgResourceState(img, "test://img-a", firstHandle);
        invokeImgResourceState(img, "test://img-a", firstHandle);
        assertEquals(1, loadCalls.get());

        invokeImgReset(img);

        ImageHandle secondHandle = new ImageHandle("test://img-b", document.getRefreshGeneration());
        secondHandle.markReady(null);
        invokeImgResourceState(img, "test://img-b", secondHandle);
        assertEquals(2, loadCalls.get());
    }

    @Test
    void imgErrorEventDispatchesOncePerFailedTransition() throws Exception {
        Document document = createDocument();
        Img img = new Img(document);
        document.appendChild(img);

        AtomicInteger errorCalls = new AtomicInteger();
        img.addEventListener("error", event -> {
            errorCalls.incrementAndGet();
            assertSame(img, event.target);
            assertFalse(event.bubbles);
        });

        ImageHandle handle = new ImageHandle("test://missing", document.getRefreshGeneration());
        handle.markFailed(new IllegalStateException("missing"), System.currentTimeMillis());

        invokeImgResourceState(img, "test://missing", handle);
        invokeImgResourceState(img, "test://missing", handle);
        assertEquals(1, errorCalls.get());
    }

    @Test
    void imgMetadataReflectsResolvedSourceAndCompletionState() {
        Document document = createDocument();
        TestImg img = new TestImg(document);
        document.appendChild(img);

        assertTrue(img.isComplete());
        assertEquals("", img.getCurrentSrc());
        assertEquals(0, img.getNaturalWidth());
        assertEquals(0, img.getNaturalHeight());

        img.setAttribute("src", "textures/demo.png");
        assertTrue(img.getCurrentSrc().endsWith("textures/demo.png"));
        assertFalse(img.isComplete());
        assertEquals(0, img.getNaturalWidth());
        assertEquals(0, img.getNaturalHeight());

        ImageHandle readyHandle = new ImageHandle(img.getCurrentSrc(), document.getRefreshGeneration());
        readyHandle.markReady(null);
        img.testHandle = readyHandle;
        assertTrue(img.isComplete());
        assertEquals(0, img.getNaturalWidth());
        assertEquals(0, img.getNaturalHeight());

        ImageHandle failedHandle = new ImageHandle(img.getCurrentSrc(), document.getRefreshGeneration());
        failedHandle.markFailed(new IllegalStateException("bad"), System.currentTimeMillis());
        img.testHandle = failedHandle;
        assertTrue(img.isComplete());
        assertEquals(0, img.getNaturalWidth());
        assertEquals(0, img.getNaturalHeight());
    }

    private static Document createDocument() {
        Document document = new Document("test://doc", false);
        document.body = new Body(document);
        return document;
    }

    private static final class HeadlessInput extends Input {
        private HeadlessInput(Document document) {
            super(document);
        }

        @Override
        protected void clampScroll() {
            addDirtyFlags(Drawer.REPAINT);
        }
    }

    private static final class HeadlessScroller extends Element {
        private double testScrollLeft = 0;
        private double testScrollTop = 0;

        private HeadlessScroller(Document document) {
            super(document, "div");
        }

        @Override
        public void setScrollLeft(double value) {
            testScrollLeft = value;
        }

        @Override
        public void setScrollTop(double value) {
            testScrollTop = value;
        }

        @Override
        public double getTargetScrollLeft() {
            return testScrollLeft;
        }

        @Override
        public double getTargetScrollTop() {
            return testScrollTop;
        }
    }

    private static final class TestImg extends Img {
        private ImageHandle testHandle;

        private TestImg(Document document) {
            super(document);
        }

        @Override
        protected ImageHandle resolveCurrentHandle() {
            return testHandle;
        }
    }

    private static void invokeLifecycle(Document document, String methodName) throws Exception {
        Method method = Document.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(document);
    }

    private static void invokeImgResourceState(Img img, String resolvedSrc, ImageHandle handle) {
        img.testUpdateResourceState(resolvedSrc, handle);
    }

    private static void invokeImgReset(Img img) {
        img.testResetResourceObservation();
    }
}
