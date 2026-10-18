package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputDefaultStretchTest {
    @Test
    void frameworkInputDefaultDoesNotPreventColumnFlexStretch() throws Exception {
        var document = TestDocumentFactory.createDocument();
        var rules = new HashMap<String, Map<String, CSS.Declaration>>();
        try (var stream = getClass().getResourceAsStream("/assets/apricityui/apricity/global.css")) {
            assertNotNull(stream);
            CSS.readCSS(new String(stream.readAllBytes(), StandardCharsets.UTF_8), rules, "global.css");
        }
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        Element group = document.createElement("div");
        group.setAttribute("style", "display:flex;flex-direction:column;width:400px;");
        document.body.appendChild(group);
        Element input = document.createElement("input");
        input.setAttribute("style", "min-width:0;padding:10px 12px;border:4px solid black;");
        input.setValue("example");
        group.appendChild(input);
        document.commitRenderState();
        assertEquals("auto", input.getComputedStyle().width);
        assertEquals(400, Size.of(input).width(), 0.01);
        input.setAttribute("style", "width:120px;padding:0;border:0;");
        document.commitRenderState();
        assertEquals(120, Size.of(input).width(), 0.01);
    }
}
