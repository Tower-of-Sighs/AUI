# resource.html 浏览器一致性 TODO

目标：让 `src/main/resources/assets/apricityui/apricity/devtools/resource.html` 在 AUI 中尽量接近浏览器截图 `img_2.png` 的视觉结果。对比 AUI 截图为 `img_3.png`。

说明：截图坐标是物理像素，不等于 CSS px。AUI 截图包含约 `53px` 的 Minecraft 窗口标题栏；做图像对比时必须先裁掉标题栏、底部窗口边缘和浏览器外框圆角。

## P0 结构级差异

- [ ] 修复 Header 右侧按钮没有贴到右边的问题。
  - 现象：浏览器按钮从约 `x=2070` 开始，AUI 按钮从约 `x=989` 开始，按钮后方留下超大空白。
  - 疑点：`.header-actions { margin-left: auto; display: flex; }` 的 flex auto margin / 剩余空间分配。
  - 验收：`BACK / UP / NEW` 位于 Header 最右侧，右边距接近浏览器；按钮自身尺寸和间距保持稳定。

- [ ] 单独验证 flex `margin-left: auto`。
  - 现象：`.content-header { justify-content: space-between; }` 基本成立，但 Header auto margin 失败。
  - 疑点：auto margin 逻辑缺失或只在部分 layout path 中生效。
  - 验收：构造最小 flex 用例，前项固定宽度、后项 `margin-left:auto`，后项应被推到容器右侧。

- [ ] 修复 Content 标题下方紫线变黑线。
  - 现象：浏览器 `x=560,y=250` 是 `rgb(139,92,246)`；AUI 对应线是 `rgb(26,26,26)`。
  - 疑点：`.content-header::after` 的 `position:absolute; bottom:-2px`、负偏移、border 与伪元素绘制顺序。
  - 验收：`SURVIVAL` 下方横线最终显示为紫色，而不是 `.content-header` 的黑色 `border-bottom`。

- [ ] 区分“伪元素不生成”和“伪元素被盖住/裁掉”。
  - 现象：`.sidebar-title::after` 紫色短线能显示，说明不是所有 `::after` 都失效。
  - 疑点：absolute containing block、负 bottom、border paint order。
  - 验收：最小用例覆盖普通 `::after`、absolute `::after`、负 bottom `::after`、border 覆盖顺序。

## P0 Viewport 与尺度

- [ ] 明确 `mode=window` 下 CSS px 到物理像素的映射。
  - 现象：CSS `.sidebar width:280px`，浏览器截图约 `495px`，AUI 约 `488px`。
  - 疑点：浏览器缩放、OS DPI、MC framebuffer、GUI scale、AUI renderScale 不一致。
  - 验收：同一 CSS 尺寸在 browser-mode/window-mode 下有可解释、可配置、稳定的换算。

- [ ] 决定是否新增 `browser/css` viewport 模式。
  - 现象：`mode=window` 只能接近窗口尺寸，不能保证浏览器 CSS viewport 一致。
  - 疑点：`100vh` 绑定的是 MC 内容区、物理窗口还是 framebuffer。
  - 验收：给出明确模式语义，例如 browser 模式按浏览器 CSS px 语义布局，window 模式按 MC 窗口语义布局。

- [ ] 统一三栏横向宽度分配。
  - 现象：Sidebar 浏览器约 `495px`，AUI 约 `488px`；详情栏左边界浏览器约 `x=2029..2031`，AUI 约 `x=2035..2038`。
  - 疑点：viewport 宽度、flex 剩余空间、固定宽度换算、border-box 取整。
  - 验收：Sidebar、Content、Detail 的边界在裁剪后与浏览器误差收敛到小范围。

- [ ] 校准垂直尺度和 `100vh`。
  - 现象：AUI 截图高度比浏览器多 `200px`，底部空白更多；但其中一部分来自窗口标题栏和截图高度不同。
  - 疑点：`body height:100vh`、`.main height:calc(100vh - 60px)` 的坐标系。
  - 验收：在相同内容 viewport 高度下，Header、Main、底部空白比例一致。

## P1 字体与文本度量

