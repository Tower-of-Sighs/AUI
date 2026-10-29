package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** MC is supplied to this target's test runtime, not its test compile classpath. */
class PipelineAdaptationTest {
    private static final String PIPELINE = "com.mojang.renderpearl.api.pipeline.";

    private static Object field(String type, String name) throws Exception {
        return Class.forName(type).getField(name).get(null);
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

    private Object material() throws Exception {
        Class<?> pipeline = Class.forName(PIPELINE + "RenderPipeline");
        Object snippets = java.lang.reflect.Array.newInstance(Class.forName(PIPELINE + "RenderPipeline$Snippet"), 0);
        Object builder = call(pipeline, "builder", snippets);
        call(builder, "withLocation", "apricityui/test_material");
        call(builder, "withVertexShader", "core/position_tex_color");
        call(builder, "withFragmentShader", "core/position_tex_color");
        call(builder, "withShaderDefine", "TEST_FLAG");
        call(builder, "withShaderDefine", "TEST_VALUE", 3);
        call(builder, "withBindGroupLayout", field("net.minecraft.client.renderer.BindGroupLayouts", "PROJECTION"));
        call(builder, "withVertexBinding", 0, field("com.mojang.blaze3d.vertex.DefaultVertexFormat", "POSITION_TEX_COLOR"));
        call(builder, "withPrimitiveTopology", field(PIPELINE + "PrimitiveTopology", "QUADS"));
        call(builder, "withPolygonMode", field(PIPELINE + "PolygonMode", "WIREFRAME"));
        call(builder, "withCull", false);
        call(builder, "withPushConstantSize", 16);
        Object color = Class.forName(PIPELINE + "ColorTargetState").getConstructor(Optional.class,
                Class.forName("com.mojang.renderpearl.api.GpuFormat"), int.class)
                .newInstance(Optional.of(field(PIPELINE + "BlendFunction", "TRANSLUCENT")),
                        field("com.mojang.renderpearl.api.GpuFormat", "RGBA8_UNORM"), 7);
        call(builder, "withColorTargetState", color);
        call(builder, "withUnusedColorTargetState", 1);
        Object depth = Class.forName(PIPELINE + "DepthStencilState").getConstructor(
                Class.forName(PIPELINE + "CompareOp"), boolean.class, float.class, float.class)
                .newInstance(field(PIPELINE + "CompareOp", "GREATER_THAN_OR_EQUAL"), true, 2f, 3f);
        call(builder, "withDepthStencilState", depth);
        return call(builder, "build");
    }

    private Object adapt(Object source, boolean reverse, boolean depth, boolean stencil, Object test) throws Exception {
        return call(PipelineCache.class, "adapt", source, reverse,
                field("com.mojang.renderpearl.api.GpuFormat", "RGBA8_UNORM"), depth, stencil, test);
    }

    @Test
    void clonesAllMaterialStateConvertsDepthAndCachesInjectedStencil() throws Exception {
        Object source = material();
        Object stencil = call(PipelineCache.class, "stencil", 514, 2, 255, 0, 7680, 7680, 7680);
        Object adapted = adapt(source, true, true, true, stencil);
        assertSame(adapted, adapt(source, true, true, true, stencil));
        for (String property : new String[] {"getShaders", "getShaderDefines", "getBindGroupLayouts",
                "getVertexFormatBindings", "getPrimitiveTopology", "getColorTargetStates", "getPolygonMode",
                "isCull", "pushConstantSize"}) assertEquals(call(source, property), call(adapted, property), property);
        Object depth = call(adapted, "getDepthStencilState");
        assertEquals("LESS_THAN_OR_EQUAL", call(depth, "depthTest").toString());
        assertEquals(true, call(depth, "writeDepth"));
        assertEquals(-2f, call(depth, "depthBiasScaleFactor"));
        assertEquals(-3f, call(depth, "depthBiasConstant"));
        assertEquals(stencil, call(depth, "stencilTest"));
        assertNull(call(call(source, "getDepthStencilState"), "stencilTest"));
        assertEquals("GREATER_THAN_OR_EQUAL", call(call(source, "getDepthStencilState"), "depthTest").toString());
    }

    @Test
    void attachmentFormatChangesWithoutChangingBlendOrWriteMask() throws Exception {
        Object source = material();
        Object format = field("com.mojang.renderpearl.api.GpuFormat", "RGBA16_FLOAT");
        Object adapted = call(PipelineCache.class, "adapt", source, false, format, true, false, null);
        var originalColors = (java.util.List<?>) call(source, "getColorTargetStates");
        var colors = (java.util.List<?>) call(adapted, "getColorTargetStates");
        assertEquals(format, call(colors.getFirst(), "format"));
        assertEquals(call(originalColors.getFirst(), "blendFunction"), call(colors.getFirst(), "blendFunction"));
        assertEquals(call(originalColors.getFirst(), "writeMask"), call(colors.getFirst(), "writeMask"));
        assertNull(colors.get(1));
        assertEquals(call(source, "getDepthStencilState"), call(adapted, "getDepthStencilState"));
    }

    @Test
    void missingDepthRemovesDepthStencilAndStencilOnlyDoesNotWriteDepth() throws Exception {
        Object source = material();
        Object stencil = call(PipelineCache.class, "stencil", 514, 1, 255, 0, 7680, 7680, 7680);
        assertNull(call(adapt(source, false, false, false, stencil), "getDepthStencilState"));
        assertNull(call(call(adapt(source, false, true, false, stencil), "getDepthStencilState"), "stencilTest"));
        Object builder = call(source, "toBuilder");
        call(builder, "withDepthStencilState", Optional.empty());
        Object noDepth = call(builder, "build");
        Object depth = call(adapt(noDepth, true, true, true, stencil), "getDepthStencilState");
        assertEquals("ALWAYS_PASS", call(depth, "depthTest").toString());
        assertEquals(false, call(depth, "writeDepth"));
        assertEquals(stencil, call(depth, "stencilTest"));
        for (Object op : Class.forName(PIPELINE + "CompareOp").getEnumConstants())
            assertEquals(op, call(PipelineCache.class, "reverse", call(PipelineCache.class, "reverse", op)));
    }
}
