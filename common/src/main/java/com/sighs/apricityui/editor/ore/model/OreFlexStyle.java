package com.sighs.apricityui.editor.ore.model;

public final class OreFlexStyle {
    private String direction = "row";
    private String wrap = "nowrap";
    private String justifyContent = "flex-start";
    private String alignItems = "stretch";
    private String alignContent = "stretch";
    private String gap = "0px";
    private String rowGap = "0px";
    private String columnGap = "0px";

    public String direction() { return direction; }
    public String wrap() { return wrap; }
    public String justifyContent() { return justifyContent; }
    public String alignItems() { return alignItems; }
    public String alignContent() { return alignContent; }
    public String gap() { return gap; }
    public String rowGap() { return rowGap; }
    public String columnGap() { return columnGap; }
    public void setDirection(String value) { direction = value == null || value.isBlank() ? "row" : value; }
    public void setWrap(String value) { wrap = value == null || value.isBlank() ? "nowrap" : value; }
    public void setJustifyContent(String value) { justifyContent = value == null || value.isBlank() ? "flex-start" : value; }
    public void setAlignItems(String value) { alignItems = value == null || value.isBlank() ? "stretch" : value; }
    public void setAlignContent(String value) { alignContent = value == null || value.isBlank() ? "stretch" : value; }
    public void setGap(String value) { gap = value == null || value.isBlank() ? "0px" : value; }
    public void setRowGap(String value) { rowGap = value == null || value.isBlank() ? "0px" : value; }
    public void setColumnGap(String value) { columnGap = value == null || value.isBlank() ? "0px" : value; }
}
