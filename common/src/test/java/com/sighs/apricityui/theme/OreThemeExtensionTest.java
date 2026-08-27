package com.sighs.apricityui.theme;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the Ore theme extension (roadmap.md).
 *
 * The extension is append-only: legacy classes must keep their computed
 * styles, and numbered variant classes (-2/-3) plus the new components
 * (switch, checkbox, radio, slider, tooltip, dropdown, toast, drawer,
 * loading, icon button, scrollbar, sidebar) must resolve to the ported
 * token values.
 */
class OreThemeExtensionTest {
    private static final Path ORE = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css");

    @Test
    void legacyClassesKeepBaselineComputedStyles() throws Exception {
        Document document = document();

        assertEquals("#3c8527", style(document, "button", "button button-primary").backgroundColor);
        assertEquals("#7345e5", style(document, "button", "button button-secondary").backgroundColor);
        assertEquals("#b33b31", style(document, "button", "button button-danger").backgroundColor);
        assertEquals("#48494a", style(document, "div", "card").backgroundColor);
        assertEquals("#48494a", style(document, "div", "panel").backgroundColor);
        assertEquals("#313233", style(document, "input", "form-input").backgroundColor);
        assertEquals("#363739", style(document, "div", "alert alert-warning").backgroundColor);
        assertEquals("#3c8527", style(document, "div", "progress-bar").backgroundColor);
        assertEquals("#37383a", style(document, "button", "tab").backgroundColor);
        assertEquals("#2d78a8", style(document, "span", "badge").backgroundColor);
    }

    @Test
    void semanticTokensAreRegisteredAlongsideLegacyTokens() throws Exception {
        Document document = document();
        Element root = document.body;

        assertEquals("var(--ore-ink)", root.getComputedStyle().getPropertyValue("--ore-color-foreground"));
        assertEquals("var(--ore-green)", root.getComputedStyle().getPropertyValue("--ore-color-primary"));
        assertEquals("2px", root.getComputedStyle().getPropertyValue("--ore-size-unit"));
        assertEquals("100ms", root.getComputedStyle().getPropertyValue("--ore-motion-fast"));
        // Legacy tokens untouched.
        assertEquals("#f4f5f7", root.getComputedStyle().getPropertyValue("--ore-ink"));
        assertEquals("#3c8527", root.getComputedStyle().getPropertyValue("--ore-green"));
        // Ported scale tokens resolve.
        assertEquals("#8c8d90", root.getComputedStyle().getPropertyValue("--ore-gray-50"));
        assertEquals("#1d4d13", root.getComputedStyle().getPropertyValue("--ore-button-primary-2-shadow"));
    }

    @Test
    void buttonVariantsApplyFlatAndUnitBevelStyles() throws Exception {
        Document document = document();

        Element flatSecondary = element(document, "button", "button button-secondary-2");
        assertEquals("#d0d1d4", flatSecondary.getComputedStyle().backgroundColor);
        assertEquals("#1e1e1f", flatSecondary.getComputedStyle().borderColor);
        assertNotEquals(
                style(document, "button", "button button-secondary").backgroundColor,
                flatSecondary.getComputedStyle().backgroundColor);

        Element flatDanger = element(document, "button", "button button-danger-2");
        assertEquals("#ca3636", flatDanger.getComputedStyle().backgroundColor);

        Element unitBevel = element(document, "button", "button button-primary-3");
        assertEquals("#3c8527", unitBevel.getComputedStyle().backgroundColor);
        assertEquals("44px", unitBevel.getComputedStyle().minHeight);

        Element disabledFlat = element(document, "button", "button button-primary-2 disabled");
        assertEquals("#d0d1d4", disabledFlat.getComputedStyle().backgroundColor);
        assertEquals("#8c8d90", disabledFlat.getComputedStyle().borderColor);
        assertEquals("#48494a", disabledFlat.getComputedStyle().color);
    }

    @Test
    void surfaceAndFormVariantsApply() throws Exception {
        Document document = document();

        Element card2 = element(document, "div", "card card-2");
        assertEquals("#48494a", card2.getComputedStyle().backgroundColor);

        Element panel2 = element(document, "div", "panel panel-2");
        assertEquals("#313233", panel2.getComputedStyle().backgroundColor);
        assertEquals("2px solid #1e1e1f", panel2.getComputedStyle().borderTop);

        Element input2 = element(document, "input", "form-input-2");
        assertEquals("#313233", input2.getComputedStyle().backgroundColor);
        assertEquals("2px solid #1e1e1f", input2.getComputedStyle().borderTop);

        Element invalid2 = element(document, "input", "form-input-2 is-invalid");
        assertEquals("#cf4a4a", invalid2.getComputedStyle().borderColor);

        Element tab2 = element(document, "button", "tab-2");
        assertEquals("#48494a", tab2.getComputedStyle().backgroundColor);

        Element tab2Active = element(document, "button", "tab-2 active");
        assertEquals("#3c8527", tab2Active.getComputedStyle().backgroundColor);

        Element tab3 = element(document, "button", "tab-3 active");
        assertEquals("#313233", tab3.getComputedStyle().backgroundColor);

        Element progress2 = element(document, "div", "progress-2");
        assertEquals("#1e1e1f", progress2.getComputedStyle().backgroundColor);
    }

