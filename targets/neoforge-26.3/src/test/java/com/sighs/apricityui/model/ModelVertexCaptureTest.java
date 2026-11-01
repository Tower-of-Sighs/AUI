package com.sighs.apricityui.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelVertexCaptureTest {
    @Test
    void preservesMaterialFaceAndVertexAttributes() throws Exception {
        Class<?> identifierType = Class.forName("net.minecraft.resources.Identifier");
        Object identifier = identifierType.getMethod("withDefaultNamespace", String.class)
                .invoke(null, "textures/entity/iron_golem/iron_golem.png");
        Class<?> renderType = Class.forName("net.minecraft.client.renderer.rendertype.RenderType");
        Class<?> renderTypes = Class.forName("net.minecraft.client.renderer.rendertype.RenderTypes");
        Object material = renderTypes.getMethod("entityTranslucent", identifierType).invoke(null, identifier);

        Class<?> captureType = Class.forName("com.sighs.apricityui.model.ModelVertexCapture");
        Constructor<?> constructor = captureType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object capture = constructor.newInstance();
        Method getBuffer = captureType.getDeclaredMethod("getBuffer", renderType);
        getBuffer.setAccessible(true);
        Object vertices = getBuffer.invoke(capture, material);
        Class<?> consumerType = Class.forName("com.mojang.blaze3d.vertex.VertexConsumer");
        for (int i = 0; i < 4; i++) {
            Object current = invoke(consumerType.getMethod("addVertex", float.class, float.class, float.class),
                    vertices, (float) (i & 1), (float) (i >> 1), 0f);
            current = invoke(consumerType.getMethod("setColor", int.class), current, 0xff6a7b8c);
            current = invoke(consumerType.getMethod("setUv", float.class, float.class), current, i / 3f, 1 - i / 3f);
            current = invoke(consumerType.getMethod("setOverlay", int.class), current, 0x000a0000);
            current = invoke(consumerType.getMethod("setLight", int.class), current, 15728880);
            invoke(consumerType.getMethod("setNormal", float.class, float.class, float.class), current, 0f, 0f, 1f);
        }
        Method finish = captureType.getDeclaredMethod("finish");
        finish.setAccessible(true);
        finish.invoke(capture);

        var meshField = captureType.getDeclaredField("mesh");
        meshField.setAccessible(true);
        Object mesh = meshField.get(capture);
        Method layout = mesh.getClass().getMethod("layout", ModelSpec.class, float.class, float.class, float.class,
                float.class, Matrix4f.class, Matrix3f.class);
        var faces = (List<?>) layout.invoke(mesh, new ModelSpec("bakedmodel", "minecraft:iron_golem", false, 0, 0, 0),
                0f, 0f, 16f, 16f, new Matrix4f(), new Matrix3f());
        assertFalse(faces.isEmpty());
        Object face = faces.getFirst();
        List<?> capturedVertices = (List<?>) face.getClass().getMethod("vertices").invoke(face);
        assertEquals(4, capturedVertices.size());
        Object vertex = capturedVertices.getFirst();
        assertEquals(0xff6a7b8c, (int) vertex.getClass().getMethod("color").invoke(vertex));
        assertEquals(15728880, (int) vertex.getClass().getMethod("light").invoke(vertex));
        assertEquals(0f, (float) vertex.getClass().getMethod("u").invoke(vertex));
        assertEquals(0x000a0000, (int) vertex.getClass().getMethod("overlay").invoke(vertex));
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        return method.invoke(target, arguments);
    }
}
