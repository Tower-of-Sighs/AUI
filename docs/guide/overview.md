# ApricityUI 总览

ApricityUI 是一个 Minecraft 模组：用 HTML、CSS、JavaScript 三件套写游戏 UI。它不是内嵌浏览器——没有 Chromium，HTML 解析、CSS 布局、绘制全部是自研引擎，页面脚本由 Rhino 执行。换来的是轻量：不引入浏览器进程，页面直接画进 Minecraft 的渲染管线，能读写游戏内的物品、方块实体、世界数据。

这篇是全部能力的地图，每个方向都链到对应的专题文档。

## 页面能放在哪

一个 HTML 页面（Document）有四种宿主，覆盖 Minecraft UI 的全部场景：

| 宿主 | 场景 | 文档 |
| --- | --- | --- |
| `ApricityScreen` | 全屏 GUI：设置页、主菜单式界面 | [ApricityScreen](apricity-screen) |
| `ApricityContainerScreen` | 容器界面：背包、机器、存储，带真实槽位 | [容器文档](container) |
| `WorldWindow` | 世界内平面：信息牌、机器外屏、实体头顶标签 | [WorldWindow](world-window) |
| Overlay Document | 悬浮层：HUD、Toast、常驻面板 | [Overlay 文档](overlay-document) |

四种宿主里跑的是同一套页面：同样的 DOM、CSS、脚本能力，只是显示位置和输入路径不同。

页面行为由三个 meta 控制——逻辑视口（`aui-viewport`）、字体模式（`aui-font-mode`）、鼠标拦截（`aui-mouse-events`）。完整说明集中在 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)。

## 页面里能用什么

**HTML/CSS**：选择器层接近完整；布局是常用子集（flex、grid 可用，没有 float、sticky、表格布局）；绘制层覆盖很广——阴影、滤镜、clip-path、transform、动画都行。注意**没有 UA 默认样式**，`h1` 和 `div` 长得一样，样式全自己写。完整清单：[HTML/CSS 覆盖面](html-css-coverage)。

**JavaScript / Web API**：DOM 查询修改、事件（捕获冒泡都有）、表单和约束校验、fetch、localStorage、Canvas 2D、Observer、定时器都能用。它是浏览器风格 API 的子集，不是完整浏览器——没有 WebGL、XHR、history、完整 Promise。哪些可用、哪些是轻量兼容、哪些根本没有：[Web API](web-api)。

**扩展元素**：标准标签之外的一组 MC 向标签——`<texture>`（游戏纹理）、`<sprite>`（图集帧动画）、`<translation>`（本地化）、`<svg>`（矢量图标）、`<container>/<slot>/<recipe>`（物品槽位）。用法：[扩展元素](extension-elements)。

**浏览器式辅助行为**：Ctrl+滚轮缩放、文字选择复制、剪贴板、表单默认按键、滚动：[浏览器辅助功能](browser-features)。

**Ore 主题**：内置的 MC 风格纯 CSS 主题（像素边框、深色表面、绿紫金强调色），引一行 CSS 就有成套的按钮、卡片、表单、表格、徽章样式，另有配套的**可视化编辑器**在游戏里拖页面、调 token、导出 HTML：[Ore 主题](ore-theme)。

## 容器：和真实物品打交道

容器页面能把 HTML 槽位绑定到真实数据源——玩家背包、方块实体 capability、实体 capability、世界级 SavedData 持久库存。HTML 负责结构和样式，服务端菜单负责物品逻辑和安全校验；shift-click、拖拽、权限都走 MC 原生菜单规则。打开方式只有一条正路：服务端 `ApricityUI.menu(player, path).bind(...)`。细节：[容器文档](container)。

## 资源从哪来

页面和资源（CSS、图片、字体、数据 JSON）用**逻辑路径**引用，如 `screens/home.html`。资源有三层来源：模组 jar 内置、资源包、本地 `apricity/` 目录，上层覆盖下层；远程资源走受限 HTTPS 白名单管线。按 `END` 全量重载。游戏内按 `F10` 打开**资源管理器**：浏览、预览、新建、改 meta、查引用。规则：[资源管理](resource-manager)。

