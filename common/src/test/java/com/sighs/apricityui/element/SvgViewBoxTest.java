package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgViewBoxTest {
    @Test
    void defaultNonSquareViewBoxUsesCenteredUniformMeetScale() {
        Svg.ViewBoxTransform transform = Svg.resolveViewBoxTransform(
                24, 24, 0, 0, 22, 24, null);

        assertEquals(1.0d, transform.scaleX(), 0.0001d);
        assertEquals(1.0d, transform.scaleY(), 0.0001d);
        assertEquals(1.0d, transform.offsetX(), 0.0001d);
        assertEquals(0.0d, transform.offsetY(), 0.0001d);
    }

    @Test
    void preserveAspectRatioNoneRetainsNonUniformScaling() {
        Svg.ViewBoxTransform transform = Svg.resolveViewBoxTransform(
                24, 24, 0, 0, 22, 24, "none");

        assertEquals(24.0d / 22.0d, transform.scaleX(), 0.0001d);
        assertEquals(1.0d, transform.scaleY(), 0.0001d);
        assertEquals(0.0d, transform.offsetX(), 0.0001d);
        assertEquals(0.0d, transform.offsetY(), 0.0001d);
    }

    @Test
    void crispVectorSurfacesUseNearestSamplingUnlessExplicitlyOverridden() {
        assertEquals("crispEdges", Svg.resolveShapeRendering("auto", "crispEdges"));
        assertEquals("geometricPrecision", Svg.resolveShapeRendering("geometricPrecision", "crispEdges"));
        assertEquals(1.0d, Svg.rasterScaleFor("crispEdges", 1.0d));
        assertEquals(4.0d, Svg.rasterScaleFor("crispEdges", 4.0d));
        assertEquals(4.0d, Svg.rasterScaleFor("auto", 1.0d));
        assertFalse(Svg.useLinearSampling("auto", "crispEdges"));
        assertFalse(Svg.useLinearSampling("unset", "optimizeSpeed"));
        assertTrue(Svg.useLinearSampling("linear", "crispEdges"));
        assertTrue(Svg.useLinearSampling("auto", "auto"));
    }

    @Test
    void crispFillSnapsTransformedEdgesToDevicePixels() {
        var snapped = Svg.snapFilledShape(
                new Rectangle2D.Double(11, 4, 2, 7),
                AffineTransform.getScaleInstance(2.0d / 3.0d, 2.0d / 3.0d));

        assertEquals(7.0d, snapped.getBounds2D().getX());
        assertEquals(2.0d, snapped.getBounds2D().getWidth());
    }

    @Test
    void embeddedDataUriImageIsPaintedInsideSvgViewport() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xffff0000);
        source.setRGB(1, 0, 0xff00ff00);
        source.setRGB(0, 1, 0xff0000ff);
        source.setRGB(1, 1, 0xffffffff);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);

        Element image = new Element(null, "IMAGE");
        image.setAttribute("href", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
        image.setAttribute("x", "1");
        image.setAttribute("y", "1");
        image.setAttribute("width", "4");
        image.setAttribute("height", "4");

        BufferedImage target = new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        assertTrue(Svg.drawEmbeddedImage(graphics, image, 1.0d));
        graphics.dispose();

        assertEquals(0, target.getRGB(0, 0));
        assertEquals(0xffff0000, target.getRGB(1, 1));
        assertEquals(0xffffffff, target.getRGB(4, 4));
        assertEquals(0, target.getRGB(5, 5));
    }
}
