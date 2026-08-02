# 内置 DevTools 使用文档

最后更新：2026-08-02

ApricityUI 内置了一套面向 HTML `Document` 的调试工具。它运行在游戏内，直接读取当前客户端中的 DOM、布局、CSS 规则和运行时日志，可以检视普通 Screen、Overlay、容器页面以及满足命中条件的 `WorldWindow` 文档。

本文只介绍内置 DevTools。外部调试协议、Node.js 客户端和 MCP 属于另一条调试链路，见 [附加工具使用说明](tools.md)。资源树、资源预览和文件管理器也单独维护，不在本文展开。

## 1. DevTools 的组成

DevTools 同时包含两个概念：

| 概念 | 说明 |
| --- | --- |
| 工具文档 | DevTools 自己的界面，资源路径是 `devtools/devtools.html` |
| 目标文档 | 当前被检视的业务 HTML `Document` |

打开 DevTools 时，框架会创建工具文档；目标文档仍由原来的 Screen、Overlay 或 WorldWindow 持有。工具文档不会被列为可检视目标，也会排除内部准心覆盖层等框架辅助文档。

DevTools 会在目标文档上安装 MutationObserver。目标 DOM 发生变化后，工具界面通常会在下一帧安排一次刷新，而不是每次变更都立即重建整个工具界面。

## 2. 打开和关闭

默认按键如下：

| 操作 | 默认按键 | 说明 |
| --- | --- | --- |
| 打开/关闭 DevTools | `F12` | Minecraft GUI 输入上下文中的绑定 |
| 打开/关闭 DevTools | `Ctrl+Shift+I` | 内置快捷方式，与浏览器 DevTools 习惯一致 |
| 重载页面资源 | `END` | 这是框架的页面重载快捷键，不只重载 DevTools |

按键可以在 Minecraft 的控制设置中重新绑定。快捷键处理发生在 ApricityUI 的键盘事件分发流程中；当文本输入框正在编辑时，普通文本输入优先交给输入框处理。

也可以从 Java 代码操作 DevTools：

~~~java
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

DevTools.toggle();

if (DevTools.ensureOpen()) {
    Document document = /* 当前正在运行的 Document */;
    DevTools.selectDocument(document);

    Element button = document.querySelector("#save");
    if (button != null) {
        DevTools.selectElement(button);
    }
}
~~~

关闭 DevTools 只会移除工具文档，不会关闭或删除当前目标文档。工具栏中的“关闭检视文档”是另一项操作，见第 6 节。

## 3. 工具栏

DevTools 顶部工具栏从左到右提供以下功能：

| 控件 | 功能 |
| --- | --- |
| 拖动手柄 | 水平移动 DevTools 面板。面板位置会限制在工具文档 viewport 内 |
| 保存 | 将当前可写资源中的 CSS 修改，以及可选的 DOM 树修改写回源文件 |
| 重载文档 | 重新读取 HTML 资源并重建目标文档 |
| Meta | 编辑 HTML 的 AUI `meta` 元素，并设置当前运行时 viewport 缩放 |
| 拾取 | 进入页面元素拾取模式 |
| 控制台/检视切换 | 在 DOM 检视布局和控制台之间切换 |
| 设置 | 打开 DevTools 配置窗口 |
| 关闭 DevTools | 关闭工具本身 |

右侧检视器还有一个垂直拖动手柄，可以调整检视区域高度。它只改变工具界面的布局，不会修改目标页面。

## 4. 选择目标 Document

### 4.1 自动选择

首次打开 DevTools 时，目标选择顺序是：

1. 当前 `ApricityScreen` 绑定的可调试文档。
2. 当前 `ApricityContainerScreen` 绑定的可调试文档。
3. 全局 Document 列表中最近创建且仍然有效的可调试文档。

可调试文档必须满足以下条件：

- 仍处于 active 状态；
- 有 `body`；
- 路径不是 `devtools/devtools.html`；
- 不是内部准心或鼠标覆盖层文档。

