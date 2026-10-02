# ApricityUI 总览

ApricityUI 是一个 Minecraft 模组：用 HTML、CSS、JavaScript 写游戏 UI。它不是内嵌浏览器——HTML 解析、CSS 布局和绘制由 AUI 自己实现。页面脚本的运行能力依赖 loader target，详见本文的兼容矩阵。

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

页面行为由 meta 控制——逻辑视口（`aui-viewport`）、鼠标拦截（`aui-mouse-events`）等。完整说明集中在 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)。

## Loader 与脚本支持

仓库当前包含以下 target。此表反映源码中的实现与 CI 配置，不代表每个 target 都已作为发布文件提供：

| Target | MC | 页面脚本 | KubeJS 的 `ApricityUI` 绑定 | CI JDK |
| --- | --- | --- | --- | --- |
| Forge | 1.20.1 | 支持 | 提供 | 21 |
| Fabric | 1.20.1 | 当前不执行 | 不提供 | 17 |
| Fabric | 1.21.1 | 当前不执行 | 不提供 | 21 |
| NeoForge | 1.21.1 | 支持 | 提供 | 21 |
| Fabric | 26.1 | 当前不执行 | 不提供 | 25 |
| NeoForge | 26.1 | 支持 | 不提供 | 25 |

Java 的 common API 与 HTML/CSS 渲染在这些 target 中共用。Fabric target 当前不执行页面脚本；没有页面脚本支持时，页面仍可渲染，但 `<script>` 不运行。依赖脚本的交互可改用 Java 或选择支持页面脚本的 target。游戏内的资源管理器、DevTools 和资源重载按键默认未绑定，需在 MC 控制设置中自行绑定。

## 页面里能用什么

**HTML/CSS**：选择器层接近完整；布局是常用子集（flex、grid 可用，没有 float、sticky、表格布局）；绘制层覆盖很广——阴影、滤镜、clip-path、transform、动画都行。注意**没有 UA 默认样式**，`h1` 和 `div` 长得一样，样式全自己写。完整清单：[HTML/CSS 覆盖面](html-css-coverage)。

**JavaScript / Web API**：在支持页面脚本的 target 上，DOM 查询修改、事件、表单、fetch、localStorage、Canvas 2D、Observer、定时器和音频 API 可用。它是浏览器风格 API 的子集，不是完整浏览器。哪些可用、哪些是轻量兼容、哪些根本没有：[Web API](web-api)。

**MC 元素**：`<item>` 和 `<ingredient>` 显示物品，`<recipe>` 预览配方，`<container>/<slot>` 构成容器和槽位。详情见 [MC 物品与配方元素](mc-elements) 和[容器文档](container)。其他扩展标签见[扩展元素](extension-elements)。

**浏览器式辅助行为**：Ctrl+滚轮缩放、文字选择复制、剪贴板、表单默认按键、滚动：[浏览器辅助功能](browser-features)。

**Ore 主题**：内置的 MC 风格纯 CSS 主题（像素边框、深色表面、绿紫金强调色），引一行 CSS 就有成套的按钮、卡片、表单、表格、徽章样式，另有配套的**可视化编辑器**在游戏里拖页面、调 token、导出 HTML：[Ore 主题](ore-theme)。

**McUI 主题**：另一套使用同一组件类与 token 契约的纯 CSS 主题，切换时只需更换样式表和根作用域类：[McUI 主题](mcui-theme)。

## 容器：和真实物品打交道

容器页面能把 HTML 槽位绑定到真实数据源——玩家背包、方块实体 capability、实体 capability、世界级 SavedData 持久库存。HTML 负责结构和样式，服务端菜单负责物品逻辑和安全校验；shift-click、拖拽、权限都走 MC 原生菜单规则。打开方式只有一条正路：服务端 `ApricityUI.menu(player, path).bind(...)`。细节：[容器文档](container)。

## 资源从哪来

页面和资源（CSS、图片、字体、数据 JSON）用**逻辑路径**引用，如 `screens/home.html`。资源有三层来源：模组 jar 内置、资源包、本地 `apricity/` 目录，上层覆盖下层；远程资源走受限 HTTPS 管线。资源管理器与资源重载操作默认未绑定，需在 MC 控制设置中自行绑定。资源管理器可浏览、预览、新建、改 meta、查引用。规则：[资源管理](resource-manager)。

