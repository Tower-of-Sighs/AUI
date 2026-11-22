package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** issue#147: CSS fill/stroke/stroke-width must participate in SVG paint resolution. */
class SvgCssPaintTest {
    private static final String SOURCE = """
            <html><head><style>
              .ring  { fill:none; stroke:#a4ff80; stroke-width:4; }
              .group { fill:#00ff00; stroke:#0000ff; fill-rule:evenodd; }
            </style></head><body>
              <svg viewBox="0 0 160 160" width="160" height="160">
                <circle id="ring" class="ring" cx="80" cy="80" r="64"></circle>
                <g class="group"><circle id="inherited" cx="80" cy="80" r="10"></circle></g>
                <circle id="default" cx="40" cy="40" r="5"></circle>
              </svg>
            </body></html>
            """;

    @Test
    void cssPaintPropertiesDriveSvgPaint() {
        String path = "test://svg-css-paint";
        HTML.putTemple(path, SOURCE);
        Document document = new Document(path, false);
        try {
            document.refresh();

            Element ring = document.getElementById("ring");
            assertEquals("none", ring.getComputedStyle().fill);
            assertEquals("#a4ff80", ring.getComputedStyle().stroke.toLowerCase(Locale.ROOT));
            assertEquals("4", ring.getComputedStyle().strokeWidth);

            Element inherited = document.getElementById("inherited");
            assertEquals("#00ff00", inherited.getComputedStyle().fill.toLowerCase(Locale.ROOT));
            assertEquals("#0000ff", inherited.getComputedStyle().stroke.toLowerCase(Locale.ROOT));
            assertEquals("evenodd", inherited.getComputedStyle().fillRule);

            Element plain = document.getElementById("default");
            assertEquals("black", plain.getComputedStyle().fill);
            assertEquals("none", plain.getComputedStyle().stroke);
        } finally {
            document.remove();
        }
    }
}
