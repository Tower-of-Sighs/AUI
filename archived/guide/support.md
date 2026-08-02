### 支持情况

先说结论：晴雪UI确实尽量往 Web 靠，但它不是浏览器。

所以最好的理解方式不是“网页能干啥它就能干啥”，而是“它支持一套很好用、而且越来越像 Web 的子集”。

如果你照着浏览器全家桶去写，那大概率会踩坑。

---

### 先记住这一条

本框架默认布局不是浏览器那套文档流，而是更接近 `flex`，而且默认主轴思路基本按纵向来理解更稳妥。

所以你在浏览器里写惯了“一个 div 丢进去先自然往下排”，到了这里最好还是主动写布局，不要偷懒。

---

### 已注册标签

日常最常用的是这些：

- `body`
- `div`
- `span`
- `pre`
- `img`
- `a`
- `input`
- `textarea`
- `select`
- `option`
- `sprite`

Minecraft 专属标签是这些：

- `container`
- `slot`
- `recipe`
- `translation`
- `texture`

另外还有一批标签虽然没做浏览器级别专门逻辑，但 `global.css` 已经给了默认样式，平时可以直接用：

- `p`
- `h1` 到 `h6`
- `small`
- `b` `strong`
- `i` `em`
- `mark`
- `sub` `sup`
- `code` `kbd`
- `hr`
- `blockquote`

---

### CSS 选择器

常用的这些都支持得比较实用：

- 标签选择器
- `.class`
- `#id`
- `[attr]`
- `[attr=value]`
- `:first-child`
- `:last-child`
- `:nth-child()`
- `:hover`
- `:active`
- `:focus`
- `:empty`
- `:checked`
- 后代选择器空格
- 子代选择器 `>`
- 多选择器 `,`

已经够写大部分常规界面了。

---

### CSS 属性

常用布局属性基本都能覆盖到：

```css
.panel {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    width: 180px;
    height: 96px;
    padding: 8px;
    margin: 4px;
    position: relative;
    top: 4px;
    left: 4px;
}
```

比较常用、也比较靠谱的范围大概有这些：

- 布局：`display` `flex-*` `grid-template-*` `grid-row` `grid-column`
- 尺寸：`width` `height` `min/max-*`
- 盒模型：`margin*` `padding*` `border*` `border-radius` `border-image`
- 位置：`position` `top` `right` `bottom` `left` `z-index`
- 背景：`background-*`
- 文本：`color` `font-size` `font-family` `font-weight` `font-style` `line-height` `text-stroke`
- 视觉：`opacity` `box-shadow` `transform` `clip-path` `filter` `backdrop-filter`
- 交互：`cursor` `pointer-events` `visibility` `user-select`
- 动画：`transition` `animation` `@keyframes`
- 字体：`@font-face`
- 变量：`--*`

如果你写了没实现的属性，最常见的结果不是报错，而是安静地没效果。

所以调样式的时候，别默认自己写对了，先怀疑一下“这个属性到底支不支持”。

---

### global.css 给了你什么

内置的 `global.css` 已经帮你做了一些基础兜底，比如：

1. `p`、`h1-h6`、`blockquote`、`code`、`kbd`、`pre` 这些常用文本标签的基础样式。
2. `input`、`select` 这类表单标签的基本表现。
3. `container`、`slot`、`recipe` 的默认样式骨架。
4. 一组槽位相关 CSS 变量。

也就是说，你不用从纯白纸开始写，但也别把它当浏览器 UA 样式那样万能。

---

### 槽位相关变量

这些变量在 `slot` 和 `container` 场景里很好用：

- `--aui-slot-size`
- `--aui-slot-icon-scale`
- `--aui-slot-z`
- `--aui-slot-interactive`（`tooltip`、`slot`、`none` 或 `tooltip slot`）
- `--aui-slot-cycle`
- `--aui-slot-cycle-interval`
- `--aui-container-columns`

如果你在写容器 UI，这组变量建议直接记住。

---

### slot 物品渲染

`slot` 的视觉模式统一由 `render` 属性控制：

```html
<slot render="all">minecraft:diamond</slot>
<slot render="bg">minecraft:diamond</slot>
<slot render="item">minecraft:diamond</slot>
<slot render="none">minecraft:diamond</slot>
```

- `all`：背景和物品都绘制；缺省、空值或非法值也按 `all`。
- `bg`：只绘制 slot 背景。
- `item`：只绘制物品模型和装饰。
- `none`：背景和物品都不绘制。
- 物品 tooltip 与菜单取放由 `interactive` 能力独立控制，`render` 只影响可见性。

标准 Item 和 BlockItem 会通过 AUI 的内容渲染节点提交模型顶点；展示槽位在主菜单、没有存档或 `Minecraft.level == null`
时也可以解析标准模型。最终模型要求自定义 renderer 时，AUI 会通过 Forge BEWLR 在同一 ItemDrawer 路径中受控绘制；动态标准模型只做帧内
mesh 缓存，避免跨帧陈旧 quad。模型解析或第三方 renderer 实际抛错时不会回退到原版 `renderItem`，而会显示紫黑棋盘格并记录一次诊断。Forge
`ItemDecorator` 扩展同样不会执行，因为它依赖原版 `GuiGraphics` 物品装饰路径。

