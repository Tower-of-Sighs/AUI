package com.sighs.apricityui.task;

import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Operation;

import java.util.function.Supplier;

/**
 * 60Hz 固定节拍的 mousemove 引擎。
 *
 * <p>为什么不挂在渲染帧上直接分发：渲染帧率由显示器/场景决定（144Hz 屏会拖出
 * 144 次/秒分发，低帧率场景又反过来喂不满 60Hz）。本引擎以固定相位调度，
 * 渲染帧与 tick 只作为轮询载具——未到期时一次 {@code nanoTime} 比较即返回，
 * 到点才执行一次真正的分发。
 *
 * <p>为什么不另起线程：hitTest / 布局 / JS 回调 / DOM 全部约定在渲染线程上跑
 * （ThreadLocal 缓存遍布全引擎），跨线程分发不具可行性。渲染帧率低于 60 时
 * 退化为按帧率分发，这是同线程架构下的物理上限。
 *
 * <p>相位策略：到期时间按 {@code nextDue += STEP} 推进而非"距上次 ≥ 间隔"的
 * 节流——后者在 144Hz 下会别名到 48Hz（每 3 帧才凑够 16.7ms）。落后超过一个
 * 整节拍（低帧率/卡顿）时直接对齐到当前时刻，不补发堆积节拍：mousemove 是
 * coalesced 事件，只有最新位置有意义。
 */
public final class MouseMoveEngine {
    /** 固定节拍间隔：60Hz。 */
    private static final long STEP_NS = 1_000_000_000L / 60L;
    /** 下一次到期的 nanoTime；Long.MIN_VALUE 表示尚未启动，首次轮询立即分发。 */
    private static long nextDueNs = Long.MIN_VALUE;

    private MouseMoveEngine() {
    }

    /**
     * 由渲染帧 / tick 等高频钩子轮询；到点才取坐标并分发一次 mousemove。
     * 坐标经 Supplier 惰性求值，未到期时连坐标换算都不做。
     */
    public static void poll(Supplier<Position> mousePosition) {
        long now = System.nanoTime();
        if (nextDueNs == Long.MIN_VALUE) {
            nextDueNs = now;
        }
        // 有符号差值比较，nanoTime 回绕安全
        if (now - nextDueNs < 0) return;
        if (now - nextDueNs >= STEP_NS * 2) {
            // 落后太多：丢弃堆积节拍，从当前时刻重新对齐
            nextDueNs = now + STEP_NS;
        } else {
            nextDueNs += STEP_NS;
        }
        Operation.onMouseMoveFrame(mousePosition.get());
    }
}
