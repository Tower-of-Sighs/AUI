# ApricityUI HTML / CSS 浏览器标准覆盖面

最后更新：2026-08-02

本文盘点 ApricityUI 对 HTML 与 CSS 浏览器标准的覆盖程度，回答“哪些网页写法可以直接搬过来用，哪些会被静默降级或忽略”。

覆盖面分三档标注：

| 标记 | 含义 |
| --- | --- |
| ✅ | 行为与浏览器基本一致（在框架运行环境约束内） |
| 🟡 | 部分支持：能解析、有行为，但与规范有明确差距，注意“限制”列 |
| ❌ | 不支持：无代码路径，设置后被忽略（CSS 属性会 warn 一次） |

阅读本文前建议先了解运行环境差异：[浏览器辅助功能](browser-features.md)（事件、剪贴板、生命周期），布局几何精度验证见 [WPT 文档](wpt.md)。

## 1. 总体定位

ApricityUI 是 Minecraft 客户端内的自研 HTML/CSS 引擎（正则解析器 + 自研布局/渲染），不是内嵌浏览器。它的覆盖策略是：

- **选择器层最完整**：Selectors 3 近乎全覆盖，另有 `:is()`/`:where()`/`:not()`、`::before`/`::after`、正确的 specificity 与 `!important` 层叠。
- **布局层是“常用子集 + 关键缺失”**：Normal Flow（含 margin 折叠与基线）、Flexbox、Grid 各覆盖主流用法，但缺 float、表格布局、`position: sticky`、flex `order`/reverse 等。
- **绘制层覆盖较广**：多层背景、九宫格 border-image、box-shadow、filter/backdrop-filter、clip-path、transform、transition/animation 均可用。
- **HTML 层面向 UI 而非文档**：21 个标签有专用元素类，其余标签按通用 block/inline 处理，没有 UA 默认样式（`h1` 和 `div` 视觉无差别）。

WPT 几何对比（Chromium 快照 vs AUI 快照，0.25px 阈值）当前数据见第 10 节：**19,775 个 CSS 布局候选页中仅 5 个几何完全一致**，绝大多数差异来自本文列出的布局缺口与 UA 样式缺失。这个 pass 率反映的是“像素级几何一致”，不是“功能不可用”——框架的目标场景是手写 UI，不是渲染任意第三方网页。

## 2. HTML 解析器

解析器在 `resource/HTML.java`，是基于正则的分词器，不是标准 tree builder。

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| 开始/结束标签、属性（双引号/单引号/无引号/布尔属性） | ✅ | HTML.java:201-202、310-345 |
| 注释 `<!-- -->` → CommentNode | ✅ | HTML.java:229-232、500-503 |
| `<!doctype>` / `<?xml?>` 剥离 | ✅ | HTML.java:23-24、567-576 |
| 显式自闭合 `/>` 与 void 标签集（br/hr/img/input/link/meta 等） | ✅ | HTML.java:192-195、251-257 |
| 命名实体 | 🟡 | 仅 `amp apos gt lt nbsp quot` 6 个 + 十/十六进制数字实体（HTML.java:184-191、347-407） |
| script/style raw text（不解码实体） | ✅ | HTML.java:542-544 |
| 错误恢复（孤立闭合、嵌套失配、EOF 隐式闭合） | 🟡 | 有弹栈恢复（HTML.java:449-526），但**没有浏览器的隐含标签生成**：不会自动闭合 `<p>`、不补 `<tbody>` |
| `<script>` | 🟡 | src 与内联**都执行**（非标准，浏览器二选一）；无 defer/async/module 语义（JS.java:77-95） |
| `<style>` / `<link rel=stylesheet>` | ✅ | CSS.java:78-211；`<style src>` 不支持仅告警（CSS.java:114-121） |
| `<meta>` | 🟡 | 不进语义树，仅正则读取 `aui-viewport`/`aui-font-mode`/`aui-mouse-events` 三个专用 meta；charset 保留但不参与编码协商（固定 UTF-8） |
| 文档骨架 | ✅ | 总是合成 html/head/body；`<head>` 块从标记中剔除，head 子节点（如 title）不进 DOM（HTML.java:546-565、601-638） |
| 文本节点 | ✅ | TextNode 挂 childNodes，布局阶段作为 inline run 排版；修改产生 characterData mutation |

## 3. HTML 元素覆盖

### 3.1 有专用元素类的标签（21 个）

