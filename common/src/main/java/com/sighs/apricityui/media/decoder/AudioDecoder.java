package com.sighs.apricityui.media.decoder;

import com.sighs.apricityui.media.DecodedAudio;

/** 音频解码器入口：按扩展名分发到具体格式解码器。 */
public final class AudioDecoder {
    private AudioDecoder() {
    }

    /** 解码失败返回 null（调用方负责记日志/标失败，原因可用 {@link #rejectionReason} 诊断）。 */
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

    /** decode 返回 null 后的失败原因诊断（用于日志；不做二次解码）。 */
    public static String rejectionReason(String path, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "文件为空或读取失败";
        String lower = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        boolean wavExt = lower.endsWith(".wav");
        boolean oggExt = lower.endsWith(".ogg");
        boolean riffMagic = bytes.length >= 4 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
        boolean oggMagic = bytes.length >= 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
        if (!wavExt && !oggExt && !riffMagic && !oggMagic) {
            String extension = "";
            String name = path == null ? "" : path;
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) extension = name.substring(dot);
            return "不支持的音频格式（仅支持 OGG Vorbis / WAV PCM）"
                    + (extension.isEmpty() ? "" : ": " + extension);
        }
        if (wavExt || riffMagic) {
            return "WAV 解码失败：仅支持 PCM（format 1）、1-2 声道、8/16-bit";
        }
        return "OGG 解码失败：文件损坏、非 Vorbis 流或缺少 STB natives";
    }
}
