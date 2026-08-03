package com.sighs.apricityui.editor.ore.drag;

import com.sighs.apricityui.editor.ore.palette.OreComponentDefinition;

import java.util.function.BiConsumer;

/** Small AUI-event-only drag state machine for palette payloads. */
public final class OreDragController {
    private OreComponentDefinition payload;
    private double x;
    private double y;
    private boolean active;

    public boolean active() { return active; }
    public OreComponentDefinition payload() { return payload; }
    public double x() { return x; }
    public double y() { return y; }
    public void begin(OreComponentDefinition payload, double x, double y) {
        this.payload = payload;
        this.x = x;
        this.y = y;
        this.active = payload != null;
    }
    public void move(double x, double y) { if (active) { this.x = x; this.y = y; } }
    public void end(BiConsumer<OreComponentDefinition, double[]> drop) {
        if (active && payload != null) drop.accept(payload, new double[]{x, y});
        cancel();
    }
    public void cancel() { active = false; payload = null; }
}
