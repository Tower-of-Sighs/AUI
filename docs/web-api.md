# ApricityUI Web API

最后更新：2026-08-02

本文是 ApricityUI 页面脚本运行时的 Web API 参考。内容以当前源码和测试中的实际行为为准，参考了 archived/guide/web-api.md，但不沿用其中已经过时的完整标准兼容声明。

ApricityUI 不是 Chromium，也没有加载完整浏览器内核。页面 JavaScript 由 Rhino 执行，脚本执行前会注入 global.js，再把 Java 侧的 Document、Element、事件和资源管线桥接成浏览器风格对象。因此这里的 API 分为三类：

- **可直接使用**：页面中可以按下文示例调用的接口；
- **轻量兼容**：名称和常用调用方式接近浏览器，但返回值、调度时机或参数范围有所缩减；
- **未提供**：当前没有实现，不能假定存在。

CSS 属性和布局算法不在本文重复维护，详见 [HTML / CSS 浏览器标准覆盖面](html-css-coverage.md)。Ctrl + 滚轮缩放、复制、Meta 和选择行为详见 [浏览器辅助功能](browser-features.md)。

## 1. 快速开始

页面资源使用 AUI Loader 的逻辑路径。例如资源位于：

~~~text
src/main/resources/assets/apricityui/apricity/pages/example.html
~~~

创建 Document 时使用：

~~~text
pages/example.html
~~~

不要把 assets/apricityui/apricity/ 写进路径，也不要把本地磁盘路径传给页面 API。

最小页面示例：

~~~html
<!doctype html>
<html>
<head>
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-font-mode" content="web">
</head>
<body>
    <button id="load" type="button">读取数据</button>
    <pre id="output"></pre>

    <script>
        var output = document.getElementById("output");

        document.getElementById("load").addEventListener("click", function () {
            fetch("data.json")
                .then(function (response) {
                    if (!response.ok) {
                        output.textContent = "HTTP status: " + response.status;
                        return;
                    }
                    output.textContent = JSON.stringify(response.json());
                }, function (error) {
                    console.error(error);
                    output.textContent = "读取失败";
                });
        });
    </script>
</body>
</html>
~~~

页面脚本建议优先使用 var、普通 function 和传统循环。ApricityUI 会对部分常见写法做兼容转换，但 Rhino 不是完整的现代浏览器 JavaScript 引擎。

## 2. 全局对象和构造器

每个活动 Document 都会获得独立的页面 document，并共享 AUI 的 window 兼容对象。当前注入的主要全局对象如下：

| 全局 | 状态 | 说明 |
| --- | --- | --- |
| window | 可用 | AUI Window 兼容对象 |
| document | 可用 | 当前页面的 Document |
| console | 可用 | 写入 AUI 日志 |
| localStorage | 可用 | 持久化 Web Storage |
| sessionStorage | 可用 | 当前客户端运行期存储 |
| performance | 轻量兼容 | 提供 now() |
| fetch | 轻量兼容 | 资源读取和受限 HTTPS 网络读取 |
| getComputedStyle | 轻量兼容 | 读取计算样式 |
| requestAnimationFrame | 轻量兼容 | 约 16 ms 调度一次 |
| setTimeout / setInterval | 轻量兼容 | 由客户端调度器执行 |
| Event / CustomEvent | 可用 | 支持 AUI 事件分发模型 |
| MouseEvent | 可用 | 合成鼠标事件的常用字段 |
| WheelEvent | 可用 | 合成滚轮事件的常用字段 |
| PointerEvent | 可用 | 鼠标兼容的 Pointer Event |
| URLSearchParams | 轻量兼容 | 仅实现有限方法集 |
| FormData | 轻量兼容 | 支持表单快照和键值操作 |
| ResizeObserver | 轻量兼容 | 按文档帧检查尺寸变化 |
| MutationObserver | 轻量兼容 | 按文档帧批量派发变更 |
| DOMMatrix | 轻量兼容 | 2D 仿射矩阵 |
| OffscreenCanvas | 轻量兼容 | AUI Canvas 的离屏版本 |
| Path2D | 轻量兼容 | 路径绘制对象 |
| createImageBitmap | 轻量兼容 | 同步创建 AUI 图像位图 |
| createImageBitmapAsync | 轻量兼容 | 异步兼容版本 |

下面这些对象当前没有作为完整 Web API 提供：KeyboardEvent 构造器、navigator.clipboard、Selection/Range、history、matchMedia、XMLHttpRequest、WebSocket、IntersectionObserver 和 WebGL。

## 3. Window

### 3.1 视口属性

~~~javascript
window.innerWidth
window.innerHeight
window.devicePixelRatio
~~~

- innerWidth 和 innerHeight 是当前 Document 的逻辑 viewport 尺寸；
- devicePixelRatio 返回 Minecraft GUI scale；
- 页面存在 aui-viewport 缩放时，布局尺寸和实际绘制尺寸可能不同；
- 事件中的 clientX、clientY 已经是 Document 逻辑坐标，不要再次乘 devicePixelRatio 或 render scale。

### 3.2 Window 事件

~~~javascript
window.addEventListener("resize", function (event) {
    console.log(window.innerWidth, window.innerHeight);
});

window.dispatchEvent(new Event("custom", {bubbles: false}));
~~~

支持：

~~~text
window.addEventListener(type, listener)
window.addEventListener(type, listener, useCapture)
window.removeEventListener(type, listener)
window.removeEventListener(type, listener, useCapture)
window.dispatchEvent(event)
~~~

第三个参数当前按布尔捕获标记处理。不要把标准 {passive: true, signal: ...} 选项对象当作已实现的通用选项。AUI 内部还可使用第四个布尔参数表示 once：

~~~javascript
function onReady(event) {
    console.log("只执行一次");
}

window.addEventListener("custom", onReady, false, true);
~~~

