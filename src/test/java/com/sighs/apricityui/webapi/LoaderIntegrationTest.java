package com.sighs.apricityui.webapi;

import com.sighs.apricityui.instance.loader.Loader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoaderIntegrationTest {
    @Test
    void globalBootstrapResourcesAreReadableThroughLoader() throws IOException {
        String globalJs = Loader.readGlobalJS();

        assertNotNull(globalJs);
        assertTrue(globalJs.contains("ApricityUI.getDocumentByUUID(\"__AUI_DOCUMENT_UUID__\")"));
        assertTrue(globalJs.contains("function MutationObserver(callback)"));

        try (InputStream stream = Loader.getResourceStream("global.js")) {
            assertNotNull(stream);
            String fromStream = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(globalJs, fromStream);
        }
    }

    @Test
    void resolveNormalizesRelativeAbsoluteAndRemotePaths() {
        assertEquals("scripts/app.js", Loader.resolve("pages/index.html", "../scripts/app.js"));
        assertEquals("assets/app.js", Loader.resolve("pages/index.html", "/assets/app.js"));
        assertEquals("https://example.com/app.js", Loader.resolve("pages/index.html", "https://example.com/app.js"));
        assertEquals("", Loader.resolve("pages/index.html", "   "));
    }

    @Test
    void watchRootsIncludeWorkspaceDevAssetDirectory() {
        List<Path> roots = Loader.getWatchRoots();

        assertFalse(roots.isEmpty());
        assertTrue(roots.stream().anyMatch(path ->
                path.toString().replace('\\', '/').endsWith("src/main/resources/assets/apricityui/apricity")));
    }

    @Test
    void projectRootCandidatesProgressivelyTryShorterRelativeSuffixes() throws Exception {
        Method method = Loader.class.getDeclaredMethod("buildProjectRootCandidates", Path.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Path> candidates = (List<Path>) method.invoke(null, Path.of("D:/work/AUI"), "assets/apricityui/apricity/global.js");

        assertEquals(List.of(
                Path.of("D:/work/AUI/assets/apricityui/apricity/global.js").normalize(),
                Path.of("D:/work/AUI/apricityui/apricity/global.js").normalize(),
                Path.of("D:/work/AUI/apricity/global.js").normalize(),
                Path.of("D:/work/AUI/global.js").normalize()
        ), candidates);
    }
}
