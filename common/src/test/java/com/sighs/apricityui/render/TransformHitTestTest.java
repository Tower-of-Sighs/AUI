package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransformHitTestTest {
    @Test
    void centeredTransformedModalHitsItsVisualButtonPosition() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:800px;height:600px;");

        Element overlay = document.createElement("div");
        overlay.setAttribute("style", "position:fixed;left:0;top:0;width:800px;height:600px;z-index:10;");
        Element modal = document.createElement("div");
        modal.setAttribute("style", "position:fixed;left:50%;top:50%;width:200px;height:100px;"
                + "transform:translate(-50%,-50%);z-index:11;");
        Element content = document.createElement("div");
        content.setAttribute("style", "position:relative;width:200px;height:100px;overflow:hidden;");
        Element button = document.createElement("button");
        button.setAttribute("style", "position:absolute;left:10px;top:10px;width:80px;height:40px;");

        content.appendChild(button);
        modal.appendChild(content);
        document.body.appendChild(overlay);
        document.body.appendChild(modal);
        document.commitRenderState();

        Rect buttonRect = button.getRenderer().getCommittedRect();
        double rawX = buttonRect.position.x + buttonRect.getElementSize().width() / 2.0;
        double rawY = buttonRect.position.y + buttonRect.getElementSize().height() / 2.0;
        Rect modalRect = modal.getRenderer().getCommittedRect();
        double visualX = rawX - modalRect.getElementSize().width() / 2.0;
        double visualY = rawY - modalRect.getElementSize().height() / 2.0;

        assertNotEquals(rawX, visualX, 0.001);
        assertNotEquals(rawY, visualY, 0.001);
        assertSame(button, document.hitTest(new Position(visualX, visualY)));

        MouseEvent.tiggerEvent(new MouseEvent("mousemove", new Position(visualX, visualY), -1, false), document);
        assertSame(button, document.hitTest(new Position(visualX, visualY)),
                "Hover style invalidation must not drop the ancestor world transform from hit testing");

        button.getRenderer().clearCommittedWorldTransformSubtree();
        document.markHitTestDirtyAll();
        assertSame(button, document.hitTest(new Position(visualX, visualY)),
                "Hit testing must recompute a valid world transform instead of falling back to raw layout coordinates");

        AtomicInteger clicks = new AtomicInteger();
        button.addEventListener("click", ignored -> clicks.incrementAndGet());
        MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(visualX, visualY), 0, false), document);
        MouseEvent.tiggerEvent(new MouseEvent("mouseup", new Position(visualX, visualY), 0, false), document);
        assertEquals(1, clicks.get());
    }

    @Test
    void newlyShownTransformedModalCommitsBeforeHitTesting() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:800px;height:600px;");

        Element modal = document.createElement("div");
        modal.setAttribute("style", "display:none;position:fixed;left:50%;top:50%;width:200px;height:100px;"
                + "transform:translate(-50%,-50%);z-index:11;");
        Element content = document.createElement("div");
        content.setAttribute("style", "position:relative;width:200px;height:100px;overflow:hidden;");
        Element button = document.createElement("button");
        button.setAttribute("style", "position:absolute;left:10px;top:10px;width:80px;height:40px;");
        content.appendChild(button);
        modal.appendChild(content);
        document.body.appendChild(modal);
        document.commitRenderState();

        modal.setAttribute("style", "display:block;position:fixed;left:50%;top:50%;width:200px;height:100px;"
                + "transform:translate(-50%,-50%);z-index:11;");
        document.flushPendingStyleUpdates();
        document.commitRenderState();

        Rect buttonRect = button.getRenderer().getCommittedRect();
        Rect modalRect = modal.getRenderer().getCommittedRect();
        double visualX = buttonRect.position.x + buttonRect.getElementSize().width() / 2.0
                - modalRect.getElementSize().width() / 2.0;
        double visualY = buttonRect.position.y + buttonRect.getElementSize().height() / 2.0
                - modalRect.getElementSize().height() / 2.0;
        assertSame(button, document.hitTest(new Position(visualX, visualY)));
    }

    @Test
    void nativeControlKeepsActivationWhenPressedStyleMovesItWithinClickSlop() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:800px;height:600px;");

        Element overlay = document.createElement("div");
        overlay.setAttribute("style", "position:fixed;left:0;top:0;width:800px;height:600px;z-index:10;");
        Element button = document.createElement("button");
        button.setAttribute("style", "position:fixed;left:100px;top:100px;width:160px;height:40px;z-index:11;");
        document.body.appendChild(overlay);
        document.body.appendChild(button);
        document.commitRenderState();

        Position pointer = new Position(180.0, 101.0);
        assertSame(button, document.hitTest(pointer));

        AtomicInteger clicks = new AtomicInteger();
        button.addEventListener("mousedown", ignored -> button.setAttribute("style",
                "position:fixed;left:100px;top:104px;width:160px;height:36px;z-index:11;"));
        button.addEventListener("click", ignored -> clicks.incrementAndGet());

        MouseEvent.tiggerEvent(new MouseEvent("mousedown", pointer, 0, false), document);
        assertSame(overlay, document.hitTest(pointer));
        MouseEvent.tiggerEvent(new MouseEvent("mouseup", pointer, 0, false), document);

        assertEquals(1, clicks.get());
    }
}
