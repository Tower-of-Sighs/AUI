package com.sighs.apricityui.event;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseEventCommittedGeometryTest {
    @Test
    void hitBoxQueryUsesCommittedGeometryWithoutReenteringLayout() {
        Document document = TestDocumentFactory.createDocument();
        GuardedElement element = new GuardedElement(document);
        element.setAttribute("style", "width:80px;height:30px;margin:4px;");
        document.body.appendChild(element);

        Rect committed = Rect.of(element);
        element.getLayout().commitRect(
                committed,
                element.getLayout().rectDependency(document)
        );
        Position point = new Position(
                committed.position.x + committed.box.getMarginLeft() + 1,
                committed.position.y + committed.box.getMarginTop() + 1
        );
        element.rejectLayout = true;

        assertTrue(MouseEvent.checkCursor(element, point));
    }

    @Test
    void boundingRectUsesTheCommittedSizeSnapshot() {
        Document document = TestDocumentFactory.createDocument();
        GuardedElement element = new GuardedElement(document);
        element.setAttribute("style", "width:80px;height:30px;margin:4px;");
        document.body.appendChild(element);

        Rect committed = Rect.of(element);
        element.rejectLayout = true;

        Element.DOMRect bounds = element.getBoundingClientRect();

        assertEquals(committed.getElementSize().width(), bounds.width);
        assertEquals(committed.getElementSize().height(), bounds.height);
    }

    @Test
    void missingGeometryDoesNotFallBackToLayout() {
        Document document = TestDocumentFactory.createDocument();
        GuardedElement element = new GuardedElement(document);
        document.body.appendChild(element);
        element.rejectLayout = true;
        assertFalse(MouseEvent.checkCursor(element, new Position(1, 1)));
    }

    @Test
    void clickImmediatelyAfterReplacementUsesTheNewPaintTree() {
        String path = "test://click-replacement";
        com.sighs.apricityui.parser.HTML.putTemple(path, """
                <html><head><meta name="aui-viewport" content="mode=fixed,width=200,height=200"></head>
                <body style="margin:0"><div id="first" style="width:80px;height:30px"></div></body></html>
                """);
        Document document = Document.create(path);
        try {
            Element first = document.getElementById("first");
            assertSame(first, document.hitTest(new Position(10, 10)), "initial click");

            Element replacement = document.createElement("div");
            replacement.setAttribute("style", "width:80px;height:30px");
            document.body.replaceChildren(replacement);
            assertSame(replacement, document.hitTest(new Position(10, 10)), "click before another tick");
        } finally {
            document.remove();
        }
    }

    private static final class GuardedElement extends Element {
        private boolean rejectLayout;

        private GuardedElement(Document document) {
            super(document, "guarded");
        }

        @Override
        public Style getComputedStyle() {
            if (rejectLayout) throw new AssertionError("committed hit geometry must not trigger layout");
            return super.getComputedStyle();
        }
    }
}