### 4.2 手动切换

工具栏下方的 Document 下拉框会列出当前可调试文档。显示文本包含资源路径和 UUID 前缀；相同路径的多个 Document 仍然是不同目标，不能只按路径区分。

切换目标后，DevTools 会：

- 断开旧目标的 MutationObserver；
- 绑定新目标；
- 重置 DOM 树的展开状态；
- 默认选中新目标的 `body`；
- 重新渲染 DOM、Inspector 和页面高亮。

### 4.3 关闭检视文档

Document 选择栏右侧的关闭按钮会直接调用目标文档的 `remove()`。这不是“取消检视”，而是从当前客户端 Document 列表中移除该页面。页面所属的 Screen 或 WorldWindow 也会因此失去对应文档。

只有在确认页面不再需要时才使用它。若只是想查看另一个页面，应使用下拉框切换目标。

## 5. DOM 树

DOM 树位于左侧面板，展示目标文档的元素层级、属性、文本节点和闭合标签。

### 5.1 展开和选择

- 点击三角按钮展开或折叠节点。
- 点击节点内容选择元素。
- 有子节点的元素在折叠状态下不会立即物化所有后代节点。
- 文本节点会显示为带引号的截断文本。
- 非 void 元素会显示独立的闭合标签行；点击闭合标签行仍然会选中对应的元素。
- 顶部节点计数会随目标 DOM 的变更更新。

DOM 树采用按需展开的方式。大型页面初次打开时通常只物化根节点和已展开层级，查看深层节点时再展开祖先，可以降低 DevTools 自身的 DOM 和布局开销。

### 5.2 节点悬停

鼠标悬停在 DOM 树行上时，目标页面对应元素会显示高亮。高亮包括：

- margin 区域；
- border 区域；
- padding 区域；
- content 区域；
- 元素标签、`id`、class 和尺寸标签。

离开 DOM 树行后，高亮会隐藏。悬停高亮只改变 DevTools 的辅助显示，不会修改目标元素的 CSS。

### 5.3 节点右键菜单

对元素右键可以执行：

| 菜单项 | 作用 |
| --- | --- |
| `COPY OUTER HTML` | 复制元素序列化后的 `outerHTML` |
| `COPY SELECTOR` | 复制框架生成的 CSS selector |
| `ADD CHILD ELEMENT` | 在当前元素下创建一个新元素 |
| `HIDE ELEMENT` | 添加运行时 `display: none` 样式 |
| `DUPLICATE ELEMENT` | 深拷贝当前元素并插入到其后方 |
| `DELETE ELEMENT` | 删除当前元素 |
| `PROPERTIES` | 保持当前元素为选中项并显示属性面板 |

新增元素的标签名必须符合 `[a-z][a-z0-9-]*`。为了避免破坏文档根结构，不能删除 `documentElement`，也不能对没有父节点的元素执行结构修改。

## 6. 页面拾取模式

点击拾取按钮后，鼠标会切换为十字光标。此时鼠标移动到目标页面上会根据当前目标文档的实际命中测试显示元素高亮；点击后会：

1. 选中命中的元素；
2. 展开该元素的祖先节点；
3. 将 DOM 树滚动到选中行附近；
4. 退出拾取模式。

拾取模式的几个边界需要注意：

- 只对当前下拉框选中的目标文档做命中测试，不会从其他重叠 Document 中挑选元素；
- 鼠标位于 DevTools 工具面板上时不会拾取页面元素；
- 点击选择会消费这次拾取操作的鼠标释放事件，避免同一次点击继续触发游戏或页面的其他输入；
- 普通屏幕目标使用 Document viewport 变换；
- `WorldWindow` 目标使用世界平面投影和准心命中结果，坐标不会直接按屏幕像素比例换算。

因此，世界内页面必须确实位于准心命中位置，并且满足窗口的显示距离、交互距离和遮挡条件，拾取才会成功。

## 7. Inspector

右侧 Inspector 有 `Attributes`、`Styles` 和 `Box Model` 三个标签。

