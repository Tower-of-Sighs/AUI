# ApricityUI 浏览器辅助功能

最后更新：2026-08-02

ApricityUI 不是完整的网页浏览器，但它为 Minecraft 内的 HTML UI 提供了一组专门的浏览器式辅助行为。它们覆盖页面缩放、文本选择、剪贴板、焦点、键盘、表单、鼠标事件、滚动、生命周期和常用 JavaScript Web API。

本文只介绍这些“浏览器辅助层”的行为和边界。页面加载、`ApricityScreen` 的创建方式见 [ApricityScreen 使用文档](apricity-screen.md)；需要处理 Overlay 层级、输入穿透和世界内窗口时，见 [以 Overlay 形式管理 Document](overlay-document.md)。

## 1. 快速示例

下面的页面同时使用了本框架提供的 viewport、字体模式、鼠标拦截、文本选择和表单键盘行为：

~~~html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport"
          content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body {
            margin: 0;
            padding: 16px;
            color: #e5e7eb;
            background: #20242b;
            font-size: 16px;
        }

        .copyable {
            user-select: text;
            padding: 8px;
            background: #303640;
        }

        .copy-all {
            user-select: all;
        }

        .scroll-box {
            width: 280px;
            height: 90px;
            overflow: auto;
            margin-top: 12px;
            padding: 8px;
            background: #15181d;
        }
    </style>
</head>
<body>
    <p class="copyable">拖拽选择这段文字，然后按 Ctrl+C 复制。</p>
    <p class="copy-all">点击这里可以选择整段文字。</p>

    <form id="profile-form">
        <input name="nickname" value="Apricity">
        <button type="submit">提交</button>
    </form>

    <div class="scroll-box">
        这是一个可以滚动的区域。鼠标滚轮优先作用于命中的可滚动元素。
        这是额外内容，用于产生滚动范围。重复内容不会改变辅助行为。
    </div>

    <script>
        document.getElementById("profile-form").addEventListener("submit", function (event) {
            event.preventDefault();
            console.log(new FormData(event.target).toString());
        });
    </script>
</body>
</html>
~~~

## 2. Ctrl + 滚轮缩放

### 2.1 快捷键

`ApricityScreen` 和 `ApricityContainerScreen` 都支持以下浏览器式缩放操作：

| 操作 | 行为 |
| --- | --- |
| `Ctrl` + 鼠标滚轮向上 | 放大一个 `zoom-step` |
| `Ctrl` + 鼠标滚轮向下 | 缩小一个 `zoom-step` |
| `Ctrl` + `=` 或 `Ctrl` + `+` | 放大 |
| `Ctrl` + `-` | 缩小 |
| `Ctrl` + `0` | 恢复到 `zoom` 指定的初始值 |

缩放值会被限制在 `min-zoom` 和 `max-zoom` 之间。缩放不是简单地把最终图片拉伸：框架会重新计算 Document 的逻辑 viewport，并同步渲染变换和命中测试。因此，布局、鼠标命中和事件中的页面坐标会保持一致。

### 2.2 viewport Meta

在 HTML 的 `<head>` 中配置：

~~~html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
~~~

`content` 是逗号或分号分隔的键值列表，键名不区分大小写。支持的缩放选项如下：

| 选项 | 默认值 | 说明 |
| --- | ---: | --- |
| `zoom` | `1` | 初始缩放值，也是 `Ctrl+0` 的恢复目标 |
| `min-zoom` | `0.5` | 用户缩放下限 |
| `max-zoom` | `3` | 用户缩放上限 |
| `zoom-step` | `0.1` | 每次放大或缩小的步长 |
| `user-scalable` | `true` | 是否允许用户快捷键改变缩放 |

`user-scalable=false` 只禁止用户快捷键和用户滚轮缩放，不会禁止 Java 或 DevTools 调用 `Document.setViewportZoom(...)`。这使得开发工具可以在页面禁止用户缩放时仍然强制设置预览比例。

### 2.3 viewport 模式

`mode` 决定逻辑 viewport 如何产生：

