package com.sighs.apricityui.spi;

import com.sighs.apricityui.media.DecodedAudio;

/**
 * 音频输出后端 SPI。common 侧完成资源加载与 OGG/WAV 解码（PCM），
 * 本接口只负责把 PCM 变成可播放的 buffer/channel。
 * <p>
 * headless 默认实现是"时钟模拟后端"（AuiServices.Defaults.AUDIO）：
 * 不出声，但 position/play/pause/seek 语义完整，使 AudioPlayer 状态机
 * 可以在无音频设备的测试 JVM 里完整测试。游戏内由 target bootstrap
 * 注册 OpenAL 真后端。
 */
public interface AuiAudioService {

    /** 由解码后的 PCM 创建音频 buffer；失败返回 null（不抛异常）。 */
    AudioBufferHandle createBuffer(DecodedAudio audio);

    /** 在 buffer 上打开一个独占 channel；后端不可用返回 null。 */
    AudioChannel openChannel(AudioBufferHandle buffer);

    /** 平台主音量（0..1），播放器每帧乘进有效音量。 */
    default float masterVolume() {
        return 1.0f;
    }

    interface AudioBufferHandle {
        double durationSeconds();

        void destroy();
    }

    interface AudioChannel {
        void play();

        void pause();

        void stop();

        void seekSeconds(double seconds);

        double positionSeconds();

        void setVolume(float volume);

        boolean isPlaying();

        void destroy();
    }
}