### 7.1 Attributes

Attributes 面板显示当前元素的全部属性，并允许：

- 修改属性值；
- 按 `Enter` 或失去焦点提交修改；
- 添加属性；
- 删除属性。

面板底部还显示元素标签名、短 UUID、子元素数量等运行时信息。修改 `style` 属性时，DevTools 会同步运行时样式缓存，避免下一次布局重新应用旧的 inline style。

### 7.2 Styles

Styles 面板分为几类信息。

#### Inline styles

这里显示当前元素的 `style` 属性，可以：

- 新增 CSS 属性；
- 修改属性名和值；
- 删除属性；
- 点击开关暂时禁用或恢复某条声明；
- 对颜色值使用颜色选择器。

禁用声明不会立即从历史状态中丢失，重新打开开关可以恢复它。禁用状态属于当前 DevTools 会话，重载目标文档后会清空。

#### Computed size

当前实现显示元素的计算宽度和高度。这些值是只读布局结果，不是一个可以直接写回的 CSS 属性编辑框。

#### 匹配的 CSS 规则

下面会列出目标元素匹配到的调试 CSS 规则，包括：

- selector；
- 来源文件；
- 每条声明的值和 `!important` 状态；
- 被后续规则覆盖的声明；
- 被 DevTools 暂时禁用的声明。

规则声明同样支持修改、改名、添加、删除、禁用和颜色选择。规则修改先作用于运行时 CSS 调试缓存，点击“保存”后才会尝试写回 HTML 内联 `<style>` 或可写的外部 CSS 文件。

### 7.3 Box Model

Box Model 面板显示当前元素的：

- margin 上、右、下、左；
- border 上、右、下、左；
- padding 上、右、下、左；
- content 宽高。

显示的尺寸来自当前布局结果。修改尺寸应回到 Styles 面板编辑对应的 CSS，而不是修改 Box Model 中的只读数字。

## 8. 运行时编辑和历史

DevTools 的编辑会立即作用于当前运行中的 Document：页面会重新布局、重绘、重排并更新命中测试。它们不会自动写入源文件。

编辑历史按 Document 保存，最多保留 200 条。支持：

| 操作 | 快捷键 |
| --- | --- |
| 撤销 | `Ctrl+Z` |
| 重做 | `Ctrl+Shift+Z` 或 `Ctrl+Y` |

当焦点位于文本输入框时，快捷键会交给输入框处理，不会误撤销页面编辑。关闭 DevTools、重载目标文档或关闭目标文档时，相关运行时编辑历史会被清理。

结构编辑和样式编辑都会进入历史，包括新增、复制、删除元素，属性修改，inline style 修改，以及调试 CSS 规则修改。

## 9. 保存到资源文件

点击工具栏保存按钮后，DevTools 会先解析目标文档对应的源文件，再显示保存确认窗口。保存不是对任意路径进行写入，只允许解析到当前 Apricity 资源列表中的本地 HTML/CSS 文件。

### 9.1 可写条件

以下资源通常可以保存：

- 本地资源目录中的 HTML；
- 开发环境中允许写入的开发资源；
- 由可写资源目录提供的外部 CSS。

以下资源不能保存：

- `RESOURCE_PACK` 提供的只读资源；
- 生产环境中的开发目录资源；
- 远程 stylesheet；
- 源文件已经被删除或无法读取的资源；
- 不是 `.html` 的目标文档；
- 路径包含非法 `.`、`..` 或越界片段的资源。

保存失败时会通过 DevTools Toast 给出原因，不会将内容写到猜测出来的其他路径。

### 9.2 保存范围

保存确认窗口有一个可选的“保存 DOM 树”选项：

- 未勾选时，主要写回 DevTools 修改过的 CSS 规则；
- 勾选后，会将当前 DOM 序列化回 HTML，同时保存属性、结构和可序列化的 DOM 修改；
- 修改外部 CSS 时，可能同时写入多个可写 CSS 源文件；
- 没有可写变化时不会覆盖源文件。