    @Test
    void choiceControlsApply() throws Exception {
        Document document = document();

        // katorlys ore-switch port: host > .switch-control > .switch-status + .switch-button.
        Element switchOff = element(document, "span", "switch");
        Element controlOff = child(document, switchOff, "switch-control");
        Element statusOff = child(document, controlOff, "switch-status");
        Element buttonOff = child(document, controlOff, "switch-button");
        assertEquals("56px", controlOff.getComputedStyle().width);
        assertEquals("30px", controlOff.getComputedStyle().height);
        assertEquals("26px", statusOff.getComputedStyle().width);
        assertEquals("26px", statusOff.getComputedStyle().height);
        assertEquals("#1e1e1f", statusOff.getComputedStyle().backgroundColor);
        assertEquals("30px", buttonOff.getComputedStyle().width);
        assertEquals("-1", buttonOff.getComputedStyle().order);
        // Unchecked: fill flush against the thumb (left), dark frame outside.
        assertEquals("0px 4px, 0px 2px", statusOff.getComputedStyle().backgroundPosition);
        assertTrue(statusOff.getComputedStyle().backgroundImage.contains("#8c8d90"));
        assertTrue(statusOff.getComputedStyle().backgroundImage.contains("#a3a4a6"));

        Element switchOn = element(document, "span", "switch on");
        Element controlOn = child(document, switchOn, "switch-control");
        Element statusOn = child(document, controlOn, "switch-status");
        Element buttonOn = child(document, controlOn, "switch-button");
        assertEquals("0", buttonOn.getComputedStyle().order);
        // Checked: bevel mirrors, dark frame on the outer (left) edge.
        assertEquals("4px 4px, 2px 2px", statusOn.getComputedStyle().backgroundPosition);
        assertTrue(statusOn.getComputedStyle().backgroundImage.contains("#3c8527"));
        assertTrue(statusOn.getComputedStyle().backgroundImage.contains("#639d52"));

        Element switchGold = element(document, "span", "switch on");
        switchGold.setAttribute("color", "gold");
        Element statusGold = child(document, child(document, switchGold, "switch-control"), "switch-status");
        assertTrue(statusGold.getComputedStyle().backgroundImage.contains("#ffc42b"));

        Element switchDisabled = element(document, "span", "switch disabled");
        Element statusDisabled = child(document, child(document, switchDisabled, "switch-control"), "switch-status");
        assertEquals("#8c8d90", statusDisabled.getComputedStyle().backgroundColor);
        assertFalse(statusDisabled.getComputedStyle().backgroundImage.contains("#58585a"));

        Element checkbox = element(document, "span", "checkbox");
        assertEquals("20px", checkbox.getComputedStyle().width);
        assertEquals("#8c8d90", checkbox.getComputedStyle().backgroundColor);

        Element checkboxOn = element(document, "span", "checkbox on");
        assertEquals("#3c8527", checkboxOn.getComputedStyle().backgroundColor);

        Element checkbox2 = element(document, "span", "checkbox-2");
        assertEquals("24px", checkbox2.getComputedStyle().width);

        Element radioOn = element(document, "span", "radio on");
        assertEquals("#3c8527", radioOn.getComputedStyle().backgroundColor);

        Element radio2 = element(document, "span", "radio-2");
        assertEquals("18px", radio2.getComputedStyle().width);
        assertEquals("rotate(45deg)", radio2.getComputedStyle().transform);

        Element slider = element(document, "div", "slider");
        assertEquals("8px", slider.getComputedStyle().height);

        Element slider2 = element(document, "div", "slider-2");
        assertEquals("12px", slider2.getComputedStyle().height);
        assertTrue(slider2.getComputedStyle().backgroundImage.contains("repeating-linear-gradient"));
    }