## 怎么打开页面

**Java**：统一入口 `com.sighs.apricityui.ApricityUI`——`createDocument`、`new ApricityScreen(path)`、`menu(player, path).bind(...)`、`createWorldWindow(...)`。

**KubeJS**：客户端/服务端脚本里注入全局 `ApricityUI`，方法集按侧隔离（客户端管 Document/Toast/WorldWindow，服务端管容器）。模组还能注册自己的 KJS 绑定。

完整 API 表和线程/空值/刷新规则：[模组专属 API](apricity-api)。

## 调试和工具

**游戏内 DevTools**（`F12`）：DOM 树、元素拾取、Attributes/Styles/盒模型检视、运行时改样式改结构、存回源文件、meta 编辑、受限控制台。见 [DevTools](devtools)。

**外部调试协议**：游戏内开 `remoteDebug` 后，本机 WebSocket（`127.0.0.1:25321`）可以查 DOM、读样式、模拟点击输入。仓库自带 Node 客户端和 MCP 桥，AI 工具可以直连运行中的页面。另有两个截图脚本做视觉回归。见[附加工具](tools)。

**帧耗时 HUD**：`debug.frameTimingHud` 显示 AUI 渲染耗时和批次统计，定位性能问题用。见[二次开发](secondary-development)。

**WPT 布局对比**：把 Web Platform Tests 的 CSS 布局页面在 Chromium 和 AUI 里各采一遍几何快照做 diff，用来验证布局引擎的浏览器一致性。见 [WPT](wpt)。

## 给模组作者的扩展点

- 注册自己的 HTML 标签：`@ElementRegister` + 扫描包，自定义绘制或纯语义元素都行；
- 注册自己的 KubeJS 全局对象：`@KJSBindings`；
- 内置 Java 组件库直接复用：DialogWindow、ContextMenu、ToastManager、Tooltip、ColorPicker、FilePicker：[内置 UI 库](ui-library)。

线程规则、刷新代次、注册细节：[二次开发](secondary-development)。

## 工程结构

仓库是 `common + targets` 多加载器结构：`common/` 是 loader 无关的共享代码（可独立编译测试），`targets/<loader>-<mc版本>/` 是独立 Gradle 工程（当前是 Forge 1.20.1），loader 绑定通过 SPI 下沉。构建命令、CI、发布流程见根目录 [README](../../README)。

## 文档地图

| 主题 | 文档 |
| --- | --- |
| 全屏页面、三个 meta 的权威说明 | [apricity-screen.md](apricity-screen) |
| 悬浮层 / HUD | [overlay-document.md](overlay-document) |
| 容器和真实槽位 | [container.md](container) |
| 世界内窗口 | [world-window.md](world-window) |
| 页面 JS / DOM API | [web-api.md](web-api) |
| HTML/CSS 支持度 | [html-css-coverage.md](html-css-coverage) |
| 扩展标签 | [extension-elements.md](extension-elements) |
| 缩放、选择、剪贴板等辅助行为 | [browser-features.md](browser-features) |
| 资源路径和资源管理器 | [resource-manager.md](resource-manager) |
| KJS / Java 模组 API | [apricity-api.md](apricity-api) |
| Ore 主题和可视化编辑器 | [ore-theme.md](ore-theme) |
| Java 组件库 | [ui-library.md](ui-library) |
| 游戏内 DevTools | [devtools.md](devtools) |
| 自定义元素 / KJS 绑定 / 帧耗时 | [secondary-development.md](secondary-development) |
| 外部调试协议、MCP、截图工具 | [tools.md](tools) |
| WPT 布局对比 | [wpt.md](wpt) |
| AI 开发与调试规则（给 AI 的 skill 文档） | [ai-skill.md](../ai-skill) |