因此，想把“添加元素、删除元素、修改属性或修改元素的 `style` 属性”写进 HTML，应在保存确认窗口勾选保存 DOM 树。只想持久化 stylesheet 规则时，可以不勾选。

保存成功会使静态资源缓存失效，但当前目标文档不会因此自动完整重建。需要验证源文件重新解析结果时，使用工具栏的“重载文档”按钮或 `END`。

保存确认窗口还支持“下次不再询问”。该选项只影响当前 DevTools 控制器会话，不是一个永久配置文件开关。

## 10. 重载目标文档

点击重载按钮时，DevTools 会按以下顺序处理：

1. 重新读取目标路径对应的 HTML 模板；
2. 调用目标 `Document.refresh()`；
3. 清除目标的 DOM 树展开状态、禁用样式和编辑历史；
4. 重新绑定目标 MutationObserver；
5. 选中新文档的 `body` 并刷新工具界面。

重载会重建 DOM、重新应用 CSS 并重新执行页面脚本。所有没有保存的运行时修改都会丢失，旧的 `Element` Java 引用也不应继续使用：重载完成后必须重新通过 selector 或 Document API 获取元素。

## 11. Meta 和 viewport 缩放编辑器

点击工具栏 Meta 按钮会打开当前 HTML 的 Meta 编辑器。它使用当前目标文档对应的可写本地源文件，因此资源包内置 HTML 通常不能编辑。

编辑器支持以下字段：

| 字段 | 可选值或行为 |
| --- | --- |
| `charset` | 读取和写回字符集声明 |
| `aui-font-mode` | 未设置、`mc`、`web`、`web-scaled` |
| `aui-viewport` | 未设置、GUI、browser、window、固定尺寸和 fixed/fit 预设 |
| `aui-mouse-events` | 未设置/穿透，或 `intercept` |
| `ZOOM` | DevTools 中显示的当前运行时 viewport 缩放，范围 `0.01..10` |

框架认识的 AUI Meta 会由编辑器重新生成；其他 `<meta>` 标签会保留。保存 Meta 后会写回 HTML，并触发客户端资源重载回调。`ZOOM` 字段通过 `Document.setViewportZoom` 应用当前运行时缩放，用于立即观察页面大小变化；它与 `aui-viewport` 中声明的布局模式是两个层次，不要把它当成 CSS `zoom` 属性。

常见用途示例：

~~~html
<meta name="aui-viewport" content="mode=browser">
<meta name="aui-font-mode" content="web">
<meta name="aui-mouse-events" content="intercept">
~~~

`aui-mouse-events=intercept` 只控制命中页面后的输入是否继续交给 Minecraft 原生处理，具体的页面事件行为仍由 [浏览器辅助功能文档](browser-features.md) 中的事件和 `preventDefault()` 规则决定。

## 12. Console

点击顶部的控制台按钮会把 DOM/Inspector 区域切换为控制台。再次点击会返回元素检视模式。进入控制台模式后，输入框会自动获得焦点。

### 12.1 日志

控制台可以接收：

- 页面脚本产生的 console 日志；
- ApricityUI `DevToolsLogBridge` 转发的客户端日志；
- DevTools 自身的生命周期、命令和错误信息。

每条日志包含级别、时间、来源、正文，部分错误还包含堆栈。内存中最多保留 2000 条日志；外部日志每个客户端 tick 最多导入 128 条，防止突发日志阻塞 UI。

控制台工具栏支持：

- `ALL`、`INFO`、`WARN`、`ERROR` 过滤；
- 关键字搜索；
- 换行显示开关；
- 清空日志；
- 查看各级别数量。

清空操作会清除当前日志窗口，然后写入一条 `Console cleared.` 系统日志。搜索和过滤只影响显示，不会删除内存中的其他日志。

### 12.2 输入和历史

- `Enter` 执行当前命令；
- `ArrowUp` / `ArrowDown` 浏览命令历史；
- `Ctrl+L` 清空日志；
- 点击输入提示可以直接填入常用命令。

