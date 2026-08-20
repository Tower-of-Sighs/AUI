# 资源路径与内置资源管理器

AUI 的页面不直接读磁盘文件。所有 HTML、CSS、JS、图片、字体都先映射到一个**逻辑资源空间**，再由加载器从资源包、本地目录或开发目录里找实际内容。

## 逻辑路径

写代码和写页面时只用一种路径——逻辑路径：

```java
Document.create("screens/example.html");
```

```html
<link rel="stylesheet" href="../styles/common.css">
<img src="/images/logo.png">
```

不要这样写：

```text
assets/apricityui/apricity/screens/example.html      ← 资源根不是前缀
src/main/resources/...                                ← 磁盘路径更不是
D:/work/...                                           ← 想都别想
```

规则很简单：

- 统一用 `/` 分隔；
- `../styles/page.css` 这种相对路径，相对的是**当前文件所在目录**（CSS 里的 url 相对 CSS 文件自己，不是引用它的 HTML）；
- 以 `/` 开头表示逻辑资源根，如 `/images/logo.png`；
- `.` 和 `..` 会被规范化；
- 远程只认 `https://`。

## 资源放在哪

三个来源，优先级从低到高，同路径后加载的覆盖先加载的：

| 来源 | 位置 | 可写 |
| --- | --- | --- |
| 资源包 | `assets/apricityui/apricity/...`（打进模组 jar） | 否 |
| 本地目录 | `<实例目录>/apricity/...`（开发环境通常是 `run/apricity/`） | 是 |
| 开发目录 | `src/main/resources/assets/apricityui/apricity/...` | 是 |

所以：打包发布用资源包路径；不重新打包就想覆盖某个页面，往 `run/apricity/` 丢同名文件；日常开发放 src 下的开发目录。典型的开发目录结构：

```text
src/main/resources/assets/apricityui/apricity/
├── global.css            ← 存在则自动加入每个页面
├── global.js             ← 存在则每个 Document 刷新时执行
├── screens/example.html
├── styles/common.css
├── scripts/page.js
├── images/logo.png
└── fonts/display.ttf
```

## 各种资源的用法

**HTML**：页面入口，必须以 `.html` 结尾。`Document.create`、ApricityScreen、容器、WorldWindow 都用逻辑路径引用它。

**CSS**：`<style>` 内联或 `<link rel="stylesheet">` 外链，外链里还能 `@import`（有嵌套深度限制，循环引用会被忽略并记日志）。

**JS**：内联或 `<script src="../scripts/page.js">`。注意远程 `https://` 脚本**不会**被下载执行——别指望像浏览器一样。

**图片**：`<img>` 和 CSS `url(...)` 都行，支持 png / jpg / jpeg / bmp / gif / webp，GIF 动画也支持。

**字体**：CSS `@font-face` 加载 ttf / otf：

```css
@font-face {
    font-family: "display-font";
    src: url("../fonts/display.ttf") format("truetype");
}
```

## 远程 HTTPS 资源

远程引用只接受 `https://`，能走的入口：外链 CSS 和 `@import`、`<img>` 和 CSS 图片 URL、`@font-face` 字体、页面 `fetch()`。远程脚本和远程 HTML 不行。

网络策略是固定的，不用配也没法配：

| 项目 | 限制 |
| --- | --- |
| 协议 | 仅 HTTPS |
| 超时 | 连接、读取各 3 秒 |
| 重定向 / 重试 | 最多 3 次 / 1 次 |
| 并发 / 单资源大小 | 4 个 / 8 MiB |
| 缓存 | 内存 60 秒；磁盘 7 天（`apricity/.cache/network/`） |

失败原因会写进 `[AUI Network]` 日志。注意按 END 重载不清磁盘缓存，验证服务器新内容时要么等过期，要么手动删 `.bin` 文件。没有 CORS、Cookie、权限提示这些浏览器概念。

## 扫描与重载

客户端启动时扫描所有 HTML 进模板表，`Document.create(path)` 只从模板表建页面——模板不在表里就返回 null 并记日志。

