# HTML / CSS 覆盖面

AUI 是自研的 HTML/CSS 引擎，不是内嵌浏览器。这篇回答一个问题：**哪些网页写法能直接搬过来，哪些会被静默降级或忽略**。

三档标注：

| 标记 | 含义 |
| --- | --- |
| ✅ | 行为与浏览器基本一致 |
| 🟡 | 部分支持：能解析、有行为，但与规范有明确差距 |
| ❌ | 不支持：设置后被忽略（CSS 属性会 warn 一次） |

总体画像：选择器层最完整；布局是"常用子集 + 关键缺失"（没 float、没表格布局、没 sticky）；绘制层覆盖挺广（阴影、滤镜、clip-path、transform、动画都行）；HTML 层面向 UI 而非文档——**没有 UA 默认样式**，`h1` 和 `div` 视觉上没区别。

## 迁移速查

赶时间只看这一节。把浏览器页面搬进 AUI 前，按优先级检查：

1. **UA 样式为零**：h1-h6、p、ul/li 的样式全部自己写；别用 br/hr 表达布局；
2. **布局避开**：float、sticky、flex 的 order/reverse/align-content/baseline、grid 的 areas/auto-flow/命名线、负 margin、表格布局；
3. **值解析避开**：calc 乘除、border 虚线等样式（只有实线）、currentColor、26 个以外的命名色、radial/conic 渐变、contrast/saturate/sepia 滤镜、skew/matrix transform；
4. **层叠注意**：内联 style 会覆盖样式表的 `!important`（和浏览器相反）；
5. **文本注意**：italic 无效（用 oblique）、justify 无效、vertical-align 只有 baseline 有效、text-decoration 只有下划线/删除线；
6. **表单注意**：date/email/url 等 type 降级为纯文本框；form 提交只发事件不发请求；
7. **transition 白名单**：名单外属性（gap、grid-template、font-size 等）直接跳变。

## HTML 解析器

基于正则的分词器，不是标准 tree builder。

