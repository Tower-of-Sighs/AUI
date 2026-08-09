package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import net.neoforged.fml.ModList;

public class ApricityJS {
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

    public static void reload() {
        if (!isKubeJsLoaded()) return;
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
        String warmupCode = globalJs == null || globalJs.isBlank() ? "void 0;" : globalJs;
        context.compileString(
                JS.rewriteForRhino(warmupCode),
                "<aui-global-warmup>",
                1,
                null
        );
    }

    private static boolean isKubeJsLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (LinkageError | RuntimeException unavailableForgeRuntime) {
            return false;
        }
    }
}
