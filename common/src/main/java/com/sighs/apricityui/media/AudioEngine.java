package com.sighs.apricityui.media;

import com.sighs.apricityui.init.Document;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 音频引擎：document → players 注册表 + 每帧驱动。
 * <p>
 * FrameScheduler.tick() 每帧调 {@link #tick()} 推进所有 player
 * （句柄轮询/ended 检测/timeupdate 节流）。文档关闭时
 * Document.disposeLifecycle() 调 {@link #releaseDocument(Document)}
 * 停止并释放该文档的全部播放。资源 reload 时 AudioAsyncHandler
 * 调 {@link #stopAll()}（buffer 即将销毁，channel 必须先停）。
 */
public final class AudioEngine {
    private static final Map<Document, List<AudioPlayer>> PLAYERS = new ConcurrentHashMap<>();
    /** 无 document 归属的游离 player（new Audio() 未挂树/测试直建）。 */
    private static final List<AudioPlayer> DETACHED = new CopyOnWriteArrayList<>();

    private AudioEngine() {
    }

    public static void register(Document document, AudioPlayer player) {
        if (player == null) return;
        if (document == null) {
            if (!DETACHED.contains(player)) DETACHED.add(player);
            return;
        }
        List<AudioPlayer> list = PLAYERS.computeIfAbsent(document, key -> new CopyOnWriteArrayList<>());
        if (!list.contains(player)) list.add(player);
    }

    public static void unregister(Document document, AudioPlayer player) {
        if (player == null) return;
        if (document == null) {
            DETACHED.remove(player);
            return;
        }
        List<AudioPlayer> list = PLAYERS.get(document);
        if (list != null) list.remove(player);
    }

    public static void tick() {
        for (List<AudioPlayer> list : PLAYERS.values()) {
            for (AudioPlayer player : list) player.tick();
        }
        for (AudioPlayer player : DETACHED) player.tick();
    }

    /** 文档销毁：停止并释放其全部 player。 */
    public static void releaseDocument(Document document) {
        List<AudioPlayer> list = PLAYERS.remove(document);
        if (list == null) return;
        for (AudioPlayer player : list) player.destroy();
    }

    /** 资源 reload：停掉所有播放（保留 src，可重新 load）。 */
    public static void stopAll() {
        for (List<AudioPlayer> list : PLAYERS.values()) {
            for (AudioPlayer player : list) player.stopForResourceClear();
        }
        for (AudioPlayer player : DETACHED) player.stopForResourceClear();
    }

    /** 测试用：清空全部注册表。 */
    public static void clearForTest() {
        PLAYERS.clear();
        DETACHED.clear();
    }
}
