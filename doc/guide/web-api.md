# Web API 接口总览

本文档说明 ApricityUI 当前直接提供给页面脚本使用的 Web 风格接口。

说明范围：

- 仅覆盖页面内可直接使用的 JS 接口
- 以当前实现为准，不按浏览器标准做超出实现范围的承诺
- 优先说明“能用什么”，并明确当前限制

## 总体说明

ApricityUI 会在页面脚本执行前注入一层浏览器兼容包装，因此页面里可以直接写：

```js
document.querySelector(...)
window.setTimeout(...)
localStorage.setItem(...)
fetch("data.json").then(...)
new ResizeObserver(...)
new MutationObserver(...)
```

当前接口分成两类：

- 浏览器风格包装接口：`window`、`document`、`Element`、`Event`、`fetch`、`localStorage` 等
- 轻量实现接口：`ResizeObserver`、`MutationObserver`、`FormData`、`URLSearchParams`、`location`

## 全局对象

页面脚本默认可直接使用以下全局对象或构造器：

- `window`
- `document`
- `console`
- `localStorage`
- `sessionStorage`
- `performance`
- `fetch`
- `getComputedStyle`
- `requestAnimationFrame`
- `cancelAnimationFrame`
- `setTimeout`
- `clearTimeout`
- `setInterval`
- `clearInterval`
- `Event`
- `CustomEvent`
- `MouseEvent`
- `ResizeObserver`
- `MutationObserver`
- `URLSearchParams`
- `FormData`

## window

### 属性

- `window.innerWidth`
- `window.innerHeight`
- `window.devicePixelRatio`
- `window.location`

### 事件

- `window.addEventListener(type, listener)`
- `window.addEventListener(type, listener, useCapture)`
- `window.removeEventListener(type, listener)`
- `window.removeEventListener(type, listener, useCapture)`
- `window.dispatchEvent(event)`

当前 `window` 侧事件支持轻量实现，已知最常见的是：

- `resize`

### 定时器与帧调度

- `window.requestAnimationFrame(callback)`
- `window.cancelAnimationFrame(id)`
- `window.setTimeout(callback, delay)`
- `window.clearTimeout(handle)`
- `window.setInterval(callback, delay)`
- `window.clearInterval(handle)`

说明：

- `requestAnimationFrame` 当前通过客户端调度器模拟，目标频率约为 16ms 一次
- `setInterval` / `setTimeout` 由客户端调度器驱动，不是浏览器原生事件循环

### 视口滚动

- `window.scrollTo(x, y)`
- `window.scrollTo({ left, top })`
- `window.scrollBy(x, y)`
- `window.scrollBy({ left, top })`

当前会转发到 `document` / `body` 的滚动模型。

### 样式

- `window.getComputedStyle(element)`

返回对象支持：

- `style.getPropertyValue(name)`
- `style.get(name)`

当前是轻量只读实现，主要用于读取当前计算后的样式值。

### 网络

- `window.fetch(url)`

也可以直接用全局 `fetch(url)`。

## document

### 常用属性

- `document.readyState`
- `document.activeElement`
- `document.location`
- `document.body`

说明：

- `readyState` 当前会经历 `loading -> interactive -> complete`
- 生命周期事件在页面脚本执行后触发

### 查询与创建

- `document.querySelector(selector)`
- `document.querySelectorAll(selector)`
- `document.getElementById(id)`
- `document.getElementsByClassName(name)`
- `document.getElementsByTagName(name)`
- `document.getElementsByName(name)`
- `document.createElement(tagName)`
- `document.createTextNode(text)`

说明：

- `querySelectorAll` / `getElementsBy*` 返回的是 JS 数组风格结果，不是原生 `NodeList` / `HTMLCollection`
- `createTextNode` 当前返回的是框架内部的文本节点近似实现，不是完整浏览器 Text 节点模型

### DOM 操作

- `document.appendChild(child)`
- `document.append(...nodesOrText)`
- `document.prepend(...nodesOrText)`

