package com.sighs.apricityui.init;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;

/**
 * AUI 的帧调度器。
 * <p>
 * 目的不是去做功能，而是把 tick/render 的生命周期边界固定下来：
 * <ul>
 *     <li>tick（逻辑线程）：输入/DOM commit/异步资源 apply/样式/布局/绘制队列更新</li>
 *     <li>render（渲染线程）：只读稳定的渲染数据并发出 draw call</li>
 * </ul>
 */
public final class FrameScheduler {
    private FrameScheduler() {
    }

    public static void tick() {
        // 1) Drain async apply tasks (style/image decode -> apply)
        StyleAsyncHandler.INSTANCE.tickApplyQueue();
        ImageAsyncHandler.INSTANCE.tickApplyQueue();

        // 2) Document commit / style flush / layout + paint-list updates
        for (Document document : Document.getAll()) {
            if (document == null) continue;
            document.tickFrame();
        }
    }

    /**
     * render 入口（渲染线程）。
     * <p>
     * 当前主要用于 drain RenderSystem 的 fenced tasks（例如图片纹理上传）。
     * 未来可在此处接入更多“渲染帧级别”的 begin/end 管理。
     */
    public static void renderBegin() {
        RenderSystem.executePendingTasks();
    }
}