- ✅ 属性各种写法、注释、doctype 剥离、自闭合与 void 标签、script/style raw text、总是合成 html/head/body；
- 🟡 命名实体只有 `amp apos gt lt nbsp quot` 六个 + 数字实体；有弹栈错误恢复，但**没有浏览器的隐含标签生成**——不会自动闭合 `<p>`、不补 `<tbody>`；
- 🟡 `<script>` 带 src 又写内联时**两个都执行**（非标准）；没有 defer/async/module；
- `<head>` 子节点（title 等）不进 DOM，没有 `document.title`；`<meta>` 只读三个 aui-* 专用配置（见 [meta 章节](apricity-screen#页面-meta-配置)），charset 固定 UTF-8。

## HTML 元素

**有专用实现的标签**：html/head/body、div/span、pre、textarea（多行编辑 + 拖拽 resize）、select/option/optgroup（弹出层 + 完整键盘操作）、canvas（仅 2d）、img（异步加载、load/error；**无 alt 渲染、无 srcset**）、a（仅点击用系统浏览器打开 href）、svg/path（子集）。

**input 按 type 分档**：

| type | 支持度 |
| --- | --- |
| text（含未识别兜底） | ✅ 编辑、选区、placeholder、maxlength、undo |
| number / range | ✅ spinner、滑轨、min/max/step |
| checkbox / radio | ✅ 自绘控件、组互斥 |
| color | ✅ 框架 ColorPicker |
| password | 🟡 只把渲染替换成 `*` |
| file | 🟡 系统文件对话框，但只拿到路径字符串 |
| button / submit / reset / image | 🟡 统一按钮模式，image 不显示图 |
| hidden | ✅ |
| date / email / url / tel / search / time 等 | ❌ 全部降级为 text，无各自 UI 和校验 |

**表单**：submit/requestSubmit/reset、约束校验、FormData 收集、label 关联、fieldset disabled 级联、`form=id` 外部关联都 ✅；action 提交和导航 ❌（只触发事件）。

**无专用类的标签**：p/h1-h6/ul/ol/li/table 等按通用 block/inline 处理，**无 UA 样式**；table 没有表格布局，`display:table` 也降级为 block；br/hr 基本别用；iframe/video/audio/object/embed 无实现。

**UA 默认样式表**全部内容：约 30 个标签是 inline（a、b、i、code、img、input 等），head/script/style/title/meta/option 等 display:none，其余一切 block。没了。

扩展标签（texture、sprite、container、slot、recipe、translation 等）见[扩展元素文档](extension-elements)。未知标签按通用 Element 渲染，不警告。

## CSS 选择器

✅ 几乎全部常用：基础选择器、四种组合器、属性选择器（含 `i`/`s` 标志）、结构伪类全家（nth-child 含 An+B）、状态伪类（hover/active/focus/focus-within/disabled/checked 等）、表单伪类、`:not()/:is()/:where()`、`::before/::after`、正确的 specificity 和 `!important` 层叠。

🟡 `:focus-visible` 退化为 `:focus`；`:nth-child()` 不支持 `of S`；**内联 style 会覆盖样式表 `!important`（规范相反），内联 `!important` 本身不生效**；不支持的伪类 warn 一次后按不匹配处理。

❌ `:link :visited :target :lang() :dir() :has()`、`::first-line ::first-letter ::marker ::selection`。

## @规则与样式来源

- ✅ style/link/内联三来源层叠、`@keyframes`、`@import`（递归内联、有深度上限和循环检测）、简写展开、css-wide 关键字、CSS 变量 `var()`（含 fallback，嵌套深度 8）；
- 🟡 `@media` 只有 min/max-width/height、orientation + `and`；`@font-face` 只取 font-family 和第一个 src url()，忽略 format()、多 src 和其他描述符；
- ❌ `@supports @layer @page @container @scope`。

## 布局

**display**：block/inline/inline-block/flex/inline-flex/grid/inline-grid/none ✅；table/list-item/flow-root 降级 block；contents 等未知值一律 block。

**盒模型**：

- ✅ margin/padding（含 auto 居中）、margin 折叠、border 简写、border-radius（含椭圆双半径）、border-image 九宫格、box-shadow（多重、inset）、box-sizing、宽高 min/max、aspect-ratio、px/%/em/rem/vw/vh、min()/max()/clamp()；
- 🟡 **负 margin 被钳成 0**；calc() **只支持加减**，不能乘除嵌套；
- ❌ border-style（全部画成实线）、border-width 关键字（thin/medium/thick）、fit-content/min-content/max-content、vmin/vmax/ch/ex、物理单位。

**定位**：static/relative/fixed/absolute ✅（absolute 包含块规则正常；已知偏差：双侧 auto 锚定包含块原点而非静态位置，transform/filter 祖先不形成包含块）；z-index ✅；**sticky ❌、float/clear ❌**。

**Flexbox**：row/column、wrap、justify-content 六值、grow/shrink/basis、gap、auto margin、匿名 item 都 ✅；align-items 里 **baseline 退化为 flex-start**；❌ **row-reverse/column-reverse（静默忽略）、wrap-reverse、align-content（解析但不生效）、order**。

**Grid**（MVP）：template-columns/rows（px/auto/fr/minmax/repeat 含 auto-fill/fit）、gap、items/self 对齐、`grid-row/column` 的 `N`、`span N`、`N / M`、自动放置 ✅；❌ 命名线、template-areas、auto-flow、隐式轨道、place-* 简写、subgrid。

**行内**：inline/inline-block 换行、基线对齐 ✅；**vertical-align 只有 baseline 有真实效果**，其余关键字静默无效。

**滚动**：overflow 五值 ✅（clip 裁剪不可滚）、自绘滚动条 ✅；`scroll-behavior` 属性不解析（平滑滚动是内建的）；scrollbar-width/color、scroll-snap 等 ❌。

## 绘制与视觉

| 属性 | 支持度 | 备注 |
| --- | --- | --- |
| color、background-color、opacity | ✅ | |
| background-image | 🟡 | url()、linear-gradient、多背景分层；无 radial/conic |
| background-repeat | 🟡 | `space` 被当普通 repeat |
| background-size/position/简写 | ✅ | |
| background-attachment/origin/clip/blend-mode | ❌ | |
| object-fit / object-position | ✅ | |
| visibility | 🟡 | collapse 等同 hidden |
| clip-path | 🟡 | polygon/circle/ellipse/inset；inset 的 round 半径被忽略 |
| mask | ❌ | |
| filter / backdrop-filter | 🟡 | blur/brightness/grayscale/invert/hue-rotate/opacity/drop-shadow，可动画；无 contrast/saturate/sepia |
| transform | 🟡 | translate/rotate/scale 各轴向，角度单位全；**无 skew、matrix、perspective** |
| transform-origin | ✅ | |
| rotate 独立属性 | ✅ | translate/scale 独立属性 ❌ |
| mix-blend-mode/isolation/contain/will-change | ❌ | |

## 文本

- ✅ font-family（@font-face + 回退链）、font-size、line-height、text-indent、letter-spacing、white-space 六值、text-overflow:ellipsis、line-clamp、direction；
- 🟡 font-weight（bolder/lighter 固定映射 700/300，不按父权重算）；**font-style 只认 oblique，italic 不触发斜体**；text-align 的 **justify 等同 start**；text-decoration 只有 underline/line-through；
- ❌ font 简写、text-shadow、text-transform、word-break、overflow-wrap、word-spacing；
- 非标准扩展：`selection-color`（选区颜色）、`text-stroke`（描边）。

## 颜色、交互、动画

**颜色**：hex 全家、rgb/rgba 新旧语法、hsl/hsla、transparent ✅；命名色只有 26 个（CSS 有 148 个）；**currentColor 解析为黑**（SVG 内部除外）；hwb/lab/lch/oklch ❌。

**交互**：cursor 常用值 + `url()` 自定义 ✅（move/wait/grab 等回退箭头）；user-select、pointer-events、accent-color、appearance、resize（textarea）✅；outline ❌。

**伪元素 content**：只支持单个引号字符串；attr()/counter()/url()/多段拼接 ❌。

**transition**：简写、逗号列表、all、延迟、中断反向重定向 ✅；但**可动画属性是白名单**——opacity、宽高、transform、filter、color、background-color、位置四边、margin/padding、border-width、border-radius、box-shadow；名单外直接跳变。

**animation**：简写全家、多动画、四种 direction/fill-mode、steps/cubic-bezier ✅；插值上 transform/filter/box-shadow 有专用插值，其余走通用数值/颜色插值，离散属性不动画。

## 渲染层

- 文字：AWT 光栅化 + 字体图集，自定义字体和 MC 字体双路径；
- 图片：UV 窗口、多层平铺、九宫格、合批；
- 遮罩：stencil 模板遮罩（含圆角）、scissor、滚动裁剪；
- 滤镜：离屏 FBO、可分离 blur；
- Canvas 2D：路径全 API、渐变/pattern、ImageData、Path2D（SVG path 全命令）、OffscreenCanvas；无 conicGradient、WebGL；
- SVG：基础图形 + path（含弧线、evenodd）+ fill/stroke 系 + currentColor，4x 超采样输出位图；无 g/defs/use/text、渐变、filter、transform 属性、嵌套 svg。

## 关于像素级一致性

框架内部用 WPT 几何快照对比验证布局（机制见 [wpt.md](wpt)）。现状是和 Chromium 像素级一致的页面极少，差异几乎全部来自上面列出的缺口（无 UA 样式、负 margin、vertical-align 等）。这不影响它的目标场景——手写 UI——但意味着**别指望任意第三方网页搬过来能看**。
