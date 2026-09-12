package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleFontWarmupTest {
    @Test
    void localFontFacesLoadBeforeDocumentCreationAndOnlyOncePerGeneration() {
        AbstractAsyncHandler.clearAllAndBumpGeneration();
        Font.prepareReload();
        long before = Font.getMetricsRevision();

        int stylesheets = StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(
                "apricityui/theme/ore/mcui-example.html",
                List.of("ore.css", "mcui.css", "overview.css"),
                List.of(),
                new Size(1920, 1080)
        );
        long afterFirstWarmup = Font.getMetricsRevision();

        StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(
                "apricityui/theme/ore/mcui-example.html",
                List.of("ore.css", "mcui.css", "overview.css"),
                List.of(),
                new Size(1920, 1080)
        );

        assertEquals(4, stylesheets);
        assertTrue(afterFirstWarmup > before);
        assertEquals(afterFirstWarmup, Font.getMetricsRevision());
        assertTrue(Font.isRegistered("Minecraft Seven"));
        assertTrue(Font.isRegistered("Minecraft Ten"));
        assertTrue(Font.isRegistered("NotoSans Bold"));
    }
}
