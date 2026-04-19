# Sprite Element Guide

`<sprite>` is used for sprite-sheet frame animation. It is suitable for effects such as character running cycles or animated item frames.

## 1. Basic Example

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

## 2. Attributes

| Attribute | Required | Default | Description |
|---|---|---|---|
| `src` | Yes | - | Path to the sprite-sheet resource. |
| `steps` | Recommended | - | Total frame count. Must be a positive integer. |
| `frameW` | Recommended | - | Width of each frame in px. |
| `frameH` | Recommended | - | Height of each frame in px. |
| `duration` | No | `1s` | Animation duration, supporting `s` or `ms`, such as `1.2s` or `900ms`. |
| `direction` | No | `right` | Frame progression direction: `right / left / up / down`. |
| `loop` | No | `infinite` | Loop count: `infinite` or a positive integer. |
| `steps-mode` | No | `end` | `steps()` mode: `start` or `end`. |
| `autoplay` | No | `true` | Whether to play automatically. `false/0/no/off` disables it. |
| `initialFrame` | No | `0` | Initial frame index, non-negative integer. |
| `fit` | No | `none` | Background fitting mode: `none / contain / cover / stretch`. |

## 3. Behavior Rules

1. Frame animation only starts when `src + steps + frameW + frameH` are all valid.
2. If parameters are missing or invalid, it falls back to a static display using the first frame or the `initialFrame`.
3. `duration` controls playback speed.
4. If the element also has other animations, `sprite` controls the frame-animation segment and other animations may coexist.
5. Properties in `style` that conflict with sprite-managed output, such as `background-*` or `animation*`, are overridden by the sprite calculation result.

## 4. Practical Recommendations

1. Use equal-width and equal-height frames to avoid visible jitter.
2. Keep `steps` consistent with the real frame count in the asset.
3. For looping assets, prefer `loop="infinite"`. For one-shot assets, use a finite number.
4. If you want the initial frame to stay visible, set `autoplay="false"` and combine it with `initialFrame`.
