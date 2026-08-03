package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlExporter;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlImporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OreEditorHtmlImporterTest {
    @Test
    void importsEditableBodyStructureStylesAndThemeVariables() {
        OreEditorProject project = new OreEditorHtmlImporter().read("""
                <!DOCTYPE html><html><body style="--ore-purple:#123456">
                  <section id="main" class="shell" data-page="home" style="display:flex;flex-direction:column;gap:12px;padding:4px">
                    <button class="primary" data-action="build" aria-label="Build project" onclick="ignored()" style="color:#fff;position:absolute;left:8px">Build</button>
                  </div>
                </body></html>
                """);

        assertEquals("#123456", project.theme().get("--ore-purple"));
        OreContainerNode container = assertInstanceOf(OreContainerNode.class, project.root().children().get(0));
        assertEquals("section", container.tag());
        assertEquals("main", container.attributes().get("id"));
        assertEquals("shell", container.attributes().get("class"));
        assertEquals("home", container.attributes().get("data-page"));
        assertEquals("column", container.flex().direction());
        assertEquals("12px", container.flex().gap());
        assertEquals("4px", container.style().get("padding"));
        OreComponentNode button = assertInstanceOf(OreComponentNode.class, container.children().get(0));
        assertEquals("button", button.type());
        assertEquals("primary", button.attributes().get("class"));
        assertEquals("build", button.attributes().get("data-action"));
        assertEquals("Build project", button.attributes().get("aria-label"));
        assertEquals(null, button.attributes().get("onclick"));
        assertEquals("Build", button.content().trim());
        assertEquals("#fff", button.style().get("color"));
        assertEquals("8px", button.style().get("left"));
        assertEquals(true, button.absolute());
    }

    @Test
    void preservesDocumentEnvelopeWhenExportingAnOpenedHtmlFile() {
        OreEditorProject project = new OreEditorHtmlImporter().read("""
                <!doctype html><html lang="zh-CN" data-site="ore"><head><title>My page</title><script>window.keepHead = true;</script></head>
                <body class="site-page" data-page="home" style="background:#111"><a href="/build" data-action="build">Build</a><script>window.keepBody = true;</script></body></html>
                """);

        String html = new OreEditorHtmlExporter().export(project);

        assertEquals("zh-CN", project.documentMetadata().htmlAttributes().get("lang"));
        assertEquals("site-page", project.documentMetadata().bodyAttributes().get("class"));
        assertEquals("<!doctype html>", project.documentMetadata().doctype());
        assertEquals(true, html.contains("<title>My page</title>"));
        assertEquals(true, html.contains("window.keepHead = true;"));
        assertEquals(true, html.contains("window.keepBody = true;"));
        assertEquals(true, html.contains("<html lang=\"zh-CN\" data-site=\"ore\">"));
        assertEquals(true, html.contains("<body data-page=\"home\" class=\"site-page ore-theme\" style=\"background:#111;"));
        assertEquals(true, html.contains("href=\"/build\""));
    }
}
