package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.util.AuiLog;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import net.neoforged.fml.ModList;

import java.util.function.Consumer;

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

    private static boolean isKubeJsLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (LinkageError | RuntimeException unavailableForgeRuntime) {
            return false;
        }
    }

    public static Consumer<Event> browserEventListener(Object listener, Object currentTarget) {
        if (!(listener instanceof Function function)) return null;
        return new RhinoEventListener(function, currentTarget);
    }

    private static final class RhinoEventListener implements Consumer<Event> {
        private final Function function;
        private final Object currentTarget;

        private RhinoEventListener(Function function, Object currentTarget) {
            this.function = function;
            this.currentTarget = currentTarget;
        }

        @Override
        public void accept(Event event) {
            var manager = KubeJS.getClientScriptManager();
            var context = (KubeJSContext) manager.contextFactory.enter();
            try {
                Object eventArgument = context.javaToJS(event, context.topLevelScope);
                Scriptable scriptTarget = context.toObject(currentTarget, context.topLevelScope);
                Object previousCurrentTarget = event.currentTarget;
                event.currentTarget = scriptTarget;
                try {
                    context.callSync(function, context.topLevelScope, scriptTarget, new Object[]{eventArgument});
                } finally {
                    event.currentTarget = previousCurrentTarget;
                }
            } catch (RuntimeException exception) {
                String documentPath = event != null && event.target instanceof Node node
                        && node.document != null ? node.document.getPath() : "<unknown>";
                ApricityUI.LOGGER.error(
                        "[AUI JS] event listener failed document={} event={} target={}",
                        AuiLog.source(documentPath),
                        event == null ? "<unknown>" : event.type,
                        event == null ? "<null>" : String.valueOf(event.currentTarget),
                        exception
                );
                throw exception;
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
