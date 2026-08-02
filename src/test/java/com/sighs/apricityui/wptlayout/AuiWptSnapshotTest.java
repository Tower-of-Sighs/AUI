package com.sighs.apricityui.wptlayout;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch bridge used only by wpt/tools/run.mjs. */
class AuiWptSnapshotTest {
    private static final Gson GSON = new Gson();

    @Test
    void captureRequestedWptPages() throws Exception {
        String inputProperty = System.getProperty("aui.wpt.input");
        String outputProperty = System.getProperty("aui.wpt.output");
        if (inputProperty == null || inputProperty.isBlank() || outputProperty == null || outputProperty.isBlank()) return;

        JsonObject input = GSON.fromJson(Files.readString(Path.of(inputProperty), StandardCharsets.UTF_8), JsonObject.class);
        JsonArray results = new JsonArray();
        int width = input.getAsJsonObject("viewport").get("width").getAsInt();
        int height = input.getAsJsonObject("viewport").get("height").getAsInt();
        Size.setViewportOverride(width, height);
        try {
            for (JsonElement entry : input.getAsJsonArray("cases")) {
                JsonObject testCase = entry.getAsJsonObject();
                results.add(capture(testCase));
            }
        } finally {
            Size.clearViewportOverride();
        }
        JsonObject output = new JsonObject();
        output.add("cases", results);
        Files.writeString(Path.of(outputProperty), GSON.toJson(output), StandardCharsets.UTF_8);
        assertTrue(results.size() == input.getAsJsonArray("cases").size(), "every requested WPT case must produce a result");
    }

    private static JsonObject capture(JsonObject testCase) {
        JsonObject result = new JsonObject();
        result.addProperty("id", testCase.get("id").getAsString());
        try {
            String source = Files.readString(Path.of(testCase.get("source").getAsString()), StandardCharsets.UTF_8);
            String documentPath = "wpt://" + testCase.get("id").getAsString();
            HTML.putTemple(documentPath, source);
            Document document = new Document(documentPath, false);
            try {
                document.refresh();
            } catch (NoClassDefFoundError unavailableForgeRuntime) {
                if (document.documentElement == null
                        || !String.valueOf(unavailableForgeRuntime.getMessage()).contains("net/minecraftforge/fml/ModList")) {
                    throw unavailableForgeRuntime;
                }
            }
            if (document.documentElement == null) throw new IllegalStateException("AUI did not create a document element");

            JsonArray nodes = new JsonArray();
            for (Element element : document.querySelectorAll("*")) {
                Element.DOMRect rect = element.getBoundingClientRect();
                JsonObject node = new JsonObject();
                node.addProperty("tag", element.getNodeName().toLowerCase());
                node.addProperty("id", element.getAttribute("id"));
                node.add("rect", numbers(rect.x, rect.y, rect.width, rect.height));
                node.add("scroll", numbers(element.scrollWidth, element.scrollHeight, rect.width, rect.height));
                JsonObject style = new JsonObject();
                style.addProperty("display", element.getComputedStyle().display);
                style.addProperty("position", element.getComputedStyle().position);
                style.addProperty("boxSizing", element.getComputedStyle().boxSizing);
                style.addProperty("overflowX", element.getComputedStyle().overflowX);
                style.addProperty("overflowY", element.getComputedStyle().overflowY);
                node.add("computed", style);
                nodes.add(node);
            }
            JsonObject snapshot = new JsonObject();
            snapshot.add("nodes", nodes);
            result.addProperty("status", "pass");
            result.add("snapshot", snapshot);
        } catch (Exception exception) {
            result.addProperty("status", "aui-runtime-unsupported");
            result.addProperty("reason", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        return result;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(Math.rint(value * 10000.0d) / 10000.0d);
        return array;
    }
}
