package com.sighs.apricityui.chunkmap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Captures native model ownership on reload and hashes effective resources in the background. */
public final class AuiActiveMapResources implements AutoCloseable {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService workers = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "AUI active map resources");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    private final AuiChunkTiles tiles;
    private final CompletableFuture<AuiChunkTiles> renderer;
    private volatile String fingerprint;
    private record SpriteLayout(int x, int y, float u0, float u1, float v0, float v1) { }

    public AuiActiveMapResources(ResourceManager manager, Path cacheDirectory) {
        this(manager, cacheDirectory, net.minecraft.util.Mth.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4));
    }

    public AuiActiveMapResources(ResourceManager manager, Path cacheDirectory, int parallelism) {
        tiles = new AuiChunkTiles(parallelism);
        Map<String, Resource> resources = new TreeMap<>();
        for (String directory : List.of("blockstates", "models", "textures", "atlases")) {
            manager.listResources(directory, id -> true).forEach((id, resource) ->
                    resources.put("assets/" + id.getNamespace() + "/" + id.getPath(), resource));
        }
        Map<String, SpriteLayout> sprites = new TreeMap<>();
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        atlas.getTextureLocations().forEach(id -> {
            var sprite = atlas.getSprite(id);
            sprites.put(id.toString(), new SpriteLayout(sprite.getX(), sprite.getY(),
                    sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1()));
        });
        renderer = CompletableFuture.supplyAsync(() -> {
            try {
                fingerprint = fingerprint(resources, sprites);
                if (closed.get()) throw new CancellationException();
                return tiles;
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, workers);
    }

    private String fingerprint(Map<String, Resource> resources, Map<String, SpriteLayout> sprites) throws IOException {
        MessageDigest combined = digest();
        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), combined))) {
            output.writeUTF("AUI-native-model-resources-1-minecraft-1.20.1");
            output.writeInt(resources.size());
            byte[] buffer = new byte[32768];
            for (var resource : resources.entrySet()) {
                if (closed.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                MessageDigest content = digest();
                long size = 0;
                try (var input = resource.getValue().open()) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (closed.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                        content.update(buffer, 0, count);
                        size += count;
                    }
                }
                output.writeUTF(resource.getKey());
                output.writeLong(size);
                output.write(content.digest());
            }
            output.writeInt(sprites.size());
            for (var entry : sprites.entrySet()) {
                output.writeUTF(entry.getKey());
                SpriteLayout sprite = entry.getValue();
                output.writeInt(sprite.x()); output.writeInt(sprite.y());
                output.writeFloat(sprite.u0()); output.writeFloat(sprite.u1());
                output.writeFloat(sprite.v0()); output.writeFloat(sprite.v1());
            }
        }
        return HexFormat.of().formatHex(combined.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public CompletableFuture<AuiChunkTiles> renderer() { return renderer; }
    public String fingerprint() { return fingerprint; }
    public long generation() { return generation; }
    public boolean isClosed() { return closed.get(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        tiles.close();
        workers.shutdown();
    }
}
