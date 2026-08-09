package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.rhino.Script;
import dev.latvian.mods.rhino.Scriptable;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import net.minecraftforge.fml.ModList;

public class ApricityJS {
    private static final Object GLOBAL_SCRIPT_LOCK = new Object();
    private static final String DOCUMENT_UUID_BINDING = "__auiDocumentUuid";
    private static String cachedGlobalCode;
    private static Script cachedGlobalScript;

    // 框架目前只给元素桥接了 textContent，页面脚本常用 innerText 来设置文本。
    // 在页面脚本执行前，动态装饰器上补一个 innerText 的 getter/setter。
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

        if (event != null) {
            // Event scripts get a stable source label while preserving the existing event binding.
        }
        code = JS.rewriteForRhino(code);

        var manager = KubeJS.getClientScriptManager();
        var context = manager.context;
        var top = manager.topLevelScope;
        Object previousEvent = null;
        boolean hadEvent = false;
        if (event != null) {
            previousEvent = top.get(context, "event", top);
            hadEvent = previousEvent != Scriptable.NOT_FOUND;
            top.put(context, "event", top, event);
        }
        try {
            context.evaluateString(top, code, AuiLog.source(source), 1, null);
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
                if (hadEvent) {
                    top.put(context, "event", top, previousEvent);
                } else {
                    top.delete(context, "event");
                }
            }
        }
    }

    public static void evalGlobal(String code, String documentUuid) {
        if (!isKubeJsLoaded()) return;
        if (code == null || code.isBlank()) return;

        var manager = KubeJS.getClientScriptManager();
        var context = manager.context;
        var top = manager.topLevelScope;
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
            KubeJS.PROXY.reloadClientInternal();
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error("[AUI JS] KubeJS client script reload failed", exception);
            throw exception;
        }
    }

    public static void warmUp() {
        if (!isKubeJsLoaded()) return;
        var manager = KubeJS.getClientScriptManager();
        String globalJs = Loader.readGlobalJS();
        if (globalJs == null || globalJs.isBlank()) return;
        compiledGlobalScript(manager.context, globalJs);
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