| 模式 | 别名 | 主要用途 |
| --- | --- | --- |
| `gui` | `mc`、`default` | 使用 Minecraft GUI 尺寸，适合原版风格界面 |
| `browser` | `css`、`web` | 使用固定 CSS 宽度并随当前 GUI 窗口缩放，适合网页式界面 |
| `window` | `native`、`screen`、`fullscreen` | 使用显示器推导的 CSS 宽度，窗口调整时保持横向布局更稳定 |
| `fixed` | 无 | 使用显式逻辑宽高，适合固定格式界面 |

固定格式页面示例：

~~~html
<meta name="aui-viewport"
      content="mode=fixed,width=427,height=249,scale=fit,zoom=1">
~~~

`fixed` 模式还支持 `scale=fit`、`scale=contain`、`scale=gui`、`scale=window` 等兼容选项。具体的 Screen 创建和 viewport 选择说明见 [ApricityScreen 使用文档](apricity-screen.md#6-viewport-配置)。

### 2.4 Java 控制缩放

Document 创建完成后，可以直接控制缩放：

~~~java
Document document = screen.getLinkedDocument();
if (document != null) {
    document.setViewportZoom(1.25d);
}
~~~

`ApricityScreen` 也提供：

~~~java
screen.handleViewportZoom(true);  // 放大
screen.handleViewportZoom(false); // 缩小
screen.resetViewportZoom();        // 恢复 meta zoom
~~~

注意：`getLinkedDocument()` 在 Screen 尚未初始化、资源解析失败或 Screen 已关闭时可能返回 `null`。不要在构造函数中缓存 Document；应在 `init()` 完成后读取，并考虑刷新或重新初始化导致的 DOM 重建。

### 2.5 缩放值持久化

用户快捷键改变的缩放值按模板路径保存到客户端配置目录：

~~~text
config/apricityui/viewport-zoom.properties
~~~

因此，同一路径的页面重新打开时可能保留上次缩放值。修改 HTML 中的 `zoom`、`min-zoom` 或 `max-zoom` 后，刷新页面会重新应用新的约束；排查“页面看起来仍然放大”时也要检查这个持久化值。

## 3. Overlay 的缩放穿透

Overlay 文档通常位于被检视页面之上。若 Overlay 自己处理了 `Ctrl+滚轮`，页面就会出现“顶层 toast 或工具栏被缩放，底下页面不缩放”的现象。

可以在客户端配置中开启 viewport 缩放穿透：

~~~toml
[input]
viewportZoomPassThrough = true
~~~

该配置位于 Minecraft 实例的 `config/apricityui-client.toml`；开发运行目录通常是 `run/config/apricityui-client.toml`。

开启后，Overlay 不能消费 viewport 缩放时，输入会继续交给下面的 Document。这个配置只解决 viewport 缩放的输入层级，不会让普通点击、拖拽或文本选择穿过一个真正拦截输入的 Overlay。Overlay 的完整层级规则见 [Overlay 文档](overlay-document.md#8-输入事件和穿透)。

## 4. Meta 元素扩展

ApricityUI 识别以下专用 Meta：

| Meta | 作用 |
| --- | --- |
| `aui-viewport` | 逻辑 viewport、渲染比例和用户缩放策略 |
| `aui-font-mode` | 默认字体模式和字体渲染比例 |
| `aui-mouse-events` | 命中页面是否拦截原生 Minecraft 鼠标输入 |

Meta 的 `name` 匹配不区分大小写；同名元素存在多个时，使用第一个匹配项的 `content`。推荐每个专用 Meta 只保留一个，避免维护工具和运行时对重复声明产生不同预期。

### 4.1 `aui-font-mode`

~~~html
<meta name="aui-font-mode" content="web">
~~~

支持的值：

| 值 | 行为 |
| --- | --- |
| `mc` | 兼容 Minecraft 风格的默认字体尺寸 |
| `web` | 使用网页式默认字体尺寸 |
| `web-scaled` | 使用网页式逻辑字号，并按 ApricityUI 字体比例进行绘制 |

默认模式是 `web-scaled`。Meta 影响默认字体、根字号和文字绘制基础模式；显式的 CSS `font-size` 仍然以 CSS 声明为准，但文字的基础绘制方式仍受该 Meta 影响。

### 4.2 `aui-mouse-events`

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

以下值表示启用拦截：`intercept`、`block`、`true`、`yes`、`on`、`1`。它的含义是：当屏幕坐标命中该 Document 的可命中元素时，阻止同一 Minecraft 鼠标事件继续落到游戏原生界面。

它不是 JavaScript 事件监听器的开关，也不会让隐藏、裁剪、`pointer-events` 禁用或不在命中区域内的元素接收事件。没有设置该 Meta 时，Document 仍然可以收到 AUI 分发的事件，但原生 Minecraft 输入可以继续向下处理。

鼠标滚轮有额外规则：未拦截的 Document 不会消费普通滚轮事件，使底层页面或游戏仍有机会处理滚动；需要完整接管页面输入时，通常使用 `content="intercept"`。

### 4.3 Meta 的应用时机

Meta 在 Document 创建和 `Document.refresh()` 时读取。运行时执行以下代码只会修改 DOM 属性本身，不会自动重新应用已经解析的 viewport、字体模式或鼠标拦截策略：

~~~javascript
var meta = document.querySelector('meta[name="aui-viewport"]');
if (meta) {
    meta.setAttribute("content", "mode=browser,zoom=1.25");
}
// 需要重新应用时刷新 Document，或由 Java/DevTools 直接调用对应 API。
~~~

HTML 使用 UTF-8 读取。`charset` 会被解析器和 Meta 编辑器保留，但它不会像真实浏览器那样参与网络编码协商。

## 5. 文字选择与复制

### 5.1 普通 HTML 文本

普通文本选择面向“没有子元素的叶子元素”。可选择元素通常需要满足：

- 元素没有子元素；
- 元素有实际文本内容；
- `user-select` 不是 `none`；
- 元素不是框架内部专用的 `AbstractText` 绘制节点。

CSS 示例：

~~~css
.copyable {
    user-select: text;
}

.copy-all {
    user-select: all;
}

.decorative {
    user-select: none;
}
~~~

对应行为：

| CSS | 行为 |
| --- | --- |
| `user-select: text` | 鼠标拖拽选择文本 |
| `user-select: all` | 点击时选择整个元素文本 |
| `user-select: none` | 禁止该元素进行文本选择 |

选择操作以元素的实际文本为基础。当前实现不提供完整浏览器式跨多个 DOM 文本节点、跨段落和任意范围的 `Selection` 对象；需要稳定复制内容时，优先把可复制内容放在一个叶子元素中。

### 5.2 快捷键

| 操作 | 普通文本 | 输入控件 |
| --- | --- | --- |
| 鼠标拖拽 | 选择叶子元素的一段文本 | 选择输入内容 |
| `Ctrl+A` | 选择当前元素全部文本 | 选择控件全部内容 |
| `Ctrl+C` | 复制选中文本 | 复制选中内容 |
| `Ctrl+X` | 不修改普通文本 | 剪切选中内容 |
| `Ctrl+V` | 不插入普通文本 | 插入剪贴板文本 |
| `Ctrl+Z` | 不执行普通文本撤销 | 撤销输入控件最近编辑 |
| `Escape` | 清除选择并清理焦点 | 清理焦点 |

点击另一个 Document 时，框架会清理其他页面的文本选择；在同一个 Document 内点击其他元素时，也会清理不属于当前目标的选择。这使多个 Overlay 同时存在时不会残留多处高亮。

### 5.3 剪贴板与事件

剪贴板由 Minecraft 的键盘处理器提供。用户快捷键会先触发可取消事件：

~~~javascript
input.addEventListener("copy", function (event) {
    console.log("copy", event.target.value);
});

input.addEventListener("paste", function (event) {
    console.log("paste");
});
~~~

如果 `copy`、`cut` 或 `paste` 事件调用了 `preventDefault()`，框架不会执行对应的默认剪贴板操作。文本编辑还会触发 `beforeinput`、`input`、`change` 以及输入法相关的 `compositionstart`、`compositionupdate`、`compositionend` 事件。

页面 JavaScript 当前没有标准浏览器式 `navigator.clipboard` API。页面内复制应使用用户快捷键；Java 侧需要直接读写时使用：

~~~java
String value = Operation.getClipboardText();
Operation.setClipboardText(value);
~~~

这两个方法依赖客户端键盘处理器，应在客户端线程和可用的 Minecraft 窗口环境中调用。

## 6. 键盘、焦点和表单辅助

### 6.1 KeyboardEvent 字段

JavaScript 收到的键盘事件提供常用浏览器字段：

~~~javascript
document.addEventListener("keydown", function (event) {
    console.log(event.key, event.code, event.keyCode, event.scanCode);
    console.log(event.repeat, event.altKey, event.shiftKey,
                event.controlKey, event.metaKey);
});
~~~

其中 `controlKey` 和 `metaKey` 按 Minecraft/GLFW 的 Ctrl、Super/Win 修饰键映射。它们不是网页浏览器平台无关性的承诺；跨平台快捷键通常同时判断业务需要的修饰键，并优先使用框架已经实现的默认行为。

### 6.2 控件默认行为

框架在事件脚本之前处理一部分控件默认行为，并在行为成功时消费原生 Minecraft 输入：

- `button`、`submit`、`reset` 和 `button` 类型 `input` 可由 Enter 或 Space 激活；
- 文本输入控件的 Enter 会提交所在 `form`；`textarea` 的 Enter 插入换行；
- `number` 输入框支持方向键和滚轮按步长增减；
- `range` 输入框支持方向键按 `step` 调整；
- `select` 支持键盘选择和弹出选择器；
- checkbox 和 radio 支持浏览器式切换；
- `file` 输入框使用 Minecraft/系统文件选择器；
- `color` 输入框使用 ApricityUI 颜色选择器。

页面脚本可以通过 `event.preventDefault()` 阻止相应的默认动作。不要在监听器中把同一个控件再次 `click()`，否则可能造成一次按键触发两次提交或切换。

### 6.3 FormData

框架提供了 JavaScript `FormData` 桥接，并会按表单成功控件规则收集 `input`、`select` 和 `textarea`：

~~~javascript
var form = document.getElementById("profile-form");
var data = new FormData(form);

console.log(data.get("nickname"));
console.log(data.entries());
console.log(data.toString());
~~~

还支持 `append`、`set`、`delete`、`get`、`has`、`getAll`、`keys`、`values`、`entries` 和 `forEach`。禁用控件不会进入序列化结果，checkbox/radio 只在选中时进入，select 按选中 option 进入。

## 7. 鼠标、滚轮和 Pointer 兼容

### 7.1 事件类型

框架分发：

~~~text
mousemove   mousedown   mouseup   click   dblclick
contextmenu wheel       mouseover  mouseout
mouseenter  mouseleave
~~~

同时提供对应的 Pointer 兼容事件：

~~~text
pointermove pointerdown pointerup pointerover pointerout
pointerenter pointerleave
~~~

`MouseEvent` 常用字段包括 `clientX`、`clientY`、`pageX`、`pageY`、`offsetX`、`offsetY`、`movementX`、`movementY`、`button`、`buttons`、`deltaX`、`deltaY`、`deltaMode`、`pointerId`、`pointerType` 和 `isPrimary`。

### 7.2 缩放后的坐标

当页面存在 viewport 缩放时，框架会先把屏幕坐标反变换为 Document 逻辑坐标，再进行命中测试和事件分发。因此脚本中的：

~~~javascript
element.addEventListener("mousemove", function (event) {
    // 这是页面逻辑坐标，不需要再次乘 renderScale。
    console.log(event.clientX, event.clientY);
});
~~~

通常不要在事件回调中再次乘 `renderScale` 或 `devicePixelRatio`。直接把 `clientX/clientY` 与元素布局坐标比较即可。这个规则对世界内窗口和 Overlay 尤其重要，因为它们可能还存在额外的屏幕到 Document 变换。

### 7.3 滚动目标

普通滚轮默认优先作用于命中元素的可滚动祖先：

- 纵向滚轮调整 `scrollTop`；
- `Shift + 滚轮` 优先调整有横向滚动范围的元素的 `scrollLeft`；
- 没有横向滚动范围时，Shift 滚轮回退到纵向滚动；
- 事件监听器调用 `preventDefault()` 后，默认滚动不会发生。

页面脚本也可以使用：

~~~javascript
window.scrollTo(0, 120);
window.scrollBy(0, 20);
element.scrollTo({left: 10, top: 30});
element.scrollBy(0, 20);
~~~

滚动会使相关 Document 标记为需要重绘，但不会改变 DOM 层级。没有实际滚动范围的元素不会因为滚轮事件被强行移动。

## 8. 生命周期与 DOM 辅助

### 8.1 readyState 和生命周期

Document 的 `readyState` 支持：

~~~text
loading -> interactive -> complete
~~~

典型顺序为：

~~~text
创建 Document
  -> 解析 HTML、CSS、JS
  -> readyState = interactive
  -> DOMContentLoaded
  -> readyState = complete
  -> load
~~~

页面脚本可以像在浏览器中一样注册：

~~~javascript
document.addEventListener("DOMContentLoaded", function () {
    console.log("DOM ready", document.readyState);
});

window.addEventListener("load", function () {
    console.log("page loaded", document.readyState);
});
~~~

关闭 Screen 时会触发页面的 `unload`，随后 Document 被移除。`Document.refresh()` 会重新解析 HTML、CSS 和 JS，重新执行页面脚本，并递增 refresh generation；不要假设刷新前保存的 Element 引用仍然属于当前 DOM。

### 8.2 全局对象和常用桥接

`src/main/resources/assets/apricityui/apricity/global.js` 为每个 Document 安装一层轻量浏览器桥接，常用对象包括：

~~~text
window              document            console
localStorage        sessionStorage      performance
fetch               requestAnimationFrame / cancelAnimationFrame
setTimeout          setInterval         Event / CustomEvent
MouseEvent          WheelEvent          PointerEvent
URLSearchParams     FormData            ResizeObserver
MutationObserver    DOMMatrix           OffscreenCanvas
~~~

常用属性和方法还包括：

- `document.readyState`、`document.activeElement`；
- `window.innerWidth`、`window.innerHeight`、`window.devicePixelRatio`；
- `element.textContent`、`element.innerText`、`element.value`；
- `element.selectionStart`、`element.selectionEnd`、`setSelectionRange`、`setRangeText`；
- `element.scrollTop`、`element.scrollLeft`、`scrollTo`、`scrollBy`；
- `element.children`、`element.options`、`element.validity`、`element.files`；
- `window.location` 的 `href`、`pathname`、`search`、`hash`、`searchParams`。

`window.location` 是由 Document 资源路径生成的虚拟对象，适合读取当前资源地址。它的 `assign()`、`replace()` 和 `reload()` 当前不会执行真实网页导航。

### 8.3 Observer

`MutationObserver` 和 `ResizeObserver` 可用于响应 DOM 或布局变化：

~~~javascript
var observer = new ResizeObserver(function (entries) {
    for (var i = 0; i < entries.length; i++) {
        console.log(entries[i].contentRect.width);
    }
});
observer.observe(document.getElementById("panel"));
~~~

`MutationObserver` 属于当前 Document 的生命周期，刷新或移除 Document 时会由框架自动断开。`ResizeObserver` 由 Window 持有，页面不再需要时应显式断开：

~~~javascript
observer.disconnect();
~~~

刷新会创建新的 DOM 节点，因此两类 Observer 都不应被当作跨刷新句柄；刷新后要重新获取元素并重新注册观察。

## 9. 与真实浏览器的差异

为了让页面能够复用常见 HTML/CSS/JS 写法，ApricityUI 提供了浏览器式接口，但它仍运行在 Minecraft 客户端和 JavaScript 引擎中。开发页面时应明确以下差异：

| 能力 | ApricityUI 行为 |
| --- | --- |
| 网络和导航 | `fetch` 通过 AUI 资源/客户端桥接工作；`location.assign/replace/reload` 不执行真实网页导航 |
| 剪贴板 | 主要由 Ctrl 快捷键和 Java 的 `Operation` 提供，没有标准 `navigator.clipboard` |
| 文本选择 | 主要支持叶子元素和输入控件，不是完整跨节点 DOM Range |
| Meta | 专用 `aui-*` Meta 在创建或刷新时读取，运行时改属性不会自动重建 viewport |
| 坐标 | 事件坐标是 Document 逻辑坐标，不应按屏幕像素再次缩放 |
| 文件和颜色 | 使用 Minecraft/系统选择器，不是浏览器地址栏或网页权限模型 |
| 生命周期 | 有 `loading`、`interactive`、`complete`，但资源加载和脚本执行仍受 AUI 解析器与客户端线程控制 |

不要把“存在同名 JavaScript API”理解为所有浏览器规范细节都已实现。需要跨环境复用代码时，优先使用本文列出的字段和方法，并在不可用能力上加显式降级。

## 10. 常见问题

### Ctrl + 滚轮缩放了 Overlay，而不是页面

检查 Overlay 是否位于前层并消费了滚轮事件，以及客户端配置中的 `viewportZoomPassThrough`。Overlay 场景还要确认页面是否在当前坐标命中范围内。详见 [Overlay 文档](overlay-document.md#8-输入事件和穿透)。

### `user-scalable=false` 后 Java 也无法缩放

用户快捷键会被禁止，但 `Document.setViewportZoom(double)` 不受该标志阻止。如果 Java 调用没有效果，检查 Document 是否已经初始化、缩放值是否为有限正数，以及是否超出 `min-zoom/max-zoom`。

### 修改 Meta 后页面仍使用旧配置

Meta 只在 Document 创建和刷新时应用。修改属性后调用 `Document.refresh()`，或直接使用对应的 Java/DevTools viewport API。刷新会重建 DOM，因此要重新获取元素引用。

### `Ctrl+C` 没有复制内容

确认文本满足 `user-select` 条件且确实有选择范围；输入控件需要先获得焦点。若监听器取消了 `copy` 或 `cut` 事件，默认剪贴板动作会被阻止。页面脚本不要依赖 `navigator.clipboard`。

### 事件中的坐标与屏幕准心不一致

事件坐标已经经过 viewport 反变换，是 Document 逻辑坐标。检查代码是否重复乘了 `renderScale`、viewport zoom 或 `devicePixelRatio`，并确认世界窗口/Overlay 没有额外的坐标变换。

### 刷新后监听器或 Observer 消失

这是预期行为。`Document.refresh()` 会重新创建页面节点并清理旧 Observer；把初始化逻辑放在脚本执行、`DOMContentLoaded` 或 `load` 回调中，并在每次刷新后重新绑定。

## 11. 相关源码和测试

功能实现主要分布在以下文件：

~~~text
src/main/java/com/sighs/apricityui/instance/ApricityViewport.java
src/main/java/com/sighs/apricityui/instance/ApricityScreen.java
src/main/java/com/sighs/apricityui/instance/ApricityContainerScreen.java
src/main/java/com/sighs/apricityui/init/Document.java
src/main/java/com/sighs/apricityui/init/Operation.java
src/main/java/com/sighs/apricityui/init/TextSelection.java
src/main/java/com/sighs/apricityui/event/KeyEvent.java
src/main/java/com/sighs/apricityui/event/MouseEvent.java
src/main/java/com/sighs/apricityui/resource/HTML.java
src/main/java/com/sighs/apricityui/instance/ApricityUIConfig.java
src/main/resources/assets/apricityui/apricity/global.js
~~~

可用于验证这些行为的测试页面包括：

~~~text
src/main/resources/assets/apricityui/apricity/tests/form-controls-test.html
src/main/resources/assets/apricityui/apricity/tests/lifecycle-event-test.html
src/main/resources/assets/apricityui/apricity/tests/client-runtime-self-test.html
src/main/resources/assets/apricityui/apricity/tests/prompt-api-test.html
~~~

修改页面后可以使用客户端的重载快捷键重新创建 Document；如果测试的是 Meta，必须确认刷新已经重新读取了 `<head>` 中的声明。
