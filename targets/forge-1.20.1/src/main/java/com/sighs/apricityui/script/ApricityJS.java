package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.rhino.Scriptable;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import net.minecraftforge.fml.ModList;

public class ApricityJS {
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

    public static void reload() {
        if (!isKubeJsLoaded()) return;
        try {
            KubeJS.PROXY.reloadClientInternal();
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error("[AUI JS] KubeJS client script reload failed", exception);
            throw exception;
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
