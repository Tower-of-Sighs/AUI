package com.sighs.apricityui.ui.file;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.ui.FilePicker;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePickerTest {
    @Test
    void filtersExtensionsAndReadOnlyResourcePackEntriesBeforeCompletingSelection() {
        Document document = TestDocumentFactory.createDocument();
        List<Loader.StaticResourceEntry> entries = List.of(
                entry("pack.html", "html", Loader.ResourceLayer.RESOURCE_PACK),
                entry("local.html", "html", Loader.ResourceLayer.LOCAL_FOLDER),
                entry("local.css", "css", Loader.ResourceLayer.LOCAL_FOLDER),
                entry("nested/page.html", "html", Loader.ResourceLayer.DEV_FOLDER)
        );
        try {
            CompletableFuture<Optional<FilePicker.Selection>> result = FilePicker.pickIn(document,
                    new FilePicker.Options("Select HTML", Set.of(".html"), false), entries);

            assertFalse(result.isDone());
            assertNotNull(document.querySelector(".aui-file-picker-title"));
            assertEquals("Select HTML", document.querySelector(".aui-file-picker-title").getTextContent());
            assertEquals(1, document.querySelectorAll(".aui-file-picker-file").size());
            assertEquals(2, document.querySelectorAll(".aui-file-picker-path").size());

            Element local = document.querySelector(".aui-file-picker-file");
            local.click();
            Element selected = document.querySelector(".aui-file-picker-file");
            selected.dispatchEvent(new Event(selected, "dblclick", true));

            Optional<FilePicker.Selection> selection = result.join();
            assertTrue(selection.isPresent());
            assertEquals("local.html", selection.get().path());
            assertEquals(Loader.ResourceLayer.LOCAL_FOLDER, selection.get().layer());
            assertFalse(FilePicker.isOpen());
        } finally {
            FilePicker.closeActive();
            document.remove();
        }
    }

    @Test
    void cancellationCompletesNormallyWithAnEmptyOptional() {
        Document document = TestDocumentFactory.createDocument();
        try {
            CompletableFuture<Optional<FilePicker.Selection>> result = FilePicker.pickIn(document,
                    FilePicker.Options.any("Select", true), List.of(entry("pack.html", "html", Loader.ResourceLayer.RESOURCE_PACK)));
            FilePicker.closeActive();
            assertTrue(result.join().isEmpty());
        } finally {
            FilePicker.closeActive();
            document.remove();
        }
    }

    @Test
    void rendersTranslatedTitleAndSharedLabelsAsTranslationNodes() {
        Document document = TestDocumentFactory.createDocument();
        try {
            FilePicker.pickIn(document, FilePicker.Options.htmlTranslation(
                    "devtools.apricityui.ore_editor.select_html", false), List.of());

            assertEquals("TRANSLATION", document.querySelector(".aui-file-picker-title").children.get(0).tagName);
            assertEquals("devtools.apricityui.ore_editor.select_html",
                    document.querySelector(".aui-file-picker-title").children.get(0).getTextContent());
            assertEquals("TRANSLATION", document.querySelector(".aui-file-picker-cancel").children.get(0).children.get(0).tagName);
            assertEquals("TRANSLATION", document.querySelector(".aui-file-picker-empty").children.get(0).tagName);
            assertEquals("TRANSLATION", document.querySelector(".aui-file-picker-select").children.get(0).children.get(0).tagName);
        } finally {
            FilePicker.closeActive();
            document.remove();
        }
    }

    @Test
    void usesALocalizedTitleWhenTheCallerDoesNotProvideOne() {
        Document document = TestDocumentFactory.createDocument();
        try {
            FilePicker.pickIn(document, FilePicker.Options.any(null, true), List.of());

            Element title = document.querySelector(".aui-file-picker-title");
            assertEquals("TRANSLATION", title.children.get(0).tagName);
            assertEquals("file_picker.apricityui.title", title.children.get(0).getTextContent());
        } finally {
            FilePicker.closeActive();
            document.remove();
        }
    }

    @Test
    void onlyOffersHtmlCreationWhenTheExtensionFilterAllowsHtml() {
        Document document = TestDocumentFactory.createDocument();
        try {
            FilePicker.pickIn(document, FilePicker.Options.html("Select HTML", true), List.of());
            assertNotNull(document.querySelector(".aui-file-picker-create"));

            FilePicker.pickIn(document, new FilePicker.Options("Select CSS", Set.of("css"), true), List.of());
            assertTrue(document.querySelector(".aui-file-picker-create") == null);
        } finally {
            FilePicker.closeActive();
            document.remove();
        }
    }

    private static Loader.StaticResourceEntry entry(String path, String extension, Loader.ResourceLayer layer) {
        return new Loader.StaticResourceEntry(path, extension, layer, "", "", 1L);
    }
}
