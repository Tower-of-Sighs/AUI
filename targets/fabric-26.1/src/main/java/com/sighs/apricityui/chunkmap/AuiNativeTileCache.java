package com.sighs.apricityui.chunkmap;

import com.sighs.apricityui.ApricityUI;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/** Persistent cache for native tile geometry and height data. */
public final class AuiNativeTileCache {
    private static final int MAGIC = 0x4155494E;
    private static final int VERSION = 1;
    private static final int HEIGHT_COUNT = 32 * 32;
    private static final int MAX_MODEL_BYTES = 64 * 1024 * 1024;
    private static final long FIXED_FILE_BYTES = Integer.BYTES * 5L + HEIGHT_COUNT * Integer.BYTES;
    private static final Pattern SHA256_KEY = Pattern.compile("[0-9a-f]{64}");

    private final Path directory;

    public AuiNativeTileCache(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public record Hit(String key, AuiChunkTiles.NativeTile tile) { }

    public synchronized Hit commit(String key, AuiChunkTiles.NativeTile incoming, int ceiling) {
        validateKey(key);
        if (incoming == null) throw new NullPointerException("tile");
        if (incoming.key() == null) throw new IllegalArgumentException("tile key is required");
        int mask = availableMask(incoming.heights());
        if (mask != 15) {
            var previous = readLatest(incoming.key(), ceiling);
            if (previous.isPresent()) {
                int previousMask = availableMask(previous.get().tile().heights());
                if (previousMask != mask && (previousMask & mask) == mask) return previous.get();
            }
        }
        write(key, incoming);
        remember(key, incoming.key(), ceiling);
        return new Hit(key, incoming);
    }

    public Optional<Hit> readLatest(AuiChunkTiles.Tile tile, int ceiling) {
        Path reference = referenceFor(tile, ceiling);
        try {
            if (!Files.isRegularFile(reference) || Files.size(reference) != 64) return Optional.empty();
            String key = Files.readString(reference, java.nio.charset.StandardCharsets.US_ASCII);
            if (!SHA256_KEY.matcher(key).matches()) return Optional.empty();
            return read(key, tile).map(model -> new Hit(key, model));
        } catch (IOException | SecurityException failure) {
            ApricityUI.LOGGER.debug("Native tile index unavailable for {}: {}", tile, failure.toString());
            return Optional.empty();
        }
    }

    public synchronized void remember(String key, AuiChunkTiles.Tile tile, int ceiling) {
        validateKey(key);
        Path reference = referenceFor(tile, ceiling), temporary = null;
        try {
            if (!Files.isRegularFile(fileFor(key))) return;
            String previous = Files.isRegularFile(reference) && Files.size(reference) == 64
                    ? Files.readString(reference, java.nio.charset.StandardCharsets.US_ASCII) : null;
            Files.createDirectories(reference.getParent());
            temporary = Files.createTempFile(reference.getParent(), ".tile-index-", ".tmp");
            Files.writeString(temporary, key, java.nio.charset.StandardCharsets.US_ASCII);
            Files.move(temporary, reference, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            if (previous != null && !previous.equals(key) && SHA256_KEY.matcher(previous).matches()) {
                boolean referenced = false;
                try (var layers = Files.list(directory)) {
                    for (var layer : layers.filter(path -> path.getFileName().toString().startsWith("latest-")).toList()) {
                        Path other = layer.resolve(reference.getFileName());
                        if (Files.isRegularFile(other) && Files.size(other) == 64
                                && previous.equals(Files.readString(other, java.nio.charset.StandardCharsets.US_ASCII))) {
                            referenced = true; break;
                        }
                    }
                }
                if (!referenced) Files.deleteIfExists(fileFor(previous));
            }
        } catch (IOException | SecurityException failure) {
            ApricityUI.LOGGER.debug("Native tile index write failed for {}: {}", tile, failure.toString());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException | SecurityException failure) {
                    ApricityUI.LOGGER.debug("Native tile index cleanup failed: {}", failure.toString());
                }
            }
        }
    }

