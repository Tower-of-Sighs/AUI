package com.sighs.apricityui.init;

import com.sighs.apricityui.init.Window.IntersectionEntryData;
import com.sighs.apricityui.init.Window.IntersectionObserverEngine;
import com.sighs.apricityui.init.Window.IntersectionOptions;
import com.sighs.apricityui.init.Window.IntersectionRect;
import com.sighs.apricityui.init.Window.IntersectionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionObserverEngineTest {
    private static final IntersectionRect ROOT = new IntersectionRect(0, 0, 100, 100);

    @Test
    void intersectionsPreserveBoundaryContactsAndDistinguishSeparatedRects() {
        IntersectionRect.Intersection contact = IntersectionRect.intersect(
                new IntersectionRect(0, 0, 100, 100),
                new IntersectionRect(100, 20, 20, 30)
        );

        assertTrue(contact.intersects());
        assertEquals(new IntersectionRect(100, 20, 0, 30), contact.rect());

        IntersectionRect.Intersection separated = IntersectionRect.intersect(
                new IntersectionRect(0, 0, 10, 10),
                new IntersectionRect(11, 0, 10, 10)
        );

        assertFalse(separated.intersects());
        assertEquals(IntersectionRect.ZERO, separated.rect());
    }

    @Test
    void normalizesRootMarginsAndThresholdsUsingRootWidthForAllPercentages() {
        IntersectionOptions options = new IntersectionOptions(
                "10% 20px 30% 40px",
                List.of(0.75, 0.0, 0.75, 0.25)
        );

        assertEquals("10% 20px 30% 40px", options.rootMargin());
        assertEquals(List.of(0.0, 0.25, 0.75), options.thresholds());
        assertEquals(new IntersectionRect(-40, -10, 160, 240),
                options.expandRootBounds(new IntersectionRect(0, 0, 100, 200)));
        assertEquals("12px 5% 12px 5%", new IntersectionOptions("12px 5%", List.of()).rootMargin());
        assertEquals(List.of(0.0), new IntersectionOptions("0px", List.of()).thresholds());
    }

    @Test
    void rejectsInvalidRootMarginsAndThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new IntersectionOptions("10em", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IntersectionOptions("1px 2px 3px 4px 5px", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IntersectionOptions("0px", List.of(-0.1)));
        assertThrows(IllegalArgumentException.class, () -> new IntersectionOptions("0px", List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> new IntersectionOptions("0px", List.of(1.1)));
    }

    @Test
    void queuesInitialEntryOnlyOnceForStableGeometryAndObserveIsIdempotent() {
        Object target = new Object();
        IntersectionObserverEngine<Object> engine = new IntersectionObserverEngine<>(
                new IntersectionOptions("0px", List.of(0.0, 0.5, 1.0))
        );

        engine.observe(target);
        engine.observe(target);
        engine.evaluate(observed -> snapshot(observed, 10, new IntersectionRect(0, 0, 50, 50)));

        List<IntersectionEntryData<Object>> initialEntries = engine.takeRecords();
        assertEquals(1, initialEntries.size());
        assertTrue(initialEntries.get(0).isIntersecting());
        assertEquals(1.0, initialEntries.get(0).intersectionRatio(), 0.00001);

        engine.evaluate(observed -> snapshot(observed, 20, new IntersectionRect(0, 0, 50, 50)));
        assertTrue(engine.takeRecords().isEmpty());
    }

    @Test
    void queuesOneEntryForIntersectionChangesAndThresholdCrossings() {
        Object target = new Object();
        IntersectionObserverEngine<Object> engine = new IntersectionObserverEngine<>(
                new IntersectionOptions("0px", List.of(0.0, 0.25, 0.5, 0.75))
        );
        engine.observe(target);

        engine.evaluate(observed -> snapshot(observed, 1, new IntersectionRect(-101, 0, 100, 100)));
        assertEquals(1, engine.takeRecords().size());

        engine.evaluate(observed -> snapshot(observed, 2, new IntersectionRect(-70, 0, 100, 100)));
        assertSingleRatio(engine.takeRecords(), 0.3);

        engine.evaluate(observed -> snapshot(observed, 3, new IntersectionRect(-40, 0, 100, 100)));
        assertSingleRatio(engine.takeRecords(), 0.6);

        engine.evaluate(observed -> snapshot(observed, 4, new IntersectionRect(-35, 0, 100, 100)));
        assertTrue(engine.takeRecords().isEmpty());

        engine.evaluate(observed -> snapshot(observed, 5, new IntersectionRect(-150, 0, 100, 100)));
        List<IntersectionEntryData<Object>> exited = engine.takeRecords();
        assertEquals(1, exited.size());
        assertFalse(exited.get(0).isIntersecting());
        assertEquals(0.0, exited.get(0).intersectionRatio(), 0.00001);
    }

    @Test
    void appliesAncestorClipsAndPreservesBoundaryAndZeroAreaSemantics() {
        Object target = new Object();
        IntersectionObserverEngine<Object> engine = new IntersectionObserverEngine<>(
                new IntersectionOptions("0px", List.of())
        );
        engine.observe(target);

        engine.evaluate(observed -> new IntersectionSnapshot<>(
                observed,
                1,
                ROOT,
                new IntersectionRect(0, 0, 100, 100),
                List.of(new IntersectionRect(0, 0, 10, 100))
        ));
        IntersectionEntryData<Object> clipped = engine.takeRecords().get(0);
        assertTrue(clipped.isIntersecting());
        assertEquals(new IntersectionRect(0, 0, 10, 100), clipped.intersectionRect());
        assertEquals(0.1, clipped.intersectionRatio(), 0.00001);

        engine.unobserve(target);
        engine.observe(target);
        engine.evaluate(observed -> snapshot(observed, 2, new IntersectionRect(100, 25, 10, 10)));
        IntersectionEntryData<Object> touching = engine.takeRecords().get(0);
        assertTrue(touching.isIntersecting());
        assertEquals(new IntersectionRect(100, 25, 0, 10), touching.intersectionRect());
        assertEquals(0.0, touching.intersectionRatio(), 0.00001);

        engine.unobserve(target);
        engine.observe(target);
        engine.evaluate(observed -> snapshot(observed, 3, new IntersectionRect(20, 20, 0, 10)));
        IntersectionEntryData<Object> zeroAreaTarget = engine.takeRecords().get(0);
        assertTrue(zeroAreaTarget.isIntersecting());
        assertEquals(1.0, zeroAreaTarget.intersectionRatio(), 0.00001);
    }

    @Test
    void takeRecordsDrainsAtomicallyAndQueuedEntriesSurviveUnobserveAndDisconnect() {
        Object first = new Object();
        Object second = new Object();
        IntersectionObserverEngine<Object> engine = new IntersectionObserverEngine<>(new IntersectionOptions("0px", List.of()));

        engine.observe(first);
        engine.evaluate(observed -> snapshot(observed, 1, new IntersectionRect(0, 0, 10, 10)));
        engine.unobserve(first);
        assertEquals(1, engine.takeRecords().size());
        assertTrue(engine.takeRecords().isEmpty());

        engine.observe(first);
        engine.evaluate(observed -> snapshot(observed, 2, new IntersectionRect(0, 0, 10, 10)));
        engine.disconnect();
        assertEquals(1, engine.takeRecords().size());

        engine.observe(second);
        engine.evaluate(observed -> snapshot(observed, 3, new IntersectionRect(0, 0, 10, 10)));
        List<IntersectionEntryData<Object>> records = engine.takeRecords();
        assertEquals(1, records.size());
        assertEquals(second, records.get(0).target());
    }

    private static IntersectionSnapshot<Object> snapshot(Object target, double time, IntersectionRect targetBounds) {
        return new IntersectionSnapshot<>(target, time, ROOT, targetBounds, List.of());
    }

    private static void assertSingleRatio(List<IntersectionEntryData<Object>> entries, double expectedRatio) {
        assertEquals(1, entries.size());
        assertEquals(expectedRatio, entries.get(0).intersectionRatio(), 0.00001);
    }
}