### 3.3 定时器和帧调度

~~~javascript
var timeoutId = setTimeout(function () {
    console.log("timeout");
}, 100);

var intervalId = setInterval(function () {
    console.log("interval");
}, 1000);

clearTimeout(timeoutId);
clearInterval(intervalId);

var frameId = requestAnimationFrame(function (timestamp) {
    console.log(timestamp);
});
cancelAnimationFrame(frameId);
~~~

同样支持 window.setTimeout、window.clearTimeout、window.setInterval、window.clearInterval、window.requestAnimationFrame 和 window.cancelAnimationFrame。

这是客户端调度器驱动的轻量实现：

- requestAnimationFrame 目标间隔约为 16 ms，但不保证浏览器的帧时机语义；
- 定时器回调不在 DOM 微任务队列中执行；
- Document 被移除后，业务代码仍应主动清理自己保存的定时器句柄。

### 3.4 滚动代理

~~~javascript
window.scrollTo(0, 120);
window.scrollTo({left: 0, top: 120});
window.scrollBy(0, 20);
window.scrollBy({left: 0, top: 20});
~~~

Window 滚动会代理到当前 Document 的根滚动模型。滚轮默认滚动命中的可滚动元素；脚本调用 preventDefault() 后可以阻止本次默认滚动。详见 [浏览器辅助功能](browser-features.md) 的滚动和事件章节。

### 3.5 计算样式

~~~javascript
var style = getComputedStyle(document.getElementById("panel"));
console.log(style.getPropertyValue("display"));
console.log(style.get("font-size"));
console.log(style.fontSize);
~~~

当前 CSS style declaration 是轻量只读对象，常用读取方式为：

- getPropertyValue(name)；
- get(name)；
- 常见 Java Bean 属性，例如 fontSize、fontWeight、fontFamily、lineHeight、display 和 color。

它不是完整 CSSOM，不应假定支持浏览器中全部 CSSStyleDeclaration 枚举和修改接口。需要修改样式时使用 element.style 或 element.setAttribute("style", value)。

### 3.6 performance

~~~javascript
var started = performance.now();
// 执行需要测量的代码
console.log(performance.now() - started);
~~~

当前提供 performance.now()。它适合测量同一次客户端运行中的耗时，不等价于浏览器完整 Performance Timeline。

## 4. Document

### 4.1 主要属性

~~~javascript
document.readyState
document.activeElement
document.body
document.head
document.documentElement
document.location
document.URL
document.documentURI
document.baseURI
~~~

readyState 的状态流为：

~~~text
loading -> interactive -> complete
~~~

activeElement 没有焦点时通常回退到 body。URL、documentURI 和 baseURI 对应当前资源的逻辑路径；它们不是浏览器地址栏，也不会自动建立真实网页导航。

### 4.2 查询

~~~javascript
var panel = document.querySelector("#panel");
var buttons = document.querySelectorAll("button.action");
var byId = document.getElementById("panel");
var cards = document.getElementsByClassName("card");
var images = document.getElementsByTagName("img");
var named = document.getElementsByName("query");
~~~

支持：

~~~text
querySelector(selector)
querySelectorAll(selector)
getElementById(id)
getElementsByClassName(className)
getElementsByTagName(tagName)
getElementsByName(name)
~~~

querySelectorAll 和 getElementsBy* 返回的是 JavaScript 数组风格对象，不是完整的 NodeList 或 HTMLCollection：

~~~javascript
var list = document.querySelectorAll(".item");
for (var i = 0; i < list.length; i++) {
    console.log(list[i].textContent);
}

// 部分集合还提供 item() 和 namedItem()。
var first = list.item ? list.item(0) : list[0];
~~~

集合通常是当前调用时生成的快照，不要依赖浏览器 live collection 的行为。

### 4.3 创建节点

~~~javascript
var element = document.createElement("div");
var text = document.createTextNode("hello");
var comment = document.createComment("debug");
var fragment = document.createDocumentFragment();

element.appendChild(text);
fragment.appendChild(element);
document.body.appendChild(fragment);
~~~

支持 createElement、createTextNode、createComment 和 createDocumentFragment。AUI 的 Text、Comment 和 DocumentFragment 模型足够支持常见 DOM 构建，但不等价于浏览器中所有 Node 子类和 Shadow DOM 行为。

### 4.4 Document 树操作

~~~javascript
document.appendChild(node);
document.append(node, "text");
document.prepend(node);
~~~

append、prepend、元素的同名方法和 before/after/replaceWith 接受节点以及字符串、数字、布尔值等简单值。简单值会转换为文本节点。

### 4.5 Document 滚动和事件

~~~javascript
document.scrollTo(0, 120);
document.scrollBy(0, 20);

document.addEventListener("DOMContentLoaded", function () {
    console.log("DOM ready");
});

document.addEventListener("load", function () {
    console.log("resources loaded");
});
~~~

Document 支持事件监听、移除和分发，以及 scrollTo、scrollBy。普通页面创建时会按生命周期章节的顺序派发事件。

## 5. Node 和 Element

### 5.1 Node 类型和关系

~~~javascript
node.nodeType
node.nodeName
node.nodeValue
node.textContent
node.parentNode
node.childNodes
node.firstChild
node.lastChild
node.nextSibling
node.previousSibling
node.ownerDocument
node.isConnected
~~~

当前常量和类型值为：

| 类型 | 值 |
| --- | ---: |
| ELEMENT_NODE | 1 |
| TEXT_NODE | 3 |
| COMMENT_NODE | 8 |
| DOCUMENT_FRAGMENT_NODE | 11 |

支持：

~~~javascript
node.appendChild(child);
node.removeChild(child);
node.insertBefore(child, reference);
node.replaceChild(newChild, oldChild);
node.cloneNode(true);
node.before(nodeOrText);
node.after(nodeOrText);
node.replaceWith(nodeOrText);
node.remove();
node.contains(otherNode);
node.hasChildNodes();
~~~

