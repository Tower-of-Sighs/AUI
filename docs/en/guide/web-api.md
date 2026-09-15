# ApricityUI Web API

AUI is not Chromium and has no browser engine. Page JavaScript runs on Rhino, while the Java-side Document, Element, events, and resource pipeline are bridged into browser-style objects. So the APIs here fall into three categories:

- **Available**: use them directly as shown in the examples below;
- **Lightweight compatibility**: same names and common call patterns as the browser, but with reduced return values, timing, or parameter ranges;
- **Not provided**: not implemented — don't assume they exist.

For CSS properties and layout, see [HTML/CSS Coverage](html-css-coverage); for page-level viewport/font/mouse meta configuration, see the [ApricityScreen documentation](apricity-screen#page-meta-configuration).

## Quick Start

```html
<button id="load" type="button">Load Data</button>
<pre id="output"></pre>
<script>
    var output = document.getElementById("output");
    document.getElementById("load").addEventListener("click", function () {
        fetch("data.json").then(function (response) {
            output.textContent = response.ok
                ? JSON.stringify(response.json())
                : "HTTP status: " + response.status;
        }, function (error) {
            output.textContent = "Load failed";
        });
    });
</script>
```

Page scripts should stick to `var`, plain `function`, and traditional loops. AUI performs compatibility transforms for some modern syntax, but Rhino is not a modern JS engine — don't bet on it.

## Global Objects

Each Document has its own `document`, and they share one window compatibility object. Injected globals:

| Global | Status | Notes |
| --- | --- | --- |
| window / document / console | Available | console writes to the AUI log |
| localStorage / sessionStorage | Available | see the Storage section below |
| performance | Lightweight | only `now()` |
| fetch | Lightweight | restricted GET, see below |
| getComputedStyle | Lightweight | read-only |
| setTimeout / setInterval / requestAnimationFrame | Lightweight | driven by the client scheduler |
| Event / CustomEvent / MouseEvent / WheelEvent / PointerEvent | Available | constructors read a limited set of fields |
| URLSearchParams / FormData | Lightweight | smaller method sets than the standard |
| ResizeObserver / IntersectionObserver / MutationObserver | Lightweight | dispatched per document frame, not at microtask timing |
| DOMMatrix / Path2D / OffscreenCanvas / createImageBitmap | Lightweight | see the Canvas section |

**Not provided**: KeyboardEvent constructor, navigator.clipboard, Selection/Range, history, matchMedia, XMLHttpRequest, WebSocket, WebGL, Service Worker, full Promise, AbortController, Shadow DOM, iframe/postMessage. Text selection and copy is AUI's own implementation — don't write code against Selection/Range.

## Window

```javascript
window.innerWidth          // Document logical viewport size
window.innerHeight
window.devicePixelRatio    // Minecraft GUI scale
```

The `clientX/clientY` in events are already logical coordinates — do not multiply them by devicePixelRatio or renderScale again.

**Events**: `addEventListener(type, fn)` / `removeEventListener` / `dispatchEvent`. The third parameter is only treated as a boolean capture flag; the `{passive, signal}` options object is not implemented. AUI internally supports an extra fourth parameter for once: `addEventListener("custom", fn, false, true)`.

**Timers**:

```javascript
var id = setTimeout(fn, 100);        clearTimeout(id);
var id = setInterval(fn, 1000);      clearInterval(id);
var id = requestAnimationFrame(fn);  cancelAnimationFrame(id);
```

The rAF target interval is about 16ms, but browser frame timing is not guaranteed. After a Document is removed, any timer handles you saved must be cleared by yourself.

**Scrolling**: `window.scrollTo(x, y)` / `scrollTo({left, top})` / `scrollBy(...)`, proxied to the root scrolling model.

**getComputedStyle** is a read-only lightweight object:

```javascript
var style = getComputedStyle(el);
style.getPropertyValue("display");
style.get("font-size");   // equivalent
style.fontSize;           // common fields can also be read directly (fontSize/fontWeight/fontFamily/lineHeight/display/color, etc.)
```

Use the live, bidirectionally bound `element.style` object or `setAttribute("style", ...)` to change styles.

## Document

Properties: `readyState` (loading → interactive → complete), `activeElement` (falls back to body when nothing is focused), `body`, `head`, `documentElement`, `location`, `URL` / `documentURI` / `baseURI` (all logical paths, not browser address bar URLs).

Queries:

```javascript
document.querySelector("#panel");
document.querySelectorAll("button.action");
document.getElementById("panel");
document.getElementsByClassName("card");
document.getElementsByTagName("img");
document.getElementsByName("query");
```

These return array-style snapshots, not live NodeList/HTMLCollection objects. Iterate with `list.length` + indexing; some collections have `item()` / `namedItem()`.

Creating nodes: `createElement` / `createTextNode` / `createComment` / `createDocumentFragment`. There is no Shadow DOM.

Tree operations: `appendChild` / `append` / `prepend`; `append`/`prepend` accept simple values like strings and numbers (converted to text nodes). Document also supports event listeners and `scrollTo` / `scrollBy`.

## Node and Element

**Node**: `nodeType` (element 1, text 3, comment 8, Fragment 11), `nodeName`, `nodeValue`, `textContent`, `parentNode`, `childNodes`, `firstChild`/`lastChild`, `nextSibling`/`previousSibling`, `ownerDocument`, `isConnected`; methods `appendChild` / `removeChild` / `insertBefore` / `replaceChild` / `cloneNode(deep)` / `before` / `after` / `replaceWith` / `remove` / `contains` / `hasChildNodes`.

**Content and attributes**:

```javascript
el.textContent = "<not html>";     // plain text
el.innerHTML = "<span>x</span>";   // re-parsed; the old subtree becomes invalid
el.outerHTML = "<section>...</section>";
el.getAttribute("data-state");
el.setAttribute("data-state", "ready");
el.removeAttribute("hidden");
el.hasAttribute("disabled");
el.toggleAttribute("hidden", true);
```

`textContent` returns the raw text in the DOM subtree. `innerText` returns rendered text and accounts for `display:none`, `visibility`, `white-space`, `text-transform`, block boundaries, `<br>`, and table separators. Assigning `innerText` converts line breaks to `<br>` elements, while assigning `textContent` stores one plain text value. Serialized text and attributes are escaped; use `innerHTML` only when markup must be parsed.

**Queries and relationships**: Element has the same query methods as Document, plus `matches` / `closest` / `contains` / `children` / `firstElementChild` / `lastElementChild` / `nextElementSibling` / `previousElementSibling` / `parentElement`. There is no `insertAdjacentHTML` and no `element.animate()`.

**classList and dataset**:

```javascript
el.classList.add("active");
el.classList.toggle("selected", true);
el.classList.contains("active");

el.dataset.set("userId", "42");   // method style
el.dataset.userId;                // property style, maps to data-user-id
```

classList has `length/contains/add/remove/toggle/item/toString`; dataset methods are `get/set/has/delete/keys`.

**Styles**: `element.style` is a stable inline `CSSStyleDeclaration`. Field assignment, `cssText`, `length/item()`, numeric indexes, and `getPropertyValue/getPropertyPriority/setProperty/removeProperty` stay synchronized with the `style` attribute, computed style, and layout. You can also use `el.setAttribute("style", "...")` or `el.setInlineStyleProperty("background-color", "...")`.

**Geometry and scrolling**: `getBoundingClientRect()` returns a DOMRect with `x/y/width/height/left/top/right/bottom`; `scrollTop/scrollLeft/scrollTo/scrollBy` use logical coordinates.

**Image elements**: `currentSrc`, `naturalWidth/naturalHeight`, `complete`. The first ready dispatch fires `load`, the first failure fires `error`; neither bubbles and neither is re-dispatched.

## Events

Constructors can read these init fields (extra fields are not read):

- `Event`: type, bubbles
- `CustomEvent`: detail, bubbles
- `MouseEvent`: clientX, clientY, button
- `WheelEvent`: mouse fields + deltaX/deltaY/deltaMode
- `PointerEvent`: mouse fields + pointerId, pointerType, isPrimary

`new Event("x", {cancelable: true})` does not give you full cancelable semantics. To prevent default behavior, prefer real input events generated by the framework.

The fields and methods are the usual ones: `type/target/currentTarget/bubbles/cancelable/defaultPrevented/detail/eventPhase/cancelBubble/returnValue/isTrusted/timeStamp`, `stopPropagation()` / `stopImmediatePropagation()` / `preventDefault()` / `composedPath()`. All three phases — capture, at-target, bubble — are present; once listeners are removed automatically after firing; `dispatchEvent` returns false when the final `defaultPrevented` is true. `isTrusted` distinguishes real input from script-synthesized events — programmatic triggers like `element.click()` should not expect the full privileges of real input.

Fields readable on mouse/wheel/pointer events:

```text
clientX clientY pageX pageY offsetX offsetY
movementX movementY button buttons
deltaX deltaY deltaMode
pointerId pointerType isPrimary
altKey shiftKey controlKey metaKey
```

Keyboard events are generated from GLFW input; they can be listened to but have no constructor:

```javascript
document.addEventListener("keydown", function (event) {
    if (event.controlKey && event.key === "s") event.preventDefault();
});
```

Note it is `controlKey`, not `ctrlKey` — a very common pitfall when migrating code from the browser. Fields: `key/code/keyCode/scanCode/repeat/altKey/shiftKey/controlKey/metaKey`. Under different keyboard layouts, `key` is resolved from the GLFW name and may be `"Unidentified"`.

Supported event types:

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

focus, blur, and image load/error do not bubble — don't handle them as bubbling events.

## Forms

Controls: form, input, textarea, select, option, button.

```javascript
input.value = "hello";
input.checked = true;
input.focus();
select.selectedIndex = 1;
input.setSelectionRange(0, 3);
```

Supported properties and methods include: `value/defaultValue/checked/defaultChecked/disabled/name/type`, `multiple/required/readOnly/pattern/min/max/step`, `placeholder/accept/autocomplete/inputMode`, `selectionStart/selectionEnd/selectionDirection/setSelectionRange/setRangeText/select`, `valueAsNumber/stepUp/stepDown`, `focus()/blur()/click()`, constraint validation (`validity/validationMessage/willValidate/checkValidity/reportValidity/setCustomValidity`). select additionally has `options/selectedOptions/selectedIndex`, and option has `selected`. `files` is a path-approximated FileList, not real File objects. Unsupported input types degrade to a plain text box, and there are no native browser date/color/file picker popups.

Submit and reset:

```javascript
form.addEventListener("submit", function (e) {
    e.preventDefault();
    console.log(e.submitter);        // the button that triggered the submit
});
form.addEventListener("formdata", function (e) {
    console.log(e.formData.get("name"));
});
form.requestSubmit();   // runs constraint validation first; a submit button can be passed
form.submit();          // dispatches submit directly
form.reset();           // cancelable; restores default values if not canceled
```

AUI does not send HTTP requests for a form's action. After a submit is canceled, formdata is no longer dispatched. Controls outside the form can be associated with `form="id"`.

FormData:

```javascript
var data = new FormData(form);
data.append("tag", "aui");
data.get("tag");  data.getAll("tag");  data.set("page", "1");
data.has("page"); data.delete("page");
data.forEach(function (v, k) { ... });
```

`keys()/values()/entries()` return array-style results, not iterators. `toString()` encodes to a query string, useful for debugging.

## Storage and location

```javascript
localStorage.setItem("theme", "ore");
localStorage.getItem("theme");   // also removeItem/clear/key/length
sessionStorage.setItem("draft", "text");
```

localStorage persists to `config/apricityui/localStorage.nbt`; sessionStorage is only valid for the current client run. Empty keys are ignored; passing null may store the string "null"; there are no storage events.

`window.location` is generated from the resource path and exposes `href/protocol/host/hostname/port/origin/pathname/search/hash/searchParams`. `assign/replace/reload` are no-ops; there is no real navigation. To get the string form, read `href` — it has no custom toString.

URLSearchParams only has these methods: `append / getAll / sort / forEach / toString`. There is no `get/set/delete/has/keys/values/entries` — don't use it per the standard.

## fetch

```javascript
fetch("data.json").then(function (response) {
    console.log(response.ok, response.status, response.url);
    var text = response.text();
    var json = response.json();     // AUI built-in JSON parsing
    var bytes = response.bytes();   // copy of the byte array
});
```

Only single-argument `fetch(url)` — no init, no method/headers/body, no AbortController. Relative paths are resolved against the current Document's baseURI; remote URLs only go through the restricted HTTPS pipeline (limits described in the [Resource Management documentation](resource-manager)).

The return value is not a standard Promise: it supports `then(onOk, onErr)` and `catchError`; the page bridge provides a `catch` alias, but don't build complex chained transforms. To read JSON, call `response.json()` inside the same then.

## Observers

```javascript
var ro = new ResizeObserver(function (entries) {
    for (var i = 0; i < entries.length; i++) {
        console.log(entries[i].target, entries[i].contentRect.width);
    }
});
ro.observe(el);  ro.unobserve(el);  ro.disconnect();
```

An entry has `target/contentRect/borderBoxSize/contentBoxSize`; contentRect additionally has `borderBoxWidth/borderBoxHeight` beyond the regular rect fields. Without an actual size change, the callback is not fired again.

```javascript
var io = new IntersectionObserver(function (entries, observer) {
    for (var i = 0; i < entries.length; i++) {
        var entry = entries[i];
        if (entry.isIntersecting) console.log(entry.target, entry.intersectionRatio);
    }
}, {
    root: null,                         // null = the Document logical viewport
    rootMargin: "20px 0px",
    threshold: [0, 0.5, 1]
});
io.observe(el);
io.unobserve(el);
io.takeRecords();
io.disconnect();
```

An `IntersectionObserver` entry exposes `target/time/rootBounds/boundingClientRect/intersectionRect/isIntersecting/intersectionRatio`. The observer exposes read-only `root/rootMargin/thresholds`; rootMargin is normalized to four values and thresholds are sorted and deduplicated. `root` must be an Element from the same Document or null. An explicit root uses its committed padding overflow clip (including scrollbar gutters) when it clips overflow, otherwise its border box. A target also uses its committed border box plus ancestor overflow clips from the actual paint list. rootMargin accepts one to four `px` or `%` values; every percentage resolves against root width. Invalid rootMargin or threshold values throw.

This is the V1 core subset: `trackVisibility`, `delay`, `scrollMargin`, cross-Document observation, and precise transform or `clip-path` intersections are not supported. `visibility:hidden` is not automatically non-intersecting; `display:none`, disconnected nodes, and inactive Documents cannot produce an intersecting result.

```javascript
var mo = new MutationObserver(function (records) { ... });
mo.observe(document.documentElement, {
    childList: true, attributes: true, characterData: true, subtree: true,
    attributeOldValue: true, characterDataOldValue: true,
    attributeFilter: ["class", "style"]
});
mo.takeRecords();  mo.disconnect();
```

A record exposes `type/target/addedNodes/removedNodes/previousSibling/nextSibling/attributeName/oldValue`.

All three dispatch in batches per document frame, not at browser microtask timing. IntersectionObserver collects entries after every Document has committed layout and its paint list for the frame, then invokes callbacks; style changes, scrolling, unobserve, or disconnect inside a callback affect the next frame. After a Document refresh, observers are cleaned up — you must re-query the nodes and observe them again.

## Canvas and Images

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

`getContext("2d")` is the only context (no WebGL). Capability list: the rect trio, text (`fillText/strokeText/measureText`), paths (`beginPath/closePath/moveTo/lineTo/rect/roundRect/arc/arcTo/ellipse/quadraticCurveTo/bezierCurveTo/fill/stroke/clip/isPointInPath/isPointInStroke`), transforms (`save/restore/translate/rotate/scale/transform/setTransform/resetTransform`), gradients and patterns, the ImageData trio, `drawImage`, `globalAlpha/globalCompositeOperation/filter/shadows`, and export via `toDataURL()` / `toBlob()`. The Blob-compatible object from toBlob has `size/type/arrayBuffer()/text()/toDataURL()`.

The backend is Java2D, so colors, filters, and compositing may differ from browsers. Bitmap size is set by the `width/height` attributes (default 300×150); CSS only controls display scaling — don't change only CSS and assume the drawing coordinates changed.

Path2D supports path methods (including `addPath`) and construction from SVG path strings; DOMMatrix is a 2D affine matrix (fields a~f, `translateSelf/scaleSelf/rotateSelf/multiplySelf/invertSelf`):

```javascript
var path = new Path2D("M0 0 L40 20 Z");
var m = new DOMMatrix();
m.translateSelf(10, 5).scaleSelf(2, 2);
ctx.setTransform(m);
ctx.stroke(path);
```

OffscreenCanvas and createImageBitmap:

```javascript
var off = new OffscreenCanvas(128, 64);
var bitmap = off.getContext("2d").transferToImageBitmap();  // draw first, then transfer
bitmap.close();                                             // release when done
var bmp = createImageBitmap(canvas);              // synchronous
var cropped = createImageBitmap(canvas, 0, 0, 32, 32);  // crop overload
createImageBitmapAsync(canvas).then(function (b) { ... });  // async version
```

## Audio

Two entry points, both with browser HTMLAudioElement semantics: the `<audio>` element and the `new Audio()` factory.

```html
<audio src="apricityui/sounds/click.ogg" controls autoplay></audio>
<script>
    var audio = new Audio("apricityui/sounds/bgm.ogg");  // detached instance, not in the DOM tree
    audio.loop = true;
    audio.volume = 0.6;
    audio.addEventListener("ended", function () { console.log("done"); });
    audio.play().then(function () { /* playing */ })['catch'](function (err) { /* load failed */ });
</script>
```

- **Sources**: the same resource chain as images/fonts — dev-environment resource dirs, `<gamedir>/apricity/`, classpath, resource packs; relative `src` resolves against the page path. No remote URLs.
- **Formats**: OGG Vorbis and WAV/PCM (8/16-bit, mono/stereo). No MP3, no Web Audio API.
- **API**: `play()` (returns a Promise with `then`/`['catch']`), `pause()`, `load()`; `currentTime` (read/write, write = seek), `duration` (NaN until ready), `volume` (clamped to 0..1), `muted`, `loop`, `paused`, `ended`, `seeking`, `readyState` (0→1→4; full-decode skips 2/3), `networkState` (3 with no src), `preload` (`none` waits for play()/load(); `metadata`/`auto` both load immediately), `autoplay`.
- **Event sequence**: `loadstart → durationchange → loadedmetadata → canplay → canplaythrough → play → playing → timeupdate (250ms throttle) → pause / ended`; failures dispatch `error`; seeks dispatch `seeking → seeked`. Inline `oncanplay`-style attributes work too.
- **controls**: a self-drawn controls bar (play/pause button, click + drag seek on the progress track, time text), UA-sized 240×28 and overridable via CSS; without the `controls` attribute the element is `display:none` and takes no space.
- **Volume mixing**: effective volume = `volume × (muted ? 0 : 1) × MC master volume`, tracking the game's sound settings in real time.
- **Lifecycle**: closing/refreshing a document stops and releases all of its audio (including detached `new Audio()` instances); a resource reload stops everything and drops the decode cache. Decoded data is shared per path while every instance gets its own channel, so overlapping playback works.
- **Degradation**: with no sound card or a failed OpenAL init, playback dispatches `error` instead of crashing.

## Lifecycle and Refresh

```text
Create Document -> loading -> parse HTML/CSS/JS -> interactive
  -> DOMContentLoaded -> complete -> load
  -> (host closed) -> unload -> Document removed
```

`document.refresh()` or a global resource reload rebuilds the page: it clears the old DOM, styles, and script caches, re-runs scripts, and re-fires DOMContentLoaded/load. **After a refresh, nothing old is usable**: Elements, collections, listeners, Observer targets, Canvas surfaces. The right pattern is to put initialization into a function and rebind on every DOMContentLoaded:

```javascript
function installPage() {
    var button = document.getElementById("reloadable");
    if (button === null) return;
    button.addEventListener("click", function () { ... });
}
document.addEventListener("DOMContentLoaded", installPage);
```

Don't call `refresh()` every frame — it rebuilds the entire page; it is a reload mechanism, not an update mechanism.

## AUI-Specific Behavior

**Dual script content**: a `<script>` with both `src` and inline code executes both (with a warning). Don't rely on the browser behavior of "src ignores inline".

**Log prefixes**: when troubleshooting, search for `[AUI HTML]` / `[AUI CSS]` / `[AUI JS]` / `[AUI Fetch]` / `[AUI Canvas]` / `[AUI Event]`, usually with a resource path attached. In pages, use `console.log/debug/warn/error` and `console.time/timeEnd`.

**Top layer**: the host-side `setTopLayer(true)` makes popups and dropdown menus draw last within the current Document and not get clipped by ancestor overflow. It only affects ordering within this Document; it does not lift one Document above another.

**Manual rendering**: the host can set a Document to manual rendering, after which it leaves global drawing and input dispatch, and the caller draws it and forwards events itself. Normal pages should not use this.

**Extension elements**: AUI registers Minecraft-oriented tags such as `<texture>`, `<sprite>`, `<translation>`, `<svg>`, `<canvas>`, `<container>` — these are not native browser HTML. See the [Extension Elements documentation](extension-elements).

**Hosts**: the page DOM API does not create hosts. Screen, Overlay, Container, and WorldWindow each have their own documentation.

## Performance Tips

- For high-frequency updates, change `textContent`, attributes, and classes — don't rewrite `innerHTML` every frame;
- Batch large DOM modifications into one pass so the framework can coalesce style and layout dirty flags;
- Actively clean up observers, timers, and listeners on close;
- Don't cache any DOM objects across a `refresh()`;
- Use event coordinates directly — don't multiply by scale again;
- Write error handling for fetch, json(), and Canvas decoding.
