# 内置 DevTools

游戏内的页面调试器，能检视普通 Screen、Overlay、容器页面和命中条件下的 WorldWindow 文档：看 DOM、改样式、查日志、把修改存回源文件。外部调试协议和 Node 工具是另一条链路，见[附加工具说明](tools)。

打开/关闭：`F12` 或 `Ctrl+Shift+I`（可在 MC 控制设置里重绑）。Java 侧用 `DevTools.toggle()` / `ensureOpen()` / `selectDocument(doc)` / `selectElement(element)`。

## 目标文档

DevTools 自己也是一个 Document（`devtools/devtools.html`），它检视的是另一个**目标文档**。打开时按这个顺序自动选目标：当前 ApricityScreen → 当前容器 Screen → 最近创建的有效文档。工具栏下方的下拉框手动切换——同路径可以有多个实例，靠 UUID 前缀区分。

下拉框右侧的关闭按钮是**移除目标页面**（调 `remove()`），不是"取消检视"。想换页面看用下拉框，别点它。

## 界面速览

**工具栏**（从左到右）：拖动面板、保存、重载文档、Meta 编辑、拾取、控制台/检视切换、设置、关闭。

**DOM 树**（左侧）：点击选择元素，悬停时目标页面高亮该元素的 margin/border/padding/content 区域。右键菜单：复制 outerHTML、复制 selector、加子元素、隐藏（运行时 `display:none`）、复制、删除、属性面板。删不了根元素。

**拾取模式**：点拾取按钮后鼠标变十字，在目标页面上移动即高亮命中元素，点击选中并退出。只对当前选中的目标文档命中；WorldWindow 目标要走世界投影，准心真的命中平面才能拾取到。

**Inspector**（右侧三个标签）：

- **Attributes**：改、加、删属性，Enter 提交；
- **Styles**：inline style 编辑（可临时禁用单条声明、颜色值有取色器）、只读的计算尺寸、匹配的 CSS 规则列表（含来源文件、覆盖关系、!important）；
- **Box Model**：只读的 margin/border/padding/content 数值，改尺寸回 Styles 改。

## 运行时编辑与保存

所有编辑**立即生效但不写文件**。历史按文档保存，Ctrl+Z 撤销、Ctrl+Shift+Z / Ctrl+Y 重做（焦点在输入框时快捷键归输入框）。

点保存时才写回源文件，且只写**可写的本地资源**：资源包提供的、远程的、生产环境开发目录的资源一律拒绝，失败会给 Toast 说明原因，不会写到猜出来的路径。

保存确认窗口的"保存 DOM 树"选项决定写多少：

- 不勾选：只写回改过的 CSS 规则（可能涉及多个 CSS 文件）；
- 勾选：把当前 DOM 序列化回 HTML，元素增删、属性修改一起写入。

保存后源文件变了但当前文档**不会自动重建**——点"重载文档"或按 END 验证最终效果。注意重载会丢弃所有没保存的运行时修改，旧 Element 引用也全部失效。

## Meta 编辑器

工具栏 Meta 按钮编辑当前 HTML 的 meta（只支持可写本地源文件）。能改 charset、三个 aui-* meta 和当前运行时缩放（ZOOM 字段，走 `setViewportZoom` 立即生效）。三个 meta 的含义见 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)。其他 meta 标签原样保留。保存后触发资源重载。

## Console

顶栏按钮切换到控制台。日志来自页面脚本的 console 输出、转发的客户端日志和 DevTools 自身，支持级别过滤、关键字搜索、清空。

输入框**不是任意 JS 解释器**，是受限命令集：

| 命令 | 作用 |
| --- | --- |
| `help` | 命令帮助 |
| `clear` / `cls` | 清空 |
| `$("#save")` / `$$(".btn")` | 查第一个 / 全部匹配元素 |
| `select(12)` | 按先序编号选元素（从 1 开始） |
| `inspect` | 开关拾取模式 |
| `copy(text)` | 复制到剪贴板 |
| `dir(obj)` / `keys(obj)` / `table(arr)` | 对象概览 / 键名 / 表格 |
| `count()` / `tree` | 节点统计 / 文本 DOM 树 |
| `echo` / `warn` / `error <text>` | 写日志 |

外加简单四则运算和字面量。Enter 执行，上下方向键翻历史，Ctrl+L 清空。要调真正的脚本逻辑，看页面日志或用[外部调试工具](tools)。

## 设置

设置按钮直接编辑 `config/apricityui-client.toml`：debug 开关（autoReload、frameTimingHud、remoteDebug、资源管理器 WorldWindow 模式）、input（viewportZoomPassThrough）、worldWindow（距离、LOD、深度偏移）。各键含义见[模组 API 文档的配置表](apricity-api#客户端配置键)。

## 一个标准调试流程

1. 打开目标页面，F12，确认下拉框选对了实例；
2. 先看 Console 有没有脚本错误；
3. 拾取或 DOM 树定位元素，Attributes / Styles / Box Model 三层排查；
4. 直接改样式验证假设，用撤销比较；
5. 确认后保存（改了结构就勾 DOM 树）；
6. 重载文档，验证源文件解析后的最终效果。

## 常见问题

**没有可选目标**：页面还 active 且有 body 吗？被 remove 过的页面要重新创建。

**改了又恢复**：运行时编辑不写文件，要保存。资源包和远程 CSS 写不了。

**重载后引用失效**：预期行为，重新查元素。

**拾取高亮对不上**：普通页面查缩放和 viewport；WorldWindow 查窗口位置、距离、遮挡和准心。事件坐标已经是文档坐标，别再乘缩放。

**控制台跑不了我的 JS**：它不是 JS 控制台，见上面命令表。
