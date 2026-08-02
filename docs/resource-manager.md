# 资源形式、资源路径、资源管理与内置资源管理器

最后更新：2026-08-02

ApricityUI 的页面不是从任意操作系统文件路径直接读取的，而是先映射到一个逻辑资源空间，再由加载器从资源包、本地目录或开发目录中寻找实际内容。HTML、CSS、JavaScript、图片和字体都使用这套逻辑路径。

本文覆盖：

- AUI 支持的资源形式和目录结构；
- 逻辑资源路径、相对引用和资源覆盖优先级；
- 资源扫描、异步加载、缓存和 END 重载；
- 内置资源管理器的浏览、预览、新建、Meta 编辑和 Reference 生成功能。

页面实例的创建方式见 [ApricityScreen 使用文档](apricity-screen.md)、[Overlay Document 使用文档](overlay-document.md)、[Apricity 容器使用文档](container.md) 和 [WorldWindow 世界内窗口使用文档](world-window.md)。浏览器式缩放、文本复制和 Meta 的运行时行为见 [ApricityUI 浏览器辅助功能](browser-features.md)。内置 DevTools 的样式、DOM 和运行时调试功能见 [内置 DevTools 使用文档](devtools.md)。

## 1. 三种路径概念

使用资源时会同时遇到三种路径，它们的用途不同，不能混写。

| 概念 | 示例 | 用途 |
| --- | --- | --- |
| 逻辑资源路径 | screens/example.html | Document.create、HTML/CSS/JS 引用 |
| 逻辑资源根 | assets/apricityui/apricity | 资源包和开发资源目录的根 |
| 本地来源路径 | D:/work/AUI/src/main/resources/assets/apricityui/apricity | 显示来源、打开文件夹和实际写文件 |

普通页面代码只使用逻辑资源路径：

~~~java
Document document = Document.create("screens/example.html");
~~~

不要把资源根或本地工程目录写进逻辑路径：

~~~java
Document.create("assets/apricityui/apricity/screens/example.html");
Document.create("src/main/resources/assets/apricityui/apricity/screens/example.html");
~~~

逻辑路径使用 / 分隔。框架在部分本地入口兼容 Windows 反斜杠，但代码、HTML 和 CSS 仍建议统一使用 /。

## 2. 支持的资源形式

### 2.1 HTML 和 HTM

HTML 是 AUI 的页面模板资源。运行时会把它解析成 html、head 和 body，再提取样式、脚本和 Meta。

~~~text
assets/apricityui/apricity/
├── screens/example.html
├── overlays/status.html
└── world/notice.html
~~~

Document.create、ApricityScreen、容器页面和 WorldWindow 都通过逻辑路径引用 HTML。资源管理器可以预览 .html 和 .htm，但新建 HTML 对话框只接受 .html 保存路径。

### 2.2 CSS

CSS 可以写在 style 中，也可以通过 link rel="stylesheet" 引用外部文件；外部样式表还可以使用 @import。

~~~html
<link rel="stylesheet" href="../styles/common.css">
<style>
    .title {
        color: #e5e7eb;
    }
</style>
~~~

外部 CSS、@import 和 @font-face 由异步样式处理器加载，完成后在主线程重新应用样式。@import 有最大嵌套深度，循环引用会被忽略并写入日志。

### 2.3 JavaScript

JavaScript 可以内联，也可以通过 src 引用本地、开发目录或资源包中的脚本。

~~~html
<script src="../scripts/page.js"></script>
<script>
    console.log("page ready");
</script>
~~~

解析 HTML 时，框架会把 script 标签移出 DOM，之后依次执行全局 global.js、外部脚本和内联脚本。外部脚本通过 ClientLoader.getResourceStream 读取，不应假设 HTTPS JavaScript 会像 CSS 一样自动下载。

如果 script 同时存在 src 和内联代码，当前实现会对两者都执行并输出警告；页面不应依赖这个非浏览器标准行为。

### 2.4 图片

HTML 图片和 CSS url(...) 都先经过逻辑路径解析，再由异步图片处理器读取。

~~~html
<img src="../images/logo.png" alt="logo">
~~~

~~~css
.panel {
    background-image: url("../images/panel.png");
}
~~~

资源管理器缩略图和图片预览支持：

