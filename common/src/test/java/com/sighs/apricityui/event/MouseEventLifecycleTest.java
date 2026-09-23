package com.sighs.apricityui.event;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.viewport.ApricityViewport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseEventLifecycleTest {
    @ParameterizedTest
    @ValueSource(strings = {"mousedown", "mouseup"})
    void closingDocumentDuringDispatchStillConsumesNativeInput(String type) throws Exception {
        String path = "test://mouse-close-" + type;
        HTML.putTemple(path, """
                <html><head><meta name="aui-mouse-events" content="intercept"></head>
                <body><div id="close" style="position:fixed;left:0;top:0;width:80px;height:80px"></div></body></html>
                """);
        Document document = Document.create(path);
        try {
            var viewport = Document.class.getDeclaredField("viewport");
            viewport.setAccessible(true);
            viewport.set(document, new ApricityViewport(200, 100, 1.0f, 1.0d));
            document.tickFrame();
            Position point = new Position(20, 20);
            assertTrue(document.interceptsMouseEventsAt(point));
            document.getElementById("close").addEventListener(type, event -> document.remove());

            MouseEvent event = new MouseEvent(type, point, 0, false);
            MouseEvent.tiggerEvent(event, document);

            assertTrue(document.isDisposed());
            assertTrue(event.isNativeConsumed());
        } finally {
            document.remove();
        }
    }
}
