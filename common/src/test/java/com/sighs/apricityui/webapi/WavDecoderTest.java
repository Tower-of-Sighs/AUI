package com.sighs.apricityui.webapi;

import com.sighs.apricityui.media.DecodedAudio;
import com.sighs.apricityui.media.decoder.AudioDecoder;
import com.sighs.apricityui.media.decoder.WavDecoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WAV 解码器的 headless 测试：程序生成字节，断言 PCM/格式/时长。 */
class WavDecoderTest {

    @Test
    void decodes16BitMonoPcm() {
        // 8000Hz mono 16-bit，1000 采样 = 0.125s
        byte[] wav = wav(1, 8000, 16, 1000);
        DecodedAudio audio = WavDecoder.decode(wav);
        assertNotNull(audio);
        assertEquals(8000, audio.sampleRate);
        assertEquals(1, audio.channels);
        assertEquals(2000, audio.pcm.length);
        assertEquals(0.125, audio.durationSeconds, 0.0001);
    }

    @Test
    void decodes16BitStereoPcm() {
        byte[] wav = wav(2, 44100, 16, 4410);
        DecodedAudio audio = WavDecoder.decode(wav);
        assertNotNull(audio);
        assertEquals(2, audio.channels);
        assertEquals(4410 * 2 * 2, audio.pcm.length);
        assertEquals(0.1, audio.durationSeconds, 0.0001);
    }

    @Test
    void promotes8BitUnsignedTo16BitSigned() {
        // 8-bit WAV 是无符号：0x80=静音 → 0，0xFF → +32512，0x00 → -32768
        byte[] wav = wav(1, 8000, 8, 3, new byte[]{(byte) 0x80, (byte) 0xFF, 0x00});
        DecodedAudio audio = WavDecoder.decode(wav);
        assertNotNull(audio);
        assertEquals(6, audio.pcm.length, "8-bit 提升 16-bit 后字节数翻倍");
        assertEquals(0, sampleAt(audio.pcm, 0));
        assertEquals(32512, sampleAt(audio.pcm, 1));
        assertEquals(-32768, sampleAt(audio.pcm, 2));
    }

    @Test
    void rejectsNonWavAndUnsupportedFormats() {
        assertNull(WavDecoder.decode(new byte[10]), "太短");
        assertNull(WavDecoder.decode("not a wav file at all...............".getBytes()), "非 RIFF");
        // IEEE float (format 3) 不支持
        assertNull(WavDecoder.decode(wav(3, 1, 8000, 16, 100)), "float 格式应拒绝");
    }

    @Test
    void skipsUnknownChunksBeforeFmtAndData() {
        // 在 fmt 前插入一个奇数大小的未知 chunk（按 2 字节对齐补齐）
        byte[] base = wav(1, 8000, 16, 100);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes());
        writeU32le(out, 0); // 占位，最后回填
        out.writeBytes("WAVE".getBytes());
        out.writeBytes("JUNK".getBytes());
        writeU32le(out, 3);
        out.writeBytes(new byte[]{1, 2, 3, 0}); // 3 + 1 对齐
        out.writeBytes(java.util.Arrays.copyOfRange(base, 12, base.length));
        byte[] bytes = out.toByteArray();
        int riffSize = bytes.length - 8;
        bytes[4] = (byte) (riffSize & 0xFF);
        bytes[5] = (byte) ((riffSize >> 8) & 0xFF);
        bytes[6] = (byte) ((riffSize >> 16) & 0xFF);
        bytes[7] = (byte) ((riffSize >> 24) & 0xFF);

        DecodedAudio audio = WavDecoder.decode(bytes);
        assertNotNull(audio, "fmt 前的未知 chunk 必须被跳过");
        assertEquals(8000, audio.sampleRate);
    }

    @Test
    void audioDecoderDispatchesByExtensionAndMagic() throws Exception {
        byte[] wav = wav(1, 8000, 16, 100);
        assertNotNull(AudioDecoder.decode("a/b.wav", wav), "按 .wav 扩展名");
        assertNotNull(AudioDecoder.decode("no-extension", wav), "按 RIFF 魔数嗅探");
        assertNull(AudioDecoder.decode("a/b.ogg", wav), "WAV 字节走 OGG 解码必须失败");

        // 真实测试资源（classpath 上的静音 wav）也能解
        try (InputStream stream = getClass().getResourceAsStream("/assets/apricityui/apricity/apricityui/test-tone.wav")) {
            assertNotNull(stream, "测试资源必须存在");
            DecodedAudio tone = AudioDecoder.decode("test-tone.wav", stream.readAllBytes());
            assertNotNull(tone);
            assertEquals(0.5, tone.durationSeconds, 0.01);
            assertEquals(8000, tone.sampleRate);
        }
    }

    // --------------------------------------------------------------

    private static short sampleAt(byte[] pcm, int index) {
        return (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8));
    }

    private static byte[] wav(int channels, int sampleRate, int bits, int samples) {
        return wav(1, channels, sampleRate, bits, samples, null);
    }

    private static byte[] wav(int channels, int sampleRate, int bits, int samples, byte[] dataOverride) {
        return wav(1, channels, sampleRate, bits, samples, dataOverride);
    }

    private static byte[] wav(int format, int channels, int sampleRate, int bits, int samples) {
        return wav(format, channels, sampleRate, bits, samples, null);
    }

    private static byte[] wav(int format, int channels, int sampleRate, int bits, int samples, byte[] dataOverride) {
        int bytesPerSample = bits / 8;
        byte[] data = dataOverride != null ? dataOverride : new byte[samples * channels * bytesPerSample];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes());
        writeU32le(out, 36 + data.length);
        out.writeBytes("WAVE".getBytes());
        out.writeBytes("fmt ".getBytes());
        writeU32le(out, 16);
        writeU16le(out, format);
        writeU16le(out, channels);
        writeU32le(out, sampleRate);
        writeU32le(out, sampleRate * channels * bytesPerSample);
        writeU16le(out, channels * bytesPerSample);
        writeU16le(out, bits);
        out.writeBytes("data".getBytes());
        writeU32le(out, data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    private static void writeU16le(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeU32le(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
