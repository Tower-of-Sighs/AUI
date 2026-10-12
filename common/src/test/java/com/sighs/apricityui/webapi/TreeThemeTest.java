package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeThemeTest {
    @Test
    void emptyTreeToggleDrawsChevronAndTracksExpansion() throws Exception {
        for (String theme : new String[]{"t10-neobrutalist", "t11-hand-drawn-doodle"}) {
            Path css = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/"
                    + theme + "/" + theme + ".css");
            Map<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
            CSS.readCSS(Files.readString(css), cache, css.toString(), new Size(800, 600));
            Document document = TestDocumentFactory.createDocument();
            document.CSSCache.putAll(cache);
            document.rebuildSelectorIndex();
            document.body.setAttribute("class", theme + "-theme");
            Element item = new Element(document, "div");
            item.setAttribute("class", "tree-item");
            Element toggle = new Element(document, "span");
            toggle.setAttribute("class", "tree-toggle");
            item.appendChild(toggle);
            document.body.appendChild(item);
            document.flushPendingStyleUpdates();
            assertEquals("rotate(-45deg)", toggle.getComputedStyle().transform, theme);
            assertEquals("2px solid " + toggle.getComputedStyle().color, toggle.getComputedStyle().borderRight, theme);
            assertEquals(toggle.getComputedStyle().borderRight, toggle.getComputedStyle().borderBottom, theme);
            assertTrue(toggle.getComputedStyle().borderTop.startsWith("0px "), theme);
            assertTrue(toggle.getComputedStyle().borderLeft.startsWith("0px "), theme);

            for (String hook : new String[]{"class", "data-state", "aria-expanded"}) {
                item.setAttribute(hook, hook.equals("class") ? "tree-item open" : hook.equals("data-state") ? "open" : "true");
                document.flushPendingStyleUpdates();
                assertEquals("rotate(45deg)", toggle.getComputedStyle().transform, theme + " " + hook);
                item.setAttribute(hook, hook.equals("class") ? "tree-item" : "");
                document.flushPendingStyleUpdates();
                assertEquals("rotate(-45deg)", toggle.getComputedStyle().transform, theme + " closed");
            }
        }
    }
}