节点被移动到新的父节点时，会同步更新 AUI 的 DOM 树、样式缓存、布局脏标记和 MutationObserver 记录。

### 5.2 元素内容和属性

~~~javascript
element.textContent = "<not html>";
element.innerText = "same text bridge";

element.innerHTML = "<span>new content</span>";
element.outerHTML = "<section>replacement</section>";

element.getAttribute("aria-label");
element.setAttribute("data-state", "ready");
element.removeAttribute("hidden");
element.hasAttribute("disabled");
element.toggleAttribute("hidden", true);
~~~

textContent 和 innerText 在当前页面桥接为同一类文本操作，不是完整浏览器的布局感知 innerText 算法。innerHTML 和 outerHTML 会重新解析 HTML；写入时会使旧子树失效，并触发对应的样式、布局和 MutationObserver 更新。

读取序列化结果时，文本和属性值会进行 HTML 转义：

~~~javascript
var label = document.createElement("div");
label.textContent = "<safe> & \"quoted\"";
console.log(label.innerHTML);
// &lt;safe&gt; &amp; &quot;quoted&quot;
~~~

HTML 资源解析阶段支持常见字符引用解码。不要把 innerHTML 当作纯文本字段；写纯文本请使用 textContent。

### 5.3 查询和关系方法

Element 支持与 Document 同名的查询方法，以及：

~~~javascript
element.matches(".selected");
element.closest(".card");
element.contains(otherElement);
element.children;
element.firstElementChild;
element.lastElementChild;
element.nextElementSibling;
element.previousElementSibling;
element.parentElement;
~~~

children 是元素数组风格结果；childNodes 会包含 AUI 的文本和注释节点。当前没有 Shadow DOM、insertAdjacentHTML 或 element.animate() 的完整实现。

### 5.4 classList 和 dataset

~~~javascript
element.classList.add("active");
element.classList.remove("loading");
element.classList.toggle("selected");
element.classList.toggle("selected", true);
element.classList.contains("active");
element.classList.item(0);

element.dataset.set("userId", "42");
element.dataset.set("mode", "compact");
console.log(element.dataset.get("mode"));
element.dataset.delete("mode");
~~~

classList 支持 length、contains、add、remove、toggle、item 和 toString。

dataset 同时支持属性风格和方法风格：

~~~text
data-user-id <-> dataset.userId
~~~

方法为 get、set、has、delete 和 keys。它是轻量 DOMStringMap，不是完整标准对象的所有枚举和原型行为。

### 5.5 样式

element.style 是当前 inline Style 对象的字段桥接。读取或修改已有字段可以用于兼容代码，但直接修改字段不应当作为触发 AUI 样式失效的唯一方式：

~~~javascript
console.log(element.style.color);
~~~

也可以通过 AUI 侧的属性方法写入：

~~~javascript
element.setAttribute("style", "color: white; padding: 4px;");
element.setInlineStyleProperty("background-color", "rgba(0, 0, 0, .5)");
~~~

element.style 是 AUI Style 对象的字段桥接，不是完整 CSSStyleDeclaration；不要假设所有 CSSOM 的 style 方法和属性都存在。具体 CSS 属性、值解析和布局差异见 [HTML / CSS 浏览器标准覆盖面](html-css-coverage.md)。

### 5.6 几何和滚动

~~~javascript
var rect = element.getBoundingClientRect();
console.log(rect.x, rect.y, rect.width, rect.height);
console.log(rect.left, rect.top, rect.right, rect.bottom);

element.scrollTop = 100;
element.scrollLeft = 20;
element.scrollTo(0, 120);
element.scrollTo({left: 20, top: 120});
element.scrollBy(0, 20);
~~~

DOMRect 提供 x、y、width、height、left、top、right 和 bottom。滚动属性使用 Document 逻辑坐标；滚动容器必须存在实际滚动范围才会移动。

### 5.7 图片元素

图片元素提供：

~~~javascript
image.currentSrc
image.naturalWidth
image.naturalHeight
image.complete
~~~

首次进入 ready 状态时派发 load，首次进入 failed 状态时派发 error。这两个事件默认不冒泡，也不会因为之后重复读取状态而补发历史事件。资源重载后，旧的图片句柄和事件监听器不能继续当作新一代 DOM 的句柄使用。

## 6. 事件

### 6.1 构造器

~~~javascript
var event = new Event("custom", {bubbles: true});
var custom = new CustomEvent("data", {
    detail: {value: 42},
    bubbles: true
});

var mouse = new MouseEvent("click", {
    clientX: 10,
    clientY: 20,
    button: 0
});

var wheel = new WheelEvent("wheel", {
    clientX: 10,
    clientY: 20,
    deltaX: 0,
    deltaY: 32,
    deltaMode: 0
});

var pointer = new PointerEvent("pointerdown", {
    clientX: 10,
    clientY: 20,
    button: 0,
    pointerId: 1,
    pointerType: "mouse",
    isPrimary: true
});
~~~

当前构造器实际读取的初始化字段有限：

- Event：type 和 bubbles；
- CustomEvent：detail 和 bubbles；
- MouseEvent：clientX、clientY、button；
- WheelEvent：鼠标字段以及 deltaX、deltaY、deltaMode；
- PointerEvent：鼠标字段以及 pointerId、pointerType、isPrimary。

例如标准 new Event("x", {cancelable: true}) 当前不会通过页面构造器打开完整的可取消语义。需要阻止默认行为时，优先使用框架生成的真实输入事件或表单事件。

### 6.2 事件字段和控制

常用字段：

~~~text
type target currentTarget bubbles cancelable defaultPrevented
detail eventPhase cancelBubble returnValue isTrusted timeStamp
~~~

