# 扩展元素

AUI 在标准 HTML 之外注册了一组扩展标签，都是普通 DOM 元素，能正常参与 CSS、布局、命中测试和脚本操作。它们解决一类共同问题：**把游戏资源和动画画进页面**。

标准元素的能力边界见 [HTML/CSS 覆盖面](html-css-coverage)，容器/槽位/配方见[容器文档](container)，注册自己的元素见[二次开发文档](secondary-development)，这里都不重复。

## 怎么选

| 需求 | 用 |
| --- | --- |
| 页面目录里的静态图（PNG/GIF/WebP） | 标准 `<img>` / CSS `background-image` |
| Minecraft 注册表纹理（物品、方块贴图） | `<texture>` |
| 图集序列帧动画 | `<sprite>` |
| 本地化文本 | `<translation>` |
| 矢量图标、线条、曲线 | `<svg>` |
| 逐像素、图表、每帧重算的画面 | `<canvas>` |
| 物品槽、背包、配方预览 | `<container>` / `<slot>` / `<recipe>`（容器文档） |

所有自定义绘制元素都没有固有尺寸，记得用 CSS 或属性给稳定的 `width`/`height`，否则资源异步就绪后布局会跳。别在脚本里每帧 `refresh()` 等资源——资源就绪后框架会标记重绘。

## texture：Minecraft 纹理

```html
<texture src="minecraft:textures/item/diamond.png" style="width: 32px; height: 32px;"></texture>
<texture src="examplemod:textures/block/machine.png" blur="true"></texture>
```

- `src` 是 `namespace:path` 形式的 ResourceLocation，直接走 MC 纹理系统，**不是**页面逻辑路径，也不是磁盘路径——页面目录里的图请用 `<img>` 或 `<sprite>`；
- `blur` 只有精确写 `true` 才开启模糊绘制；
- src 无效或元素尺寸为零时不画纹理，但 CSS 背景、边框照常绘制；改 `src` 立即生效。

## sprite：图集序列帧

把一张横向或纵向图集播成 `steps()` 帧动画，适合加载圈、按钮状态、粒子、待机动作：

```html
<sprite class="coin"
        src="images/coin-strip.png"
        steps="8"
        direction="right"
        duration="640ms"
        loop="infinite"></sprite>
```

`src` 按当前 HTML 的**逻辑路径**解析（`screens/home.html` 里的 `images/x.png` 解析为 `screens/images/x.png`，回资源根用 `/images/x.png`），也可以是 HTTPS 图片。这和 texture 的 ResourceLocation 正好相反，别搞混。

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `steps` | 无 | 正整数帧数；缺失或非法时按静态背景处理 |
| `direction` | `right` | `right`/`left`/`up`/`down`，同时决定图集排布和帧移动方向 |
| `duration` | `1s` | CSS 时间值（`250ms`、`1.5s`） |
| `loop` | `infinite` | `infinite` 或正整数播放次数 |
| `steps-mode` | `end` | `start` / `end`，对应 CSS `steps()` 的时序模式 |
| `autoplay` | `true` | `false`/`0`/`no`/`off` 关闭；关闭时仍显示 `initialframe` 那一帧 |
| `initialframe` | `0` | 起始帧，越界钳到最后一帧 |
| `fit` | `none` | `none`/`contain`/`cover`/`stretch`，映射到 background-size |

横向图集单帧尺寸 = 宽/steps × 高，纵向反之——图集每帧尺寸必须一致，否则推导会错。

**注意**：Sprite 会在 inline style 里托管 `background-image/position/size/repeat` 和 `animation` 系属性，覆盖你手写的同名 inline 声明（其他 CSS 动画会合并保留）。想完全自己控制背景动画，别用 sprite，用普通 div + CSS。

## translation：本地化文本

```html
<translation>container.apricityui.title</translation>
```

文本内容就是 MC 翻译键，按当前语言渲染。textContent 是键本身。没有参数插值——带参数的翻译在脚本侧拼好再用普通文本元素。

## svg / path：矢量图形

SVG 子集，4x 超采样栅格化成位图，适合图标和简单装饰：

```html
<svg viewBox="0 0 64 64" width="64" height="64" style="color: #7dd3fc;">
    <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="4"></circle>
    <path d="M18 34 L28 44 L47 22 Z" fill="currentColor"></path>
</svg>
```

- 子图形：`circle`、`ellipse`、`rect`、`line`、`polyline`、`polygon`、`path`；
- 绘制属性：`fill`/`stroke`（含 `none`）、`stroke-width`、`stroke-linecap/join`、`opacity` 三件套、`fill-rule`（支持 evenodd）、`currentColor`（读元素或祖先的 `color`）；属性可从父级继承；
- path 命令：M/L/H/V/C/S/Q/T/A/Z，大小写都支持；
- `viewBox` 是内部坐标系，元素尺寸用 SVG 属性或 CSS 给；
- **没有** defs/use、渐变、滤镜、遮罩、文本、transform、外部 SVG。复杂图标导出时拍平成纯 path，或换 canvas。

SVG 会缓存栅格结果，属性或子树变化才重画。大尺寸复杂路径的栅格化成本不低，配合 frameTimingHud 观察。

## canvas：脚本绘制

```html
<canvas id="chart" width="320" height="160"></canvas>
```

标准 Canvas 2D 子集（Java2D 后端）。`width`/`height` 属性设位图尺寸，CSS 设显示尺寸，两者不同会缩放——别只改 CSS 就当坐标系变了。完整的 API 支持度和限制见 [Web API 文档](web-api)。

大画布频繁重绘有上传成本。静态矢量用 svg，游戏纹理用 texture——canvas 是这几个里最贵的，只留给真正需要逐帧计算的画面。

## 常见问题

**texture 不显示**：`src` 写成逻辑路径或文件路径了，必须是 `namespace:path`。要显示页面目录里的图用 img。

**sprite 不动**：steps 和图集实际帧数对不上、direction 和图集排布方向不符、元素没有尺寸。图集没加载完前会先显示静态背景，属正常。

**sprite 的样式被我写的 background 覆盖了 / 反过来**：托管属性只覆盖同名 inline style，样式表里的不受影响。排查时先想到托管清单。

**translation 显示原键**：键不存在或语言文件没这条，和原版行为一致。

**svg 的渐变/分组没画**：不支持。拍平成 path 或换方案。

**刷新后 canvas 内容没了**：刷新重建 DOM，位图跟着清。绘制逻辑放初始化函数，`DOMContentLoaded`/`load` 里重跑。