~~~text
png  jpg  jpeg  bmp  gif  webp
~~~

运行时图片解码器还包含 GIF 动画以及 CUR、ANI 相关分支，但这些扩展不一定出现在资源管理器的图片预览入口中。

### 2.5 字体

字体通过 CSS 的 @font-face 使用：

~~~css
@font-face {
    font-family: "display-font";
    src: url("../fonts/display.ttf") format("truetype");
}

body {
    font-family: "display-font", sans-serif;
}
~~~

资源管理器的字体卡片和字体预览支持 ttf、otf。外部 CSS 的 @font-face 会异步读取字体并注册到 AUI 字体系统。

### 2.6 全局资源和其他静态文件

以下两个文件如果存在，会由框架自动读取：

~~~text
global.css
global.js
~~~

global.css 会加入页面样式链，global.js 会在每个 Document 刷新时执行。资源管理器也会扫描资源根下的其他文件，但 JSON、配置文件和压缩包不会自动变成 HTML、CSS 或 JavaScript。

## 3. 资源目录结构

### 3.1 开发资源目录

源码开发时推荐使用：

~~~text
src/main/resources/assets/apricityui/apricity/
~~~

例如：

~~~text
src/main/resources/assets/apricityui/apricity/
├── global.css
├── global.js
├── screens/example.html
├── styles/common.css
├── scripts/page.js
├── images/logo.png
└── fonts/display.ttf
~~~

Loader 会从当前 Minecraft 实例附近寻找一个或多个开发资源目录。开发目录之间会按项目距离排序，较近项目的同名逻辑资源拥有更高优先级。

### 3.2 生产环境本地目录

生产环境中新建资源默认写入：

~~~text
<Minecraft实例目录>/apricity/
~~~

默认 Forge 开发运行配置通常对应：

~~~text
run/apricity/
~~~

其内部结构直接对应逻辑资源路径，例如 run/apricity/screens/example.html 对应 screens/example.html。

### 3.3 资源包目录

打包到模组或其他资源包时，资源位于：

~~~text
assets/apricityui/apricity/<逻辑资源路径>
~~~

源码文件：

~~~text
src/main/resources/assets/apricityui/apricity/screens/example.html
~~~

打包后的资源包路径：

~~~text
assets/apricityui/apricity/screens/example.html
~~~

assets/apricityui/apricity/ 是资源包格式的一部分，不是传给 Document.create 的前缀。

## 4. 资源来源和覆盖优先级

AUI 统一使用三个来源层：

| Loader.ResourceLayer | 典型来源 | 是否通常可写 |
| --- | --- | --- |
| RESOURCE_PACK | 模组 JAR、Minecraft 资源包、ResourceManager | 否 |
| LOCAL_FOLDER | <Minecraft实例目录>/apricity/ | 是，取决于文件权限 |
| DEV_FOLDER | src/main/resources/assets/apricityui/apricity/ | 是，取决于文件权限 |

构建最终静态资源清单时按以下顺序写入：

1. 资源包资源；
2. Minecraft 实例的本地 apricity 目录；
3. 开发资源目录。

同一个逻辑路径只保留一个最终条目，后加载来源覆盖前面来源。因此开发目录可以覆盖资源包中的同名文件，本地 apricity 目录也可以覆盖资源包版本。多个开发资源根会先加载较远项目，再加载较近项目。

单资源读取和资源管理器清单的入口略有不同：

- ClientLoader.getResourceStream(path) 读取单个资源时优先尝试开发文件系统、本地目录和 classpath，然后查询 Minecraft ResourceManager；
- ClientLoader.listFinalStaticResources() 为资源管理器构建合并清单，并记录最终获胜来源。

资源管理器不会为了展示覆盖关系而生成多个同路径卡片，只展示当前生效的资源。
## 5. 逻辑路径解析

核心方法是：

~~~java
String resolved = Loader.resolve(contextPath, rawPath);
~~~

### 5.1 相对、根相对和远程路径

| 上下文 | 原始引用 | 解析结果 |
| --- | --- | --- |
| screens/page.html | ../styles/page.css | styles/page.css |
| tests/page.html | ../devtools/bear.png | devtools/bear.png |
| screens/page.html | /images/logo.png | images/logo.png |
| screens/page.html | https://example.com/app.css | https://example.com/app.css |