### 事件

- `document.addEventListener(type, listener)`
- `document.addEventListener(type, listener, useCapture)`
- `document.removeEventListener(type, listener)`
- `document.removeEventListener(type, listener, useCapture)`
- `document.dispatchEvent(event)`

当前常用生命周期事件：

- `DOMContentLoaded`
- `load`

### 滚动

- `document.scrollTo(x, y)`
- `document.scrollBy(x, y)`

## Element

页面里通过查询、创建、事件回调拿到的节点，都会经过一层 Element 装饰，提供更接近浏览器的属性和方法。

### 常用属性

- `el.textContent`
- `el.innerHTML`
- `el.outerHTML`
- `el.className`
- `el.classList`
- `el.dataset`
- `el.value`
- `el.checked`
- `el.selectedIndex`
- `el.scrollTop`
- `el.scrollLeft`
- `el.children`
- `el.childNodes`
- `el.options`
- `el.selectedOptions`
- `el.firstElementChild`
- `el.lastElementChild`
- `el.nextElementSibling`
- `el.previousElementSibling`
- `el.parentElement`

说明：

- `children` / `childNodes` / `options` / `selectedOptions` 返回 JS 数组
- `childNodes` 当前仍然是元素风格结果，不区分完整浏览器 Node 类型层次
- `options` / `selectedOptions` 主要为 `select` 提供

### 查询

- `el.querySelector(selector)`
- `el.querySelectorAll(selector)`
- `el.getElementsByClassName(name)`
- `el.getElementsByTagName(name)`
- `el.getElementsByName(name)`
- `el.closest(selector)`
- `el.matches(selector)`
- `el.contains(node)`

### DOM 增删改

- `el.appendChild(child)`
- `el.insertBefore(child, ref)`
- `el.removeChild(child)`
- `el.append(...nodesOrText)`
- `el.prepend(...nodesOrText)`
- `el.before(...nodesOrText)`
- `el.after(...nodesOrText)`
- `el.replaceWith(...nodesOrText)`
- `el.remove()`

说明：

- `append` / `prepend` / `before` / `after` / `replaceWith` 支持字符串、数字、布尔值，会自动转成文本节点近似实现

### 表单与交互

- `el.focus()`
- `el.blur()`
- `el.click()`

相关字段：

- `el.value`
- `el.checked`
- `el.selectedIndex`

当前已覆盖的常见元素场景：

- `input`
- `textarea`
- `select`
- `option`
- `checkbox`
- `radio`

### 滚动与布局

- `el.scrollTo(x, y)`
- `el.scrollTo({ left, top })`
- `el.scrollBy(x, y)`
- `el.scrollBy({ left, top })`
- `el.getBoundingClientRect()`

`getBoundingClientRect()` 返回轻量对象，包含：

- `x`
- `y`
- `width`
- `height`
- `left`
- `top`
- `right`
- `bottom`

### 事件

- `el.addEventListener(type, listener)`
- `el.addEventListener(type, listener, useCapture)`
- `el.removeEventListener(type, listener, useCapture)`
- `el.dispatchEvent(event)`

### 原始属性访问

除浏览器风格包装外，元素对象仍可直接调用底层接口：

- `el.getAttribute(name)`
- `el.setAttribute(name, value)`
- `el.removeAttribute(name)`
- `el.hasAttribute(name)`

这部分对调试很有用，也是目前很多测试页最常用的写法。

## classList

`el.classList` 当前提供轻量 `DOMTokenList` 风格支持：

- `classList.length`
- `classList.contains(token)`
- `classList.add(...tokens)`
- `classList.remove(...tokens)`
- `classList.toggle(token)`
- `classList.toggle(token, force)`
- `classList.item(index)`
- `classList.toString()`

## dataset

`el.dataset` 当前提供轻量 `DOMStringMap` 风格支持：