    @Test
    void feedbackComponentsApply() throws Exception {
        Document document = document();

        Element tooltip = element(document, "span", "tooltip-content");
        assertEquals("#1f1f1f", tooltip.getComputedStyle().backgroundColor);
        assertEquals("0", tooltip.getComputedStyle().opacity);

        Element tooltip2 = element(document, "span", "tooltip-2");
        assertNotEquals("unset", tooltip2.getComputedStyle().display);

        Element dropdownLabel = element(document, "button", "dropdown-label");
        assertEquals("#d0d1d4", dropdownLabel.getComputedStyle().backgroundColor);

        Element dropdownOptions = element(document, "div", "dropdown-options");
        assertEquals("none", dropdownOptions.getComputedStyle().display);
        assertEquals("#58585a", dropdownOptions.getComputedStyle().backgroundColor);

        Element toast = element(document, "div", "toast");
        assertEquals("#1f1f1f", toast.getComputedStyle().backgroundColor);
        assertEquals("0", toast.getComputedStyle().opacity);

        Element toastSuccess = element(document, "div", "toast toast-success show");
        assertEquals("#6cc349", toastSuccess.getComputedStyle().color);
        assertEquals("1", toastSuccess.getComputedStyle().opacity);

        Element toast2 = element(document, "div", "toast-2 toast-2-notice show");
        assertEquals("#ffe866", toast2.getComputedStyle().backgroundColor);
        assertEquals("#1e1e1f", toast2.getComputedStyle().color);

        Element drawer = element(document, "div", "drawer drawer-left");
        assertEquals("#313233", drawer.getComputedStyle().backgroundColor);
        assertEquals("hidden", drawer.getComputedStyle().visibility);

        Element drawerOpen = element(document, "div", "drawer drawer-left open");
        assertEquals("visible", drawerOpen.getComputedStyle().visibility);

        Element iconButton = element(document, "button", "icon-button");
        assertEquals("36px", iconButton.getComputedStyle().width);
        assertEquals("#48494a", iconButton.getComputedStyle().backgroundColor);

        Element iconButton2 = element(document, "button", "icon-button-2");
        assertEquals("#d0d1d4", iconButton2.getComputedStyle().backgroundColor);

        Element spinner = element(document, "span", "spinner");
        assertEquals("32px", spinner.getComputedStyle().width);

        Element loadingMask = element(document, "div", "loading-mask");
        assertEquals("#48494a", loadingMask.getComputedStyle().backgroundColor);
    }

    @Test
    void sidebarAndScrollbarApply() throws Exception {
        Document document = document();

        Element sidebar = element(document, "div", "sidebar");
        assertEquals("238px", sidebar.getComputedStyle().width);
        assertEquals("#313233", sidebar.getComputedStyle().backgroundColor);

        Element sidebar2 = element(document, "div", "sidebar-2");
        assertEquals("240px", sidebar2.getComputedStyle().width);
        assertEquals("#48494a", sidebar2.getComputedStyle().backgroundColor);

        Element scrollbar = element(document, "div", "scrollbar");
        assertEquals("22px", scrollbar.getComputedStyle().width);

        Element scrollbar2 = element(document, "div", "scrollbar-2");
        assertEquals("18px", scrollbar2.getComputedStyle().width);

        Element tag = element(document, "span", "tag tag-primary");
        assertEquals("#6cc349", tag.getComputedStyle().backgroundColor);

        Element banner = element(document, "div", "banner banner-important");
        assertEquals("#ffe866", banner.getComputedStyle().backgroundColor);

        Element list2 = element(document, "ul", "list-group-2");
        assertEquals("#313233", list2.getComputedStyle().backgroundColor);
    }

    @Test
    void stylesheetUsesOnlyAuiCompatibleSyntax() throws Exception {
        String css = Files.readString(ORE);

        assertFalse(css.contains("color-mix("), "color-mix() is banned by roadmap 6.3");
        assertFalse(css.contains(":has("), ":has() is unsupported");
        assertFalse(css.contains("@layer"), "unprocessed @layer is banned");
        assertFalse(css.contains("@import"), "@import must be flattened");
        assertFalse(css.contains("clip-path"), "clip-path is unsupported by the AUI parser");
        assertFalse(css.contains("ore-edit"), "deprecated ore-edit references must be gone");
    }

    private static Document document() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "ore-theme");
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(Files.readString(ORE), cache, ORE.toString());
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        return document;
    }

    private static Element element(Document document, String tag, String classes) {
        Element element = document.createElement(tag);
        element.setAttribute("class", classes);
        document.body.appendChild(element);
        return element;
    }

    private static Element child(Document document, Element parent, String classes) {
        Element element = document.createElement("span");
        element.setAttribute("class", classes);
        parent.appendChild(element);
        return element;
    }

    private static com.sighs.apricityui.style.Style style(Document document, String tag, String classes) {
        return element(document, tag, classes).getComputedStyle();
    }
}
