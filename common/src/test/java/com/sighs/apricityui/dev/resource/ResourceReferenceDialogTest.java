package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.loader.Loader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceReferenceDialogTest {
    @Test
    void imageReferencesUseRootPathsForCssAndHtml() {
        Loader.StaticResourceEntry entry = entry("images/hero-photo.png", "png");

        List<ResourceReferenceDialog.ReferenceOption> options = ResourceReferenceDialog.optionsFor(entry, "");

        assertEquals(2, options.size());
        assertEquals("background-image: url(\"/images/hero-photo.png\");", options.get(0).snippet());
        assertEquals("<img src=\"/images/hero-photo.png\" alt=\"hero-photo\">", options.get(1).snippet());
    }

    @Test
    void fontReferencesShareTheConfiguredFamilyAndCorrectFormat() {
        Loader.StaticResourceEntry entry = entry("fonts/display.otf", "otf");

        List<ResourceReferenceDialog.ReferenceOption> options = ResourceReferenceDialog.optionsFor(entry, "Display Sans");

        assertEquals(2, options.size());
        assertEquals("""
                @font-face {
                    font-family: "Display Sans";
                    src: url("/fonts/display.otf") format("opentype");
                }""", options.get(0).snippet());
        assertEquals("font-family: \"Display Sans\", sans-serif;", options.get(1).snippet());
    }

    @Test
    void fontReferenceDefaultsToTheFileStem() {
        Loader.StaticResourceEntry entry = entry("fonts/lxgw3500.ttf", "ttf");

        List<ResourceReferenceDialog.ReferenceOption> options = ResourceReferenceDialog.optionsFor(entry, "");

        assertEquals("font-family: \"lxgw3500\", sans-serif;", options.get(1).snippet());
    }

    @Test
    void htmlJavaReferencesCoverAllFourRenderingScenarios() {
        Loader.StaticResourceEntry entry = entry("screens/inventory.html", "html");

        List<ResourceReferenceDialog.ReferenceOption> options =
                ResourceReferenceDialog.optionsFor(entry, "", "java");

        assertTrue(ResourceReferenceDialog.supports(entry));
        assertEquals(4, options.size());
        assertEquals("ApricityUI.screen(\"screens/inventory.html\");", options.get(0).snippet());
        assertEquals("ApricityUI.menu(player, \"screens/inventory.html\")"
                + ".bind(bindings -> bindings.player());", options.get(1).snippet());
        assertEquals("var overlay = ApricityUI.createDocument(\"screens/inventory.html\");",
                options.get(2).snippet());
        assertEquals("""
                var worldWindow = ApricityUI.createWorldWindow(
                    "screens/inventory.html",
                    new Vec3(0.0, 64.0, 0.0),
                    16
                );""", options.get(3).snippet());
    }

    @Test
    void htmlKjsReferencesCoverAllFourRenderingScenarios() {
        Loader.StaticResourceEntry entry = entry("screens/inventory.html", "html");

        List<ResourceReferenceDialog.ReferenceOption> options =
                ResourceReferenceDialog.optionsFor(entry, "", "kjs");

        assertEquals(4, options.size());
        assertEquals("ApricityUI.screen(\"screens/inventory.html\")", options.get(0).snippet());
        assertEquals("ApricityUI.menu(player, \"screens/inventory.html\")"
                + ".bind(bindings => bindings.player())", options.get(1).snippet());
        assertEquals("let overlay = ApricityUI.createDocument(\"screens/inventory.html\")",
                options.get(2).snippet());
        assertEquals("""
                let worldWindow = ApricityUI.createWorldWindow(
                    "screens/inventory.html",
                    0, 64, 0,
                    16
                )""", options.get(3).snippet());
    }

    private static Loader.StaticResourceEntry entry(String path, String extension) {
        return new Loader.StaticResourceEntry(path, extension, Loader.ResourceLayer.DEV_FOLDER, "", "", 1);
    }
}
