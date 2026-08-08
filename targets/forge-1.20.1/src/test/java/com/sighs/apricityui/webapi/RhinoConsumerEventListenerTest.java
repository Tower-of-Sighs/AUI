package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Window;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhinoConsumerEventListenerTest {
    @Test
    void javascriptFunctionConvertsToConsumerAndCanBeRemovedByIdentity() {
        Context context = Context.enter();
        ScriptableObject scope = context.initStandardObjects();
        Window window = new Window();
        ScriptableObject.putProperty(scope, "window", Context.javaToJS(context, window, scope), context);

        Object calls = context.evaluateString(scope, """
                var calls = 0;
                var listener = function(event) { calls++; };
                window.addEventListener('custom', listener);
                window.dispatchEvent(window.createEvent('custom', false));
                window.removeEventListener('custom', listener);
                window.dispatchEvent(window.createEvent('custom', false));
                calls;
                """, "<consumer-event-listener-test>", 1, null);

        assertEquals(1.0, ((Number) calls).doubleValue());
    }
}