- 属性读写：`el.dataset.foo = "bar"`
- 方法读写：`el.dataset.set("foo", "bar")`
- 读取：`el.dataset.get("foo")`
- 判定：`el.dataset.has("foo")`
- 删除：`el.dataset.delete("foo")`
- 枚举键：`el.dataset.keys()`

说明：

- 会在 `data-foo-bar` 与 `dataset.fooBar` 间做转换
- 当前是轻量实现，不提供完整浏览器枚举行为

## Event / CustomEvent / MouseEvent

### Event

```js
let e = new Event(type, { bubbles: true });
```

当前支持的常见字段：

- `event.type`
- `event.target`
- `event.currentTarget`
- `event.bubbles`
- `event.cancelable`
- `event.defaultPrevented`

支持的方法：

- `event.stopPropagation()`
- `event.preventDefault()`

### CustomEvent

```js
let e = new CustomEvent(type, {
  detail: value,
  bubbles: true
});
```

额外支持：

- `event.detail`

### MouseEvent

```js
let e = new MouseEvent(type, {
  clientX: 10,
  clientY: 20,
  button: 0
});
```

当前支持字段：

- `clientX`
- `clientY`
- `pageX`
- `pageY`
- `button`
- `bubbles`

## localStorage / sessionStorage

### localStorage

支持：

- `localStorage.getItem(key)`
- `localStorage.setItem(key, value)`
- `localStorage.removeItem(key)`
- `localStorage.clear()`
- `localStorage.key(index)`
- `localStorage.length`

实现特性：

- 值按字符串存储
- `localStorage` 会持久化到本地配置目录

### sessionStorage

支持：

- `sessionStorage.getItem(key)`
- `sessionStorage.setItem(key, value)`
- `sessionStorage.removeItem(key)`
- `sessionStorage.clear()`
- `sessionStorage.key(index)`
- `sessionStorage.length`

实现特性：

- 仅当前运行期内有效
- 不落盘

## console

当前支持：

- `console.log(value)`
- `console.warn(value)`
- `console.error(value)`
- `console.debug(value)`
- `console.time(label)`
- `console.timeEnd(label)`

说明：

- `console.debug` 当前等价于 `console.log`
- 输出会进入模组日志

## performance

当前支持：

- `performance.now()`

## fetch

当前支持最简形式：

```js
fetch("path/to/file.json")
  .then(function (resp) {
    return resp.text();
  })
  ["catch"](function (err) {
    console.error(err);
  });
```

### 调用形式

- `fetch(url)`

当前限制：

- 仅支持单参数 `url`
- 不支持 `fetch(url, init)`
- 不支持自定义 method、headers、body

### Promise 风格对象

返回对象支持：

- `promise.then(onFulfilled)`
- `promise.then(onFulfilled, onRejected)`
- `promise.catchError(onRejected)`
- `promise["catch"](onRejected)`

### Response 对象

支持属性：

- `resp.ok`
- `resp.status`
- `resp.url`

支持方法：

- `resp.text()`
- `resp.json()`
- `resp.bytes()`

说明：

- 当前主要用于读取本地资源或远程资源
- 当前实现可视作轻量 GET

## location

当前可用：

- `window.location`
- `document.location`

支持字段：

- `href`
- `protocol`
- `host`
- `hostname`
- `port`
- `origin`
- `pathname`
- `search`
- `hash`
- `searchParams`

支持方法：

- `toString()`
- `assign()`
- `replace()`
- `reload()`

说明：

- 当前 `assign` / `replace` / `reload` 仅占位，不执行真实导航
- `location` 主要用于读取当前路径与查询参数

## URLSearchParams

当前支持：

- `new URLSearchParams(stringOrObjectOrArray)`
- `append(key, value)`
- `delete(key)`
- `get(key)`
- `getAll(key)`
- `has(key)`
- `set(key, value)`
- `sort()`
- `keys()`
- `values()`
- `entries()`
- `forEach(callback, thisArg)`
- `toString()`

## FormData

当前支持：

