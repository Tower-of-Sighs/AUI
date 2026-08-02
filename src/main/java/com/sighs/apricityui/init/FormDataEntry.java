package com.sighs.apricityui.init;

/** One successful control contribution in form submission order. */
public record FormDataEntry(String name, String value, String filename) {
    public FormDataEntry(String name, String value) {
        this(name, value, "");
    }

    public String getName() { return name; }
    public String getValue() { return value; }
    public String getFilename() { return filename; }
}
