package com.sighs.apricityui.chunkmap;

record AuiTileGrid(int size, int offset) {
    int getCellX(int x) { return Math.floorDiv(x - offset, size); }
    int getCellY(int z) { return Math.floorDiv(z - offset, size); }
    int getCellMinX(int x) { return x * size + offset; }
    int getCellMinY(int z) { return z * size + offset; }
    int getLocalX(int x) { return Math.floorMod(x - offset, size); }
    int getLocalY(int z) { return Math.floorMod(z - offset, size); }
}
