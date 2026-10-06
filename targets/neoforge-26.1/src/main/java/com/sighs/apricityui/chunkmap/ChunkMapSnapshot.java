package com.sighs.apricityui.chunkmap;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable block columns for an AUI map. Capture live levels on their owning thread. */
public final class ChunkMapSnapshot {
    private static final int MAX_SIDE = 576;
    private static final long MAX_VOLUME = 134_217_728L;
    public record Run(int fromY, int toY, BlockState state) {
    }
    public record BiomeTint(String namespace, String path, float temperature,
                            int water, int grass, int foliage, int dryFoliage) {
        static BiomeTint from(Holder<Biome> holder, int worldX, int worldZ) {
            var id = holder.unwrapKey().orElseThrow().identifier();
            Biome biome = holder.value();
            return new BiomeTint(id.getNamespace(), id.getPath(), biome.getBaseTemperature(),
                    biome.getWaterColor(), biome.getGrassColor(worldX, worldZ),
                    biome.getFoliageColor(), biome.getDryFoliageColor());
        }
    }

    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final int step;
    private final int verticalStep;
    private final int minY;
    private final int maxY;
    private final List<Run>[] columns;
    private final BiomeTint[] biomes;

    private ChunkMapSnapshot(Builder builder) {
        originX = builder.originX;
        originZ = builder.originZ;
        width = builder.width;
        depth = builder.depth;
        step = builder.step;
        verticalStep = builder.verticalStep;
        minY = builder.minY;
        maxY = builder.maxY;
        columns = builder.columns.clone();
        biomes = builder.biomes.clone();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i] == null ? List.of() : List.copyOf(columns[i]);
        }
    }

    public static Builder builder(int originX, int originZ, int width, int depth, int step,
                                  int minY, int maxY) {
        return new Builder(originX, originZ, width, depth, step, minY, maxY);
    }

    /** Copies real blocks, including plants and fluids, before worker-side meshing. */
    public static ChunkMapSnapshot capture(BlockGetter level, BlockPos min, int width, int depth,
                                           int height) {
        Builder builder = builder(min.getX(), min.getZ(), width, depth, 1,
                min.getY(), min.getY() + height);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                BlockState previous = Blocks.AIR.defaultBlockState();
                int start = min.getY();
                for (int y = min.getY(); y <= min.getY() + height; y++) {
                    BlockState state = y == min.getY() + height
                            ? Blocks.AIR.defaultBlockState()
                            : level.getBlockState(pos.set(min.getX() + x, y, min.getZ() + z));
                    if (state == previous) continue;
                    if (!previous.isAir()) builder.addRun(x, z, start, y, previous);
                    previous = state;
                    start = y;
                }
                if (level instanceof LevelReader reader) {
                    builder.setBiome(x, z, reader.getBiome(pos.set(
                            min.getX() + x, min.getY() + height - 1, min.getZ() + z)));
                }
            }
        }
        return builder.build();
    }

    public BlockState block(int x, int y, int z) {
        if (x < 0 || x >= width || z < 0 || z >= depth || y < minY || y >= maxY) {
            return Blocks.AIR.defaultBlockState();
        }
        for (Run run : columns[z * width + x]) {
            if (y >= run.fromY() && y < run.toY()) return run.state();
        }
        return Blocks.AIR.defaultBlockState();
    }

    public List<Run> column(int x, int z) {
        return columns[z * width + x];
    }

    public BiomeTint biome(int x, int z) {
        return biomes[z * width + x];
    }

    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int step() { return step; }
    public int verticalStep() { return verticalStep; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }

    public static final class Builder {
        private final int originX;
        private final int originZ;
        private final int width;
        private final int depth;
        private final int step;
        private int verticalStep;
        private final int minY;
        private final int maxY;
        private final List<Run>[] columns;
        private final BiomeTint[] biomes;

        @SuppressWarnings("unchecked")
        private Builder(int originX, int originZ, int width, int depth, int step, int minY, int maxY) {
            if (width <= 0 || depth <= 0 || width > MAX_SIDE || depth > MAX_SIDE
                    || step <= 0 || maxY <= minY
                    || (long) width * depth * ((long) maxY - minY) > MAX_VOLUME) {
                throw new IllegalArgumentException("Chunk map dimensions exceed the preview budget");
            }
            this.originX = originX;
            this.originZ = originZ;
            this.width = width;
            this.depth = depth;
            this.step = step;
            this.verticalStep = step;
            this.minY = minY;
            this.maxY = maxY;
            this.columns = (List<Run>[]) new List<?>[width * depth];
            this.biomes = new BiomeTint[width * depth];
        }

        public Builder setBiome(int x, int z, Holder<Biome> biome) {
            return setBiome(x, z, biome, (originX + x) * step, (originZ + z) * step);
        }

        public Builder verticalStep(int verticalStep) {
            if (verticalStep <= 0) throw new IllegalArgumentException("verticalStep must be > 0");
            this.verticalStep = verticalStep;
            return this;
        }

        public Builder setBiome(int x, int z, Holder<Biome> biome, int worldX, int worldZ) {
            biomes[z * width + x] = BiomeTint.from(biome, worldX, worldZ);
            return this;
        }

        public Builder addRun(int x, int z, int fromY, int toY, BlockState state) {
            if (x < 0 || x >= width || z < 0 || z >= depth || fromY < minY || toY > maxY
                    || fromY >= toY) throw new IllegalArgumentException("Invalid map column run");
            int index = z * width + x;
            if (columns[index] == null) columns[index] = new ArrayList<>();
            columns[index].add(new Run(fromY, toY, state));
            return this;
        }

        public ChunkMapSnapshot build() {
            return new ChunkMapSnapshot(this);
        }
    }
}
