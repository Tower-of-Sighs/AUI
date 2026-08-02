package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.canvas.OreFlexInsertionResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OreFlexInsertionResolverTest {
    private final OreFlexInsertionResolver resolver = new OreFlexInsertionResolver();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void resolvesRowFromVisualGeometryRatherThanInputOrder() {
        var insertion = resolver.resolve("row", "nowrap", List.of(
                item(second, 60, 0), item(first, 0, 0), item(third, 120, 0)), 55, 10);
        assertEquals(second, insertion.beforeId());
        assertEquals(60, insertion.coordinate());
    }

    @Test
    void respectsReverseMainAxisAndIgnoresAbsoluteItems() {
        var insertion = resolver.resolve("row-reverse", "nowrap", List.of(
                item(first, 100, 0), item(second, 40, 0), new OreFlexInsertionResolver.Item(third,
                        new OreFlexInsertionResolver.Bounds(0, 0, 30, 20), true)), 70, 10);
        assertEquals(first, insertion.beforeId());
        assertEquals(130, insertion.coordinate());
    }

    @Test
    void resolvesTheNearestWrappedLineAndAppendsAfterItsLastItem() {
        var insertion = resolver.resolve("row", "wrap", List.of(
                item(first, 0, 0), item(second, 50, 0), item(third, 0, 40)), 80, 48);
        assertNull(insertion.beforeId());
        assertEquals(30, insertion.coordinate());
    }

    @Test
    void resolvesWrapReverseFromVisualCrossAxisGeometry() {
        var insertion = resolver.resolve("row", "wrap-reverse", List.of(
                item(third, 50, 40), item(first, 0, 0), item(second, 50, 0)), 45, 8);
        assertEquals(second, insertion.beforeId());
        assertEquals(50, insertion.coordinate());
        assertEquals(0, insertion.crossStart());
    }

    @Test
    void resolvesColumnReverseUsingVisualOrderRatherThanCandidateOrder() {
        var insertion = resolver.resolve("column-reverse", "nowrap", List.of(
                item(second, 0, 40), item(first, 0, 100), item(third, 0, 0)), 8, 75);
        assertEquals(first, insertion.beforeId());
        assertEquals(120, insertion.coordinate());
    }

    private OreFlexInsertionResolver.Item item(UUID id, double left, double top) {
        return new OreFlexInsertionResolver.Item(id, new OreFlexInsertionResolver.Bounds(left, top, 30, 20), false);
    }
}
