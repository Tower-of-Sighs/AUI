package com.sighs.apricityui.chunkmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** AUI's BlueMap preview session; the page is an AUI iframe with native mouse forwarding. */
public final class BlueMapChunkPreview implements AutoCloseable {
    private final BlueMapPreviewServer server;
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final CompletableFuture<BlueMapChunkTiles> tiles;

    public BlueMapChunkPreview(List<Path> packRoots, Path cacheDirectory) throws IOException {
        server = new BlueMapPreviewServer();
        tiles = CompletableFuture.supplyAsync(() -> {
            try {
                return new BlueMapChunkTiles(packRoots, cacheDirectory);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
        }, requests);
    }

    public CompletableFuture<String> render(ChunkMapSnapshot snapshot, BooleanSupplier cancelled) {
        return tiles.thenApplyAsync(renderer -> server.publish(renderer.render(snapshot, cancelled)), requests);
    }

    public CompletableFuture<String> render(ChunkMapSnapshot snapshot, long sceneKey, BooleanSupplier cancelled) {
        return tiles.thenApplyAsync(renderer -> server.publish(renderer.render(snapshot, cancelled), sceneKey), requests);
    }

    public CompletableFuture<Boolean> renderDetail(ChunkMapSnapshot snapshot, long sceneKey, BooleanSupplier cancelled) {
        return tiles.thenApplyAsync(renderer -> server.publishDetail(renderer.render(snapshot, cancelled), sceneKey), requests);
    }

    public void clearDetail(long sceneKey) { server.clearDetail(sceneKey); }

    @Override
    public void close() {
        tiles.thenAccept(BlueMapChunkTiles::close);
        requests.shutdownNow();
        server.close();
    }
}
