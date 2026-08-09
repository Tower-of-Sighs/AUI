package com.sighs.apricityui.webapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterRendererOptimizationTest {
    private static final Path SHADERS = Path.of(
            "../../common/src/main/resources/assets/apricityui/shaders/core");
    private static final Path NEOFORGE_261_SHADERS = Path.of(
            "../../targets/neoforge-26.1/src/main/resources/assets/apricityui/shaders/core");

    @Test
    void filterCompositeDoesNotContainQuadraticBlurLoops() throws Exception {
        String composite = Files.readString(SHADERS.resolve("filter.fsh"));
        String blur = Files.readString(SHADERS.resolve("filter_blur.fsh"));
        String neoForge261Blur = Files.readString(NEOFORGE_261_SHADERS.resolve("filter_blur.fsh"));

        assertFalse(composite.contains("for (int x"));
        assertFalse(composite.contains("for (int y"));
        // The shared common shader serves the legacy ShaderInstance targets
        // (1.20/1.21) and uses classic uniforms; the 26.1 target carries its own
        // std140 FilterParams variant. Either way the blur must stay separable.
        assertTrue(blur.contains("uniform vec2 Direction"));
        assertTrue(blur.contains("uniform float Radius"));
        assertTrue(neoForge261Blur.contains("layout(std140) uniform FilterParams"));
        assertTrue(neoForge261Blur.contains("#define Direction BlurDirection.xy"));
        assertTrue(blur.contains("for (int i = -32; i <= 32; i++)"));
        assertTrue(neoForge261Blur.contains("for (int i = -32; i <= 32; i++)"));
    }

    @Test
    void shaderDescriptorsExposeSeparableBlurAndPreblurredShadowSampler() throws Exception {
        JsonObject blur = JsonParser.parseString(
                Files.readString(SHADERS.resolve("filter_blur.json"))).getAsJsonObject();
        JsonObject composite = JsonParser.parseString(
                Files.readString(SHADERS.resolve("filter.json"))).getAsJsonObject();

        assertTrue(blur.getAsJsonArray("uniforms").toString().contains("Direction"));
        assertTrue(composite.getAsJsonArray("samplers").toString().contains("Sampler1"));
        assertTrue(composite.getAsJsonArray("uniforms").toString().contains("UvPerGuiPixel"));
    }

    @Test
    void backdropCopiesOnlyThePaddedElementRegion() throws Exception {
        String renderer = Files.readString(Path.of(
                "../../common/src/main/java/com/sighs/apricityui/render/FilterRenderer.java"));

        assertTrue(renderer.contains("prepareBackdropSource"));
        assertTrue(renderer.contains("srcX0, srcY0, srcX1, srcY1"));
        assertTrue(renderer.contains("chooseDownsample"));
        assertFalse(renderer.contains("copyToBackdropSource"));
    }
}
