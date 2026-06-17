package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.CommentNode;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertNull(seedEvent.currentTarget);

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
        assertTrue(document.dispatchEvent(new Event(null, "custom", null, false)));
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
    void textAndCommentNodesDispatchAsEventTargetsWithoutElementCoercion() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        TextNode textNode = document.createTextNode("hello");
        CommentNode commentNode = document.createComment("anchor");
        document.appendChild(parent);
        parent.appendChild(textNode);
        parent.appendChild(commentNode);

        AtomicInteger parentTextEvents = new AtomicInteger();
        AtomicInteger parentCommentEvents = new AtomicInteger();
        AtomicInteger textEvents = new AtomicInteger();
        AtomicInteger commentEvents = new AtomicInteger();

        parent.addEventListener("custom", event -> {
            assertSame(parent, event.currentTarget);
            if (event.target == textNode) {
                parentTextEvents.incrementAndGet();
                return;
            }
            if (event.target == commentNode) {
                parentCommentEvents.incrementAndGet();
                return;
            }
            fail("unexpected target: " + event.target);
        });
        textNode.addEventListener("custom", event -> {
            textEvents.incrementAndGet();
            assertSame(textNode, event.target);
            assertSame(textNode, event.currentTarget);
        });
        commentNode.addEventListener("custom", event -> {
            commentEvents.incrementAndGet();
            assertSame(commentNode, event.target);
            assertSame(commentNode, event.currentTarget);
        });

        Event textEvent = new Event(textNode, "custom", null, false);
        assertTrue(textNode.dispatchEvent(textEvent));
        assertEquals(1, textEvents.get());
        assertEquals(1, parentTextEvents.get());

        Event commentEvent = new Event(commentNode, "custom", null, false);
        assertTrue(commentNode.dispatchEvent(commentEvent));
        assertEquals(1, commentEvents.get());
        assertEquals(1, parentCommentEvents.get());
    }

    @Test
    void nodeLevelInsertionStillUsesRegisteredElementFactory() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        document.appendChild(parent);
        Element.register("INPUT", (doc, tag) -> new Input(doc));

        Node inserted = ((Node) parent).appendChild(new Element(document, "input"));

        assertInstanceOf(Input.class, inserted);
        assertInstanceOf(Input.class, parent.getFirstChild());
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
    void tickElementsUsesSnapshotWhenElementMutatesDocumentStructure() {
        Document document = createDocument();

        class PassiveElement extends Element {
            private PassiveElement(Document ownerDocument, String tagName) {
                super(ownerDocument, tagName);
            }

            @Override
            public void tick() {
            }
        }

        class MutatingElement extends PassiveElement {
            private boolean appended;

            private MutatingElement(Document ownerDocument) {
                super(ownerDocument, "div");
            }

            @Override
            public void tick() {
                if (!appended) {
                    appended = true;
                    document.getElements().add(new PassiveElement(document, "span"));
                }
            }
        }

        MutatingElement first = new MutatingElement(document);
        PassiveElement second = new PassiveElement(document, "div");
        document.getElements().add(first);
        document.getElements().add(second);

        assertDoesNotThrow(document::tickElements);
        assertEquals(3, document.getElements().size());
    }

    @Test
    void documentMarkDirtyFlagsWholeSnapshotAndSpecificElement() {
        Document document = createDocument();
        Element first = new Element(document, "div");
        Element second = new Element(document, "span");
        document.body.appendChild(first);
        document.body.appendChild(second);
        document.getDirtyElements().clear();
        first.clearDirtyFlags();
        second.clearDirtyFlags();

        document.markDirty(Drawer.RELAYOUT);

        assertEquals(Drawer.RELAYOUT, document.getGlobalDirtyMask());
        assertFalse(first.hasDirtyFlag(Drawer.RELAYOUT));
        assertFalse(second.hasDirtyFlag(Drawer.RELAYOUT));
        assertTrue(document.getDirtyElements().isEmpty());

        document.commitRenderState();

        assertEquals(0, document.getGlobalDirtyMask());
        assertTrue(document.getDirtyElements().isEmpty());
        assertFalse(first.hasDirtyFlag(Drawer.RELAYOUT));
        assertFalse(second.hasDirtyFlag(Drawer.RELAYOUT));

        second.clearDirtyFlags();
        document.getDirtyElements().clear();

        document.markDirty(second, Drawer.REPAINT);

        assertFalse(first.hasDirtyFlag(Drawer.REPAINT));
        assertTrue(second.hasDirtyFlag(Drawer.REPAINT));
        assertEquals(1, document.getDirtyElements().size());
        assertTrue(document.getDirtyElements().contains(second));
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

        AtomicInteger beforeInputEvents = new AtomicInteger();
        AtomicInteger inputEvents = new AtomicInteger();
        AtomicInteger changeEvents = new AtomicInteger();
        input.addEventListener("beforeinput", event -> beforeInputEvents.incrementAndGet());
        input.addEventListener("input", event -> inputEvents.incrementAndGet());
        input.addEventListener("change", event -> changeEvents.incrementAndGet());

        input.focus();
        input.insertText("a");
        input.insertText("b");
        assertEquals("ab", input.getValue());
        assertEquals(2, beforeInputEvents.get());
        assertEquals(2, inputEvents.get());
        assertEquals(0, changeEvents.get());

        input.blur();
        assertEquals(1, changeEvents.get());
    }

    @Test
    void beforeInputCanCancelTextInsertionAndDeletion() {
        Document document = createDocument();
        Input input = new HeadlessInput(document);
        document.appendChild(input);

        AtomicInteger beforeInputCalls = new AtomicInteger();
        AtomicInteger inputCalls = new AtomicInteger();
        input.addEventListener("beforeinput", event -> {
            beforeInputCalls.incrementAndGet();
            Event.InputEvent inputEvent = (Event.InputEvent) event;
            if ("x".equals(inputEvent.data) || "deleteContentBackward".equals(inputEvent.inputType)) {
                event.preventDefault();
            }
        });
        input.addEventListener("input", event -> inputCalls.incrementAndGet());

        input.focus();
        input.insertText("a");
        input.insertText("x");
        assertEquals("a", input.getValue());
        assertEquals(2, beforeInputCalls.get());
        assertEquals(1, inputCalls.get());

        assertFalse(input.deleteBackward());
        assertEquals("a", input.getValue());
        assertEquals(3, beforeInputCalls.get());
        assertEquals(1, inputCalls.get());
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
    void stopImmediatePropagationBlocksLaterListenersOnSameNode() {
        Document document = createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);

        ArrayList<String> calls = new ArrayList<>();
        target.addEventListener("custom", event -> {
            calls.add("first");
            event.stopImmediatePropagation();
        });
        target.addEventListener("custom", event -> calls.add("second"));

        assertTrue(target.dispatchEvent(new Event(target, "custom", true)));
        assertEquals(java.util.List.of("first"), calls);
    }

    @Test
    void stopPropagationStillAllowsLaterListenersOnSameNode() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element target = new Element(document, "div");
        document.appendChild(parent);
        parent.appendChild(target);

        ArrayList<String> calls = new ArrayList<>();
        parent.addEventListener("custom", event -> calls.add("parent"));
        target.addEventListener("custom", event -> {
            calls.add("first");
            event.stopPropagation();
        });
        target.addEventListener("custom", event -> calls.add("second"));

        assertTrue(target.dispatchEvent(new Event(target, "custom", true)));
        assertEquals(java.util.List.of("first", "second"), calls);
    }

    @Test
    void eventPhaseTracksCaptureTargetAndBubbleOrder() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element target = new Element(document, "button");
        document.appendChild(parent);
        parent.appendChild(target);

        ArrayList<String> phases = new ArrayList<>();
        parent.addEventListener("custom", event -> {
            phases.add("parent-capture");
            assertEquals(Event.CAPTURING_PHASE, event.eventPhase);
            assertSame(target, event.target);
            assertSame(parent, event.currentTarget);
        }, true);
        target.addEventListener("custom", event -> {
            phases.add("target-capture");
            assertEquals(Event.AT_TARGET, event.eventPhase);
            assertSame(target, event.target);
            assertSame(target, event.currentTarget);
        }, true);
        target.addEventListener("custom", event -> {
            phases.add("target-bubble");
            assertEquals(Event.AT_TARGET, event.eventPhase);
            assertSame(target, event.target);
            assertSame(target, event.currentTarget);
        });
        parent.addEventListener("custom", event -> {
            phases.add("parent-bubble");
            assertEquals(Event.BUBBLING_PHASE, event.eventPhase);
            assertSame(target, event.target);
            assertSame(parent, event.currentTarget);
        });

        Event event = new Event(target, "custom", true);
        assertTrue(target.dispatchEvent(event));
        assertEquals(java.util.List.of("parent-capture", "target-capture", "target-bubble", "parent-bubble"), phases);
        assertEquals(Event.NONE, event.eventPhase);
        assertNull(event.currentTarget);
    }

    @Test
    void composedPathAndCancelBubbleReflectPropagationState() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element target = new Element(document, "button");
        document.appendChild(parent);
        parent.appendChild(target);

        AtomicInteger targetCalls = new AtomicInteger();
        AtomicInteger parentCalls = new AtomicInteger();
        parent.addEventListener("custom", event -> {
            parentCalls.incrementAndGet();
            event.stopPropagation();
            assertTrue(event.cancelBubble);
            assertEquals(java.util.List.of(target, parent, document.body), event.composedPath());
        });
        target.addEventListener("custom", event -> {
            targetCalls.incrementAndGet();
            assertFalse(event.cancelBubble);
            assertEquals(java.util.List.of(target, parent, document.body), event.composedPath());
        });

        Event event = new Event(target, "custom", true);
        assertTrue(target.dispatchEvent(event));
        assertEquals(1, targetCalls.get());
        assertEquals(1, parentCalls.get());
        assertTrue(event.cancelBubble);
        assertEquals(java.util.List.of(target, parent, document.body), event.composedPath());
    }

    @Test
    void returnValueTracksPreventDefault() {
        Document document = createDocument();
        Element form = new Element(document, "form");
        document.appendChild(form);

        AtomicInteger calls = new AtomicInteger();
        form.addEventListener("submit", event -> {
            calls.incrementAndGet();
            assertTrue(event.returnValue);
            event.preventDefault();
            assertFalse(event.returnValue);
        });

        assertFalse(form.submit());
        assertEquals(1, calls.get());
    }

    @Test
    void onceListenerRunsOnlyOnce() {
        Document document = createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);

        AtomicInteger calls = new AtomicInteger();
        target.addEventListener("custom", event -> calls.incrementAndGet(), false, true);

        assertTrue(target.dispatchEvent(new Event(target, "custom", true)));
        assertTrue(target.dispatchEvent(new Event(target, "custom", true)));
        assertEquals(1, calls.get());
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
    void hitTestPrefersTopmostOverlappingElementOnRealMousePath() {
        Document document = createDocument();
        Element bottom = new Element(document, "button");
        Element top = new Element(document, "button");
        document.appendChild(bottom);
        document.appendChild(top);
        setRelativeHitBox(document.body, 0, 0, 200, 200);
        setRelativeHitBox(bottom, 10, 10, 50, 50);
        setRelativeHitBox(top, 10, 10, 50, 50);
        setPaintOrder(document, bottom, top);

        AtomicInteger bottomClicks = new AtomicInteger();
        AtomicInteger topClicks = new AtomicInteger();
        bottom.addEventListener("click", event -> bottomClicks.incrementAndGet());
        top.addEventListener("click", event -> topClicks.incrementAndGet());

        MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(20, 20), 0, false), document);
        MouseEvent.tiggerEvent(new MouseEvent("mouseup", new Position(20, 20), 0, false), document);
        assertEquals(0, bottomClicks.get());
        assertEquals(1, topClicks.get());
    }

    @Test
    void realMousePathResolvesFocusedTargetAndBubblesToAncestors() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Input input = new HeadlessInput(document);
        document.appendChild(parent);
        parent.appendChild(input);
        setRelativeHitBox(document.body, 0, 0, 200, 200);
        setRelativeHitBox(parent, 10, 10, 100, 40);
        setRelativeHitBox(input, 5, 5, 80, 20);
        setPaintOrder(document, parent, input);

        AtomicInteger parentMouseDownCalls = new AtomicInteger();
        AtomicInteger inputMouseDownCalls = new AtomicInteger();
        parent.addEventListener("mousedown", event -> {
            parentMouseDownCalls.incrementAndGet();
            assertSame(input, event.target);
            assertSame(parent, event.currentTarget);
        });
        input.addEventListener("mousedown", event -> {
            inputMouseDownCalls.incrementAndGet();
            assertSame(input, event.target);
            assertSame(input, event.currentTarget);
        });

        assertTrue(MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(20, 20), 0, false), document));
        assertSame(input, document.getFocusedElement());
        assertEquals(1, inputMouseDownCalls.get());
        assertEquals(1, parentMouseDownCalls.get());
    }

    @Test
    void realWheelPathScrollsNearestScrollableAncestor() {
        Document document = createDocument();
        Element scroller = new HeadlessScroller(document);
        Element child = new Element(document, "div");
        document.appendChild(scroller);
        scroller.appendChild(child);
        scroller.setAttribute("style", "overflow: auto;");
        scroller.scrollHeight = 200;
        scroller.scrollWidth = 100;
        setRelativeHitBox(document.body, 0, 0, 240, 240);
        setRelativeHitBox(scroller, 10, 10, 100, 60);
        setRelativeHitBox(child, 5, 5, 80, 20);
        setPaintOrder(document, scroller, child);

        AtomicInteger scrollCalls = new AtomicInteger();
        scroller.addEventListener("scroll", event -> scrollCalls.incrementAndGet());

        MouseEvent wheel = new MouseEvent("wheel", new Position(20, 20), -1, false);
        wheel.scrollDelta = 24;
        assertTrue(MouseEvent.tiggerEvent(wheel, document));
        assertEquals(24, scroller.getTargetScrollTop());
        assertEquals(1, scrollCalls.get());
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
    void mouseEventExposesResolvedPointerAndPositionFields() {
        Document document = createDocument();
        Element parent = new Element(document, "div");
        Element target = new Element(document, "div");
        document.appendChild(parent);
        parent.appendChild(target);
        setRelativeHitBox(document.body, 0, 0, 200, 200);
        setRelativeHitBox(parent, 10, 10, 100, 100);
        setRelativeHitBox(target, 5, 7, 40, 30);
        setPaintOrder(document, parent, target);

        AtomicInteger calls = new AtomicInteger();
        target.addEventListener("mousedown", event -> {
            calls.incrementAndGet();
            MouseEvent mouseEvent = (MouseEvent) event;
            assertEquals(20.0, mouseEvent.clientX);
            assertEquals(25.0, mouseEvent.clientY);
            assertEquals(20.0, mouseEvent.pageX);
            assertEquals(25.0, mouseEvent.pageY);
            assertEquals(5.0, mouseEvent.offsetX);
            assertEquals(8.0, mouseEvent.offsetY);
            assertEquals(0, mouseEvent.button);
            assertEquals(Event.AT_TARGET, mouseEvent.eventPhase);
        }, true);
        target.addEventListener("mousedown", event -> {
            MouseEvent mouseEvent = (MouseEvent) event;
            assertEquals(5.0, mouseEvent.offsetX);
            assertEquals(8.0, mouseEvent.offsetY);
            assertEquals(0, mouseEvent.button);
            assertEquals(Event.AT_TARGET, mouseEvent.eventPhase);
        });

        assertTrue(MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(20, 25), 0, false), document));
        assertEquals(1, calls.get());
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
    void textInputDispatchesTypedInputEventPayload() {
        Document document = createDocument();
        Input input = new HeadlessInput(document);
        document.appendChild(input);

        AtomicInteger beforeInputCalls = new AtomicInteger();
        AtomicInteger inputCalls = new AtomicInteger();
        AtomicInteger changeCalls = new AtomicInteger();
        input.addEventListener("beforeinput", event -> {
            beforeInputCalls.incrementAndGet();
            Event.InputEvent inputEvent = (Event.InputEvent) event;
            assertEquals("insertText", inputEvent.inputType);
            assertEquals(beforeInputCalls.get() == 1 ? "a" : "b", inputEvent.data);
            assertSame(input, inputEvent.target);
            assertEquals(Event.AT_TARGET, inputEvent.eventPhase);
            assertTrue(inputEvent.cancelable);
            assertFalse(inputEvent.isTrusted);
            assertFalse(inputEvent.isComposing);
        });
        input.addEventListener("input", event -> {
            inputCalls.incrementAndGet();
            Event.InputEvent inputEvent = (Event.InputEvent) event;
            assertEquals("insertText", inputEvent.inputType);
            assertEquals(inputCalls.get() == 1 ? "a" : "b", inputEvent.data);
            assertSame(input, inputEvent.target);
            assertEquals(Event.AT_TARGET, inputEvent.eventPhase);
            assertFalse(inputEvent.isTrusted);
            assertFalse(inputEvent.isComposing);
        });
        input.addEventListener("change", event -> {
            changeCalls.incrementAndGet();
            assertFalse(event instanceof Event.InputEvent);
            assertSame(input, event.target);
            assertEquals(Event.AT_TARGET, event.eventPhase);
            assertFalse(event.isTrusted);
        });

        input.focus();
        input.insertText("a");
        input.insertText("b");
        input.blur();

        assertEquals(2, beforeInputCalls.get());
        assertEquals(2, inputCalls.get());
        assertEquals(1, changeCalls.get());
    }

    @Test
    void checkboxDispatchesTypedReplacementInputEvents() {
        Document document = createDocument();
        Input checkbox = new Input(document);
        checkbox.setAttribute("type", "checkbox");
        document.appendChild(checkbox);

        ArrayList<String> payloads = new ArrayList<>();
        checkbox.addEventListener("input", event -> {
            assertFalse(event.isTrusted);
            payloads.add(event.type + ":" + event.getClass().getSimpleName());
        });
        checkbox.addEventListener("change", event -> {
            assertFalse(event.isTrusted);
            payloads.add(event.type + ":" + event.getClass().getSimpleName());
        });

        assertTrue(checkbox.handleSpaceKey());
        assertEquals(java.util.List.of(
                "input:Event",
                "change:Event"
        ), payloads);
    }

    @Test
    void keyEventExposesDomStyleKeyMetadata() {
        final int enterKeyCode = 257;
        Document document = createDocument();
        Input input = new HeadlessInput(document);
        document.appendChild(input);
        input.focus();

        AtomicInteger calls = new AtomicInteger();
        input.addEventListener("keydown", event -> {
            calls.incrementAndGet();
            KeyEvent keyEvent = (KeyEvent) event;
            assertEquals(enterKeyCode, keyEvent.keyCode);
            assertEquals("Enter", keyEvent.key);
            assertEquals("Enter", keyEvent.code);
            assertTrue(keyEvent.repeat);
            assertFalse(keyEvent.altKey);
            assertFalse(keyEvent.shiftKey);
            assertFalse(keyEvent.controlKey);
            assertFalse(keyEvent.metaKey);
            assertEquals(Event.AT_TARGET, keyEvent.eventPhase);
            assertTrue(keyEvent.isTrusted);
            assertTrue(keyEvent.timeStamp > 0);
        });

        KeyEvent.triggerEvent(document, "keydown", enterKeyCode, 0, 0, true, KeyEvent.Source.INPUT_EVENT);
        assertEquals(1, calls.get());
    }

    @Test
    void dispatchEventReturnsFalseOnlyWhenCancelableEventIsPrevented() {
        Document document = createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);

        java.util.function.Consumer<Event> listener = Event::preventDefault;
        target.addEventListener("custom", listener);

        Event prevented = new Event(target, "custom", true);
        prevented.cancelable = true;
        assertFalse(target.dispatchEvent(prevented));

        Event notCancelable = new Event(target, "custom", true);
        assertTrue(target.dispatchEvent(notCancelable));

        target.removeEventListener("custom", listener);
        assertTrue(target.dispatchEvent(new Event(target, "missing", true)));
    }

    @Test
    void scriptDispatchedEventsRemainUntrustedAndKeepTimestamp() {
        Document document = createDocument();
        Element target = new Element(document, "div");
        document.appendChild(target);

        Event event = new Event(target, "custom", true);
        double timestamp = event.timeStamp;
        AtomicInteger calls = new AtomicInteger();
        target.addEventListener("custom", seen -> {
            calls.incrementAndGet();
            assertFalse(seen.isTrusted);
            assertEquals(timestamp, seen.timeStamp);
        });

        assertTrue(target.dispatchEvent(event));
        assertEquals(1, calls.get());
        assertEquals(timestamp, event.timeStamp);
        assertFalse(event.isTrusted);
    }

    @Test
    void trustedMouseDispatchCarriesIntoDerivedFocusAndScrollEvents() {
        Document document = createDocument();
        Input input = new HeadlessInput(document);
        Element scroller = new HeadlessScroller(document);
        document.appendChild(scroller);
        scroller.appendChild(input);
        scroller.setAttribute("style", "overflow: auto;");
        scroller.scrollHeight = 200;
        scroller.scrollWidth = 100;
        setRelativeHitBox(document.body, 0, 0, 240, 240);
        setRelativeHitBox(scroller, 10, 10, 120, 80);
        setRelativeHitBox(input, 5, 5, 80, 20);
        setPaintOrder(document, scroller, input);

        AtomicInteger focusCalls = new AtomicInteger();
        AtomicInteger scrollCalls = new AtomicInteger();
        input.addEventListener("focus", event -> {
            focusCalls.incrementAndGet();
            assertTrue(event.isTrusted);
        });
        scroller.addEventListener("scroll", event -> {
            scrollCalls.incrementAndGet();
            assertTrue(event.isTrusted);
        });

        MouseEvent mouseDown = new MouseEvent("mousedown", new Position(20, 20), 0, false);
        mouseDown.setTrusted(true);
        assertTrue(MouseEvent.tiggerEvent(mouseDown, document));

        MouseEvent wheel = new MouseEvent("wheel", new Position(20, 20), -1, false);
        wheel.scrollDelta = 16;
        wheel.setTrusted(true);
        assertTrue(MouseEvent.tiggerEvent(wheel, document));

        assertEquals(1, focusCalls.get());
        assertEquals(1, scrollCalls.get());
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

    @Test
    void slotDisplayExpressionsCanComeFromDirectTextNodes() {
        assumeMinecraftItemRuntime();
        Document document = createDocument();

        Slot literalSlot = new Slot(document);
        literalSlot.appendChild(new TextNode(document, "minecraft:diamond"));
        document.body.appendChild(literalSlot);
        literalSlot.tick();
        assertFalse(invokeResolveDisplayStack(literalSlot).toString().isBlank());

        Slot jsonSlot = new Slot(document);
        jsonSlot.setAttribute("cycle", "0");
        jsonSlot.appendChild(new TextNode(document, "[{\"item\":\"minecraft:oak_log\"},{\"item\":\"minecraft:birch_log\"}]"));
        document.body.appendChild(jsonSlot);
        jsonSlot.tick();
        assertFalse(invokeResolveDisplayStack(jsonSlot).toString().isBlank());
    }

    @Test
    void slotShorthandExpressionsCanAlsoComeFromDirectTextNodes() {
        assumeMinecraftItemRuntime();
        Document document = createDocument();
        Slot slot = new Slot(document);
        slot.setAttribute("cycle", "0");
        slot.appendChild(new TextNode(document, "minecraft:diamond|minecraft:emerald"));
        document.body.appendChild(slot);

        slot.tick();

        assertFalse(invokeResolveDisplayStack(slot).toString().isBlank());
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

    private static void setRelativeHitBox(Element element, double x, double y, double width, double height) {
        Box box = new Box();
        box.element = element;
        element.getRenderer().box.set(box);
        element.getRenderer().size.set(new Size(width, height));
        element.getRenderer().position.set(new Position(x, y));
    }

    private static void setPaintOrder(Document document, Element... elements) {
        ArrayList<RenderNode> paintList = document.getPaintList();
        paintList.clear();
        for (Element element : elements) {
            paintList.add(new RenderNode.ElementPhaseNode(element, Base.RenderPhase.BODY));
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

    private static Object invokeResolveDisplayStack(Slot slot) {
        try {
            Method method = Slot.class.getDeclaredMethod("resolveDisplayStack");
            method.setAccessible(true);
            return method.invoke(slot);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assumeMinecraftItemRuntime() {
        Assumptions.assumeTrue(isClassPresent("net.minecraft.world.item.ItemStack"));
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
