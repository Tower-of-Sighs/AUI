# Sprite 元素说明

`<sprite>` 用于播放精灵图（Sprite Sheet）帧动画，适合做角色跑步、物品循环帧等效果。

## 1. 基本示例

```html
<sprite
    src="lava_bucket.png"
    steps="24"
    frameW="48"
    frameH="48"
    duration="1.2s"
    direction="right"
    loop="infinite"
    steps-mode="end"
    autoplay="true"
    initialFrame="0"
    fit="none">
</sprite>
```

## 2. 属性列表

| 属性 | 是否必填 | 默认值 | 说明 |
|---|---|---|---|
| `src` | 是 | - | 精灵图资源路径。 |
| `steps` | 建议是 | - | 总帧数，需为正整数。 |
| `frameW` | 建议是 | - | 单帧宽度（px，正整数）。 |
| `frameH` | 建议是 | - | 单帧高度（px，正整数）。 |
| `duration` | 否 | `1s` | 动画时长，支持 `s/ms`，如 `1.2s`、`900ms`。 |
| `direction` | 否 | `right` | 帧推进方向：`right / left / up / down`。 |
| `loop` | 否 | `infinite` | 循环次数：`infinite` 或正整数。 |
| `steps-mode` | 否 | `end` | `steps()` 模式：`start` 或 `end`。 |
| `autoplay` | 否 | `true` | 是否自动播放。`false/0/no/off` 视为关闭。 |
| `initialFrame` | 否 | `0` | 初始帧索引（非负整数）。 |
| `fit` | 否 | `none` | 背景适配：`none / contain / cover / stretch`。 |

## 3. 行为规则

1. 只有在 `src + steps + frameW + frameH` 均有效时，才会启动帧动画。
2. 参数不足或非法时，会降级为静态展示（首帧或 `initialFrame` 对应帧）。
3. `duration` 表示动画时长，用于控制播放速度。
4. 当元素同时存在其他动画时，`sprite` 负责帧动画段，其他动画可继续并存。
5. `style` 中与 `sprite` 托管冲突的键（`background-*`、`animation*`）会以 `sprite` 计算结果为准。

## 4. 实践建议

1. 统一使用等宽等高帧，避免位移抖动。
2. `steps` 与素材真实帧数保持一致，避免最后一帧跳变。
3. 对循环素材建议使用 `loop="infinite"`，对一次性素材可使用有限次数。
4. 如果需要首帧停留展示，设置 `autoplay="false"` 并配合 `initialFrame`。
