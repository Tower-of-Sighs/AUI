package com.sighs.apricityui.style;

import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShorthandParserTest {
    private Style stylesheet(String declarations, String inline) {
        Map<String, Map<String, CSS.Declaration>> rules = new LinkedHashMap<>();
        CSS.readCSS(".item { " + declarations + " }", rules, "test://shorthands.css");
        Style style = new Style();
        style.mergeCascade(rules.get(".item"), inline);
        style.resolveVarReferences(null);
        return style;
    }

    @Test
    void gridVariablesResolveTheWholeShorthand() {
        for (boolean inline : new boolean[]{false, true}) {
            String declarations = "grid-template:var(--template);grid-area:var(--area);"
                    + "--template:20px / 30px 40px;--area:1 / 2 / 3 / 4;";
            Style style = inline ? new Style() : stylesheet(declarations, "");
            if (inline) {
                style.merge(declarations);
                assertEquals("var(--template)", style.pendingShorthands.get("grid-template-rows").value());
                assertEquals("var(--area)", style.pendingShorthands.get("grid-row-start").value());
                style.resolveVarReferences(null);
            }
            assertEquals("20px", style.gridTemplateRows);
            assertEquals("30px 40px", style.gridTemplateColumns);
            assertEquals("1", style.gridRowStart);
            assertEquals("2", style.gridColumnStart);
            assertEquals("3", style.gridRowEnd);
            assertEquals("4", style.gridColumnEnd);
        }
    }

    @Test
    void gridVariablesRespectLonghandOrderAndImportance() {
        String variables = "--template:20px / 30px;";
        assertEquals("50px", stylesheet(variables
                + "grid-template:var(--template);grid-template-columns:50px;", "").gridTemplateColumns);
        assertEquals("30px", stylesheet(variables
                + "grid-template-columns:50px;grid-template:var(--template);", "").gridTemplateColumns);
        assertEquals("50px", stylesheet(variables
                + "grid-template-columns:50px !important;grid-template:var(--template);", "").gridTemplateColumns);
        assertEquals("30px", stylesheet(variables
                + "grid-template:var(--template) !important;grid-template-columns:50px;",
                "grid-template-columns:60px;").gridTemplateColumns);
        assertEquals("60px", stylesheet(variables
                + "grid-template:var(--template) !important;", "grid-template-columns:60px !important;").gridTemplateColumns);
        Style inline = new Style();
        inline.merge(variables + "grid-template:var(--template);grid-template-columns:50px;");
        inline.resolveVarReferences(null);
        assertEquals("50px", inline.gridTemplateColumns);
        inline = new Style();
        inline.merge(variables + "grid-template-columns:50px !important;grid-template:var(--template);");
        inline.resolveVarReferences(null);
        assertEquals("50px", inline.gridTemplateColumns);
    }

    @Test
    void gridVariableFallbacksAndOverlappingShorthandsResolveIndependently() {
        Style style = stylesheet("--row:2 / 5;grid-area:var(--missing, 1 / 2 / 3 / 4);"
                + "grid-row:var(--nested, var(--row));place-items:var(--alignment, center end);", "");
        assertEquals("2", style.gridRowStart);
        assertEquals("5", style.gridRowEnd);
        assertEquals("2", style.gridColumnStart);
        assertEquals("4", style.gridColumnEnd);
        assertEquals("center", style.alignItems);
        assertEquals("end", style.justifyItems);
    }

    @Test
    void invalidFlexDoesNotOverrideValidDeclarations() {
        for (String invalid : new String[]{"bogus bogus", "bogus", "-1", "1 -1", "1 -2px",
                "1 20px 2", "1 2 3", "1 2 20px extra", "20px 30px", "2nonsense", "NaN", "Infinity",
                "calc(20bogus)", "calc(20px nonsense)", "min(20px nonsense,30px)", "clamp(20px,30px)", "1."}) {
            assertTrue(ShorthandParser.expandFlex(invalid).isEmpty(), invalid);
            for (String priority : new String[]{"", " !important"}) {
                Style style = stylesheet("flex:2 3 40px;flex:" + invalid + priority + ";", "");
                assertEquals("2", style.flexGrow, invalid);
                assertEquals("3", style.flexShrink, invalid);
                assertEquals("40px", style.flexBasis, invalid);
                Style inline = new Style();
                inline.merge("flex:2 3 40px;flex:" + invalid + priority + ";");
                assertEquals("2", inline.flexGrow, invalid);
                assertEquals("3", inline.flexShrink, invalid);
                assertEquals("40px", inline.flexBasis, invalid);
            }
            assertEquals("7", stylesheet("flex-grow:7;flex:" + invalid + ";", "").flexGrow);
        }
        Style direct = new Style();
        direct.update("flex-grow", "7");
        direct.update("flex", "bogus bogus");
        assertEquals("7", direct.flexGrow);
    }

    @Test
    void validFlexFormsShareOneExpansion() {
        String[][] cases = {{"none", "0", "0", "auto"}, {"auto", "1", "1", "auto"},
                {"2", "2", "1", "0%"}, {"2 1", "2", "1", "0%"},
                {"0 0 250px", "0", "0", "250px"}, {"20px", "1", "1", "20px"},
                {"2 30%", "2", "1", "30%"}, {"40px 2 3", "2", "3", "40px"},
                {"1 2 0", "1", "2", "0"}, {"2 calc(50% - 10px)", "2", "1", "calc(50% - 10px)"},
                {"2 min(30px,40px)", "2", "1", "min(30px,40px)"},
                {"clamp(10px,30%,50px)", "1", "1", "clamp(10px,30%,50px)"},
                {"content", "1", "1", "content"}, {"1e2", "100", "1", "0%"},
                {".0000001", "0.0000001", "1", "0%"}, {"1e10", "10000000000", "1", "0%"}};
        for (String[] test : cases) {
            Map<String, String> expected = Map.of("flex-grow", test[1], "flex-shrink", test[2], "flex-basis", test[3]);
            assertEquals(expected, ShorthandParser.expandFlex(test[0]), test[0]);
            Style style = new Style();
            style.update("flex", test[0]);
            assertEquals(test[1], style.flexGrow);
            assertEquals(test[2], style.flexShrink);
            assertEquals(test[3], style.flexBasis);
        }
        for (String keyword : new String[]{"initial", "inherit", "unset", "revert", "revert-layer"}) {
            assertEquals(Map.of("flex-grow", keyword, "flex-shrink", keyword, "flex-basis", keyword),
                    ShorthandParser.expandFlex(keyword));
        }
    }

    @Test
    void flexVariablesAreValidatedAfterResolution() {
        Style style = stylesheet("--flex:2 3 40px;flex:var(--flex);flex-shrink:7;", "");
        assertEquals("2", style.flexGrow);
        assertEquals("7", style.flexShrink);
        assertEquals("40px", style.flexBasis);
        assertEquals("unset", stylesheet("--flex:bogus bogus;flex:var(--flex);", "").flexGrow);
    }

    @Test
    void deferredValuesAreIndependentInStyleSnapshots() {
        Style original = new Style();
        original.merge("--template:20px / 30px;grid-template:var(--template);");
        Style clone = original.clone();
        Style copy = new Style();
        copy.copyFrom(original);
        clone.update("grid-template-columns", "40px");
        copy.update("--template", "50px / 60px");
        original.resolveVarReferences(null);
        clone.resolveVarReferences(null);
        copy.resolveVarReferences(null);
        assertEquals("30px", original.gridTemplateColumns);
        assertEquals("40px", clone.gridTemplateColumns);
        assertEquals("60px", copy.gridTemplateColumns);
    }
}
