package com.sighs.apricityui.dev.resource;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceFileWriterTest {
    @Test
    void writesHtmlOnlyInsideProvidedResourceRoot() throws Exception {
        Path root = Files.createTempDirectory("aui-resource-writer");
        try {
            ResourceFileWriter.WriteResult result = ResourceFileWriter.writeHtml(root, "example/imported.html", "<body>ok</body>");
            assertTrue(result.success());
            assertEquals("<body>ok</body>", Files.readString(root.resolve("example/imported.html")));

            assertFalse(ResourceFileWriter.writeHtml(root, "../outside.html", "x").success());
            assertFalse(ResourceFileWriter.writeHtml(root, "example/not-html.txt", "x").success());
            assertFalse(ResourceFileWriter.writeHtml(root, "example/empty.html", "").success());
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }
}