Tooltip 仍由 Minecraft 绘制。旧的 `--aui-slot-render-bg`、`--aui-slot-render-item`、`render-bg` 和 `render-item`
已移除，这是保持版本号不变的破坏性更新。

---

### JS 支持到什么程度

如果没装 KubeJS，HTML 和 CSS 还能正常用，但脚本和调试台能力会明显缩水。

装了 KubeJS 以后，日常够用的接口大概是这些：

```javascript
let title = document.querySelector(".title")
let items = document.querySelectorAll("slot")

let badge = document.createElement("div")
badge.innerText = "Hello"
badge.setAttribute("class", "badge")
document.body.append(badge)

badge.addEventListener("mousedown", e => {
    badge.setAttribute("data-state", "active")
})

window.setTimeout(() => {
    badge.remove()
}, 1000)
```

比较稳的接口包括：

- `document.querySelector()`
- `document.querySelectorAll()`
- `document.createElement()`
- `element.append()`
- `element.prepend()`
- `element.remove()`
- `element.getAttribute()`
- `element.setAttribute()`
- `element.removeAttribute()`
- `element.innerText`
- `element.value`
- `addEventListener()`
- `window.setTimeout()`
- `window.setInterval()`
- `window.localStorage`

不要把它当完整浏览器环境去写：

- 复杂 BOM 不要指望。
- `fetch` 不要指望。
- 很多现代 Web API 不要指望。

顺手一提，现在客户端 KJS 绑定里也已经补上了世界内窗口接口：

- `ApricityUI.createInWorldDocument()`
- `ApricityUI.createWorldWindow()`
- `ApricityUI.createFollowFacingWorldWindow()`
- `ApricityUI.removeWorldWindow()`
- `ApricityUI.clearWorldWindows()`

如果你在写客户端可视化脚本，这几条会很顺手。

---

### Java 侧统一入口

如果你是模组开发者，不想一会儿记 `Document`，一会儿记网络处理器，一会儿记 `WorldWindow`，现在也可以直接从主类 `ApricityUI` 走统一入口。

常用的有这些：

- `ApricityUI.createDocument()`
- `ApricityUI.createInWorldDocument()`
- `ApricityUI.removeDocument()`
- `ApricityUI.openScreen()`
- `ApricityUI.closeScreen()`
- `ApricityUI.bind()`
- `ApricityUI.createWorldWindow()`
- `ApricityUI.createFollowFacingWorldWindow()`
- `ApricityUI.removeWorldWindow()`
- `ApricityUI.clearWorldWindows()`

这样文档里的示例无论写 JS 还是写 Java，思路都能尽量统一。

---

### 几个专属标签的当前语义

这里只点重点，更完整的说明请看对应章节。

1. `slot` 现在统一用一个标签，容器内默认 bound，容器外默认 virtual。
2. `slot` 使用 `render="all|bg|item|none"` 控制视觉层，旧 render flag 与 CSS 变量不再支持。
3. virtual 物品优先读 innerText，不再推荐旧属性写法。
4. `recipe` 现在是 `<recipe type="..."></recipe>` 这种风格，配方 id 读 innerText，不再读 `recipe-id`。
5. `translation` 的 innerText 就是翻译 key。
6. `sprite` 用 `src + steps + duration + direction` 这一套参数，别再用旧时代切图思路硬塞浏览器插件写法。
7. `texture` 的 `src` 是 Minecraft `ResourceLocation`（例如 `minecraft:textures/gui/icons.png`），它不走 `img` 的相对路径或网络图片加载流程。

---

### 常用接口，够你写大部分交互了

#### Element

```javascript
let panel = document.createElement("div")
panel.setAttribute("class", "panel")
panel.innerText = "example"

document.body.append(panel)
panel.prepend(document.createElement("span"))

let cls = panel.getAttribute("class")
panel.removeAttribute("class")
panel.remove()
```

#### Document

```javascript
let el = document.querySelector(".panel")
let all = document.querySelectorAll("div")
let node = document.createElement("span")
document.body.append(node)
```

#### Window

```javascript
window.localStorage.localStorage.putString("theme", "dark")

window.setTimeout(() => {
    // later
}, 500)

window.setInterval(() => {
    // repeat
}, 1000)
```

---

### 回归测试页入口

框架内已经放了一组独立的手工回归页，统一放在：

- `src/main/resources/assets/apricityui/apricity/tests/`

现在可以直接从下面这个入口页开始点：

- `src/main/resources/assets/apricityui/apricity/tests/index.html`

里面已经把现有测试页按主题归了类，当前重点包括：

- 生命周期回归：`lifecycle-event-test.html`
- 交互控件回归：`interactive-controls-test.html`
- 扩展鼠标事件回归：`mouse-extended-event-test.html`
- Pointer 兼容事件回归：`pointer-event-test.html`
- 图片资源事件回归：`img-load-event-test.html`
- 滚轮 / 滚动事件回归：`wheel-scroll-event-test.html`
- 布局与文本回归：`layout-*`、`text-*`
- Canvas 与专用标签回归：`canvas-*`、`container-slot-recipe-test.html`
- 世界内窗口验收页：`world-window-acceptance.html`

建议后面每补一类事件或生命周期语义，都顺手加一个独立回归页，不要只靠示例页手点。

---