规则如下：

- 空白会被去掉，空引用解析为空字符串；
- 以 / 开头的引用表示逻辑资源根，会去掉开头的 /；
- 其他引用相对于上下文文件所在的目录解析；
- . 和 .. 会在逻辑路径中规范化；
- 当前远程路径识别只接受 https://；
- 普通本地路径不要写盘符、工作目录或 src/main/resources 前缀。

例如 CSS 位于 styles/theme/base.css 时：

~~~css
/* 解析到 images/icon.png */
.icon {
    background-image: url("../../images/icon.png");
}
~~~

路径解析使用 CSS 文件自己的逻辑路径作为上下文，不使用引用它的 HTML 路径。图片、边框图片、背景图片、光标和字体 URL 也遵守同样的上下文规则。

### 5.2 远程 HTTPS 资源策略

远程引用只接受 `https://`。它们不属于资源包、开发目录或 `run/apricity/` 本地资源，不会进入资源管理器的静态资源清单，也没有可写的本地来源。当前适合远程加载的入口主要是：

- `<link rel="stylesheet" href="https://...">` 和 CSS `@import`；
- HTML/CSS 图片 URL，例如 `<img src="https://...">`、`url("https://...")`；
- CSS `@font-face` 的字体 URL；
- 页面 `fetch()` 或 Canvas 图片读取所经过的受限网络管线。

普通 `<script src="https://...">` 不会像浏览器一样自动下载并执行；远程 HTML 也不能直接作为 `Document.create()` 的模板。远程 `fetch()` 虽然使用同一网络入口，但仍受下面的响应类型和大小限制，不应把它当成任意 HTTP 客户端。

网络策略是固定的：

| 项目 | 限制 |
| --- | --- |
| 协议 | 只允许 HTTPS；HTTP、`file:` 和其他协议拒绝 |
| 连接/读取超时 | 分别为 3 秒、3 秒 |
| 重定向 | 最多 3 次，每个目标仍必须是 HTTPS |
| 重试 | 最多 1 次；429 等待 20 秒，5xx/超时等待 2 秒 |
| 并发 | 最多 4 个正在进行的请求 |
| 单资源大小 | 最多 8 MiB，既检查 Content-Length，也限制实际读取字节数 |
| Content-Type | 通常接受 `image/*`、`text/css` 和字体类型；其他类型可能被拒绝 |
| 同 URL 并发 | 合并为一次请求，其他调用等待同一结果 |
| 成功内存缓存 | 60 秒 |
| 磁盘缓存 | 7 天；按 URL 的 SHA-256 保存 |

磁盘缓存位于：

~~~text
<Minecraft实例目录>/apricity/.cache/network/<sha256>.bin
~~~

成功响应先查内存缓存，再查磁盘缓存，最后才发起网络请求。按 `END` 重载时会清理内存缓存、进行中的请求状态和旧的异步资源代次，但不会主动删除磁盘缓存；因此重载后短时间内仍可能看到上一份远程资源。需要验证服务器新内容时，应等待磁盘缓存过期，或手工删除对应的 `.bin` 文件后重新加载。

远程资源失败会写入 `[AUI Network]` 日志，常见原因包括协议不允许、证书/TLS 失败、超时、HTTP 状态码、重定向超限、Content-Type 不支持、响应为空和超过 8 MiB。跨域、Cookie、浏览器权限提示和 Service Worker 不属于这条管线；页面也没有浏览器式的 CORS 协商模型。

### 5.3 ResourcePath 和真正写文件的校验

资源管理器使用 ResourcePath.normalize 做显示、导航和文件名拆分。它会统一斜杠、去掉首尾斜杠，但它不是完整的路径穿越安全检查。

真正新建 HTML 时还会调用 ResourceFileWriter.validateHtmlPath，要求：

- 路径是相对路径；
- 以 .html 结尾；
- 不为空；
- 每个路径段都不是 . 或 ..；
- 目标规范化后仍位于资源根目录内。

因此不要通过 ../outside.html 绕出资源目录。资源管理器的 OPEN FOLDER 也会再次验证解析后的本地路径必须位于 sourceRoot 内。

## 6. HTML、CSS、JS、图片和字体引用示例