注册机制：解析时先建通用 `Element`，`Element.init()` 按注册表替换为专用类（init/Element.java:953-1004）。

| 标签 | 支持度 | 说明 |
| --- | --- | --- |
| html / head / body | ✅ | Head 强制 display:none（Head.java:13） |
| div / span | ✅ | 纯容器 |
| a | 🟡 | 仅 mouseup 时用系统浏览器打开 href；无 target、无页内导航（A.java:17-25） |
| img | 🟡 | src 异步加载、load/error 事件、naturalWidth/complete；**无 alt 文本渲染、无 srcset/sizes/picture**（Img.java:16-108） |
| input | ✅🟡 | 按 type 分档，见 3.2 |
| textarea | ✅ | 多行编辑、右下角拖拽 resize、resize CSS 属性（TextArea.java:22-78、338-350） |
| select / option / optgroup | ✅ | 弹出层、键盘全套（方向/Home/End/PgUp/PgDn/Enter/Space/Esc/前缀搜索）、multiple、optgroup 分组（Select.java:116-162） |
| pre | ✅ | 保留换行 |
| canvas | ✅ | 仅 `getContext("2d")`，默认 300x150，无 WebGL（Canvas.java:78-80） |
| svg / path | 🟡 | 基础图形子集，见第 8 节 |
| meta | 🟡 | 仅三个 aui-* 专用 meta 生效 |

### 3.2 input type 覆盖（element/Input.java:109-123）

| type | 支持度 | 说明 |
| --- | --- | --- |
| text（含未识别 type 兜底） | ✅ | 编辑、选区、placeholder、maxlength、undo |
| password | 🟡 | 仅渲染替换为 `*`，无自动填充语义 |
| number | ✅ | spinner 按钮、滚轮/方向键步进、min/max 钳制 |
| range | ✅ | 拖拽滑轨、min/max/step、键盘调整 |
| checkbox / radio | ✅ | 自绘控件、Space 切换、radio 组互斥 |
| file | 🟡 | 系统文件对话框、accept、multiple；只拿到路径字符串，无 File 内容模型 |
| color | ✅ | 框架 ColorPicker |
| button / submit / reset / image | 🟡 | 统一按钮模式，submit 提交 form、reset 重置；image 不显示图片 |
| hidden | ✅ | 不渲染、不可聚焦 |
| date / datetime-local / email / month / search / tel / time / url / week | ❌ | JS 属性回显标准化（Element.java:603-610），但控件全部降级为 text，无各自 UI 与校验 |

### 3.3 表单体系（Element 上 tagName 驱动，无专用类）

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| form submit/requestSubmit/reset | ✅ | submit/reset/formdata 事件、novalidate、约束校验、FormData 成功控件收集（Element.java:1645-1845） |
| form action 提交与导航 | ❌ | 提交只触发事件，不发请求、不导航 |
| label for / 隐式关联 | ✅ | 点击激活被标注控件（Element.java:2355-2398） |
| fieldset disabled 级联（legend 豁免） | ✅ | Element.java:627-643、710-719 |
| `form=id` 外部关联 | ✅ | Element.java:650-667 |

### 3.4 无专用类的常见标签（重点）

| 标签 | 行为 |
| --- | --- |
| p / h1-h6 / ul / ol / li / dl / blockquote / figure / section… | 通用 Element + block，**无 UA 样式**：h1 无大字号，ul/li 无项目符号，p 无上下边距（Style.java:1156-1163） |
| table / tr / td / th | 按普通 block 嵌套排版，**无表格布局**；CSS `display:table` 也降级为 block（Style.java:1206-1215） |
| br / hr | 仅识别为 void 标签；br 成为 0 内容 block 盒（碰巧阻断行内流但无行高），hr 无默认边框——**不要依赖它们** |
| iframe / video / audio / object / embed | 无任何实现 |
| title | 不进 DOM；无 `document.title` |

### 3.5 UA 默认样式表（Style.java:429-442、1151-1164）

- **inline**：a、b、i、em、strong、code、small、sub、sup、u、label、mark、span、img、input、select、textarea、canvas、svg 等约 30 个标签。
- **display:none**：head、script、style、title、meta、link、option、optgroup、`input[type=hidden]`。
- **其余一切**：block。
- 补充：button `text-align:center`；select 强制 border-box/nowrap/overflow hidden。

### 3.6 非标准扩展标签