- [ ] 明确 `font-mode=web` 的定义范围。
  - 现象：AUI 文字整体更紧，logo、面包屑、Sidebar、文件名、`DIRECTORIES` 字距都略小。
  - 疑点：字号接近但字体 fallback、字重、hinting、letter-spacing、line-height normal 不一致。
  - 验收：文档说明 `font-mode=web` 是否只管字号，还是要模拟浏览器字体度量。

- [ ] 处理 `Chakra Petch` 字体来源。
  - 现象：页面写了 `font-family:'Chakra Petch', sans-serif`，但没有 `@font-face` 或 `@import`。
  - 疑点：浏览器和 AUI 使用不同 fallback 字体。
  - 验收：要么嵌入同一字体，要么明确 fallback 差异不可避免。

- [ ] 校准 `letter-spacing`。
  - 现象：`MINE//EXPLORER`、`DIRECTORIES`、详情提示文字在 AUI 中展开宽度更小。
  - 疑点：letter-spacing 实现、字体度量、缩放取整。
  - 验收：同样文本、同样 CSS 字距下，文本宽度接近浏览器。

- [ ] 校准 line-height / ascent / descent。
  - 现象：文件卡内容在 AUI 中更靠上，卡片高度略矮。
  - 疑点：`.file-name line-height:1.4`、字体 ascent/descent、文本 box 计算。
  - 验收：文件图标、文件名、meta 文本的垂直间距接近浏览器。

- [ ] 确认特殊符号渲染一致。
  - 现象：源码中的 `◀`、`▲`、`▸`、`▾` 是正常 UTF-8，但 AUI 字形边缘更硬、中心略偏。
  - 疑点：符号 fallback 字体、旋转 glyph 的基线/居中。
  - 验收：按钮箭头、树展开三角、面包屑三角大小和居中接近浏览器。

## P1 SVG 与图形绘制

- [ ] 校准 SVG stroke 抗锯齿。
  - 现象：文件、图片、锁、按钮边框在 AUI 中边缘更硬、黑线偏粗。
  - 疑点：SVG rasterize、stroke 对齐、亚像素抗锯齿。
  - 验收：图标黑色 stroke 粗细和边缘柔和度接近浏览器。

- [ ] 校准 SVG opacity。
  - 现象：`ICON.PNG` 图标内部半透明紫色和山形层次在 AUI 中更像块状。
  - 疑点：`opacity="0.2"`、path 填充顺序、alpha 混合。
  - 验收：图片图标内部半透明层次与浏览器接近。

- [ ] 校准 SVG viewBox 到 CSS px 的缩放。
  - 现象：图标尺寸大体接近，但边缘和内部细节不同。
  - 疑点：`40x40` SVG 放入 `48x48` `.file-icon` 时的缩放采样。
  - 验收：文件夹、文件、图片、锁图标大小、居中、内部比例一致。

## P1 背景、渐变与颜色

- [ ] 修复 Header 背景网格过淡/缺失。
  - 现象：浏览器可见 `repeating-linear-gradient` 竖向浅紫网格，AUI 中几乎不可见。
  - 疑点：1 CSS px gradient stop、`rgba(...,0.03)` alpha 精度、采样取整。
  - 验收：Header 空白区域可见与浏览器相近的淡紫网格。

- [ ] 验证 Header 扫描渐变动画。
  - 现象：浏览器能看到淡紫扫描覆盖，AUI 不明显；但截图时间点会影响判断。
  - 疑点：`linear-gradient` + `animation` 组合、动画时间同步。
  - 验收：禁用动画或固定时间点后比较；启用时扫描线能正常移动。

- [ ] 校准 `#fff` 与 `#fafafa` 层次。
  - 现象：AUI 大面积背景更接近纯白，浏览器中白色面板和浅灰页面背景层次更明显。
  - 疑点：body/main/content/detail 背景覆盖、清屏背景、颜色混合。
  - 验收：Header/Sidebar/Detail 白色与 Content 背景 `#fafafa` 的差别可见且接近浏览器。

- [ ] 校准浅灰边框和文字颜色。
  - 现象：边框颜色接近但 AUI 更硬；灰色小字更锐、更暗或混合不同。
  - 疑点：颜色空间、抗锯齿、文本混合。
  - 验收：`5 ITEMS`、详情提示、卡片边框、栏分隔线观感接近浏览器。