## 怎么打开页面

**Java**：统一入口 `com.sighs.apricityui.ApricityUI`——`createDocument`、`new ApricityScreen(path)`、`menu(player, path).bind(...)`、`createWorldWindow(...)`。

**KubeJS**：全局 `ApricityUI` 的客户端/服务端绑定仅在 Forge 1.20.1 和 NeoForge 1.21.1 提供，方法集按侧隔离（客户端管 Document/Toast/WorldWindow，服务端管容器）。模组也可在支持的环境注册自己的 KJS 绑定。

完整 API 表和线程/空值/刷新规则：[模组专属 API](apricity-api)。

## 调试和工具

**游戏内 DevTools**：DOM 树、元素拾取、Attributes/Styles/盒模型检视、运行时改样式改结构、存回源文件、meta 编辑、受限控制台。其按键操作默认未绑定，需在控制设置中绑定。见 [DevTools](devtools)。

**外部调试协议**：游戏内开 `remoteDebug` 后，本机 WebSocket（`127.0.0.1:25321`）可以查 DOM、读样式、模拟点击输入。仓库自带 Node 客户端和 MCP 桥，AI 工具可以直连运行中的页面。另有两个截图脚本做视觉回归。见[附加工具](tools)。

**帧耗时 HUD**：`debug.frameTimingHud` 显示 AUI 渲染耗时和批次统计，定位性能问题用。见[二次开发](secondary-development)。

**WPT 布局对比**：把 Web Platform Tests 的 CSS 布局页面在 Chromium 和 AUI 里各采一遍几何快照做 diff，用来验证布局引擎的浏览器一致性。见 [WPT](wpt)。

## 给模组作者的扩展点

- 注册自己的 HTML 标签：`@ElementRegister` + 扫描包，自定义绘制或纯语义元素都行；
- 注册自己的 KubeJS 全局对象：`@KJSBindings`；
- 内置 Java 组件库直接复用：DialogWindow、ContextMenu、ToastManager、Tooltip、ColorPicker、FilePicker：[内置 UI 库](ui-library)。

线程规则、刷新代次、注册细节：[二次开发](secondary-development)。

## 工程结构

仓库是 `common + targets` 多加载器结构：`common/` 是 loader 无关的共享代码（可独立编译测试），`targets/<loader>-<mc版本>/` 是独立 Gradle 工程（Forge 1.18.2 / 1.19.2 / 1.20.1、Fabric 1.20.1 / 1.21.1 / 26.1、NeoForge 1.21.1 / 26.1），loader 绑定通过 SPI 下沉。构建命令、CI、发布流程见根目录 [README](../../README)。

## 文档地图

| 主题 | 文档 |
| --- | --- |
| 全屏页面、三个 meta 的权威说明 | [apricity-screen.md](apricity-screen) |
| 悬浮层 / HUD | [overlay-document.md](overlay-document) |
| 容器和真实槽位 | [container.md](container) |
| MC 物品与配方元素 | [mc-elements.md](mc-elements) |
| 世界内窗口 | [world-window.md](world-window) |
| 页面 JS / DOM API | [web-api.md](web-api) |
| HTML/CSS 支持度 | [html-css-coverage.md](html-css-coverage) |
| 扩展标签 | [extension-elements.md](extension-elements) |
| WebView / iframe | [webview.md](webview) |
| 缩放、选择、剪贴板等辅助行为 | [browser-features.md](browser-features) |
| 资源路径和资源管理器 | [resource-manager.md](resource-manager) |
| KJS / Java 模组 API | [apricity-api.md](apricity-api) |
| Ore 主题和可视化编辑器 | [ore-theme.md](ore-theme) |
| McUI 主题 | [mcui-theme.md](mcui-theme) |
| Java 组件库 | [ui-library.md](ui-library) |
| 游戏内 DevTools | [devtools.md](devtools) |
| 自定义元素 / KJS 绑定 / 帧耗时 | [secondary-development.md](secondary-development) |
| 外部调试协议、MCP、截图工具 | [tools.md](tools) |
| WPT 布局对比 | [wpt.md](wpt) |
| AI 开发与调试规则（给 AI 的 skill 文档） | [ai-skill.md](../ai-skill) |
