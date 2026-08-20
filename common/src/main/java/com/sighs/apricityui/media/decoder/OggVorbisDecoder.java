package com.sighs.apricityui.media.decoder;

import com.sighs.apricityui.media.DecodedAudio;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * OGG Vorbis 解码器：对 STB stb_vorbis 的薄封装。
 * <p>
 * lwjgl-stb 由 Minecraft 运行时提供；headless 测试 JVM 没有加载 natives，
 * stb_vorbis_decode_memory 会抛 UnsatisfiedLinkError —— 由调用方
 * （AudioDecoder）统一捕获为"解码失败"。
 */
public final class OggVorbisDecoder {
    private OggVorbisDecoder() {
    }

    public static DecodedAudio decode(byte[] bytes) {
        ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
        encoded.put(bytes);
        encoded.flip();

        IntBuffer channelsOut = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRateOut = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channelsOut, sampleRateOut);
        if (pcm == null) return null;

        int channels = channelsOut.get(0);
        int sampleRate = sampleRateOut.get(0);
        if (channels < 1 || channels > 2 || sampleRate <= 0) return null;

        // ShortBuffer（原生字节序）→ S16LE 字节数组
        byte[] out = new byte[pcm.remaining() * 2];
        for (int i = 0; i < out.length; i += 2) {
            short sample = pcm.get();
            out[i] = (byte) (sample & 0xFF);
            out[i + 1] = (byte) (sample >>> 8);
        }
        return new DecodedAudio(out, sampleRate, channels);
    }
}
