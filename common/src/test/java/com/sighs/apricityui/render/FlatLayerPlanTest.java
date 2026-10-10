package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One composite pass now shares a single {@link Base.FlatLayerPlan} instead of
 * re-sorting the registry per document. The plan must hand out exactly what the
 * old per-document {@link Base#resolveFlatDocumentBaseZ(Document)} path computed,
 * or stacking would silently change.
 */
class FlatLayerPlanTest {
    @Test
    void planMatchesPerDocumentResolutionAndSkipsNonFlatDocuments() {
        String backPath = "test://flat-plan-back";
        String middlePath = "test://flat-plan-middle";
        String frontPath = "test://flat-plan-front";
        String manualPath = "test://flat-plan-manual";
        HTML.putTemple(backPath, "<html><body style=\"transform:translateZ(-5px)\"></body></html>");
        HTML.putTemple(middlePath, "<html><body></body></html>");
        HTML.putTemple(frontPath, "<html><body style=\"transform:translateZ(30px)\"></body></html>");
        HTML.putTemple(manualPath, "<html><body></body></html>");

        Document back = Document.create(backPath);
        Document middle = Document.create(middlePath);
        Document front = Document.create(frontPath);
        Document manual = Document.create(manualPath);
        try {
            manual.setManuallyRendered(true);

            Base.FlatLayerPlan plan = Base.planFlatLayers();

            // Flat documents only, back-to-front.
            assertEquals(List.of(back, middle, front), plan.orderedDocuments());
            // The same base Z the per-document path produced. `manual` is not flat, so it
            // also exercises the "not in this plan" fallback of baseZOf.
            for (Document document : List.of(back, middle, front, manual)) {
                assertEquals(Base.resolveFlatDocumentBaseZ(document), plan.baseZOf(document), 0.0f,
                        "base Z differs for " + document.getPath());
            }
            assertEquals(Base.getFlatOverlayZ(), plan.overlayZ(), 0.0f);
        } finally {
            back.remove();
            middle.remove();
            front.remove();
            manual.remove();
        }
    }
}
