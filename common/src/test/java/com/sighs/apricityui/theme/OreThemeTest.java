package com.sighs.apricityui.theme;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreThemeTest {
    private static final String RESOURCE_BASE = "assets/apricityui/apricity/apricityui/";
    private static final Path ASSET_ROOT = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui");

    @Test
    void pureCssThemesKeepSeparateSingleClassEntrypoints() throws Exception {
        String ore = read("theme/ore/ore.css");
        String mcui = read("theme/mcui/mcui.css");
        assertTrue(ore.contains(".ore-theme .button"));
        assertTrue(mcui.contains(".mcui-theme .button"));
        assertFalse(mcui.contains(".ore-theme"));
        assertFalse(ore.contains("@import"));
        assertFalse(mcui.contains("@import"));

        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(mcui, cache, "theme/mcui/mcui.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        document.body.setAttribute("class", "mcui-theme");
        Element button = document.createElement("button");
        button.setAttribute("class", "button button-primary");
        document.body.appendChild(button);
        assertEquals("#3c8527", button.getComputedStyle().backgroundColor);
        assertTrue(Files.isRegularFile(ASSET_ROOT.resolve("theme/ore/example.html")));
        assertTrue(Files.isRegularFile(ASSET_ROOT.resolve("theme/mcui/example.html")));
    }

    @Test
    void latestComponentGalleryUsesTheIndependentRuntime() throws Exception {
        String oreEntry = read("theme/ore/mcui-example.html");
        String mcuiEntry = read("theme/mcui/vue-example.html");
        for (String entry : new String[]{oreEntry, mcuiEntry}) {
            assertTrue(entry.contains("../../runtime/mcui/components.css"));
            assertTrue(entry.contains("../../runtime/mcui/gallery.aui.js"));
            assertTrue(entry.contains("McUIVue.createMcUI("));
            assertTrue(entry.contains("McUIVisualGallery.default"));
            assertFalse(entry.contains("McUIVue.default"));
            assertFalse(entry.contains("type=\"module\""));
        }
        String css = read("runtime/mcui/components.css");
        assertTrue(css.contains(":where(.mc-theme"));
        assertTrue(css.contains(".mc-checkbox__mark"));
        assertFalse(css.contains(".mc-skin-viewer"));
        assertFalse(Files.exists(ASSET_ROOT.resolve("theme/ore/runtime/mcui-oreui.aui.js")));
    }

    @Test
    void oreAndMcUiRuntimeManifestsCoverAllPackagedResources() throws Exception {
        verifyManifest("theme/ore");
        verifyManifest("runtime/mcui");
    }

    @Test
    void latestSourceAndOptionalFontLicensesArePackaged() throws Exception {
        assertTrue(read("runtime/mcui/source.md").contains("d3344a6cec68ce97eb990c125ed3e050c69d7d4c"));
        assertTrue(read("runtime/mcui/source.md").contains("68 public components"));
        assertTrue(read("runtime/mcui/LICENSE.txt").contains("MIT License"));
        assertTrue(read("theme/ore/license.txt").contains("Mozilla Public License Version 2.0"));
        assertTrue(read("theme/ore/source.md").contains("single-class Ore theme"));
        assertTrue(read("runtime/mcui/fonts.css").contains("fonts/Minecraft-Ten.otf"));
        for (String name : new String[]{"Minecraft-Ten.otf", "Minecraft-Seven.otf",
                "Minecraft-Five.otf", "Minecraft-Five-Bold.otf"}) {
            assertNotNull(OreThemeTest.class.getClassLoader()
                    .getResource(RESOURCE_BASE + "runtime/mcui/fonts/" + name));
        }
    }

    private static void verifyManifest(String relativeRoot) throws Exception {
        String manifest = read(relativeRoot + "/provenance.sha256");
        Set<String> manifestPaths = new TreeSet<>();
        for (String line : manifest.split("\\R")) {
            if (line.isBlank()) continue;
            String[] fields = line.trim().split("\\s+", 2);
            assertEquals(2, fields.length, line);
            assertTrue(manifestPaths.add(fields[1]), "duplicate manifest path: " + fields[1]);
            byte[] bytes = readBytes(relativeRoot + "/" + fields[1]);
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(fields[0], actual, relativeRoot + "/" + fields[1]);
        }
        Set<String> sourcePaths = new TreeSet<>();
        Path root = ASSET_ROOT.resolve(relativeRoot);
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !"provenance.sha256".equals(path))
                    .forEach(sourcePaths::add);
        }
        assertEquals(sourcePaths, manifestPaths, relativeRoot);
    }

    private static String read(String relative) throws Exception {
        return new String(readBytes(relative), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String relative) throws Exception {
        try (InputStream input = OreThemeTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_BASE + relative)) {
            assertNotNull(input, relative);
            return input.readAllBytes();
        }
    }
}
