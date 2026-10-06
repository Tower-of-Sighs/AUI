package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.ecmascript.EcmaEventListener;
import com.sighs.apricityui.script.host.AuiScriptHost;
import com.sighs.apricityui.script.host.RhinoHostObject;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.spi.AuiServices;
import dev.latvian.mods.rhino.Callable;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class RhinoHostObjectTest {
    @Test
    void browserCreateElementUsesTheSpecializedCanvasFactory() {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        Object wrappedDocument = RhinoTestSupport.wrap(context, scope, document);
        assertEquals(RhinoHostObject.class, wrappedDocument.getClass());
        ScriptableObject.putProperty(scope, "__auiTestDocument", wrappedDocument, context);
        ScriptableObject.putProperty(scope, "__auiTestWindow",
                RhinoTestSupport.wrap(context, scope, Window.window), context);
        String bootstrap = Loader.readGlobalJS()
                .replace("let document = ApricityUI.getDocumentByUUID(\"__AUI_DOCUMENT_UUID__\");",
                        "let document = __auiTestDocument;")
                .replace("let window = ApricityUI.getWindow();", "let window = __auiTestWindow;");
        context.evaluateString(scope, bootstrap, "global.js", 1, null);

        assertEquals("function|CANVAS|function|true", context.evaluateString(scope,
                "var canvas = document.createElement('canvas');"
                        + "typeof document.createElement + '|' + canvas.tagName + '|'"
                        + "+ typeof canvas.getContext + '|'"
                        + "+ (typeof canvas.getContext === 'function' && canvas.getContext('2d') != null);",
                "specialized-canvas", 1, null));
    }

    @Test
    void genericStringAndSymbolExpandosPreserveJavaMembers() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        Scriptable delegate = (Scriptable) RhinoTestSupport.wrap(context, scope, element);
        RhinoHostObject host = new RhinoHostObject(element, delegate, scope);
        ScriptableObject.putProperty(scope, "host", host, context);

        Object result = context.evaluateString(scope,
                "var first = Symbol('same'); var second = Symbol('same');"
                        + "host.arbitrary = 41; host[''] = 7;"
                        + "host[first] = 'first'; host[second] = 'second';"
                        + "Object.defineProperty(host, 'derived', {"
                        + " get: function() { return host.arbitrary + 1; }, configurable: true"
                        + "});"
                        + "host.setAttribute('data-probe', 'ok');"
                        + "host.arbitrary + '|' + host[''] + '|' + host[first] + '|'"
                        + "+ host[second] + '|' + host.derived + '|' + host.getAttribute('data-probe');",
                "host-expandos", 1, null);

        assertEquals("41|7|first|second|42|ok", result);
    }

    @Test
    void wrapperIdentityIsScopedToOneDocumentGeneration() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        Scriptable firstDelegate = (Scriptable) RhinoTestSupport.wrap(context, scope, element);
        Scriptable secondDelegate = (Scriptable) RhinoTestSupport.wrap(context, scope, element);

        RhinoHostObject first;
        RhinoHostObject second;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            first = StandaloneRhinoRuntime.wrapHostObject(element, firstDelegate, scope);
            second = StandaloneRhinoRuntime.wrapHostObject(element, secondDelegate, scope);
        }
        assertSame(first, second);

        StandaloneRhinoRuntime.release(document);
        try (Document.ContextScope ignored = Document.withContext(document)) {
            assertNotSame(first, StandaloneRhinoRuntime.wrapHostObject(element, firstDelegate, scope));
        }
    }

    @Test
    void nestedHostResultsUseTheSharedWrapperIdentityAndKeepExpandos() {
        Document document = TestDocumentFactory.createDocument();
        NestedHost nested = new NestedHost();
        ParentHost parent = new ParentHost(nested);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            ScriptableObject.putProperty(scope, "host", RhinoTestSupport.wrap(context, scope, parent), context);
            result = context.evaluateString(scope,
                    "var nestedHost = host.child(); nestedHost._vnode = 'cached';"
                            + "nestedHost._vnode + '|' + (nestedHost === host.child());",
                    "nested-host-expando", 1, null);
        }

        assertEquals("cached|true", result);
    }

    @Test
    void stringPropertiesUseEcmaScriptMethodsInsteadOfJavaOverloads() {
        Document document = TestDocumentFactory.createDocument();
        Element input = document.createElement("input");
        input.setValue("12a3");
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        Scriptable delegate = (Scriptable) RhinoTestSupport.wrap(context, scope, input);
        RhinoHostObject host = new RhinoHostObject(input, delegate, scope);
        ScriptableObject.putProperty(scope, "host", host, context);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            assertEquals("string|123", context.evaluateString(scope,
                    "typeof host.value + '|' + host.value.replace(/[^0-9]/g, '')",
                    "host-string-regexp-replace", 1, null));
        }
    }

    @Test
    void eventListenerArgumentsUseTheSharedHostWrapper() {
        Document document = TestDocumentFactory.createDocument();
        EventLikeHost event = new EventLikeHost();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        AuiScriptService previous = AuiServices.script();
        AuiServices.setScript(new TestScriptService(context, scope));
        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, "var callbackResult;", "event-host-state", 1, null);
            Callable callback = (Callable) context.evaluateString(scope,
                    "(function(value) { value._vts = 17; value._stopped = true;"
                            + " callbackResult = value._vts + '|' + value._stopped; })",
                    "event-host-callback", 1, null);

            new EcmaEventListener(callback, scope, context).accept(event);

            assertEquals("17|true", context.evaluateString(scope,
                    "callbackResult", "event-host-value", 1, null));
        } finally {
            AuiServices.setScript(previous);
        }
    }

    @Test
    void eventCallbacksUseCurrentTargetAsThisWithoutChangingOrdinaryCallbacks() {
        Document document = TestDocumentFactory.createDocument();
        Element target = document.createElement("div");
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        AuiScriptService previous = AuiServices.script();
        AuiServices.setScript(new AuiScriptService() {
            @Override
            public void eval(String code, Event event, String source) {
            }

            @Override
            public void reload() {
            }

            @Override
            public Object wrapHostObject(Object value) {
                return RhinoTestSupport.wrap(context, scope, value);
            }

            @Override
            public Consumer<Object> createCallback(Object callback) {
                return StandaloneRhinoRuntime.createCallback(callback);
            }
        });
        try (Document.ContextScope ignored = Document.withContext(document)) {
            ScriptableObject.putProperty(scope, "target", RhinoTestSupport.wrap(context, scope, target), context);
            context.evaluateString(scope,
                    "var testGlobal = this;"
                            + "var dispatchThis, dispatchCurrentTarget, ordinaryValue, ordinaryThis;"
                            + "var dispatchListener = function(event) { dispatchThis = this; dispatchCurrentTarget = event.currentTarget; };"
                            + "var ordinaryListener = function(value) { ordinaryValue = value; ordinaryThis = this; };"
                            + "target.addEventListener('click', dispatchListener);",
                    "event-current-target-this", 1, null);

            target.dispatchEvent(new Event(target, "click", false));

            assertEquals(true, context.evaluateString(scope,
                    "dispatchThis === dispatchCurrentTarget", "event-current-target-check", 1, null));

            Callable directEventListener = (Callable) context.evaluateString(scope,
                    "function(event) { dispatchThis = this; dispatchCurrentTarget = event.currentTarget; }",
                    "direct-event-listener", 1, null);
            Event directEvent = new Event(target, "direct", false);
            new EcmaEventListener(directEventListener, scope, context).accept(directEvent);
            assertEquals(true, context.evaluateString(scope,
                    "dispatchThis === dispatchCurrentTarget", "direct-event-current-target-check", 1, null));

            Consumer<Object> ordinary = StandaloneRhinoRuntime.createCallback(
                    (Callable) context.evaluateString(scope, "ordinaryListener", "ordinary-listener", 1, null));
            ordinary.accept("payload");
            assertEquals("payload", context.evaluateString(scope,
                    "ordinaryValue", "ordinary-value-check", 1, null));
            assertEquals(true, context.evaluateString(scope,
                    "ordinaryThis !== dispatchCurrentTarget", "ordinary-this-check", 1, null));

            new EcmaEventListener(
                    (Callable) context.evaluateString(scope, "ordinaryListener", "ordinary-event-listener", 1, null),
                    scope, context).accept("ecma-payload");
            assertEquals("ecma-payload", context.evaluateString(scope,
                    "ordinaryValue", "ecma-ordinary-value-check", 1, null));
            assertEquals(true, context.evaluateString(scope,
                    "ordinaryThis === testGlobal", "ecma-ordinary-this-check", 1, null));
        } finally {
            AuiServices.setScript(previous);
        }
    }

    public static final class ParentHost implements AuiScriptHost {
        private final NestedHost child;

        public ParentHost(NestedHost child) {
            this.child = child;
        }

        public NestedHost child() {
            return child;
        }
    }

    public static final class NestedHost implements AuiScriptHost {
    }

    public static final class EventLikeHost implements AuiScriptHost {
    }

    private record TestScriptService(Context context, Scriptable scope) implements AuiScriptService {
        @Override
        public void eval(String code, Event event, String source) {
        }

        @Override
        public void reload() {
        }

        @Override
        public Object wrapHostObject(Object value) {
            return RhinoTestSupport.wrap(context, scope, value);
        }

        @Override
        public Consumer<Object> createCallback(Object callback) {
            return null;
        }
    }
}
