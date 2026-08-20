package com.sighs.apricityui.webapi;

import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.Transition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * contrast / saturate / sepia 三个滤镜函数的全链路回归：
 * 解析（%、数字、默认值）→ FilterState → 动画插值 → readTransition 合并序列化。
 */
class FilterColorMatrixTest {

    private static final float EPS = 0.0001f;

    @Test
    void parseExtractsPercentAndPlainNumbers() {
        Filter.FilterState state = Filter.parse("contrast(150%) saturate(2) sepia(80%)", 1.0f);
        assertEquals(1.5f, state.contrast(), EPS);
        assertEquals(2.0f, state.saturate(), EPS);
        assertEquals(0.8f, state.sepia(), EPS);

        Filter.FilterState percents = Filter.parse("contrast(35%) saturate(260%) sepia(100%)", 1.0f);
        assertEquals(0.35f, percents.contrast(), EPS);
        assertEquals(2.6f, percents.saturate(), EPS);
        assertEquals(1.0f, percents.sepia(), EPS);
    }

    @Test
    void defaultsKeepStateEmpty() {
        Filter.FilterState state = Filter.parse("blur(2px)", 1.0f);
        assertEquals(1.0f, state.contrast(), EPS, "contrast defaults to 1");
        assertEquals(1.0f, state.saturate(), EPS, "saturate defaults to 1");
        assertEquals(0.0f, state.sepia(), EPS, "sepia defaults to 0");

        assertTrue(Filter.parse("contrast(1) saturate(100%) sepia(0)", 1.0f).isEmpty(),
                "identity values must not force the filter pipeline on");
        assertFalse(Filter.parse("sepia(10%)", 1.0f).isEmpty());
    }

    @Test
    void interpolateEmitsChangesForTheThreeFunctions() {
        List<Transition.Change> changes = new ArrayList<>();
        Filter.interpolateFilter(changes,
                "contrast(1) saturate(1) sepia(0)",
                "contrast(2) saturate(3) sepia(100%)", 0.5);

        double contrast = valueOf(changes, "filter-contrast");
        double saturate = valueOf(changes, "filter-saturate");
        double sepia = valueOf(changes, "filter-sepia");
        assertEquals(1.5, contrast, EPS);
        assertEquals(2.0, saturate, EPS);
        assertEquals(0.5, sepia, EPS);
    }

    @Test
    void readTransitionMergesOnlyListedChanges() {
        Style style = new Style();
        style.filter = "brightness(1.2) sepia(40%)";

        List<Transition.Change> changes = new ArrayList<>();
        changes.add(new Transition.Change("filter-contrast", 1.3));
        changes.add(new Transition.Change("filter-saturate", 0.75));
        Filter.readTransition(changes, style);

        Filter.FilterState merged = Filter.parse(style.filter, 1.0f);
        assertEquals(1.3f, merged.contrast(), EPS);
        assertEquals(0.75f, merged.saturate(), EPS);
        assertEquals(1.2f, merged.brightness(), EPS, "unlisted functions survive the merge");
        assertEquals(0.4f, merged.sepia(), EPS);

        assertTrue(style.filter.contains("contrast("), "serialized: " + style.filter);
        assertTrue(style.filter.contains("saturate("), "serialized: " + style.filter);
        assertTrue(style.filter.contains("sepia("), "serialized: " + style.filter);
        assertTrue(changes.isEmpty(), "filter changes are consumed by readTransition");
    }

    @Test
    void keyframeInterpolationReplacesWholeFilterProperty() {
        // CSS semantics: while a filter animation runs, the property value comes
        // from the interpolated keyframes — base-style functions not present in
        // the keyframes fall back to their defaults rather than leaking through.
        Style style = new Style();
        style.filter = "brightness(1.2)";

        List<Transition.Change> changes = new ArrayList<>();
        Filter.interpolateFilter(changes, "sepia(0)", "sepia(100%)", 0.5);
        Filter.readTransition(changes, style);

        Filter.FilterState merged = Filter.parse(style.filter, 1.0f);
        assertEquals(0.5f, merged.sepia(), EPS);
        assertEquals(1.0f, merged.brightness(), EPS, "keyframe default replaces base value");
    }

    private static double valueOf(List<Transition.Change> changes, String name) {
        for (Transition.Change change : changes) {
            if (change.name().equals(name)) return change.value();
        }
        throw new AssertionError("missing change: " + name + " in " + changes);
    }
}