常用方法：

~~~javascript
event.stopPropagation();
event.stopImmediatePropagation();
event.preventDefault();
event.composedPath();
~~~

事件分发支持捕获阶段、at-target 阶段和冒泡阶段。once 监听器执行后自动移除。preventDefault() 只有在事件可取消时才会设置 defaultPrevented；dispatchEvent 仅在最终 defaultPrevented 为真时返回 false。

isTrusted 用于区分 Minecraft/客户端输入派发的事件和脚本合成事件。调用 element.click() 等程序化操作不要假定会获得真实用户输入的全部权限。

### 6.3 鼠标、滚轮和指针坐标

真实鼠标事件的 clientX、clientY 已完成屏幕到 Document 的 viewport 反变换：

~~~javascript
document.addEventListener("mousemove", function (event) {
    var rect = document.getElementById("panel").getBoundingClientRect();
    var inside = event.clientX >= rect.left && event.clientX <= rect.right
        && event.clientY >= rect.top && event.clientY <= rect.bottom;
});
~~~

不要再次乘 renderScale、devicePixelRatio 或页面缩放值。对于 WorldWindow，事件坐标已经是世界平面对应的 Document 坐标，具体的世界投影流程见 [WorldWindow 完整文档](world-window.md)。

常见字段包括：

~~~text
clientX clientY pageX pageY offsetX offsetY
movementX movementY button buttons
deltaX deltaY deltaMode
pointerId pointerType isPrimary
altKey shiftKey controlKey metaKey
~~~

### 6.4 键盘事件

键盘事件由 Minecraft/GLFW 输入侧生成，页面可以监听，但当前没有标准 KeyboardEvent 构造器：

~~~javascript
document.addEventListener("keydown", function (event) {
    if (event.controlKey && event.key === "s") {
        event.preventDefault();
    }
});
~~~

键盘事件常用字段为：

~~~text
key code keyCode scanCode repeat
altKey shiftKey controlKey metaKey
~~~

controlKey 是 AUI 当前的控制键字段；迁移浏览器代码时不要只检查不存在的 ctrlKey。不同键盘布局下 key 由 GLFW 名称解析，可能是 Unidentified。

### 6.5 支持的事件类型

当前输入和生命周期中常见的事件包括：

~~~text
click dblclick contextmenu
mousedown mouseup mousemove mouseover mouseout mouseenter mouseleave
wheel scroll
pointerdown pointerup pointermove pointerover pointerout pointerenter pointerleave
keydown keyup
focus blur input beforeinput change submit reset formdata invalid select
copy cut paste compositionstart compositionupdate compositionend
DOMContentLoaded load unload resize
~~~

事件是否冒泡、是否可取消取决于事件来源。特别是 focus、blur、图片 load/error 和部分指针边界事件不要按普通冒泡事件处理。

## 7. 表单 API

### 7.1 控件

当前重点支持：form、input、textarea、select、option、button。常用属性和方法包括：

~~~javascript
input.value = "hello";
input.defaultValue = "initial";
input.checked = true;
input.defaultChecked = false;
input.disabled = false;
input.name = "query";
input.type = "text";

select.selectedIndex = 1;
select.options;
select.selectedOptions;
option.selected = true;

input.focus();
input.blur();
input.click();
~~~

还支持：

- multiple、required、readOnly、pattern、min、max、step；
- placeholder、accept、autocomplete、inputMode；
- selectionStart、selectionEnd、selectionDirection；
- setSelectionRange、setRangeText、select；
- valueAsNumber、stepUp、stepDown；
- validity、validationMessage、willValidate；
- checkValidity、reportValidity、setCustomValidity；
- files，但当前是路径信息近似的 FileList。

控件类型会按 AUI 支持范围归一化；不支持的特殊输入类型可能降级为普通文本输入。不要依赖浏览器原生弹出的日期、颜色或文件选择界面。

### 7.2 表单提交和重置

~~~javascript
var form = document.getElementById("settings");

form.addEventListener("submit", function (event) {
    event.preventDefault();
    console.log("submitter", event.submitter);
});

form.addEventListener("formdata", function (event) {
    console.log(event.formData.get("name"));
});

form.requestSubmit();
form.submit();
form.reset();
~~~

- submit() 派发可取消的 submit 事件；
- requestSubmit() 会先执行约束校验，并可以传入 submit button；
- 取消 submit 后不会派发 formdata；
- reset() 会派发可取消的 reset，未取消时恢复控件默认值；
- AUI 不会因为表单 action 自动发起 HTTP 请求。

成功控件收集会处理禁用控件、checkbox/radio 选中状态、select 的选项、submitter 以及 file 控件等规则。form 关联支持控件在表单外通过 form="id" 关联的常见用法。

### 7.3 FormData

~~~javascript
var data = new FormData(form);
data.append("tag", "aui");
data.append("tag", "rhino");
data.set("page", "1");

console.log(data.get("tag"));
console.log(data.getAll("tag"));
console.log(data.has("page"));
data.delete("page");

data.forEach(function (value, key) {
    console.log(key, value);
});

var keys = data.keys();
var values = data.values();
var entries = data.entries();
~~~

支持：

~~~text
new FormData()
new FormData(form)
append set delete get has getAll
keys values entries forEach toString
~~~

keys()、values() 和 entries() 返回数组风格结果，不是浏览器的可迭代 iterator。当前表单文件项以路径/文件名近似值保存；不能把它当作完整 Blob/File 上传对象。

toString() 将当前键值编码成查询字符串，适合调试或简单表单数据处理，不会自动把它接入 fetch。

## 8. Storage、Location 和 URLSearchParams

### 8.1 Storage

~~~javascript
localStorage.setItem("theme", "ore");
var theme = localStorage.getItem("theme");
localStorage.removeItem("theme");
localStorage.clear();
console.log(localStorage.length, localStorage.key(0));

