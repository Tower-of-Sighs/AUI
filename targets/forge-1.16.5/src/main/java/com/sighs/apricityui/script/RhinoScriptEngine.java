package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import com.sighs.apricityui.util.kjs.ApricityUIClientUtil;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Script;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.WrapFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RhinoScriptEngine {
    private static final String DOCUMENT_UUID_BINDING = "__auiDocumentUuid";
    private static final String EVENT_BINDING = "event";
    private static final Map<String, RuntimeState> RUNTIMES = new ConcurrentHashMap<>();
    private static final Object GLOBAL_SCRIPT_LOCK = new Object();
    private static String cachedGlobalCode;
    private static Script cachedGlobalScript;

    private RhinoScriptEngine() {
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
        Context context = Context.enter();
        WrapFactory factory = context.getWrapFactory();
        boolean previousWrap = factory.isJavaPrimitiveWrap();
        factory.setJavaPrimitiveWrap(false);
        try {
            synchronized (runtime) {
                Scriptable scope = scopeFor(context, runtime);
                Object previousEvent = scope.get(EVENT_BINDING, scope);
                boolean hadEvent = previousEvent != Scriptable.NOT_FOUND;
                if (event != null) scope.put(EVENT_BINDING, scope, event);
                try {
                    context.evaluateString(scope, JS.rewriteForRhino(code), AuiLog.source(source), 1, null);
                } catch (RuntimeException exception) {
                    ApricityUI.LOGGER.error("[AUI JS] script execution failed source={} event={} code={}",
                            AuiLog.source(source), event == null ? "<none>" : event.type,
                            AuiLog.compact(code), exception);
                    throw exception;
                } finally {
                    if (event != null) {
                        if (hadEvent) scope.put(EVENT_BINDING, scope, previousEvent);
                        else scope.delete(EVENT_BINDING);
                    }
                }
            }
        } finally {
            factory.setJavaPrimitiveWrap(previousWrap);
            Context.exit();
        }
    }

    public static void evalGlobal(String code, String documentUuid) {
        if (code == null || code.isBlank()) return;
        Document document = Document.getContextDocument();
        if (document == null || document.isDisposed()) {
            ApricityUI.LOGGER.warn("[AUI JS] global script skipped without active document");
            return;
        }
        RuntimeState runtime = runtimeFor(document);
        Context context = Context.enter();
        WrapFactory factory = context.getWrapFactory();
        boolean previousWrap = factory.isJavaPrimitiveWrap();
        factory.setJavaPrimitiveWrap(false);
        try {
            synchronized (runtime) {
                Scriptable scope = scopeFor(context, runtime);
                Object previousUuid = scope.get(DOCUMENT_UUID_BINDING, scope);
                boolean hadUuid = previousUuid != Scriptable.NOT_FOUND;
                scope.put(DOCUMENT_UUID_BINDING, scope, documentUuid == null ? "" : documentUuid);
                try {
                    compiledGlobalScript(context, code).exec(context, scope);
                } catch (RuntimeException exception) {
                    ApricityUI.LOGGER.error("[AUI JS] global script execution failed document={} code={}",
                            documentUuid, AuiLog.compact(code), exception);
                    throw exception;
                } finally {
                    if (hadUuid) scope.put(DOCUMENT_UUID_BINDING, scope, previousUuid);
                    else scope.delete(DOCUMENT_UUID_BINDING);
                }
            }
        } finally {
            factory.setJavaPrimitiveWrap(previousWrap);
            Context.exit();
        }
    }

    public static void reload() {
        RUNTIMES.clear();
        synchronized (GLOBAL_SCRIPT_LOCK) {
            cachedGlobalCode = null;
            cachedGlobalScript = null;
        }
    }

    public static void warmUp() {
        Context context = Context.enter();
        WrapFactory factory = context.getWrapFactory();
        boolean previousWrap = factory.isJavaPrimitiveWrap();
        factory.setJavaPrimitiveWrap(false);
        try {
            context.initStandardObjects();
            String globalJs = Loader.readGlobalJS();
            if (globalJs == null || globalJs.isBlank()) {
                context.compileString("void 0;", "<aui-global-warmup>", 1, null);
            } else {
                compiledGlobalScript(context, globalJs);
            }
        } finally {
            factory.setJavaPrimitiveWrap(previousWrap);
            Context.exit();
        }
    }

    private static Scriptable scopeFor(Context context, RuntimeState runtime) {
        if (runtime.scope != null) return runtime.scope;
        ScriptableObject scope = context.initStandardObjects();
        Scriptable apricityUI = context.getWrapFactory().wrapJavaClass(context, scope, ApricityUIClientUtil.class);
        ScriptableObject.putProperty(scope, "ApricityUI", apricityUI);
        runtime.scope = scope;
        return scope;
    }

    private static Script compiledGlobalScript(Context context, String code) {
        String prepared = JS.rewriteForRhino(code)
                .replace("\"__AUI_DOCUMENT_UUID__\"", DOCUMENT_UUID_BINDING)
                .replace("'__AUI_DOCUMENT_UUID__'", DOCUMENT_UUID_BINDING)
                .replace("__AUI_DOCUMENT_UUID__", DOCUMENT_UUID_BINDING);
        synchronized (GLOBAL_SCRIPT_LOCK) {
            if (cachedGlobalScript == null || !prepared.equals(cachedGlobalCode)) {
                cachedGlobalScript = context.compileString(prepared, "global.js", 1, null);
                cachedGlobalCode = prepared;
            }
            return cachedGlobalScript;
        }
    }

    private static RuntimeState runtimeFor(Document document) {
        RuntimeState runtime = RUNTIMES.computeIfAbsent(document.getUuid().toString(), ignored -> new RuntimeState());
        synchronized (runtime) {
            if (runtime.generation != document.getRefreshGeneration()) {
                runtime.scope = null;
                runtime.generation = document.getRefreshGeneration();
            }
        }
        return runtime;
    }

    private static final class RuntimeState {
        private long generation = -1L;
        private Scriptable scope;
    }
}
