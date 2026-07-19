# resource.html 浏览器与 AUI 渲染差异清单

对比对象：

- 浏览器截图：`D:\work\AUI\img_2.png`
- AUI 截图：`D:\work\AUI\img_3.png`
- 页面：`file:/D:/work/AUI/src/main/resources/assets/apricityui/apricity/devtools/resource.html`
- 页面当前 meta：
  - `<meta name="aui-font-mode" content="web">`
  - `<meta name="aui-viewport" content="mode=window">`

## 0. 截图基础差异

1. 浏览器截图尺寸是 `2560 x 1316`。
2. AUI 截图尺寸是 `2560 x 1516`。
3. AUI 截图顶部包含 Minecraft Forge 窗口标题栏，约 `53px` 高；浏览器截图没有浏览器标题栏或地址栏，直接从页面内容开始。
4. 如果把 AUI 内容区按 `y - 53px` 对齐，Header 底部紫线大体与浏览器一致：浏览器紫线在 `y=100..104`，AUI 紫线在 `y=153..157`，扣除标题栏后也是 `100..104`。
5. 由于 AUI 截图总高度多出约 `200px`，主体下方空白明显更多；这不是页面 CSS 本身的直接差异，而是截图窗口/画布高度不同造成的结果。
6. 浏览器截图外圈有页面/截图容器的圆角和黑色边缘；AUI 是原生游戏窗口矩形边界，顶部还有 Windows 标题栏。

## 1. 整体布局

1. 浏览器中 UI 内容从 `y=0` 开始；AUI 中 UI 内容从窗口标题栏下方约 `y=53` 开始。
2. 两者页面宽度都是 `2560px`，但内部三栏横向分配不完全一致。
3. 浏览器左侧栏右边界约在 `x=494..496`；AUI 左侧栏右边界约在 `x=486..489`。AUI 左侧栏窄了约 `7..10px`。
4. 浏览器右侧详情栏左边界约在 `x=2029..2031`；AUI 右侧详情栏左边界约在 `x=2035..2038`。AUI 详情栏更靠右约 `6..9px`。
5. 因为左栏更窄、右栏更靠右，AUI 中间内容区域整体比浏览器略宽，但内容起点反而更靠左。
6. 浏览器中左栏、中栏、右栏高度延伸到页面底部 `1316px`；AUI 中这些栏只渲染到 Minecraft 内容区域内，下面还有额外空白到 `1516px`。
7. AUI 底部可见一条深色/红黑色游戏窗口底边；浏览器截图底部是页面截图边框。

## 2. Header 区域

1. Header 内容高度相对页面内容基本一致，AUI 扣除标题栏后与浏览器接近。
2. AUI Header 的左侧 logo 整体比浏览器更靠左：浏览器 logo 方块左边约 `x=47`，AUI 约 `x=40`。
3. AUI logo 方块尺寸看起来接近浏览器，但位置更靠左、更靠上方内容区边缘。
4. 浏览器中 `MINE//EXPLORER` 左边约 `x=125`；AUI 中约 `x=118`，整体左移约 `7px`。
5. AUI 的 Header 文本垂直位置与浏览器大体一致，但受字体度量影响，字形重心略有差别。
6. 浏览器 Header 背景上的竖向浅紫网格线更明显；AUI 中网格线更淡、更接近不可见。
7. 浏览器 Header 右侧按钮位于最右侧，`BACK / UP / NEW` 靠近窗口右边，大约从 `x=2070` 开始。
8. AUI Header 右侧按钮没有贴到最右，而是出现在导航路径后方，大约在 `x=990..1430` 区间。
9. AUI 中 Header 右侧从按钮后到窗口右边是一大片空白，浏览器中这片空间由按钮占据到接近右边距。
10. 这是最明显的布局差异之一：`.header-actions { margin-left: auto; }` 在 AUI 中没有把按钮推到 Header 右侧极限，表现像 flex auto margin 或剩余空间计算不一致。
11. 浏览器按钮之间的间距约等于 CSS `gap: 8px` 的视觉结果；AUI 按钮之间也有间距，但因为整体位置错误，视觉关系不同。
12. 浏览器按钮外框线条约 `2px`，边缘锐利；AUI 按钮黑色边框偏粗或抗锯齿更硬。
13. 浏览器 `BACK` 按钮文本是 `◀ BACK`；AUI 中显示为 `◀ BACK`，内容一致。
14. 浏览器 `UP` 按钮文本是 `▲ UP`；AUI 中显示为 `▲ UP`，内容一致。
15. 浏览器 `NEW` 按钮文本是 `+ NEW`；AUI 中显示为 `+ NEW`，内容一致。
16. AUI 的按钮高度、文字高度与浏览器接近，但水平位置不一致导致 Header 重心偏左。

