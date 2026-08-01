package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.test.TestRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTextAlignmentTest {
    @Test
    void outOfFlowPseudoElementDoesNotDisableDirectTextAlignment() throws Exception {
        TestRuntime.assumeClassUsable("com.mojang.blaze3d.vertex.PoseStack", "render element pseudo geometry");
        Document document = createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(".label::after { content: '+'; position: absolute; left: 50%; }",
                cache, "test://text-align-pseudo.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element label = new Element(document, "div");
        label.setAttribute("class", "label");
        label.setAttribute("style", "text-align: center;");
        label.setTextContent("Viewport");
        document.body.appendChild(label);

        Method method = Element.class.getDeclaredMethod("shouldAlignDirectNormalFlowTextRuns");
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(label));
    }

    @Test
    void normalFlowTextAlignmentUsesTheContainingLineWidth() {
        Text text = new Text();
        text.textAlign = "center";
        assertEquals(30, Element.computeAlignedX(text, 100, 40, false), 0.001);

        text.textAlign = "right";
        assertEquals(60, Element.computeAlignedX(text, 100, 40, false), 0.001);
    }

    private static Document createDocument() {
        Document document = new Document("test://text-align", false);
        document.documentElement = new Html(document);
        document.head = new Head(document);
        document.body = new Body(document);
        document.documentElement.appendChild(document.head);
        document.documentElement.appendChild(document.body);
        return document;
    }

}
