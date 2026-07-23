package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/** Resolves DevTools documents to existing, writable Apricity HTML resources. */
final class DevToolsDocumentStore {
    private DevToolsDocumentStore() {
    }

    static Resolution resolve(Document document) {
        if (document == null) return Resolution.failure("No document selected");
        try {
            return resolve(document.getPath(), ClientLoader.listFinalStaticResources(), FMLEnvironment.production);
        } catch (RuntimeException | LinkageError ignored) {
            return Resolution.failure("This document is not a writable Apricity resource");
        }
    }

    static Resolution resolve(String documentPath, List<Loader.StaticResourceEntry> entries, boolean production) {
        String relativePath = normalize(documentPath);
        if (relativePath.isBlank() || !relativePath.toLowerCase(Locale.ROOT).endsWith(".html")) {
            return Resolution.failure("Only Apricity HTML documents can be saved");
        }
        Loader.StaticResourceEntry matched = null;
        if (entries != null) {
            for (Loader.StaticResourceEntry entry : entries) {
                if (entry != null && relativePath.equals(normalize(entry.path()))) matched = entry;
            }
        }
        if (matched == null) return Resolution.failure("This document is not a file-backed Apricity resource");
        if (matched.layer() == Loader.ResourceLayer.RESOURCE_PACK) {
            return Resolution.failure("Resource-pack HTML is read-only");
        }
        if (matched.layer() == Loader.ResourceLayer.DEV_FOLDER && production) {
            return Resolution.failure("Development resources cannot be saved in production");
        }

        try {
            Path root = Path.of(matched.sourceRoot()).toAbsolutePath().normalize().toRealPath();
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) return Resolution.failure("Resource path is outside its source root");
            if (!Files.isRegularFile(target)) return Resolution.failure("The source HTML file no longer exists");
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(root)) return Resolution.failure("Resource path is outside its source root");
            return Resolution.success(new SaveTarget(relativePath, realTarget, matched.layer()));
        } catch (IOException | RuntimeException ignored) {
            return Resolution.failure("The source HTML path is invalid");
        }
    }

    static SaveResult save(SaveTarget target, String html) {
        if (target == null || html == null) return SaveResult.failure("Nothing to save");
        Path file = target.file().toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) return SaveResult.failure("The source HTML file no longer exists");
        try {
            Files.writeString(file, html, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            ClientLoader.invalidateStaticResourceCache();
            return SaveResult.success(file);
        } catch (IOException | RuntimeException ignored) {
            return SaveResult.failure("Could not save the HTML file");
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
