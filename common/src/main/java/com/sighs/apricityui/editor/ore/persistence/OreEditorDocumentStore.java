package com.sighs.apricityui.editor.ore.persistence;

import com.sighs.apricityui.spi.AuiServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes editor-owned documents below the game directory without accepting arbitrary paths. */
public final class OreEditorDocumentStore {
    private static final String PROJECT_NAME = "untitled.ore.json";
    private static final String EXPORT_NAME = "untitled.html";
    private final Path root;

    public OreEditorDocumentStore() {
        this(resolveDefaultRoot());
    }

    private static Path resolveDefaultRoot() {
        Path dir = AuiServices.client().getGameDirectory();
        if (dir == null) return null;
        return dir.resolve("apricity").resolve("ore-projects");
    }

    public OreEditorDocumentStore(Path root) {
        this.root = root == null ? null : root.toAbsolutePath().normalize();
    }

    public Result saveProject(String content) { return write(PROJECT_NAME, content); }
    public Result exportHtml(String content) { return write(EXPORT_NAME, content); }

    public ReadResult readProject() {
        if (root == null) return ReadResult.failure("Editor directory is unavailable");
        Path target = root.resolve(PROJECT_NAME).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) return ReadResult.failure("Saved editor project was not found");
        try {
            return ReadResult.success(target, Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            return ReadResult.failure("Could not read editor project");
        }
    }

    private Result write(String fileName, String content) {
        if (content == null || content.isBlank()) return Result.failure("Empty editor document");
        if (root == null) return Result.failure("Editor directory is unavailable");
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) return Result.failure("Invalid editor path");
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempFile(root, fileName, ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return Result.success(target);
        } catch (IOException | RuntimeException ignored) {
            return Result.failure("Could not write editor document");
        }
    }

    public record Result(boolean success, Path file, String message) {
        static Result success(Path file) { return new Result(true, file, ""); }
        static Result failure(String message) { return new Result(false, null, message); }
    }
    public record ReadResult(boolean success, Path file, String content, String message) {
        static ReadResult success(Path file, String content) { return new ReadResult(true, file, content, ""); }
        static ReadResult failure(String message) { return new ReadResult(false, null, null, message); }
    }
}
