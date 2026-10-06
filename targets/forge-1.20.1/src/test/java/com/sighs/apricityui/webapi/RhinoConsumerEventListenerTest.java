package com.sighs.apricityui.webapi;

import com.sighs.apricityui.forge.ScriptService;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.spi.AuiServices;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhinoConsumerEventListenerTest {
    @Test
    void javascriptFunctionConvertsToConsumerAndCanBeRemovedByIdentity() {
        Context context = Context.enter();
        ScriptableObject scope = context.initStandardObjects();
        Document document = TestDocumentFactory.createDocument();
        Window window = new Window();
        AuiScriptService previousScript = AuiServices.script();
        AuiServices.setScript(ScriptService.INSTANCE);
        ScriptableObject.putProperty(scope, "window", Context.javaToJS(context, window, scope), context);

        Object calls;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            calls = context.evaluateString(scope, """
                var calls = 0;
                var listener = function(event) { calls++; };
                window.addEventListener('custom', listener);
                window.dispatchEvent(window.createEvent('custom', false));
                window.removeEventListener('custom', listener);
                window.dispatchEvent(window.createEvent('custom', false));
                calls;
                    """, "<consumer-event-listener-test>", 1, null);
        } finally {
            AuiServices.setScript(previousScript);
        }

        assertEquals(1.0, ((Number) calls).doubleValue());
    }
}