~~~text
src/main/resources/assets/apricityui/apricity/
├── screens/home.html
├── styles/home.css
├── scripts/home.js
├── images/logo.png
└── fonts/ui.ttf
~~~

HTML：

~~~html
<!doctype html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <link rel="stylesheet" href="../styles/home.css">
    <script src="../scripts/home.js"></script>
</head>
<body>
    <img src="../images/logo.png" alt="logo">
    <main class="home">Home</main>
</body>
</html>
~~~

CSS：

~~~css
@font-face {
    font-family: "ui-font";
    src: url("../fonts/ui.ttf") format("truetype");
}

.home {
    background-image: url("../images/logo.png");
    font-family: "ui-font", sans-serif;
}
~~~

Java：

~~~java
Document document = Document.create("screens/home.html");
~~~

如果页面通过 ApricityScreen、Overlay、容器或 WorldWindow 打开，传入的仍然是同一个逻辑路径。页面类型只改变 Document 的宿主和输入方式，不改变资源根和 URL 解析规则。

## 7. 资源扫描和加载生命周期

### 7.1 启动扫描

客户端初始化完成后，ClientLoader 会扫描 HTML 等资源，并把模板放入 HTML 的模板表。之后 Document.create(path) 只从已扫描的模板表中创建页面；模板不存在时会记录错误并返回 null。

一个 HTML Document 的刷新大致经过下面的阶段：

1. 根据当前 HTML 的 aui-viewport 和 aui-mouse-events 创建运行状态；
2. 清理旧 DOM、CSS 缓存、脚本缓存和渲染状态；
3. 提取 style、外部 stylesheet、script 和外部脚本；
4. 解析 HTML 标签、属性、文本和注释；
5. 计算初始样式并执行 Document 扩展器；
6. 重新计算最终样式、建立绘制列表并预取图片；
7. 执行全局 global.js 和页面脚本；
8. 派发 DOMContentLoaded 和 load 生命周期事件。

样式、图片和字体的网络或解码任务可能在异步线程执行。异步任务带有刷新代次，旧页面的任务在重载后不会覆盖新页面。

### 7.2 END 重载

默认 END 绑定到客户端资源重载。也可以从 Java 调用：

~~~java
ClientLoader.reload();
~~~

一次重载会延迟约两帧执行，让当前界面先完成输入和进度 Toast 的显示，然后依次执行：

1. 重载客户端 KubeJS 脚本；
2. 清空静态资源清单缓存；
3. 递增异步资源代次并清理图片、样式和网络任务；
4. 重新扫描 HTML；
5. 刷新可重载的普通 Document；
6. 刷新 WorldWindow、DevTools 和资源管理器；
7. 显示扫描和刷新耗时。

Document.setReloadPersistent(true) 的 Document 不会被普通 Document.refreshAll() 自动重建，这是用于工具文档和需要自行维护 DOM 的页面。WorldWindow 和内置工具会在重载流程中通过各自的管理器再次刷新。

重载会重建 DOM。旧的 Element 引用、旧的布局对象和依附于旧节点的事件状态不应在刷新后继续使用；需要重新从当前 Document 查询节点或在 load / 自定义初始化流程中重新绑定。

### 7.3 只重载一个模板

框架内部还提供单模板读取入口：

~~~java
boolean found = HTML.reload("screens/home.html");
~~~

它只重新读取模板，不会自动刷新所有已经创建的 Document。要让页面显示新模板，仍需对目标 Document 调用 refresh()，或使用完整的 ClientLoader.reload()。

## 8. 资源管理 API 速查

| API | 作用 |
| --- | --- |
| Loader.resolve(context, raw) | 按 HTML/CSS 文件上下文解析逻辑路径 |
| Loader.getResourceStream(path) | 从通用文件系统或 classpath 尝试读取资源 |
| ClientLoader.getResourceStream(path) | 在客户端读取资源，并补充 Minecraft ResourceManager 资源包查询 |
| HTML.scan() | 扫描 HTML 模板 |
| HTML.getTemple(path) | 获取已经扫描的 HTML 模板内容 |
| HTML.reload(path) | 重新读取单个 HTML 模板 |
| ClientLoader.listFinalStaticResources() | 获取资源管理器使用的最终合并清单 |
| Loader.getWatchRoots() | 获取开发自动重载需要监听的目录 |
| Loader.getGameDirectory() | 获取当前 Minecraft 实例目录 |

