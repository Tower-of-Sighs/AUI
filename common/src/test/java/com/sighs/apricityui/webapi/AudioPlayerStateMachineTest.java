package com.sighs.apricityui.webapi;

import com.sighs.apricityui.media.AudioEngine;
import com.sighs.apricityui.media.AudioPlayer;
import com.sighs.apricityui.resource.async.audio.AudioAsyncHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AudioPlayer 状态机测试：headless 下 AuiServices 默认是时钟模拟后端，
 * position 按真实时钟推进，ended/loop/seek 语义可完整断言。
 * 音频源用 classpath 测试资源（0.5s 静音 WAV）。
 */
class AudioPlayerStateMachineTest {

    private static final String SRC = "apricityui/test-tone.wav"; // 0.5s 静音

    private List<String> events;
    private AudioPlayer player;

    @BeforeEach
    void setUp() {
        events = new CopyOnWriteArrayList<>();
        player = new AudioPlayer(events::add);
        AudioEngine.register(null, player);
    }

    @AfterEach
    void tearDown() {
        player.destroy();
        AudioEngine.clearForTest();
    }

    @Test
    void loadProgressionFiresSpecEventSequence() {
        player.setSrc(SRC);
        pumpUntil(() -> player.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);

        assertEquals(0.5, player.getDuration(), 0.01);
        // 规范序列：loadstart → durationchange → loadedmetadata → canplay → canplaythrough
        assertEventOrder("loadstart", "durationchange", "loadedmetadata", "canplay", "canplaythrough");
        assertEquals(AudioPlayer.NETWORK_IDLE, player.getNetworkState());
    }

    @Test
    void preloadNoneDefersLoadingUntilExplicitLoad() {
        player.setPreload("none");
        player.setSrc(SRC);
        assertEquals(AudioPlayer.HAVE_NOTHING, player.getReadyState(), "preload=none 不得自动加载");
        assertTrue(events.isEmpty(), "未加载前不得派发媒体事件");

        player.load();
        pumpUntil(() -> player.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);
        assertEquals(0.5, player.getDuration(), 0.01);
    }

    @Test
    void playResolvesPromiseAndEndsAtDuration() throws Exception {
        player.setSrc(SRC);
        pumpUntil(() -> player.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);
        events.clear();

        boolean[] resolved = {false};
        player.play().then(() -> resolved[0] = true);
        assertTrue(resolved[0], "已就绪时 play() 必须立即 resolve");
        assertFalse(player.isPaused());
        assertEventOrder("play", "playing");

        // 播到尾（0.5s 真实时钟 + 余量）→ ended
        long deadline = System.currentTimeMillis() + 3000;
        while (!player.isEnded() && System.currentTimeMillis() < deadline) {
            player.tick();
            Thread.sleep(20);
        }
        assertTrue(player.isEnded(), "播完必须进入 ended 状态");
        assertTrue(player.isPaused(), "ended 后 paused=true");
        assertTrue(events.contains("ended"));
    }

    @Test
    void loopRewindsInsteadOfEnding() throws Exception {
        player.setSrc(SRC);
        player.setLoop(true);
        pumpUntil(() -> player.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);

        player.play();
        // 播过一个完整时长：loop 下不得 ended，position 回卷
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            player.tick();
            if (player.getCurrentTime() < 0.2 && System.currentTimeMillis() > deadline - 2400) break;
            Thread.sleep(20);
        }
        assertFalse(player.isEnded(), "loop 不得触发 ended");
        assertFalse(events.contains("ended"));
        assertFalse(player.isPaused(), "loop 必须继续播放");
    }

    @Test
    void seekClampsToDurationAndFiresSeekingSeeked() {
        player.setSrc(SRC);
        pumpUntil(() -> player.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);
        events.clear();

        player.setCurrentTime(999);
        assertEquals(0.5, player.getCurrentTime(), 0.05, "seek 超时长必须钳到 duration");

        player.setCurrentTime(0.25);
        assertEquals(0.25, player.getCurrentTime(), 0.05);
        int seekingIndex = events.indexOf("seeking");
        int seekedIndex = events.indexOf("seeked");
        assertTrue(seekingIndex >= 0 && seekedIndex > seekingIndex, "必须先 seeking 后 seeked");
    }

    @Test
    void playBeforeReadyLoadsThenStarts() {
        player.setSrc(SRC);
        boolean[] resolved = {false};
        player.play().then(() -> resolved[0] = true);
        assertFalse(resolved[0], "未就绪时 play() 不得同步 resolve");

        pumpUntil(() -> resolved[0], 5000);
        assertFalse(player.isPaused(), "就绪后必须自动开播");
    }

    @Test
    void invalidSourceRejectsPlayAndFiresError() {
        player.setSrc("apricityui/definitely-missing.wav");
        boolean[] rejected = {false};
        player.play().catchError(reason -> rejected[0] = true);
        pumpUntil(() -> rejected[0], 5000);
        assertTrue(events.contains("error"), "加载失败必须派发 error");
        assertEquals(AudioPlayer.HAVE_NOTHING, player.getReadyState());
    }

    @Test
    void volumeAndMutedClampAndNotify() {
        events.clear();
        player.setVolume(0.5);
        player.setVolume(1.7);
        assertEquals(1.0, player.getVolume(), 0.0001, "volume 必须钳到 1");
        player.setVolume(-3);
        assertEquals(0.0, player.getVolume(), 0.0001, "volume 必须钳到 0");
        player.setMuted(true);
        assertTrue(player.isMuted());
        assertEquals(4, events.stream().filter("volumechange"::equals).count(),
                "每次有效变更各派一次 volumechange");
        // 无变化不重复派发
        player.setMuted(true);
        player.setVolume(0.0);
        assertEquals(4, events.stream().filter("volumechange"::equals).count(),
                "值未变化时不得重复派发 volumechange");
    }

    @Test
    void emptySrcEntersNoSourceState() {
        player.setSrc("");
        assertEquals(AudioPlayer.NETWORK_NO_SOURCE, player.getNetworkState());
        boolean[] rejected = {false};
        player.play().catchError(reason -> rejected[0] = true);
        assertTrue(rejected[0], "无源 play() 必须 reject");
    }

    // --------------------------------------------------------------

    /** 泵异步加载：worker 解码在后台线程，apply 队列需手动 tick。 */
    private void pumpUntil(Check condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.met() && System.currentTimeMillis() < deadline) {
            AudioAsyncHandler.INSTANCE.tickApplyQueue();
            player.tick();
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.met(), "泵超时：条件未达成");
    }

    private void assertEventOrder(String... expectedSequence) {
        int cursor = -1;
        for (String expected : expectedSequence) {
            int index = events.indexOf(expected);
            assertTrue(index > cursor, "事件 " + expected + " 缺失或顺序错误，实际序列: " + events);
            cursor = index;
        }
    }

    private interface Check {
        boolean met();
    }
}
