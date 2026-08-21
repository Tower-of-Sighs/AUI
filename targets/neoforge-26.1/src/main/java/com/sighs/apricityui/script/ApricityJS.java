package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Script;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Object GLOBAL_SCRIPT_LOCK = new Object();
    private static final String DOCUMENT_UUID_BINDING = "__auiDocumentUuid";
    private static String cachedGlobalCode;
    private static Script cachedGlobalScript;

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

    /**
     * global.js 是每个文档的引导脚本，必须每文档执行一次（在其独立 scope 内）。
     * 解析（rewriteForRhino + Rhino AST）只做一次并缓存编译产物，
     * 文档 uuid 通过作用域变量传入，避免每个文档重新解析整份脚本。
     */
    public static void evalGlobal(String code, String documentUuid) {
        if (code == null || code.isBlank()) return;

        Document document = Document.getContextDocument();
        if (document == null || document.isDisposed()) {
            ApricityUI.LOGGER.warn("[AUI JS] global script skipped without active document");
            return;
        }

        RuntimeState runtime = runtimeFor(document);
        Context context = CONTEXT_FACTORY.enter();
        synchronized (runtime) {
            ensureScope(context, runtime);
            Scriptable scope = runtime.scope;
            Object previousUuid = scope.get(context, DOCUMENT_UUID_BINDING, scope);
            boolean hadUuid = previousUuid != Scriptable.NOT_FOUND;
            scope.put(context, DOCUMENT_UUID_BINDING, scope, documentUuid == null ? "" : documentUuid);
            try {
                compiledGlobalScript(context, code).exec(context, scope);
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI JS] global script execution failed document={} code={}",
                        documentUuid,
                        AuiLog.compact(code),
                        exception
                );
                throw exception;
            } finally {
                if (hadUuid) scope.put(context, DOCUMENT_UUID_BINDING, scope, previousUuid);
                else scope.delete(context, DOCUMENT_UUID_BINDING);
            }
        }
    }

    /** Drops page scopes so the next resource refresh starts from a clean global environment. */
    public static void reload() {
        RUNTIMES.clear();
        clearGlobalScriptCache();
    }

    public static void warmUp() {
        Context context = CONTEXT_FACTORY.enter();
        context.initStandardObjects();
        String globalJs = Loader.readGlobalJS();
        if (globalJs == null || globalJs.isBlank()) {
            context.compileString("void 0;", "<aui-global-warmup>", 1, null);
            return;
        }
        // 预热即编译进缓存：第一个文档打开时直接 exec，不再重复解析。
        compiledGlobalScript(context, globalJs);
    }

    private static Script compiledGlobalScript(Context context, String code) {
        String prepared = prepareGlobalCode(code);
        synchronized (GLOBAL_SCRIPT_LOCK) {
            if (cachedGlobalScript == null || !prepared.equals(cachedGlobalCode)) {
                cachedGlobalScript = context.compileString(prepared, "global.js", 1, null);
                cachedGlobalCode = prepared;
            }
            return cachedGlobalScript;
        }
    }

    private static String prepareGlobalCode(String code) {
        return JS.rewriteForRhino(code)
                .replace("\"__AUI_DOCUMENT_UUID__\"", DOCUMENT_UUID_BINDING)
                .replace("'__AUI_DOCUMENT_UUID__'", DOCUMENT_UUID_BINDING)
                .replace("__AUI_DOCUMENT_UUID__", DOCUMENT_UUID_BINDING);
    }

    private static void clearGlobalScriptCache() {
        synchronized (GLOBAL_SCRIPT_LOCK) {
            cachedGlobalCode = null;
            cachedGlobalScript = null;
        }
    }

    private static RuntimeState runtimeFor(Document document) {
        String key = document.getUuid().toString();
        RuntimeState runtime = RUNTIMES.computeIfAbsent(key, ignored -> new RuntimeState());
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

    private static final class RuntimeState {
        private long generation = -1L;
        private ScriptableObject scope;
    }
}