读取任意静态资源时，使用逻辑路径并关闭流：

~~~java
try (InputStream stream = ClientLoader.getResourceStream("data/example.json")) {
    if (stream == null) {
        // 资源不存在；实际项目中应记录带路径的错误
        return;
    }
    String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
}
~~~

普通业务代码不应自行拼接 assets/apricityui/apricity/，也不应直接假设资源一定来自 src/main/resources。

## 9. StaticResourceEntry 清单

资源管理器和 DevTools 的资源来源判断使用 Loader.StaticResourceEntry：

~~~java
public record StaticResourceEntry(
        String path,
        String extension,
        ResourceLayer layer,
        String sourceRoot,
        String sourceDetail,
        long sizeBytes
) {
}
~~~

字段含义：

| 字段 | 含义 |
| --- | --- |
| path | 合并后的逻辑资源路径，例如 screens/home.html |
| extension | 小写扩展名，例如 html、png |
| layer | RESOURCE_PACK、LOCAL_FOLDER 或 DEV_FOLDER |
| sourceRoot | 来源根目录；资源包通常是 resource-pack |
| sourceDetail | 来源细节，例如资源包 ID 或本地根路径 |
| sizeBytes | 本地文件字节数；资源包资源通常为 -1 |

资源包没有可靠的本地文件大小，所以资源管理器显示为 --。sourceDetail 可以复制到剪贴板，但它表示来源路径或来源标识，不表示文件内容。

## 10. 内置资源管理器

### 10.1 打开和显示模式

资源管理器自身是逻辑路径为 devtools/resource.html 的内置 HTML 工具文档。默认快捷键如下：

| 操作 | 默认按键 |
| --- | --- |
| 打开或关闭资源管理器 | F10 |
| 刷新当前资源列表 | 空白区域右键菜单中的 REFRESH |
| 完整重载资源和页面 | END |

F10 可以在 Minecraft 控制设置中重新绑定。资源管理器默认以普通 GUI 文档打开；还可以在配置中启用世界内窗口模式：

~~~text
config/apricityui-client.toml
debug.resourceManagerWorldWindow = true
~~~

也可以在内置 DevTools 的配置界面修改 debug.resourceManagerWorldWindow。启用后，只有在玩家位于世界中且没有打开普通 Minecraft Screen 时，资源管理器才会创建为 WorldWindow；条件不满足时会保留普通 GUI 模式或等待下一次打开。配置项和 WorldWindow 的投影、交互限制见 [WorldWindow 世界内窗口使用文档](world-window.md)。

### 10.2 界面区域

资源管理器由四个主要区域组成：

| 区域 | 功能 |
| --- | --- |
| 左侧资源树 | 按目录层级查看所有最终资源 |
| 顶部路径导航 | 点击 ROOT 或任意祖先目录跳转 |
| 中央文件网格 | 查看当前目录的文件夹和文件卡片 |
| 右侧详情面板 | 查看选中项目的类型、大小、层级和逻辑路径 |

资源树和当前目录内容都按名称排序，文件夹排在文件之前。单击项目会选中它并更新详情面板；双击可预览的文件会打开预览窗口。

展开资源树文件夹时只更新该分支的子节点，不会重建整个资源树。资源列表刷新或完整重载后，资源树会重新根据最终静态资源清单构建。

### 10.3 右键菜单

文件夹右键菜单：

~~~text
OPEN
NEW FILE HERE
~~~

文件右键菜单根据资源类型和来源提供：

~~~text
PREVIEW
REFERENCE
EDIT META
COPY PATH
COPY SOURCE
OPEN FOLDER
PROPERTIES
~~~

其中：

- PREVIEW 对 HTML、图片和 TTF/OTF 字体可用；
- REFERENCE 对 HTML、图片和字体可用；
- EDIT META 只对有本地文件来源的 HTML 可用；
- COPY PATH 复制逻辑资源路径，例如 images/logo.png；
- COPY SOURCE 复制来源路径或资源包来源标识，不复制文件内容；
- OPEN FOLDER 只对真实本地文件来源可用；
- PROPERTIES 保持当前项目选中状态并显示详情。