## 3. 面包屑导航

1. 浏览器中 `ROOT > WORLDS > SURVIVAL` 大约位于 logo 右侧，`SURVIVAL` 在 `x=925` 附近。
2. AUI 中同一导航整体更靠左，`SURVIVAL` 在 `x=860` 左右。
3. 浏览器中 `SURVIVAL` 下方紫色 active 下划线较明显，AUI 中下划线也存在，但宽度/位置略有不同。
4. 浏览器中面包屑每段之间留白更宽；AUI 中段间距更紧。
5. AUI 的 `ROOT` 与 logo 文字间距比浏览器更小。
6. AUI 的分隔三角符号相对文字的垂直居中略有差异，三角看起来更靠上或更小。
7. 浏览器中 `ROOT` 和 `WORLDS` 是灰色，AUI 也是灰色，但 AUI 灰度略深或抗锯齿不同。

## 4. 左侧 Sidebar

1. 浏览器 Sidebar 宽度约 `495px`；AUI 约 `488px`，AUI 更窄。
2. 浏览器 Sidebar 右边竖线颜色约为浅灰，AUI 同样有竖线，但线条位置左移。
3. 浏览器 `DIRECTORIES` 标题区域从 Header 下方开始，AUI 同样开始，但扣除标题栏后纵向基本一致。
4. 浏览器 `DIRECTORIES` 左侧三角与文字之间间距略大；AUI 该间距更紧。
5. 浏览器 `DIRECTORIES` 字母间距表现更宽；AUI 字母间距更窄或字体渲染更紧。
6. 浏览器 `DIRECTORIES` 下方紫色短线从 `x=50` 左右开始，AUI 从 `x=50` 左右开始，但由于侧栏宽度差异，右侧灰线的位置不同。
7. 浏览器树节点整体左内边距更接近 CSS 计算值；AUI 树节点水平位置普遍略左。
8. AUI 中选中行 `SURVIVAL` 的浅紫背景高度和浏览器接近，但右端因为 Sidebar 更窄提前结束。
9. 浏览器选中行左侧紫色边框在 `x=7` 左右；AUI 左侧边框在窗口内容最左侧附近，位置略不同。
10. 浏览器树节点图标和文字的间距更稳定；AUI 中若干节点文字更靠近图标。
11. 浏览器小 SVG 图标颜色饱和度更接近 CSS 紫色；AUI 图标紫色略有差异，可能受颜色空间、混合或抗锯齿影响。
12. 浏览器文件图标线条较细且清晰；AUI 文件图标黑色边框偏粗。
13. 浏览器 `LEVEL.DAT` 等子项文字大小与 AUI 接近，但 AUI 字体形状更像另一套字体或度量不同。
14. 浏览器 `SESSION.LOCK` 图标锁体较小且居中；AUI 中锁图标比例接近，但黑色锁梁抗锯齿更硬。
15. 浏览器最底部 `CONFIG` 行到截图底部距离约 `330px`；AUI 到截图底部距离更大，约 `700px`，主要来自窗口高度差异。

## 5. 中间 Content 区域

1. 浏览器中 Content 左内边距从 Sidebar 后约 `55px` 开始，标题 `SURVIVAL` 左边约 `x=553`。
2. AUI 中标题 `SURVIVAL` 左边约 `x=545`，比浏览器左移约 `8px`。
3. 浏览器内容标题基线与 AUI 扣除标题栏后基本接近，但 AUI 标题看起来略小或字形更紧。
4. 浏览器标题 `SURVIVAL` 字重清晰；AUI 字重接近，但边缘更硬。
5. 浏览器右侧 `5 ITEMS` 位于内容标题行右端，约 `x=1875`；AUI 位置约 `x=1875` 附近，基本接近。
6. 浏览器内容分隔线最终呈紫色，因为 `.content-header::after` 覆盖了黑色 border；AUI 这里显示为黑色横线。
7. 这是关键渲染差异：AUI 没有正确表现 `.content-header::after` 的紫色覆盖效果，或者伪元素/绝对定位/层级绘制结果和浏览器不同。
8. 浏览器内容分隔线在 `y=250..253`；AUI 在 `y=288..290`，扣除标题栏后约 `235..237`，比浏览器高约 `13..18px`。
9. AUI 内容标题到分隔线的垂直距离比浏览器短。
10. 浏览器分隔线颜色为紫色 `#8b5cf6`；AUI 分隔线颜色为黑色 `#1a1a1a`。
11. 浏览器分隔线厚度约 `4px`；AUI 黑线厚度约 `3px`。
12. 浏览器分隔线长度到卡片区域右侧约 `x=1972`；AUI 黑线到详情栏边界前，长度接近但具体起点左移。

