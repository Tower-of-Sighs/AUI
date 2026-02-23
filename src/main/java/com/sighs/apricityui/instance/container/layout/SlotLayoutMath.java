package com.sighs.apricityui.instance.container.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

public final class SlotLayoutMath {
    public static ContentBox resolveContentBox(Element element) {
        Position position = Position.of(element);
        Box box = Box.of(element);
        Size size = Size.of(element);

        double contentX = position.x + box.getMarginLeft() + box.getBorderLeft() + box.getPaddingLeft();
        double contentY = position.y + box.getMarginTop() + box.getBorderTop() + box.getPaddingTop();
        double contentWidth = size.width() - box.getBorderHorizontal() - box.getPaddingHorizontal();
        double contentHeight = size.height() - box.getBorderVertical() - box.getPaddingVertical();
        return new ContentBox(contentX, contentY, contentWidth, contentHeight);
    }

    public static GridShape resolveAutoGrid(double contentWidth, double contentHeight, int slotCount, int slotPixelSize, int gap) {
        if (slotCount <= 0) return new GridShape(1, 1);

        int columns = Math.max(1, (int) Math.floor((contentWidth + gap) / (slotPixelSize + gap)));
        columns = Math.min(columns, slotCount);
        int rows = (int) Math.ceil(slotCount / (double) columns);

        int maxRowsByHeight = Math.max(1, (int) Math.floor((contentHeight + gap) / (slotPixelSize + gap)));
        if (rows > maxRowsByHeight) {
            rows = maxRowsByHeight;
            columns = (int) Math.ceil(slotCount / (double) rows);
        }
        return new GridShape(Math.max(1, rows), Math.max(1, columns));
    }

    public static GridShape resolveConfiguredGrid(double contentWidth, int slotCount, Container.SlotLayoutSpec slotLayoutSpec, int slotPixelSize, int gap) {
        if (slotCount <= 0) return new GridShape(1, 1);

        Integer configuredRows = slotLayoutSpec.rows();
        Integer configuredColumns = slotLayoutSpec.columns();
        int rows;
        int columns;

        if (configuredRows != null && configuredColumns != null) {
            rows = configuredRows;
            columns = configuredColumns;
        } else if (configuredRows != null) {
            rows = configuredRows;
            columns = (int) Math.ceil(slotCount / (double) rows);
        } else if (configuredColumns != null) {
            columns = configuredColumns;
            rows = (int) Math.ceil(slotCount / (double) columns);
        } else {
            columns = Math.max(1, (int) Math.floor((contentWidth + gap) / (slotPixelSize + gap)));
            columns = Math.min(columns, slotCount);
            rows = (int) Math.ceil(slotCount / (double) columns);
        }

        return new GridShape(Math.max(1, rows), Math.max(1, columns));
    }

    public record ContentBox(double x, double y, double width, double height) {
    }

    public record GridShape(int rows, int columns) {
    }
}
