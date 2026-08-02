package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Selector;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsStylesheetEditingTest {
    private static final Path TEMPLATE = Path.of(
            "src/main/resources/assets/apricityui/apricity/devtools/devtools.html");

    @Test
    void exposesAuthorRulesAndMarksCascadeLosers() {
        Document document = styledDocument();
        try {
            Element target = document.querySelector("#target");
            assertNotNull(target);
            List<CSS.DebugRule> testRules = testRules(document);
            assertEquals(3, testRules.size());
            assertTrue(testRules.get(0).properties().containsKey("padding"));
            assertFalse(testRules.get(0).properties().containsKey("padding-left"),
                    "DevTools must expose the authored shorthand, not generated longhands");

            List<Selector.DebugStyleBlock> blocks = Selector.getDebugStyles(target);
            int lowerOrder = testRules.get(0).order();
            int winnerOrder = testRules.get(1).order();
            Selector.DebugDeclaration lowerColor = declaration(blocks, lowerOrder, "color");
            Selector.DebugDeclaration winningColor = declaration(blocks, winnerOrder, "color");
            assertTrue(lowerColor.overridden());
            assertFalse(winningColor.overridden());
            assertTrue(winningColor.important());

            target.setAttribute("style", "color: green;");
            assertTrue(declaration(Selector.getDebugStyles(target), lowerOrder, "color").overridden());
            assertTrue(declaration(Selector.getDebugStyles(target), winnerOrder, "color").overridden());
        } finally {
            document.remove();
        }
    }

    @Test
    void editsTheMatchedRuleAndSupportsDisableUndoAndRedo() throws Exception {
        HTML.putTemple(DevToolsController.PATH, Files.readString(TEMPLATE));
        Document document = styledDocument();
        DevToolsController controller = new DevToolsController();
        try {
            Element target = document.querySelector("#target");
            assertNotNull(target);
            List<CSS.DebugRule> testRules = testRules(document);
            int lowerOrder = testRules.get(0).order();
            int winnerOrder = testRules.get(1).order();
            assertTrue(controller.selectDocument(document));
            assertTrue(controller.selectElement(target));

            controller.updateStylesheetStyle(target, winnerOrder, "color", "gold !important");
            assertEquals("gold", Selector.matchCSS(target).get("color"));
            assertFalse(target.hasAttribute("style"));

            Document tool = controller.getToolDocument();
            tool.querySelector(".inspector-tab[data-tab=\"styles\"]").click();
            Element initiallyOverriddenRow = tool.querySelector(
                    ".style-rule[data-rule-order=\"" + lowerOrder + "\"] .style-prop[data-property=\"color\"]");
            assertNotNull(initiallyOverriddenRow);
            assertTrue(initiallyOverriddenRow.getClassNames().contains("overridden"));

            controller.toggleStylesheetStyle(target, winnerOrder, "color");
            assertEquals("red", Selector.matchCSS(target).get("color"));
            Selector.DebugStyleBlock disabledBlock = block(Selector.getDebugStyles(target), winnerOrder);
            DevToolsController.RuleStyle disabled = controller.stylesheetStyles(disabledBlock).get("color");
            assertNotNull(disabled);
            assertTrue(disabled.disabled());

            assertTrue(controller.undoEdit());
            assertEquals("gold", Selector.matchCSS(target).get("color"));
            assertTrue(controller.redoEdit());
            assertEquals("red", Selector.matchCSS(target).get("color"));

            Element fallbackRow = tool.querySelector(
                    ".style-rule[data-rule-order=\"" + lowerOrder + "\"] .style-prop[data-property=\"color\"]");
            Element disabledRow = tool.querySelector(
                    ".style-rule[data-rule-order=\"" + winnerOrder + "\"] .style-prop[data-property=\"color\"]");
            assertNotNull(fallbackRow);
            assertNotNull(disabledRow);
            assertFalse(fallbackRow.getClassNames().contains("overridden"));
            assertTrue(disabledRow.getClassNames().contains("disabled"));
            assertNotNull(fallbackRow.querySelector("input.style-name"));
            assertNotNull(fallbackRow.querySelector("input.style-value"));

            Element shortCustomRow = tool.querySelector(
                    ".style-rule[data-rule-order=\"" + lowerOrder + "\"] .style-prop[data-property=\"--x\"]");
            Element longCustomRow = tool.querySelector(
                    ".style-rule[data-rule-order=\"" + lowerOrder
                            + "\"] .style-prop[data-property=\"--aui-slot-cycle-interval\"]");
            assertNotNull(shortCustomRow);
            assertNotNull(longCustomRow);
            Element shortPropertyName = shortCustomRow.querySelector(".style-name");
            Element longPropertyName = longCustomRow.querySelector(".style-name");
            assertNotNull(shortPropertyName);
            assertNotNull(longPropertyName);
            assertTrue(com.sighs.apricityui.layout.Size.parse(longPropertyName.getComputedStyle().width)
                            > com.sighs.apricityui.layout.Size.parse(shortPropertyName.getComputedStyle().width),
                    "Each property name must be sized from its own rendered text");
            assertEquals("0", longPropertyName.getComputedStyle().flexGrow);
            assertEquals("0", longPropertyName.getComputedStyle().flexShrink);
            assertEquals("auto", longPropertyName.getComputedStyle().flexBasis);
            assertEquals("0", longPropertyName.getComputedStyle().minWidth);
        } finally {
            if (controller.isOpen()) controller.toggle();
            document.remove();
        }
    }

    @Test
    void editsPropertiesOnTheOriginalRuleAndKeepsHistoryOrdered() throws Exception {
        HTML.putTemple(DevToolsController.PATH, Files.readString(TEMPLATE));
        Document document = styledDocument();
        DevToolsController controller = new DevToolsController();
        try {
            Element target = document.querySelector("#target");
            assertNotNull(target);
            CSS.DebugRule backgroundRule = testRules(document).get(2);
            int ruleOrder = backgroundRule.order();
            assertTrue(controller.selectDocument(document));
            assertTrue(controller.selectElement(target));

            controller.renameStylesheetStyle(target, ruleOrder, "background", "background-color");
            assertFalse(backgroundRule.properties().containsKey("background"));
            assertEquals("black", backgroundRule.properties().get("background-color").value());
            assertEquals("black", Selector.matchCSS(target).get("background-color"));

            controller.updateStylesheetStyle(target, ruleOrder, "background-color", "navy");
            assertEquals("navy", Selector.matchCSS(target).get("background-color"));

            controller.addStylesheetStyle(target, ruleOrder, "border-color", "gold");
            assertEquals("gold", Selector.matchCSS(target).get("border-color"));

            controller.deleteStylesheetStyle(target, ruleOrder, "background-color");
            assertFalse(backgroundRule.properties().containsKey("background-color"));
            assertNull(Selector.matchCSS(target).get("background-color"));

            assertTrue(controller.undoEdit());
            assertEquals("navy", Selector.matchCSS(target).get("background-color"));
            assertTrue(controller.undoEdit());
            assertNull(Selector.matchCSS(target).get("border-color"));
            assertTrue(controller.redoEdit());
            assertEquals("gold", Selector.matchCSS(target).get("border-color"));
        } finally {
            if (controller.isOpen()) controller.toggle();
            document.remove();
        }
    }

    @Test
    void importantDeclarationOverridesHigherSpecificityNormalDeclaration() {
        Document document = styledDocument();
        try {
            Element target = document.querySelector("#target");
            assertNotNull(target);
            List<CSS.DebugRule> testRules = testRules(document);
            int lowerOrder = testRules.get(0).order();
            int winnerOrder = testRules.get(1).order();
            testRules.get(0).properties().put("color", new CSS.Declaration("red", true));
            testRules.get(1).properties().put("color", new CSS.Declaration("blue", false));
            CSS.rebuildCacheFromDebugRules(document.CSSDebugRules, document.CSSCache);
            document.rebuildSelectorIndex();

            assertEquals("red", Selector.matchCSS(target).get("color"));
            assertFalse(declaration(Selector.getDebugStyles(target), lowerOrder, "color").overridden());
            assertTrue(declaration(Selector.getDebugStyles(target), winnerOrder, "color").overridden());
        } finally {
            document.remove();
        }
    }

    private static Document styledDocument() {
        String path = "test://stylesheet-edit-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body><div id=\"target\" class=\"card\"></div></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        int orderStart = document.CSSDebugRules.stream()
                .mapToInt(CSS.DebugRule::order)
                .max()
                .orElse(-1) + 1;
        CSS.readCSS("""
                .card {
                    color: red;
                    padding: 1px 2px;
                    --x: 1;
                    --aui-slot-cycle-interval: 1000;
                }
                #target { color: blue !important; }
                .card { background: black; }
                """, document.CSSCache, document.CSSDebugRules, "styles/test.css", orderStart);
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
        return document;
    }

    private static List<CSS.DebugRule> testRules(Document document) {
        return document.CSSDebugRules.stream()
                .filter(rule -> "styles/test.css".equals(rule.sourcePath()))
                .toList();
    }

    private static Selector.DebugStyleBlock block(List<Selector.DebugStyleBlock> blocks, int order) {
        return blocks.stream().filter(candidate -> candidate.ruleOrder() == order).findFirst().orElseThrow();
    }

    private static Selector.DebugDeclaration declaration(List<Selector.DebugStyleBlock> blocks,
                                                          int order, String property) {
        Selector.DebugDeclaration result = block(blocks, order).declarations().get(property);
        assertNotNull(result);
        return result;
    }

}
