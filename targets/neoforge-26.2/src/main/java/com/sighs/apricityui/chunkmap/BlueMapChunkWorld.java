package com.sighs.apricityui.chunkmap;

import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.core.util.Grid;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.Chunk;
import de.bluecolored.bluemap.core.world.DimensionType;
import de.bluecolored.bluemap.core.world.Entity;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.Region;
import de.bluecolored.bluemap.core.world.World;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.biome.GrassColorModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** An immutable Minecraft chunk snapshot exposed to BlueMap's real tile renderer. */
final class BlueMapChunkWorld implements World {
    private final ChunkMapSnapshot snapshot;
    private final Chunk chunk = new SnapshotChunk();
    private final Grid chunkGrid = new Grid(16);
    private final Grid regionGrid = new Grid(512);
    private final Map<BlockState, de.bluecolored.bluemap.core.world.BlockState> states =
            new ConcurrentHashMap<>();
    private final Map<ChunkMapSnapshot.BiomeTint, Biome> biomes = new ConcurrentHashMap<>();

    BlueMapChunkWorld(ChunkMapSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override public String getId() { return "apricityui-preview"; }
    @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }
    @Override public Grid getChunkGrid() { return chunkGrid; }
    @Override public Grid getRegionGrid() { return regionGrid; }

    @Override
    public Chunk getChunkAtBlock(int x, int z) {
        return x >= snapshot.originX() - 1 && x <= snapshot.originX() + snapshot.width()
                && z >= snapshot.originZ() - 1 && z <= snapshot.originZ() + snapshot.depth()
                ? chunk : Chunk.EMPTY_CHUNK;
    }

    @Override
    public Chunk getChunk(int x, int z) {
        return overlaps(x * 16, z * 16, 16) ? chunk : Chunk.EMPTY_CHUNK;
    }

    private boolean overlaps(int x, int z, int size) {
        return x < snapshot.originX() + snapshot.width() && x + size > snapshot.originX()
                && z < snapshot.originZ() + snapshot.depth() && z + size > snapshot.originZ();
    }

    @Override
    public Region<Chunk> getRegion(int x, int z) {
        return new Region<>() {
            @Override
            public void iterateAllChunks(de.bluecolored.bluemap.core.world.ChunkConsumer<Chunk> consumer) {
                for (int cz = z * 32; cz < z * 32 + 32; cz++) {
                    for (int cx = x * 32; cx < x * 32 + 32; cx++) {
                        Chunk current = getChunk(cx, cz);
                        if (current != Chunk.EMPTY_CHUNK && consumer.filter(cx, cz, 0)) {
                            consumer.accept(cx, cz, current);
                        }
                    }
                }
            }

            @Override public Chunk emptyChunk() { return Chunk.EMPTY_CHUNK; }
            @Override public boolean exists() { return overlaps(x * 512, z * 512, 512); }
        };
    }

    @Override
    public Collection<Vector2i> listRegions() {
        int minX = Math.floorDiv(snapshot.originX(), 512);
        int minZ = Math.floorDiv(snapshot.originZ(), 512);
        int maxX = Math.floorDiv(snapshot.originX() + snapshot.width() - 1, 512);
        int maxZ = Math.floorDiv(snapshot.originZ() + snapshot.depth() - 1, 512);
        Collection<Vector2i> regions = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) regions.add(new Vector2i(x, z));
        }
        return regions;
    }

    @Override public void preloadRegionChunks(int x, int z, Predicate<Vector2i> filter) { }
    @Override public void invalidateChunkCache() { }
    @Override public void invalidateChunkCache(int x, int z) { }
    @Override public void iterateEntities(int minX, int minZ, int maxX, int maxZ,
                                          Consumer<Entity> consumer) { }

    private de.bluecolored.bluemap.core.world.BlockState convert(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Map<String, String> properties = new java.util.HashMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValue(state, property));
        }
        return new de.bluecolored.bluemap.core.world.BlockState(
                new Key(id.getNamespace(), id.getPath()), Map.copyOf(properties));
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static final class MappedBiome implements Biome {
        private final Key key;
        private final float temperature;
        private final Color water;
        private final Color grass;
        private final Color foliage;
        private final Color dryFoliage;

        private MappedBiome(ChunkMapSnapshot.BiomeTint tint) {
            key = new Key(tint.namespace(), tint.path());
            temperature = tint.temperature();
            water = color(tint.water());
            grass = color(tint.grass());
            foliage = color(tint.foliage());
            dryFoliage = color(tint.dryFoliage());
        }

        private static Color color(int rgb) {
            return new Color().set(rgb | 0xFF000000).premultiplied();
        }

        @Override public Key getKey() { return key; }
        @Override public float getDownfall() { return 0.5F; }
        @Override public float getTemperature() { return temperature; }
        @Override public Color getWaterColor() { return water; }
        @Override public Color getOverlayFoliageColor() { return foliage; }
        @Override public Color getOverlayDryFoliageColor() { return dryFoliage; }
        @Override public Color getOverlayGrassColor() { return grass; }
        @Override public GrassColorModifier getGrassColorModifier() { return GrassColorModifier.NONE; }
    }

    private final class SnapshotChunk implements Chunk {
        @Override public boolean isGenerated() { return true; }
        @Override public boolean hasLightData() { return false; }
        @Override public int getMinY(int x, int z) { return snapshot.minY(); }
        @Override public int getMaxY(int x, int z) { return snapshot.maxY() - 1; }
        @Override public LightData getLightData(int x, int y, int z, LightData target) {
            return target.set(15, 0);
        }
        @Override public Biome getBiome(int x, int y, int z) {
            var tint = snapshot.biome(localX(x), localZ(z));
            return tint == null ? Biome.DEFAULT : biomes.computeIfAbsent(tint, MappedBiome::new);
        }
        @Override public boolean hasWorldSurfaceHeights() { return true; }
        @Override public int getWorldSurfaceY(int x, int z) {
            var column = snapshot.column(localX(x), localZ(z));
            return column.isEmpty() ? snapshot.minY() : column.getLast().toY();
        }
        @Override
        public de.bluecolored.bluemap.core.world.BlockState getBlockState(int x, int y, int z) {
            BlockState state = snapshot.block(localX(x), y, localZ(z));
            return state.isAir() ? de.bluecolored.bluemap.core.world.BlockState.AIR
                    : states.computeIfAbsent(state, BlueMapChunkWorld.this::convert);
        }
    }

    private int localX(int worldX) {
        return Math.clamp(worldX - snapshot.originX(), 0, snapshot.width() - 1);
    }

    private int localZ(int worldZ) {
        return Math.clamp(worldZ - snapshot.originZ(), 0, snapshot.depth() - 1);
    }
}