中央内容区域的空白处右键菜单为：

~~~text
NEW FILE
GO UP
REFRESH
~~~

资源包中的 devtools/resource-preview-image.html 是图片预览使用的内部资源，会被资源树过滤，不作为普通项目显示。

## 11. 资源预览

### 11.1 HTML 预览

HTML 双击预览使用一个单独的手动渲染 Document。它不会替换资源管理器，也不会把预览页面当成普通资源管理器条目继续显示。

预览页面会根据窗口内容区域进行适配缩放，并转发以下鼠标事件：

~~~text
mousedown
mouseup
mousemove
wheel
~~~

因此预览中的按钮、输入框、悬停状态和滚动可以交互。预览使用原资源路径作为 Document 上下文，所以相对 CSS、图片和字体路径会按被预览 HTML 的目录解析。

预览窗口中的运行时 DOM 修改不会自动写回源 HTML。要持久化页面结构，应使用 DevTools 的 DOM 保存功能；要修改源文件的 AUI Meta，应关闭预览后使用 EDIT META。

### 11.2 图片预览

图片资源卡片显示缩略图，双击后在预览窗口中以 object-fit: contain 方式显示。支持的扩展名为：

~~~text
png  jpg  jpeg  bmp  gif  webp
~~~

图片加载和解码是异步的。文件存在但格式损坏、字节为空、解码失败或纹理上传失败时，预览可能为空，同时应在日志中查看 [AUI Image] 条目。

### 11.3 字体预览

TTF/OTF 文件会注册一个按逻辑路径稳定生成的临时字体族名。资源卡片显示 Aa，双击后显示中英文示例文本；预览文本区域本身可编辑，但编辑内容只存在于预览窗口，不会修改字体文件。

## 12. 新建 HTML

### 12.1 打开方式

点击顶部 NEW、文件夹菜单中的 NEW FILE HERE，或空白区域菜单中的 NEW FILE，都会打开新建窗口。当前目录会被用作保存路径输入框的初始前缀。

新建窗口把“保存路径”和“HTML 内容来源”分开处理。内容来源有三种：

| 来源 | 行为 |
| --- | --- |
| LOCAL FILE | 通过文件选择器导入本地 .html 文件 |
| CLIPBOARD | 读取剪贴板中的 HTML 文本 |
| BLANK TEMPLATE | 先配置常用 Meta，再生成空白 HTML |

### 12.2 空白模板的默认 Meta

空白模板可以配置：

- charset；
- aui-font-mode；
- aui-viewport；
- aui-mouse-events；
- 其他要保留的 Meta。

默认模板会生成 HTML、head 和 body，并把设置结果写入 head，然后作为完整文本写入资源目录。

### 12.3 保存规则

开发环境默认写入：

~~~text
src/main/resources/assets/apricityui/apricity/
~~~

生产环境默认写入：

~~~text
<Minecraft实例目录>/apricity/
~~~

保存时会自动创建父目录，并使用 UTF-8 覆盖写入目标文件。当前写入器没有额外版本备份。

合法示例：

~~~text
screens/settings.html
pages/example.html
devtools/custom.html
~~~

拒绝示例：

~~~text
../outside.html
screens/../outside.html
screens/example.txt
<空路径>
~~~

创建成功后会调用 ClientLoader.reload()，让新模板进入扫描表并刷新相关页面。重载后新 Document 才能通过 Document.create("screens/settings.html") 创建。

## 13. Meta 编辑

### 13.1 可编辑范围

选择 HTML 后使用 EDIT META，资源管理器会从本地文件读取 head 中的 Meta，并只编辑 AUI 管理的字段。非 AUI Meta 会保留，body 和其他 HTML 内容也会保留。

资源包条目没有本地可写文件，因此 EDIT META 会禁用；直接打开资源包来源的 HTML 只能读取和预览。远程 URL 也不属于资源管理器的本地写入对象。

### 13.2 支持的字段

