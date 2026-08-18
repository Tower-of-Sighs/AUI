package com.sighs.apricityui.media.decoder;

import com.sighs.apricityui.media.DecodedAudio;

/**
 * WAV/PCM 解码器（纯 Java，headless 可测）。
 * 支持 RIFF/WAVE 的 PCM (format 1) 8-bit 无符号与 16-bit 有符号；
 * 8-bit 提升为 16-bit，统一输出 DecodedAudio 的 S16LE 交错格式。
 * 不支持的格式（IEEE float、ADPCM 等）返回 null。
 */
public final class WavDecoder {
    private WavDecoder() {
    }

    public static DecodedAudio decode(byte[] bytes) {
        try {
            return doDecode(bytes);
        } catch (Exception exception) {
            return null;
        }
    }

    private static DecodedAudio doDecode(byte[] bytes) {
        if (bytes.length < 44) return null;
        if (!ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) return null;

        int offset = 12;
        int audioFormat = -1;
        int channels = -1;
        int sampleRate = -1;
        int bitsPerSample = -1;
        byte[] data = null;

        // 逐 chunk 扫描（chunk 大小按 2 字节对齐补齐）
        while (offset + 8 <= bytes.length) {
            String chunkId = ascii4(bytes, offset);
            int chunkSize = u32le(bytes, offset + 4);
            int body = offset + 8;
            if (body + chunkSize > bytes.length) break;
            if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16) return null;
                audioFormat = u16le(bytes, body);
                channels = u16le(bytes, body + 2);
                sampleRate = u32le(bytes, body + 4);
                bitsPerSample = u16le(bytes, body + 14);
            } else if ("data".equals(chunkId)) {
                data = new byte[chunkSize];
                System.arraycopy(bytes, body, data, 0, chunkSize);
            }
            offset = body + chunkSize + (chunkSize & 1);
        }

        if (audioFormat != 1 || channels < 1 || channels > 2 || sampleRate <= 0 || data == null) return null;
        if (bitsPerSample == 16) return new DecodedAudio(data, sampleRate, channels);
        if (bitsPerSample == 8) {
            // 8-bit WAV 是无符号 PCM：提升为 16-bit 有符号（(v - 128) << 8）
            byte[] pcm16 = new byte[data.length * 2];
            for (int i = 0; i < data.length; i++) {
                short sample = (short) (((data[i] & 0xFF) - 128) << 8);
                pcm16[i * 2] = (byte) (sample & 0xFF);
                pcm16[i * 2 + 1] = (byte) (sample >>> 8);
            }
            return new DecodedAudio(pcm16, sampleRate, channels);
        }
        return null;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return expected.equals(ascii4(bytes, offset));
    }

    private static String ascii4(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) return "";
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) builder.append((char) (bytes[offset + i] & 0xFF));
        return builder.toString();
    }

    private static int u16le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int u32le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
