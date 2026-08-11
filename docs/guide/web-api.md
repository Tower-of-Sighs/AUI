# ApricityUI Web API

AUI 不是 Chromium，也没有浏览器内核。页面 JavaScript 由 Rhino 执行，Java 侧的 Document、Element、事件和资源管线被桥接成浏览器风格的对象。所以这里的 API 分三类：

- **可用**：按下文示例直接用；
- **轻量兼容**：名字和常用调法和浏览器一样，但返回值、时机或参数范围有缩减；
- **未提供**：没实现，别假设存在。

CSS 属性和布局见 [HTML/CSS 覆盖面](html-css-coverage)，页面级的 viewport/字体/鼠标 meta 配置见 [ApricityScreen 文档](apricity-screen#页面-meta-配置)。

## 快速开始

```html
<button id="load" type="button">读取数据</button>
<pre id="output"></pre>
<script>
    var output = document.getElementById("output");
    document.getElementById("load").addEventListener("click", function () {
        fetch("data.json").then(function (response) {
            output.textContent = response.ok
                ? JSON.stringify(response.json())
                : "HTTP status: " + response.status;
        }, function (error) {
            output.textContent = "读取失败";
        });
    });
</script>
```

页面脚本建议用 `var`、普通 `function` 和传统循环。AUI 会对部分现代写法做兼容转换，但 Rhino 不是现代 JS 引擎，别赌。

## 全局对象

每个 Document 有独立的 `document`，共享一个 window 兼容对象。注入的全局：

| 全局 | 状态 | 说明 |
| --- | --- | --- |
| window / document / console | 可用 | console 写入 AUI 日志 |
| localStorage / sessionStorage | 可用 | 见下文 Storage 节 |
| performance | 轻量 | 只有 `now()` |
| fetch | 轻量 | 受限 GET，见下文 |
| getComputedStyle | 轻量 | 只读 |
| setTimeout / setInterval / requestAnimationFrame | 轻量 | 客户端调度器驱动 |
| Event / CustomEvent / MouseEvent / WheelEvent / PointerEvent | 可用 | 构造器读取的字段有限 |
| URLSearchParams / FormData | 轻量 | 方法集比标准少 |
| ResizeObserver / IntersectionObserver / MutationObserver | 轻量 | 按文档帧派发，不是微任务时机 |
| DOMMatrix / Path2D / OffscreenCanvas / createImageBitmap | 轻量 | 见 Canvas 节 |

**没有提供**：KeyboardEvent 构造器、navigator.clipboard、Selection/Range、history、matchMedia、XMLHttpRequest、WebSocket、WebGL、Service Worker、完整 Promise、AbortController、Shadow DOM、iframe/postMessage。文字选择复制是 AUI 自己的实现，别按 Selection/Range 写。

## Window

```javascript
window.innerWidth          // Document 逻辑视口尺寸
window.innerHeight
window.devicePixelRatio    // Minecraft GUI scale
```

事件里的 `clientX/clientY` 已经是逻辑坐标，不要再乘 devicePixelRatio 或 renderScale。

**事件**：`addEventListener(type, fn)` / `removeEventListener` / `dispatchEvent`，第三参数只按布尔 capture 处理，`{passive, signal}` 选项对象没实现。AUI 内部多一个第四参数表示 once：`addEventListener("custom", fn, false, true)`。

**定时器**：

```javascript
var id = setTimeout(fn, 100);        clearTimeout(id);
var id = setInterval(fn, 1000);      clearInterval(id);
var id = requestAnimationFrame(fn);  cancelAnimationFrame(id);
```

rAF 目标间隔约 16ms，但不保证浏览器帧时机。Document 移除后，自己保存的定时器句柄要自己清。

**滚动**：`window.scrollTo(x, y)` / `scrollTo({left, top})` / `scrollBy(...)`，代理到根滚动模型。

**getComputedStyle** 是只读轻量对象：

```javascript
var style = getComputedStyle(el);
style.getPropertyValue("display");
style.get("font-size");   // 等价写法
style.fontSize;           // 常见字段也可直接读（fontSize/fontWeight/fontFamily/lineHeight/display/color 等）
```

改样式可以使用实时双向绑定的 `element.style`，也可以使用 `setAttribute("style", ...)`。

## Document

属性：`readyState`（loading → interactive → complete）、`activeElement`（无焦点时回退 body）、`body`、`head`、`documentElement`、`location`、`URL` / `documentURI` / `baseURI`（都是逻辑路径，不是浏览器地址栏）。

查询：

```javascript
document.querySelector("#panel");
document.querySelectorAll("button.action");
document.getElementById("panel");
document.getElementsByClassName("card");
document.getElementsByTagName("img");
document.getElementsByName("query");
```

返回的是数组风格快照，不是 live 的 NodeList/HTMLCollection。用 `list.length` + 下标遍历，部分集合有 `item()` / `namedItem()`。

创建节点：`createElement` / `createTextNode` / `createComment` / `createDocumentFragment`。没有 Shadow DOM。

树操作：`appendChild` / `append` / `prepend`，`append`/`prepend` 接受字符串、数字等简单值（转成文本节点）。Document 也支持事件监听和 `scrollTo` / `scrollBy`。

## Node 和 Element

**Node**：`nodeType`（元素 1、文本 3、注释 8、Fragment 11）、`nodeName`、`nodeValue`、`textContent`、`parentNode`、`childNodes`、`firstChild`/`lastChild`、`nextSibling`/`previousSibling`、`ownerDocument`、`isConnected`；方法 `appendChild` / `removeChild` / `insertBefore` / `replaceChild` / `cloneNode(deep)` / `before` / `after` / `replaceWith` / `remove` / `contains` / `hasChildNodes`。

**内容和属性**：

```javascript
el.textContent = "<not html>";     // 纯文本
el.innerHTML = "<span>x</span>";   // 重新解析，旧子树失效
el.outerHTML = "<section>...</section>";
el.getAttribute("data-state");
el.setAttribute("data-state", "ready");
el.removeAttribute("hidden");
el.hasAttribute("disabled");
el.toggleAttribute("hidden", true);
```

`textContent` 返回 DOM 子树中的原始文本；`innerText` 返回渲染文本，会处理 `display:none`、`visibility`、`white-space`、`text-transform`、块级边界、`<br>` 与表格分隔符。写入 `innerText` 时换行会转换为 `<br>`，写入 `textContent` 时则保留为一个纯文本值。读序列化结果时文本和属性会被转义；需要解析标签时才使用 `innerHTML`。

**查询和关系**：Element 有和 Document 同名的查询方法，外加 `matches` / `closest` / `contains` / `children` / `firstElementChild` / `lastElementChild` / `nextElementSibling` / `previousElementSibling` / `parentElement`。没有 `insertAdjacentHTML` 和 `element.animate()`。

**classList 和 dataset**：

```javascript
el.classList.add("active");
el.classList.toggle("selected", true);
el.classList.contains("active");

el.dataset.set("userId", "42");   // 方法风格
el.dataset.userId;                // 属性风格，对应 data-user-id
```

classList 有 `length/contains/add/remove/toggle/item/toString`；dataset 方法有 `get/set/has/delete/keys`。

**样式**：`element.style` 是稳定的 inline `CSSStyleDeclaration` 对象。字段赋值、`cssText`、`length/item()`、数字索引以及 `getPropertyValue/getPropertyPriority/setProperty/removeProperty` 都会和 `style` attribute、计算样式及布局实时双向同步；也可使用 `el.setAttribute("style", "...")` 或 `el.setInlineStyleProperty("background-color", "...")`。

**几何和滚动**：`getBoundingClientRect()` 返回带 `x/y/width/height/left/top/right/bottom` 的 DOMRect；`scrollTop/scrollLeft/scrollTo/scrollBy` 用逻辑坐标。

**图片元素**：`currentSrc`、`naturalWidth/naturalHeight`、`complete`。首次 ready 派发 `load`、首次失败派发 `error`，都不冒泡、不补发。

## 事件

构造器能读这些初始化字段（多了不读）：

- `Event`：type、bubbles
- `CustomEvent`：detail、bubbles
- `MouseEvent`：clientX、clientY、button
- `WheelEvent`：鼠标字段 + deltaX/deltaY/deltaMode
- `PointerEvent`：鼠标字段 + pointerId、pointerType、isPrimary

`new Event("x", {cancelable: true})` 不会得到完整可取消语义。要阻止默认行为，尽量用框架生成的真实输入事件。

字段和方法都是常见的那些：`type/target/currentTarget/bubbles/cancelable/defaultPrevented/detail/eventPhase/cancelBubble/returnValue/isTrusted/timeStamp`，`stopPropagation()` / `stopImmediatePropagation()` / `preventDefault()` / `composedPath()`。捕获、at-target、冒泡三阶段都有；once 监听器执行后自动移除；`dispatchEvent` 在最终 `defaultPrevented` 为真时返回 false。`isTrusted` 区分真实输入和脚本合成事件——`element.click()` 这类程序化触发别指望获得真实输入的全部权限。

鼠标/滚轮/指针事件可读的字段：

```text
clientX clientY pageX pageY offsetX offsetY
movementX movementY button buttons
deltaX deltaY deltaMode
pointerId pointerType isPrimary
altKey shiftKey controlKey metaKey
```

键盘事件由 GLFW 输入生成，可监听但没有构造器：

```javascript
document.addEventListener("keydown", function (event) {
    if (event.controlKey && event.key === "s") event.preventDefault();
});
```

注意是 `controlKey` 不是 `ctrlKey`——从浏览器迁代码时这是个高频坑。字段：`key/code/keyCode/scanCode/repeat/altKey/shiftKey/controlKey/metaKey`。不同键盘布局下 `key` 由 GLFW 名称解析，可能是 `"Unidentified"`。

支持的事件类型：

```text
click dblclick contextmenu
mousedown mouseup mousemove mouseover mouseout mouseenter mouseleave
wheel scroll
pointerdown pointerup pointermove pointerover pointerout pointerenter pointerleave
keydown keyup
focus blur input beforeinput change submit reset formdata invalid select
copy cut paste compositionstart compositionupdate compositionend
DOMContentLoaded load unload resize
```

focus、blur、图片 load/error 不冒泡，别按冒泡事件处理。

## 表单

控件：form、input、textarea、select、option、button。

```javascript
input.value = "hello";
input.checked = true;
input.focus();
select.selectedIndex = 1;
input.setSelectionRange(0, 3);
```

支持的属性和方法包括：`value/defaultValue/checked/defaultChecked/disabled/name/type`、`multiple/required/readOnly/pattern/min/max/step`、`placeholder/accept/autocomplete/inputMode`、`selectionStart/selectionEnd/selectionDirection/setSelectionRange/setRangeText/select`、`valueAsNumber/stepUp/stepDown`、`focus()/blur()/click()`、约束校验（`validity/validationMessage/willValidate/checkValidity/reportValidity/setCustomValidity`）。select 另有 `options/selectedOptions/selectedIndex`，option 有 `selected`。`files` 是路径近似的 FileList，不是真 File 对象。不支持的 input type 会降级成普通文本框，也没有浏览器原生的日期/颜色/文件选择弹窗。

提交和重置：

```javascript
form.addEventListener("submit", function (e) {
    e.preventDefault();
    console.log(e.submitter);        // 触发提交的按钮
});
form.addEventListener("formdata", function (e) {
    console.log(e.formData.get("name"));
});
form.requestSubmit();   // 先跑约束校验，可传 submit 按钮
form.submit();          // 直接派发 submit
form.reset();           // 可取消，未取消则恢复默认值
```

AUI 不会因为 form 的 action 发 HTTP 请求。取消 submit 后不会再派 formdata。表单外控件可以用 `form="id"` 关联。

FormData：

```javascript
var data = new FormData(form);
data.append("tag", "aui");
data.get("tag");  data.getAll("tag");  data.set("page", "1");
data.has("page"); data.delete("page");
data.forEach(function (v, k) { ... });
```

`keys()/values()/entries()` 返回数组风格结果，不是 iterator。`toString()` 编成查询字符串，调试用。

## Storage 和 location

```javascript
localStorage.setItem("theme", "ore");
localStorage.getItem("theme");   // 还有 removeItem/clear/key/length
sessionStorage.setItem("draft", "text");
```

localStorage 持久化到 `config/apricityui/localStorage.nbt`，sessionStorage 只在本次客户端运行有效。空 key 被忽略；传 null 可能存成字符串 "null"；没有 storage 事件。

`window.location` 由资源路径生成，能读 `href/protocol/host/hostname/port/origin/pathname/search/hash/searchParams`。`assign/replace/reload` 是空操作，没有真实导航。要字符串形式就读 `href`，它没有自定义 toString。

URLSearchParams 只有这几个方法：`append / getAll / sort / forEach / toString`。没有 `get/set/delete/has/keys/values/entries`，别按标准用。

## fetch

```javascript
fetch("data.json").then(function (response) {
    console.log(response.ok, response.status, response.url);
    var text = response.text();
    var json = response.json();     // AUI 内置 JSON 解析
    var bytes = response.bytes();   // 字节数组副本
});
```

只有 `fetch(url)` 单参数——没有 init、没有 method/headers/body、没有 AbortController。相对路径按当前 Document 的 baseURI 解析；远程只走受限 HTTPS 管线（限制见[资源管理文档](resource-manager)）。

返回的不是标准 Promise：支持 `then(onOk, onErr)` 和 `catchError`，页面桥提供了 `catch` 别名，但别做复杂的链式变换。读 JSON 就在同一个 then 里调 `response.json()`。

## Observer

```javascript
var ro = new ResizeObserver(function (entries) {
    for (var i = 0; i < entries.length; i++) {
        console.log(entries[i].target, entries[i].contentRect.width);
    }
});
ro.observe(el);  ro.unobserve(el);  ro.disconnect();
```

entry 有 `target/contentRect/borderBoxSize/contentBoxSize`；contentRect 除了常规矩形字段还有 `borderBoxWidth/borderBoxHeight`。没有实际尺寸变化不会重复回调。

```javascript
var io = new IntersectionObserver(function (entries, observer) {
    for (var i = 0; i < entries.length; i++) {
        var entry = entries[i];
        if (entry.isIntersecting) console.log(entry.target, entry.intersectionRatio);
    }
}, {
    root: null,                         // null = Document 逻辑 viewport
    rootMargin: "20px 0px",
    threshold: [0, 0.5, 1]
});
io.observe(el);
io.unobserve(el);
io.takeRecords();
io.disconnect();
```

`IntersectionObserver` 的 entry 有 `target/time/rootBounds/boundingClientRect/intersectionRect/isIntersecting/intersectionRatio`。实例只读 `root/rootMargin/thresholds`，其中 rootMargin 会规范为四值，threshold 会排序去重。`root` 必须是同一 Document 的 Element 或 null；显式 root 在 overflow 裁剪时使用提交后的 padding clip（含滚动条 gutter），否则使用 border box。target 也基于提交后的 border box，并叠加实际 paint-list 中的祖先 overflow clip。rootMargin 只支持 1–4 个 `px` 或 `%` 值，所有百分比都按 root 宽度解析；非法 rootMargin 或 threshold 会抛错。

这是 V1 核心子集：没有 `trackVisibility`、`delay`、`scrollMargin`、跨 Document 观察、变换或 `clip-path` 的精确相交。`visibility:hidden` 不会自动视为不可相交；`display:none`、断连或失活 Document 不会产生可相交结果。

```javascript
var mo = new MutationObserver(function (records) { ... });
mo.observe(document.documentElement, {
    childList: true, attributes: true, characterData: true, subtree: true,
    attributeOldValue: true, characterDataOldValue: true,
    attributeFilter: ["class", "style"]
});
mo.takeRecords();  mo.disconnect();
```

record 可读 `type/target/addedNodes/removedNodes/previousSibling/nextSibling/attributeName/oldValue`。

三者都按文档帧批量派发，不是浏览器微任务时机。IntersectionObserver 在所有 Document 完成该轮布局和 paint-list 提交后统一收集，再调用回调；回调内的样式、滚动、unobserve 或 disconnect 影响下一轮。Document 刷新后观察器被清理，必须重新查询节点重新 observe。

## Canvas 和图像

```html
<canvas id="chart" width="320" height="160"></canvas>
<script>
    var ctx = document.getElementById("chart").getContext("2d");
    ctx.fillStyle = "#2f7d8c";
    ctx.fillRect(10, 10, 120, 40);
    ctx.font = "16px sans-serif";
    ctx.fillText("ApricityUI", 18, 36);
</script>
```

`getContext("2d")` 是唯一 context（没有 WebGL）。能力清单：rect 三件套、文本（`fillText/strokeText/measureText`）、路径（`beginPath/closePath/moveTo/lineTo/rect/roundRect/arc/arcTo/ellipse/quadraticCurveTo/bezierCurveTo/fill/stroke/clip/isPointInPath/isPointInStroke`）、变换（`save/restore/translate/rotate/scale/transform/setTransform/resetTransform`）、渐变和 pattern、ImageData 三件套、`drawImage`、`globalAlpha/globalCompositeOperation/filter/阴影`，导出 `toDataURL()` / `toBlob()`。toBlob 给出的 Blob 兼容对象有 `size/type/arrayBuffer()/text()/toDataURL()`。

底层是 Java2D，颜色、滤镜、合成效果可能和浏览器有出入。位图尺寸由 `width/height` 属性定（默认 300×150），CSS 只控制显示缩放——别只改 CSS 然后假设绘图坐标变了。

Path2D 支持路径方法（含 `addPath`）和 SVG path 字符串构造；DOMMatrix 是 2D 仿射矩阵（a~f 字段，`translateSelf/scaleSelf/rotateSelf/multiplySelf/invertSelf`）：

```javascript
var path = new Path2D("M0 0 L40 20 Z");
var m = new DOMMatrix();
m.translateSelf(10, 5).scaleSelf(2, 2);
ctx.setTransform(m);
ctx.stroke(path);
```

OffscreenCanvas 和 createImageBitmap：

```javascript
var off = new OffscreenCanvas(128, 64);
var bitmap = off.getContext("2d").transferToImageBitmap();  // 先画再转
bitmap.close();                                             // 用完可释放
var bmp = createImageBitmap(canvas);              // 同步
var cropped = createImageBitmap(canvas, 0, 0, 32, 32);  // 裁剪重载
createImageBitmapAsync(canvas).then(function (b) { ... });  // 异步版
```

## 音频

两种用法，都是浏览器的 HTMLAudioElement 语义：`<audio>` 元素和 `new Audio()` 工厂。

```html
<audio src="apricityui/sounds/click.ogg" controls autoplay></audio>
<script>
    var audio = new Audio("apricityui/sounds/bgm.ogg");  // 游离实例，不入 DOM 树
    audio.loop = true;
    audio.volume = 0.6;
    audio.addEventListener("ended", function () { console.log("播完"); });
    audio.play().then(function () { /* 已开播 */ })['catch'](function (err) { /* 加载失败 */ });
</script>
```

- **来源**：与图片/字体同一条资源链——开发环境 resource 目录、`<游戏目录>/apricity/`、classpath、资源包；`src` 相对路径按页面路径解析。不支持远程 URL。
- **格式**：OGG Vorbis、WAV/PCM（8/16-bit、单/双声道）。无 MP3、无 Web Audio API。
- **API**：`play()`（返回带 `then`/`['catch']` 的 Promise）、`pause()`、`load()`；`currentTime`（可读写，写 = seek）、`duration`（未就绪为 NaN）、`volume`（钳 0..1）、`muted`、`loop`、`paused`、`ended`、`seeking`、`readyState`（0→1→4，全量解码没有 2/3）、`networkState`（无 src 为 3）、`preload`（`none` 等到 play()/load()，`metadata`/`auto` 等价立即加载）、`autoplay`。
- **事件序列**：`loadstart → durationchange → loadedmetadata → canplay → canplaythrough → play → playing → timeupdate（250ms 节流）→ pause / ended`；失败派 `error`；seek 派 `seeking → seeked`。内联 `oncanplay` 等属性同样可用。
- **controls**：自绘控件条（播放/暂停键、进度条点击 + 拖动 seek、时间文本），UA 默认 240×28 可用 CSS 覆盖；无 `controls` 属性时 `display:none` 不占空间。
- **音量合成**：实际音量 = `volume × (muted ? 0 : 1) × MC 主音量`，跟随游戏声音设置实时变化。
- **生命周期**：文档关闭/刷新自动停止并释放全部音频（含 `new Audio()` 游离实例）；资源重载会停掉所有播放并清掉解码缓存。同路径共享解码结果，每个实例独立 channel，可叠加播放。
- **降级**：无声卡/OpenAL 初始化失败时播放派 `error` 事件而不是崩溃。

## 生命周期和刷新

```text
创建 Document -> loading -> 解析 HTML/CSS/JS -> interactive
  -> DOMContentLoaded -> complete -> load
  -> （宿主关闭）-> unload -> Document 移除
```

`document.refresh()` 或全局资源重载会重建页面：清掉旧 DOM、样式、脚本缓存，重跑脚本，重发 DOMContentLoaded/load。**刷新后旧的一切都不能用**：Element、集合、监听器、Observer 目标、Canvas surface。正确姿势是把初始化放进函数，每次 DOMContentLoaded 重绑：

```javascript
function installPage() {
    var button = document.getElementById("reloadable");
    if (button === null) return;
    button.addEventListener("click", function () { ... });
}
document.addEventListener("DOMContentLoaded", installPage);
```

别每帧调 `refresh()`——它重建整个页面，是重载手段，不是更新手段。

## AUI 特有行为

**script 双内容**：带 `src` 又写了内联代码的 `<script>`，两个都会执行（带警告）。别依赖浏览器"有 src 就忽略内联"的行为。

**日志前缀**：排查问题搜 `[AUI HTML]` / `[AUI CSS]` / `[AUI JS]` / `[AUI Fetch]` / `[AUI Canvas]` / `[AUI Event]`，一般带资源路径。页面里用 `console.log/debug/warn/error` 和 `console.time/timeEnd`。

**Top layer**：宿主侧 `setTopLayer(true)` 让弹窗、下拉菜单在当前 Document 内最后绘制、不被祖先 overflow 裁剪。只影响本 Document 内顺序，不会把一个 Document 抬到另一个之上。

**手动渲染**：宿主可以把 Document 设为手动渲染，之后它退出全局绘制和输入分发，由调用方自己画、自己转发事件。普通页面别用。

**扩展元素**：AUI 注册了 `<texture>`、`<sprite>`、`<translation>`、`<svg>`、`<canvas>`、`<container>` 等 Minecraft 向的标签，不是浏览器原生 HTML。见[扩展元素文档](extension-elements)。

**宿主**：页面 DOM API 不管创建宿主。Screen、Overlay、Container、WorldWindow 分别见各自文档。

## 性能建议

- 高频更新改 `textContent`、属性、class，别每帧 `innerHTML`；
- 大量 DOM 修改集中做一次，让框架合并样式和布局脏标记；
- Observer、定时器、监听器在关闭时主动清；
- 别缓存跨 `refresh()` 的任何 DOM 对象；
- 事件坐标直接用，别重复乘缩放；
- fetch、json()、Canvas 解码都要写错误处理。
