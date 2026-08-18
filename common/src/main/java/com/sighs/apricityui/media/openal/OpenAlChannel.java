package com.sighs.apricityui.media.openal;

import com.sighs.apricityui.spi.AuiAudioService;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * 单个 OpenAL source 的播放通道。play/pause/seek/setVolume 允许任意线程
 * 调用（内部 synchronized 串行化 AL 操作）；position 用 AL_SAMPLE_OFFSET
 * 精确读取，seek 同源。
 */
final class OpenAlChannel implements AuiAudioService.AudioChannel {
    private final int sourceId;
    private final OpenAlAudioService.OpenAlBuffer buffer;
    private boolean destroyed;

    OpenAlChannel(int sourceId, OpenAlAudioService.OpenAlBuffer buffer) {
        this.sourceId = sourceId;
        this.buffer = buffer;
    }

    @Override
    public synchronized void play() {
        if (destroyed) return;
        AL10.alSourcePlay(sourceId);
    }

    @Override
    public synchronized void pause() {
        if (destroyed) return;
        AL10.alSourcePause(sourceId);
    }

    @Override
    public synchronized void stop() {
        if (destroyed) return;
        AL10.alSourceStop(sourceId);
        AL10.alSourcei(sourceId, AL11.AL_SAMPLE_OFFSET, 0);
    }

    @Override
    public synchronized void seekSeconds(double seconds) {
        if (destroyed) return;
        double clamped = Math.max(0, Math.min(seconds, buffer.durationSeconds()));
        // 采样偏移是 int：时长折算采样数（44.1k 下约 24 小时才溢出，安全）
        AL10.alSourcei(sourceId, AL11.AL_SAMPLE_OFFSET, (int) (clamped * sampleRateHint()));
    }

    @Override
    public synchronized double positionSeconds() {
        if (destroyed) return 0;
        int sampleOffset = AL10.alGetSourcei(sourceId, AL11.AL_SAMPLE_OFFSET);
        double position = sampleOffset / sampleRateHint();
        return Math.max(0, Math.min(position, buffer.durationSeconds()));
    }

    @Override
    public synchronized void setVolume(float volume) {
        if (destroyed) return;
        AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0f, Math.min(1f, volume)));
    }

    @Override
    public synchronized boolean isPlaying() {
        if (destroyed) return false;
        return AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    @Override
    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        AL10.alSourceStop(sourceId);
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
        AL10.alDeleteSources(sourceId);
    }

    /** position/seek 换算需要采样率；从 buffer 时长与 PCM 无关，这里用 AL 自身读取。 */
    private float sampleRateHint() {
        int frequency = AL10.alGetBufferi(buffer.id(), AL10.AL_FREQUENCY);
        return frequency > 0 ? frequency : 44100f;
    }
}
