package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThemeDetailTest {
    private static final String[] THEMES = {"t10-neobrutalist", "t11-hand-drawn-doodle"};

    private Document themedDocument(String theme) throws Exception {
        Path css = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/"
                + theme + "/" + theme + ".css");
        Map<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(Files.readString(css), cache, css.toString(), new Size(800, 600));
        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        document.body.setAttribute("class", theme + "-theme");
        return document;
    }

    private Element append(Document document, Element parent, String classes) {
        Element element = new Element(document, "div");
        element.setAttribute("class", classes);
        parent.appendChild(element);
        document.flushPendingStyleUpdates();
        return element;
    }

    private Style glyph(Element element) {
        assertEquals(1, element.getRenderChildren().size(), "Empty markup must generate its icon");
        return element.getRenderChildren().get(0).getComputedStyle();
    }

    @Test
    void generatedIconsStayHollowAndVisibleWhenDisabledOrHovered() throws Exception {
        for (String theme : THEMES) {
            Document document = themedDocument(theme);
            for (String classes : new String[]{"split-button-arrow", "dropdown-label", "dropdown-option selected",
                    "accordion-header", "checkbox on", "checkbox-2 on"}) {
                Element control = append(document, document.body, classes);
                assertNotEquals("none", glyph(control).transform, theme + " " + classes);
                for (String hook : new String[]{"disabled", "aria-disabled", "class"}) {
                    control.setAttribute(hook, hook.equals("class") ? classes + " disabled" : "true");
                    document.flushPendingStyleUpdates();
                    assertEquals("transparent", glyph(control).backgroundColor, theme + " " + classes);
                    String border = glyph(control).borderRight;
                    control.setHover(true);
                    document.flushPendingStyleUpdates();
                    assertEquals("transparent", glyph(control).backgroundColor);
                    assertEquals(border, glyph(control).borderRight);
                    control.setHover(false);
                    control.removeAttribute(hook);
                    control.setAttribute("class", classes);
                    document.flushPendingStyleUpdates();
                }
            }
            Element loader = append(document, document.body, "button button-secondary");
            loader.setAttribute("data-state", "loading");
            document.flushPendingStyleUpdates();
            assertFalse(glyph(loader).borderRight.endsWith(loader.getComputedStyle().backgroundColor), theme);
        }
    }

    @Test
    void settingsSlidersFillTheirControlColumn() throws Exception {
        for (String theme : THEMES) {
            Document document = themedDocument(theme);
            document.body.setAttribute("style", "width: 800px; height: 600px;");
            for (String sliderClass : new String[]{"slider", "slider-2"}) {
                Element row = append(document, document.body, "settings-row active");
                row.setAttribute("style", "width: 400px;");
                Element label = append(document, row, "settings-label");
                label.innerText = "Brightness";
                Element help = append(document, label, "settings-help");
                help.innerText = "0-100";
                Element control = append(document, row, "settings-control");
                Element slider = append(document, control, sliderClass);
                slider.setAttribute("style", "--value: 60%;");
                for (int width : new int[]{200, 120}) {
                    control.setAttribute("style", "width: " + width + "px;");
                    document.flushPendingStyleUpdates();
                    double trackWidth = Box.of(slider).size().width();
                    assertTrue(trackWidth >= width - 32 && trackWidth <= width,
                            theme + " " + sliderClass + " track collapsed: " + trackWidth);
                }
                assertEquals("block", help.getComputedStyle().display);
                assertNotEquals("none", row.getComputedStyle().boxShadow);
            }
        }
    }

    @Test
    void dropdownArrowAndOptionsFollowEveryExpansionHook() throws Exception {
        for (String theme : THEMES) {
            Document document = themedDocument(theme);
            Element dropdown = append(document, document.body, "dropdown");
            Element label = append(document, dropdown, "dropdown-label");
            Element options = append(document, dropdown, "dropdown-options");
            assertEquals("200px", dropdown.getComputedStyle().minWidth);
            assertEquals("100%", label.getComputedStyle().width);
            assertEquals("240px", options.getComputedStyle().maxHeight);
            assertEquals("auto", options.getComputedStyle().overflow);
            for (String hook : new String[]{"open", "show", "data-state", "aria-expanded", "focus"}) {
                assertEquals("none", options.getComputedStyle().display, theme + " closed");
                assertEquals("rotate(45deg)", glyph(label).transform);
                if (hook.equals("focus")) document.setFocusedElement(label);
                else if (hook.equals("open") || hook.equals("show")) dropdown.setAttribute("class", "dropdown " + hook);
                else dropdown.setAttribute(hook, hook.equals("data-state") ? "open" : "true");
                document.flushPendingStyleUpdates();
                assertEquals("block", options.getComputedStyle().display, theme + " " + hook);
                assertEquals("rotate(225deg)", glyph(label).transform, theme + " " + hook);
                document.clearFocus();
                dropdown.setAttribute("class", "dropdown");
                dropdown.removeAttribute("data-state");
                dropdown.removeAttribute("aria-expanded");
                document.flushPendingStyleUpdates();
            }
        }
    }
}
