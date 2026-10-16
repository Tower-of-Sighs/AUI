package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavbarButtonBaselineTest {
    @Test
    void tallButtonDoesNotAddDescentToNavbarListItemLine() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element item = new Element(document, "li");
        item.setAttribute("style", "display: block; width: 120px; font-size: 16px; line-height: 24px;");
        document.body.appendChild(item);

        Element button = new Element(document, "button");
        button.setAttribute("style", "display: inline-block; min-height: 36px; font-size: 12px; line-height: 16px; padding: 0; border: 0;");
        button.appendChild(new TextNode(document, "COPY"));
        item.appendChild(button);

        assertEquals(36, Size.of(button).height(), 0.01);
        assertEquals(36, NormalFlow.computeContentSize(item).height(), 0.01,
                "the 24px parent line strut must fit within the centered 36px button");
        assertEquals(0, Position.getOffset(button).y, 0.01);
    }

    @Test
    void navbarHeightIncludesPaddingAndBorderButNotExtraButtonDescent() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element navbar = new Element(document, "nav");
        navbar.setAttribute("style", "display: flex; width: 300px; padding: 16px 32px; border-bottom: 4px solid black; box-sizing: border-box; align-items: center; font-size: 16px; line-height: 24px;");
        document.body.appendChild(navbar);
        Element list = new Element(document, "ul");
        list.setAttribute("style", "display: flex; align-items: center; padding: 0; margin: 0; font-size: 16px; line-height: 24px;");
        navbar.appendChild(list);
        Element item = new Element(document, "li");
        item.setAttribute("style", "display: block; font-size: 16px; line-height: 24px;");
        list.appendChild(item);
        Element button = new Element(document, "button");
        button.setAttribute("style", "display: inline-block; min-height: 36px; font-size: 12px; line-height: 16px; padding: 0; border: 0;");
        button.appendChild(new TextNode(document, "COPY"));
        item.appendChild(button);

        assertEquals(36, Size.of(button).height(), 0.01);
        assertEquals(36, Size.of(item).height(), 0.01);
        assertEquals(36, Size.of(list).height(), 0.01);
        assertEquals(72, Size.of(navbar).height(), 0.01);
    }
}
