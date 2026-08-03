package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.spi.AuiServices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import com.sighs.apricityui.parser.HTML;

/** Resolves DevTools documents and stylesheets to existing, writable Apricity resources. */
final class DevToolsDocumentStore {
    private DevToolsDocumentStore() {
    }

    static Resolution resolve(Document document) {
        if (document == null) return Resolution.failure("No document selected");
        try {
            return resolve(document.getPath(), ClientLoader.listFinalStaticResources(), AuiServices.client().isProduction());
        } catch (RuntimeException | LinkageError ignored) {
            return Resolution.failure("This document is not a writable Apricity resource");
        }
    }

    static Resolution resolve(String documentPath, List<Loader.StaticResourceEntry> entries, boolean production) {
        String relativePath = normalize(documentPath);
        if (relativePath.isBlank() || !relativePath.toLowerCase(Locale.ROOT).endsWith(".html")) {
            return Resolution.failure("Only Apricity HTML documents can be saved");
        }
        return resolveNormalized(relativePath, entries, production, "HTML");
    }

    static Resolution resolveResource(String resourcePath, List<Loader.StaticResourceEntry> entries,
                                      boolean production) {
        String relativePath = normalize(resourcePath);
        if (relativePath.isBlank()) {
            return Resolution.failure("Only Apricity resources can be saved");
        }
        return resolveNormalized(relativePath, entries, production, "stylesheet");
    }

    private static Resolution resolveNormalized(String relativePath, List<Loader.StaticResourceEntry> entries,
                                                boolean production, String resourceKind) {
        Loader.StaticResourceEntry matched = null;
        if (entries != null) {
            for (Loader.StaticResourceEntry entry : entries) {
                if (entry != null && relativePath.equals(normalize(entry.path()))) matched = entry;
            }
        }
        if (matched == null) return Resolution.failure(
                "This " + resourceKind + " is not a file-backed Apricity resource");
        if (matched.layer() == Loader.ResourceLayer.RESOURCE_PACK) {
            return Resolution.failure("Resource-pack " + resourceKind.toLowerCase(Locale.ROOT) + " is read-only");
        }
        if (matched.layer() == Loader.ResourceLayer.DEV_FOLDER && production) {
            return Resolution.failure("Development resources cannot be saved in production");
        }

        try {
            Path root = Path.of(matched.sourceRoot()).toAbsolutePath().normalize().toRealPath();
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) return Resolution.failure("Resource path is outside its source root");
            if (!Files.isRegularFile(target)) return Resolution.failure(
                    "The source " + resourceKind.toLowerCase(Locale.ROOT) + " file no longer exists");
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(root)) return Resolution.failure("Resource path is outside its source root");
            return Resolution.success(new SaveTarget(relativePath, realTarget, matched.layer()));
        } catch (IOException | RuntimeException ignored) {
            return Resolution.failure("The source " + resourceKind.toLowerCase(Locale.ROOT) + " path is invalid");
        }
    }

    static SaveResult save(SaveTarget target, String content) {
        if (target == null || content == null) return SaveResult.failure("Nothing to save");
        Path file = target.file().toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) return SaveResult.failure("The source file no longer exists");
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            ClientLoader.invalidateStaticResourceCache();
            return SaveResult.success(file);
        } catch (IOException | RuntimeException ignored) {
            return SaveResult.failure("Could not save the source file");
        }
    }

    static String read(SaveTarget target) {
        if (target == null) return null;
        try {
            return Files.readString(target.file(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    static String readResource(String resourcePath) {
        try (InputStream stream = ClientLoader.getResourceStream(resourcePath)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String normalize(String path) {
        if (path == null) return "";
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank()) return "";
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) return "";
        }
        return normalized;
    }

    record SaveTarget(String relativePath, Path file, Loader.ResourceLayer layer) {
    }

    record Resolution(boolean writable, SaveTarget target, String message) {
        static Resolution success(SaveTarget target) {
            return new Resolution(true, target, "");
        }

        static Resolution failure(String message) {
            return new Resolution(false, null, message);
        }
    }

    record SaveResult(boolean success, Path file, String message) {
        static SaveResult success(Path file) {
            return new SaveResult(true, file, "");
        }

        static SaveResult failure(String message) {
            return new SaveResult(false, null, message);
        }
    }
}
