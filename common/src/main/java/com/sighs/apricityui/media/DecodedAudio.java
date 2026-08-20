package com.sighs.apricityui.media;

/**
 * 解码后的音频：16-bit 有符号 little-endian 交错 PCM。
 * 所有解码器（WAV/OGG）统一输出此格式，与 OpenAL 的
 * AL_FORMAT_MONO16/AL_FORMAT_STEREO16 直接对应。
 */
public final class DecodedAudio {
    public final byte[] pcm;
    public final int sampleRate;
    public final int channels;
    public final double durationSeconds;

    public DecodedAudio(byte[] pcm, int sampleRate, int channels) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.channels = channels;
        int bytesPerFrame = Math.max(1, channels) * 2;
        this.durationSeconds = sampleRate > 0 ? (double) pcm.length / bytesPerFrame / sampleRate : 0;
    }
}
