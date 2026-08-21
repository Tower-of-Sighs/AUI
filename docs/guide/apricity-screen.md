# ApricityScreen 使用文档

ApricityScreen 是 AUI 对 Minecraft Screen 的封装：把一个 HTML 加载成 Document，画到当前界面上，并把鼠标、键盘、滚轮输入转发给它。

## 三种页面宿主

AUI 的页面可以由不同的宿主承载，DOM API 完全一样，区别只在"页面出现在哪、谁来提供数据"：

| 宿主 | 创建方式 | 适用场景 |
| --- | --- | --- |
| ApricityScreen | 客户端直接 `new ApricityScreen(path)` | 纯 UI：设置页、调试页、客户端工具 |
| ApricityContainerScreen | `ApricityUI.screen(path)` 或 `ApricityUI.menu(...)` | 需要真实容器槽位或服务端数据 |
| WorldWindow | `ApricityUI.createWorldWindow(...)` | 渲染在世界中的窗口 |

注意：`ApricityUI.screen(path)` 走的是网络请求，最终打开的是 ApricityContainerScreen，哪怕页面里没有 container。想要真正的 ApricityScreen，只能在客户端直接 setScreen。容器和 WorldWindow 各有自己的文档，本文只讲 ApricityScreen。

## 最小示例

