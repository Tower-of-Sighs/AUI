package com.sighs.apricityui.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** `<audio controls>` 控件条的 headless 测试：命中区域换算 + 时间格式化（纯几何，不触 GL）。 */
class AudioControlsTest {

    // 布局常量：pad=4, button=min(20, h-8), gap=6, timeWidth=62
    // rect = (100, 50, 240, 28)：按钮 (104,54) 20x20；进度带 x∈[130, 274]

    @Test
    void hitTestFindsButton() {
        assertEquals(Audio.HIT_BUTTON, Audio.hitTestControls(104, 54, 100, 50, 240, 28), "按钮左上角");
        assertEquals(Audio.HIT_BUTTON, Audio.hitTestControls(124, 74, 100, 50, 240, 28), "按钮右下角");
        assertEquals(Audio.HIT_BUTTON, Audio.hitTestControls(114, 64, 100, 50, 240, 28), "按钮中心");
    }

    @Test
    void hitTestFindsTrack() {
        assertEquals(Audio.HIT_TRACK, Audio.hitTestControls(130, 64, 100, 50, 240, 28), "进度带左端");
        assertEquals(Audio.HIT_TRACK, Audio.hitTestControls(274, 64, 100, 50, 240, 28), "进度带右端");
        assertEquals(Audio.HIT_TRACK, Audio.hitTestControls(200, 50, 100, 50, 240, 28), "进度带整高度可点（含顶边）");
        assertEquals(Audio.HIT_TRACK, Audio.hitTestControls(200, 78, 100, 50, 240, 28), "含底边");
    }

    @Test
    void hitTestMissesPaddingGapAndTimeText() {
        assertEquals(Audio.HIT_NONE, Audio.hitTestControls(102, 64, 100, 50, 240, 28), "左 padding");
        assertEquals(Audio.HIT_NONE, Audio.hitTestControls(127, 64, 100, 50, 240, 28), "按钮与进度带之间缝隙");
        assertEquals(Audio.HIT_NONE, Audio.hitTestControls(300, 64, 100, 50, 240, 28), "时间文本区不响应");
        assertEquals(Audio.HIT_NONE, Audio.hitTestControls(200, 90, 100, 50, 240, 28), "元素外");
    }

    @Test
    void hitTestShrinksButtonOnShortBox() {
        // 高度 10 → 按钮边长 min(20, 10-8)=2，位于 (104, 54)；进度带起点 x=112
        assertEquals(Audio.HIT_BUTTON, Audio.hitTestControls(105, 55, 100, 50, 240, 10));
        assertEquals(Audio.HIT_NONE, Audio.hitTestControls(108, 55, 100, 50, 240, 10),
                "小按钮之外、进度带起点之前");
        assertEquals(Audio.HIT_TRACK, Audio.hitTestControls(120, 55, 100, 50, 240, 10),
                "进度带随小按钮左移");
    }

    @Test
    void formatTimeRendersMinutesAndPlaceholder() {
        assertEquals("0:00", Audio.formatTime(0));
        assertEquals("0:05", Audio.formatTime(4.6));
        assertEquals("1:05", Audio.formatTime(65.4));
        assertEquals("10:00", Audio.formatTime(600));
        assertEquals("--:--", Audio.formatTime(Double.NaN), "未就绪时长显示占位符");
        assertEquals("--:--", Audio.formatTime(-3), "负值显示占位符");
    }
}
