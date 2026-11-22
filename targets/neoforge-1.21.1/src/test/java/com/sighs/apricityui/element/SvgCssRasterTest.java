package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SvgCssRasterTest {
    private static Map<String, BiFunction<Document, String, ? extends Element>> registry;
    private static BiFunction<Document, String, ? extends Element> previousSvgFactory;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void registerSvg() throws ReflectiveOperationException {
        // Headless tests do not run the loader's @ElementRegister scan.
        var field = Element.class.getDeclaredField("REGISTRY");
        field.setAccessible(true);
        registry = (Map<String, BiFunction<Document, String, ? extends Element>>) field.get(null);
        previousSvgFactory = registry.get(Svg.TAG_NAME);
        Element.register(Svg.TAG_NAME, (document, tag) -> new Svg(document));
    }

    @AfterAll
    static void restoreSvgFactory() {
        if (registry == null) return;
        if (previousSvgFactory == null) registry.remove(Svg.TAG_NAME);
        else registry.put(Svg.TAG_NAME, previousSvgFactory);
    }

    @Test
    void inheritsHtmlPaintAndResolvesCurrentColorOnEachShape() {
        String source = "test://svg-css-inherited-paint";
        HTML.putTemple(source, """
                <html><head><style>
                  .icon { --ink: #ff0000; color: var(--ink); fill: currentColor; stroke: currentColor; stroke-width: 2; }
                  .icon:hover { --ink: #ff00ff; }
                  #outline { fill: none; color: currentColor; }
                  #plain { fill: initial; stroke: initial; }
                  #overridden { fill: #ffff00; }
                </style></head><body>
                  <div class="icon">
                    <svg id="image" width="40" height="10" viewBox="0 0 40 10">
                      <rect x="2" y="2" width="6" height="6" />
                      <g id="group" color="#0000ff">
                        <rect id="outline" x="12" y="2" width="6" height="6" />
                      </g>
                      <rect id="plain" x="22" y="2" width="6" height="6" fill="#ffffff" />
                      <rect id="overridden" x="32" y="2" width="6" height="6" fill="#ffffff" />
                    </svg>
                  </div>
                </body></html>
                """);
        Document document = new Document(source, false);
        try {
            document.refresh();
            Svg svg = (Svg) document.getElementById("image");
            BufferedImage image = rasterize(svg);
            assertEquals(0xffff0000, pixel(image, 5, 5));
            assertEquals(0xff0000ff, pixel(image, 12, 5));
            assertEquals(0, pixel(image, 15, 5));
            assertEquals(0xff000000, pixel(image, 25, 5));
            assertEquals(0, pixel(image, 21.5, 5));
            assertEquals(0xffffff00, pixel(image, 35, 5));

            Element group = document.getElementById("group");
            group.setAttribute("color", "#00ff00");
            document.flushPendingStyleUpdates();
            assertEquals(0xff00ff00, pixel(rasterize(svg), 12, 5));
            group.removeAttribute("color");
            document.flushPendingStyleUpdates();
            assertEquals(0xffff0000, pixel(rasterize(svg), 12, 5));

            document.querySelector(".icon").setHover(true);
            document.flushPendingStyleUpdates();
            assertEquals(0xffff00ff, pixel(rasterize(svg), 5, 5));
            assertEquals(0xffff00ff, pixel(rasterize(svg), 12, 5));
        } finally {
            document.remove();
        }
    }

    @Test
    void descendantHoverRepaintsTheCachedSurface() {
        String source = "test://svg-css-hover-paint";
        HTML.putTemple(source, """
                <html><head><style>
                  #icon { fill: #ff0000; }
                  #icon:hover { fill: #00ff00; }
                </style></head><body>
                  <svg id="image" width="40" height="10" viewBox="0 0 40 10">
                    <rect id="icon" x="2" y="2" width="6" height="6" />
                  </svg>
                </body></html>
                """);
        Document document = new Document(source, false);
        try {
            document.refresh();
            Svg svg = (Svg) document.getElementById("image");
            assertEquals(0xffff0000, pixel(rasterize(svg), 5, 5));

            Element icon = document.getElementById("icon");
            icon.setHover(true);
            document.flushPendingStyleUpdates();
            assertEquals(0xff00ff00, pixel(rasterize(svg), 5, 5));

            icon.setHover(false);
            document.flushPendingStyleUpdates();
            assertEquals(0xffff0000, pixel(rasterize(svg), 5, 5));
        } finally {
            document.remove();
        }
    }

    private static BufferedImage rasterize(Svg svg) {
        svg.renderVectorSurface();
        return svg.getSurface();
    }

    private static int pixel(BufferedImage image, double x, double y) {
        return image.getRGB((int) (x * image.getWidth() / 40), (int) (y * image.getHeight() / 10));
    }
}
