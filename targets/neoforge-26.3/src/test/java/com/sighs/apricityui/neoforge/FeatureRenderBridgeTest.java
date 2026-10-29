package com.sighs.apricityui.neoforge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Minecraft is available at test runtime only; exercise its interfaces through reflection. */
class FeatureRenderBridgeTest {
    @Test
    void intersectsNegativeAndOversizedScissors() {
        assertEquals(new FeatureRenderBridge.Rect(0, 10, 20, 70),
                FeatureRenderBridge.intersect(-10, 10, 30, 200, 100, 80));
        assertEquals(new FeatureRenderBridge.Rect(90, 70, 10, 10),
                FeatureRenderBridge.intersect(90, 70, 100, 100, 100, 80));
    }

    @Test
    void emptyAndOverflowScissorsNeverReachGpu() {
        assertTrue(FeatureRenderBridge.intersect(-100, 0, 20, 20, 100, 80).empty());
        assertTrue(FeatureRenderBridge.intersect(101, 0, 20, 20, 100, 80).empty());
        assertTrue(FeatureRenderBridge.intersect(0, 0, -1, 20, 100, 80).empty());
        assertTrue(FeatureRenderBridge.intersect(0, 0, 20, 0, 100, 80).empty());
        assertTrue(FeatureRenderBridge.intersect(0, 0, 20, 20, 0, 80).empty());
        assertEquals(new FeatureRenderBridge.Rect(0, 0, 100, 80),
                FeatureRenderBridge.intersect(-1, -1, Integer.MAX_VALUE, Integer.MAX_VALUE, 100, 80));
        assertTrue(FeatureRenderBridge.intersect(Integer.MAX_VALUE - 1, 0, 100, 20, 100, 80).empty());
        assertTrue(FeatureRenderBridge.intersect(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 20, 100, 80).empty());
    }

    @Test
    void wrapperCannotEscapeElementClipAndRestoresAfterEmptyClip() throws Exception {
        List<String> calls = new ArrayList<>();
        Object pass = clippedPass(calls, new FeatureRenderBridge.Rect(10, 20, 30, 40));
        assertEquals(List.of("scissor:10,20,30,40"), calls);
        call(pass, "enableScissor", -10, 10, 30, 30);
        assertEquals("scissor:10,20,10,20", calls.getLast());
        call(pass, "draw", 3, 1, 0, 0);
        assertEquals("draw", calls.getLast());
        int beforeEmpty = calls.size();
        call(pass, "enableScissor", 200, 200, 10, 10);
        // Every draw overload, including indirect/multi-draw, must suppress empty clips.
        for (Method method : pass.getClass().getMethods()) {
            if (!method.getName().startsWith("draw") && !method.getName().startsWith("multiDraw")) continue;
            Object[] args = new Object[method.getParameterCount()];
            for (int i = 0; i < args.length; i++) if (method.getParameterTypes()[i] == int.class) args[i] = 1;
            method.invoke(pass, args);
        }
        assertEquals(beforeEmpty, calls.size());
        call(pass, "disableScissor");
        assertEquals("scissor:10,20,30,40", calls.getLast());
        call(pass, "drawIndexed", 3, 1, 0, 0, 0);
        assertEquals("drawIndexed", calls.getLast());
        call(pass, "close");
        assertFalse(calls.contains("close"), "The wrapper must not close a pass it does not own");
    }

    @Test
    void wrapperForwardsPipelineAndUniformsWithoutReconstructingMaterial() throws Exception {
        List<String> calls = new ArrayList<>();
        Object pass = clippedPass(calls, new FeatureRenderBridge.Rect(0, 0, 10, 10));
        call(pass, "setPipeline", (Object) null);
        call(pass, "setUniform", "texture", null, null);
        call(pass, "pushConstants", (Object) null);
        call(pass, "setViewport", 0, 0, 10, 10);
        call(pass, "setVertexBuffer", 0, null);
        call(pass, "setIndexBuffer", null, null);
        assertEquals(List.of("scissor:0,0,10,10", "setPipeline", "setUniform", "pushConstants",
                "setViewport", "setVertexBuffer", "setIndexBuffer"), calls);
    }

