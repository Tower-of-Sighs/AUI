package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.init.Document;

public final class TestDocumentFactory {
    private TestDocumentFactory() {
    }

    public static Document createDocument() {
        Document document = new Document("test://doc", false);
        document.documentElement = new Html(document);
        document.head = new Head(document);
        document.body = new Body(document);
        document.documentElement.appendChild(document.head);
        document.documentElement.appendChild(document.body);
        return document;
    }
}
