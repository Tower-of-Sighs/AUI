package com.sighs.apricityui.chunkmap;

import com.sighs.apricityui.ApricityUI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;

/** Copies loaded chunks on their owner, or parses existing singleplayer storage without loading chunks. */
final class AuiNativeChunkSource implements AutoCloseable {
    static final String CACHE_SUFFIX = "-minecraft-1.18.2-native-3";
    static final String MODEL_FORMAT = "AUI-native-model-2-minecraft-1.21.1";

    int minY() { return level.getMinBuildHeight(); }
    int maxY() { return level.getMaxBuildHeight(); }

    record Capture(ChunkMapSnapshot snapshot, boolean[] knownColumns) { }
    private record SectionCopy(PalettedContainer<BlockState> states, PalettedContainer<Holder<Biome>> biomes) {
        BlockState getBlockState(int x, int y, int z) { return states.get(x, y, z); }
        Holder<Biome> getNoiseBiome(int x, int y, int z) { return biomes.get(x, y, z); }
    }
    private record ChunkCopy(SectionCopy[] sections, int firstSectionY) {
        SectionCopy section(int y) {
            int index = (y >> 4) - firstSectionY;
            return index >= 0 && index < sections.length ? sections[index] : null;
        }
    }
    private record StoredContext(MinecraftServer server, ChunkStorage storage, Path regions,
                                 LevelHeightAccessor height, ResourceKey<Level> dimension,
                                 Optional<ResourceKey<Codec<? extends ChunkGenerator>>> generatorType,
                                 DataFixer dataFixer, Registry<Biome> biomes, RegistryOps<Tag> registryOps) { }
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codec(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());
    private static final AuiTileGrid GRID = new AuiTileGrid(32, 0);
    private static final int HALO = 16;
    private static final int SNAPSHOT_SIZE = 32 + HALO * 2;
    private static final long MISSING_RETRY_NANOS = 5_000_000_000L;
    private final ClientLevel level;
    private final Executor conversion;
    private final Holder<Biome> fallbackBiome;
    private final CompletableFuture<StoredContext> stored;
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkCopy>> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> missingUntil = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    AuiNativeChunkSource(ClientLevel level, Executor conversion) {
        this.level = level; this.conversion = conversion;
        fallbackBiome = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        stored = server == null ? CompletableFuture.completedFuture(null) : server.submit(() -> {
            var world = server.getLevel(level.dimension());
            if (world == null || closed.get()) return null;
            var storage = world.getChunkSource().chunkMap;
            Path regions = DimensionType.getStorageFolder(world.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("region");
            Registry<Biome> biomes = world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
            return new StoredContext(server, storage, regions,
                    LevelHeightAccessor.create(world.getMinBuildHeight(), world.getHeight()), world.dimension(),
                    world.getChunkSource().getGenerator().getTypeNameForDataFixer(), server.getFixerUpper(), biomes,
                    RegistryOps.create(NbtOps.INSTANCE, world.registryAccess()));
        });
    }

    CompletableFuture<Capture> capture(AuiChunkTiles.Tile tile, int ceiling) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Capture must start on the client thread");
        int originX = GRID.getCellMinX(tile.x()) - HALO, originZ = GRID.getCellMinY(tile.z()) - HALO;
        int minChunkX = Math.floorDiv(originX, 16), minChunkZ = Math.floorDiv(originZ, 16);
        int maxChunkX = Math.floorDiv(originX + SNAPSHOT_SIZE - 1, 16), maxChunkZ = Math.floorDiv(originZ + SNAPSHOT_SIZE - 1, 16);
        int width = maxChunkX - minChunkX + 1, depth = maxChunkZ - minChunkZ + 1;
        @SuppressWarnings("unchecked") CompletableFuture<ChunkCopy>[] copies = new CompletableFuture[width * depth];
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++)
            copies[z * width + x] = chunk(minChunkX + x, minChunkZ + z);
        int minY = level.getMinBuildHeight(), maxY = Math.min(level.getMaxBuildHeight(), ceiling);
        return CompletableFuture.allOf(copies).thenApplyAsync(ignored -> {
            if (closed.get()) throw new java.util.concurrent.CancellationException();
            ChunkCopy[] ready = Arrays.stream(copies).map(CompletableFuture::join).toArray(ChunkCopy[]::new);
            if (maxY <= minY) return null;
            var builder = ChunkMapSnapshot.builder(originX, originZ, SNAPSHOT_SIZE, SNAPSHOT_SIZE, 1, minY, maxY);
            boolean[] known = new boolean[SNAPSHOT_SIZE * SNAPSHOT_SIZE];
            boolean corePresent = false;
            for (int z = 0; z < SNAPSHOT_SIZE; z++) for (int x = 0; x < SNAPSHOT_SIZE; x++) {
                if (closed.get()) throw new java.util.concurrent.CancellationException();
                int worldX = originX + x, worldZ = originZ + z;
                ChunkCopy copy = ready[(Math.floorDiv(worldZ, 16) - minChunkZ) * width + Math.floorDiv(worldX, 16) - minChunkX];
                if (copy == null) continue;
                known[z * SNAPSHOT_SIZE + x] = true;
                if (x >= HALO && x < HALO + 32 && z >= HALO && z < HALO + 32) corePresent = true;
                int columnMax = maxY;
                if (ceiling != Integer.MAX_VALUE) {
                    while (columnMax > minY) {
                        var section = copy.section(columnMax - 1);
                        if (section == null || !section.getBlockState(worldX & 15, (columnMax - 1) & 15, worldZ & 15).canOcclude()) break;
                        columnMax--;
                    }
                }
                BlockState previous = Blocks.AIR.defaultBlockState(); int start = minY, top = minY;
                for (int y = minY; y <= columnMax; y++) {
                    var section = copy.section(y);
                    BlockState state = y == columnMax || section == null ? Blocks.AIR.defaultBlockState()
                            : section.getBlockState(worldX & 15, y & 15, worldZ & 15);
                    if (state == previous) continue;
                    if (!previous.isAir()) { builder.addRun(x, z, start, y, previous); top = y - 1; }
                    previous = state; start = y;
                }
                var biomeSection = copy.section(top);
                Holder<Biome> biome = biomeSection == null ? fallbackBiome : biomeSection.getNoiseBiome(
                        (worldX & 15) >> 2, (top & 15) >> 2, (worldZ & 15) >> 2);
                builder.setBiome(x, z, biome, worldX, worldZ);
            }
            return corePresent ? new Capture(builder.build(), known) : null;
        }, conversion);
    }

    private CompletableFuture<ChunkCopy> chunk(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        Long retry = missingUntil.get(key);
        if (retry != null) {
            if (System.nanoTime() < retry && level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null)
                return CompletableFuture.completedFuture(null);
            missingUntil.remove(key, retry);
        }
        if (chunks.size() > 512) {
            for (var cached : chunks.entrySet()) {
                if (cached.getValue().isDone() && cached.getKey() != key) chunks.remove(cached.getKey(), cached.getValue());
                if (chunks.size() <= 512) break;
            }
        }
        var result = chunks.computeIfAbsent(key, ignored -> {
            LevelChunk live = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (live != null) return CompletableFuture.completedFuture(copy(live,
                    level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)));
            return stored.thenCompose(context -> {
                if (context == null || closed.get() || context.server.isStopped()) return CompletableFuture.completedFuture(null);
                return context.server.submit(() -> {
                    if (closed.get() || context.server.isStopped()) return (ChunkCopy) null;
                    var world = context.server.getLevel(level.dimension());
                    if (world == null) return (ChunkCopy) null;
                    LevelChunk current = world.getChunkSource().getChunkNow(x, z);
                    return current == null ? null : copy(current, world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY));
                }).thenComposeAsync(current -> {
                    if (current != null) return CompletableFuture.completedFuture(current);
                    if (closed.get() || context.server.isStopped()) return CompletableFuture.completedFuture(null);
                    Path region = context.regions.resolve("r." + Math.floorDiv(x, 32) + "." + Math.floorDiv(z, 32) + ".mca");
                    if (!Files.isRegularFile(region)) return CompletableFuture.<ChunkCopy>completedFuture(null);
                    try {
                        return CompletableFuture.completedFuture(decode(context,
                                Optional.ofNullable(context.storage.read(new ChunkPos(x, z)))));
                    } catch (java.io.IOException failure) {
                        return CompletableFuture.failedFuture(failure);
                    }
                }, conversion);
            }).exceptionally(failure -> {
                if (!closed.get()) ApricityUI.LOGGER.debug("Existing map chunk {},{} unavailable: {}", x, z, failure.toString());
                return null;
            });
        });
        result.whenComplete((copy, failure) -> {
            if (copy != null) return;
            chunks.computeIfPresent(key, (ignored, current) -> {
                if (current != result) return current;
                if (!closed.get()) {
                    if (missingUntil.size() >= 65536) {
                        var keys = missingUntil.keys();
                        if (keys.hasMoreElements()) missingUntil.remove(keys.nextElement());
                    }
                    missingUntil.put(key, System.nanoTime() + MISSING_RETRY_NANOS);
                }
                return null;
            });
        });
        return result;
    }

    private ChunkCopy decode(StoredContext context, Optional<CompoundTag> tag) {
        if (closed.get() || tag.isEmpty()) return null;
        CompoundTag upgraded = tag.get().copy();
        ChunkStorage.injectDatafixingContext(upgraded, context.dimension, context.generatorType);
        upgraded = (CompoundTag) context.dataFixer.update(DataFixTypes.CHUNK.getType(),
                new Dynamic<>(NbtOps.INSTANCE, upgraded), ChunkStorage.getVersion(upgraded),
                SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
        if (!Optional.ofNullable(ChunkStatus.byName(upgraded.getString("Status")))
                .filter(status -> status.isOrAfter(ChunkStatus.FULL)).isPresent()) return null;
        SectionCopy[] sections = new SectionCopy[context.height.getSectionsCount()];
        var biomeCodec = PalettedContainer.codec(context.biomes.asHolderIdMap(),
                context.biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, context.biomes.getHolderOrThrow(Biomes.PLAINS));
        var sectionTags = upgraded.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag section = sectionTags.getCompound(i);
            int index = section.getByte("Y") - context.height.getMinSection();
            if (index < 0 || index >= sections.length) continue;
            PalettedContainer<BlockState> states = section.contains("block_states", Tag.TAG_COMPOUND)
                    ? decodePalette(BLOCK_STATES, context.registryOps, section.getCompound("block_states"))
                    : new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES);
        PalettedContainer<Holder<Biome>> biomes = section.contains("biomes", Tag.TAG_COMPOUND)
                    ? decodePalette(biomeCodec, context.registryOps, section.getCompound("biomes"))
                    : new PalettedContainer<>(context.biomes.asHolderIdMap(), context.biomes.getHolderOrThrow(Biomes.PLAINS),
                            PalettedContainer.Strategy.SECTION_BIOMES);
            sections[index] = new SectionCopy(states, biomes);
        }
        return new ChunkCopy(sections, context.height.getMinSection());
    }

    private static <T> T decodePalette(Codec<T> codec, DynamicOps<Tag> ops, CompoundTag tag) {
        DataResult<T> decoded = codec.parse(ops, tag);
        return decoded.getOrThrow(false, message -> new IllegalArgumentException("Invalid map chunk palette: " + message));
    }

    private static PalettedContainer<Holder<Biome>> copyBiomes(PalettedContainer<Holder<Biome>> source,
                                                               Registry<Biome> biomes) {
        Holder<Biome> fallback = biomes.getHolderOrThrow(Biomes.PLAINS);
        var copy = new PalettedContainer<>(biomes.asHolderIdMap(), fallback,
                PalettedContainer.Strategy.SECTION_BIOMES);
        for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
            copy.set(x, y, z, source.get(x, y, z));
        return copy;
    }

    private static ChunkCopy copy(LevelChunk chunk, Registry<Biome> biomes) {
        LevelChunkSection[] source = chunk.getSections();
        SectionCopy[] sections = new SectionCopy[source.length];
        for (int i = 0; i < source.length; i++) {
            LevelChunkSection section = source[i];
            sections[i] = new SectionCopy(section.getStates().copy(),
                    copyBiomes(section.getBiomes(), biomes));
        }
        return new ChunkCopy(sections, chunk.getMinSection());
    }

    void invalidate(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        chunks.remove(key); missingUntil.remove(key);
    }

    @Override public void close() {
        closed.set(true); chunks.values().forEach(future -> future.cancel(false)); chunks.clear(); missingUntil.clear(); stored.cancel(false);
    }
}
