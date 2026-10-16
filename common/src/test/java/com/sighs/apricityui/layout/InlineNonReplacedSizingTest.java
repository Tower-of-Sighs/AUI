package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineNonReplacedSizingTest {
    @Test
    void inlineLabelIgnoresSpecifiedWidthHeightAndMinHeightInLineBox() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element plainRow = row(document);
        Element plain = label(document, "display:inline;background-color:#333;");
        plainRow.appendChild(plain);
        Element plainFollowing = followingBlock(document);
        plainRow.appendChild(plainFollowing);

        Element sizedRow = row(document);
        Element sized = label(document, "display:inline;background-color:#333;width:240px;height:80px;min-height:36px;");
        sizedRow.appendChild(sized);
        Element sizedFollowing = followingBlock(document);
        sizedRow.appendChild(sizedFollowing);

        assertEquals(Size.of(plain).width(), Size.of(sized).width(), 0.01);
        assertEquals(Size.of(plain).height(), Size.of(sized).height(), 0.01);
        assertEquals(Position.getOffset(plainFollowing).y, Position.getOffset(sizedFollowing).y, 0.01,
                "inline label dimensions must not stretch the containing line box");
    }

    @Test
    void inlineBlockStillHonorsSpecifiedWidthHeightAndMinHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");
        Element row = row(document);
        Element block = label(document, "display:inline-block;vertical-align:top;width:120px;height:8px;min-height:36px;");
        row.appendChild(block);
        Element following = followingBlock(document);
        row.appendChild(following);

        assertEquals(120, Size.of(block).width(), 0.01);
        assertEquals(36, Size.of(block).height(), 0.01);
        assertTrue(Position.getOffset(following).y >= 36);
    }

    private static Element row(Document document) {
        Element row = document.createElement("div");
        row.setAttribute("style", "width:300px;");
        document.body.appendChild(row);
        return row;
    }

    private static Element label(Document document, String style) {
        Element label = document.createElement("span");
        label.setAttribute("style", style);
        label.appendChild(new TextNode(document, "Label"));
        return label;
    }

    private static Element followingBlock(Document document) {
        Element block = document.createElement("div");
        block.setAttribute("style", "height:4px;");
        return block;
    }
}
