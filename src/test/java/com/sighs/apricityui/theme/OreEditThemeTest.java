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

class OreEditThemeTest {
    private static final Path ORE = Path.of("src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css");
    private static final Path EDIT = Path.of("src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore-edit.css");

    @Test
    void editableThemePreservesStableThemeComputedStyles() throws Exception {
        for (Sample sample : Sample.values()) {
            String stable = computedStyle(ORE, sample);
            String editable = computedStyle(EDIT, sample);
            assertEquals(stable, editable, sample.name());
        }
    }

    private static String computedStyle(Path stylesheet, Sample sample) throws Exception {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "ore-theme ore-edit-theme");
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(Files.readString(stylesheet), cache, stylesheet.toString());
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element element = document.createElement(sample.tag);
        element.setAttribute("class", sample.classes);
        document.body.appendChild(element);
        return element.getComputedStyle().toString();
    }

    private enum Sample {
        PRIMARY_BUTTON("button", "button button-primary"),
        SECONDARY_BUTTON("button", "button button-secondary"),
        DANGER_BUTTON("button", "button button-danger"),
        PANEL("div", "panel"),
        FORM_INPUT("input", "form-input"),
        FORM_SELECT("select", "form-select"),
        FORM_TEXTAREA("textarea", "form-textarea"),
        ALERT("div", "alert alert-warning"),
        PROGRESS("div", "progress"),
        CODE("pre", "ore-code"),
        INVENTORY_SLOT("div", "inventory-slot"),
        SHOWCASE_HERO("section", "showcase-hero");

        private final String tag;
        private final String classes;

        Sample(String tag, String classes) {
            this.tag = tag;
            this.classes = classes;
        }
    }
}