| 字段 | 可用值或示例 | 作用 |
| --- | --- | --- |
| charset | UTF-8 | 文本编码声明 |
| aui-font-mode | 空、mc、web、web-scaled | 选择字体模式 |
| aui-viewport | mode=gui、mode=browser、mode=window | 选择逻辑 viewport 模式 |
| aui-viewport | mode=fixed,width=427,height=249 | 固定逻辑尺寸 |
| aui-viewport | mode=fixed,width=1920,height=1080,scale=fit | 固定尺寸并适配窗口 |
| aui-mouse-events | 空或 intercept | 页面是否拦截鼠标事件 |

NOT SET / PASS THROUGH 表示移除对应 Meta，而不是写入特殊字符串。未知的当前值会作为 CURRENT / ... 选项保留，避免编辑器打开后意外丢失配置。

资源管理器的 Meta 编辑器负责持久化 HTML Meta；它默认不包含 DevTools 中用于当前运行实例的实时 ZOOM 字段。需要同时修改运行时缩放时，应使用 DevTools 的 Meta 面板，具体见 [内置 DevTools 使用文档](devtools.md)。

保存后会覆盖原 HTML 文件的 Meta 区域，并执行回调触发资源重载。若文件被删除、不是 .html 或无法读取，编辑器会提示错误而不会写入。

## 14. Reference 代码生成

REFERENCE 对三类资源生成常用代码，并把选中的片段复制到剪贴板。

### 14.1 图片

图片会生成 CSS 背景和 HTML img 两种形式：

~~~css
background-image: url("/images/logo.png");
~~~

~~~html
<img src="/images/logo.png" alt="logo">
~~~

这里的 / 表示 AUI 逻辑资源根，不是 Windows 磁盘根。

### 14.2 字体

字体 Reference 可以生成注册和使用两部分：

~~~css
@font-face {
    font-family: "display-font";
    src: url("/fonts/display.ttf") format("truetype");
}
~~~

~~~css
font-family: "display-font", sans-serif;
~~~

TTF 使用 truetype，OTF 使用 opentype。字体族名默认取文件名，也可以在 Reference 对话框中改成业务需要的名字。

### 14.3 HTML 页面

Java 选项会生成 Screen、容器、Overlay 和 WorldWindow 四种入口：

~~~java
ApricityUI.screen("screens/example.html");
~~~

~~~java
var overlay = ApricityUI.createDocument("screens/example.html");
~~~

~~~java
var worldWindow = ApricityUI.createWorldWindow(
        "screens/example.html",
        new Vec3(0.0, 64.0, 0.0),
        16
);
~~~

KubeJS 选项会生成对应的 KubeJS 形式，包括 ApricityUI.menu(player, ...)、createDocument 和 createWorldWindow。生成的代码是起点，不会替代容器绑定、WorldWindow 生命周期管理或业务数据绑定。

## 15. 可读、可预览和可写的边界

| 资源来源 | 运行时读取 | 资源管理器显示 | 预览 | Meta 编辑或新建覆盖 |
| --- | --- | --- | --- | --- |
| 资源包 | 是 | 是 | 视文件类型 | 否 |
| 本地 apricity | 是 | 是 | 视文件类型 | 可以，受文件权限影响 |
| 开发资源目录 | 是 | 是 | 视文件类型 | 有实际本地文件时可以 |
| HTTPS CSS/图片/字体 | 是，按对应加载器 | 不作为静态清单来源 | 不直接作为资源卡片 | 否 |
| HTTPS JavaScript | 不按外部脚本自动下载 | 不作为静态清单来源 | 不适用 | 否 |

“资源管理器看得到”只代表它存在于最终静态清单，不代表它可以写回。“预览成功”也只代表运行时创建了临时预览，不代表预览中的 DOM、文本或样式会自动保存。

## 16. 错误日志和排查入口

资源系统会按阶段使用不同日志前缀。查看 Minecraft 的 logs/latest.log 时，可以先搜索：

| 前缀 | 常见问题 |
| --- | --- |
| [AUI Resource] | 资源扫描、文件读取、资源包读取失败 |
| [AUI HTML] | 模板缺失、为空、标签或属性异常、解析流程异常 |
| [AUI CSS] | stylesheet 缺失、@import 循环、URL 异常、字体声明异常 |
| [AUI JS] | script 标签不匹配、外部脚本缺失或读取失败 |
| [AUI Image] | 图片缺失、字节为空、解码失败或纹理上传失败 |
| [AUI Font] | 字体缺失、注册失败或预览加载失败 |
| [AUI Network] | HTTPS 下载、重定向、超时、HTTP 状态或缓存失败 |
| [AUI Document] | Document 刷新阶段、DOM 构建或脚本执行失败 |

