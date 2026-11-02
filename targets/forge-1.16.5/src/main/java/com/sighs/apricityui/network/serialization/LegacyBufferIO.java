package com.sighs.apricityui.network.serialization;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

final class LegacyBufferIO {
    private LegacyBufferIO() {
    }

    private static int readSize(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 1_048_576) throw new DecoderException("Invalid collection size: " + size);
        return size;
    }

    static long[] readLongArray(FriendlyByteBuf buf) {
        return buf.readLongArray(null);
    }

    static BitSet readBitSet(FriendlyByteBuf buf) {
        return BitSet.valueOf(readLongArray(buf));
    }

    static void writeBitSet(FriendlyByteBuf buf, BitSet bits) {
        buf.writeLongArray(bits.toLongArray());
    }

    static ChunkPos readChunkPos(FriendlyByteBuf buf) {
        return new ChunkPos(buf.readLong());
    }

    static void writeChunkPos(FriendlyByteBuf buf, ChunkPos pos) {
        buf.writeLong(pos.toLong());
    }

    static void writeSectionPos(FriendlyByteBuf buf, SectionPos pos) {
        buf.writeLong(pos.asLong());
    }

    static IntList readIntIdList(FriendlyByteBuf buf) {
        int size = readSize(buf);
        IntList values = new IntArrayList(size);
        for (int i = 0; i < size; i++) values.add(buf.readVarInt());
        return values;
    }

    static void writeIntIdList(FriendlyByteBuf buf, IntList values) {
        buf.writeVarInt(values.size());
        for (int i = 0; i < values.size(); i++) buf.writeVarInt(values.getInt(i));
    }

    static void readWithCount(FriendlyByteBuf buf, Consumer<FriendlyByteBuf> reader) {
        int size = readSize(buf);
        for (int i = 0; i < size; i++) reader.accept(buf);
    }

    static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        return readCollection(buf, ArrayList::new, reader);
    }

    static <T, C extends Collection<T>> C readCollection(FriendlyByteBuf buf, IntFunction<C> factory,
                                                        Function<FriendlyByteBuf, T> reader) {
        int size = readSize(buf);
        C values = factory.apply(size);
        for (int i = 0; i < size; i++) values.add(reader.apply(buf));
        return values;
    }

    static <K, V> Map<K, V> readMap(FriendlyByteBuf buf, Function<FriendlyByteBuf, K> keyReader,
                                   Function<FriendlyByteBuf, V> valueReader) {
        int size = readSize(buf);
        Map<K, V> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) values.put(keyReader.apply(buf), valueReader.apply(buf));
        return values;
    }

    static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> values,
                                     BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(values.size());
        for (T value : values) writer.accept(buf, value);
    }

    static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> values,
                                BiConsumer<FriendlyByteBuf, K> keyWriter,
                                BiConsumer<FriendlyByteBuf, V> valueWriter) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            keyWriter.accept(buf, key);
            valueWriter.accept(buf, value);
        });
    }
}
