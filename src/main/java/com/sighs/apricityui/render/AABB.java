package com.sighs.apricityui.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class AABB {
    private float x;
    private float y;
    private float width;
    private float height;

    public float maxX() {
        return x + width;
    }

    public float maxY() {
        return y + height;
    }

    public AABB intersection(AABB other) {
        float newX = Math.max(this.x, other.x);
        float newY = Math.max(this.y, other.y);
        float newMaxX = Math.min(this.maxX(), other.maxX());
        float newMaxY = Math.min(this.maxY(), other.maxY());
        return new AABB(newX, newY, Math.max(0, newMaxX - newX), Math.max(0, newMaxY - newY));
    }

    public boolean intersects(AABB other) {
        return this.x < other.maxX() && this.maxX() > other.x &&
                this.y < other.maxY() && this.maxY() > other.y;
    }

    public boolean isValid() {
        return width > 0 && height > 0;
    }
}
