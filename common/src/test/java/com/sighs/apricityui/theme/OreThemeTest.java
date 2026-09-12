package com.sighs.apricityui.theme;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.webapi.RhinoTestSupport;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreThemeTest {
    private static final String RESOURCE_ROOT =
            "assets/apricityui/apricity/apricityui/theme/ore/";
    private static final Path THEME_ROOT = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme");

    @Test
    void onlyMcuiOreThemeIsBundled() {
        assertTrue(Files.isDirectory(THEME_ROOT.resolve("ore")));
        assertFalse(Files.exists(THEME_ROOT.resolve("ore-jiyath5516f")));
        assertFalse(Files.exists(THEME_ROOT.resolve("ore-spectrollay")));
        assertFalse(Files.exists(THEME_ROOT.resolve("ore-paraore")));
        assertFalse(Files.exists(THEME_ROOT.resolve("ore/ore-edit-example.html")));
        assertFalse(Files.exists(THEME_ROOT.resolve("ore/ore-edit.css")));
        assertTrue(Files.exists(THEME_ROOT.resolve("ore/fonts/minecraft-ten.otf")));
        assertTrue(Files.exists(THEME_ROOT.resolve("ore/fonts/minecraft-seven.otf")));
        assertTrue(Files.exists(THEME_ROOT.resolve("ore/fonts/minecraft-regular.otf")));
        assertTrue(Files.exists(THEME_ROOT.resolve("ore/fonts/minecraft-ten.ttf")));
    }

    @Test
    void pinnedRuntimeAndReferenceAssetsMatchTheIntegrityManifest() throws Exception {
        String manifest = readResource("provenance.sha256");
        Set<String> manifestPaths = new TreeSet<>();
        for (String line : manifest.split("\\R")) {
            if (line.isBlank()) continue;
            String[] fields = line.trim().split("\\s+", 2);
            assertEquals(2, fields.length, line);
            assertTrue(manifestPaths.add(fields[1]), "duplicate manifest path: " + fields[1]);
            try (InputStream input = OreThemeTest.class.getClassLoader()
                    .getResourceAsStream(RESOURCE_ROOT + fields[1])) {
                assertNotNull(input, fields[1]);
                String actual = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                assertEquals(fields[0], actual, fields[1]);
            }
        }
        Set<String> sourcePaths = new TreeSet<>();
        Path oreRoot = THEME_ROOT.resolve("ore");
        try (var paths = Files.walk(oreRoot)) {
            paths.filter(Files::isRegularFile)
                    .map(oreRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !"provenance.sha256".equals(path))
                    .forEach(sourcePaths::add);
        }
        assertEquals(sourcePaths, manifestPaths,
                "integrity manifest must cover every Ore resource except itself");
    }

    @Test
    void sourceAndRuntimeBoundaryAreExplicit() throws Exception {
        String source = readResource("source.md");
        String readme = readResource("readme.md");
        String license = readResource("license.txt");

        assertTrue(source.contains("ec87d29a9516a741e5bd4ac707dcabc704409cb2"));
        assertTrue(source.contains("ShenYuanOR/mcui-oreui"));
        assertTrue(readme.contains("mcui-oreui Vue runtime"));
        assertTrue(readme.contains("remaining 32 components"));
        assertTrue(readResource("runtime/vue-license.txt").contains("MIT License"));
        assertTrue(readResource("overview.css").contains(".ore-overview"));
        assertTrue(readme.contains("No Chromium, MCEF, JCEF"));
        assertTrue(license.contains("MIT License"));
    }

    @Test
    void exampleUsesSingleScopeAndCompilesOnRhino() throws Exception {
        String html = readResource("mcui-example.html");

        assertTrue(html.contains("href=\"ore.css\""));
        assertTrue(html.contains("href=\"mcui.css\""));
        assertTrue(html.contains("id=\"showcase-root\""));
        assertTrue(html.contains("src=\"runtime/vue.aui.js\""));
        assertTrue(html.contains("src=\"runtime/mcui-oreui.aui.js\""));
        assertTrue(html.contains("src=\"runtime/showcase.aui.js\""));
        assertFalse(html.contains("runtime/docs-shell.aui.js"));
        assertFalse(html.contains("data-theme-button"));
        assertFalse(html.contains("type=\"module\""));
        String script = readResource("runtime/showcase.aui.js");
        assertTrue(script.contains("app.use(Mc.default)"));
        assertTrue(script.contains("data-mcui-components"));
        dev.latvian.mods.rhino.Context context = RhinoTestSupport.enterContext();
        assertNotNull(context.compileString(script, "ore/runtime/showcase.aui.js", 1, null));
    }

    @Test
    void componentStylesStayScopedAndPreserveUpstreamRelationalSelectors() throws Exception {
        String components = readResource("ore-components.css");

        assertTrue(components.contains(":has("));
        assertFalse(components.contains("\n:root"));
        assertTrue(components.contains(".ore-theme .mc-panel"));
        assertTrue(components.contains(".ore-theme .mc-appbar"));
        assertTrue(components.contains(".ore-theme .mc-progress"));
        assertTrue(components.contains(".ore-theme .mc-list"));
        assertTrue(components.contains(".ore-theme .primary_btn"));
        assertTrue(components.contains(".ore-theme .dropdown_option:focus"));

        assertTokenDriven(readResource("mcui.css"), "mcui Ore declarations");
        assertTokenDriven(components, "component declarations");
    }

    @Test
    void representativeComponentsResolveOnlyInsideOreScope() throws Exception {
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(readResource("ore.css"), cache, "ore/ore.css");
        CSS.readCSS(readResource("ore-components.css"), cache, "ore/ore-components.css");

        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "ore-theme");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element primary = assertDisplay(document, "button", "btn middle_btn primary_btn", "inline-flex");
        assertEquals("#3C8527", primary.getComputedStyle().backgroundColor);
        assertFalse("none".equals(primary.getComputedStyle().boxShadow));
        Element appbar = assertDisplay(document, "div", "mc-appbar", "flex");
        assertEquals("relative", appbar.getComputedStyle().position);
        assertEquals("1", appbar.getComputedStyle().zIndex);
        assertDisplay(document, "section", "mc-panel", "inline-flex");
        assertDisplay(document, "div", "mc-progress", "block");
        assertDisplay(document, "div", "mc-list", "block");
        Element icon = assertDisplay(document, "span", "mc-icon", "inline-flex");
        assertEquals("visible", icon.getComputedStyle().overflow);
        Element pop = assertDisplay(document, "div", "pop show", "block");
        assertEquals("1.0", pop.getComputedStyle().opacity);
        assertFalse(Drawer.createPaintList(document.body).stream().anyMatch(node ->
                node instanceof RenderNode.FilterPushNode
                        && RenderNode.getRenderNodeTarget(node) == pop));

        Document unscoped = TestDocumentFactory.createDocument();
        unscoped.CSSCache.putAll(cache);
        unscoped.rebuildSelectorIndex();
        Element button = unscoped.createElement("button");
        button.setAttribute("class", "btn middle_btn primary_btn");
        unscoped.body.appendChild(button);
        assertFalse("inline-flex".equals(button.getComputedStyle().display));
    }

    @Test
    void runtimeFontsArePresent() {
        String[] resources = {
                "fonts/minecraft-ten.otf", "fonts/minecraft-seven.otf",
                "fonts/minecraft-five.otf", "fonts/minecraft-five-bold.otf",
                "fonts/noto-sans-bold.ttf", "fonts/noto-sans-bold-italic.ttf",
                "fonts/noto-sans-italic.ttf"
        };
        for (String resource : resources) {
            assertNotNull(OreThemeTest.class.getClassLoader().getResource(RESOURCE_ROOT + resource), resource);
        }
    }

    @Test
    void customerShowcaseIsAStandaloneProductDemoUsingEveryMcUiElement() throws Exception {
        String customer = readCustomerDemo();

        assertTrue(Pattern.compile("(?is)<body\\b[^>]*\\bdata-customer-showcase\\s*=\\s*\"true\"")
                .matcher(customer).find());
        assertTrue(Pattern.compile("(?is)<body\\b[^>]*\\bdata-customer-demo\\s*=\\s*\"true\"")
                .matcher(customer).find());
        assertTrue(Pattern.compile("(?is)<body\\b[^>]*\\bdata-mcui-runtime-components\\s*=\\s*\"32\"")
                .matcher(customer).find());
        assertTrue(customer.contains("id=\"customer-demo-root\""));
        assertTrue(customer.contains("var Vue;"), "customer showcase must inline Vue");
        assertTrue(customer.contains("m.McUIVue = {}"), "customer showcase must inline McUI");

        Set<String> expectedComponents = Set.of(
                "mc-icon", "mc-button", "mc-card", "mc-panel", "mc-tooltip", "mc-progress",
                "mc-spinner", "mc-checkbox", "mc-radio", "mc-radio-group", "mc-form-field",
                "mc-switch", "mc-dropdown", "mc-text-field", "mc-slider", "mc-layout", "mc-header",
                "mc-appbar", "mc-appbar-button", "mc-appbar-icon", "mc-tabs", "mc-button-tabs",
                "mc-list", "mc-list-item", "mc-scroll-view", "mc-modal", "mc-confirm", "mc-drawer",
                "mc-loading-mask", "mc-pop-host", "mc-tcode", "mc-formatted-text");

        int demoStart = customer.indexOf("/* customer-demo-app:start */");
        int demoEnd = customer.indexOf("/* customer-demo-app:end */", demoStart);
        assertTrue(demoStart >= 0 && demoEnd > demoStart, "missing customer demo app markers");
        String demoApp = customer.substring(demoStart, demoEnd);
        assertTrue(demoApp.contains("customer-demo-root"),
                "CUSTOMER_APP_JS must mount the dedicated customer demo app");
        assertTrue(demoApp.contains("Vue.createApp"), "customer demo must create a Vue app");
        assertTrue(demoApp.contains("app.mount"), "customer demo must mount its Vue app");
        assertTrue(demoApp.contains("renderedComponents[name] = true"),
                "customer demo must record components reached by its render path");
        assertTrue(demoApp.contains("data-customer-demo-mounted-components"),
                "customer demo must expose its runtime-mounted component count");

        Set<String> actualComponents = new TreeSet<>();
        Matcher components = Pattern.compile("\\bmc\\s*\\(\\s*[\"'](mc-[a-z0-9-]+)[\"']")
                .matcher(demoApp);
        while (components.find()) {
            actualComponents.add(components.group(1));
        }
        assertEquals(expectedComponents, actualComponents,
                "customer demo must call every McUI element and no others");

        assertFalse(Pattern.compile("(?is)<link\\b[^>]*\\brel\\s*=\\s*[\"']stylesheet[\"']")
                .matcher(customer).find(), "customer showcase must inline CSS");
        assertFalse(Pattern.compile("(?is)<script\\b[^>]*\\bsrc\\s*=")
                .matcher(customer).find(), "customer showcase must inline scripts");
        assertFalse(Pattern.compile("(?is)\\b(?:src|href)\\s*=\\s*[\"'](?!data:|#)[^\"']+[\"']")
                .matcher(customer).find(), "customer showcase must not use non-data resource URLs");
        assertFalse(customer.contains("showcase.aui.js"),
                "customer demo must not reference or inline the documentation showcase runtime");
        assertFalse(customer.contains("docs-shell"),
                "customer demo must not reference or inline the documentation shell runtime");
        assertFalse(customer.contains("data-component-anchor"),
                "customer demo must not expose a component catalog");
        assertFalse(Pattern.compile("(?is)<a\\b[^>]*\\bhref\\s*=\\s*[\"']#mc-[^\"']*[\"']")
                .matcher(customer).find(), "customer demo must not use component-directory links");
        assertFalse(Pattern.compile("(?is)data-component\\s*=").matcher(customer).find(),
                "customer demo must not use overview component markers");
        assertFalse(customer.contains("data-mcui-components"),
                "customer demo must not reuse the overview runtime contract");
        assertFalse(customer.contains("ore-component-case"),
                "customer demo must not inline the documentation showcase app");
        assertFalse(customer.contains("data-doc-page") || customer.contains("ore-doc-nav"),
                "customer demo must not inline the documentation shell app");
        assertNull(OreThemeTest.class.getClassLoader().getResource(
                RESOURCE_ROOT + "customer-showcase.html"),
                "customer demo must not be packaged as a mod resource");
        assertFalse(Files.exists(THEME_ROOT.resolve("ore/customer-showcase.html")),
                "customer demo must remain outside the Ore mod resource directory");
        assertFalse(Pattern.compile("(?is)href\\s*=\\s*[\"'][^\"']*details/[^\"']*\\.html")
                .matcher(customer).find(), "customer demo must not link to documentation details");
        Matcher styles = Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style>").matcher(customer);
        assertTrue(styles.find(), "customer showcase must inline CSS");
        do {
            assertFalse(Pattern.compile("(?is)url\\s*\\(\\s*(?!(?:[\"']?data:))")
                    .matcher(styles.group(1)).find(), "customer showcase CSS must not reference non-data URLs");
        } while (styles.find());
        assertFalse(Pattern.compile("(?is)\\bfetch\\s*\\(|\\bXMLHttpRequest\\b|\\bimport\\s*\\(")
                .matcher(customer).find(), "customer showcase must not load code dynamically");
    }

    @Test
    void upstreamFontsBackEveryOreRuntimeFamilyAndInteractionRegressionsStayFixed() throws Exception {
        String css = readResource("mcui.css");
        String legacyCss = readResource("ore.css");
        String componentsCss = readResource("ore-components.css") + "\n" + css;

        assertTrue(legacyCss.contains("font-family: OreRegular;"));
        assertTrue(legacyCss.contains("fonts/minecraft-regular.otf"));
        assertTrue(legacyCss.contains("font-family: OreDisplay;"));
        assertTrue(legacyCss.contains("fonts/minecraft-ten.ttf"));
        assertFontFace(css, "Minecraft Ten", "fonts/minecraft-ten.otf", "opentype");
        assertFontFace(css, "Minecraft Seven", "fonts/minecraft-seven.otf", "opentype");
        assertFontFace(css, "Minecraft Five", "fonts/minecraft-five.otf", "opentype");
        assertFontFace(css, "Minecraft Five Bold", "fonts/minecraft-five-bold.otf", "opentype");
        assertFontFace(css, "NotoSans Bold", "fonts/noto-sans-bold.ttf", "truetype");
        assertFontFace(css, "NotoSans BoldItalic", "fonts/noto-sans-bold-italic.ttf", "truetype");
        assertFontFace(css, "NotoSans Italic", "fonts/noto-sans-italic.ttf", "truetype");
        assertFalse(css.contains("NotoSans Bold Italic"));

        assertTrue(componentsCss.contains(".ore-theme .dropdown_options"));
        assertTrue(componentsCss.contains("top: calc(100% + 2px);"));
        assertTrue(componentsCss.contains("custom-dropdown:has(.dropdown_label.open_dropdown)"));
        assertTrue(componentsCss.contains("z-index: 5;"));

        assertTrue(componentsCss.contains("link-block::before"));
        assertTrue(componentsCss.contains("link-block::after"));
        assertTrue(componentsCss.contains("content: none;"));
        assertTrue(componentsCss.contains("@keyframes ore-card-flash"));
        assertFalse(componentsCss.contains("@keyframes ore-card-thick-flash"));
        assertFalse(componentsCss.contains("@keyframes ore-card-thin-flash"));
        assertTrue(componentsCss.contains("transform: translateX(-100%);"));
        assertTrue(componentsCss.contains("transform: translateX(100%);"));
        assertTrue(componentsCss.contains("animation: ore-card-flash 0.6s"));
        assertTrue(componentsCss.contains(".ore-theme link-block:hover,"));

        assertTrue(componentsCss.contains(".ore-theme .mc-tooltip:hover,"));
        assertTrue(componentsCss.contains(".ore-theme .mc-tooltip:focus-within"));
        assertTrue(componentsCss.contains(".ore-theme .btn_with_tooltip_content:hover,"));
        assertFalse(componentsCss.contains(".ore-theme .mc-appbar:has(.btn_with_tooltip_content:hover),"));
        assertTrue(componentsCss.contains(".ore-theme .mc-appbar {"));
        int popStart = componentsCss.indexOf(".ore-theme .pop {");
        int popEnd = componentsCss.indexOf(".ore-theme .btn_with_tooltip_content", popStart);
        assertTrue(popStart >= 0 && popEnd > popStart);
        String popCss = componentsCss.substring(popStart, popEnd);
        assertTrue(popCss.contains("transform: translateY(20px);"));
        assertTrue(popCss.contains("transition: transform 0.3s ease;"));
        assertFalse(popCss.contains("opacity"));
        assertTrue(popCss.contains(".ore-theme .pop.show { transform: translateY(0); }"));
        assertTrue(componentsCss.contains(".ore-theme .mc-icon {\n"));
        assertTrue(componentsCss.contains("avoid a second fractional scissor"));
        assertTrue(componentsCss.contains("overflow: visible;"));

        String overviewCss = readResource("overview.css");
        assertTrue(overviewCss.contains(".ore-component-case:has(.dropdown_label.open_dropdown)"));
    }

    private static Element assertDisplay(Document document, String tag, String classes, String expected) {
        Element element = document.createElement(tag);
        element.setAttribute("class", classes);
        document.body.appendChild(element);
        assertEquals(expected, element.getComputedStyle().display, classes);
        return element;
    }

    private static void assertTokenDriven(String css, String label) {
        String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder declarations = new StringBuilder();
        boolean customProperty = false;
        for (String line : withoutComments.split("\\R")) {
            String trimmed = line.trim();
            if (!customProperty && trimmed.startsWith("--")) customProperty = true;
            if (customProperty) {
                if (trimmed.contains(";")) customProperty = false;
                continue;
            }
            declarations.append(line).append('\n');
        }
        assertFalse(declarations.toString().matches(
                        "(?s).*\\b[A-Za-z-]+\\s*:[^;{}]*(#[0-9A-Fa-f]{3,8}|rgba?\\([^;)]*\\)).*"),
                label + " consume scoped design tokens");
    }

    private static void assertFontFace(String css, String family, String path, String format) {
        String declaration = "font-family: \"" + family + "\";\n"
                + "  src: url(\"" + path + "\") format(\"" + format + "\");";
        assertTrue(css.contains(declaration), family + " must map to " + path);
    }

    private static byte[] readResourceBytes(String relative) throws Exception {
        try (InputStream input = OreThemeTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + relative)) {
            assertNotNull(input, relative);
            return input.readAllBytes();
        }
    }

    private static String readResource(String relative) throws Exception {
        return new String(readResourceBytes(relative), StandardCharsets.UTF_8);
    }

    private static String readCustomerDemo() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("mcui-oreui-customer-demo.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new AssertionError("missing repository customer demo: mcui-oreui-customer-demo.html");
    }
}
