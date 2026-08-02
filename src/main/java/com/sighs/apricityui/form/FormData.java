package com.sighs.apricityui.form;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Java-side FormData snapshot used by formdata events and integrations. */
public final class FormData implements Iterable<FormDataEntry> {
    private final ArrayList<FormDataEntry> entries = new ArrayList<>();

    public FormData() {
    }

    public FormData(List<FormDataEntry> initialEntries) {
        if (initialEntries != null) entries.addAll(initialEntries);
    }

    public void append(String name, String value) {
        entries.add(new FormDataEntry(name == null ? "" : name, value == null ? "" : value));
    }

    public void append(String name, String value, String filename) {
        entries.add(new FormDataEntry(name == null ? "" : name,
                value == null ? "" : value, filename == null ? "" : filename));
    }

    public void delete(String name) {
        String key = name == null ? "" : name;
        entries.removeIf(entry -> key.equals(entry.name()));
    }

    public String get(String name) {
        String key = name == null ? "" : name;
        for (FormDataEntry entry : entries) if (key.equals(entry.name())) return entry.value();
        return null;
    }

    public List<String> getAll(String name) {
        String key = name == null ? "" : name;
        ArrayList<String> result = new ArrayList<>();
        for (FormDataEntry entry : entries) if (key.equals(entry.name())) result.add(entry.value());
        return Collections.unmodifiableList(result);
    }

    public boolean has(String name) {
        return get(name) != null;
    }

    public void set(String name, String value) {
        set(name, value, "");
    }

    public void set(String name, String value, String filename) {
        String key = name == null ? "" : name;
        String normalized = value == null ? "" : value;
        String normalizedFilename = filename == null ? "" : filename;
        boolean replaced = false;
        ArrayList<FormDataEntry> next = new ArrayList<>();
        for (FormDataEntry entry : entries) {
            if (!key.equals(entry.name())) next.add(entry);
            else if (!replaced) {
                next.add(new FormDataEntry(key, normalized, normalizedFilename));
                replaced = true;
            }
        }
        if (!replaced) next.add(new FormDataEntry(key, normalized, normalizedFilename));
        entries.clear();
        entries.addAll(next);
    }

    public List<FormDataEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public List<FormDataEntry> entries() {
        return getEntries();
    }

    @Override
    public java.util.Iterator<FormDataEntry> iterator() {
        return getEntries().iterator();
    }

    public List<String> keys() {
        ArrayList<String> result = new ArrayList<>();
        for (FormDataEntry entry : entries) result.add(entry.name());
        return Collections.unmodifiableList(result);
    }

    public List<String> values() {
        ArrayList<String> result = new ArrayList<>();
        for (FormDataEntry entry : entries) result.add(entry.value());
        return Collections.unmodifiableList(result);
    }

    public int getSize() {
        return entries.size();
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (FormDataEntry entry : entries) {
            if (out.length() > 0) out.append('&');
            out.append(URLEncoder.encode(entry.name(), StandardCharsets.UTF_8));
            out.append('=');
            out.append(URLEncoder.encode(entry.value(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