    @Test
    void preparesAndRendersActualGlyphFromSelectedFontProvider() throws Exception {
        AtomicInteger glyphRequests = new AtomicInteger();
        AtomicInteger rendered = new AtomicInteger();
        Object glyph = proxy("net.minecraft.client.gui.font.TextRenderable$Styled", (p, method, args) -> {
            if (method.getName().equals("renderType")) {
                assertEquals("NORMAL", args[0].toString());
                return null;
            }
            if (method.getName().equals("render")) {
                assertEquals(15728880, args[2]);
                assertEquals(false, args[3]);
                rendered.incrementAndGet();
                return null;
            }
            return 1f;
        });
        Object info = call(Class.forName("com.mojang.blaze3d.font.GlyphInfo"), "simple", 7f);
        Object baked = proxy("net.minecraft.client.gui.font.glyphs.BakedGlyph", (p, method, args) -> {
            if (method.getName().equals("info")) return info;
            assertEquals("createGlyph", method.getName());
            assertEquals(12f, args[0]);
            assertEquals(9f, args[1]);
            assertEquals(0xFF123456, args[2]);
            assertEquals(0xFF040D15, args[3], "Selected Font must also prepare its shadow");
            return glyph;
        });
        Object source = proxy("net.minecraft.client.gui.GlyphSource", (p, method, args) -> {
            glyphRequests.incrementAndGet();
            assertEquals((int) 'X', args[0]);
            return baked;
        });
        Object provider = proxy("net.minecraft.client.gui.Font$Provider", (p, method, args) -> {
            assertEquals("glyphs", method.getName());
            return source;
        });
        Class<?> fontType = Class.forName("net.minecraft.client.gui.Font");
        Object font = fontType.getConstructor(Class.forName("net.minecraft.client.gui.Font$Provider")).newInstance(provider);
        Object style = Class.forName("net.minecraft.network.chat.Style").getField("EMPTY").get(null);
        Object sequence = call(Class.forName("net.minecraft.util.FormattedCharSequence"), "codepoint", (int) 'X', style);
        List<Object> geometry = new ArrayList<>();
        Object collector = proxy("net.minecraft.client.renderer.SubmitNodeCollector", (p, method, args) -> {
            assertEquals("submitCustomGeometry", method.getName(), "Must not defer to the dispatcher's default Font");
            geometry.add(args[2]);
            return null;
        });
        Object pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
        call(FeatureRenderBridge.class, "submitText", collector, pose, font, sequence, 12f, 9f, 0xFF123456, true);
        assertEquals(1, glyphRequests.get());
        assertEquals(1, geometry.size());
        Class<?> renderer = Class.forName("net.minecraft.client.renderer.SubmitNodeCollector$CustomGeometryRenderer");
        renderer.getMethods()[0].invoke(geometry.getFirst(), call(pose, "last"), null);
        assertEquals(1, rendered.get());
    }

    @Test
    void discardDrainsTextOnlyStorage() throws Exception {
        Object storage = Class.forName("net.minecraft.client.renderer.SubmitNodeStorage").getConstructor().newInstance();
        Object pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
        Object sequence = Class.forName("net.minecraft.util.FormattedCharSequence").getField("EMPTY").get(null);
        // SEE_THROUGH exercises a real translucent phase without SharedConstants' FML bootstrap.
        Object mode = java.util.Arrays.stream(Class.forName("net.minecraft.client.gui.Font$DisplayMode").getEnumConstants())
                .filter(value -> value.toString().equals("SEE_THROUGH")).findFirst().orElseThrow();
        call(storage, "submitText", pose, 0f, 0f, sequence, false, mode, 15728880, -1, 0, 0);
        Method discard = FeatureRenderBridge.class.getDeclaredMethod("discard", storage.getClass());
        discard.invoke(null, storage);
        AtomicInteger remaining = new AtomicInteger();
        java.util.function.Consumer<Object> drain = phase -> {
            try {
                Object output = proxy("net.minecraft.client.renderer.feature.phase.FeatureRenderPhase$Output", (p, m, args) -> {
                    remaining.incrementAndGet();
                    return null;
                });
                call(phase, "sortInto", output);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };
        call(storage, "drainPhases", drain);
        assertEquals(0, remaining.get());
    }

    private static Object clippedPass(List<String> calls, FeatureRenderBridge.Rect bounds) throws Exception {
        Object delegate = proxy("com.mojang.renderpearl.api.commands.RenderPass", (p, method, args) -> {
            if (method.getName().equals("enableScissor")) {
                calls.add("scissor:" + args[0] + "," + args[1] + "," + args[2] + "," + args[3]);
            } else calls.add(method.getName());
            return null;
        });
        var constructor = FeatureRenderBridge.ClippedPass.class.getDeclaredConstructors()[0];
        return constructor.newInstance(delegate, bounds);
    }

    private static Object proxy(String type, InvocationHandler handler) throws Exception {
        return Proxy.newProxyInstance(FeatureRenderBridgeTest.class.getClassLoader(), new Class<?>[]{Class.forName(type)}, handler);
    }

    private static Object call(Object receiver, String name, Object... args) throws Exception {
        Class<?> type = receiver instanceof Class<?> clazz ? clazz : receiver.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> parameter = parameters[i];
                if (parameter == int.class) parameter = Integer.class;
                if (parameter == boolean.class) parameter = Boolean.class;
                if (parameter == float.class) parameter = Float.class;
                if (args[i] != null && !parameter.isInstance(args[i])) matches = false;
            }
            if (matches) return method.invoke(receiver instanceof Class<?> ? null : receiver, args);
        }
        throw new NoSuchMethodException(type + "." + name);
    }
}