## 6. 文件网格

1. 浏览器第一张文件卡左边约 `x=553..555`；AUI 第一张卡左边约 `x=546..548`，左移约 `7px`。
2. 浏览器第一张卡右边约 `x=812..814`；AUI 第一张卡右边约 `x=807..809`，左移约 `5px`。
3. 卡片宽度基本一致，浏览器约 `262px`，AUI 约 `263px`。
4. 卡片之间 gap 基本一致，约 `29px`。
5. 浏览器第一张卡顶部约 `y=295..297`；AUI 第一张卡顶部约 `y=333..336`，扣除标题栏后约 `280..283`，比浏览器高约 `12..17px`。
6. 浏览器第一张卡底部约 `y=538..540`；AUI 第一张卡底部约 `y=568..570`，扣除标题栏后约 `515..517`，比浏览器高约 `21..25px`。
7. 因此 AUI 卡片高度略小，或顶部/底部布局同时发生压缩。
8. 浏览器卡片边框颜色为浅灰，AUI 也是浅灰，但 AUI 边框更硬、更像整数像素线。
9. 浏览器卡片内 icon 到顶部的距离较大；AUI 中 icon 看起来略靠上。
10. 浏览器卡片内文件名与 icon 间距较舒展；AUI 中略紧。
11. 浏览器卡片内 meta 文本到文件名间距接近 CSS `margin-top: 6px` 的视觉结果；AUI 中间距看起来略小。
12. 浏览器 `LEVEL.DAT` 文件图标线条较细；AUI 线条偏粗。
13. 浏览器文件夹图标紫色块边缘更平滑；AUI 边缘更硬。
14. 浏览器 `ICON.PNG` 图片图标内部山形图案完整；AUI 图标内部紫色区域更像实心矩形，细节有缺失或被简化。
15. 浏览器 `SESSION.LOCK` 锁图标黑色锁梁较圆滑；AUI 锁梁粗细、抗锯齿不同。
16. 浏览器卡片整体垂直居中观感较好；AUI 卡片内容略偏上。
17. 浏览器五张卡片最后一张右边约 `x=1972`；AUI 最后一张右边约 `x=1978`，AUI 整排宽度略向右多延伸。

## 7. 右侧详情面板

1. 浏览器详情面板左边界约 `x=2029..2031`；AUI 约 `x=2035..2038`，AUI 向右偏移约 `6..9px`。
2. 浏览器详情面板宽度约 `529px` 到右边；AUI 约 `522px` 到右边，AUI 更窄。
3. 浏览器详情面板左边竖线浅灰；AUI 同样浅灰，但位置不同。
4. 浏览器 `SELECT FILE TO VIEW DETAILS` 位于面板中上部，约 `x=2125`、`y=270`。
5. AUI 文本位于约 `x=2115`、`y=310`，扣除标题栏后约 `y=257`，比浏览器略高约 `13px`，水平更靠左约 `10px`。
6. 浏览器该提示文字字母间距更宽；AUI 字符间距和字体度量略不同。
7. 浏览器提示文字颜色较浅；AUI 颜色接近，但边缘更硬。
8. 浏览器右侧面板底部延伸到截图底部；AUI 面板底部只到游戏内容可见区域，之后是额外白/窗口区域。

## 8. 字体与文字渲染

1. 两者都没有明显使用系统默认 serif，整体字体族接近，但不是完全一致。
2. AUI 字体度量更紧：面包屑、Sidebar、文件名的水平占用普遍比浏览器略短。
3. AUI 字体抗锯齿更硬，黑色文字边缘更锐；浏览器字体边缘更平滑。
4. 浏览器大标题 `SURVIVAL` 字间距视觉上更接近 CSS `letter-spacing: 1px`；AUI 字间距略小。
5. 浏览器 Header logo `MINE//EXPLORER` 字符间距更宽；AUI 更紧。
6. 浏览器 `DIRECTORIES` 的 `letter-spacing: 3px` 效果明显；AUI 也有字距，但实际展开宽度更小。
7. 浏览器灰色文字更接近 CSS `#999999` 的抗锯齿混合结果；AUI 灰色文字边缘像直接像素混合，灰度略有差异。
8. AUI 的 SVG 内文字无关，但图标线条也呈现类似硬边，说明差异不只是字体，还包括图形抗锯齿/缩放。

## 9. 颜色与绘制