    private Path referenceFor(AuiChunkTiles.Tile tile, int ceiling) {
        return directory.resolve("latest-" + ceiling).resolve(tile.x() + "_" + tile.z() + ".key");
    }

    public Optional<AuiChunkTiles.NativeTile> read(String sha256Key, AuiChunkTiles.Tile expected) {
        validateKey(sha256Key);
        if (expected == null) throw new NullPointerException("expected");

        Path file = fileFor(sha256Key);
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            long fileSize = Files.size(file);
            if (fileSize < FIXED_FILE_BYTES || fileSize > FIXED_FILE_BYTES + MAX_MODEL_BYTES) {
                return miss(sha256Key, "invalid file size");
            }

            try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                    return miss(sha256Key, "invalid header");
                }

                int x = input.readInt();
                int z = input.readInt();
                int modelLength = input.readInt();
                if (x != expected.x() || z != expected.z()
                        || modelLength < 0 || modelLength > MAX_MODEL_BYTES
                        || fileSize != FIXED_FILE_BYTES + modelLength) {
                    return miss(sha256Key, "tile or payload mismatch");
                }

                byte[] model = new byte[modelLength];
                input.readFully(model);
                int[] heights = new int[HEIGHT_COUNT];
                for (int i = 0; i < heights.length; i++) heights[i] = input.readInt();
                if (input.read() != -1) return miss(sha256Key, "trailing data");

                return Optional.of(new AuiChunkTiles.NativeTile(expected, model, heights));
            }
        } catch (IOException | SecurityException failure) {
            ApricityUI.LOGGER.debug("Native tile cache miss for {}: {}", sha256Key, failure.toString());
            return Optional.empty();
        }
    }

    public void write(String sha256Key, AuiChunkTiles.NativeTile tile) {
        validateKey(sha256Key);
        if (tile == null) throw new NullPointerException("tile");
        if (tile.key() == null) throw new IllegalArgumentException("tile key is required");
        if (tile.model() == null || tile.model().length > MAX_MODEL_BYTES) {
            throw new IllegalArgumentException("model must be at most 64 MiB");
        }
        if (tile.heights() == null || tile.heights().length != HEIGHT_COUNT) {
            throw new IllegalArgumentException("tile must contain exactly 1024 heights");
        }

        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".native-tile-", ".tmp");
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(tile.key().x());
                output.writeInt(tile.key().z());
                output.writeInt(tile.model().length);
                output.write(tile.model());
                for (int height : tile.heights()) output.writeInt(height);
            }

            Files.move(temporary, fileFor(sha256Key), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException | SecurityException failure) {
            ApricityUI.LOGGER.debug("Native tile cache write failed for {}: {}", sha256Key, failure.toString());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException cleanupFailure) {
                    ApricityUI.LOGGER.debug("Native tile cache temp cleanup failed: {}", cleanupFailure.toString());
                }
            }
        }
    }

    private static int availableMask(int[] heights) {
        if (heights == null || heights.length != HEIGHT_COUNT) {
            throw new IllegalArgumentException("tile must contain exactly 1024 heights");
        }
        int mask = 0;
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            boolean known = true;
            int x0 = (quadrant & 1) * 16, z0 = (quadrant >> 1) * 16;
            for (int z = z0; z < z0 + 16 && known; z++) {
                for (int x = x0; x < x0 + 16; x++) {
                    if (heights[z * 32 + x] == 32767) { known = false; break; }
                }
            }
            if (known) mask |= 1 << quadrant;
        }
        return mask;
    }

    private Path fileFor(String sha256Key) {
        return directory.resolve(sha256Key + ".tile");
    }

    private static void validateKey(String sha256Key) {
        if (sha256Key == null || !SHA256_KEY.matcher(sha256Key).matches()) {
            throw new IllegalArgumentException("cache key must be 64 lowercase hexadecimal characters");
        }
    }

    private static Optional<AuiChunkTiles.NativeTile> miss(String key, String reason) {
        ApricityUI.LOGGER.debug("Native tile cache miss for {}: {}", key, reason);
        return Optional.empty();
    }
}