### 12.3 支持的命令

控制台是一个受限的 DevTools 命令解释器，不会执行任意 JavaScript。支持的命令如下：

| 命令 | 作用 |
| --- | --- |
| `help` | 显示命令帮助 |
| `clear` / `cls` | 清空控制台 |
| `select(<index>)` | 按元素先序编号选择元素，编号从 1 开始 |
| `inspect` | 开关页面拾取模式 |
| `$(<css>)` | 查询第一个匹配元素 |
| `$$(<css>)` | 查询所有匹配元素 |
| `querySelectorAll(<css>)` | 查询所有匹配元素 |
| `copy(<value>)` | 将括号中的文本复制到剪贴板 |
| `dir(<object>)` | 显示对象概览 |
| `table(<array>)` | 显示表格模式占位结果 |
| `keys(<object>)` | 显示对象键名 |
| `count()` | 统计目标文档节点 |
| `tree` | 输出文本版 DOM 树 |
| `echo <text>` | 输出文本 |
| `warn <text>` | 写入警告级别日志 |
| `error <text>` | 写入错误级别日志和示例堆栈 |

此外，解释器支持简单的整数四则运算、数字、布尔值、`null`、`undefined` 和带引号字符串。例如：

~~~text
help
$("#save")
$$(".button")
select(12)
2 + 3
copy(hello)
~~~

未知表达式不会访问页面 JavaScript 变量，而会输出类似 `ReferenceError` 的错误。因此，若需要调试完整脚本执行逻辑，应查看页面日志或使用 [外部调试工具](tools.md)。

## 13. DevTools 配置

点击设置按钮可以编辑客户端 Forge 配置。配置文件通常位于：

~~~text
run/config/apricityui-client.toml
~~~

实际运行目录改变时，以当前 Minecraft 实例的 `config/apricityui-client.toml` 为准。

### 13.1 Debug 设置

