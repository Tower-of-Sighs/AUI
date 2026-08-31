package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.Loader;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueBundleCompatibilityTest {
    private static final String ECMASCRIPT = "assets/apricityui/apricity/ecmascript.js";
    private static final String ROOT = "assets/apricityui/apricity/apricityui/theme/ore/runtime/";

    @Test
    void pinnedProductionBundlesCompileOnTheAuiScriptEngine() throws Exception {
        Context context = RhinoTestSupport.enterContext();
        String vue = readRuntime("vue.aui.js");
        String mcui = readRuntime("mcui-oreui.aui.js");
        assertNotNull(context.compileString(vue, "vue.aui.js", 1, null));
        assertNotNull(context.compileString(mcui, "mcui-oreui.aui.js", 1, null));

        ScriptableObject scope = initializedScope(context);
        context.evaluateString(scope, vue, "vue.aui.js", 1, null);
        assertNotEquals(dev.latvian.mods.rhino.Scriptable.NOT_FOUND,
                ScriptableObject.getProperty(scope, "Vue", context));
        context.evaluateString(scope, mcui, "mcui-oreui.aui.js", 1, null);
        assertNotEquals(dev.latvian.mods.rhino.Scriptable.NOT_FOUND,
                ScriptableObject.getProperty(scope, "McUIVue", context));
    }

    @Test
    void vueReactivityUsesTheGenericProxyAndReflectContract() throws Exception {
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = initializedScope(context);
        context.evaluateString(scope, readRuntime("vue.aui.js"), "vue.aui.js", 1, null);

        Object result = context.evaluateString(scope,
                "var state = Vue.reactive({ count: 1 });"
                        + "var values = [];"
                        + "Vue.effect(function() { values.push(state.count); });"
                        + "state.count = 2;"
                        + "values.join(',') + '|' + Vue.isReactive(state);",
                "vue-reactivity", 1, null);

        assertEquals("1,2|true", result);
    }

    @Test
    void vueArrayMutationsUseStringProxyKeysAndTriggerLengthDependencies() throws Exception {
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = initializedScope(context);
        context.evaluateString(scope, readRuntime("vue.aui.js"), "vue.aui.js", 1, null);

        Object result = context.evaluateString(scope,
                "var target=[];var keys=[];"
                        + "var probe=new Proxy(target,{"
                        + "get:function(t,k,r){return Reflect.get(t,k,r);},"
                        + "set:function(t,k,v,r){keys.push(typeof k+':'+String(k));return Reflect.set(t,k,v,r);}});"
                        + "probe.unshift(1);"
                        + "var spliceTarget=['a','b'];"
                        + "var spliceProbe=new Proxy(spliceTarget,{"
                        + "get:function(t,k,r){return Reflect.get(t,k,r);},"
                        + "set:function(t,k,v,r){return Reflect.set(t,k,v,r);},"
                        + "deleteProperty:function(t,k){return Reflect.deleteProperty(t,k);}});"
                        + "spliceProbe.splice(0,1);"
                        + "var items=Vue.ref([]);var lengths=[];"
                        + "Vue.effect(function(){lengths.push(items.value.length);});"
                        + "items.value.unshift('a');items.value.push('b');items.value.splice(0,1);"
                        + "keys.join(',')+'|'+lengths.join(',')+'|'"
                        + "+String(spliceTarget.length)+':'+spliceTarget.join(',');",
                "vue-array-proxy-keys", 1, null);

        assertEquals("string:0,string:length|0,1,2,1|1:b", result);
    }

    @Test
    void proxySupportsTheReflectTrapsUsedByVue() throws Exception {
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = initializedScope(context);

        Object result = context.evaluateString(scope,
                "var target = { a: 1 };"
                        + "var proxy = new Proxy(target, {"
                        + " get: function(t, k, r) { return Reflect.get(t, k, r); },"
                        + " set: function(t, k, v, r) { return Reflect.set(t, k, v, r); },"
                        + " has: function(t, k) { return Reflect.has(t, k); },"
                        + " deleteProperty: function(t, k) { return Reflect.deleteProperty(t, k); },"
                        + " ownKeys: function(t) { return Reflect.ownKeys(t); },"
                        + " defineProperty: function(t, k, d) { return Reflect.defineProperty(t, k, d); }"
                        + "});"
                        + "var read = proxy.a; proxy.b = 3;"
                        + "var has = ('b' in proxy); var keys = Object.keys(proxy).sort().join(',');"
                        + "Object.defineProperty(proxy, 'c', { value: 4, enumerable: true, configurable: true });"
                        + "delete proxy.a;"
                        + "read + '|' + target.b + '|' + has + '|' + keys + '|' + target.c + '|' + ('a' in target);",
                "proxy-reflect", 1, null);

        assertEquals("1|3|true|a,b|4|false", result);
    }

    @Test
    void promiseCallbacksUseAnAsynchronousFifoMicrotaskQueue() throws Exception {
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        context.evaluateString(scope,
                "var jobs = []; var window = { queueMicrotask: function(callback) { jobs.push(callback); } };",
                "microtask-host", 1, null);
        context.evaluateString(scope, read(ECMASCRIPT), "ecmascript.js", 1, null);

        Object result = context.evaluateString(scope,
                "var order = [];"
                        + "Promise.resolve(1).then(function(v) { order.push('p' + v); return v + 1; })"
                        + ".then(function(v) { order.push('p' + v); });"
                        + "queueMicrotask(function() { order.push('q'); });"
                        + "order.push('sync');"
                        + "while (jobs.length) jobs.shift()();"
                        + "order.join(',');",
                "promise-order", 1, null);

        assertEquals("sync,p1,q,p2", result);
    }

    @Test
    void vueMountsAndUpdatesAgainstTheGenericAuiDom() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        CountDownLatch updated = new CountDownLatch(1);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "__auiTestDocument",
                RhinoTestSupport.wrap(context, scope, document), context);
        ScriptableObject.putProperty(scope, "__auiTestWindow",
                RhinoTestSupport.wrap(context, scope, Window.window), context);
        ScriptableObject.putProperty(scope, "__auiUpdated",
                RhinoTestSupport.wrap(context, scope, updated), context);
        String bootstrap = Loader.readGlobalJS()
                .replace("let document = ApricityUI.getDocumentByUUID(\"__AUI_DOCUMENT_UUID__\");",
                        "let document = __auiTestDocument;")
                .replace("let window = ApricityUI.getWindow();", "let window = __auiTestWindow;");

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, bootstrap, "global.js", 1, null);
            context.evaluateString(scope, readRuntime("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, "var root = document.createElement('div');", "dom-root", 1, null);
            context.evaluateString(scope,
                    "document.body.appendChild(root);"
                            + "var value = Vue.ref('initial');"
                            + "var app = Vue.createApp({ render: function() { return Vue.h('span', { id: 'vue-probe' }, value.value); } });"
                            + "app.mount(root); value.value = 'updated';"
                            + "Vue.nextTick(function() { __auiUpdated.countDown(); });",
                    "vue-mount", 1, null);
        }

        assertTrue(updated.await(2, TimeUnit.SECONDS));
        assertEquals("updated", document.querySelector("#vue-probe").getTextContent());
    }

    private static ScriptableObject initializedScope(Context context) throws Exception {
        ScriptableObject scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "window", RhinoTestSupport.wrap(context, scope, Window.window), context);
        context.evaluateString(scope, read(ECMASCRIPT), "ecmascript.js", 1, null);
        assertEquals("function|object|function|function", context.evaluateString(scope,
                "typeof Proxy + '|' + typeof Reflect + '|' + typeof Promise + '|' + typeof queueMicrotask",
                "ecmascript-types", 1, null));
        return scope;
    }

    private static String read(String name) throws Exception {
        try (InputStream stream = VueBundleCompatibilityTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readRuntime(String name) throws Exception {
        return read(ROOT + name);
    }
}
