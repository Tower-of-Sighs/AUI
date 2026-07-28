package com.sighs.apricityui.editor.ore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OreEditorTranslationTest {
    private static final Path ENGLISH = Path.of("src/main/resources/assets/apricityui/lang/en_us.json");
    private static final Path CHINESE = Path.of("src/main/resources/assets/apricityui/lang/zh_cn.json");

    @Test
    void editorTranslationKeysAreCompleteAndNonBlankInBothLanguages() throws Exception {
        JsonObject english = JsonParser.parseString(Files.readString(ENGLISH)).getAsJsonObject();
        JsonObject chinese = JsonParser.parseString(Files.readString(CHINESE)).getAsJsonObject();
        Set<String> englishKeys = editorKeys(english);
        Set<String> chineseKeys = editorKeys(chinese);

        assertEquals(englishKeys, chineseKeys);
        for (String key : englishKeys) {
            assertFalse(english.get(key).getAsString().isBlank(), key);
            assertFalse(chinese.get(key).getAsString().isBlank(), key);
        }
    }

    private static Set<String> editorKeys(JsonObject object) {
        return object.keySet().stream()
                .filter(key -> key.startsWith("ore_editor.apricityui.")
                        || key.startsWith("tooltip.apricityui.ore_editor."))
                .collect(Collectors.toUnmodifiableSet());
    }
}
