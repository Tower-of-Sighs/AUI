package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShorthandCascadeTest {
    private Element item(String css, String inline) {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> rules = new LinkedHashMap<>();
        CSS.readCSS(css, rules, "test://shorthand-cascade.css");
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        document.body.setAttribute("style", "--template:20px / 30px;--area:1 / 2 / 3 / 4;--flex:2 3 40px;");
        Element item = new Element(document, "div");
        item.setAttribute("id", "item");
        item.setAttribute("class", "item");
        item.setAttribute("style", inline);
        document.body.appendChild(item);
        return item;
    }

    @Test
    void moreSpecificVariableShorthandOverridesLonghands() {
        Style style = item("#item {grid-template:var(--template);grid-area:var(--area);flex:var(--flex);}"
                + ".item {grid-template-columns:50px;grid-column-start:5;flex-grow:7;}", "").getComputedStyle();
        assertEquals("20px", style.gridTemplateRows);
        assertEquals("30px", style.gridTemplateColumns);
        assertEquals("2", style.gridColumnStart);
        assertEquals("2", style.flexGrow);
    }

    @Test
    void moreSpecificLonghandsOverrideVariableShorthand() {
        Style style = item("#item {grid-template-columns:50px;grid-column-start:5;flex-grow:7;}"
                + ".item {grid-template:var(--template);grid-area:var(--area);flex:var(--flex);}", "").getComputedStyle();
        assertEquals("20px", style.gridTemplateRows);
        assertEquals("50px", style.gridTemplateColumns);
        assertEquals("5", style.gridColumnStart);
        assertEquals("7", style.flexGrow);
        assertEquals("40px", style.flexBasis);
    }

    @Test
    void invalidComputedShorthandUsesInitialValuesWithoutRevivingEarlierDeclarations() {
        Style style = item(".item {grid-template-columns:50px;grid-template:var(--missing);"
                + "flex-grow:7;flex:var(--invalid,bogus bogus);}", "").getComputedStyle();
        assertEquals("unset", style.gridTemplateColumns);
        assertEquals("0", style.flexGrow);
        assertEquals("1", style.flexShrink);
        assertEquals("auto", style.flexBasis);
    }

    @Test
    void importantVariableShorthandAndInlineLonghandsKeepTheirPriority() {
        String css = ".item {grid-template:var(--template) !important;flex:var(--flex) !important;}";
        Style normal = item(css, "grid-template-columns:50px;flex-grow:7;").getComputedStyle();
        assertEquals("30px", normal.gridTemplateColumns);
        assertEquals("2", normal.flexGrow);
        Style important = item(css, "grid-template-columns:50px !important;flex-grow:7 !important;").getComputedStyle();
        assertEquals("50px", important.gridTemplateColumns);
        assertEquals("7", important.flexGrow);
    }
}
