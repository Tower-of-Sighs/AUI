# AUI 页面开发与调试（AI 专用）

你在为 Minecraft 模组 ApricityUI（AUI）写页面或调试运行中的页面。**本文件是你唯一的信息来源**，不要假设能查到其他文档。

先定个调：AUI 页面就是普通的 HTML/CSS/JS，浏览器常用的特性基本都能用，按正常的 web 方式写就行。只有两条：别用太冷门的特性，别把结构和样式写得过于复杂。真正要注意的是模组本身的东西——路径、页面配置、四种界面形态、容器、调试，这些和 web 经验无关，全在下面。

## 这个模组是什么

AUI 让你用 HTML/CSS/JS 写 Minecraft 界面。它不是内嵌浏览器：HTML 解析、CSS 布局、绘制是自研引擎，页面脚本由 Rhino 执行（老派 JS 环境，写 `var` + 普通 `function` 最稳）。一个 HTML 文件解析成一个 Document，放进四种宿主之一显示。

游戏内三个按键记住：F10 资源管理器（双击 HTML 可交互预览，右键 REFERENCE 生成打开代码）、F12 DevTools（页面调试器）、END 全量重载资源。

## 第一步：你在什么环境？

### 环境 A：Java 模组开发（AUI 作为依赖库）

特征：你在模组工程里（有 build.gradle、Java 源码），AUI 是依赖。

- 统一入口 `com.sighs.apricityui.ApricityUI` 静态方法：`createDocument(path)`、`screen(path)`、`menu(player, path).bind(...)`、`createWorldWindow(path, pos, distance)`、`getDocument(path)`、`getDocumentByUUID(uuid)`；
- **线程规则**：一切 DOM/宿主操作必须在客户端线程。异步回调（网络包、线程池）里先 `Minecraft.getInstance().execute(...)` 再碰页面，否则偶发崩溃；
- **空值约定**：`createDocument` 资源缺失返回 null，要判空；
- **刷新失效**：页面 refresh/重载会重建整个 DOM，旧 Element 引用、监听器全部失效，重新查询。

### 环境 B：整合包 / 服务器，用 KubeJS 写 UI

特征：你在实例目录或整合包仓库里，写 `kubejs/` 下的脚本。

- 全局对象 `ApricityUI` 已注入 KJS，客户端脚本和服务端脚本的方法集**不互通**：客户端管界面（createDocument/screen/createWorldWindow），服务端管容器（menu）；
- **创建≠显示**：`createDocument(path)` 建出 Overlay 立即显示；`Document.createInWorld(path)` 单独调用什么都不显示；全屏界面要 `screen(path)`；容器必须服务端 `menu(...).bind(...)`。

## 第二步：路径规则

- 一切资源用**逻辑路径**：`screens/home.html`。不写 `assets/...` 前缀，不写磁盘路径；
- 页面文件实际放在 `<游戏目录>/apricity/` 下（如 `<游戏目录>/apricity/screens/home.html`），写完按 END 让模组扫到；
- 页面内引用 CSS/图片/字体用相对路径（相对当前 HTML），`/` 开头表示逻辑资源根；
- 唯一例外：`<texture>` 的 src 是 MC ResourceLocation（`minecraft:textures/item/diamond.png`），不是逻辑路径。

## 第三步：页面 meta（每个页面必须配）

放在 `<head>` 里，只在页面创建和 refresh 时读取：

```html
<meta name="aui-font-mode" content="web">
<meta name="aui-viewport" content="mode=browser">
<meta name="aui-mouse-events" content="intercept">
```

- **aui-font-mode**：`mc`（原版字体）、`web`（网页字体规则，大多数页面选它）、`web-scaled`（web + 跟随缩放）；
- **aui-viewport**：`mode=browser` 跟随窗口（Screen 首选）；`mode=fixed,width=N,height=N` 固定逻辑尺寸（**WorldWindow 必须用它**，否则默认宽度上千像素，面板在世界里巨大无比）；`mode=gui` 跟随 MC GUI 缩放（兼容旧页面）。缩放参数 `zoom/min-zoom/max-zoom/zoom-step/user-scalable` 可选；
- **aui-mouse-events**：写 `intercept` 页面才拦截鼠标。**页面有任何可交互元素就必须写**，否则点击落到游戏而不是页面；纯展示 Overlay 不写（让它穿透）。

## 第四步：选宿主

同一份 HTML 四种宿主通用，区别在出现在哪、谁来开：

| 你要做什么 | 宿主 | 打开方式 | 要点 |
| --- | --- | --- | --- |
| 全屏界面：设置页、菜单 | Screen | KJS `ApricityUI.screen("screens/x.html")`；Java `Minecraft.getInstance().setScreen(new ApricityScreen(path))` | meta 按上面配 |
| HUD、常驻状态、通知 | Overlay | KJS/Java `ApricityUI.createDocument("overlays/x.html")` | 创建即显示；纯展示别配 intercept |
| 背包、机器——操作**真实物品** | 容器 Screen | **只能**服务端 `ApricityUI.menu(player, path).bind(...)` | 见第六步 |
| 世界里的显示屏、头顶标签 | WorldWindow | `ApricityUI.createWorldWindow("world/x.html", pos, 32)`（Java 第二参数是 Vec3） | 页面必须 `mode=fixed` 视口；创建即显示 |

