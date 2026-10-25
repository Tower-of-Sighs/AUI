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
import dev.latvian.mods.rhino.SharedContextData;
import dev.latvian.mods.rhino.WrapFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KubeJS 缺失但 Rhino 在场时使用的页面脚本运行时（1.18.2 版）。
 *
 * <p>与 {@link ApricityJS} 保持相同的求值语义：{@code event} 临时绑定、
 * {@code global.js} 每文档执行一次并缓存编译产物、{@code __auiDocumentUuid} 通过作用域变量传入。
 * 每个文档一个独立顶层作用域，资源重载（refresh generation 变化）后丢弃重建。</p>
 *
 * <p>1.18.2 的 Rhino 没有 {@code ContextFactory}，包装器挂在 {@link SharedContextData} 上，
 * 且 {@code Scriptable} 的读写是不带 {@code Context} 参数的老签名。这里显式建立
 * {@link SharedContextData} 并关闭 {@code javaPrimitiveWrap}，让 Java 的
 * String/Number/Boolean 原样进入脚本（与 issue #98 的浏览器语义一致）。</p>
 *
 * <p>本类引用 Rhino 类型，只能由 {@link ScriptRuntimeSupport#backend()} 判定为
 * {@link ScriptRuntimeSupport.Backend#RHINO} 之后加载。</p>
 */
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
        SharedContextData previousData = context.sharedContextData;
        try {
            synchronized (runtime) {
                Scriptable scope = scopeFor(context, runtime);
                WrapFactory wrapFactory = context.sharedContextData.getWrapFactory();
                boolean previousWrap = wrapFactory.isJavaPrimitiveWrap();
                wrapFactory.setJavaPrimitiveWrap(false);
                try {
                    Object previousEvent = scope.get(EVENT_BINDING, scope);
                    boolean hadEvent = previousEvent != Scriptable.NOT_FOUND;
                    if (event != null) {
                        scope.put(EVENT_BINDING, scope, event);
                    }
                    try {
                        context.evaluateString(scope, JS.rewriteForRhino(code), AuiLog.source(source), 1, null);
                    } catch (RuntimeException exception) {
                        ApricityUI.LOGGER.error(
                                "[AUI JS] script execution failed source={} event={} code={}",
                                AuiLog.source(source),
                                event == null ? "<none>" : event.type,
                                AuiLog.compact(code),
                                exception
                        );
                        throw exception;
                    } finally {
                        if (event != null) {
                            if (hadEvent) scope.put(EVENT_BINDING, scope, previousEvent);
                            else scope.delete(EVENT_BINDING);
                        }
                    }
                } finally {
                    wrapFactory.setJavaPrimitiveWrap(previousWrap);
                }
            }
        } finally {
            Context.exit();
            // 恢复线程上下文上原有的共享数据，避免影响同线程其它模组的 Rhino 脚本。
            context.sharedContextData = previousData;
        }
    }

    /**
     * {@code global.js} 是每个文档的引导脚本，必须每文档在其作用域内执行一次。
     * 解析（rewriteForRhino + Rhino AST）只做一次并缓存编译产物，文档 uuid 通过作用域变量传入。
     */
    public static void evalGlobal(String code, String documentUuid) {
        if (code == null || code.isBlank()) return;

        Document document = Document.getContextDocument();
        if (document == null || document.isDisposed()) {
            ApricityUI.LOGGER.warn("[AUI JS] global script skipped without active document");
            return;
        }

        RuntimeState runtime = runtimeFor(document);
        Context context = Context.enter();
        SharedContextData previousData = context.sharedContextData;
        try {
            synchronized (runtime) {
                Scriptable scope = scopeFor(context, runtime);
                WrapFactory wrapFactory = context.sharedContextData.getWrapFactory();
                boolean previousWrap = wrapFactory.isJavaPrimitiveWrap();
                wrapFactory.setJavaPrimitiveWrap(false);
                try {
                    Object previousUuid = scope.get(DOCUMENT_UUID_BINDING, scope);
                    boolean hadUuid = previousUuid != Scriptable.NOT_FOUND;
                    scope.put(DOCUMENT_UUID_BINDING, scope, documentUuid == null ? "" : documentUuid);
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
                        if (hadUuid) scope.put(DOCUMENT_UUID_BINDING, scope, previousUuid);
                        else scope.delete(DOCUMENT_UUID_BINDING);
                    }
                } finally {
                    wrapFactory.setJavaPrimitiveWrap(previousWrap);
                }
            }
        } finally {
            Context.exit();
            // 恢复线程上下文上原有的共享数据，避免影响同线程其它模组的 Rhino 脚本。
            context.sharedContextData = previousData;
        }
    }

    /**
     * Drops page scopes so the next resource refresh starts from a clean global environment.
     */
    public static void reload() {
        RUNTIMES.clear();
        clearGlobalScriptCache();
    }

    public static void warmUp() {
        Context context = Context.enter();
        SharedContextData previousData = context.sharedContextData;
        try {
            ScriptableObject scope = context.initStandardObjects();
            SharedContextData data = ensureSharedContextData(context, scope);
            WrapFactory wrapFactory = data.getWrapFactory();
            boolean previousWrap = wrapFactory.isJavaPrimitiveWrap();
            wrapFactory.setJavaPrimitiveWrap(false);
            try {
                String globalJs = Loader.readGlobalJS();
                if (globalJs == null || globalJs.isBlank()) {
                    context.compileString("void 0;", "<aui-global-warmup>", 1, null);
                    return;
                }
                compiledGlobalScript(context, globalJs);
            } finally {
                wrapFactory.setJavaPrimitiveWrap(previousWrap);
            }
        } finally {
            Context.exit();
            // 恢复线程上下文上原有的共享数据，避免影响同线程其它模组的 Rhino 脚本。
            context.sharedContextData = previousData;
        }
    }

    private static Scriptable scopeFor(Context context, RuntimeState runtime) {
        if (runtime.scope != null) {
            // Context.enter()/exit() 会换掉线程上下文，共享数据可能为空，这里补建。
            ensureSharedContextData(context, (ScriptableObject) runtime.scope);
            return runtime.scope;
        }
        ScriptableObject scope = context.initStandardObjects();
        SharedContextData data = ensureSharedContextData(context, scope);
        Scriptable apricityUI = data.getWrapFactory().wrapJavaClass(data, scope, ApricityUIClientUtil.class);
        ScriptableObject.putProperty(scope, "ApricityUI", apricityUI);
        runtime.scope = scope;
        return scope;
    }

    /**
     * 1.18.2 的包装器/包装器开关都挂在 {@link SharedContextData} 上，首次使用时建立。
     */
    private static SharedContextData ensureSharedContextData(Context context, ScriptableObject scope) {
        if (context.sharedContextData == null) {
            context.sharedContextData = new SharedContextData(scope);
        }
        return context.sharedContextData;
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

    private static final class RuntimeState {
        private long generation = -1L;
        private Scriptable scope;
    }
}