常见字段包括 path、document、resolved、source、stage 和 family。排查相对路径时应同时记录原始引用和解析结果：

~~~text
document=screens/home.html
src=../images/logo.png
resolved=images/logo.png
~~~

### 16.1 页面创建返回 null

优先检查：

1. 传入的是不是逻辑路径，而不是完整本地路径；
2. 文件是否位于正确的资源根；
3. 是否已经执行初始扫描或 ClientLoader.reload()；
4. 文件扩展名和实际文件名大小写是否一致；
5. 日志中是否存在 [AUI HTML] template resource is missing 或 empty。

### 16.2 CSS 或图片找不到

确认引用是相对于“当前 CSS 或 HTML 文件”计算的，而不是相对于项目根。需要跨目录时使用足够数量的 ../，需要从逻辑根开始时使用 /images/...。

### 16.3 改了文件但界面不变

开发文件变化不会让已经存在的 Document 自动变成新 DOM，除非开启了开发自动重载且文件位于监听根。最直接的处理方式是按 END，或调用：

~~~java
ClientLoader.reload();
~~~

如果页面设置了 setReloadPersistent(true)，它可能不会由普通 Document 全量刷新流程自动重建，需要由页面或对应工具显式刷新。

### 16.4 EDIT META 被禁用

检查当前最终条目的来源层。资源包和远程资源没有可写本地目标；只有能通过 sourceRoot + path 解析到真实本地文件的条目才会启用 Meta 编辑和 OPEN FOLDER。

### 16.5 资源管理器无法以世界窗口打开

debug.resourceManagerWorldWindow=true 只表示“允许使用世界窗口模式”，并不强制在所有时刻创建 WorldWindow。打开时还需要已进入世界、玩家存在且当前没有普通 Minecraft Screen。条件不满足时，关闭普通 Screen 后再次按 F10。

## 17. 相关实现和测试

资源加载核心：

~~~text
src/main/java/com/sighs/apricityui/instance/Loader.java
src/main/java/com/sighs/apricityui/instance/ClientLoader.java
src/main/java/com/sighs/apricityui/resource/HTML.java
src/main/java/com/sighs/apricityui/resource/CSS.java
src/main/java/com/sighs/apricityui/resource/JS.java
src/main/java/com/sighs/apricityui/resource/async/style/StyleAsyncHandler.java
src/main/java/com/sighs/apricityui/resource/async/image/ImageAsyncHandler.java
src/main/java/com/sighs/apricityui/resource/async/network/NetworkAsyncHandler.java
~~~

资源管理器及其对话框：

~~~text
src/main/java/com/sighs/apricityui/dev/ResourceManager.java
src/main/java/com/sighs/apricityui/dev/resource/ResourcePath.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceFileWriter.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceCreateDialog.java
src/main/java/com/sighs/apricityui/dev/resource/ResourcePreviewDialog.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceMetaDialog.java
src/main/java/com/sighs/apricityui/dev/resource/HtmlMetaEditor.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceReferenceDialog.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceFontAsset.java
src/main/resources/assets/apricityui/apricity/devtools/resource.html
~~~

相关测试：

~~~text
src/test/java/com/sighs/apricityui/webapi/LoaderIntegrationTest.java
src/test/java/com/sighs/apricityui/webapi/ResourcePipelineTest.java
src/test/java/com/sighs/apricityui/webapi/ResourceManagerScrollTest.java
src/test/java/com/sighs/apricityui/webapi/ResourceDialogStyleTest.java
src/test/java/com/sighs/apricityui/dev/resource/ResourceFileWriterTest.java
src/test/java/com/sighs/apricityui/dev/resource/HtmlMetaEditorTest.java
src/test/java/com/sighs/apricityui/dev/resource/ResourceReferenceDialogTest.java
~~~

这些测试分别覆盖逻辑路径解析、资源扫描入口、资源管理器滚动和预览、对话框布局、写入路径安全、Meta 保留规则以及 Reference 代码生成。
