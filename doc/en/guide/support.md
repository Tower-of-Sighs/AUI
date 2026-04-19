### Support Status

Short version: ApricityUI tries to move toward the Web, but it is not a browser.

The best mental model is not "if a webpage can do it, ApricityUI can too", but "ApricityUI supports a practical subset that keeps becoming more Web-like".

If you write against full browser expectations, you will probably hit rough edges.

---

### Remember This First

The default layout model is not browser document flow. It is closer to `flex`, and it is generally safer to think in terms of a vertical main axis by default.

So if you are used to dropping a `div` into a browser page and letting it stack naturally, do not rely on that here. Write layout intentionally.

---

### Registered Tags

These are the most commonly used tags:

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

Minecraft-specific tags:

- `container`
- `slot`
- `recipe`
- `translation`

There is also a group of tags that do not have browser-level built-in logic, but `global.css` already provides reasonable default styles for them:

- `p`
- `h1` to `h6`
- `small`
- `b` `strong`
- `i` `em`
- `mark`
- `sub` `sup`
- `code` `kbd`
- `hr`
- `blockquote`

---

### CSS Selectors

These common selectors are practically supported:

- Tag selectors
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
- Descendant selector via space
- Child selector `>`
- Multiple selectors `,`

That is enough for most regular interfaces.

---

### CSS Properties

Common layout properties are already usable:

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

Common and relatively reliable groups include:

- Layout: `display` `flex-*` `grid-template-*` `grid-row` `grid-column`
- Size: `width` `height` `min/max-*`
- Box model: `margin*` `padding*` `border*` `border-radius` `border-image`
- Positioning: `position` `top` `right` `bottom` `left` `z-index`
- Background: `background-*`
- Text: `color` `font-size` `font-family` `font-weight` `font-style` `line-height` `text-stroke`
- Visual: `opacity` `box-shadow` `transform` `clip-path` `filter` `backdrop-filter`
- Interaction: `cursor` `pointer-events` `visibility` `user-select`
- Animation: `transition` `animation` `@keyframes`
- Fonts: `@font-face`
- Variables: `--*`

If you use an unsupported property, the usual outcome is not an error. It simply does nothing.

So when debugging styles, do not assume the property is implemented just because the syntax is correct.

---

### What `global.css` Gives You

The built-in `global.css` already provides some foundation:

1. Basic styling for common text tags such as `p`, `h1-h6`, `blockquote`, `code`, `kbd`, and `pre`
2. Basic styling for form tags such as `input` and `select`
3. Default structural styling for `container`, `slot`, and `recipe`
4. A set of slot-related CSS variables

So you are not starting from a blank sheet, but you also should not treat it like a full browser UA stylesheet.

---

### Slot-Related Variables

These variables are useful in `slot` and `container` scenarios:

- `--aui-slot-size`
- `--aui-slot-render-bg`
- `--aui-slot-render-item`
- `--aui-slot-icon-scale`
- `--aui-slot-padding`
- `--aui-slot-z`
- `--aui-slot-interactive`
- `--aui-slot-cycle`
- `--aui-slot-cycle-interval`
- `--aui-container-columns`

If you are building container UI, these are worth memorizing.

---

### How Far JS Support Goes

Without KubeJS, HTML and CSS still work, but scripting and devtools are significantly reduced.

With KubeJS installed, the commonly useful interfaces include:

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

Reliable interfaces include:

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

Do not treat it like a complete browser:

- Do not expect a full BOM
- Do not expect `fetch`
- Do not expect many modern Web APIs

There are also in-world window interfaces on the client KJS side:

- `ApricityUI.createInWorldDocument()`
- `ApricityUI.createWorldWindow()`
- `ApricityUI.createFollowFacingWorldWindow()`
- `ApricityUI.removeWorldWindow()`
- `ApricityUI.clearWorldWindows()`

These are very convenient for client-side visualization scripts.

---

### Unified Java Entry Points

If you are developing a mod and do not want to remember separate APIs for `Document`, networking, and `WorldWindow`, you can now mostly go through `ApricityUI` directly.

Common ones include:

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

That helps keep the JS-side and Java-side documentation aligned.

---

### Current Semantics Of A Few Special Tags

Only the essentials here. For full details, see the dedicated sections.

1. `slot` is unified into one tag. Inside containers it defaults to `bound`, outside containers it defaults to `virtual`.
2. Virtual items should now come from `innerText`; old attribute-based forms are no longer recommended.
3. `recipe` now uses `<recipe type="..."></recipe>`, with the recipe id read from `innerText`, not `recipe-id`.
4. `translation` uses `innerText` as the translation key.
5. `sprite` uses the `src + steps + duration + direction` style of parameters. Do not force old image-slicing workflows into it.

---

### Common Interfaces For Most Interactions

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
