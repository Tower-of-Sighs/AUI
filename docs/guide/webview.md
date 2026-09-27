# WebView 与 iframe

AUI 本身不是浏览器——HTML 解析、CSS 布局、绘制都是自研引擎。但当页面需要**真正跑一个网页**（第三方站点、视频、不值得用 AUI 重写的复杂界面）时，框架留了一个逃生门：`<iframe>` 接的是**操作系统自带的 WebView**（Windows 上是 WebView2 / Edge Runtime），离屏渲染后当纹理贴进页面。

这一章讲 `<iframe>` 的完整行为、WebView 后端的架构与性能特性、能做什么和不能做什么。标准元素的能力边界见 [HTML/CSS 覆盖面](html-css-coverage)，`<iframe>` 作为扩展元素的速览见[扩展元素文档](extension-elements)，这里都不重复。

## 什么时候用

| 需求 | 选择 |
| --- | --- |
| 游戏内的 HUD、面板、设置页 | AUI 原生元素 |
| 和游戏数据 / 物品 / 方块实体交互 | AUI 原生元素 + [容器](container) |
| 跑一个真正的网页、第三方 Web 内容 | `<iframe>` |
| 播放视频、访问实时更新的外部文档 | `<iframe>` |

- `<iframe>` 的内容**在 AUI 之外**：页面里没有 AUI 的 DOM，AUI 脚本也读不到它的内容。它是"贴图 + 输入转发"，不是可组合的组件；
- 每个实例是一整套离屏浏览器，内存占用高，**不适合 Overlay 和世界内窗口**，留给全屏页或较大的面板；
- 只有 Windows x64 有后端，其他平台是空占位符（见[平台与可用性](#平台与可用性)）。

## 快速开始

```html
<iframe src="https://example.com/panel" style="width: 480px; height: 320px; border: 0;"></iframe>
<iframe src="file:///C:/pages/tool.html" style="width: 400px; height: 300px;"></iframe>
```

- `src` 必须是**绝对 URL**（`https:`、`file:` 等）。引擎没有文档 base URL，相对路径原样交给浏览器，解析不出结果；
- **只有写了 `src` 才启动浏览器实例**；没有 `src` 的 `<iframe>` 不占进程，只是一个占位盒子；
- 先用 `AuiServices.webView()` 确认后端可用（见[平台与可用性](#平台与可用性)），不可用时元素退化为空盒子。

## 属性

| 属性 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `src` | 绝对 URL | 无 | 决定是否创建实例；改它即导航（复用实例） |
| `width` / `height` | 数字（px） | 300 / 150 | presentational hint，仅当 CSS 未指定时生效 |
| `capture` | `auto` / `lossless`(`png`) / `fast`(`jpeg`) / `stream`(`raw`) | `auto` | 抓帧编码策略，见[抓帧与性能](#抓帧与性能) |
| `capture-scale` | 0.25 ~ 1.0 的浮点 | 1.0 | 栅格缩比；低于 0.25 夹到 0.25，非法或 ≤0 视为 1.0 |

没有 `zoom` 属性，也没有 `focus` 属性——`focus=` 只是 `status()` 的输出字段。运行中改 `capture` 立即生效；改 `capture-scale` 在下一 tick 重算栅格时才生效。

## 尺寸与布局

按 CSS 2.1 的替换元素规则实现，和浏览器一致：

```html
<iframe src="..."></iframe>                        <!-- 300x150，默认对象尺寸 -->
<iframe src="..." width="400" height="200"></iframe><!-- 属性 = presentational hint -->
<iframe src="..." style="width:400px"></iframe>     <!-- 400x150，缺的一边取默认值 -->
<iframe src="..." style="width:100%;height:240px"></iframe> <!-- 想撑满要显式写 -->
```

- UA 默认 `display: inline`，和浏览器一致。要块级布局自己写 `display: block`；
- `width`/`height` 属性是 **presentational hint**：作者 CSS 能覆盖它，没被覆盖时才是尺寸来源。只给一边时另一边取默认值（300 / 150），**没有等比换算**——iframe 没有内在宽高比（需要就自己写 CSS `aspect-ratio`）；
- 块级（或绝对定位）且 `width: auto` 时取**固有宽度，不撑满父容器**，`inset: 0` 也拉不开它。要撑满写 `width: 100%`；
- 布局、裁剪、transform、层叠、命中测试都和 canvas 这类贴图元素一样——因为它就是一张纹理：纹理被拉伸到元素的**内容盒**绘制，与栅格分辨率无关。

## 页面坐标与清晰度

- 内部页面的 **CSS 视口 = 元素的内容盒**（CSS px）：`innerWidth`、`vw/vh`、媒体查询都按内容盒算；
- 栅格按内容盒的**设备像素**（内容盒 × 设备缩放）抓取，页面侧 `devicePixelRatio` 等于该缩放，所以默认是 1:1 实分辨率，非 100% 屏幕缩放下也不糊；
- 栅格可能被三件事缩小：`capture-scale`、单边 4096 px 上限、按面积降采样（超过 120 万像素）。缩小时 WebView2 的 ZoomFactor（`zoom`，被限死在 0.25 ~ 5）会同步重算，**页面的 CSS 视口仍然精确**，只是画面变软；
- 纹理始终按内容盒绘制，所以栅格变小不会让内容错位，只牺牲清晰度。

## 渲染管线

`<iframe>` 的像素不是框架画的，而是这样来的：

1. **宿主线程**在一个屏幕外的隐藏窗口（`WS_POPUP`，默认停在 -32000,-32000）里用 WebView2 的 CompositionController 跑真实网页；同一进程内所有 iframe 共享一个浏览器进程和 profile；
2. 宿主周期性调用 WebView2 的 `CapturePreview` 拿截图，得到 PNG 或 JPEG 编码的字节（抓帧和编码都发生在浏览器进程里）；
3. **解码线程**（优先级低于普通线程）做 WIC 解码、通道字节交换，并把结果和"渲染器手上那张画布"按 32×32 瓦片比对，只把变化的矩形写进一块**共享内存段**；
4. 渲染线程按包读共享内存，只把变化的矩形写进纹理并只上传这些区域。

传输成本因此跟**变化面积**成正比，跟画布大小无关。每个实例各有一条宿主线程、一条解码线程、一块共享内存段和一个隐藏窗口；浏览器进程与 profile 是共享的。

## 抓帧与性能

- **单次抓帧有约 20ms 的固定成本**。WebView2 只提供无损（PNG）和有损（JPEG）两种格式，`CapturePreview` 每次往返最少 ~21ms（400×300 和 1280×720 都测过：小图 21ms、1280×720 约 28ms；页面静止时也是这个数，说明这部分是浏览器进程里的回读 + 编码 + IPC，不是"等新合成帧"）。重页面更贵：900×700 的 PNG 往返约 110ms（≈9fps），JPEG 约 31ms；面积成本约 20ms + 28ms/百万像素；
- `capture` 默认 **auto**：页面不动时用无损（配合下面的载荷去重，代价约 0），持续变化时自动切快速编码——250ms 窗口内发布 ≥2 帧就切，切完静默 1.5s 再回落。要钉死写 `capture="lossless"` 或 `capture="fast"`；
- **抓帧是流水线的**：那 ~21ms 大部分是"等"，所以最多同时挂 4 次 `CapturePreview`，一次在途时就发下一次；回调乱序返回时只发布最新的那一帧（旧帧丢弃，记 `stale=`）。实测（800×600、页面 60fps 重绘）PNG 33→**61fps**、JPEG 39→**61fps**，1280×720 从 30→**60fps**，1600×900 约 **59fps**。在途深度自适应：单次延迟 <45ms 挂 4 个，>60ms 降到 2 个；
- **两层去重**：①宿主把这一帧的压缩载荷和上一帧逐字节比对，完全相同且读取端没有欠账时**整帧跳过**（不解码、不传输、不上传）；②逐 32×32 瓦片比对，只发变化的矩形。实测 800×600 页面里只有一个 48×48 小方块在动时，每帧脏区约 **1.5%** 画布（同一页面用 JPEG 抓帧时平均上传约 **4%** 画布，JPEG 噪声会让更多瓦片变脏）；页面完全静止时**一个包都不发**。整帧传输只发生在首次、元素尺寸变化、以及读取端请求重刷时；
- **抓帧率跟随元素绘制率**：实测游戏 60fps 时抓 57 帧/s、30fps 时 27、20fps 时 20；请求间隔跟着实测帧时间走，夹在 16 ~ 50ms（50ms 是下限帧率，防止页面看起来冻住）；
- 取帧和取更新发生在**渲染阶段**（每渲染帧一次），不受 20Hz 逻辑 tick 限制；元素连续 2 秒没被绘制时暂停抓帧；
- `capture-scale` 是**省 CPU / 省带宽**的开关，不是帧率开关：800×600 → 400×300 单次抓帧省约 5ms，但流水线已经把这点延迟藏住了；
- `capture="stream"` 想绕开编解码直接拿原始像素流——**当前用不了**：`Windows.Graphics.Capture` 能启动、也能拿到帧，但每一帧都是**单色**（WebView2 的内容挂在 `IDCompositionTarget` 上，WGC 和 `PrintWindow` 都看不到这部分）。宿主发现看不到内容后会自我禁用并回退到编解码抓帧，`status()` 里会标出 `composition stream saw no content; fell back to the capture codecs`；默认 auto **不会**尝试它，要试就显式写 `capture="stream"`。

## 输入

- **鼠标移动 / 按下 / 松开**：指针在内容盒内、或已有按键按下（拖拽指针捕获）时转发，坐标夹到盒内；按下状态随移动一起发（否则 Chromium 会把拖拽判成悬停）。拖拽期间指针移出内容盒仍继续转发，松开也一定送达，`mouseLeave` 在按住期间不发；
- **指针移动会合并**：宿主只保留最新位置、每轮转发一次（拖拽时实测一半以上采样被合并掉，记 `coalesced=`）；按键、滚轮、移出这些有顺序含义的事件不合并；
- **滚轮**：转发给页面；页面自己有滚轮监听时 AUI 不再滚动父容器。注意 `iframe` 只阻止事件冒泡、不把滚轮交给原生，所以宿主文档**没有**声明 `aui-mouse-events: intercept` 时，滚轮仍可能漏到游戏（见 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)）。滚轮只按符号换算成 **1 格**、横向滚轮不转发，`deltaY` 为 0 会算成向上；悬停在 iframe 上按 **Ctrl+滚轮**会优先被 AUI 的页面缩放吃掉、不进网页（除非页面 meta 关了 `user-scalable`，见[浏览器辅助功能](browser-features#页面缩放)）；
- **键盘**：iframe 成为文档焦点元素后，`keydown`/`keyup` 转发给页面并**吞掉**这些按键，Minecraft 快捷键不会同时触发。文字输入要求**网页自己的可编辑元素拿到 DOM 焦点**（先在网页里点一下输入框），和真浏览器一致；没有元素持有焦点时字符会被**静默丢弃**（页面完全不会给反馈）；
- **没有原生键盘注入 API**：按键是在页面里**合成 DOM 事件**实现的，浏览器的默认动作不会自动发生，得由框架显式补上（输入框里的退格/删除/方向键已处理；`textarea` 里回车换行，单行输入框里回车提交所属表单，和浏览器一致）；IME 组合输入以"已上屏文本"送入，没有组合过程。

## 生命周期

- 设置 `src` 后，实例在**后台线程**创建（首次要几百毫秒，不卡帧），期间 `status()` 是 `starting`；
- 改 `src` 复用已有实例导航，不重建；想强制重载就对同一个值再 `setAttribute("src", ...)`（元素不做去重）；
- `removeAttribute("src")`、把元素从文档移除、以及**关闭整个文档**，都会释放该实例（浏览器、宿主线程、解码线程、共享内存段）；
- 浏览器数据（cookie、localStorage）存在 `游戏目录/apricity/.cache/webview` 下，重启后保留。它在**页面根目录之外**的缓存区里（和网络缓存 `apricity/.cache/network` 并列）：`apricity/` 本身是给人写页面、给资源扫描和热重载遍历的目录，而浏览器 profile 是几万个文件、随时在变的机器状态（它的缓存条目还正好落在热重载监听的 `.html/.css/.js` 上），放在资源树里会被当成资源、还会误触发热重载；`.` 开头的目录资源扫描和热重载都会跳过（见[资源管理](resource-manager)）；
- **后端不可用是粘性的**：一旦创建失败或实例失效，该元素此后只当占位符，不会重试（即使改 `src`）。

## 弹窗与导航

- `window.open` / `target="_blank"` 被**当前视图接管并原地导航**——点链接就在这个 iframe 里跳转，不会在桌面上弹出一个你看不见、也动不了的窗口；
- `window.close()` 不处理（没有属于我们的窗口可关）。

## 平台与可用性

- **仅 Windows x64**：其他平台、以及非 64 位 x86 的 JVM 都没有后端；
- 需要系统装了 **WebView2 Runtime**；没装、或装了但版本过旧（不支持离屏 hosting）都会失败；
- 用 `AuiServices.webView()` 判断：

```java
AuiServices.webView().isAvailable();        // 后端是否可用
AuiServices.webView().backendName();        // "webview2"
AuiServices.webView().unavailableReason();  // 不可用原因
```

  不可用原因包括：非 Windows（`offscreen WebView2 hosting is Windows-only`）、非 64 位 x86（`needs a 64-bit x86 JVM`）、jar 内原生库缺失、`native library not loaded`、`WebView2 runtime is not installed`、runtime 过旧；
- 不可用时 `<iframe>` 什么都不画，但 CSS 背景和边框照常显示——它是空盒子，不是崩坏。

## 限制

- 相对路径、`srcdoc`、`sandbox` 都不支持；
- 页面里的 `window.parent` / `postMessage` 指向浏览器内部，**没有**接到 AUI 上；`contentWindow` / `contentDocument` 也没有暴露；
- 页面内容不透明合成，没有 alpha 通道；
- ZoomFactor 被 WebView2 限死在 0.25 ~ 5，SDK 无法放宽；
- **没有面向脚本的 webview API**：JS 侧只有通用 DOM 属性 / 事件（`getAttribute`/`setAttribute`）；Java 侧也没有 `reload()` / `navigate()` 这类方法，用改属性代替。

## 调试

`Iframe.status()` 是主要入口，三种形态：`unavailable: <原因>`、`no view` / `starting`、或完整一行。完整一行里的字段：

| 字段 | 含义 |
| --- | --- |
| `ready=` | WebView2 控制器是否就绪 |
| `nav=` | 导航完成次数 |
| `capture=` / `done=` / `rejected=` | 发起的抓帧 / 完成的抓帧 / 失败或被拒的抓帧 |
| `stale=` | 返回太晚、被更新的帧顶掉的旧帧 |
| `decodeFail=` / `bytes=` | 解码失败数 / 最近一帧的压缩载荷字节 |
| `roundTrip=` / `decode=` / `period=` | 最近一次抓帧往返 / 解码耗时 / 相邻抓帧起始间隔（ms） |
| `loop=` / `fps=` | 宿主循环 Hz / 过去 1 秒发布的帧数 |
| `cmd=<last>/<max>ms` | 命令在队列里的排队时延（当前 / 峰值） |
| `pending=` / `dropped=` / `coalesced=` | 待解码数 / 解码积压丢弃数 / 被合并掉的指针移动数 |
| `raster=` / `format=` / `window=` | 栅格尺寸 / 实际编码格式 / 宿主窗口尺寸 |
| `stream=<回调>/<帧>/<空回调>/<单色>@<W>x<H>` | 原始像素流的合成计数 |
| `raster capped by area` | 触发了按面积降采样 |
| `box=` / `zoom=` / `focus=` / `buttons=` | 内容盒尺寸 / 当前 zoom / 该 iframe 是否是文档焦点元素 / 按住的鼠标键 |
| 增量流段 | `stream=<W>x<H> packets=<已应用>/<已发布> rects=… payload=<已用>/<已发布>KB [resync=<n>]` |
| `channel=…` | 共享内存段的读写位置、包数、矩形数、整帧数与重同步次数 |

- **frame timing HUD**（DevTools `F12`）末尾会多一段 `stream=…` 和宿主那行的 `fps/period/roundTrip/cmd/raster`，可以直接在游戏里看抓帧率和输入排队；
- 读法：`fps=` 是抓帧率，`period=` 是相邻抓帧间隔（远大于 `roundTrip=` 说明是调度问题，接近说明抓帧本身到顶），`cmd=` 是输入排队时长（大就是 UI 线程被占），`decode=` 是解码耗时，`stale=` / `dropped=` 是丢帧。

## 常见问题

**页面一片空白**：先确认后端可用（`AuiServices.webView().unavailableReason()`）。非 Windows、没装 WebView2 Runtime、或非 x64 都会退化成空盒子；`src` 用了相对路径也会解析不出内容。

**点了输入框还是打不进字**：看 `Iframe.status()` 里的 `focus=`。`no` 说明这个 iframe 不是文档的焦点元素（AUI 不会把字符转给它）；`yes` 但还打不进，就是网页里没有元素持有焦点——常见原因是**你没点在输入框上**：固定宽度站点（例如 mcmod.cn，正文约 1200px 宽）在窄视口下会把输入框甩到视口右侧之外，`status()` 里的 `box=` 就是页面的 CSS 视口尺寸，比这个宽度小就说明得先在页面里横向滚动才能点到它。

**Ctrl+滚轮把整个页面缩放了，而不是滚网页**：Ctrl+滚轮在转发给 iframe 之前就被 AUI 的视口缩放处理掉了。要让滚轮进网页，得先关掉宿主页面的用户缩放（`<meta name="aui-viewport" content="user-scalable=false">`，见[浏览器辅助功能](browser-features#页面缩放)）。

**滚轮把游戏快捷栏也切了**：宿主文档没有声明 `aui-mouse-events: intercept`，原生滚轮漏到了游戏。补上这个 meta（见 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)）。

**大 iframe 画面发软**：触发了单边 4096 上限或按面积降采样（`status()` 里会有 `raster capped by area`）。这是有意为之——保帧率、只牺牲清晰度，页面的 CSS 视口仍然精确。

**视频播起来很卡**：抓帧有固定成本，重页面 PNG 会掉到个位数帧率。保持默认 `capture="auto"`（会自动切 JPEG），必要时再降 `capture-scale`；想钉死 JPEG 用 `capture="fast"`。

**改了 `src` 没反应**：目标 URL 和当前一样时元素不做去重、仍会导航；没有 `src` 属性则永远不会创建实例。若还是旧内容，看 `status()` 的 `nav=` 有没有增加。
