# 快速上手

从零到写出第一个真正能用的游戏界面。读完你会：装好模组、写一个 HTML 页面、把它变好看、在游戏里打开它、用代码操作它。

## 1. 安装

- Minecraft Java 版 **1.20.1** + **Forge 47.3.0+**；
- KubeJS 可选，想用脚本控制界面才装。

jar 放进实例的 `mods` 文件夹，启动游戏。这是客户端模组。

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
    <meta name="aui-font-mode" content="web">
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

**三个 meta 是页面配置**：`aui-font-mode=web` 让字体按网页规则来；`aui-viewport` 给页面一个浏览器式逻辑视口；`aui-mouse-events=intercept` 让页面拦截鼠标——不加这行，点击可能落不到页面上。完整解释在 [ApricityScreen 的 meta 章节](apricity-screen.md#页面-meta-配置)，现在知道各管一件事就够。

**没有浏览器默认样式**：`h2`、`p`、`button` 不自带任何外观，字号、颜色、间距全自己写。哪些 CSS 写法能用、哪些会被忽略，见 [HTML/CSS 覆盖面](html-css-coverage.md)。

**脚本由 Rhino 执行**：API 是浏览器的子集，写法建议 `var` + 普通 `function`。能力清单见 [Web API](web-api.md)。

**路径**：模组按**逻辑路径**找文件，不按磁盘位置。文件在 `<游戏目录>/apricity/screens/hello.html`，代码里就写 `screens/hello.html`——不带 `assets/...` 前缀，不写盘符。页面里引 CSS、图片同理。规则见[资源管理](resource-manager.md)。

写完按 END（或 F10 里刷新），在资源管理器里双击 `screens/hello.html` 预览：居中的面板，点按钮，文字变"被点击了"。

## 4. 用 Ore 主题变好看

刚写的页面能跑，但样式是裸的。别从零写 CSS——内置的 Ore 主题是现成的 MC 风格：像素边框、深色石材表面、绿紫金强调色，按钮、卡片、表单、表格、徽章、物品栏格子全配好。引一行就能用：

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
```

然后套类名：`<button class="button button-primary">`、`<div class="card">`、`<table class="table">`。展示页 `apricityui/theme/ore/example.html`（F10 里双击打开）把全部组件演示了一遍，照着抄就行。

想改配色和间距，Ore 有可视化编辑器，在游戏里拖页面、调 token、导出 HTML，不用手改 CSS。组件清单和编辑器用法：[ore-theme.md](ore-theme.md)。

## 5. 让页面真正打开

预览只是看效果。要页面在按键、进世界、右击方块时自己弹出来：F10 里右键页面文件 → **REFERENCE**，打开代码就生成好躺在剪贴板里了，粘到你的逻辑端代码里触发即可。

同一份 HTML 有四种宿主，REFERENCE 会把打开方式都列出来，按场景选：

| 宿主 | 场景 | 文档 |
| --- | --- | --- |
| Screen | 全屏界面：设置页、菜单 | [apricity-screen.md](apricity-screen.md) |
| Overlay | 悬浮层：HUD、常驻状态、通知 | [overlay-document.md](overlay-document.md) |
| 容器 Screen | 背包、机器——操作真实物品 | [container.md](container.md) |
| WorldWindow | 世界里的显示屏、实体头顶标签 | [world-window.md](world-window.md) |

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
- **刷新后旧引用全部失效**。重载会重建整个页面，异步回调里改 DOM 前先确认代次——见[模组 API 的"线程、空值、刷新"](apricity-api.md#线程空值刷新)。

完整 API：[apricity-api.md](apricity-api.md)。

## 7. 日常改动：走 DevTools

改页面的工作流是 **F12 在游戏里边改边看**，不是改文件→END→看→再改：

- 选中元素直接改样式、改属性、删元素，立刻生效；
- 满意了点保存，写回源文件（资源包里的只读文件改不了）；
- 想重跑当前页面用 DevTools 的重载按钮；**END 是改了源文件要全局生效时才用的兜底全量重载**。

不管哪种重载都会重建页面，脚本里的旧元素引用全部失效——初始化逻辑放进 `DOMContentLoaded`，每次重建重新绑定。见 [Web API 的生命周期章节](web-api.md#生命周期和刷新)。

## 8. 出问题怎么查

按顺序来：

1. **F12 DevTools**：DOM 树逐层展开；拾取模式点一下页面元素直接定位；控制台收页面脚本的 `console.log` 和报错。见 [devtools.md](devtools.md)；
2. **翻日志**：`logs/latest.log` 搜 `[AUI HTML]`、`[AUI JS]`、`[AUI CSS]` 前缀，报错带资源路径；
3. **游戏外调试**：模组能起本机调试服务，`tools/` 里带了 Node 客户端和 MCP 桥，AI 工具能直连运行中的页面查 DOM、模拟点击。开法和用法见 [tools.md](tools.md)。

## 接下来

- 页面做得像样：[Ore 主题](ore-theme.md) → [HTML/CSS 覆盖面](html-css-coverage.md) → [Web API](web-api.md)；
- 宿主进阶：[Screen](apricity-screen.md)、[Overlay](overlay-document.md)、[WorldWindow](world-window.md)、[容器](container.md)（最进阶，涉及服务端）；
- 模组侧完整 API：[apricity-api.md](apricity-api.md)；
- 全部文档的地图：[overview.md](overview.md)。