sessionStorage.setItem("draft", "text");
~~~

两者都支持：

~~~text
getItem(key)
setItem(key, value)
removeItem(key)
clear()
key(index)
length
~~~

实现差异：

- localStorage 在 Minecraft 环境中保存到 config/apricityui/localStorage.nbt；
- sessionStorage 只在当前客户端运行期有效；
- 空 key 会被忽略；
- Java/Rhino 侧传入 null 时，当前实现可能保存为字符串 "null"；
- 当前没有标准 storage 事件和跨窗口同步模型。

### 8.2 虚拟 location

~~~javascript
console.log(window.location.href);
console.log(window.location.pathname);
console.log(window.location.search);
console.log(window.location.hash);
console.log(window.location.searchParams.getAll("page"));
~~~

window.location 和 document.location 都由当前 Document 的资源路径生成，支持：

~~~text
href protocol host hostname port origin
pathname search hash searchParams
assign replace reload
~~~

assign()、replace() 和 reload() 当前是占位方法，不会执行真实网页导航或刷新。这个对象没有把完整 URL 序列化为自定义的 toString()；需要字符串时直接读取 href。location 主要用于读取资源路径和查询字符串。

### 8.3 URLSearchParams 的实际范围

当前 URLSearchParams 是轻量实现：

~~~javascript
var params = new URLSearchParams("?page=2&tag=aui&tag=web");
params.append("sort", "name");
console.log(params.getAll("tag"));
params.forEach(function (value, key) {
    console.log(key, value);
});
params.sort();
console.log(params.toString());
~~~

当前实现的方法只有：

~~~text
new URLSearchParams(string)
append(key, value)
getAll(key)
sort()
forEach(callback, thisArg)
toString()
~~~

不要假定当前提供标准的 get、set、delete、has、keys、values 或 entries。对象、数组和完整 iterable 初始化也不应当作已支持行为。

## 9. fetch

### 9.1 基本调用

~~~javascript
fetch("data.json")
    .then(function (response) {
        var text = response.text();
        console.log(response.ok, response.status, response.url);
        console.log(text);
    })
    ["catch"](function (error) {
        console.error(error);
    });
~~~

当前页面入口支持 fetch(url) 一个参数。相对路径按照当前 Document 的 baseURI 由 AUI Loader 解析：

- 本地资源从 AUI 资源包读取；
- 远程资源受网络资源管线的协议、大小、超时、重定向、重试和并发限制；
- 远程访问按当前网络策略使用 HTTPS；
- 找不到本地资源、网络失败和 JSON 解析失败都会写入 AUI 日志。

### 9.2 Response

返回对象支持：

~~~javascript
fetch("data.json").then(function (response) {
    console.log(response.ok);
    console.log(response.status);
    console.log(response.url);

    var text = response.text();
    var json = response.json();
    var bytes = response.bytes();
});
~~~

支持的属性和方法：

~~~text
ok status url
text()
json()
bytes()
~~~

json() 使用 AUI 内置 JSON 解析器，返回 Rhino 可用的对象、数组、字符串、数字、布尔值或 null。bytes() 返回字节数组副本。

### 9.3 Promise 语义限制

fetch 返回的是 AUI 自己的轻量异步对象。支持 then(onFulfilled[, onRejected])、catchError(onRejected)，并由页面桥接提供 catch 别名：

~~~javascript
fetch("data.json").then(
    function (response) {
        console.log(response.text());
    },
    function (error) {
        console.error(error);
    }
);
~~~

它不是完整标准 Promise：

- 不支持 fetch(url, init)；
- 不支持自定义 method、headers、body、credentials 等选项；
- 不要依赖标准 Promise 的返回值变换和任意链式组合；
- 不支持 AbortController 取消请求；
- 当前主要用途是读取本地资源或简单 GET 资源。

如果需要读取 JSON，建议在同一个 then 回调中调用 response.json()，不要把它当作浏览器 Response Promise 再建立复杂链。

## 10. Observer

### 10.1 ResizeObserver

~~~javascript
var resizeObserver = new ResizeObserver(function (entries, observer) {
    for (var i = 0; i < entries.length; i++) {
        var entry = entries[i];
        console.log(entry.target, entry.contentRect.width);
    }
});

resizeObserver.observe(document.getElementById("panel"));
resizeObserver.unobserve(document.getElementById("panel"));
resizeObserver.disconnect();
~~~

支持 observe、unobserve 和 disconnect。Entry 提供：

~~~text
target
contentRect
borderBoxSize
contentBoxSize
~~~

contentRect 提供 x、y、left、top、width、height、right、bottom，并额外提供 borderBoxWidth、borderBoxHeight。borderBoxSize 和 contentBoxSize 是包含 inlineSize/blockSize 的数组风格对象。

尺寸变化由 AUI 文档帧检查，不保证浏览器 ResizeObserver 的全部循环检测和微任务时机。没有实际尺寸变化时不会重复回调。

### 10.2 MutationObserver

~~~javascript
var mutationObserver = new MutationObserver(function (records, observer) {
    for (var i = 0; i < records.length; i++) {
        var record = records[i];
        console.log(record.type, record.target, record.attributeName);
    }
});

mutationObserver.observe(document.documentElement, {
    childList: true,
    attributes: true,
    characterData: true,
    subtree: true,
    attributeOldValue: true,
    characterDataOldValue: true,
    attributeFilter: ["class", "style"]
});

var pending = mutationObserver.takeRecords();
mutationObserver.disconnect();
~~~

支持的观察选项：

~~~text
childList attributes characterData subtree
attributeOldValue characterDataOldValue attributeFilter
~~~

Record 提供：

~~~text
type target addedNodes removedNodes
previousSibling nextSibling attributeName oldValue
~~~

