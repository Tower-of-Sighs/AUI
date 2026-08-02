package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.instance.Loader;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Handles local HTML import and writes created resources under the active resource root. */
public final class ResourceFileWriter {
    private static final Path DEVELOPMENT_ROOT = Path.of("src/main/resources/assets/apricityui/apricity");

    private ResourceFileWriter() {
    }

    public static ImportedFile readHtmlFile(Path source) {
        if (source == null || !Files.isRegularFile(source)) return null;
        String name = source.getFileName() == null ? "" : source.getFileName().toString();
        if (!name.toLowerCase().endsWith(".html")) return null;
        try {
            byte[] bytes = Files.readAllBytes(source);
            return new ImportedFile(source.toAbsolutePath().normalize(), name, new String(bytes, StandardCharsets.UTF_8), bytes.length);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static WriteResult writeHtml(String requestedPath, String content) {
        return writeHtml(writableRoot(), requestedPath, content);
    }

    static WriteResult writeHtml(Path resourceRoot, String requestedPath, String content) {
        String relativePath = validateHtmlPath(requestedPath);
        if (relativePath.isBlank()) return WriteResult.failure("Enter a relative .html save path");
        if (content == null || content.isBlank()) return WriteResult.failure("Import HTML content first");

        if (resourceRoot == null) return WriteResult.failure("Resource directory is unavailable");
        Path root = resourceRoot.toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) return WriteResult.failure("Save path is outside the resource directory");
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return WriteResult.success(target);
        } catch (IOException ignored) {
            return WriteResult.failure("Could not create the HTML file");
        }
    }

    public static Path writableRoot() {
        Path root = FMLEnvironment.production
                ? Loader.getGameDirectory().resolve("apricity")
                : DEVELOPMENT_ROOT;
        return root.toAbsolutePath().normalize();
    }

    public static String validateHtmlPath(String requestedPath) {
        String normalized = ResourcePath.normalize(requestedPath);
        if (normalized.isBlank() || !normalized.toLowerCase().endsWith(".html")) return "";
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) return "";
        }
        return normalized;
    }

    public record ImportedFile(Path path, String name, String content, long sizeBytes) {
    }

    public record WriteResult(boolean success, Path target, String message) {
        static WriteResult success(Path target) {
            return new WriteResult(true, target, "");
        }

        static WriteResult failure(String message) {
            return new WriteResult(false, null, message);
        }
    }
}
