package com.sighs.apricityui.event;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.AuiLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.Consumer;
import com.sighs.apricityui.form.FormData;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

public class Event implements Cloneable {
    private static final ThreadLocal<Integer> TRUSTED_CONTEXT_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static final short NONE = 0;
    public static final short CAPTURING_PHASE = 1;
    public static final short AT_TARGET = 2;
    public static final short BUBBLING_PHASE = 3;

    public Object target;
    public Object currentTarget;
    public String type;
    public boolean bubbles = true;
    public boolean cancelable = false;
    public boolean defaultPrevented = false;
    public Object detail = null;
    /** Submit button that initiated a submit event, when applicable. */
    public Object submitter = null;
    /** FormData snapshot exposed on a formdata event. */
    public Object formData = null;
    public short eventPhase = NONE;
    public boolean cancelBubble = false;
    public boolean returnValue = true;
    public boolean isTrusted = false;
    /** 浏览器 ClipboardEvent.clipboardData 的桥（copy/cut/paste 事件携带）。 */
    public com.sighs.apricityui.render.ClipboardDataBridge clipboardData = null;
    public double timeStamp = System.nanoTime() / 1_000_000.0;

    private boolean propagationStopped = false;
    private boolean immediatePropagationStopped = false;
    private ArrayList<Object> composedPath = new ArrayList<>();

    public Event(Object target, String type) {
        this.target = target;
        this.currentTarget = target;
        this.type = type;
    }

    public Event(Object target, String type, boolean bubbles) {
        this(target, type);
        this.bubbles = bubbles;
    }

    public Event(Object currentTarget, String type, Consumer<Event> listener, boolean useCapture) {
        this(currentTarget, type);
    }

    public Event(Object currentTarget, String type, Consumer<Event> listener, boolean useCapture, boolean internal) {
        this(currentTarget, type);
    }

    public void stopPropagation() {
        cancelBubble = true;
        propagationStopped = true;
    }

    public void stopImmediatePropagation() {
        cancelBubble = true;
        immediatePropagationStopped = true;
        propagationStopped = true;
    }

    public void preventDefault() {
        if (cancelable) {
            defaultPrevented = true;
            returnValue = false;
        }
    }

    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    public boolean isImmediatePropagationStopped() {
        return immediatePropagationStopped;
    }

    public void resetForDispatch(Object dispatchTarget) {
        if (dispatchTarget != null) {
            target = dispatchTarget;
        }
        currentTarget = null;
        eventPhase = NONE;
        defaultPrevented = false;
        returnValue = true;
        cancelBubble = false;
        propagationStopped = false;
        immediatePropagationStopped = false;
        composedPath = new ArrayList<>();
    }

    protected void copyTo(Event copy) {
        copy.target = target;
        copy.currentTarget = currentTarget;
        copy.type = type;
        copy.bubbles = bubbles;
        copy.cancelable = cancelable;
        copy.defaultPrevented = defaultPrevented;
        copy.detail = detail;
        copy.submitter = submitter;
        copy.formData = formData;
        copy.eventPhase = eventPhase;
        copy.cancelBubble = cancelBubble;
        copy.returnValue = returnValue;
        copy.isTrusted = isTrusted;
        copy.timeStamp = timeStamp;
        copy.propagationStopped = propagationStopped;
        copy.immediatePropagationStopped = immediatePropagationStopped;
        copy.composedPath = new ArrayList<>(composedPath);
    }