当前主要记录 DOM 子节点增删、属性修改、文本修改以及 subtree 变化。AUI 按 Document 帧批量派发 MutationObserver，不是浏览器微任务时机。刷新或移除 Document 会清理该 Document 的观察器；刷新后必须重新获取节点并重新 observe。

## 11. Canvas 和图像

### 11.1 Canvas 2D

~~~html
<canvas id="chart" width="320" height="160"></canvas>
<script>
    var canvas = document.getElementById("chart");
    var ctx = canvas.getContext("2d");
    ctx.fillStyle = "#2f7d8c";
    ctx.fillRect(10, 10, 120, 40);
    ctx.font = "16px sans-serif";
    ctx.fillStyle = "white";
    ctx.fillText("ApricityUI", 18, 36);
</script>
~~~

getContext("2d") 是当前唯一的 Canvas context 类型。常用能力包括：

- fillRect、strokeRect、clearRect；
- fillText、strokeText、measureText；
- beginPath、closePath、moveTo、lineTo、rect、roundRect；
- arc、arcTo、ellipse、quadraticCurveTo、bezierCurveTo；
- fill、stroke、clip、isPointInPath、isPointInStroke；
- save、restore、translate、rotate、scale、transform、setTransform、resetTransform；
- createLinearGradient、createRadialGradient、createPattern；
- createImageData、getImageData、putImageData；
- drawImage；
- globalAlpha、globalCompositeOperation、filter、阴影和图像平滑属性；
- Canvas 元素的 toDataURL；2D context 的 toBlob。

Canvas 只有 AUI 的 Java2D 实现，具体颜色、滤镜、字体和合成效果可能与浏览器 Canvas 不完全相同。

### 11.2 Path2D 和 DOMMatrix

~~~javascript
var path = new Path2D();
path.moveTo(0, 0);
path.lineTo(40, 20);
path.closePath();

var matrix = new DOMMatrix();
matrix.translateSelf(10, 5).scaleSelf(2, 2).rotateSelf(15);
ctx.setTransform(matrix);
ctx.stroke(path);
~~~

Path2D 支持路径、弧线、椭圆、圆角矩形和 addPath。也可以用 SVG path 字符串构造常见路径。DOMMatrix 当前是 2D 仿射矩阵，主要字段为 a、b、c、d、e、f，支持 translateSelf、scaleSelf、rotateSelf、multiplySelf 和 invertSelf。

### 11.3 OffscreenCanvas、ImageBitmap 和 Blob

~~~javascript
var offscreen = new OffscreenCanvas(128, 64);
var offscreenContext = offscreen.getContext("2d");
offscreenContext.fillStyle = "red";
offscreenContext.fillRect(0, 0, 128, 64);
var bitmap = offscreen.transferToImageBitmap();

console.log(bitmap.width, bitmap.height);
bitmap.close();
~~~

还支持：

~~~javascript
var bitmap = createImageBitmap(canvas);
var cropped = createImageBitmap(canvas, 0, 0, 32, 32);

createImageBitmapAsync(canvas).then(function (asyncBitmap) {
    console.log(asyncBitmap.width);
});
~~~

AUI 的 createImageBitmap 当前是同步轻量实现；createImageBitmapAsync 和 Canvas 编码异步方法使用 AUI 自己的回调对象，不应假定拥有浏览器完整 Blob/Promise 生命周期。

2D context 导出的 Blob 兼容对象提供 size、type、arrayBuffer()、text() 和 toDataURL() 等 AUI 方法。当前不提供 WebGL context。

## 12. HTML、CSS、脚本和资源管线

### 12.1 HTML 解析

Document 创建或刷新时会：

1. 读取逻辑路径对应的 HTML；
2. 解析 html、head、body 和元素树；
3. 解码常见字符引用；
4. 收集 style、link rel="stylesheet" 和 script；
5. 加载 CSS、图像、字体等资源；
6. 执行页面脚本并计算初始样式和布局。

script src 使用相对于当前 HTML 的逻辑路径解析。当前实现对带 src 且仍有内联代码的 script 不执行浏览器式二选一，而是可能同时处理外部代码和内联代码；迁移页面时不要依赖浏览器忽略内联部分的行为。

### 12.2 CSS

CSS 由 AUI 自己的解析器和布局器处理。style、link rel="stylesheet"、内联 style、CSS 变量、伪元素、动画和过渡等能力的精确覆盖面见：[HTML / CSS 浏览器标准覆盖面](html-css-coverage.md)。

### 12.3 脚本异常和日志

以下问题会写入 AUI 日志：

- HTML 资源缺失或 HTML 解析失败；
- CSS 资源缺失、导入失败或 CSS 解析异常；
- JavaScript 执行异常和事件监听器异常；
- 外部脚本、图像、字体和 fetch 资源加载失败；
- JSON 解析异常；
- Canvas 图像解码和异步位图任务失败。

页面代码也可以使用：

~~~javascript
console.log("info");
console.debug("debug");
console.warn("warning");
console.error("error");

console.time("layout");
// work
console.timeEnd("layout");
~~~

console.debug 当前等价于普通日志输出。具体日志前缀通常包含 [AUI HTML]、[AUI CSS]、[AUI JS]、[AUI Fetch]、[AUI Canvas] 或 [AUI Event]，排查资源问题时优先搜索这些前缀和资源路径。

## 13. 生命周期、刷新和引用失效

### 13.1 页面生命周期

典型流程为：

~~~text
创建 Document
  -> loading
  -> 解析 HTML/CSS/JS
  -> interactive
  -> DOMContentLoaded
  -> complete
  -> load
~~~

Screen 或宿主关闭时会派发 unload，随后 Document 被移除。Overlay、Screen、Container 和 WorldWindow 对 Document 的显示宿主不同，但页面级 DOM API 相同，差异见相关宿主文档。

### 13.2 refresh