WorldWindow 补充：`setRotation(Vec3)` 参数顺序是 `(pitch, yaw, roll)`（容易搞反）；`setFacing(true)` 面向玩家；`setFollow(true)` + `setFollowFactor(0.3)` 跟随视线（头顶标签用法）；构造参数的距离是交互射线距离，`setMaxDisplayDistance` 才是显示距离，别搞混。

**从游戏代码操作已打开的页面**：`ApricityUI.getDocument(path)` 返回**列表**（同路径可开多个实例）：

```javascript
var docs = ApricityUI.getDocument("screens/hello.html");
if (docs.length > 0) {
    docs[0].getElementById("status").textContent = "HP: 20";
}
```

要精确管某个实例：保存 `createDocument` 的返回对象，或 `getDocumentByUUID(uuid)`。

## 第五步：写页面

按正常 web 方式写，注意这几个模组特有的点：

**没有浏览器默认样式**：`h1` 和 `div` 长得一样，`button` 没有按钮外观。要么全显式写样式，要么引 Ore 主题（推荐，见下）。

**初始化挂 DOMContentLoaded**：refresh 会重建页面，旧引用全部失效。初始化代码包进函数挂上去，每次重建重新执行：

```javascript
function init() {
    var btn = document.getElementById("ok");
    if (!btn) return;
    btn.addEventListener("click", function () { /* ... */ });
}
document.addEventListener("DOMContentLoaded", init);
```

**JS 环境的几个差异**：键盘修饰键是 `controlKey` 不是 `ctrlKey`；事件坐标已经是页面逻辑坐标，**不要乘**任何缩放系数；`fetch(url)` 只支持单参数 GET，`response.json()` 在 then 里同步调用；没有 WebGL/XHR/WebSocket/完整 Promise。DOM 查询修改、事件捕获冒泡、localStorage、Canvas 2D、定时器、ResizeObserver/MutationObserver 这些都有。

**扩展元素**（标准标签之外的 MC 向标签，都是普通 DOM 元素，都必须给 CSS 宽高）：

| 需求 | 写法 | 要点 |
| --- | --- | --- |
| 游戏纹理 | `<texture src="minecraft:textures/item/diamond.png">` | src 是 ResourceLocation；`blur="true"` 模糊 |
| 图集帧动画 | `<sprite src="images/coin.png" steps="8" direction="right" duration="640ms" loop="infinite">` | src 是逻辑路径；direction 是图集排布方向 |
| 本地化文本 | `<translation>item.minecraft.diamond</translation>` | 跟随游戏语言；无参数插值 |
| 矢量图标 | `<svg viewBox="0 0 24 24"><path d="..." fill="currentColor"></path></svg>` | 支持基本形状和 path；无渐变/defs/transform |
| 脚本绘制 | `<canvas>` | 2D context，API 接近浏览器 |

