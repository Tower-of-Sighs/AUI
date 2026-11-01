package com.sighs.apricityui.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ModelVertexCaptureTest {
    // Inline fixture for the real-client preview smoke run.
    static final String PREVIEW_HTML = """
            <head><meta name="aui-viewport" content="mode=browser"></head>
            <body style="margin:0;background:#202830;color:white;width:900px;height:560px;">
              <div style="display:flex;gap:16px;padding:16px;">
                <div>Zombie<model id="zombie" type="bakedmodel" yaw="25">minecraft:zombie</model></div>
                <div>Flat<model id="flat" type="bakedmodel" yaw="25" flat="true">minecraft:zombie</model></div>
                <div>Player<model id="player" type="player" yaw="25">8667ba71-b85a-4004-af54-457a9734eed7</model></div>
                <div>Flat player<model id="flat-player" type="player" flat="true" yaw="25">8667ba71-b85a-4004-af54-457a9734eed7</model></div>
              </div>
              <div style="display:flex;gap:16px;padding:16px;">
                <model id="creeper" type="bakedmodel" pitch="15" roll="10" style="width:170px;height:180px;border:2px solid white;">minecraft:creeper</model>
                <model id="opacity" type="bakedmodel" style="opacity:0.5;background:#8090a0;">minecraft:skeleton</model>
                <model id="filter" type="player" style="filter:grayscale(1);transform:rotate(10deg);">8667ba71-b85a-4004-af54-457a9734eed7</model>
                <model id="invalid" type="bakedmodel" style="border:2px solid red;">minecraft:missing_entity</model>
              </div>
            </body>
            """;

    @Test
    void capturesCompleteQuadsWithTheirMaterialAndAttributes() throws Exception {
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Class<?> resource = Class.forName("net.minecraft.resources.ResourceLocation");
        Object texture = resource.getConstructor(String.class).newInstance("minecraft:textures/entity/zombie/zombie.png");
        Class<?> renderType = Class.forName("net.minecraft.client.renderer.RenderType");
        Object material = renderType.getMethod("entityCutout", resource).invoke(null, texture);
        Object capture = ModelVertexCapture.class.getDeclaredConstructor().newInstance();
        Object vertices = ModelVertexCapture.class.getMethod("getBuffer", renderType).invoke(capture, material);
        Class<?> consumer = Class.forName("com.mojang.blaze3d.vertex.VertexConsumer");
        Method vertex = consumer.getMethod("vertex", double.class, double.class, double.class);
        for (int i = 0; i < 4; i++) {
            vertex.invoke(vertices, (double) (i % 2), (double) (i / 2), 2D);
            consumer.getMethod("color", int.class, int.class, int.class, int.class).invoke(vertices, 12, 34, 56, 200);
            consumer.getMethod("uv", float.class, float.class).invoke(vertices, .25F, .75F);
            consumer.getMethod("overlayCoords", int.class, int.class).invoke(vertices, 3, 4);
            consumer.getMethod("uv2", int.class, int.class).invoke(vertices, 5, 6);
            consumer.getMethod("normal", float.class, float.class, float.class).invoke(vertices, 0F, 0F, 1F);
            consumer.getMethod("endVertex").invoke(vertices);
        }
        ModelMesh<?> mesh = (ModelMesh<?>) ModelVertexCapture.class.getDeclaredField("mesh").get(capture);
        var faces = mesh.layout(ModelSpec.parse("bakedmodel", "minecraft:zombie", "true", "0", "0", "0"),
                0, 0, 100, 100, new Matrix4f(), new Matrix3f());
        assertEquals(1, faces.size());
        assertSame(material, faces.get(0).material());
        assertEquals(4, faces.get(0).vertices().size());
        var v = faces.get(0).vertices().get(0);
        assertEquals(0xc80c2238, v.color());
        assertEquals(.25F, v.u());
        assertEquals(.75F, v.v());
        assertEquals(3 | 4 << 16, v.overlay());
        assertEquals(5 | 6 << 16, v.light());
        assertEquals(0, v.z());
    }
}
