package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Small loader-local Rhino bridge for 26.1.
 *
 * <p>1.21.1 obtains a Rhino context from KubeJS. NeoForge 26.1 has no
 * compatible KubeJS artifact, but the Rhino artifact is available directly,
 * so page scripts can use the same DOM objects without depending on KubeJS.
 * Each document gets an isolated top-level scope; this prevents {@code let}
 * bindings and callbacks from leaking between resource-manager windows.</p>
 */
public final class ApricityJS {
    private static final ContextFactory CONTEXT_FACTORY = new ContextFactory();
    private static final Map<String, RuntimeState> RUNTIMES = new ConcurrentHashMap<>();

    private ApricityJS() {
    }

    public static void eval(String code) {
        eval(code, null, "<global>");
    }

    public static void eval(String code, Event event) {
        eval(code, event, "<inline>");
    }

    public static void eval(String code, Event event, String source) {
        if (code == null || code.isBlank()) {
            ApricityUI.LOGGER.warn("[AUI JS] empty script skipped source={}", AuiLog.source(source));
            return;
        }

        Document document = Document.getContextDocument();
        if (document == null || document.isDisposed()) {
            ApricityUI.LOGGER.warn("[AUI JS] script skipped without active document source={}", AuiLog.source(source));
            return;
        }

        RuntimeState runtime = runtimeFor(document);
        Context context = CONTEXT_FACTORY.enter();
        synchronized (runtime) {
            try {
                ensureScope(context, runtime);
                context.evaluateString(
                        runtime.scope,
                        JS.rewriteForRhino(code),
                        AuiLog.source(source),
                        1,
                        null
                );
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI JS] script execution failed source={} event={} code={}",
                        AuiLog.source(source),
                        event == null ? "<none>" : event.type,
                        AuiLog.compact(code),
                        exception
                );
                throw exception;
            }
        }
    }

    /** Drops page scopes so the next resource refresh starts from a clean global environment. */
    public static void reload() {
        RUNTIMES.clear();
    }

    public static Consumer<Event> browserEventListener(Object listener, Object currentTarget) {
        if (!(listener instanceof Function function)) return null;

        Document document = documentOf(currentTarget);
        if (document == null) document = Document.getContextDocument();
        if (document == null || document.isDisposed()) return null;

        RuntimeState runtime = runtimeFor(document);
        return new RhinoEventListener(function, currentTarget, document, runtime);
    }

    private static RuntimeState runtimeFor(Document document) {
        String key = document.getUuid().toString();
        RuntimeState runtime = RUNTIMES.computeIfAbsent(key, ignored -> new RuntimeState(document));
        if (runtime.generation != document.getRefreshGeneration()) {
            synchronized (runtime) {
                if (runtime.generation != document.getRefreshGeneration()) {
                    runtime.scope = null;
                    runtime.generation = document.getRefreshGeneration();
                }
            }
        }
        return runtime;
    }

    private static void ensureScope(Context context, RuntimeState runtime) {
        if (runtime.scope != null) return;

        ScriptableObject scope = context.initStandardObjects();
        Scriptable apricityUI = context.wrapJavaClass(scope, ApricityUI.class);
        ScriptableObject.putProperty(scope, "ApricityUI", apricityUI, context);
        runtime.scope = scope;
    }

    private static Document documentOf(Object target) {
        if (target instanceof Document document) return document;
        if (target instanceof Node node) return node.document;
        return null;
    }

    private static final class RuntimeState {
        private final Document document;
        private long generation = -1L;
        private ScriptableObject scope;

        private RuntimeState(Document document) {
            this.document = document;
        }
    }

    private static final class RhinoEventListener implements Consumer<Event> {
        private final Function function;
        private final Object currentTarget;
        private final Document document;
        private final RuntimeState runtime;

        private RhinoEventListener(Function function, Object currentTarget,
                                   Document document, RuntimeState runtime) {
            this.function = function;
            this.currentTarget = currentTarget;
            this.document = document;
            this.runtime = runtime;
        }

        @Override
        public void accept(Event event) {
            Context context = CONTEXT_FACTORY.enter();
            synchronized (runtime) {
                if (runtime.scope == null || runtime.generation != document.getRefreshGeneration()) return;

                try (Document.ContextScope ignored = Document.withContext(document)) {
                    Object eventArgument = context.javaToJS(event, runtime.scope);
                    Scriptable scriptTarget = context.toObject(currentTarget, runtime.scope);
                    Object previousCurrentTarget = event.currentTarget;
                    event.currentTarget = scriptTarget;
                    try {
                        context.callSync(function, runtime.scope, scriptTarget, new Object[]{eventArgument});
                    } finally {
                        event.currentTarget = previousCurrentTarget;
                    }
                } catch (RuntimeException exception) {
                    ApricityUI.LOGGER.error(
                            "[AUI JS] event listener failed document={} event={} target={}",
                            AuiLog.source(document.getPath()),
                            event == null ? "<unknown>" : event.type,
                            event == null ? "<null>" : String.valueOf(event.currentTarget),
                            exception
                    );
                    throw exception;
                }
            }
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof RhinoEventListener other
                    && function == other.function
                    && currentTarget == other.currentTarget;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(function) + System.identityHashCode(currentTarget);
        }
    }
}
