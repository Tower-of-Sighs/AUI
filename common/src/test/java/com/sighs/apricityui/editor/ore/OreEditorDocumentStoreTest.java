package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.persistence.OreEditorDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorDocumentStoreTest {
    @TempDir Path directory;

    @Test
    void storeWritesOnlyItsFixedProjectAndExportTargets() throws Exception {
        OreEditorDocumentStore store = new OreEditorDocumentStore(directory);
        OreEditorDocumentStore.Result project = store.saveProject("{\"format\":\"ore-editor-project\"}");
        OreEditorDocumentStore.Result html = store.exportHtml("<html></html>");

        assertTrue(project.success());
        assertTrue(html.success());
        assertTrue(project.file().startsWith(directory));
        assertTrue(html.file().startsWith(directory));
        assertEquals("<html></html>", Files.readString(html.file()));
        assertEquals("{\"format\":\"ore-editor-project\"}", store.readProject().content());
    }
}
