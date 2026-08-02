package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.instance.loader.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsDocumentStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyExistingWritableHtmlInItsResourceRoot() throws Exception {
        Path localRoot = Files.createDirectories(tempDir.resolve("apricity"));
        Path devRoot = Files.createDirectories(tempDir.resolve("resources"));
        Files.createDirectories(localRoot.resolve("pages"));
        Files.createDirectories(devRoot.resolve("dev"));
        Files.writeString(localRoot.resolve("pages/local.html"), "<html></html>");
        Files.writeString(localRoot.resolve("styles.css"), ".card { color: red; }");
        Files.writeString(devRoot.resolve("dev/source.html"), "<html></html>");
        Files.writeString(tempDir.resolve("pack.css"), ".card { color: red; }");

        Loader.StaticResourceEntry local = entry("pages/local.html", Loader.ResourceLayer.LOCAL_FOLDER, localRoot);
        Loader.StaticResourceEntry css = entry("styles.css", Loader.ResourceLayer.LOCAL_FOLDER, localRoot);
        Loader.StaticResourceEntry dev = entry("dev/source.html", Loader.ResourceLayer.DEV_FOLDER, devRoot);
        Loader.StaticResourceEntry pack = entry("pack.html", Loader.ResourceLayer.RESOURCE_PACK, tempDir);
        Loader.StaticResourceEntry packCss = entry("pack.css", Loader.ResourceLayer.RESOURCE_PACK, tempDir);

        assertTrue(DevToolsDocumentStore.resolve("pages/local.html", List.of(local), true).writable());
        assertTrue(DevToolsDocumentStore.resolve("dev/source.html", List.of(dev), false).writable());
        assertFalse(DevToolsDocumentStore.resolve("dev/source.html", List.of(dev), true).writable());
        assertFalse(DevToolsDocumentStore.resolve("pack.html", List.of(pack), false).writable());
        assertTrue(DevToolsDocumentStore.resolveResource("styles.css", List.of(css), true).writable());
        assertFalse(DevToolsDocumentStore.resolveResource("pack.css", List.of(packCss), false).writable());
        assertFalse(DevToolsDocumentStore.resolve("../outside.html", List.of(local), false).writable());
        assertFalse(DevToolsDocumentStore.resolve("pages/local.css", List.of(local), false).writable());
    }

    @Test
    void overwritesAnExistingResolvedFile() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path file = root.resolve("page.html");
        Files.writeString(file, "old");
        DevToolsDocumentStore.Resolution resolution = DevToolsDocumentStore.resolve(
                "page.html", List.of(entry("page.html", Loader.ResourceLayer.LOCAL_FOLDER, root)), true);

        assertTrue(resolution.writable());
        assertTrue(DevToolsDocumentStore.save(resolution.target(), "new").success());
        assertEquals("new", Files.readString(file));
    }

    private static Loader.StaticResourceEntry entry(String path, Loader.ResourceLayer layer, Path root) {
        return new Loader.StaticResourceEntry(path, "html", layer, root.toString(), root.toString(), 1);
    }
}
