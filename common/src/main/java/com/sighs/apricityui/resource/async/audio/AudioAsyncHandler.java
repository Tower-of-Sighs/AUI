package com.sighs.apricityui.resource.async.audio;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.media.AudioEngine;
import com.sighs.apricityui.media.DecodedAudio;
import com.sighs.apricityui.media.decoder.AudioDecoder;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.AbstractAsyncHandler;

import java.io.InputStream;
import com.sighs.apricityui.util.DataUri;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频异步加载：worker 线程读资源 + 解码（PCM），apply 阶段（client tick）
 * 创建后端 buffer。结构与 ImageAsyncHandler 对齐；构造即自注册进
 * AbstractAsyncHandler 全局表，资源 reload 时自动 clear（onBeforeClear
 * 停掉所有播放并销毁 buffer）。
 */
public final class AudioAsyncHandler extends AbstractAsyncHandler<AudioAsyncHandler.ApplyTask> {
    public static final AudioAsyncHandler INSTANCE = new AudioAsyncHandler();

    private static final long FAILED_RETRY_MS = 5_000L;
    private static final Map<String, AudioHandle> HANDLES = new ConcurrentHashMap<>();

    private AudioAsyncHandler() {
        super("audio", 64, 2, 1_500_000L, "ApricityUI-AudioWorker");
    }

    public AudioHandle request(String path) {
        if (path == null || path.isBlank() || "unset".equals(path)) return null;

        long generation = currentGeneration();
        long now = System.currentTimeMillis();
        AudioHandle handle = HANDLES.compute(path, (key, existing) -> {
            if (existing == null || existing.generation() != generation || existing.state() == AsyncState.STALE) {
                if (existing != null) existing.destroyBufferIfPresent();
                return new AudioHandle(key, generation);
            }
            if (existing.state() == AsyncState.FAILED && now - existing.failedAtMs() >= FAILED_RETRY_MS) {
                existing.reset(generation);
            }
            return existing;
        });
        submitDecodeIfNeeded(handle);
        return handle;
    }

    private void submitDecodeIfNeeded(AudioHandle handle) {
        if (handle == null || !handle.tryEnterLoading()) return;
        submitWorker(
                () -> decodeOnWorker(handle),
                ex -> {
                    ApricityUI.LOGGER.error("[AUI Audio] decode worker rejected path={}", handle.path(), ex);
                    handle.markFailed(ex, System.currentTimeMillis());
                }
        );
    }

    private void decodeOnWorker(AudioHandle handle) {
        DecodedAudio decoded;
        try {
            byte[] bytes;
            DataUri.Decoded data = DataUri.decode(handle.path());
            if (data != null) {
                bytes = data.bytes();
            } else {
                try (InputStream stream = ClientLoader.getResourceStream(handle.path())) {
                    if (stream == null) {
                        throw new IllegalStateException("未找到音频资源: " + handle.path());
                    }
                    bytes = stream.readAllBytes();
                }
            }
            decoded = AudioDecoder.decode(handle.path(), bytes);
            if (decoded == null) {
                String reason = AudioDecoder.rejectionReason(handle.path(), bytes);
                ApricityUI.LOGGER.warn("[AUI Audio] audio decode rejected path={} reason={}", handle.path(), reason);
                handle.markFailed(new IllegalStateException("音频解码失败: " + handle.path() + "（" + reason + "）"), System.currentTimeMillis());
                return;
            }
        } catch (Exception exception) {
            ApricityUI.LOGGER.error("[AUI Audio] audio load/decode failed path={}", handle.path(), exception);
            handle.markFailed(exception, System.currentTimeMillis());
            return;
        }
        if (handle.generation() != currentGeneration()) {
            handle.markStale();
            return;
        }
        if (!handle.tryEnterApplying()) return;
        enqueueApplyTask(new ApplyTask(handle, decoded, handle.generation()));
    }

    @Override
    protected void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.generation != currentGeneration || task.handle.generation() != task.generation) {
            task.handle.markStale();
            return;
        }

        // 音频输出后端（OpenAL）的 buffer 创建约定在 client 主线程执行。
        var buffer = AuiServices.audio().createBuffer(task.decoded);
        if (buffer == null) {
            task.handle.markFailed(new IllegalStateException("音频后端不可用: " + task.handle.path()), System.currentTimeMillis());
            return;
        }
        if (task.handle.state() != AsyncState.APPLYING) {
            buffer.destroy();
            return;
        }
        task.handle.markReady(buffer, task.decoded.durationSeconds);
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        // 先停掉所有播放（channel 持有 buffer 引用），再销毁 buffer。
        AudioEngine.stopAll();
        for (AudioHandle handle : HANDLES.values()) {
            handle.destroyBufferIfPresent();
            handle.markStale();
        }
        HANDLES.clear();
    }

    public record ApplyTask(AudioHandle handle, DecodedAudio decoded, long generation) {
    }
}
