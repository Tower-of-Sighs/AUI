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
import java.util.LinkedHashMap;
import java.util.Map;

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
    void optionalFontsAndRuntimeNoticesArePackaged() throws Exception {
        assertTrue(read("runtime/vue.aui.js").contains("Copyright (c) 2018-present, Yuxi (Evan) You"));
        assertTrue(read("runtime/mcui/mcui-oreui.aui.js").contains("Copyright (c) 2026 mcui-oreui contributors"));
        assertTrue(read("theme/ore/license.txt").contains("Mozilla Public License Version 2.0"));
        assertTrue(read("runtime/mcui/fonts.css").contains("fonts/Minecraft-Ten.otf"));
        for (String name : new String[]{"Minecraft-Ten.otf", "Minecraft-Seven.otf",
                "Minecraft-Five.otf", "Minecraft-Five-Bold.otf"}) {
            assertNotNull(OreThemeTest.class.getClassLoader()
                    .getResource(RESOURCE_BASE + "runtime/mcui/fonts/" + name));
        }
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
