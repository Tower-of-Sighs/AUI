package com.sighs.apricityui.media.openal;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.media.DecodedAudio;
import com.sighs.apricityui.spi.AuiAudioService;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

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
    private ALCapabilities alCapabilities;
    private boolean threadLocalContextAvailable;

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
        return withContext(() -> {
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
            return new OpenAlBuffer(this, bufferId, audio.durationSeconds);
        });
    }

    @Override
    public synchronized AudioChannel openChannel(AudioBufferHandle buffer) {
        if (!(buffer instanceof OpenAlBuffer openAlBuffer) || !ensureInit()) return null;
        return withContext(() -> {
            int sourceId = AL10.alGenSources();
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, openAlBuffer.id());
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteSources(sourceId);
                return null;
            }
            return new OpenAlChannel(this, sourceId, openAlBuffer);
        });
    }

    private boolean ensureInit() {
        if (initAttempted) return available;
        initAttempted = true;
        ALCapabilities previousCapabilities = currentCapabilities();
        long previousThreadContext = 0L;
        try {
            device = ALC10.alcOpenDevice((CharSequence) null);
            if (device == 0L) throw new IllegalStateException("alcOpenDevice returned null");
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            if (!capabilities.ALC_EXT_thread_local_context) {
                throw new IllegalStateException("ALC_EXT_thread_local_context is unavailable");
            }
            threadLocalContextAvailable = true;
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L) throw new IllegalStateException("alcCreateContext returned null");
            previousThreadContext = EXTThreadLocalContext.alcGetThreadContext();
            if (!EXTThreadLocalContext.alcSetThreadContext(context)) {
                throw new IllegalStateException("alcSetThreadContext failed");
            }
            alCapabilities = AL.createCapabilities(capabilities);
            available = true;
            ApricityUI.LOGGER.info("[AUI Audio] OpenAL backend initialized");
        } catch (Throwable throwable) {
            available = false;
            ApricityUI.LOGGER.warn("[AUI Audio] OpenAL backend unavailable, audio disabled", throwable);
            shutdownQuietly();
        } finally {
            if (threadLocalContextAvailable) restoreThreadContext(previousThreadContext, previousCapabilities);
            else AL.setCurrentThread(previousCapabilities);
        }
        return available;
    }

    synchronized void runWithContext(Runnable action) {
        withContext(() -> {
            action.run();
            return null;
        });
    }

    synchronized <T> T callWithContext(Supplier<T> action) {
        return withContext(action);
    }

    private <T> T withContext(Supplier<T> action) {
        long previousContext = EXTThreadLocalContext.alcGetThreadContext();
        ALCapabilities previousCapabilities = currentCapabilities();
        if (previousContext != context) {
            if (!EXTThreadLocalContext.alcSetThreadContext(context)) {
                throw new IllegalStateException("Could not activate AUI OpenAL context");
            }
            AL.setCurrentThread(alCapabilities);
        }
        try {
            return action.get();
        } finally {
            if (previousContext != context) restoreThreadContext(previousContext, previousCapabilities);
        }
    }

    private static ALCapabilities currentCapabilities() {
        try {
            return AL.getCapabilities();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static void restoreThreadContext(long previousContext, ALCapabilities previousCapabilities) {
        EXTThreadLocalContext.alcSetThreadContext(previousContext);
        AL.setCurrentThread(previousCapabilities);
    }

    private void shutdownQuietly() {
        try {
            if (context != 0L) {
                if (threadLocalContextAvailable && EXTThreadLocalContext.alcGetThreadContext() == context) {
                    EXTThreadLocalContext.alcSetThreadContext(0L);
                }
                ALC10.alcDestroyContext(context);
                context = 0L;
                alCapabilities = null;
                threadLocalContextAvailable = false;
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
        private final OpenAlAudioService service;
        private final int id;
        private final double durationSeconds;
        private boolean destroyed;

        OpenAlBuffer(OpenAlAudioService service, int id, double durationSeconds) {
            this.service = service;
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
            service.runWithContext(() -> AL10.alDeleteBuffers(id));
        }

        synchronized boolean isDestroyed() {
            return destroyed;
        }
    }
}