宿主调用 document.refresh() 或执行全局资源重载时，AUI 会重新构建页面：

~~~text
重新读取 HTML
  -> 清理旧 DOM、样式和渲染缓存
  -> 重新执行 CSS/JS
  -> 重新派发 DOMContentLoaded/load
~~~

Document 为每次刷新递增 refreshGeneration，Java 宿主可以用 getRefreshGeneration() 判断代数。页面脚本没有标准的 generation 属性，但可以在 load 中重新初始化自己的状态。

刷新后以下对象不能跨代复用：

- 旧 Element、Text、Comment 和集合对象；
- 旧事件监听器；
- 旧 MutationObserver 和 ResizeObserver 的目标；
- 指向旧 DOM 的业务缓存；
- 依赖旧 Canvas surface 的图像对象。

正确做法是把初始化逻辑放进函数，在 DOMContentLoaded 或 load 中重新查询节点：

~~~javascript
function installPage() {
    var button = document.getElementById("reloadable");
    if (button === null) return;
    button.addEventListener("click", function () {
        console.log("current document generation");
    });
}

document.addEventListener("DOMContentLoaded", installPage);
~~~

不要在每帧调用 refresh()。刷新会重建整个页面，适合资源重载或显式重新初始化，不适合普通状态更新。

## 14. ApricityUI 扩展

这一节是 AUI 特有能力，不属于标准 Web API。

### 14.1 aui-viewport

~~~html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
~~~

支持的核心模式包括：

~~~text
gui / mc / default
window / native / screen / fullscreen
browser / css / web
fixed
~~~

fixed 可配合 width、height 和 scale；其他模式会依据 Minecraft 窗口或显示器尺寸解析逻辑 viewport。zoom、min-zoom、max-zoom、zoom-step 和 user-scalable 控制页面缩放。

Ctrl + 滚轮、Ctrl + +/-/0 的完整规则、缩放持久化和穿透配置见 [浏览器辅助功能](browser-features.md)。缩放同时影响布局 viewport、渲染变换和命中测试，不要只把最终画面当作图片缩放。

### 14.2 aui-font-mode

~~~html
<meta name="aui-font-mode" content="web">
~~~

当前主要模式：

~~~text
mc
web
web-scaled
~~~

它影响默认字体和字号基准。页面需要与浏览器 CSS 像素接近时通常使用 web 或 web-scaled；Minecraft 风格界面可以使用 mc。这不是浏览器的标准 Meta 元素。

### 14.3 aui-mouse-events

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

intercept、block、true、yes、on 和 1 会启用该 Document 的输入拦截。它只在 Document 命中可交互元素时阻止底层输入；不会让隐藏、裁剪或 pointer-events: none 的元素获得命中。

显示层如果不应挡住底层页面，可以使用：

~~~css
.overlay-layer {
    pointer-events: none;
}

.overlay-layer button {
    pointer-events: auto;
}
~~~

### 14.4 Overlay、Screen 和手动渲染

页面 DOM API 不负责创建宿主。宿主侧可以创建 Overlay Document、绑定 ApricityScreen、绑定容器 Screen 或注册 WorldWindow。使用方式分别见：

- [ApricityScreen 使用文档](apricity-screen.md)；
- [以 Overlay 形式管理 Document](overlay-document.md)；
- [Apricity 容器文档](container.md)；
- [WorldWindow 完整文档](world-window.md)。

高级宿主可以把 Document 设置为手动渲染。手动渲染后它会从全局绘制和输入分发中排除，调用方必须自己完成绘制、坐标变换和事件转发；普通页面不要使用这个模式。

### 14.5 Top layer

AUI 的 Element 有宿主侧 setTopLayer/isTopLayer 能力，用于让当前 Document 内的弹窗、菜单或选择器在普通树内容之后绘制，并避免祖先 overflow 裁剪。它只改变当前 Document 内的绘制顺序，不会把一个 Document 放到另一个 Document 之上。

### 14.6 WorldWindow 的坐标

WorldWindow 中的页面仍然使用 document、Element 和普通事件，但页面被投影到世界平面：

- 页面逻辑尺寸由 viewport 配置决定；
- 鼠标命中先经过准心/射线与世界平面的求交，再转换为 Document 坐标；
- clientX、clientY 是平面内逻辑坐标，不是屏幕像素；
- 不要在脚本中重复应用世界窗口缩放或 Minecraft GUI scale。

资源管理器的世界内预览也遵循这个规则。发生 hover 偏移时，应先检查宿主的屏幕到 Document 变换，而不是在页面回调中给坐标再乘一次缩放。

### 14.7 自定义元素和 Minecraft 扩展

AUI 还注册了若干面向 Minecraft UI 的自定义元素，例如 TRANSLATION、纹理、Sprite、SVG、槽位和 Canvas 等。它们属于 AUI 元素库，不是浏览器原生 HTML。具体标签和属性见 [扩展元素文档](extension-elements.md)、[内置 UI 库](ui-library.md) 与 [Apricity 容器文档](container.md)。第三方模组注册自己的元素时，见 [二次开发文档](secondary-development.md)。

## 15. 支持矩阵