## P1 卡片与网格

- [ ] 校准文件卡高度。
  - 现象：浏览器第一张卡约 `y=295..540`，AUI 扣标题栏后约 `y=280..517`，视觉更矮、更靠上。
  - 疑点：文本 line-height、SVG 外盒、padding、border 取整。
  - 验收：卡片顶部、底部、内容垂直居中与浏览器一致。

- [ ] 校准文件卡横向起点和终点。
  - 现象：第一张卡 AUI 左移约 `5..7px`，整排终点 AUI 略向右。
  - 疑点：Content 可用宽度、Sidebar 宽度、grid 轨道分配。
  - 验收：五张卡横向位置和间距接近浏览器。

- [ ] 验证 CSS Grid `auto-fill minmax(140px,1fr)`。
  - 现象：两边都能排成一行，但列宽和整排终点略不同。
  - 疑点：grid container available width、fr 分配、gap 缩放。
  - 验收：最小 grid 用例中列数、列宽、gap 与浏览器一致。

## P1 Sidebar 与树

- [ ] 校准 Sidebar 固定宽度。
  - 现象：AUI Sidebar 比浏览器窄约 `7..10px`。
  - 疑点：CSS px 映射、border-box、固定宽度换算。
  - 验收：Sidebar 右边界与浏览器对齐。

- [ ] 校准树节点缩进。
  - 现象：子项文字和图标整体略紧、略左。
  - 疑点：内联 `padding-left:${24 + depth * 16}px`、flex gap、字体宽度。
  - 验收：各层级三角、图标、文字起点与浏览器一致。

- [ ] 校准 selected 行宽度和背景。
  - 现象：选中行浅紫背景随 Sidebar 更窄提前结束；颜色可能略淡。
  - 疑点：Sidebar 宽度、背景 alpha、边框绘制。
  - 验收：选中行边框、背景色、右边界与浏览器一致。

## P2 交互态待补图

- [ ] 补充 hover 状态截图。
  - 现象：当前图没有 hover，无法比较按钮 hover、卡片 hover、树 hover。
  - 验收：至少包含 Header 按钮 hover、文件卡 hover、树节点 hover。

- [ ] 补充 selected file / detail active 截图。
  - 现象：当前没有选中文件，无法比较详情面板 active 状态、文件卡 selected 状态。
  - 验收：点击一个文件后，比较详情图标、详情文本、tags、面板左侧 active 紫线。

- [ ] 补充滚动条截图。
  - 现象：当前内容不足以触发滚动条，无法验证 `::-webkit-scrollbar`。
  - 验收：构造超长 Sidebar/Content/Detail，比较滚动条宽度、颜色、位置。

- [ ] 补充动画固定帧截图。
  - 现象：`logoPulse`、`blink`、`scanline` 会让截图时机影响视觉。
  - 验收：提供禁用动画版本，或固定动画时间点后再对比。

## P2 自动验证与对比流程

- [ ] 建立裁剪规则。
  - 浏览器：裁掉截图外框、圆角和非页面边缘。
  - AUI：裁掉 `0..52px` 标题栏和底部窗口边缘。
  - 验收：图像 diff 只比较页面内容 viewport。

- [ ] 记录对比基准。
  - 需要固定：浏览器 viewport 尺寸、浏览器缩放、OS DPI、MC 窗口尺寸、GUI scale、AUI viewport meta。
  - 验收：同样环境能稳定复现同一组坐标。

- [ ] 增加最小 CSS 功能用例。
  - 用例：flex auto margin、absolute `::after` 负 bottom、border/伪元素覆盖、grid auto-fill、letter-spacing、line-height、SVG opacity、repeating-linear-gradient。
  - 验收：每个功能能单独判断是布局问题、绘制问题还是字体问题。

## 修复顺序建议

1. 修 Header flex auto margin。
2. 修 Content `::after` 紫线覆盖。
3. 明确并统一 CSS px / viewport 映射。
4. 校准字体度量、letter-spacing、line-height。
5. 校准 SVG stroke、opacity、抗锯齿。
6. 校准 gradient、alpha、背景层次。
7. 做交互态和自动截图回归。

