package com.sighs.apricityui.chunkmap;

/** Read-only model source; tile data may be loaded on a worker while metadata stays nonblocking. */
public interface AuiChunkTileSource {
    int minY();
    int maxY();
    String contentKey(AuiChunkTiles.Tile tile);
    AuiChunkTiles.NativeTile tile(AuiChunkTiles.Tile tile);
    boolean persistentCache();
}

