package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Audio;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.media.AudioEngine;
import com.sighs.apricityui.media.AudioPlayer;
import com.sighs.apricityui.resource.async.audio.AudioAsyncHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `<audio>` 元素集成测试：真实 DOM（TestDocumentFactory）+ headless 时钟后端。
 * 覆盖 src 属性反射→加载、readyState 迁移、DOM 媒体事件桥、
 * Window.createAudio 游离实例（new Audio() 的 Java 侧）。
 */
class AudioElementTest {

    /** 前导 /：Loader.resolve 对 test:// 上下文不做相对拼接，直接走 classpath 资源根。 */
    private static final String SRC = "/apricityui/test-tone.wav"; // 0.5s 静音

    @AfterEach
    void tearDown() {
        AudioEngine.clearForTest();
    }

    @Test
    void srcAttributeReflectionTriggersLoad() {
        Document document = TestDocumentFactory.createDocument();
        Audio audio = new Audio(document);
        document.body.appendChild(audio);
        List<String> events = new CopyOnWriteArrayList<>();
        audio.addEventListener("loadstart", e -> events.add("loadstart"));
        audio.addEventListener("loadedmetadata", e -> events.add("loadedmetadata"));
        audio.addEventListener("canplay", e -> events.add("canplay"));
        audio.addEventListener("canplaythrough", e -> events.add("canplaythrough"));

        audio.setAttribute("src", SRC);
        assertEquals(AudioPlayer.HAVE_NOTHING, audio.getReadyState(), "tick 前不得加载");

        audio.tick(); // tick 观察 src 属性 → setSrc → 自动加载
        pumpUntil(() -> audio.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);

        assertEquals(0.5, audio.getDuration(), 0.01);
        assertEquals(AudioPlayer.NETWORK_IDLE, audio.getNetworkState());
        assertEventOrder(events, "loadstart", "loadedmetadata", "canplay", "canplaythrough");
    }

    @Test
    void attributeReflectionHonorsPreloadNoneAndLoop() {
        Document document = TestDocumentFactory.createDocument();
        Audio audio = new Audio(document);
        document.body.appendChild(audio);
        audio.setAttribute("src", SRC);
        audio.setAttribute("preload", "none");
        audio.setAttribute("loop", "");

        audio.tick();
        assertEquals(AudioPlayer.HAVE_NOTHING, audio.getReadyState(), "preload=none 不得自动加载");
        assertTrue(audio.isLoop(), "loop 属性必须反射到播放器");

        audio.load();
        pumpUntil(() -> audio.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);
        assertEquals(0.5, audio.getDuration(), 0.01);
    }

    @Test
    void detachedFactoryInstancePlaysWithoutTreeMembership() {
        Document document = TestDocumentFactory.createDocument();
        Window window = new Window();
        Audio audio = window.createAudio(document, SRC);
        // 不入树：tick 不会被调用，play() 内部同步属性后开播
        List<String> events = new CopyOnWriteArrayList<>();
        audio.addEventListener("playing", e -> events.add("playing"));

        boolean[] resolved = {false};
        audio.play().then(() -> resolved[0] = true);
        pumpUntil(() -> resolved[0], 5000);

        assertFalse(audio.isPaused(), "就绪后必须自动开播");
        assertTrue(events.contains("playing"), "游离实例也必须走 DOM 事件桥");
    }

    @Test
    void playPauseSeekDelegateToPlayer() {
        Document document = TestDocumentFactory.createDocument();
        Audio audio = new Audio(document);
        document.body.appendChild(audio);
        audio.setAttribute("src", SRC);
        audio.tick();
        pumpUntil(() -> audio.getReadyState() >= AudioPlayer.HAVE_ENOUGH_DATA, 5000);

        audio.play();
        assertFalse(audio.isPaused());
        audio.pause();
        assertTrue(audio.isPaused());

        audio.setCurrentTime(0.25);
        assertEquals(0.25, audio.getCurrentTime(), 0.05);
        audio.setCurrentTime(999);
        assertEquals(0.5, audio.getCurrentTime(), 0.05, "seek 必须钳到 duration");

        audio.setVolume(1.7);
        assertEquals(1.0, audio.getVolume(), 0.0001);
        audio.setMuted(true);
        assertTrue(audio.isMuted());
    }

    // --------------------------------------------------------------

    /** 泵异步加载 + AudioEngine 帧驱动（元素播放器注册在 AudioEngine，不走元素 tick）。 */
    private void pumpUntil(Check condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.met() && System.currentTimeMillis() < deadline) {
            AudioAsyncHandler.INSTANCE.tickApplyQueue();
            AudioEngine.tick();
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.met(), "泵超时：条件未达成");
    }

    private void assertEventOrder(List<String> events, String... expectedSequence) {
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