1. Header 底部紫线两者颜色接近。
2. Content 标题下方横线颜色不同：浏览器为紫色，AUI 为黑色。
3. Header 背景的 repeating-linear-gradient 竖线：浏览器可见度更高，AUI 更淡或缺失。
4. Header 扫描渐变效果在截图静帧中不容易完全判断，但浏览器截图里能看到更明显的淡紫覆盖，AUI 不明显。
5. 左侧选中行背景色浏览器与 AUI 接近，但 AUI 色块可能略淡。
6. 浅灰边框颜色接近，但 AUI 边框边缘更硬，浏览器边缘更柔。
7. 图标紫色基本一致，但 AUI 内部 SVG 某些透明度/opacity 表现不同，尤其 `ICON.PNG` 图标的图片区域。
8. 浏览器页面背景 `#fafafa` 与 AUI 背景接近，但 AUI 大面积底部空白受窗口背景/内容区域影响，看起来更纯白。

## 10. SVG 图标与伪元素

1. 文件夹、文件、图片、锁图标整体都渲染出来了，没有完全丢失。
2. AUI SVG 图标尺寸和浏览器接近，但边缘质量不同。
3. AUI 对 SVG `opacity`、细线 stroke、path 抗锯齿的表现与浏览器不同。
4. `content-header::after` 是伪元素，浏览器正确用紫色覆盖黑色 border；AUI 没有得到相同结果，这是伪元素或定位绘制顺序的明确差异。
5. `.header::before` 的 repeating-linear-gradient 在浏览器中可见，在 AUI 中弱很多；这是 background gradient 或 alpha 混合差异。
6. `.header::after` 的 animated linear-gradient 在 AUI 中也不明显；可能是动画时刻不同，也可能是 gradient/animation 支持差异。
7. `.sidebar-title::after` 的紫色短线两者都出现，但长度、厚度和位置有轻微差异。

## 11. 交互状态相关静态差异

1. 当前截图没有 hover，因此 hover 阴影、hover 位移、hover 紫色角标无法比较。
2. 当前截图没有选中文件卡，因此 `.file-card.selected`、详情面板 active 状态无法比较。
3. 当前截图显示初始动画可能已结束，但如果截取时机不同，`animation` 可能影响 Header 扫描线和 logo pulse 的瞬时位置。
4. AUI 中 `logoPulse` 如果当前帧处在非 `scale(1)`，logo 方块尺寸会与浏览器截图有轻微差异；截图中差异不明显。

## 12. 最可能影响“看起来不像浏览器”的关键差异排序

1. Header 右侧按钮没有被推到最右，`margin-left: auto` / flex 剩余空间行为与浏览器不一致。
2. Content header 的紫色伪元素横线没有覆盖黑色 border，导致浏览器是紫线，AUI 是黑线。
3. AUI 截图包含游戏窗口标题栏且画布高度不同，造成整体下方空间比例完全不一样。
4. 左侧栏宽度、详情栏位置与浏览器相差约 `6..10px`，说明横向布局计算不是完全一致。
5. 字体度量不同导致导航、标题、树节点、按钮文本的水平占用不同。
6. SVG/线条/字体抗锯齿不同，让图标、边框、文字的视觉粗细和浏览器不一致。
7. Header 背景渐变/透明度效果不一致，浏览器更能看到细网格，AUI 更接近纯白。
8. 文件卡纵向位置和高度略有压缩，卡片内容显得更靠上。

## 13. 需要后续用代码验证的点

1. `display: flex` 中 `margin-left: auto` 是否被实现；Header 按钮位置问题优先查这里。
2. `position: absolute` + pseudo element + border 覆盖顺序是否与浏览器一致；Content 黑线问题优先查这里。
3. `box-sizing: border-box` 是否完整作用于所有元素；Sidebar 和卡片尺寸偏差可能与此有关。
4. `100vh` 在 `mode=window` 下绑定的是游戏内容高度、窗口物理高度还是 framebuffer 高度；这决定 AUI 和浏览器高度为何不同。
5. `font-mode=web` 是否只改字体大小，还是同时模拟浏览器字体度量；当前看起来仍有度量差异。
6. SVG stroke、opacity、path fill 与 CSS px 到实际像素的映射是否存在取整差异。
7. CSS `repeating-linear-gradient`、`linear-gradient`、`animation` 的支持是否完整；Header 背景差异需要查绘制路径。

## 14. 二次复查补漏

