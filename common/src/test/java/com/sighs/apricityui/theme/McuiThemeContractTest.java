package com.sighs.apricityui.theme;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class McuiThemeContractTest {
    private static final Path THEME_ROOT = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme");

    @Test
    void everyOreSelectorAndTokenHasAMcuiCounterpart() throws Exception {
        Map<String, Map<String, CSS.Declaration>> ore = rules("ore/ore.css");
        Map<String, Map<String, CSS.Declaration>> mcui = rules("mcui/mcui.css");
        assertFalse(ore.isEmpty());
        assertEquals(schema(ore), schema(mcui, "mcui", "ore"),
                "McUI must implement every shared component selector and declaration");

        Map<String, CSS.Declaration> oreRoot = ore.get(".ore-theme");
        Map<String, CSS.Declaration> mcuiRoot = mcui.get(".mcui-theme");
        assertNotNull(oreRoot);
        assertNotNull(mcuiRoot);
        Set<String> oreTokens = oreRoot.keySet().stream().filter(name -> name.startsWith("--"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> mcuiTokens = mcuiRoot.keySet().stream().filter(name -> name.startsWith("--"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(oreTokens, mcuiTokens, "switching themes must not lose shared tokens");
        for (String token : oreTokens) {
            if (token.startsWith("--ore-")) {
                assertEquals(oreRoot.get(token).value(), mcuiRoot.get(token).value(),
                        "frozen legacy alias changed: " + token);
            }
        }
    }

    @Test
    void sharedMarkupResolvesThroughTheSelectedRootClass() throws Exception {
        Map<String, Map<String, CSS.Declaration>> cache = rules("ore/ore.css");
        CSS.readCSS(Files.readString(THEME_ROOT.resolve("mcui/mcui.css")), cache, "mcui/mcui.css");
        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element card = document.createElement("div");
        card.setAttribute("class", "card");
        document.body.appendChild(card);
        Element button = document.createElement("button");
        button.setAttribute("class", "button button-primary");
        document.body.appendChild(button);

        document.body.setAttribute("class", "ore-theme");
        document.flushPendingStyleUpdates();
        assertEquals("#48494a", card.getComputedStyle().backgroundColor);
        document.body.setAttribute("class", "mcui-theme");
        document.flushPendingStyleUpdates();
        assertEquals("#58585a", card.getComputedStyle().backgroundColor);
        assertEquals(document.body.getComputedStyle().getPropertyValue("--green"),
                button.getComputedStyle().backgroundColor);
    }

    private static Map<String, Map<String, CSS.Declaration>> rules(String relativePath) throws Exception {
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(Files.readString(THEME_ROOT.resolve(relativePath)), cache, relativePath);
        return cache;
    }

    private static Map<String, Set<String>> schema(Map<String, Map<String, CSS.Declaration>> rules) {
        return schema(rules, "", "");
    }

    private static Map<String, Set<String>> schema(Map<String, Map<String, CSS.Declaration>> rules,
                                                    String from, String to) {
        Map<String, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, Map<String, CSS.Declaration>> rule : rules.entrySet()) {
            result.put(rule.getKey().replace(from, to), new TreeSet<>(rule.getValue().keySet()));
        }
        return result;
    }
}
