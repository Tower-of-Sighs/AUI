# 快速上手

从零到写出第一个真正能用的游戏界面。

先说一句：这个模组的页面就是普通 HTML/CSS/JS，而且自带给 AI 的说明书和调试支持——**如果你本来就打算让 AI 来写页面，第 1 节装好模组后直接跳第 8 节**，不用读中间这些。第 2~7 节是给想自己弄懂的人的：确认模组工作、写一个 HTML 页面、把它变好看、在游戏里打开它、用代码操作它。

## 1. 安装

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/apricityui
- Modrinth: https://modrinth.com/mod/apricityui

官方Maven：
```groovy
repositories {
    maven {
        url "https://maven.sighs.cc/repository/maven-public/"
    }
}
dependencies {
    implementation 'com.sighs:ApricityUI-forge-1.20.1:1.2.0'
}
```

## 2. 确认它在工作

进游戏按 **F10**，打开内置资源管理器。

这个资源管理器本身就是一个 ApricityUI 页面——它能渲染、能点，说明模组已经跑起来了。顺手记住它会反复用到的三个功能：

- **双击 HTML 文件**：可交互预览，和真实打开效果一致；
- **空白处右键 → NEW FILE**：新建页面，模板会配好常用设置；
- **选中文件右键 → REFERENCE**：生成"怎么打开这个页面"的代码，直接复制走。

三个常驻按键：**F10** 资源管理器，**F12** DevTools（页面调试器），**END** 重载全部资源。

## 3. 第一个页面

