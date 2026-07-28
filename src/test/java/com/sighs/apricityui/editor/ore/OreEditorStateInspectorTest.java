package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorStateInspectorTest {
    @Test
    void stateSelectorReportsOnlyNonEmptyPseudoStateOverrides() {
        OreComponentNode component = new OreComponentNode("button", "Build");

        assertFalse(OreEditorController.hasStateOverride(component, OreComponentNode.VisualState.DEFAULT));
        assertFalse(OreEditorController.hasStateOverride(component, OreComponentNode.VisualState.HOVER));

        component.stateStyle(OreComponentNode.VisualState.HOVER).set("background", "#654321");

        assertTrue(OreEditorController.hasStateOverride(component, OreComponentNode.VisualState.HOVER));
        assertFalse(OreEditorController.hasStateOverride(component, OreComponentNode.VisualState.ACTIVE));
    }

    @Test
    void componentColorFieldAcceptsCssColorFormsButRejectsDeclarations() {
        assertTrue(OreEditorController.validCssColor("#123"));
        assertTrue(OreEditorController.validCssColor("#123456"));
        assertTrue(OreEditorController.validCssColor("transparent"));
        assertTrue(OreEditorController.validCssColor("rgb(12, 34, 56)"));
        assertTrue(OreEditorController.validCssColor("var(--ore-purple)"));
        assertFalse(OreEditorController.validCssColor("background:#123456"));
        assertFalse(OreEditorController.validCssColor("#123456; color:red"));
    }

    @Test
    void colorFieldExtractsAndRecomposesAlphaWithoutLosingRgbChannels() {
        OreEditorController.ColorValue hex = OreEditorController.ColorValue.parse("#1234");
        assertEquals("#112233", hex.hex());
        assertEquals(68D / 255D, hex.alpha());

        OreEditorController.ColorValue rgba = OreEditorController.ColorValue.parse("rgba(12, 34, 56, 0.5)");
        assertEquals("#0c2238", rgba.hex());
        assertEquals(0.5D, rgba.alpha());
        assertEquals("rgba(12, 34, 56, 0.5)", OreEditorController.ColorValue.toCss(rgba.hex(), rgba.alpha()));
        assertEquals("#0c2238", OreEditorController.ColorValue.toCss(rgba.hex(), 1D));
    }

    @Test
    void shadowFieldParsesAndComposesAStandardSingleShadow() {
        OreEditorController.ShadowValue shadow = OreEditorController.parseShadow("inset 2px 3px 4px 5px rgba(1, 2, 3, 0.5)");

        assertTrue(shadow.inset());
        assertEquals("2px", shadow.offsetX());
        assertEquals("3px", shadow.offsetY());
        assertEquals("4px", shadow.blur());
        assertEquals("5px", shadow.spread());
        assertEquals("rgba(1, 2, 3, 0.5)", shadow.color());
        assertEquals("inset 2px 3px 4px 5px rgba(1, 2, 3, 0.5)", shadow.toCss());

        OreEditorController.ShadowValue fallback = OreEditorController.parseShadow("not a shadow");
        assertFalse(fallback.inset());
        assertEquals("0px", fallback.offsetX());
        assertEquals("#000000", fallback.color());
    }

    @Test
    void boxModelFieldExpandsShorthandAndPrefersIndividualEdges() {
        OreEditorController.BoxValue shorthand = OreEditorController.BoxValue.parse("1px 2px 3px");
        assertEquals("1px", shorthand.top());
        assertEquals("2px", shorthand.right());
        assertEquals("3px", shorthand.bottom());
        assertEquals("2px", shorthand.left());

        OreComponentNode component = new OreComponentNode("div", "");
        component.style().set("margin", "4px");
        component.style().set("margin-left", "12px");
        OreEditorController.BoxValue mixed = OreEditorController.BoxValue.of(component, "margin");
        assertEquals("4px", mixed.top());
        assertEquals("12px", mixed.left());
    }

    @Test
    void lengthFieldKeepsAutoSeparateFromNumericUnitValues() {
        OreEditorController.LengthValue auto = OreEditorController.LengthValue.parse("auto");
        assertTrue(auto.auto());
        assertEquals("px", auto.unit());

        OreEditorController.LengthValue percentage = OreEditorController.LengthValue.parse("12.5%");
        assertFalse(percentage.auto());
        assertEquals("12.5", percentage.number());
        assertEquals("%", percentage.unit());

        OreEditorController.LengthValue unsupported = OreEditorController.LengthValue.parse("calc(100% - 8px)");
        assertEquals("", unsupported.number());
        assertEquals("px", unsupported.unit());
    }
}
