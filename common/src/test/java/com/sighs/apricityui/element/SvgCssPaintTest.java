package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void svgPaintDependencyChangesWhenAncestorStyleChanges() {
        String path = "test://svg-paint-dependency";
        HTML.putTemple(path, "<html><body><svg id=\"svg\"><g><path d=\"M0 0 L1 1\"></path></g></svg></body></html>");
        Document document = new Document(path, false);
        try {
            document.refresh();
            Element svg = document.getElementById("svg");
            long before = svg.getLayout().svgPaintDependency();

            document.body.setAttribute("style", "color: #ff0000;");
            document.flushPendingStyleUpdates();

            assertTrue(svg.getLayout().svgPaintDependency() != before,
                    "ancestor style changes must invalidate cached SVG paint");
        } finally {
            document.remove();
        }
    }

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

            Svg.SvgPaint ringPaint = Svg.SvgPaint.fromElement(ring, Svg.SvgPaint.initial(0xFF000000));
            assertEquals("none", ringPaint.fill());
            assertEquals("#a4ff80", ringPaint.stroke().toLowerCase(Locale.ROOT));
            assertEquals(4.0d, ringPaint.strokeWidth(), 0.001d);

            Element group = document.querySelector(".group");
            Svg.SvgPaint groupPaint = Svg.SvgPaint.fromElement(group, Svg.SvgPaint.initial(0xFF000000));
            Element inherited = document.getElementById("inherited");
            Svg.SvgPaint inheritedPaint = Svg.SvgPaint.fromElement(inherited, groupPaint);
            assertEquals("#00ff00", inheritedPaint.fill().toLowerCase(Locale.ROOT));
            assertEquals("#0000ff", inheritedPaint.stroke().toLowerCase(Locale.ROOT));
            assertEquals("evenodd", inheritedPaint.fillRule());

            Element plain = document.getElementById("default");
            Svg.SvgPaint defaultPaint = Svg.SvgPaint.fromElement(plain, Svg.SvgPaint.initial(0xFF000000));
            assertEquals("black", defaultPaint.fill());
            assertEquals("none", defaultPaint.stroke());
        } finally {
            document.remove();
        }
    }
}