1. CSS 中 `.sidebar { width: 280px; }`，但截图量到的 Sidebar 物理宽度不是 `280px`：浏览器约 `495px`，AUI 约 `488px`。这说明两张截图的物理像素与 CSS px 之间存在缩放换算，不能把截图像素直接当 CSS px。
2. 按 Sidebar 反推，浏览器约为 `495 / 280 = 1.77` 截图像素每 CSS px；AUI 约为 `488 / 280 = 1.74`。两者缩放比例接近但不相同，相差约 `1.4%..1.8%`。
3. 这个比例差会累积到横向大布局：Header logo、面包屑、Content 起点、详情栏边界都会出现几个到十几个像素的偏移。
4. 浏览器截图中 CSS `height: 60px` 的 Header 实际到紫线约 `100px`，比例约 `1.67`；AUI 扣除标题栏后也是约 `100px`。垂直比例和水平比例并不完全一致，说明截图、窗口 DPI、浏览器缩放或 MC framebuffer/GUI scale 至少有一处不是一比一。
5. AUI 的窗口标题栏不是页面的一部分，但截图中它占了约 `53px`，并且顶部第一行有灰色标题栏背景、应用图标、窗口控制按钮；浏览器截图完全没有这一层。这会让“第一屏视觉比例”天然不同。
6. 浏览器截图左上角页面外框有圆角，AUI 没有页面圆角，只有游戏窗口矩形边界。这个差异不是 `resource.html` 的 CSS，而是截图宿主环境差异。
7. 浏览器截图最底部有黑色圆角边框区域；AUI 截图最底部有游戏窗口自身的深色/红黑边缘。底边视觉不能直接对比 UI CSS。
8. Header 右侧按钮黑色像素列复查：浏览器按钮黑边主要出现在 `x=2070` 之后；AUI 按钮黑边主要出现在 `x=989` 之后。这个不是几像素偏差，而是 flex auto margin/剩余空间分配级别的布局错误。
9. AUI 中 `BACK / UP / NEW` 按钮虽然水平位置错了，但按钮自身的内边距、边框、文字相对关系大体成立，说明问题更偏向父级 flex 布局，而不是按钮盒模型本身。
10. 浏览器 Header 右侧按钮距离右边缘仍保留一段 padding；AUI 按钮后方到右边缘留下超过一千像素空白，这是视觉重心偏左的主要原因。
11. Content 标题分隔线复查像素：浏览器在 `x=560,y=250` 是 `rgb(139,92,246)`；AUI 在对应内容线处 `x=560,y=288` 是 `rgb(26,26,26)`。这确认不是色差，而是紫色覆盖层没有出现或被黑线覆盖。
12. AUI 的黑色内容线横向检测从约 `x=546` 连续到 `x=2034`，刚好贴近中间内容区域宽度；这更像 `.content-header` 自身 `border-bottom` 被画出来，而不是 `.content-header::after` 被画出来。
13. 浏览器中同一条内容线为紫色，意味着浏览器最终绘制的是 `::after`；AUI 中最终绘制的是 border。应优先检查伪元素生成、absolute 定位、`bottom: -2px`、以及 border 与 child/伪元素绘制顺序。
14. `.content-header::after` 使用 `position: absolute; bottom: -2px; height: 2px; width: 100%;`，它依赖父级 `position: relative`。如果 AUI 的 absolute containing block 或负 bottom 支持有偏差，就会导致紫线消失、错位或被裁掉。
15. Header 背景网格来自 `repeating-linear-gradient(... rgba(...,0.03) ...)`，透明度非常低。AUI 中几乎不可见可能是 alpha 混合精度、颜色四舍五入、或 gradient stop 处理差异，不一定是完全没有渲染。
16. Header 扫描线来自 `.header::after` 的动画渐变，截图是瞬时帧；如果两张截图动画时间不同，扫描线位置不应作为稳定差异。但 AUI 中整体更不明显，仍需查 animation + linear-gradient 的组合支持。
17. 源码里 `font-family: 'Chakra Petch', sans-serif;` 但没有 `@import` 或 `@font-face` 加载 Chakra Petch。浏览器是否真的使用 Chakra Petch 取决于本机是否安装该字体；AUI 是否能找到同名字体也取决于框架字体系统。因此字体差异不能只归因于 `font-mode=web`。
18. `font-mode=web` 即使让字号接近浏览器，也未必等价于浏览器字体 fallback、字重合成、hinting、letter-spacing、line-height normal 计算。当前截图里文字宽度更紧，说明字体度量仍未完全对齐。
19. 文件中按钮和树节点使用了三角符号，例如 `◀`、`▲`、`▸`、`▾` 一类字符；PowerShell 读取时这些符号显示成乱码形态，需要确认实际文件字节编码。截图里符号能显示，但如果源文件不是稳定 UTF-8，会影响跨环境复现。
20. 浏览器中 Header logo 方块左侧留白约为 CSS padding 乘缩放后的结果；AUI 左侧留白更小，可能同时受 viewport/scissor 起点、body 默认区域、或 header padding 缩放取整影响。
21. AUI 中 logo、面包屑、Content 起点、文件卡左边都比浏览器偏左约 `5..10px`，这是一个一致方向的偏移；但详情栏边界反而更靠右，说明不是单纯整体平移，而是布局宽度分配也不同。
22. 浏览器 `content` 区域从 Sidebar 后开始后有 `padding: 32px`；截图中实际物理 padding 浏览器约 `56px`，AUI 约 `57px` 左右，单独看 content padding 并没有明显错，主要差异来自前面的 Sidebar 宽度和整体缩放。
23. 文件卡 CSS 使用 `grid-template-columns: repeat(auto-fill, minmax(140px, 1fr))`。当前五张卡在两边都排成一行，说明 auto-fill 基本工作；但每列最终宽度和整排终点略不同，可能来自 grid 容器可用宽度不同。
24. 文件卡高度在 AUI 中看起来略矮，可能与 `padding: 20px 16px`、SVG 视口缩放、字体 line-height、以及 border 取整共同有关；不能只归因于 card height。
25. 详情面板 CSS 宽度是 `300px`，截图中浏览器约 `529px`、AUI 约 `522px`，和 Sidebar 的比例差一致，进一步支持“缩放比例不完全一致”的判断。
26. `detail-empty` CSS 是 `margin-top: 60px`，浏览器和 AUI 扣除标题栏后纵向差约十几像素；这可能来自 Header/Main 高度换算，而不是 detail 自身 margin 单独错误。
27. AUI 中 SVG 的黑色 stroke 边缘偏硬，尤其文件图标、图片图标、按钮边框；这可能来自缺少浏览器级亚像素抗锯齿或 stroke 对齐策略不同。
28. 浏览器中 `ICON.PNG` 图标内部的半透明紫色背景和山形更有层次；AUI 中更接近块状，建议单独检查 SVG `opacity="0.2"` 和 path 填充顺序。
29. 浏览器和 AUI 都没有显示滚动条，虽然 `.sidebar`、`.content`、`.detail-panel` 都是 `overflow-y: auto`；当前内容高度不足以触发滚动，因此不能从这两张图判断滚动条样式是否一致。
30. 当前截图没有 hover、active、selected file、detail active 等状态，不能把交互态一致性纳入本次结论；后续需要单独截图这些状态。
31. 当前截图可能在初始进入动画之后截取，但 `logoPulse`、`blink`、`scanline` 是持续动画。任何涉及 logo 尺寸、分隔三角透明度、扫描线位置的差异，都需要同一时间点或禁用动画后再确认。
32. 浏览器截图中页面顶部没有系统标题栏，可能来自浏览器全屏、网页截图工具或裁剪；AUI 是窗口截图。若目标是“和浏览器视觉一样”，应先确定对比基准是浏览器 viewport 内容区，还是完整窗口截图。
33. `body { height: 100vh; overflow: hidden; }` 在浏览器中绑定 viewport 内容区；AUI 中 `100vh` 绑定哪个坐标系仍是关键问题。如果绑定了包含窗口标题栏之外的 framebuffer 或 MC GUI scaled height，就会导致下方空白比例不同。
34. `mode=window` 目前看起来解决了宽度接近，但没有保证 CSS px 与浏览器 CSS px 完全同尺度。若目标是浏览器一致，需要一个更明确的 `browser/css` viewport 模式或 DPI/GUI scale 映射策略。
35. 从视觉优先级看，修复顺序建议是：先解决 Header flex auto margin，再解决伪元素紫线，再统一 CSS px 缩放比例，然后处理字体/SVG 抗锯齿。前三项决定结构，后两项决定质感。

