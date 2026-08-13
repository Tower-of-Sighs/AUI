package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InnerTextTest {
    @Test
    void detachedAndDisplayNoneElementsFallBackToRawTextContent() {
        Document document = TestDocumentFactory.createDocument();
        Element detached = document.createElement("div");
        detached.appendChild(document.createTextNode("  alpha  "));
        Element hidden = document.createElement("span");
        hidden.setAttribute("style", "display:none");
        hidden.appendChild(document.createTextNode("hidden"));
        detached.appendChild(hidden);

        assertEquals("  alpha  hidden", detached.getInnerText());

        document.body.appendChild(detached);
        detached.setAttribute("style", "display:none");
        assertEquals("  alpha  hidden", detached.getInnerText());
    }

    @Test
    void getterUsesRenderedWhitespaceVisibilityBreaksAndBlocks() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        document.body.appendChild(host);
        host.appendChild(document.createTextNode("  alpha  "));

        Element inline = document.createElement("span");
        inline.appendChild(document.createTextNode(" beta "));
        host.appendChild(inline);

        Element hidden = document.createElement("span");
        hidden.setAttribute("style", "display:none");
        hidden.appendChild(document.createTextNode(" secret "));
        host.appendChild(hidden);

        host.appendChild(document.createElement("br"));
        host.appendChild(document.createTextNode(" gamma "));
        Element block = document.createElement("div");
        block.appendChild(document.createTextNode(" delta "));
        host.appendChild(block);
        host.appendChild(document.createTextNode(" tail "));

        assertEquals("alpha beta\ngamma\ndelta\ntail", host.getInnerText());
        assertEquals("  alpha   beta  secret  gamma  delta  tail ", host.getTextContent());
    }

    @Test
    void visibilityHiddenStillAllowsExplicitlyVisibleDescendants() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        host.setAttribute("style", "visibility:hidden");
        host.appendChild(document.createTextNode("hidden"));
        Element visible = document.createElement("span");
        visible.setAttribute("style", "visibility:visible");
        visible.appendChild(document.createTextNode("shown"));
        host.appendChild(visible);
        document.body.appendChild(host);

        assertEquals("shown", host.getInnerText());
    }

    @Test
    void paragraphsAndIndependentInlineContextsUseBrowserBoundaries() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        document.body.appendChild(host);
        host.appendChild(document.createTextNode("start "));

        Element inlineBlock = document.createElement("span");
        inlineBlock.setAttribute("style", "display:inline-block");
        inlineBlock.appendChild(document.createTextNode(" middle "));
        host.appendChild(inlineBlock);
        host.appendChild(document.createTextNode(" end"));

        Element first = document.createElement("p");
        first.appendChild(document.createTextNode("one"));
        Element second = document.createElement("p");
        second.appendChild(document.createTextNode("two"));
        host.appendChild(first);
        host.appendChild(second);

        assertEquals("start middle end\n\none\n\ntwo", host.getInnerText());
    }

    @Test
    void getterPreservesPreWhitespaceAndAppliesTextTransform() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        document.body.appendChild(host);
        Element pre = document.createElement("pre");
        pre.setAttribute("style", "text-transform:uppercase");
        pre.appendChild(document.createTextNode(" a\r\nb  "));
        host.appendChild(pre);

        assertEquals(" A\nB  ", host.getInnerText());
        assertEquals("uppercase", pre.getComputedStyle().textTransform);
        assertEquals(" A\nB  ", Text.of(pre).content);
    }

    @Test
    void replacedAndIndependentInlineBoxesPreserveTheirTextBoundaries() {
        Document document = TestDocumentFactory.createDocument();

        assertInnerText(document, "<div>abc <input> def</div>", "abc  def");
        assertInnerText(document, "<div><img> abc</div>", " abc");
        assertInnerText(document, "<div>abc <img></div>", "abc ");
        assertInnerText(document, "<div>abc <span style='display:inline-block'></span> def</div>", "abc  def");
        assertInnerText(document, "<div>abc <img style='display:block'> def</div>", "abc\ndef");
    }

    @Test
    void displayContentsFlexGridAndDetailsFollowRenderedStructure() {
        Document document = TestDocumentFactory.createDocument();

        assertInnerText(document, "<div>one<span style='display:contents'> two</span></div>", "one two");
        assertInnerText(document, "<div style='display:flex'><span>one</span><span>two</span></div>", "one\ntwo");
        assertInnerText(document, "<div style='display:grid'><span>one</span><span>two</span></div>", "one\ntwo");
        assertInnerText(document, "<div><details><summary>title</summary>body</details></div>", "title");
        assertInnerText(document, "<div><details open><summary>title</summary>body</details></div>", "title\nbody");
    }

    @Test
    void replacedRootsHrAndParagraphDisplayKeepElementSpecificSemantics() {
        Document document = TestDocumentFactory.createDocument();

        assertInnerText(document, "<textarea>fallback</textarea>", "");
        assertInnerText(document, "<canvas>fallback</canvas>", "");
        assertInnerText(document, "<div>abc<hr>def</div>", "abc\ndef");
        assertInnerText(document, "<div>abc<p style='display:inline-block'>def</p>ghi</div>", "abc\n\ndef\n\nghi");

        Element host = document.createElement("div");
        Element hr = document.createElement("hr");
        hr.setTextContent("forced");
        host.appendChild(hr);
        document.body.appendChild(host);
        assertEquals("forced", host.getInnerText());
    }

    @Test
    void tableCellsAndRowsUseTabsAndNewlines() {
        Document document = TestDocumentFactory.createDocument();
        Element table = document.createElement("table");
        document.body.appendChild(table);
        for (String[] values : new String[][]{{"a", "b"}, {"c", "d"}}) {
            Element row = document.createElement("tr");
            table.appendChild(row);
            for (String value : values) {
                Element cell = document.createElement("td");
                cell.appendChild(document.createTextNode(value));
                row.appendChild(cell);
            }
        }

        assertEquals("a\tb\nc\td", table.getInnerText());
        assertEquals("a\tb", table.children.get(0).getInnerText());
        assertEquals("a", table.children.get(0).children.get(0).getInnerText());
    }

    @Test
    void inlineTableKeepsInternalSeparatorsWithoutOuterBlockBreaks() {
        Document document = TestDocumentFactory.createDocument();

        assertInnerText(document,
                "<div>before<table style='display:inline-table'><tr><td>a</td><td>b</td></tr>"
                        + "<tr><td>c</td><td>d</td></tr></table>after</div>",
                "beforea\tb\nc\tdafter");
    }

    @Test
    void setterReplacesChildrenAndTurnsEveryLineBreakIntoBr() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        document.body.appendChild(host);
        host.setInnerHTML("<span>old</span>");

        host.setInnerText("a\r\nb\rc\n");

        assertEquals("a<br>b<br>c<br>", host.getInnerHTML());
        assertEquals("abc", host.getTextContent());
        assertEquals("a\nb\nc\n", host.getInnerText());
    }

    @Test
    void rhinoRewriteKeepsInnerTextAsAnIndependentProperty() {
        String source = "node.innerText = node.textContent;";
        String rewritten = JS.rewriteForRhino(source);

        assertEquals(source, rewritten);
        assertFalse(rewritten.equals("node.textContent = node.textContent;"));
    }

    private static void assertInnerText(Document document, String html, String expected) {
        Element element = document.createHTML(html);
        document.body.appendChild(element);
        assertEquals(expected, element.getInnerText());
        element.remove();
    }
}
