package com.sighs.apricityui.dev.debug;

import com.google.gson.JsonObject;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;

final class DebugInput {
    private DebugInput() {
    }

    static JsonObject hover(Document document, Element element) {
        Position screen = actionableCenter(document, element, false);
        MouseEvent.tiggerEvent(mouseEvent("mousemove", screen, -1, 0), document);
        return pointResult(screen);
    }

    static JsonObject click(Document document, Element element) {
        Position screen = actionableCenter(document, element, true);
        MouseEvent.tiggerEvent(mouseEvent("mousemove", screen, -1, 0), document);
        MouseEvent.tiggerEvent(mouseEvent("mousedown", screen, 0, 1), document);
        MouseEvent.tiggerEvent(mouseEvent("mouseup", screen, 0, 0), document);
        return pointResult(screen);
    }

    static JsonObject fill(Element element, String value) {
        if (!(element instanceof AbstractText text) || !text.canEditText()) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE,
                    "Element is not an editable input or textarea");
        }
        if (element.isDisabled()) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE, "Element is disabled");
        }
        text.focus();
        text.selectAll();
        text.replaceSelection(value == null ? "" : value);
        JsonObject result = new JsonObject();
        result.addProperty("value", text.getValue());
        return result;
    }

    private static Position actionableCenter(Document document, Element element, boolean requirePointerTarget) {
        if (!Interaction.isDisplayed(element) || !Interaction.isVisible(element) || !element.isVisible) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE, "Element is not visible");
        }
        if (requirePointerTarget && !element.isPointerEnabled) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE,
                    "Element does not accept pointer events");
        }

        Element.DOMRect rect = element.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE, "Element has no rendered size");
        }
        Position documentPosition = new Position(rect.x + rect.width / 2.0d, rect.y + rect.height / 2.0d);
        Element hit = document.hitTest(documentPosition);
        if (requirePointerTarget && (hit == null || !element.contains(hit))) {
            throw new DebugProtocolException(DebugProtocolException.NOT_ACTIONABLE,
                    "Element center is covered or outside the hit-test region");
        }
        return document.documentToScreenPosition(documentPosition);
    }

    private static MouseEvent mouseEvent(String type, Position position, int button, int buttons) {
        MouseEvent event = new MouseEvent(type, position, button, false);
        event.buttons = buttons;
        return event;
    }

    private static JsonObject pointResult(Position position) {
        JsonObject point = new JsonObject();
        point.addProperty("x", position.x);
        point.addProperty("y", position.y);
        JsonObject result = new JsonObject();
        result.add("point", point);
        return result;
    }
}
