package com.sighs.apricityui.media.openal;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.media.DecodedAudio;
import com.sighs.apricityui.spi.AuiAudioService;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALCCapabilities;

import java.util.function.DoubleSupplier;

/**
 * OpenAL 真后端：与 MC SoundEngine 并存的第二个 ALC device/context
 * （OpenAL Soft 支持同设备多 context）。lazy 初始化——首个 buffer 创建时
 * 才打开设备；初始化失败整体降级为"不可用"（createBuffer/openChannel
 * 返回 null，上层派发 error 事件），不影响游戏。
 * <p>
 * 全版本共用这一份实现：OpenAL 是 native 级稳定 API，与 MC 版本无关；
 * 唯一的版本差异（options 主音量）由构造注入的 masterVolume lambda 吸收。
 * <p>
 * 线程约定：createBuffer/openChannel/channel.destroy 在 client 主线程
 * （AudioAsyncHandler apply / AudioEngine）调用；play/pause/seek/setVolume
 * 允许任意线程，内部 synchronized 保证 AL 调用串行。
 */
public final class OpenAlAudioService implements AuiAudioService {
    private final DoubleSupplier masterVolume;

    private boolean initAttempted;
    private boolean available;
    private long device;
    private long context;

    private OpenAlAudioService(DoubleSupplier masterVolume) {
        this.masterVolume = masterVolume;
    }

    public static OpenAlAudioService create(DoubleSupplier masterVolume) {
        return new OpenAlAudioService(masterVolume == null ? () -> 1.0 : masterVolume);
    }

    @Override
    public float masterVolume() {
        return (float) masterVolume.getAsDouble();
    }

    @Override
    public synchronized AudioBufferHandle createBuffer(DecodedAudio audio) {
        if (audio == null || !ensureInit()) return null;
        makeCurrent();
        int bufferId = AL10.alGenBuffers();
        int format = audio.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        java.nio.ByteBuffer pcm = org.lwjgl.BufferUtils.createByteBuffer(audio.pcm.length);
        pcm.put(audio.pcm);
        pcm.flip();
        AL10.alBufferData(bufferId, format, pcm, audio.sampleRate);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteBuffers(bufferId);
            ApricityUI.LOGGER.warn("[AUI Audio] alBufferData failed");
            return null;
        }
        return new OpenAlBuffer(bufferId, audio.durationSeconds);
    }

    @Override
    public synchronized AudioChannel openChannel(AudioBufferHandle buffer) {
        if (!(buffer instanceof OpenAlBuffer openAlBuffer) || !ensureInit()) return null;
        makeCurrent();
        int sourceId = AL10.alGenSources();
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, openAlBuffer.id());
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteSources(sourceId);
            return null;
        }
        return new OpenAlChannel(sourceId, openAlBuffer);
    }

    private boolean ensureInit() {
        if (initAttempted) return available;
        initAttempted = true;
        try {
            device = ALC10.alcOpenDevice((CharSequence) null);
            if (device == 0L) throw new IllegalStateException("alcOpenDevice returned null");
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L) throw new IllegalStateException("alcCreateContext returned null");
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(capabilities);
            available = true;
            ApricityUI.LOGGER.info("[AUI Audio] OpenAL backend initialized");
        } catch (Throwable throwable) {
            available = false;
            ApricityUI.LOGGER.warn("[AUI Audio] OpenAL backend unavailable, audio disabled", throwable);
            shutdownQuietly();
        }
        return available;
    }

    private void makeCurrent() {
        ALC10.alcMakeContextCurrent(context);
    }

    private void shutdownQuietly() {
        try {
            if (context != 0L) {
                ALC10.alcMakeContextCurrent(0L);
                ALC10.alcDestroyContext(context);
                context = 0L;
            }
            if (device != 0L) {
                ALC10.alcCloseDevice(device);
                device = 0L;
            }
        } catch (Throwable ignored) {
        }
    }

    /** 进程退出/资源 reload 时的整体释放（目前仅兜底用）。 */
    public synchronized void shutdown() {
        shutdownQuietly();
        available = false;
        initAttempted = false;
    }

    static final class OpenAlBuffer implements AudioBufferHandle {
        private final int id;
        private final double durationSeconds;
        private boolean destroyed;

        OpenAlBuffer(int id, double durationSeconds) {
            this.id = id;
            this.durationSeconds = durationSeconds;
        }

        int id() {
            return id;
        }

        @Override
        public double durationSeconds() {
            return durationSeconds;
        }

        @Override
        public synchronized void destroy() {
            if (destroyed) return;
            destroyed = true;
            AL10.alDeleteBuffers(id);
        }

        synchronized boolean isDestroyed() {
            return destroyed;
        }
    }
}
