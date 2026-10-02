package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
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

        ScriptPack pack = clientScriptPack();
        if (pack == null) return;
        var context = pack.context;
        var top = pack.scope;
        Object previousEvent = null;
        boolean hadEvent = false;
        if (event != null) {
            previousEvent = top.get("event", top);
            hadEvent = previousEvent != Scriptable.NOT_FOUND;
            top.put("event", top, event);
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
                    top.put("event", top, previousEvent);
                } else {
                    top.delete("event");
                }
            }
        }
    }

    public static void evalGlobal(String code, String documentUuid) {
        if (!isKubeJsLoaded()) return;
        if (code == null || code.isBlank()) return;

        ScriptPack pack = clientScriptPack();
        if (pack == null) return;
        var context = pack.context;
        var top = pack.scope;
        Object previousUuid = top.get(DOCUMENT_UUID_BINDING, top);
        boolean hadUuid = previousUuid != Scriptable.NOT_FOUND;
        top.put(DOCUMENT_UUID_BINDING, top, documentUuid == null ? "" : documentUuid);
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
            if (hadUuid) top.put(DOCUMENT_UUID_BINDING, top, previousUuid);
            else top.delete(DOCUMENT_UUID_BINDING);
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
        ScriptPack pack = clientScriptPack();
        if (pack == null) return;
        String globalJs = Loader.readGlobalJS();
        if (globalJs == null || globalJs.isBlank()) return;
        compiledGlobalScript(pack.context, globalJs);
    }

    /**
     * 取客户端脚本管理器的 Rhino 上下文与顶层作用域。
     *
     * <p>1.18.2 的 KubeJS 还没有 {@code ScriptManager#context} / {@code #topLevelScope}（1.19 才
     * 直接挂到 manager 上），它们在 {@code ScriptManager#load()} 里逐个挂到 {@link ScriptPack}
     * 上。1.18.2 的每个 ScriptManager 只从自己那一个目录建出唯一一个 pack（客户端即
     * {@code kubejs/client_scripts}），所以这个 pack 的 context/scope 就等价于 1.19.2 里
     * manager 上的那两个字段。脚本还没加载时两者为 null，此时调用方直接跳过。</p>
     */
    private static ScriptPack clientScriptPack() {
        ScriptManager manager = KubeJS.clientScriptManager;
        if (manager == null) return null;
        for (ScriptPack pack : manager.packs.values()) {
            if (pack.context != null && pack.scope != null) return pack;
        }
        return null;
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
