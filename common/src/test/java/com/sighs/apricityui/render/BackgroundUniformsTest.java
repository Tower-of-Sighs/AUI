package com.sighs.apricityui.render;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundUniformsTest {
    @Test
    void legacyDeclarationsAndPipelineBlockHaveTheSamePackedOrder() throws Exception {
        Path resources = Path.of("../../common/src/main/resources/assets/apricityui/shaders/core");
        var json = JsonParser.parseString(Files.readString(resources.resolve("background.json"))).getAsJsonObject();
        var uniforms = json.getAsJsonArray("uniforms");
        assertEquals(BackgroundUniforms.VECTOR_COUNT + 2, uniforms.size());
        String shader = Files.readString(resources.resolve("background.fsh"));
        int last = -1;
        for (int i = 0; i < BackgroundUniforms.NAMES.length; i++) {
            String name = BackgroundUniforms.NAMES[i];
            assertEquals(name, uniforms.get(i + 2).getAsJsonObject().get("name").getAsString());
            int offset = shader.indexOf("uniform vec4 " + name + ";");
            assertTrue(offset > last, name);
            last = offset;
        }
        assertEquals(BackgroundUniforms.VECTOR_COUNT, Arrays.stream(BackgroundUniforms.NAMES).distinct().count());
    }

    @Test
    void pipelineUniformPackingIsStd140Vec4Aligned() {
        BackgroundUniforms uniforms = new BackgroundUniforms();
        for (int i = 0; i < BackgroundUniforms.NAMES.length; i++) {
            assertTrue(uniforms.set(BackgroundUniforms.NAMES[i], i, i + .25f, i + .5f, i + .75f));
        }
        assertFalse(uniforms.set("notBackground", 0, 0, 0, 0));
        ByteBuffer data = ByteBuffer.allocate(BackgroundUniforms.VECTOR_COUNT * 16).order(ByteOrder.nativeOrder());
        uniforms.write(data);
        assertEquals(data.capacity(), data.position());
        data.flip();
        for (int i = 0; i < BackgroundUniforms.NAMES.length; i++) {
            assertEquals(i, data.getFloat());
            assertEquals(i + .25f, data.getFloat());
            assertEquals(i + .5f, data.getFloat());
            assertEquals(i + .75f, data.getFloat());
        }
    }
}
