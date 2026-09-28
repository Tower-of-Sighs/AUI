package com.sighs.apricityui.event;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.viewport.ApricityViewport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseEventLifecycleTest {
    @Test
    void preventedMouseDownKeepsAutocompleteOptionClickable() throws Exception {
        String path = "test://mouse-prevent-focus";
        HTML.putTemple(path, """
                <html><head><meta name="aui-mouse-events" content="intercept"></head>
                <body>
                  <input id="search" style="position:absolute;left:0;top:0;width:100px;height:24px">
                  <div id="option" style="position:absolute;left:20px;top:48px;width:80px;height:24px">Survival</div>
                  <div id="outside" style="position:absolute;left:120px;top:48px;width:60px;height:24px"></div>
                </body></html>
                """);
        Document document = Document.create(path);
        try {
            var viewport = Document.class.getDeclaredField("viewport");
            viewport.setAccessible(true);
            viewport.set(document, new ApricityViewport(200, 100, 1.0f, 1.0d));
            document.tickFrame();

            Element search = document.getElementById("search");
            Element option = document.getElementById("option");
            AtomicInteger blurCount = new AtomicInteger();
            AtomicInteger clickCount = new AtomicInteger();
            AtomicInteger downCount = new AtomicInteger();
            search.addEventListener("blur", event -> {
                blurCount.incrementAndGet();
                option.remove();
            });
            option.addEventListener("mousedown", event -> {
                downCount.incrementAndGet();
                event.preventDefault();
            });
            option.addEventListener("click", event -> clickCount.incrementAndGet());
            search.focus();

            Position optionPoint = new Position(40, 60);
            assertSame(option, document.hitTest(optionPoint));
            MouseEvent down = new MouseEvent("mousedown", optionPoint, 0, false);
            MouseEvent.tiggerEvent(down, document);
            assertEquals(1, downCount.get(), "the option must receive mousedown");
            assertTrue(down.defaultPrevented, "the option must cancel the mousedown default action");
            assertSame(search, document.getFocusedElement(), "prevented mousedown must retain input focus");
            assertEquals(0, blurCount.get());
            assertTrue(option.isConnected());
            MouseEvent.tiggerEvent(new MouseEvent("mouseup", optionPoint, 0, false), document);

            assertEquals(1, clickCount.get(), "prevented mousedown must not remove the option before click");
            assertEquals(0, blurCount.get());
            assertSame(search, document.getFocusedElement());

            Position outsidePoint = new Position(140, 60);
            MouseEvent.tiggerEvent(new MouseEvent("mousedown", outsidePoint, 0, false), document);
            assertEquals(1, blurCount.get(), "ordinary mousedown must still blur the input");
            assertNull(document.getFocusedElement());
        } finally {
            document.remove();
        }
    }

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
