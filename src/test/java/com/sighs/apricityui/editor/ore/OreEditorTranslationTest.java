package com.sighs.apricityui.editor.ore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OreEditorTranslationTest {
    private static final Path ENGLISH = Path.of("src/main/resources/assets/apricityui/lang/en_us.json");
    private static final Path CHINESE = Path.of("src/main/resources/assets/apricityui/lang/zh_cn.json");
    private static final Path SHELL = Path.of("src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.html");
    private static final Pattern DIRECT_SHELL_TEXT = Pattern.compile(
            "(?is)<(?!translation\\b)[^>]+>\\s*([^<\\s][^<]*)\\s*</");

    @Test
    void editorAndFilePickerTranslationKeysAreCompleteAndNonBlankInBothLanguages() throws Exception {
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

    @Test
    void staticEditorShellHasNoHardcodedUserFacingTextOrAccessibilityLabel() throws Exception {
        String shell = Files.readString(SHELL);

        assertFalse(DIRECT_SHELL_TEXT.matcher(shell).find(), "static editor shell contains direct text");
        assertFalse(shell.matches("(?is).*\\saria-label\\s*=.*"), "accessibility labels must be translation-key driven");
    }

    private static Set<String> editorKeys(JsonObject object) {
        return object.keySet().stream()
                .filter(key -> key.startsWith("ore_editor.apricityui.")
                        || key.startsWith("tooltip.apricityui.ore_editor.")
                        || key.startsWith("file_picker.apricityui."))
                .collect(Collectors.toUnmodifiableSet());
    }
}