| 配置项 | 说明 |
| --- | --- |
| `debug.autoReload` | 允许本地文件变化触发开发自动重载 |
| `debug.aiAutoScreenshot` | 开启 AI 辅助截图任务 |
| `debug.frameTimingHud` | 显示 AUI 帧耗时 HUD；字段、采样窗口和解读方式见 [二次开发文档](secondary-development.md#5-frametiminghud-帧耗时-hud) |
| `debug.remoteDebug` | 开启本机回环地址上的外部调试服务 |
| `debug.resourceManagerWorldWindow` | 让资源管理器在游戏世界中以 WorldWindow 打开 |

### 13.2 Input 设置

| 配置项 | 说明 |
| --- | --- |
| `input.viewportZoomPassThrough` | 允许 Ctrl+滚轮 viewport 缩放穿过不拦截鼠标的持久 Overlay |

此项只影响框架对 viewport 缩放输入的分发，不会强制页面启用缩放。页面仍需使用支持缩放的 `aui-viewport` 设置。

### 13.3 WorldWindow 设置

| 配置项 | 默认值 | 约束或说明 |
| --- | ---: | --- |
| `worldWindow.depthOffsetScale` | `0.01` | `0..1`，控制距离相关深度偏移 |
| `worldWindow.maxDisplayDistance` | `128` | 不小于 `0`，控制默认最大显示/交互距离 |
| `worldWindow.lodEnabled` | `false` | 是否启用默认距离 LOD |
| `worldWindow.fullDetailDistance` | `16` | 不小于 `0` |
| `worldWindow.reducedDetailDistance` | `48` | 不小于 `fullDetailDistance` |

保存配置后会写入 `CLIENT_SPEC`，并标记客户端配置重载。部分运行时效果在下一次客户端 tick 应用；资源管理器的显示模式也会在条件允许时重新协调。WorldWindow 的创建、投影和深度行为见 [WorldWindow 文档](world-window.md)。

## 14. 一个完整的调试流程

可以按下面的顺序处理一个页面问题：

1. 打开目标 Screen、Overlay 或 WorldWindow 页面。
2. 按 `F12` 打开 DevTools，确认 Document 下拉框选中了正确实例。
3. 先在 Console 中观察页面加载日志和脚本错误。
4. 切回检视模式，展开 DOM 树或进入拾取模式定位元素。
5. 在 Attributes 检查 `id`、class、资源路径和事件相关属性。
6. 在 Styles 检查 inline style、匹配规则、覆盖关系和禁用状态。
7. 在 Box Model 检查 margin、border、padding、content 尺寸。
8. 直接修改样式验证假设，必要时使用撤销/重做比较结果。
9. 确认要保留后点击保存；涉及结构、属性或元素 `style` 属性时勾选保存 DOM 树。
10. 点击重载文档，验证源文件重新解析后的最终效果。

调试世界内窗口时，额外确认窗口没有被方块遮挡、准心确实命中平面，并区分 `maxDisplayDistance` 与交互射线距离；DevTools 的 Inspector 高亮使用同一套世界投影。

## 15. 常见问题

### 没有可选目标

确认页面 Document 仍然 active、拥有 `body`，并且不是 DevTools 自己的资源路径。若页面刚刚被 `remove()`，需要重新创建页面。

### 选中的不是当前屏幕页面

使用 Document 下拉框手动切换。相同 HTML 路径可能有多个实例，UUID 前缀可以帮助区分。

### 修改后刷新又恢复

运行时编辑不会自动写文件。点击保存，并根据修改类型选择是否保存 DOM 树；资源包和远程 CSS 不能写回。

### 保存按钮不可用或提示只读

目标必须是当前资源列表中可解析的本地 HTML，且源文件位于允许写入的本地资源目录。生产环境的开发目录、资源包和不存在的源文件都会被拒绝。

### 重载后 Java 引用失效

这是预期行为。`Document.refresh()` 会重建节点和脚本状态，旧 `Element` 不应继续使用。重载完成后重新调用 `querySelector`、`getElementById` 或重新绑定目标。

### 拾取高亮位置不对

普通页面检查 viewport transform、页面缩放和 DevTools 是否覆盖了命中区域；WorldWindow 则检查窗口的世界位置、旋转、显示距离和准心射线。页面事件坐标已经是 Document 坐标，不应再次乘 `renderScale` 或 `devicePixelRatio`。

### 控制台不能执行任意 JS

内置 Console 只实现受限的查询、选择、复制、统计和简单值计算。完整脚本调试需要依赖页面自身日志或外部调试协议。

## 16. 相关源码和测试

核心实现：

~~~text
src/main/java/com/sighs/apricityui/dev/DevTools.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsController.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsDomTree.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsInspector.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsConsole.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsConfigDialog.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsSaveDialog.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsDocumentStore.java
src/main/java/com/sighs/apricityui/dev/devtools/DevToolsCssSerializer.java
src/main/java/com/sighs/apricityui/dev/resource/ResourceMetaDialog.java
src/main/resources/assets/apricityui/apricity/devtools/devtools.html
~~~

相关测试：

~~~text
src/test/java/com/sighs/apricityui/webapi/DevToolsTest.java
src/test/java/com/sighs/apricityui/dev/devtools/DevToolsDocumentStoreTest.java
src/test/java/com/sighs/apricityui/dev/devtools/DevToolsCssSerializerTest.java
src/test/java/com/sighs/apricityui/dev/devtools/DevToolsHtmlSerializerTest.java
src/test/java/com/sighs/apricityui/dev/devtools/DevToolsEditHistoryTest.java
src/test/java/com/sighs/apricityui/dev/devtools/DevToolsStylesheetEditingTest.java
~~~

相关文档：

- [ApricityScreen 使用文档](apricity-screen.md)
- [Overlay Document 使用文档](overlay-document.md)
- [容器使用文档](container.md)
- [浏览器辅助功能](browser-features.md)
- [WorldWindow 世界内窗口](world-window.md)
- [附加工具使用说明](tools.md)
