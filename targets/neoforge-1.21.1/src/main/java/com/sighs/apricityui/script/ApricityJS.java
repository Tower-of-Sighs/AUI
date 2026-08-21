package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.Script;
import dev.latvian.mods.rhino.Scriptable;
import net.neoforged.fml.ModList;

public class ApricityJS {
    private static final Object GLOBAL_SCRIPT_LOCK = new Object();
    private static final String DOCUMENT_UUID_BINDING = "__auiDocumentUuid";
    private static String cachedGlobalCode;
    private static Script cachedGlobalScript;

    public static void eval(String code) {
        eval(code, null, "<global>");
    }

    public static void eval(String code, Event event) {
        eval(code, event, "<inline>");
    }

    public static void eval(String code, Event event, String source) {
        if (!isKubeJsLoaded()) return;
        if (code == null || code.isBlank()) {
            ApricityUI.LOGGER.warn("[AUI JS] empty script skipped source={}", AuiLog.source(source));
            return;
        }
        code = JS.rewriteForRhino(code);

        var manager = KubeJS.getClientScriptManager();
        var context = (KubeJSContext) manager.contextFactory.enter();
        try {
            context.evaluateString(context.topLevelScope, code, AuiLog.source(source), 1, null);
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

    /**
     * global.js 是每个文档的引导脚本，但必须每文档执行一次。
     * 解析（rewriteForRhino + Rhino AST）只做一次并缓存编译产物，
     * 文档 uuid 通过作用域变量传入，避免每个文档重新解析整份脚本。
     */
    public static void evalGlobal(String code, String documentUuid) {
        if (!isKubeJsLoaded()) return;
        if (code == null || code.isBlank()) return;

        var manager = KubeJS.getClientScriptManager();
        var context = (KubeJSContext) manager.contextFactory.enter();
        var top = context.topLevelScope;
        Object previousUuid = top.get(context, DOCUMENT_UUID_BINDING, top);
        boolean hadUuid = previousUuid != Scriptable.NOT_FOUND;
        top.put(context, DOCUMENT_UUID_BINDING, top, documentUuid == null ? "" : documentUuid);
        try {
            compiledGlobalScript(context, code).exec(context, top);
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error(
                    "[AUI JS] global script execution failed document={} code={}",
                    documentUuid,
                    AuiLog.compact(code),
                    exception
            );
            throw exception;
        } finally {
            if (hadUuid) top.put(context, DOCUMENT_UUID_BINDING, top, previousUuid);
            else top.delete(context, DOCUMENT_UUID_BINDING);
        }
    }

    public static void reload() {
        if (!isKubeJsLoaded()) return;
        clearGlobalScriptCache();
        try {
            KubeJSClient.reloadClientScripts();
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error("[AUI JS] KubeJS client script reload failed", exception);
            throw exception;
        }
    }

    public static void warmUp() {
        if (!isKubeJsLoaded()) return;
        var manager = KubeJS.getClientScriptManager();
        var context = (KubeJSContext) manager.contextFactory.enter();
        String globalJs = Loader.readGlobalJS();
        if (globalJs == null || globalJs.isBlank()) {
            context.compileString("void 0;", "<aui-global-warmup>", 1, null);
            return;
        }
        // 预热即编译进缓存：第一个文档打开时直接 exec，不再重复解析。
        compiledGlobalScript(context, globalJs);
    }

    private static Script compiledGlobalScript(dev.latvian.mods.rhino.Context context, String code) {
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

    private static boolean isKubeJsLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (LinkageError | RuntimeException unavailableForgeRuntime) {
            return false;
        }
    }
}