两种建法：F10 里 NEW FILE 用模板建；或者手动在 `<游戏目录>/apricity/screens/` 下建 `hello.html`（开发环境是 `run/apricity/`），回游戏按 END 让模组扫到它。

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body { margin: 0; color: #eee; background: #20242b; font-size: 16px; }
        .panel { width: 360px; margin: 60px auto; padding: 16px; background: #303640; }
    </style>
</head>
<body>
    <main class="panel">
        <h2>你好，ApricityUI</h2>
        <p id="status">等待点击</p>
        <button id="btn" type="button">点我</button>
    </main>
    <script>
        document.getElementById("btn").addEventListener("click", function () {
            document.getElementById("status").textContent = "被点击了";
        });
    </script>
</body>
</html>
```

就是一段普通网页。只有三件事需要解释：

**两个 meta 是页面配置**：`aui-viewport` 给页面一个浏览器式逻辑视口；`aui-mouse-events=intercept` 让页面拦截鼠标——不加这行，点击可能落不到页面上。完整解释在 [ApricityScreen 的 meta 章节](guide/apricity-screen#页面-meta-配置)，现在知道各管一件事就够。

**没有浏览器默认样式**：`h2`、`p`、`button` 不自带任何外观，字号、颜色、间距全自己写。哪些 CSS 写法能用、哪些会被忽略，见 [HTML/CSS 覆盖面](guide/html-css-coverage)。

**脚本由 Rhino 执行**：API 是浏览器的子集，写法建议 `var` + 普通 `function`。能力清单见 [Web API](guide/web-api)。

**路径**：模组按**逻辑路径**找文件，不按磁盘位置。文件在 `<游戏目录>/apricity/screens/hello.html`，代码里就写 `screens/hello.html`——不带 `assets/...` 前缀，不写盘符。页面里引 CSS、图片同理。规则见[资源管理](guide/resource-manager)。

写完按 END（或 F10 里刷新），在资源管理器里双击 `screens/hello.html` 预览：居中的面板，点按钮，文字变"被点击了"。

## 4. 用 Ore 主题变好看

刚写的页面能跑，但样式是裸的。AUI 只内置一套基于 mcui-oreui 的 Ore UI：

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
```

然后使用 mcui-oreui 的 DOM 合同，例如
`<button class="btn middle_btn primary_btn">` 和
`<section class="mc-panel">`。F10 双击
`apricityui/theme/ore/example.html` 查看完整组件与交互；路径、token、Vue 边界和许可说明见
[ore-theme.md](guide/ore-theme)。

## 5. 让页面真正打开

预览只是看效果。要页面在按键、进世界、右击方块时自己弹出来：F10 里右键页面文件 → **REFERENCE**，打开代码就生成好躺在剪贴板里了，粘到你的逻辑端代码里触发即可。

同一份 HTML 有四种宿主，REFERENCE 会把打开方式都列出来，按场景选：

| 宿主 | 场景 | 文档 |
| --- | --- | --- |
| Screen | 全屏界面：设置页、菜单 | [apricity-screen.md](guide/apricity-screen) |
| Overlay | 悬浮层：HUD、常驻状态、通知 | [overlay-document.md](guide/overlay-document) |
| 容器 Screen | 背包、机器——操作真实物品 | [container.md](guide/container) |
| WorldWindow | 世界里的显示屏、实体头顶标签 | [world-window.md](guide/world-window) |

HTML 写法四种宿主通用，差别只在页面出现在哪、数据谁来给。多个页面可以同时活着：全屏界面开着，HUD 上的悬浮层照跑，世界里还能飘着窗口，互不影响。

## 6. 从外面操作页面

页面内部的 `<script>` 操作 DOM 上面已经写过了。外部代码（KubeJS 脚本或 Java）先拿到页面，再用同一套 API：

```javascript
// KubeJS 客户端脚本
var docs = ApricityUI.getDocument("screens/hello.html");   // 注意：返回列表
if (docs.length > 0) {
    var status = docs[0].getElementById("status");
    status.textContent = "HP: 20";
    status.setAttribute("class", "warning");
}
```

```java
// Java
List<Document> docs = ApricityUI.getDocument("screens/hello.html");   // 同样是列表
if (!docs.isEmpty()) {
    Element status = docs.get(0).getElementById("status");
    status.setTextContent("HP: 20");
    status.setAttribute("class", "warning");
}
```

`getDocument(path)` 返回列表是因为**同一路径可以开多个实例**——要精确管某一个，创建时保存好 `createDocument` 返回的对象，或用 `getDocumentByUUID`。两端的 DOM 操作一致：`getElementById` / `querySelector` 查元素，`textContent`（Java 是 `setTextContent`）改文字，`setAttribute` 改属性，`addEventListener` 绑事件。

两个坑：

- **页面得先存在**。没打开过时 `getDocument` 返回空列表，先 `createDocument` 或等页面加载；
- **刷新后旧引用全部失效**。重载会重建整个页面，异步回调里改 DOM 前先确认代次——见[模组 API 的"线程、空值、刷新"](guide/apricity-api#线程空值刷新)。

完整 API：[apricity-api.md](guide/apricity-api)。

## 7. 日常改动：走 DevTools

改页面的工作流是 **F12 在游戏里边改边看**，不是改文件→END→看→再改。DevTools 基本是浏览器开发者工具的 MC 版：

- **DOM 树**：左侧逐层展开，悬停时页面上高亮这个元素的 margin/border/padding/content 区域；右键能加子元素、隐藏、删除、复制 outerHTML 和 selector；
- **拾取模式**：点了之后鼠标变十字，在页面上移动实时高亮命中元素，点一下直接定位到树上；
- **三个检视面板**：Attributes 改属性；Styles 改 inline style（单条声明可以临时禁用、颜色值带取色器），下面还有**匹配的 CSS 规则列表**——哪条生效、被谁覆盖、来自哪个文件一目了然，样式不对先看这里；Box Model 看盒模型数值；
- **改错能撤销**：Ctrl+Z / Ctrl+Shift+Z，编辑历史按文档保存；
- **满意了点保存，写回源文件**：只改了样式就只写回改过的 CSS 规则（涉及多个 CSS 文件也行）；动了结构就勾"保存 DOM 树"，把当前 DOM 整体序列化回 HTML。资源包里的只读文件会拒绝并说明原因，不会写去奇怪的地方；
- **控制台**：收页面脚本的 `console.log` 和报错，按级别过滤、按关键字搜；输入框是受限命令（`$("#save")` 查元素、`tree`、`count()` 之类），不是任意 JS 解释器；
- **Meta 编辑**：直接改当前页面的 charset、三个 aui-* meta 和运行时缩放，不用手编 HTML 头部；
- **设置**：面板里直接开关 `autoReload` 等调试配置，不用去翻 toml 文件。

想重跑当前页面用工具栏的"重载文档"按钮；**END 是改了源文件要全局生效时才用的兜底全量重载**。

不管哪种重载都会重建页面，脚本里的旧元素引用全部失效——初始化逻辑放进 `DOMContentLoaded`，每次重建重新绑定。见 [Web API 的生命周期章节](guide/web-api#生命周期和刷新)。完整功能说明见 [devtools.md](guide/devtools)。

## 8. 让 AI 帮你写

模组内置了一整套 AI 辅助开发支持，配一次，之后写页面的大部分活可以交给 AI。

**第一步：把 skill 给 AI。** [docs/ai-skill.md](ai-skill) 是给 AI 看的自包含说明书——路径规则、meta、四种宿主、容器、调试流程全在里面。三选一：贴进对话、放到 AI 能读到的目录、或直接给 GitHub 链接（`https://github.com/Tower-of-Sighs/AUI/blob/snow/docs/ai-skill.md`）。给完就不用你再转述规则了。

**第二步：打开两个开关**（`config/apricityui-client.toml`）：

```toml
[debug]
autoReload = true
aiAutoScreenshot = true
```

- `autoReload`：AI 改完文件保存，游戏内立刻生效——改 CSS 只重挂样式，连页面状态都不丢；改 HTML/JS 只刷新受影响的页面；
- `aiAutoScreenshot`：每秒自动截一张图到 `<游戏目录>/screenshots/aui/`，AI 自己读图确认渲染结果，不用你描述"长成什么样"。

**第三步：正常提需求。** 之后的循环是：你说要改什么 → AI 改 `<游戏目录>/apricity/` 下的文件 → 自动生效 → AI 看截图、翻 `logs/latest.log` 自查。有条件的话 AI 还能接 MCP 直连运行中的页面（工具在 GitHub 仓库 `tools/` 下，不随模组分发，能获取就用），查 DOM、点按钮做交互验证；接不了也不影响主流程。

## 9. 用 AI + Ore 主题做界面

让 AI 完整读取 [ore-theme.md](guide/ore-theme) 和
`apricityui/theme/ore/` 下的 `readme.md`、`source.md`、`ore.css`、
`ore-components.css`、`example.html`，不要只凭一小段 class 名猜结构。

给 AI 的说法大概是：

> 按 ai-skill.md 的规则写一个 AUI 页面：某某设置界面。使用唯一内置 Ore UI；先完整读取主题文档、CSS 和示例，并用 Rhino 兼容 JS 或 Java 实现交互。

改配色时在业务 CSS 中覆写 `.ore-theme` 下的 `--ore-*` / `--mc-*` token，并把业务 CSS 放在主题 CSS 之后；不要直接修改 jar 内置主题。

## 10. 出问题怎么查

按顺序来：

1. **F12 DevTools**：样式不对就看 Inspector 的"匹配规则"列表——哪条生效、被谁覆盖、来自哪个文件；结构不对用拾取模式点一下元素直接定位到 DOM 树；脚本报错和控制台输出都在控制台页签。功能明细见第 7 节和 [devtools.md](guide/devtools)；
2. **翻日志**：`logs/latest.log` 搜 `[AUI HTML]`、`[AUI JS]`、`[AUI CSS]` 前缀，报错带资源路径；
3. **游戏外调试**：模组能起本机调试服务，`tools/` 里带了 Node 客户端和 MCP 桥，AI 工具能直连运行中的页面查 DOM、模拟点击。开法和用法见 [tools.md](guide/tools)。

## 接下来

- 页面做得像样：[Ore 主题](guide/ore-theme) → [HTML/CSS 覆盖面](guide/html-css-coverage) → [Web API](guide/web-api)；
- 宿主进阶：[Screen](guide/apricity-screen)、[Overlay](guide/overlay-document)、[WorldWindow](guide/world-window)、[容器](guide/container)（最进阶，涉及服务端）；
- 模组侧完整 API：[apricity-api.md](guide/apricity-api)；
- 交给 AI 开发：[ai-skill.md](ai-skill)，用法见第 8、9 节；
- 全部文档的地图：[overview.md](guide/overview)。
