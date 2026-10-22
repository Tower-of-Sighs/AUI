package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerInventoryPresetTest {
    @ParameterizedTest
    @CsvSource({"0,9,1", "9,9,1", "0,9,2", "0,6,3"})
    void autoPlacesInventoryBeforeHotbar(int firstIndex, int columns, int gap) throws Exception {
        var document = TestDocumentFactory.createDocument();
        var rules = new HashMap<String, Map<String, CSS.Declaration>>();
        try (var stream = getClass().getResourceAsStream("/assets/apricityui/apricity/global.css")) {
            assertNotNull(stream);
            CSS.readCSS(new String(stream.readAllBytes(), StandardCharsets.UTF_8), rules, "global.css");
        }
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        document.body.setAttribute("style", "width:600px;height:400px");
        Element container = new Element(document, "container");
        container.setAttribute("layout", "preset:player");
        container.setAttribute("style", "width:170px");
        document.body.appendChild(container);
        Element[] slots = new Element[36];
        for (int i = 0; i < slots.length; i++) {
            int index = (firstIndex + i) % slots.length;
            Element slot = new Element(document, "slot");
            slot.setAttribute("slot-index", String.valueOf(index));
            slots[index] = slot;
            container.appendChild(slot);
        }
        document.commitRenderState();
        assertLayout(container, slots, 9, 1);

        container.getStyle().setProperty("--aui-player-columns", String.valueOf(columns));
        container.getStyle().setProperty("--aui-player-gap", gap + "px");
        container.getStyle().setProperty("width", (columns * 18 + (columns - 1) * gap) + "px");
        document.commitRenderState();
        assertLayout(container, slots, columns, gap);
        for (int i = 0; i < slots.length; i++) {
            assertSame(slots[(firstIndex + i) % slots.length], container.getRenderChildren().get(i));
        }
    }

    private void assertLayout(Element container, Element[] slots, int columns, int gap) {
        for (int index = 0; index < slots.length; index++) {
            int position = index < 9 ? index + 27 : index - 9;
            Element slot = slots[index];
            assertEquals(position % columns * (18 + gap), Position.getOffset(slot).x, .001, "slot " + index + " x");
            assertEquals(position / columns * (18 + gap), Position.getOffset(slot).y, .001, "slot " + index + " y");
            assertEquals(18, Size.of(slot).width(), .001);
            assertEquals(18, Size.of(slot).height(), .001);
        }
        int rows = (slots.length + columns - 1) / columns;
        assertEquals(columns * 18 + (columns - 1) * gap, Size.of(container).width(), .001);
        assertEquals(rows * 18 + (rows - 1) * gap, Size.of(container).height(), .001);
    }
}