**按 END（或调 `ClientLoader.reload()`）会**：重扫资源、清图片/样式/网络缓存、刷新所有普通 Document 和内置工具。开发时改了 HTML/CSS/JS 就按 END，这是标准循环。

两个进阶用法：

- `document.setReloadPersistent(true)`：让某个 Document 跳过全量刷新，适合需要自己维护状态的工具页面；
- `HTML.reload("screens/home.html")`：只重读单个模板，不刷新任何 Document，之后还要手动 `refresh()`。

刷新会重建 DOM，旧的 Element 引用全部失效，这是[生命周期](web-api#生命周期和刷新)里讲过的规则。

## 内置资源管理器

按 **F10** 打开（可在 MC 控制设置里改键）。它本身就是个 AUI 页面（`devtools/resource.html`）。

界面四块：左侧资源树、顶部路径导航、中央文件网格、右侧详情面板。只显示覆盖合并后**生效**的那份资源，不会为了展示覆盖关系摆一堆同名卡片。

**右键菜单**：

- 文件夹：OPEN、NEW FILE HERE；
- 文件：PREVIEW（HTML/图片/字体/音频）、REFERENCE（生成引用代码）、EDIT META（仅本地 HTML）、COPY PATH（逻辑路径）、COPY SOURCE（来源路径）、OPEN FOLDER、PROPERTIES；
- 空白处：NEW FILE、GO UP、REFRESH。

**预览**：双击 HTML 会开一个可交互的预览窗口（按钮能点、输入框能敲），但预览里的改动不会写回源文件——改结构用 DevTools 的保存，改 meta 用 EDIT META。图片双击放大看，字体会显示中英文示例，音频（OGG/WAV）会弹出内置播放器（`<audio controls>` 控件条：播放/暂停、拖动进度条 seek、时间显示），关窗即停。

**新建 HTML**：NEW FILE 支持三种内容来源——本地文件导入、剪贴板、空白模板（可顺带配好常用 meta）。保存路径必须是 `.html` 结尾的相对路径，`../` 绕不出去。保存后自动触发重载，新页面立刻可用。

**EDIT META**：编辑 HTML head 里的 AUI meta（`aui-viewport`、`aui-mouse-events` 和 charset），非 AUI 的 meta 和 body 原样保留。资源包里的文件没有可写来源，此项禁用。各 meta 的含义见 [ApricityScreen 文档](apricity-screen#页面-meta-配置)。

**REFERENCE**：一键生成引用代码并复制到剪贴板——图片给 CSS 背景和 `<img>` 两种写法，字体给 `@font-face` 注册 + `font-family` 使用，HTML 给 Screen / Overlay / WorldWindow / KubeJS 等各种打开方式。

**世界窗口模式**：`config/apricityui-client.toml` 里设 `debug.resourceManagerWorldWindow = true` 后，资源管理器会以 WorldWindow 形式出现在世界里（要求已进入世界且没开着别的 Screen）。

## 排查

日志按阶段分前缀，搜 `logs/latest.log`：

| 前缀 | 管什么 |
| --- | --- |
| `[AUI Resource]` | 扫描、文件读取 |
| `[AUI HTML]` | 模板缺失、解析失败 |
| `[AUI CSS]` | 样式缺失、@import 循环 |
| `[AUI JS]` | 脚本缺失或读取失败 |
| `[AUI Image]` / `[AUI Font]` | 解码、上传失败 |
| `[AUI Network]` | HTTPS 下载问题 |
| `[AUI Document]` | DOM 构建、脚本执行 |

几个高频问题：

- **`Document.create` 返回 null**：路径是不是逻辑路径？文件在不在资源根下？按没按 END？日志里有没有 `template resource is missing`？
- **CSS/图片 404**：相对路径是相对当前文件算的，跨目录用够 `../`，从根开始用 `/` 开头；
- **改了文件界面没变**：按 END。设了 `setReloadPersistent(true)` 的页面要手动刷；
- **EDIT META 是灰的**：这个条目来自资源包或远程，没有可写的本地文件。