- `new FormData()`
- `new FormData(formElement)`
- `append(key, value)`
- `delete(key)`
- `get(key)`
- `getAll(key)`
- `has(key)`
- `set(key, value)`
- `keys()`
- `values()`
- `entries()`
- `forEach(callback, thisArg)`
- `toString()`

从表单构造时，当前会读取：

- `input`
- `textarea`
- `select`

已处理的常见规则：

- `disabled` 字段跳过
- `checkbox` / `radio` 仅在选中时收集
- `select` 会优先收集 `selectedOptions`

当前限制：

- 不支持文件上传
- 不与 `fetch(url, init)` 自动集成

## ResizeObserver

当前支持：

```js
let ro = new ResizeObserver(function (entries, observer) {
  console.log(entries);
});

ro.observe(element);
ro.unobserve(element);
ro.disconnect();
```

### entry 字段

- `entry.target`
- `entry.contentRect`
- `entry.borderBoxSize`
- `entry.contentBoxSize`

`contentRect` 当前包含：

- `x`
- `y`
- `left`
- `top`
- `width`
- `height`
- `right`
- `bottom`
- `borderBoxWidth`
- `borderBoxHeight`

实现说明：

- 当前为轻量实现
- 由文档帧更新驱动
- 主要用于监听元素尺寸变化，不保证完全对齐浏览器标准

## MutationObserver

当前支持：

```js
let mo = new MutationObserver(function (records, observer) {
  console.log(records);
});

mo.observe(target, {
  childList: true,
  attributes: true,
  characterData: true,
  subtree: true,
  attributeOldValue: true,
  characterDataOldValue: true,
  attributeFilter: ["class", "style"]
});

mo.takeRecords();
mo.disconnect();
```

### record 字段

- `record.type`
- `record.target`
- `record.addedNodes`
- `record.removedNodes`
- `record.previousSibling`
- `record.nextSibling`
- `record.attributeName`
- `record.oldValue`

### 当前已覆盖的变更来源

- DOM 子节点增删移动
- `setAttribute`
- `removeAttribute`
- `textContent` 变更

实现说明：

- 当前为轻量实现
- 按文档帧批量派发，不是浏览器那种微任务时机
- 更适合调试、状态同步、开发工具场景

## 返回值与集合的兼容差异

以下差异需要特别注意：

- `querySelectorAll` / `children` / `childNodes` / `getElementsBy*` 返回 JS 数组，不是浏览器原生集合对象
- 文本节点不是完整浏览器 `Text` / `Node` 模型
- 一些对象是“可用但不完整”的轻量实现，例如 `location`、`fetch`、`ResizeObserver`、`MutationObserver`

## 当前未提供或仅部分提供的能力

截至当前实现，以下能力要么没有，要么不是完整浏览器语义：

- 真实页面导航
- `fetch(url, init)` 完整选项
- `Promise` 标准链式语义的完整兼容扩展
- 完整 `Node` / `Text` / `Comment` / `DocumentFragment` 模型
- 完整 `HTMLCollection` / `NodeList` 行为
- `window.history`
- `matchMedia`
- `postMessage`
- `WebSocket`
- `XMLHttpRequest`
- `MutationObserver` 微任务级调度

## 使用建议

- 日常页面逻辑优先用：`querySelector`、`setAttribute`、`classList`、`dataset`、`addEventListener`
- 要做资源读取，优先用：`fetch(url).then(resp => resp.text()/json())`
- 要做表单收集，优先用：`new FormData(form)`
- 要做响应式尺寸监听，可用：`ResizeObserver`
- 要做开发工具、编辑器、属性同步，可用：`MutationObserver`

如果你要扩展新的浏览器接口，建议同时查看以下实现文件：

- `src/main/java/com/sighs/apricityui/init/Document.java`
- `src/main/java/com/sighs/apricityui/init/Window.java`
- `src/main/java/com/sighs/apricityui/init/Element.java`
- `src/main/java/com/sighs/apricityui/init/Event.java`