    @Override
    public Event clone() {
        try {
            Event copy = (Event) super.clone();
            copyTo(copy);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayList<Node> getRoute(Node element) {
        ArrayList<Node> result = new ArrayList<>();
        Node parent = element == null ? null : element.parentNode;
        while (parent != null) {
            result.add(parent);
            parent = parent.parentNode;
        }
        return result;
    }

    public static boolean tiggerEvent(Event targetEvent) {
        if (!(targetEvent.target instanceof Node target)) return false;

        return runWithEventContext(targetEvent, () -> {
            targetEvent.resetForDispatch(target);
            String type = targetEvent.type;
            ArrayList<Node> route = target.getRouteNodes();
            route.remove(target);
            targetEvent.composedPath = buildComposedPath(route, target);
            AtomicBoolean consumed = new AtomicBoolean(false);

            Collections.reverse(route);
            for (Node node : route) {
                dispatchToNode(node, type, targetEvent, true, CAPTURING_PHASE, consumed);
                if (targetEvent.isPropagationStopped()) {
                    targetEvent.eventPhase = NONE;
                    targetEvent.currentTarget = null;
                    return consumed.get();
                }
            }

            dispatchAtTarget(target, type, targetEvent, consumed);
            if (targetEvent.isPropagationStopped()) {
                targetEvent.eventPhase = NONE;
                targetEvent.currentTarget = null;
                return consumed.get();
            }

            if (targetEvent.bubbles) {
                Collections.reverse(route);
                for (Node node : route) {
                    dispatchToNode(node, type, targetEvent, false, BUBBLING_PHASE, consumed);
                    if (targetEvent.isPropagationStopped()) break;
                }
            }

            targetEvent.eventPhase = NONE;
            targetEvent.currentTarget = null;
            return consumed.get();
        });
    }

    public static boolean triggerSingle(Event targetEvent) {
        if (!(targetEvent.target instanceof Node target)) return false;

        return runWithEventContext(targetEvent, () -> {
            targetEvent.resetForDispatch(target);
            targetEvent.composedPath = new ArrayList<>(List.of(target));
            AtomicBoolean consumed = new AtomicBoolean(false);
            dispatchAtTarget(target, targetEvent.type, targetEvent, consumed);
            targetEvent.eventPhase = NONE;
            targetEvent.currentTarget = null;
            return consumed.get();
        });
    }

    public List<Object> composedPath() {
        return List.copyOf(composedPath);
    }

    public void setTrusted(boolean trusted) {
        isTrusted = trusted;
    }

    public static void markTrustedFromCurrentDispatch(Event event) {
        if (event == null) return;
        if (TRUSTED_CONTEXT_DEPTH.get() > 0) {
            event.setTrusted(true);
        }
    }

    public static void runTrustedAction(Runnable action) {
        if (action == null) return;
        runWithTrustedContext(() -> {
            action.run();
            return null;
        });
    }

    public static void runWithEventTrust(Event sourceEvent, Runnable action) {
        if (action == null) return;
        if (sourceEvent != null && sourceEvent.isTrusted) {
            runTrustedAction(action);
            return;
        }
        action.run();
    }

    public void setComposedPath(List<Object> path) {
        composedPath = path == null ? new ArrayList<>() : new ArrayList<>(path);
    }

    private static ArrayList<Object> buildComposedPath(ArrayList<Node> route, Node target) {
        ArrayList<Object> path = new ArrayList<>();
        path.add(target);
        path.addAll(route);
        return path;
    }

    private static <T> T runWithEventContext(Event event, Supplier<T> action) {
        Document document = event != null && event.target instanceof Node target
                ? target.document
                : null;
        Supplier<T> contextualAction = () -> {
            if (event != null && event.isTrusted) {
                return runWithTrustedContext(action);
            }
            return action.get();
        };
        if (document == null) return contextualAction.get();
        try (Document.ContextScope ignored = Document.withContext(document)) {
            return contextualAction.get();
        }
    }

    private static <T> T runWithTrustedContext(Supplier<T> action) {
        TRUSTED_CONTEXT_DEPTH.set(TRUSTED_CONTEXT_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = TRUSTED_CONTEXT_DEPTH.get() - 1;
            if (depth <= 0) {
                TRUSTED_CONTEXT_DEPTH.remove();
            } else {
                TRUSTED_CONTEXT_DEPTH.set(depth);
            }
        }
    }

    private static void dispatchAtTarget(Node target, String type, Event event, AtomicBoolean consumed) {
        dispatchToNode(target, type, event, true, AT_TARGET, consumed);
        if (event.isImmediatePropagationStopped()) return;
        dispatchToNode(target, type, event, false, AT_TARGET, consumed);
    }

    private static void dispatchToNode(Node node, String type, Event event, boolean capturePhase, short phase, AtomicBoolean consumed) {
        if (node == null || type == null || event.isImmediatePropagationStopped()) return;

        node.triggerEvent(listenerRecord -> {
            if (event.isImmediatePropagationStopped()) return;
            if (!Objects.equals(type, listenerRecord.type())) return;
            if (listenerRecord.useCapture() != capturePhase) return;

            event.currentTarget = node;
            event.eventPhase = phase;
            if (!listenerRecord.internal()) {
                consumed.set(true);
            }

            if (listenerRecord.once()) {
                node.removeEventListener(type, listenerRecord.listener(), listenerRecord.useCapture());
            }
            try {
                listenerRecord.listener().accept(event);
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI Event] listener failed type={} phase={} capture={} internal={} target={} document={}",
                        type,
                        phase,
                        capturePhase,
                        listenerRecord.internal(),
                        AuiLog.node(node),
                        node.document == null ? "<unknown>" : AuiLog.source(node.document.getPath()),
                        exception
                );
                if (listenerRecord.internal()) throw exception;
            }
        });
    }

    public static class CustomEvent extends Event {
        public CustomEvent(String type, Object detail, boolean bubbles) {
            super(null, type, bubbles);
            this.detail = detail;
        }
    }

    public static class InputEvent extends Event {
        public String inputType = "";
        public String data = null;
        public boolean isComposing = false;

        public InputEvent(Object target, String type, boolean bubbles, String inputType, String data) {
            super(target, type, bubbles);
            this.inputType = inputType == null ? "" : inputType;
            this.data = data;
        }

        @Override
        public InputEvent clone() {
            InputEvent copy = new InputEvent(target, type, bubbles, inputType, data);
            copyTo(copy);
            copy.inputType = inputType;
            copy.data = data;
            copy.isComposing = isComposing;
            return copy;
        }
    }

    public static class CompositionEvent extends Event {
        public String data = "";
        public boolean isComposing = false;

        public CompositionEvent(Object target, String type, boolean bubbles, String data) {
            super(target, type, bubbles);
            this.data = data == null ? "" : data;
        }

        @Override
        public CompositionEvent clone() {
            CompositionEvent copy = new CompositionEvent(target, type, bubbles, data);
            copyTo(copy);
            copy.data = data;
            copy.isComposing = isComposing;
            return copy;
        }
    }

    public record ListenerRecord(
            String type,
            Consumer<Event> listener,
            boolean useCapture,
            boolean once,
            boolean internal
    ) {
    }
}