**Ore 主题**：内置 MC 风格 CSS 主题（像素边框、深色表面、绿紫金强调色），引一行就有成套组件样式，**别从零写样式**：

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
```

然后套类名：`.button button-primary/-secondary/-tertiary/-danger`、`.card` + `.card-header/-body`、`.form-group/.form-label/.form-input`、`.table`（固定四列，列数不同覆写 `tr` 的 `grid-template-columns`）、`.badge`、`.alert`、`.progress` > `.progress-bar`、`.container`、`.stack`/`.cluster`、`.text-center/.text-muted`、`.mt-1..4` 等。Ore 只有样式没有行为——tab 切换、modal 开关自己写 JS。全部组件的演示在游戏内 F10 双击 `apricityui/theme/ore/example.html`。

## 第六步：容器页面（真实物品）

HTML 声明结构，服务端绑定数据源，两边缺一不可：

```html
<container id="saved_data" bind="saved_data" primary="true" size="9"></container>
<container id="player" bind="player" layout="preset:player"></container>
```

**固定容器 id**（最容易踩的坑）：

| 服务端绑定方法 | HTML 必须用的 id |
| --- | --- |
| `player()` | `player` |
| `saveddata(name, cap)` | `saved_data` |
| `blockEntity(pos)` | `block_entity` |
| `entity(id)` | `entity` |

id 对不上 → 槽位全部退化成展示槽位。`saveddata("machine_data", 9)` 的第一个参数是服务端数据名，不是 HTML 的 id。

规则：

- 带 `bind` 和 `size` 的空容器自动生成槽位；`bind="player"` 自动 36 格，`layout="preset:player"` 排成原版背包样式；
- 手写 `<slot slot-index="N">` 时 N 是容器内本地索引，全部手写或全部自动生成，别混；
- UI-only 打开（`screen(path)`）的槽位全是展示型，**真实容器必须服务端 `menu(player, path).bind(b -> b.blockEntity(pos).player())` 打开**；
- shift-click 方向由服务端绑定顺序决定（第一个非玩家绑定是 primary），HTML 的 `primary="true"` 改不了；
- `<recipe type="crafting_shaped">配方ID</recipe>` 生成配方预览，纯展示不占槽位。

## 第七步：调试（重点：模组为 AI 调试准备的三个功能）

AUI 专门给"AI 在外面、游戏在跑"的场景准备了一套闭环：**改文件 → 自动重载 → 自动截图 → MCP 查 DOM/模拟操作**。三个功能都在 `config/apricityui-client.toml` 的 `[debug]` 下开：

```toml
[debug]
autoReload = true         # 监听文件变化，自动热重载
aiAutoScreenshot = true   # 每秒自动截图
remoteDebug = true        # 外部调试服务（MCP 走这里）
```

### 1. 文件热重载（autoReload）

开启后模组持续监听资源目录下的 `.html/.css/.js` 文件，保存即生效，不用人进游戏按 END。重载是精确到页面的：改 CSS 只给引用了它的页面重挂样式（`@import` 链上游也算），**DOM 和 JS 状态完整保留**——调样式不会丢页面现场；改 HTML/JS 只刷新对应的页面；新建 HTML 只注册模板、不动任何页面；改没被任何打开页面引用的文件则完全不动。这就是 AI 的开发循环：直接改 `<游戏目录>/apricity/` 下的页面文件，改动自动生效，然后截图或走 MCP 验证。

### 2. 自动截图（aiAutoScreenshot）

开启后**每秒自动截一张游戏画面**，写到 `<游戏目录>/screenshots/aui/`（只保留最新 20 张）。你直接读目录里最新的 PNG 就能看到页面实际渲染效果——布局对不对、样式生没生效、报错长什么样，不用让用户描述。想截特定状态，先用 MCP 操作页面（点开界面、填个输入框），等一两秒再读最新截图。

### 3. MCP 直连运行中的页面（remoteDebug）

`remoteDebug = true` 后，游戏在本机 `ws://127.0.0.1:25321/apricity` 起调试服务，并把 endpoint + token 写进 `run/apricity/debug.json`（**这是访问凭据，别提交别分享**）。仓库自带 MCP 桥 `tools/apricity-mcp/server.mjs`，配进 MCP 客户端（env 指向 debug.json）后你直接获得这些工具：

- `apricity_documents`：列出所有打开的 Document，拿 targetId（UUID）；
- `apricity_snapshot(targetId)`：DOM 树快照；`apricity_query` / `apricity_inspect`：查元素属性、文本、盒模型、计算样式；
- `apricity_wait_for(targetId, selector, state)`：等元素出现/可见；
- `apricity_hover` / `apricity_click` / `apricity_fill`：模拟输入。

不用 MCP 的话，仓库还有 Node 客户端 `tools/apricity-debug-client.mjs`（`connect()` 默认读 debug.json，`documents()` → `attach(targetId)` → `page.locator("#save").click()`）。

**调试纪律**：

- targetId 页面重载/重建后失效——autoReload 每次触发后 targetId 就变了，**先重新 `documents()` 再操作**，别写死；
- 协议**没有 evaluate**，不能执行任意 JS。验证逻辑的办法：改页面加 console 输出（配合热重载立刻生效），或用 inspect/query 观察 DOM 结果；
- click 要求元素可见、有尺寸、中心点没被遮挡；fill 只作用于可编辑 input/textarea；
- 页面没开就调不到——先让用户打开页面（或给出打开代码）再调试。

### 典型 AI 调试流程

1. 确认三个开关都开了；
2. 改页面文件 → autoReload 自动重载；
3. 读 `screenshots/aui/` 最新截图，看渲染结果；
4. 需要细节就 MCP：`documents()` → `snapshot`/`inspect` 查结构，`click`/`fill` 模拟操作；
5. 脚本报错看 `logs/latest.log`，搜 `[AUI JS]`/`[AUI CSS]`/`[AUI HTML]` 前缀，报错带资源路径。

人参与的调试走 F12 DevTools（DOM 树、元素拾取、改样式存回源文件），AI 优先用上面这套，不用麻烦人。

## 交付前自查

1. 三个 meta 配了？有交互的页面 `intercept` 了？WorldWindow 页面是 `mode=fixed`？
2. 路径全是逻辑路径？texture 的 src 是 ResourceLocation？
3. 初始化挂在 `DOMContentLoaded`？没写 `ctrlKey`？没给事件坐标乘缩放？
4. 样式套了 Ore 而不是从零写？
5. 容器页面：固定 id 和服务端绑定对得上？真实容器走服务端打开？
6. 实际跑过、看过渲染结果了吗（autoReload + 自动截图 + MCP，别只交付代码）？