## 15. 三次复查补漏

1. 需要更正上一轮第 19 点：`Select-String` 能正确读取源码中的 `◀`、`▲`、`▸`、`▾`，所以不能判断源文件本身乱码。之前看到的乱码更像是 PowerShell/工具输出编码显示问题；实际文件内容应按 UTF-8 正常看待。
2. 虽然源码字符是正常的，但截图中符号仍需要单独比较：浏览器里的三角符号边缘更平滑，AUI 中更硬、更接近像素化字体或不同 fallback 字体渲染。
3. AUI 截图标题栏区域从 `y=0` 开始不是纯页面背景，而是 Windows/Minecraft 窗口装饰：左上角有草方块图标，标题文字是 `Minecraft Forge* 1.20.1`，右上角有最小化、最大化、关闭按钮。任何自动图像 diff 如果不先裁掉 `0..52px`，都会把大量非 UI 差异算进去。
4. 浏览器截图左上角 `x=0..10,y=0..10` 接近纯白，并带有圆角裁剪；AUI 同一区域是窗口标题栏灰色。这个差异不是框架渲染问题。
5. 对 Header 左侧区域做非白像素边界检测时，浏览器区域会包含截图外框和圆角导致 `MinY=0`；AUI 裁掉标题栏后 Header 实际非白内容约从 `y=75` 开始。说明简单“非白像素 bounds”会被宿主边框污染，不能直接作为元素 box。
6. 浏览器截图外圈黑边和圆角会干扰最左、最右、最底部的像素统计；AUI 没有同样外圈。后续机器比较应裁剪为页面 viewport 内部再算。
7. AUI Header 左侧 logo 区域扣除标题栏后从页面内容顶端到 logo 上边的距离略小，给人的感觉是 Header 内容更贴上；浏览器因截图圆角/顶部边界和抗锯齿，看起来顶部留白更柔和。
8. Header logo 方块内部白色小方块在两边都存在，但 AUI 小方块边缘更硬，浏览器小方块边缘有轻微抗锯齿混合。
9. Header logo 紫色方块在浏览器截图中可能正处于 `logoPulse` 动画的某个缩放帧；AUI 也可能处于不同帧。严格比较 logo 方块尺寸前应禁用 `logoPulse`。
10. `.logo { gap: 12px; font-size: 20px; letter-spacing: 2px; }` 在 AUI 中视觉 gap 和文字展开宽度都略紧，说明 flex gap、字体度量、letter-spacing 三者至少有一个没有完全等价。
11. `.nav-path { margin-left: 40px; gap: 8px; }` 在 AUI 中面包屑整体更靠左，但这不仅是 `margin-left` 问题；因为前面的 logo 文字本身宽度也不同，后续所有 nav 起点都会被连带拉动。
12. Header 按钮区检测到 AUI 非白区域在 `x=900..1500` 范围内，而浏览器右侧按钮检测区域在 `x=2000..2559`。这个补充确认按钮问题可以只盯父 flex 行，不必先查按钮内部绘制。
13. `.header-actions` 自身设置了 `position: relative; z-index: 1;`。如果 AUI 的 flex 布局没有正确处理 auto margin，那么 z-index/position 不是首要嫌疑；它们更可能只影响覆盖顺序。
14. `.action-btn::before` 设置 `z-index: -1`，正常静态状态不可见。当前截图没有 hover，因此按钮 hover 背景层不能用这两张图验证。不要把 hover 伪元素问题混进当前静态差异。
15. Content header 使用 `justify-content: space-between`，浏览器与 AUI 中 `SURVIVAL` 和 `5 ITEMS` 的左右分布基本成立。也就是说 flex 的 `space-between` 至少在这个容器里可用；Header 的问题更具体地指向 `margin-left: auto`，不是所有 flex 都坏。
16. `.content-header` 的黑色 border 在 AUI 中完整出现，说明 border 绘制本身没问题；缺的是覆盖它的紫色 `::after` 或覆盖顺序。
17. 如果 AUI 支持伪元素但不支持负偏移绘制，`bottom: -2px` 可能让紫线被裁剪到父盒外；如果支持负偏移但绘制顺序错，紫线可能在黑色 border 下方或被 border 盖住。两种路径需要分别验证。
18. `.content-header::after` 没有显式 `z-index`。浏览器默认绘制顺序能覆盖 border；AUI 如果把 border 后画，就会得到现在的黑线结果。这个比“伪元素完全没生成”更贴近截图，因为其他伪元素例如 Sidebar 标题短线是存在的。
19. `.sidebar-title::after` 能显示紫色短线，说明至少一部分 `::after` 伪元素生效。Content 紫线问题不能简单归为“伪元素不支持”，应聚焦 absolute + bottom 负值 + border 覆盖。
20. 浏览器第一张卡片检测到非白边界约 `553..850 x 295..540`，AUI 对应约 `546..850 x 333..570`。AUI 宽度检测更宽但高度更矮，说明并非统一缩放，而是边框/内容/检测区域共同影响。
21. 卡片 CSS 没有固定高度，实际高度来自 `padding + icon + text + meta + line-height`。AUI 卡片更矮很可能来自文字 line-height 或 SVG 外盒高度计算，而不是单独的 `padding`。
22. `.file-icon` 设定 `48px x 48px`，内部 SVG `40px x 40px`。AUI 图标视觉更硬但尺寸接近，说明图标外盒布局大体成立，主要差异是 SVG rasterize/抗锯齿/opacity。
23. `.file-name { line-height: 1.4; }` 和 `.file-meta { margin-top: 6px; }` 对卡片高度影响很大。AUI 如果 line-height 计算按字体实际高度或整数取整不同，会导致卡片内容整体更紧。
24. 浏览器 `LEVEL.DAT` 文件名看起来垂直居中更自然，AUI 同样位置更靠上；这可能是 `align-items` 没问题但字体 ascent/descent 度量不同。
25. `.file-grid` 使用 `gap: 16px`，按截图物理像素两边 gap 接近 `28..30px`。这说明 grid gap 按当前缩放大体正确；文件卡横向间距不是主要问题。
26. 文件卡整排终点 AUI 更靠右，结合详情栏也更靠右，说明中间 content 可用宽度在 AUI 中略大。这个与 Sidebar 略窄一致。
27. 右侧详情面板内部提示文字位置相对面板左边界的偏差不大，但绝对位置受面板左边界右移影响。也就是说 detail 内部布局问题次于三栏宽度分配问题。
28. `.detail-empty { margin-top: 60px; }` 的效果在两边都能看出；AUI 扣标题栏后略高，仍可能是整体垂直尺度差和字体高度差共同导致。
29. 浏览器截图中 `5 ITEMS` 的灰色更淡、更细；AUI 边缘更硬。这个小字区域是观察字体 fallback/hinting 的好样本，因为它没有图标和复杂背景干扰。
30. 浏览器截图 Header 背景网格线在右侧空白区域更容易看见；AUI 右侧大片空白虽然存在，却几乎看不到同等网格，进一步说明 gradient alpha 或绘制精度差异真实存在。
31. `repeating-linear-gradient` 的 stop 是 `20px` 到 `21px` 的 1px 细线，透明度 `0.03`。在 AUI 当前缩放下如果 1 CSS px 被取整/采样到不同物理像素，线条可能变淡、断续或消失。
32. AUI 中大面积背景看起来更接近纯白，而 CSS `--bg` 是 `#fafafa`。需要确认 body 背景、main/content 背景、窗口清屏背景三者的覆盖关系；截图里有些区域可能不是同一个背景层。
33. 浏览器中 content 区和 detail 面板背景也有白/浅灰差别；AUI 中这种层次更弱。可能是 `#fff` 与 `#fafafa` 在当前渲染和截图压缩下更难分辨，也可能是某些容器背景覆盖范围不同。
34. 浏览器 Header 下方紫线在整个宽度上连续，并且和左右外框圆角边界相接；AUI 紫线在内容区域也是连续的，但不受圆角裁剪。边缘处理不同会影响最左/最右的观感。
35. 浏览器左侧选中行浅紫背景到右侧 Sidebar 边界结束；AUI 也结束于自己的 Sidebar 边界。由于 AUI Sidebar 窄，选中行宽度自然更短，这不是 selected 样式单独错误。
36. 树节点缩进依赖内联 `style="padding-left:${24 + depth * 16}px"`。如果 AUI 的内联样式解析、数字 px 缩放或 flex gap 有取整差异，树层级缩进会一层层累积；当前能看到子项位置略紧。
37. `tree-toggle.collapsed { transform: rotate(-90deg); }` 当前截图中 collapsed 三角能旋转显示，说明 transform 至少对该元素生效。不能把所有 transform 归为不支持。
38. 但 transform 后的三角符号在 AUI 中视觉中心略偏，可能是文本 glyph 旋转后的基线/盒子居中算法和浏览器不同。
39. 页面全局 `* { box-sizing: border-box; }` 对按钮、卡片、Header、Sidebar 都应生效。当前卡片和按钮尺寸没有大幅错，说明 box-sizing 很可能不是首要问题；更优先查 flex auto margin、伪元素覆盖、viewport 缩放。
40. 浏览器截图与 AUI 截图并非同一可视高度，所以“底部空白更多”不能直接归因于 `main height` 错。若要验证 `height: calc(100vh - 60px)`，应把两边截图裁成相同 viewport 内容高度后再比较。
41. AUI 的 `mode=window` 当前更像“使用窗口/framebuffer 尺寸参与布局”，但浏览器截图像是某个浏览器 viewport 或截图区域。若用户目标是肉眼一致，应要求一个固定浏览器 viewport 尺寸作为基准，而不是整张截图尺寸。
42. 如果后续要做自动回归，建议先生成两张裁剪图：浏览器裁掉外框圆角/边缘，AUI 裁掉 `0..52px` 标题栏和底部窗口边缘，再只比较页面内容区域。
43. 当前文档中所有坐标都是基于给定截图，不是 CSS 逻辑坐标；后续如果截图窗口大小、浏览器缩放、MC GUI scale 变化，这些坐标会变化，但差异类型仍然有效。
44. 第三轮后修复优先级不变，但原因更明确：Header 问题是 `auto margin`，Content 紫线问题是 `::after` 的负 bottom/绘制顺序，尺寸问题是 CSS px 与物理像素映射，质感问题是字体/SVG/gradient 采样。