框架特有的标签，不属于 HTML 标准：

| 标签 | 用途 |
| --- | --- |
| texture | Minecraft ResourceLocation 纹理 + 模糊（element/Texture.java） |
| sprite | 精灵图表帧动画（element/Sprite.java） |
| container / slot / recipe / translation | Minecraft 容器 UI、物品槽、配方、可翻译文本（instance/element/，@ElementRegister） |

未知标签保持通用 Element，按 UA 默认样式渲染，无警告。

## 4. CSS 选择器（init/Selector.java）

### 4.1 支持 ✅

| 类别 | 内容 |
| --- | --- |
| 基础 | 元素、`.class`、`#id`、`*`、逗号分组（正确跳过 `[]`/`()` 内逗号） |
| 组合器 | 后代、`>`、`+`、`~` |
| 属性 | `[a]` `[a=v]` `~=` `\|=` `^=` `$=` `*=`，`i`/`s` 大小写标志 |
| 结构伪类 | `:root` `:first-child` `:last-child` `:only-child` `:nth-child`（odd/even/An+B）`:nth-last-child` `:first/last/only-of-type` `:nth-of-type` `:nth-last-of-type` `:empty` |
| 状态伪类 | `:hover` `:active` `:focus` `:focus-within` `:disabled` `:enabled` `:checked` |
| 表单伪类 | `:required` `:optional` `:valid` `:invalid` `:in-range` `:out-of-range` `:read-only` `:read-write` `:placeholder-shown` |
| 函数伪类 | `:not()` `:is()` `:where()`（选择器列表参数） |
| 伪元素 | `::before` `::after`（生成真实盒，受 `content` 门控） |
| 层叠 | specificity (id, class, tag) + source order；`:is/:not` 取参数最大值、`:where` 为 0；`!important` 声明级；CSS 标识符 `\` 转义 |

### 4.2 部分支持 🟡

- `:focus-visible` 退化为 `:focus`（无输入模态追踪）。
- `:nth-child()` 不支持 `of S` 语法。
- **内联 style 与 !important 的层叠偏差**：内联普通声明会覆盖样式表的 `!important` 声明（规范相反），内联 `!important` 语法本身不生效（Element.java:403、Style.java:316-334）。
- 不支持的伪类：解析 warn 一次，匹配时返回 false。

### 4.3 不支持 ❌

`:link` `:visited` `:target` `:lang()` `:dir()` `:has()`、`::first-line` `::first-letter` `::marker` `::selection`。

## 5. @规则与样式来源

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| `<style>` / `<link rel=stylesheet>` / 内联 style | ✅ | 三种来源均参与层叠 |
| `@keyframes`（含 `@-webkit-`） | ✅ | from/to/百分比、逗号帧选择器 |
| `@import` | ✅ | 递归内联、深度上限、循环检测、支持远程 URL |
| `@media` | 🟡 | 仅 min/max-width、min/max-height、width、height、orientation + `and` + screen/all；不支持 `not`、逗号 or、范围语法、`prefers-*`、`hover`、`aspect-ratio` |
| `@font-face` | 🟡 | 仅取 font-family + 第一个 src url()；忽略 format()、多 src、weight/style/unicode-range 描述符 |
| 简写展开参与层叠 | ✅ | margin/padding/inset/border-width/border-color/border/gap 解析期展开；flex 简写 |
| css-wide 关键字 | ✅ | inherit / initial / unset / revert / revert-layer |
| CSS 变量 var() | ✅ | `--*` 定义、`var(--x, fallback)`、嵌套深度 8、沿 DOM 链继承 |
| `@supports` `@layer` `@page` `@charset` `@container` `@scope` | ❌ | 未知 @规则 warn 后跳过 |

## 6. 布局模型

### 6.1 display（Style.normalizeDisplay，Style.java:1206-1215）

| 值 | 支持度 |
| --- | --- |
| block / inline / inline-block / flex / inline-flex / grid / inline-grid / none | ✅ |
| table / list-item / flow-root | 🟡 降级为 block |
| inline-table | 🟡 降级为 inline-block |
| contents 及其他未知值 | 🟡 一律 block |

### 6.2 盒模型与尺寸

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| margin / padding（1-4 值简写、分边、auto） | ✅ | 水平 auto margin 居中；flex 轴 auto margin 吸收自由空间 |
| **负 margin** | 🟡 | `Math.max(0, …)` 钳位，**负值无效**（Box.java:270-274） |
| **相邻 margin 折叠（含负 margin 场景语义）** | ✅ | NormalFlow.java:321-324、389-397 |
| border 三 token 简写、分边 width/color | ✅ | 但样式渲染见下行 |
| border-style | ❌ | 简写中识别存储但**渲染全部画成实线**；独立 border-style 属性不存在 |
| border-width 关键字 thin/medium/thick | ❌ | 解析要求数值，否则整边框回退默认 |
| border-radius（1-4 值、%、斜杠椭圆双半径、calc、溢出缩放） | ✅ | 百分比水平分量相对盒宽、垂直分量相对盒高解析（Box.java getCalculatedRadii） |
| border-image 全家（source/slice/width/outset/repeat/fill，九宫格渲染） | ✅ | Box.java:600-646 |
| box-shadow（多阴影、inset、blur、spread，可过渡/动画） | ✅ | Box.java:507-551 |
| box-sizing | ✅ | content-box / border-box |
| width/height/min-/max-、aspect-ratio | ✅ | 含 auto 最小尺寸保护 |
| 单位 px % em rem vw vh | ✅ | Size.java:914-927 |
| calc() | 🟡 | **仅加减**，无乘除、无嵌套（Size.java:862-908） |
| min() / max() / clamp() | ✅ | Size.java:933-967 |
| fit-content / min-content / max-content、vmin/vmax/ch/ex、cm/mm/in/pt | ❌ | |

### 6.3 定位（layout/Position.java）

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| static / relative / fixed | ✅ | fixed 相对文档视口 |
| absolute | ✅ | 包含块为最近 positioned 祖先的 padding box，无 positioned 祖先时用初始包含块（文档视口，随内容滚动，区别于 fixed）；百分比尺寸/偏移同样相对该包含块解析（Position.java:136-194、Size.java）。已知偏差：双侧 auto 时锚定包含块原点而非静态位置；transform/filter 祖先不形成包含块（本引擎 transform 为纯视觉效果） |
| top/right/bottom/left + inset 简写、% 相对包含块、双侧拉伸 | ✅ | |
| sticky | ❌ | |
| z-index（整数/auto） | ✅ | Drawer.java:211-226 |
| float / clear | ❌ | 全代码库无实现 |

### 6.4 Flexbox（layout/Flex.java）

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| flex-direction: row / column | ✅ | |
| row-reverse / column-reverse | ❌ | `isReverse()` 定义了但布局从未调用，静默忽略（Flex.java:955-958） |
| flex-wrap: nowrap / wrap | ✅ | 单行与换行两套算法 |
| wrap-reverse | ❌ | 当作 nowrap |
| justify-content 六值 | ✅ | |
| align-items / align-self | 🟡 | flex-start/center/flex-end/stretch 完整；**baseline 解析但退化为 flex-start** |
| align-content | ❌ | 字段构造后布局从不读取，**解析但不生效**（Flex.java:18、25、966-990） |
| flex-grow / flex-shrink（加权收缩 + min 约束迭代冻结）/ flex-basis / flex 简写 | ✅ | |
| gap / row-gap / column-gap | ✅ | |
| order | ❌ | Style 无此字段 |
| 主轴/交叉轴 auto margin | ✅ | 优先于 justify/align |
| 匿名 flex item（容器直接文本） | ✅ | |

### 6.5 Grid（layout/Grid.java，自述 MVP）

| 能力 | 支持度 |
| --- | --- |
| grid-template-columns/rows：px、auto、fr、minmax()、repeat(n/auto-fill/auto-fit) | ✅ |
| gap、justify-items/align-items、justify-self/align-self（start/center/end/stretch） | ✅ |
| grid-row/grid-column：`N`、`span N`、`N / M`、`N / span N`、自动放置 | ✅ |
| 命名线 `[name]`、grid-template-areas、grid-auto-flow（column/dense）、grid-auto-columns/rows、place-* 简写、负线号、subgrid | ❌ |
| 轨道尺寸与 gap 取 int px | 🟡 小数 px 与百分比 gap 精度受限 |

### 6.6 Normal Flow 与行内（layout/NormalFlow.java）

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| 块级流、相邻 margin 折叠 | ✅ | |
| inline / inline-block 原子盒换行、inline 元素按文本片段化 | ✅ | |
| 行内基线对齐 + strut | ✅ | NormalFlow.java:235-286 |
| vertical-align | 🟡 | 9 个关键字能解析存储，但**只有 baseline 有真实布局效果**，其余静默无效（NormalFlow.java:214、235-240） |
| float / clear / multi-column | ❌ | |

### 6.7 滚动

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| overflow / overflow-x / overflow-y: visible / hidden / scroll / auto / clip | ✅ | clip 裁剪但不可滚动（Interaction.java:78-122） |
| 引擎自绘滚动条（track/thumb/gutter/拖拽/宽度自适应） | ✅ | ScrollModel.java:128-535 |
| 平滑滚动 | 🟡 | 内建插值，但 `scroll-behavior` CSS 属性不解析 |
| scrollbar-width / scrollbar-color / ::-webkit-scrollbar / scroll-snap / overscroll-behavior | ❌ | |

## 7. 绘制与视觉属性

| 属性 | 支持度 | 说明 |
| --- | --- | --- |
| color、background-color | ✅ | |
| background-image | 🟡 | url()、linear-gradient()、repeating-linear-gradient()、**多背景分层**；渐变角度仅 deg/to 关键字，色标仅 %/px（Gradient.java:134-241）；无 radial/conic |
| background-repeat | 🟡 | repeat/repeat-x/repeat-y/no-repeat/round 可用；**space 被当普通 repeat** |
| background-size / background-position / background 简写（含 `/size`） | ✅ | |
| background-attachment / origin / clip / blend-mode | ❌ | |
| object-fit（fill/contain/cover/none/scale-down）/ object-position | ✅ | ImageDrawer.java:133-225 |
| opacity | ✅ | |
| visibility | 🟡 | visible/hidden；collapse 等同 hidden |
| clip-path | 🟡 | polygon/circle/ellipse/inset（stencil 实现）；inset 的 `round` 半径被忽略（ClipPath.java:70-78） |
| mask / mask-image | ❌ | |
| filter | 🟡 | blur/brightness/grayscale/invert/hue-rotate/opacity/drop-shadow，多函数组合、可过渡/动画、离屏 FBO；**无 contrast/saturate/sepia/url()**（Filter.java:39-88） |
| backdrop-filter | ✅ | 同 filter 函数集 |
| transform | 🟡 | translate/translate3d/translateX/Y/Z、rotate/rotateX/Y/Z、scale/scaleX/Y，角度 deg/rad/grad/turn；**无 skew、matrix、scale3d/Z、perspective、rotate3d**（Transform.java:44-101） |
| transform-origin | ✅ | %、px、关键字 |
| rotate 独立属性 | ✅ | 合并进 transform；translate/scale 独立属性 ❌ |
| mix-blend-mode / isolation / contain / will-change / image-rendering | ❌ | |

## 8. 文本属性

| 属性 | 支持度 | 说明 |
| --- | --- | --- |
| font-family（@font-face、回退链） | ✅ | |
| font-size（全部支持的长度单位，继承） | ✅ | |
| font-weight | 🟡 | normal/bold/数值 1-1000；**bolder/lighter 固定映射 700/300，不按父权重相对计算** |
| font-style | 🟡 | **只认 oblique；italic 不触发斜体** |
| font 简写、font-variant、font-stretch、font-kerning | ❌ | |
| line-height | ✅ | normal（≈1.2 封顶 1.45）/倍数/%/长度 |
| text-align | 🟡 | start/end/left/right/center 可用；**justify 等同 start** |
| text-decoration | 🟡 | 仅 underline / line-through；无 overline、style/color/thickness |
| text-indent / letter-spacing | ✅ | |
| white-space 六值（含空白折叠语义） | ✅ | normal/nowrap/pre/pre-wrap/pre-line/break-spaces |
| text-overflow: clip / ellipsis | ✅ | |
| line-clamp（非标准行数截断） | ✅ | |
| direction: ltr / rtl | ✅ | |
| text-shadow、text-transform、word-break、overflow-wrap、word-spacing | ❌ | |
| 换行 | ✅ | 软换行 + 连字符断行（Text.java:764-853） |

非标准文本扩展：`selection-color`（选区颜色）、`text-stroke`（宽度+颜色描边）。

## 9. 颜色、交互与动画

### 9.1 颜色（style/Color.java）

| 格式 | 支持度 |
| --- | --- |
| hex 3/4/6/8、rgb/rgba（逗号与空格 + `/` alpha 新语法）、hsl/hsla（deg/rad/turn）、transparent | ✅ |
| 命名色 | 🟡 仅 26 个（CSS 全量 148 个） |
| currentColor | ❌ 解析为黑（SVG 内部属性除外，Svg 支持 currentColor 继承） |
| hwb/lab/lch/oklch/color()/系统色 | ❌ |

### 9.2 交互

| 属性 | 支持度 | 说明 |
| --- | --- | --- |
| cursor | 🟡 | auto/default/pointer/text/crosshair/四种 resize + `url() x y` 自定义伪光标；其余关键字（move/wait/grab/not-allowed…）回退箭头（Cursor.java:223-272） |
| user-select: auto/none/text/all | ✅ | 文本选择细则见 [browser-features.md](browser-features.md#5-文字选择与复制) |
| pointer-events: auto / none | ✅ | |
| accent-color（checkbox/radio 等着色） | ✅ | |
| appearance（none 隐藏 select 原生外观，含 -webkit- 别名） | ✅ | |
| resize（textarea：both/horizontal/vertical/block/inline） | ✅ | |
| outline 全家 | ❌ | |

### 9.3 content 与伪元素

| 能力 | 支持度 |
| --- | --- |
| ::before / ::after + content 单个引号字符串（含 CSS 转义） | ✅ |
| content: attr() / counter() / url() / 多段拼接 / open-quote | ❌ |

### 9.4 transition 与 animation

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| transition 简写/逗号列表/`all`/延迟/中断反向重定向 | ✅ | Transition.java:430-598 |
| transition 可动画属性白名单 | 🟡 | opacity、width、height、transform、filter、color、background-color、top/right/bottom/left、margin-*/padding-*、border-*-width、border-radius、box-shadow；**名单外属性（grid-template、gap、font-size、背景位置等）直接跳变**（Transition.java:600-614） |
| animation 全家 | ✅ | 简写 + name/duration/delay/iteration-count（infinite）/direction 4 值/fill-mode 4 值/play-state，多动画逗号列表 |
| 计时函数 | ✅ | linear、ease*、step-start/end、steps(n, jump-*)、cubic-bezier（带定义域校验） |
| animation 属性插值 | 🟡 | transform/filter/box-shadow 专用插值；其余走数值/颜色通用插值；离散属性不可动画 |
| animation-timeline/range/composition、transition-behavior | ❌ | |

## 10. 渲染层与图形元素

| 能力 | 支持度 | 说明 |
| --- | --- | --- |
| 文字渲染 | ✅ | AWT 光栅化 + 2048 字体图集、自定义字体与 MC 字体双路径、合批 |
| 图片 | ✅ | UV 窗口、多层背景平铺、九宫格、RenderType 缓存合批 |
| 遮罩/裁剪 | ✅ | stencil 模板遮罩（含圆角）、scissor 裁剪、滚动容器 clip |
| 滤镜管线 | ✅ | 离屏 FBO，可分离 blur 双 pass |
| Canvas 2D | ✅🟡 | 路径全 API（含 roundRect）、变换 + DOMMatrix、渐变/pattern、ImageData、drawImage、toBlob/toDataURL、Path2D（SVG path 全命令含弧线）、shadow、globalCompositeOperation、OffscreenCanvas、createImageBitmap；**无 conicGradient、WebGL、letterSpacing/direction 等文本 hints** |
| SVG | 🟡 | circle/ellipse/rect/line/polyline/polygon/path（d、evenodd）+ fill/stroke 系属性 + currentColor，4x 超采样光栅化；**无 g/defs/use/text、渐变、clipPath/mask/filter/marker/动画、transform 属性、嵌套 svg**，输出位图非矢量 |

## 11. JS DOM API 速览（附录）

详见 [browser-features.md](browser-features.md#82-全局对象和常用桥接)，此处只列覆盖度要点：

- **DOM 遍历与操作**：querySelector(All)、getElementById、classList、dataset、innerHTML/outerHTML、appendChild/insertBefore/removeChild/before/after/replaceWith、matches/closest、getBoundingClientRect、createElement/createTextNode/createDocumentFragment ✅；**无 Shadow DOM、scrollIntoView、insertAdjacentHTML、element.animate** ❌
- **表单反射**：value/checked/validity/checkValidity/setCustomValidity/selectionStart/setRangeText/stepUp/stepDown/options/labels/form/files（伪 FileList）等极全 ✅
- **事件流**：完整捕获 → at-target → 冒泡、stopPropagation/stopImmediatePropagation/preventDefault、composedPath、isTrusted、once ✅；addEventListener 无 `{passive, signal}` ❌
- **事件类型**：mouse 全家 + dblclick/contextmenu/wheel、pointer 兼容（鼠标派生）、keydown/keyup、focus/blur（不冒泡）、input/beforeinput/change/submit/reset/formdata/invalid/select/copy/cut/paste/composition*、scroll、resize ✅；**无触摸、拖拽、keypress、focusin/focusout** ❌
- **全局**：console、localStorage/sessionStorage、performance、fetch、rAF、setTimeout/Interval、URLSearchParams、FormData、ResizeObserver、MutationObserver、DOMMatrix、Event/CustomEvent/MouseEvent/WheelEvent/PointerEvent 构造器 ✅；**无 IntersectionObserver、matchMedia、history、navigator、XHR、WebSocket、KeyboardEvent 构造器** ❌

## 12. WPT 验证现状

框架用几何快照对比验证布局（Chromium 采集 `getBoundingClientRect`，AUI 客户端解析同一页面采集矩形，0.25px 阈值），**不执行 testharness.js 断言**——`pass` 只代表几何一致。机制见 [wpt.md](wpt.md)。

最近一次完整运行（wpt/progress.md，WPT revision `a6f29b0`，19,775 候选页）：

| 状态 | 数量 |
| --- | ---: |
| pass（全部在 css-grid） | 5 |
| layout-mismatch | 9,139 |
| aui-runtime-unsupported | 9,720 |
| timeout | 625 |
| infra-blocked | 170 |
| pending / browser-test-failed | 116 |

按模块：css-grid 5/3262 pass；css-flexbox 1550 mismatch + 397 未通过；css-align、css-box、css-inline、css-multicol 全 mismatch；css-position、css-sizing、css-tables、css-text、css-transforms、css-ui、css-values、css-writing-modes、CSS2 尚未执行。

pass 率低的主因与本文缺口一一对应：无 UA 默认样式（h1/p/ul 全部无样式）、负 margin 无效、br/hr 无语义、vertical-align 仅 baseline、display 降级映射等。JS/DOM 维度无 WPT 覆盖。

## 13. 迁移网页代码时的速查清单

把为浏览器写的 HTML/CSS 搬进 ApricityUI 前，按优先级检查：

1. **UA 样式为零**：自己给 h1-h6、p、ul/li 写样式；不要用 br/hr 表达布局。
2. **布局避开**：float、position: sticky、flex 的 order/reverse/align-content/baseline、grid 的 areas/auto-flow/命名线、负 margin、表格布局。
3. **值解析避开**：calc 乘除、border 样式（只有实线）、currentColor、26 个以外的命名色、radial/conic 渐变、contrast/saturate/sepia 滤镜、skew/matrix transform。
4. **层叠注意**：不要用内联 style 覆盖样式表 `!important`（行为与浏览器相反）。
5. **文本注意**：italic 无效（用 oblique）、justify 无效、vertical-align 只有 baseline 有效、text-decoration 仅下划线/删除线。
6. **表单注意**：date/email/url 等 type 降级为纯文本框，校验要自己做；form 提交只发事件不发请求。
7. **transition 白名单**：名单外属性（如 gap、grid-template、font-size）会跳变。

## 14. 相关源码

~~~text
src/main/java/com/sighs/apricityui/resource/HTML.java        HTML 解析器
src/main/java/com/sighs/apricityui/resource/CSS.java         CSS 解析器、@规则、层叠
src/main/java/com/sighs/apricityui/init/Selector.java        选择器引擎
src/main/java/com/sighs/apricityui/init/Style.java           属性白名单、UA 默认表、display 归一化
src/main/java/com/sighs/apricityui/init/Element.java         元素注册表、表单/伪元素逻辑
src/main/java/com/sighs/apricityui/element/                  专用元素类
src/main/java/com/sighs/apricityui/layout/                   Box/Size/NormalFlow/Flex/Grid/Position
src/main/java/com/sighs/apricityui/style/                    Animation/Transition/Transform/Filter/Text/Color 等
src/main/java/com/sighs/apricityui/render/                   渲染管线、Mask/ClipPath/FilterRenderer
src/main/resources/assets/apricityui/apricity/global.js      JS 全局桥接
wpt/progress.md                                              WPT 几何对比进度
~~~
