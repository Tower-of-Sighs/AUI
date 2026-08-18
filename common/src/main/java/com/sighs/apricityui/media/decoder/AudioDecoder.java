package com.sighs.apricityui.media.decoder;

import com.sighs.apricityui.media.DecodedAudio;

/** 音频解码器入口：按扩展名分发到具体格式解码器。 */
public final class AudioDecoder {
    private AudioDecoder() {
    }

    /** 解码失败返回 null（调用方负责记日志/标失败）。 */
    public static DecodedAudio decode(String path, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        String lower = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        try {
            if (lower.endsWith(".wav")) return WavDecoder.decode(bytes);
            if (lower.endsWith(".ogg")) return OggVorbisDecoder.decode(bytes);
            // 无扩展名/未知扩展名：按魔数嗅探
            if (bytes.length >= 4 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
                return WavDecoder.decode(bytes);
            }
            if (bytes.length >= 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') {
                return OggVorbisDecoder.decode(bytes);
            }
        } catch (Throwable ignored) {
            // LinkageError（无 STB natives）与格式异常统一走"解码失败"
            return null;
        }
        return null;
    }
}