HTML 放在 `src/main/resources/assets/apricityui/apricity/screens/example.html`：

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body { margin: 0; color: #eee; background: #20242b; font-size: 16px; }
        .panel { width: 360px; margin: 40px auto; padding: 16px; background: #303640; }
    </style>
</head>
<body>
    <main class="panel">
        <h2>ApricityScreen</h2>
        <p id="status">Ready</p>
        <button id="reload">Click me</button>
    </main>
    <script>
        document.getElementById("reload").addEventListener("click", function () {
            document.getElementById("status").textContent = "Clicked";
        });
    </script>
</body>
</html>
```

Java 侧打开：

```java
Minecraft.getInstance().setScreen(
        new ApricityScreen("screens/example.html")
                .setPauseGame(true)
                .setShowDefaultBackground(false)
);
```

路径写的是逻辑路径 `screens/example.html`，不要把 `assets/apricityui/apricity/` 前缀写进去。逻辑路径怎么解析、资源目录怎么组织，见[资源管理文档](resource-manager)。如果调用发生在别的线程，先 `minecraft.execute(...)` 切回客户端线程。

## API

```java
public ApricityScreen(String templatePath)
public ApricityScreen setPauseGame(boolean pauseGame)           // 默认 false，是否暂停游戏
public ApricityScreen setShowDefaultBackground(boolean show)    // 默认 false，是否画 MC 原版背景
public Document getLinkedDocument()
public boolean handleViewportZoom(boolean zoomIn)
public boolean resetViewportZoom()
```

`getLinkedDocument()` 在 Screen 还没被 Minecraft 初始化、HTML 缺失或解析失败、Screen 已关闭时都会返回 null。别在构造函数里缓存它。

`handleViewportZoom` / `resetViewportZoom` 受 meta 里的 `user-scalable`、`min-zoom`、`max-zoom` 限制。Java 侧想直接设置任意缩放值，用 `document.setViewportZoom(1.25)`——它不受 `user-scalable=false` 限制，但仍被 min/max 夹在范围内。

## 生命周期

```text
new ApricityScreen(path)      // 只保存路径，不读 HTML
  -> setScreen -> init()      // 创建 Document，解析 HTML/CSS/JS
  -> DOMContentLoaded -> load
  -> render() / 输入 / resize()
  -> onClose()                // 先向 body 派发 unload，再移除 Document
```

几个容易踩的点：

- `init()` 可能被重复调用（比如 resize），每次都会重建 Document；
- `Document.refresh()` 保留 Document 本身但重建 DOM、重跑脚本。刷新后旧的 Element 引用和事件监听器全部失效，用 `document.getRefreshGeneration()` / `isCurrentGeneration(gen)` 判断代数；
- 覆盖 `init()` / `onClose()` / `removed()` 时必须调 super，否则 Document 不会创建或不会清理；
- 不要在子类里再调一次 `Document.create(同路径)`，会同时存在两个 Document，绘制和输入都翻倍。

## 页面 Meta 配置

这两个 meta 标签是 AUI 的页面级配置，**所有宿主通用**（Screen、Overlay、Container、WorldWindow 都读它们）。完整说明只在这里维护一份，其他文档不再重复。

### aui-viewport：逻辑视口

```html
<meta name="aui-viewport" content="mode=browser">
```

`content` 是逗号分隔的键值列表。`mode` 缺省为 `gui`：

| 模式 | 别名 | 行为 |
| --- | --- | --- |
| `gui` | mc、default | 用 Minecraft GUI 尺寸作逻辑视口，适合 MC 风格小界面 |
| `browser` | css、web | 用 CSS 视口宽度，按窗口宽度缩放，适合类网页的设置页 |
| `window` | native、screen、fullscreen | 用显示器推导的 CSS 宽度，窗口变化时横向布局不重算 |
| `fixed` | — | 固定设计稿尺寸：`mode=fixed,width=427,height=249,scale=fit` |

fixed 模式的 `scale` 可以是数值（`scale=1`）、`fit`（等比放进窗口，别名 `contain`）、`gui`（别名 `mc`）或 `window`（别名 `native`）。`width`/`height` 缺省 427×249。

所有模式都支持缩放参数：

```html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
```

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| `zoom` | 1 | 初始缩放，也是 Ctrl+0 的重置目标 |
| `min-zoom` / `max-zoom` | 0.5 / 3 | 用户缩放范围 |
| `zoom-step` | 0.1 | 每次缩放步进 |
| `user-scalable` | true | 是否允许快捷键缩放 |

允许缩放时：Ctrl+滚轮、Ctrl+`+`/`-` 缩放，Ctrl+`0` 重置。缩放值按页面路径存到 `config/apricityui/viewport-zoom.properties`，重开页面会记住上次的值。

### gui 模式的坐标换算

`gui` 模式下文档的 GUI 缩放上限是 5。Minecraft GUI scale ≤ 5 时，文档 CSS 坐标和 Minecraft GUI 坐标（`GuiGraphics`、Screen 鼠标事件用的坐标系）完全一致，可以混用；GUI scale ≥ 6 时文档仍按 5 布局、再放大渲染，两套坐标不再 1:1。

这时不要把 `Client.getWindowSize()`、`Client.getMousePositionDirectly()` 这类原始值直接当文档坐标用，用 `Document` 上的换算 API：

```java
Size viewport = document.getViewportSize();          // 文档 CSS 视口尺寸（逻辑像素）
Position doc = document.getMouseDocumentPosition();  // 当前鼠标的文档 CSS 坐标
Position doc2 = document.guiToDocumentPosition(gui); // MC GUI 坐标 → 文档 CSS 坐标
Position gui = document.documentToGuiPosition(doc);  // 反向换算
```

Overlay 里放全屏 Canvas 时，Canvas 的 `width`/`height` 用 `getViewportSize()`，鼠标位置用 `getMouseDocumentPosition()`，任何 GUI scale 下都对齐。

### aui-mouse-events：输入拦截

```html
<meta name="aui-mouse-events" content="intercept">
```

`intercept`（也接受 `block`、`true`、`yes`、`on`、`1`）让命中区域的鼠标事件不再传给下方的 Minecraft 输入或其他 Document。不设它，HTML 事件照样派发，只是不强制消费原生输入。

两点注意：拦截按命中区域生效，不可见、被裁剪或 `pointer-events: none` 的元素不会因此获得命中；想让整个页面都吃输入，就保证可交互区域盖满视口。这是个"是否消费原生输入"的开关，不是事件监听器的开关。

## 输入事件

页面里就是熟悉的写法：

```javascript
button.addEventListener("click", function (event) { ... });
input.addEventListener("input", function () { console.log(input.value); });
```

鼠标、滚轮、键盘、焦点、表单事件都有，事件坐标已经是 Document 逻辑坐标，**不要再乘 GUI scale 或页面缩放**。完整的事件类型、字段和坑见 [Web API 文档](web-api)。

Ctrl+滚轮的缩放目标是鼠标下最上层的 Document。对 overlay 页面，客户端配置 `[input] viewportZoomPassThrough = true`（`config/apricityui-client.toml`）允许缩放穿透没声明 intercept 的 overlay。

## 拿到当前 Screen 的 Document

Java：

```java
if (Minecraft.getInstance().screen instanceof ApricityScreen screen) {
    Document document = screen.getLinkedDocument();
}
```

KubeJS 客户端脚本：

```javascript
const document = ApricityUI.getCurrentScreenDocument();
```

两者都只在当前 Screen 真的是 ApricityScreen 时有值。`ApricityUI.screen(path)` 打开的是容器 Screen，会返回 null——别用路径匹配代替这个方法。

## 需要容器时

ApricityScreen 没有 Menu 和真实槽位。页面要操作玩家背包、方块实体容器或服务端数据，走容器入口：

```java
ApricityUI.menu(player, "screens/inventory.html").bind(binding -> binding.player());
```

或客户端脚本 `ApricityUI.screen("screens/inventory.html")`。详见[容器文档](container)。

## END 重载

开发时按 END 会重扫资源并 refresh 所有普通 Document：重跑脚本、重建 DOM。所以 JS 顶层变量、动态加的节点、输入框的值都不会保留——需要留的数据放到 Java/KubeJS 侧，在 `load` 里写回页面。`document.setReloadPersistent(true)` 可以让独立 Overlay 跳过重载，但 Screen 绑定的 Document 别这么干，容易让页面代码和资源版本对不上。

## 常见问题

**页面空白**：按顺序查——路径是不是逻辑路径；文件在不在 `assets/apricityui/apricity/` 或 `run/apricity/` 下；扩展名是不是 `.html`；改完有没有按 END；日志里搜 `[AUI Resource]` / `[AUI HTML]` / `[AUI Document]`。

**鼠标事件没触发**：先看元素是不是真的在鼠标下面、有没有被 `display:none` / 裁剪 / `pointer-events` 排除，再看页面是不是被更上层的 Document 盖住了。事件坐标不要再手动乘 renderScale。

**Ctrl+滚轮缩放了错的页面**：检查鼠标下是不是有 overlay、`viewportZoomPassThrough` 配置、以及页面自己的 `user-scalable` 设置。

**END 之后状态没了**：预期行为，见上一节。

**窗口变化后布局/文字偏移**：选定一个 viewport 模式让框架自己处理 resize，不要在 CSS 和 Java 里同时手动补偿缩放。

## 性能建议

- 一个 Screen 只创建一次 Document，更新时改已有元素，别每帧 `Document.create()`；
- 动画用 CSS transition/animation，别每帧重建 body；
- 长列表复用节点；
- END 是开发用的重载键，不是运行时状态同步机制。
