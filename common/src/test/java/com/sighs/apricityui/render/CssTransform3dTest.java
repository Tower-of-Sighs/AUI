package com.sighs.apricityui.render;

import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssTransform3dTest {
    @Test
    void combinedFaceTransformWrapsTheWholeListAroundOneOrigin() throws Exception {
        List<Transform> functions = Transform.parse(
                "translateX(-20px) rotateY(-90deg) translate(-12px, -36px)", 24, 72);
        Class<?> matrixType = Class.forName("org.joml.Matrix4f");
        Class<?> matrixInterface = Class.forName("org.joml.Matrix4fc");
        Object actual = matrixType.getConstructor().newInstance();
        Method appendCssTransform = Base.class.getDeclaredMethod(
                "appendCssTransform", matrixType, List.class,
                float.class, float.class, float.class, boolean.class);
        appendCssTransform.setAccessible(true);
        appendCssTransform.invoke(null, actual, functions, 100.0F, 50.0F, 0.0F, true);

        Method translate = matrixType.getMethod("translate", float.class, float.class, float.class);
        Method rotateY = matrixType.getMethod("rotateY", float.class);
        Method mul = matrixType.getMethod("mul", matrixInterface);
        Object local = matrixType.getConstructor().newInstance();
        translate.invoke(local, -20.0F, 0.0F, 0.0F);
        rotateY.invoke(local, (float) Math.toRadians(-90));
        translate.invoke(local, -12.0F, -36.0F, 0.0F);
        Object expected = matrixType.getConstructor().newInstance();
        translate.invoke(expected, 100.0F, 50.0F, 0.0F);
        mul.invoke(expected, local);
        translate.invoke(expected, -100.0F, -50.0F, 0.0F);

        float[] actualValues = new float[16];
        float[] expectedValues = new float[16];
        Method get = matrixType.getMethod("get", float[].class);
        get.invoke(actual, (Object) actualValues);
        get.invoke(expected, (Object) expectedValues);
        for (int index = 0; index < actualValues.length; index++) {
            assertEquals(expectedValues[index], actualValues[index], 0.0001F,
                    "matrix component " + index);
        }
    }

    @Test
    void homogeneousProjectionUsesTheCssPerspectiveDivide() throws Exception {
        Class<?> matrixType = Class.forName("org.joml.Matrix4f");
        Class<?> matrixInterface = Class.forName("org.joml.Matrix4fc");
        Class<?> vectorType = Class.forName("org.joml.Vector3f");
        Object perspective = matrixType.getConstructor().newInstance();
        matrixType.getMethod("m23", float.class).invoke(perspective, -1.0F / 200.0F);
        Object matrix = matrixType.getConstructor().newInstance();
        Method translate = matrixType.getMethod("translate", float.class, float.class, float.class);
        translate.invoke(matrix, 50.0F, 50.0F, 0.0F);
        matrixType.getMethod("mul", matrixInterface).invoke(matrix, perspective);
        translate.invoke(matrix, -50.0F, -50.0F, 0.0F);
        translate.invoke(matrix, 0.0F, 0.0F, 50.0F);

        Method hasProjection = Base.class.getMethod("hasProjectiveComponent", matrixType);
        assertTrue((boolean) hasProjection.invoke(null, matrix));
        Object vector = vectorType.getConstructor().newInstance();
        Method project = Base.class.getMethod(
                "projectPosition", matrixType,
                float.class, float.class, float.class, vectorType
        );
        Object projected = project.invoke(null, matrix, 60.0F, 50.0F, 0.0F, vector);
        assertEquals(63.3333F, (float) vectorType.getMethod("x").invoke(projected), 0.0002F);
        assertEquals(50.0F, (float) vectorType.getMethod("y").invoke(projected), 0.0002F);
    }

    @Test
    void backfaceDetectionUsesProjectedWinding() throws Exception {
        Class<?> matrixType = Class.forName("org.joml.Matrix4f");
        Method backFacing = Base.class.getDeclaredMethod(
                "isBackFacing", matrixType,
                float.class, float.class, float.class, float.class
        );
        backFacing.setAccessible(true);
        Object identity = matrixType.getConstructor().newInstance();
        assertFalse((boolean) backFacing.invoke(null, identity, 0.0F, 0.0F, 20.0F, 10.0F));

        Object reversed = matrixType.getConstructor().newInstance();
        Method translate = matrixType.getMethod("translate", float.class, float.class, float.class);
        translate.invoke(reversed, 10.0F, 5.0F, 0.0F);
        matrixType.getMethod("rotateY", float.class).invoke(reversed, (float) Math.PI);
        translate.invoke(reversed, -10.0F, -5.0F, 0.0F);
        assertTrue((boolean) backFacing.invoke(null, reversed, 0.0F, 0.0F, 20.0F, 10.0F));
    }

    @Test
    void scale3dPreservesItsDepthFactor() {
        Transform.Scale scale = (Transform.Scale) Transform.parse("scale3d(1.25, 1.5, 1.75)").get(0);
        assertEquals(1.25, scale.x(), 0.0001);
        assertEquals(1.5, scale.y(), 0.0001);
        assertEquals(1.75, scale.z(), 0.0001);
    }

    @Test
    void perspectiveTransformFunctionParsesItsLength() {
        List<Transform> functions = Transform.parse("perspective(400px) rotateY(20deg)");

        assertTrue(functions.get(0) instanceof Transform.Perspective);
        assertEquals(400.0, ((Transform.Perspective) functions.get(0)).distance(), 0.0001);
        assertTrue(functions.get(1) instanceof Transform.Rotate);
    }

    @Test
    void threeDimensionalPropertiesResolveTheirInitialValues() {
        Style style = new Style();
        style.transformStyle = "initial";
        style.perspective = "initial";
        style.perspectiveOrigin = "initial";
        style.backfaceVisibility = "initial";

        style.finalizeComputedValues(null);

        assertEquals("flat", style.transformStyle);
        assertEquals("none", style.perspective);
        assertEquals("50% 50%", style.perspectiveOrigin);
        assertEquals("visible", style.backfaceVisibility);
    }

    @Test
    void threeDimensionalStyleChangesInvalidateRequiredRenderState() {
        assertDirtyMask("transform-style", "flat", "preserve-3d");
        assertDirtyMask("perspective", "none", "400px");
        assertDirtyMask("perspective-origin", "50% 50%", "25% 25%");
        assertDirtyMask("backface-visibility", "visible", "hidden");
    }

    private static void assertDirtyMask(String property, String oldValue, String newValue) {
        int expectedMask = switch (property) {
            case "transform-style", "perspective" ->
                    Drawer.REPAINT | Drawer.REORDER | Drawer.COMMIT_LAYOUT | Drawer.HITTEST;
            case "perspective-origin" -> Drawer.REPAINT | Drawer.COMMIT_LAYOUT | Drawer.HITTEST;
            case "backface-visibility" -> Drawer.REPAINT | Drawer.HITTEST;
            default -> throw new IllegalArgumentException("unexpected property: " + property);
        };
        boolean transformCacheMustClear = !"backface-visibility".equals(property);
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        RenderElement renderer = element.getRenderer();
        renderer.transform.set(List.of());
        Style origin = new Style();
        Style current = origin.clone();
        origin.update(property, oldValue);
        current.update(property, newValue);
        element.clearDirtyFlags();

        RenderElement.observeStyle(element, origin, current);

        if (transformCacheMustClear) {
            assertNull(renderer.transform.get(), property);
        } else {
            assertEquals(List.of(), renderer.transform.get(), property);
        }
        assertTrue(element.hasDirtyFlag(expectedMask & Drawer.REPAINT), property + " repaint");
        assertTrue(element.hasDirtyFlag(expectedMask & Drawer.HITTEST), property + " hit test");
        if ((expectedMask & Drawer.REORDER) != 0) {
            assertTrue(element.hasDirtyFlag(Drawer.REORDER), property + " reorder");
        }
        if ((expectedMask & Drawer.COMMIT_LAYOUT) != 0) {
            assertTrue(element.hasDirtyFlag(Drawer.COMMIT_LAYOUT), property + " commit layout");
        }
    }
}