| 范围 | 当前状态 | 备注 |
| --- | --- | --- |
| DOM 查询、创建、增删节点 | 可用 | 集合为数组风格，Node 模型为 AUI 实现 |
| 属性、classList、dataset | 可用 | dataset 为轻量 DOMStringMap |
| innerHTML、outerHTML、文本转义 | 可用 | 重新解析会使旧子树失效 |
| CSSOM 读取 | 部分支持 | getComputedStyle 只读轻量对象 |
| 几何、滚动、命中坐标 | 可用 | 使用 Document 逻辑坐标 |
| 捕获、冒泡、once、取消默认行为 | 可用 | 事件选项对象不完整 |
| 鼠标、滚轮、Pointer 事件 | 可用 | Pointer 主要是鼠标兼容层 |
| 键盘真实事件 | 可用 | 无标准 KeyboardEvent 构造器 |
| 表单控件和校验 | 可用 | 特殊输入类型和浏览器 UI 有差异 |
| FormData | 部分支持 | 数组风格集合，文件是路径近似值 |
| localStorage/sessionStorage | 可用 | localStorage 保存到 Minecraft 配置 |
| location | 部分支持 | 读取虚拟路径，导航方法为空操作 |
| URLSearchParams | 部分支持 | 方法集比标准少 |
| fetch | 部分支持 | 只有轻量 GET，不是完整 Promise |
| ResizeObserver | 部分支持 | 按文档帧检测尺寸变化 |
| MutationObserver | 部分支持 | 按文档帧批量派发 |
| Canvas 2D | 部分支持 | Java2D 实现，无 WebGL |
| HTML/CSS 解析和布局 | 部分支持 | 见独立覆盖面文档 |
| Shadow DOM、history、XHR、WebSocket | 未提供 | 不要依赖这些浏览器接口 |

## 16. 当前未提供或不应假定的接口

当前没有完整实现或没有注入页面全局的能力包括：

~~~text
KeyboardEvent 构造器
navigator / navigator.clipboard
Selection / Range
history / matchMedia
XMLHttpRequest / WebSocket / EventSource
IntersectionObserver
WebGL / WebGPU
Service Worker / Cache API
完整 Promise、AbortController、ReadableStream
Shadow DOM / Custom Elements 完整注册模型
真实网页导航、跨 Document iframe、postMessage
~~~

文字选择和复制由 AUI 自己的输入与剪贴板辅助逻辑处理，不要把它实现成浏览器 Selection/Range 代码。相关行为见 [浏览器辅助功能](browser-features.md)。

## 17. 性能和可靠性建议

- 高频状态更新优先修改 textContent、属性、class 或少量已有节点；
- 不要每帧重新设置 innerHTML，也不要每帧创建和销毁 Document；
- 大量 DOM 修改尽量在同一个客户端任务中完成，让 AUI 合并样式、布局和绘制脏标记；
- Observer、定时器、事件监听器和世界窗口在关闭时主动清理；
- 不要缓存跨 refresh() 的 Element、集合、Canvas 或 Observer；
- 读取事件坐标时使用逻辑坐标，避免重复乘缩放；
- 资源读取失败时先检查逻辑路径和 AUI 日志，再检查页面代码；
- 对 fetch、response.json() 和 Canvas 图像解码都提供错误处理；
- 需要完整浏览器语义的页面，应先按支持矩阵移除依赖或提供 AUI 专用降级路径。

## 18. 源码和测试索引

核心实现：

- [global.js](../src/main/resources/assets/apricityui/apricity/global.js)：页面脚本全局桥接、属性装饰器和轻量兼容对象；
- [Document.java](../src/main/java/com/sighs/apricityui/init/Document.java)：Document、生命周期、刷新、Observer 和滚动；
- [Window.java](../src/main/java/com/sighs/apricityui/init/Window.java)：Window、定时器、fetch、Storage、ResizeObserver 和 Canvas 辅助；
- [Node.java](../src/main/java/com/sighs/apricityui/init/Node.java)：节点树和事件注册；
- [Element.java](../src/main/java/com/sighs/apricityui/init/Element.java)：属性、DOM 操作、表单、几何、classList、dataset 和序列化；
- [Event.java](../src/main/java/com/sighs/apricityui/init/Event.java)：捕获、目标、冒泡和事件控制；
- [KeyEvent.java](../src/main/java/com/sighs/apricityui/event/KeyEvent.java)：Minecraft 键盘事件字段；
- [LocalStorage.java](../src/main/java/com/sighs/apricityui/init/LocalStorage.java)：NBT 持久化 Storage；
- [BrowserLocation.java](../src/main/java/com/sighs/apricityui/init/BrowserLocation.java)：虚拟 location 解析；
- [CanvasRenderingContext2D.java](../src/main/java/com/sighs/apricityui/canvas/CanvasRenderingContext2D.java)：Canvas 2D 绘制；
- [ApricityViewport.java](../src/main/java/com/sighs/apricityui/instance/ApricityViewport.java)：viewport 和页面缩放配置。

主要测试：

- [DomSemanticsTest.java](../src/test/java/com/sighs/apricityui/webapi/DomSemanticsTest.java)：DOM 节点、属性、序列化和几何；
- [ElementBindingTest.java](../src/test/java/com/sighs/apricityui/webapi/ElementBindingTest.java)：脚本属性、事件和输入；
- [FormCompatibilityTest.java](../src/test/java/com/sighs/apricityui/webapi/FormCompatibilityTest.java)：表单、校验和 FormData；
- [WindowApiTest.java](../src/test/java/com/sighs/apricityui/webapi/WindowApiTest.java)：Window、Storage、定时器、fetch 和 ResizeObserver；
- [DocumentLifecycleTest.java](../src/test/java/com/sighs/apricityui/webapi/DocumentLifecycleTest.java)：生命周期和刷新；
- [GlobalJsBootstrapTest.java](../src/test/java/com/sighs/apricityui/webapi/GlobalJsBootstrapTest.java)：页面全局桥接脚本；
- [ScriptDomBridgeTest.java](../src/test/java/com/sighs/apricityui/webapi/ScriptDomBridgeTest.java)：Rhino 页面脚本与 DOM 桥接；
- [ResourcePipelineTest.java](../src/test/java/com/sighs/apricityui/webapi/ResourcePipelineTest.java)：HTML、CSS、脚本和资源管线；
- [SelectCompatibilityTest.java](../src/test/java/com/sighs/apricityui/webapi/SelectCompatibilityTest.java)：select/option 交互。
