package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.element.Model;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiModelRenderRequest;
import com.sighs.apricityui.spi.AuiModelRenderService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelElementTest {
    @AfterEach
    void resetService() { AuiServices.setModels(null); }

    @Test
    void htmlAndRuntimeTextUpdatesRemainDataRatherThanPaintedText() {
        Element.register(Model.TAG_NAME, (document, tag) -> new Model(document));
        var document = TestDocumentFactory.createDocument();
        Model model = assertInstanceOf(Model.class, HTML.createElement(document, """
                <model type="bakedmodel" yaw="20"> minecraft:zombie </model>
                """));
        assertEquals("minecraft:zombie", model.getModelSpec().source());
        assertEquals(20, model.getModelSpec().yaw());
        assertTrue(Layout.isReplacedElement(model));
        assertTrue(model.getRenderChildNodes().isEmpty());
        assertEquals("", SelectionUnits.computeFlattenedSelectableText(model));
        model.setInnerText("minecraft:creeper");
        assertEquals("minecraft:creeper", model.getInnerText());
        assertEquals("minecraft:creeper", model.getModelSpec().source());
        model.setTextContent("minecraft:skeleton");
        assertEquals("minecraft:skeleton", model.getModelSpec().source());
        assertEquals("minecraft:skeleton", model.getInnerText());
        assertTrue(model.getRenderChildNodes().isEmpty());
    }

    @Test
    void sourceChangesAndDisconnectReleasePreviewsButAnglesReuseThem() {
        AtomicInteger created = new AtomicInteger(), closed = new AtomicInteger();
        AuiServices.setModels(source -> {
            created.incrementAndGet();
            return new AuiModelRenderService.Preview() {
                public void render(AuiModelRenderRequest request) { }
                public void close() { closed.incrementAndGet(); }
            };
        });
        Model model = new Model(TestDocumentFactory.createDocument());
        model.setAttribute("type", "bakedmodel");
        model.setTextContent("minecraft:zombie");
        var first = model.getPreview();
        model.setAttribute("yaw", "90");
        model.setAttribute("flat", "true");
        assertSame(first, model.getPreview());
        assertEquals(1, created.get());
        model.setTextContent("minecraft:creeper");
        assertNotSame(first, model.getPreview());
        assertEquals(1, closed.get());
        model.setAttribute("type", "player");
        model.setTextContent("8667ba71-b85a-4004-af54-457a9734eed7");
        model.getPreview();
        assertEquals(3, created.get());
        assertEquals(2, closed.get());
        model.onDisconnectedFromDocument();
        assertEquals(3, closed.get());
        model.onDisconnectedFromDocument();
        assertEquals(3, closed.get());
    }
}
