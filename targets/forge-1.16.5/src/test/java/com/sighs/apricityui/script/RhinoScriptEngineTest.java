package com.sighs.apricityui.script;

import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhinoScriptEngineTest {
    @Test
    void backendProbeAcceptsTheRhinoOnTheClasspath() {
        assertNotEquals(ScriptRuntimeSupport.Backend.NONE, ScriptRuntimeSupport.backend());
    }

    @Test
    void standaloneRhinoKeepsDocumentGlobalsAndBindsApricityUI() {
        Document document = new Document("test://rhino-engine", false);
        try (Document.ContextScope ignored = Document.withContext(document)) {
            RhinoScriptEngine.evalGlobal("var marker = 'ok';", document.getUuid().toString());
            RuntimeException failure = assertThrows(RuntimeException.class, () -> RhinoScriptEngine.eval("""
                    if (marker !== 'ok') throw 'global scope was not reused';
                    if (typeof ApricityUI === 'undefined') throw 'ApricityUI binding missing';
                    throw 'sentinel';
                    """, null, "test"));
            assertTrue(String.valueOf(failure.getMessage()).contains("sentinel"));
        }
    }
}
