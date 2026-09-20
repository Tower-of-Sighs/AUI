# Extension Elements

Beyond standard HTML, AUI registers a set of extension tags. They are all ordinary DOM elements that participate normally in CSS, layout, hit-testing, and script manipulation. They solve one common class of problem: **drawing game resources and animations into the page**.

For the capability boundaries of standard elements see [HTML/CSS Coverage](html-css-coverage); for containers/slots/recipes see the [Container doc](container); for registering your own elements see the [Secondary Development doc](secondary-development) — none of that is repeated here.

## How to Choose

| Need | Use |
| --- | --- |
| Static images in the page directory (PNG/GIF/WebP) | Standard `<img>` / CSS `background-image` |
| Minecraft registry textures (items, block textures) | `<texture>` |
| Atlas frame-by-frame animation | `<sprite>` |
| Localized text | `<translation>` |
| Vector icons, lines, curves | `<svg>` |
| Pixel-level, chart, per-frame recomputed visuals | `<canvas>` |
| Running a real web page / third-party web content | `<iframe>` |
| Item slots, inventories, recipe previews | `<container>` / `<slot>` / `<recipe>` (Container doc) |

All custom-drawn elements have no intrinsic size — remember to give them a stable `width`/`height` via CSS or attributes, otherwise the layout will jump once resources finish loading asynchronously. Don't `refresh()` every frame in scripts while waiting for resources — the framework marks a repaint once resources are ready.

## texture: Minecraft Textures

```html
<texture src="minecraft:textures/item/diamond.png" style="width: 32px; height: 32px;"></texture>
<texture src="examplemod:textures/block/machine.png" blur="true"></texture>
```

- `src` is a ResourceLocation in `namespace:path` form that goes straight through the MC texture system — it is **not** a page logical path, nor a disk path; for images in the page directory use `<img>` or `<sprite>`;
- `blur` only enables blurred rendering when written exactly as `true`;
- If src is invalid or the element has zero size, the texture is not drawn, but CSS backgrounds and borders still render; changing `src` takes effect immediately.

## sprite: Atlas Frame Animation

Plays a horizontal or vertical atlas as a `steps()` frame animation — good for loading spinners, button states, particles, idle motions:

```html
<sprite class="coin"
        src="images/coin-strip.png"
        steps="8"
        direction="right"
        duration="640ms"
        loop="infinite"></sprite>
```

`src` resolves against the current HTML's **logical path** (`images/x.png` inside `screens/home.html` resolves to `screens/images/x.png`; use `/images/x.png` to go back to the resource root), and HTTPS images are also allowed. This is exactly the opposite of texture's ResourceLocation — don't mix them up.

| Attribute | Default | Description |
| --- | --- | --- |
| `steps` | none | Positive integer frame count; treated as a static background if missing or invalid |
| `direction` | `right` | `right`/`left`/`up`/`down`; determines both the atlas layout and the frame movement direction |
| `duration` | `1s` | CSS time value (`250ms`, `1.5s`) |
| `loop` | `infinite` | `infinite` or a positive integer play count |
| `steps-mode` | `end` | `start` / `end`, corresponding to the CSS `steps()` timing mode |
| `autoplay` | `true` | `false`/`0`/`no`/`off` disable it; when disabled the `initialframe` frame is still shown |
| `initialframe` | `0` | Starting frame; out-of-range values are clamped to the last frame |
| `fit` | `none` | `none`/`contain`/`cover`/`stretch`, mapped to background-size |

For a horizontal atlas, single-frame size = width/steps × height; the reverse for vertical — every frame in the atlas must have the same size, otherwise the derivation will be wrong.

**Note**: Sprite manages `background-image/position/size/repeat` and the `animation` family of properties in the inline style, overriding your handwritten inline declarations of the same name (other CSS animations are merged and preserved). If you want full control over the background animation yourself, don't use sprite — use a plain div + CSS.

## translation: Localized Text

```html
<translation>container.apricityui.title</translation>
```

The text content is an MC translation key, rendered in the current language. textContent is the key itself. There is no parameter interpolation — assemble parameterized translations on the script side and use a normal text element.

## svg / path: Vector Graphics

An SVG subset, rasterized to a bitmap with 4x supersampling — suited for icons and simple decorations:

```html
<svg viewBox="0 0 64 64" width="64" height="64" style="color: #7dd3fc;">
    <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="4"></circle>
    <path d="M18 34 L28 44 L47 22 Z" fill="currentColor"></path>
</svg>
```

- Shapes: `circle`, `ellipse`, `rect`, `line`, `polyline`, `polygon`, `path`;
- Paint attributes: `fill`/`stroke` (including `none`), `stroke-width`, `stroke-linecap/join`, the `opacity` trio, `fill-rule` (supports evenodd), `currentColor` (reads the `color` of the element or an ancestor); attributes can be inherited from parents;
- path commands: M/L/H/V/C/S/Q/T/A/Z, both upper and lower case;
- `viewBox` is the internal coordinate system; element size is given via SVG attributes or CSS;
- **No** defs/use, gradients, filters, masks, text, transform, or external SVG. Flatten complex icons into pure paths on export, or switch to canvas.

SVG caches rasterization results and only redraws when attributes or the subtree change. Rasterizing large, complex paths is not cheap — watch it with frameTimingHud.

## canvas: Scripted Drawing

```html
<canvas id="chart" width="320" height="160"></canvas>
```

A standard Canvas 2D subset (Java2D backend). The `width`/`height` attributes set the bitmap size, CSS sets the display size, and a mismatch scales — don't assume changing only CSS changes the coordinate system. For full API support and limitations see the [Web API doc](web-api).

Frequent redraws of large canvases incur upload costs. Use svg for static vectors and texture for game textures — canvas is the most expensive of these; reserve it for visuals that truly need per-frame computation.

## iframe: The Operating System WebView

`<iframe>` renders through the **system web view** (WebView2 / Edge Runtime on Windows), not a browser embedded in the framework. The hosted page lives in an offscreen window the user never sees and streams back **only the part of its pixels that changed**, as a texture — so layout, clipping, transforms, stacking and hit testing all behave like any other texture-backed element such as canvas.

```html
<iframe src="https://example.com/panel" style="width: 480px; height: 320px; border: 0;"></iframe>
<iframe src="file:///C:/pages/tool.html" style="width: 400px; height: 300px;"></iframe>
```

- `src` must be an **absolute URL** (`https:`, `file:`, …). The engine has no document base URL, so relative paths are handed to the browser as-is and resolve to nothing;
- a browser instance is only started when the `src` attribute is present; an `<iframe>` without `src` costs no process and acts as a placeholder;

**Sizing follows the browser rules** (implemented per the CSS 2.1 replaced-element rules):

```html
<iframe src="..."></iframe>                        <!-- 300x150, the default object size -->
<iframe src="..." width="400" height="200"></iframe><!-- attributes are a presentational hint -->
<iframe src="..." style="width:400px"></iframe>     <!-- 400x150, the other axis takes the default -->
<iframe src="..." style="width:100%;height:240px"></iframe> <!-- fill explicitly -->
```

- the UA default is `display: inline`, as in a browser; ask for `display: block` yourself if you want block layout;
- `width`/`height` attributes are **presentational hints**, just like in a browser: any author CSS overrides them, and otherwise they are the size source. Give only one axis and the other takes its default (300 or 150) — there is **no aspect-ratio derivation**, an iframe has no intrinsic ratio;
- block-level (or absolutely positioned) with `width:auto` uses the **intrinsic width instead of filling the parent**, and `inset: 0` will not stretch it either. That is the standard replaced-element behaviour; write `width: 100%` to fill;
- the **inner page's CSS viewport equals the element's content box** in CSS pixels, just as in a browser: `innerWidth`, `vw`/`vh` and media queries all resolve against it. The texture is rasterised at the content box's **device pixels** (content box × device scale) and the page's `devicePixelRatio` equals that scale, so it is 1:1 at real resolution and stays crisp at any screen scaling;
- the texture is capped at 4096 px: past that the raster is truncated, but the zoom is compensated in step so the **page's CSS viewport stays correct** and only sharpness degrades;
- pointer move/press/wheel are forwarded to the page, and a page with its own wheel listener stops AUI from scrolling the parent container. Once the iframe has focus, keys go to the page and are **swallowed**, so Minecraft hotkeys do not fire at the same time;
- `overflow: hidden` works as usual and clips the overflow;
- when the backend is unavailable (not Windows, or the WebView2 Runtime is missing) the element draws nothing while its CSS background and border still render.

```js
const frame = document.getElementById("panel");
frame.getAttribute("src");
frame.setAttribute("src", "https://example.com/other");  // navigate, reusing the running browser
frame.removeAttribute("src");                            // shut the browser down, back to a placeholder
```

Limitations worth knowing:

- **A single capture costs about 20 ms, fixed.** WebView2 only offers a lossless (PNG) and a lossy (JPEG) screenshot format, and `CapturePreview` needs at least ~21 ms per call (measured at 400×300 and at 1280×720: ~21 ms for the small one, ~28 ms for the large one — and the same numbers on a page that is not animating, so this is not "waiting for a new composited frame" but the readback + encode + IPC inside the browser process). On a heavy 900×700 page PNG costs a ~110 ms round trip (≈9 fps) against JPEG's ~31 ms. The `capture` attribute defaults to **auto**: a page that is not changing uses the lossless codec (and byte-level de-duplication of the payload skips the decode, the transfer and the upload entirely, so it is free), and as soon as frames keep changing it switches to the fast codec. Pin it with `capture="lossless"` or `capture="fast"`;
- **Captures are pipelined.** Most of that ~21 ms is waiting, so the host keeps up to four `CapturePreview` calls in flight and starts the next while one is still out, which multiplies the throughput by about four. A completion that arrives after a newer one has already published is dropped rather than applied out of order (counted as `stale=` in `status()`), so the canvas only ever moves forward. Measured on an 800×600 page repainting at 60 fps: PNG 33→**61 fps** and JPEG 39→**61 fps**; 1280×720 went 30→**60 fps**, 1600×900 sits at about **59 fps**;
- **Decoding does not run on the UI thread.** The capture callback only enqueues; WIC decoding, the channel byte-order swap, the tile diff and the shared-memory publish all happen on a separate decode thread at below-normal priority. That UI thread is WebView2's own message pump, and every millisecond it spends decoding is a millisecond pointer and wheel input waits — at 1200×900 that was 2–20 ms per frame, which is exactly what made dragging and scrolling feel sticky. `cmd=` in `status()` (how long the last command sat in the queue) now sits at **0–1 ms**;
- **Pointer moves are coalesced**: only the newest cursor position is kept and forwarded once per host loop. Chromium only needs to know where the pointer is now, and forwarding every intermediate sample across the process boundary adds latency for nothing; during a drag more than half the samples are merged away (`coalesced=`). Buttons, wheel and leave carry order and are never coalesced;
- **The capture rate follows the element's draw rate**: a capture reads the composited surface back through the GPU, and the game is drawing on that same GPU. Measured: a 60 fps game gets 57 captures/s, a 30 fps game 27, a 20 fps game 20 — anything above the draw rate could never be shown anyway and only competes with the game for the GPU, so the request rate follows the measured frame time (with a 50 ms floor so the page never looks frozen);
- **the in-flight capture depth adapts**: up to four captures overlap while a capture costs less than 45 ms, dropping to two past 60 ms, so a GPU under pressure does not get a queue of readbacks piled on top of it;
- **A very large iframe is downscaled by area**: capture cost grows linearly with area (measured ≈ 20 ms + 28 ms per megapixel), so past 1.2 million pixels the raster is scaled down proportionally (the zoom is recomputed, so the **page's CSS viewport stays exact** and only sharpness is traded), and `status()` marks it `raster capped by area`. That keeps one full-screen-sized view from eating the frame rate; `capture-scale` still stacks on top of it;
- **pixels travel as an incremental image stream, not as polled whole frames.** The host compares each capture against the canvas the renderer is known to hold on a 32×32 tile grid and writes only the rectangles that changed (merged into as few as possible) into a block of shared memory; the renderer reads them in packets and rewrites only those regions of its texture, so **the cost follows the changed area, not the canvas size**. Measured on an 800×600 page with a single 48×48 box moving: about **1.5%** of the canvas is dirty per frame, and on the same page captured with JPEG an average upload covers about **4%** of the canvas (JPEG noise dirties more tiles). A page that is completely still publishes **nothing at all**, and the capture itself is skipped by the payload-level de-duplication. A whole-canvas transfer only happens on the first frame, when the element is resized, and when the reader asks for a refresh;
- frames and updates are taken in the **render phase** (once per rendered frame), so the embedded page is not capped by the 20 Hz logic tick. The capture loop pauses after two seconds without a draw, so a hidden page does not burn CPU;
- resolution is not the frame-rate knob: `capture-scale="0.5"` makes a single capture a little cheaper (800×600 → 400×300 saves about 5 ms) but the pipeline already hides that latency, so it is now a **CPU and bandwidth** control (the CSS viewport stays correct, the picture gets softer) rather than a frame-rate one;
- bypassing `CapturePreview` with a raw pixel stream was **tried and does not work here**: Windows.Graphics.Capture (DWM composition frame push) starts and delivers frames, but every frame is a **single flat colour** — the WebView2 content is attached through `IDCompositionTarget` (`CreateTargetForHwnd`) and neither WGC nor `PrintWindow(PW_RENDERFULLCONTENT)` reports it. The code is kept in `native/webview/src/frame_stream.{h,cpp}`; at runtime it is attempted, found to be blind, disabled automatically and the codec path is used, so it costs nothing — and it would start working on its own if that presentation ever changes. Note that auto no longer *attempts* it (that wasted one blank capture per view), so pin it with `capture="stream"` to try;
- **there is no native key injection API**, so keys are synthesised as DOM events inside the page: `keydown`/`keyup` reach page listeners normally, but a browser **default action never happens implicitly**, so the framework applies it explicitly (backspace/delete/enter/arrows in inputs are handled). IME composition is currently delivered as already-committed text;
- relative paths, `srcdoc` and `sandbox` are not supported; `window.parent` / `postMessage` inside the page point at the browser's own tree and are **not** wired to AUI, and `contentWindow`/`contentDocument` are not exposed;
- starting an instance takes a few hundred milliseconds (done on a background thread, so it does not stall a frame); browser data lives under `game directory/apricity/webview` and survives restarts;
- for debugging, the **frame timing HUD** in DevTools (F12) gains a `stream=WxH packets=… rects=… payload=…KB` section plus the host's `fps/period/roundTrip/cmd/raster`, so the capture rate and input queueing are visible in game. On the Java side `Iframe.status()` returns the native host's capture/navigation counters followed by the stream's send/receive statistics (canvas size, packets, rectangles, payload bytes, resyncs), and `AuiServices.webView().unavailableReason()` explains why the backend is unavailable. When something feels slow, read these first: `fps=` capture rate, `period=` gap between capture starts (much larger than `roundTrip=` means a scheduling problem, close to it means the capture itself is the ceiling), `cmd=` how long input queued (large means the UI thread is busy), `decode=`, and `stale=`/`dropped=` for lost frames.

## FAQ

**texture doesn't show**: `src` was written as a logical path or file path — it must be `namespace:path`. To show images from the page directory use img.

**sprite doesn't animate**: steps doesn't match the atlas's actual frame count, direction doesn't match the atlas layout, or the element has no size. A static background is shown until the atlas finishes loading — that's normal.

**sprite's styles are overridden by my background / or the reverse**: managed properties only override same-named inline styles; those in stylesheets are unaffected. When troubleshooting, think of the managed-property list first.

**translation shows the raw key**: the key doesn't exist or the language file lacks the entry — consistent with vanilla behavior.

**svg's gradients/groups aren't drawn**: not supported. Flatten into paths or switch approaches.

**canvas content is gone after refresh**: refresh rebuilds the DOM, and the bitmap is cleared along with it. Put drawing logic in an initialization function and rerun it on `DOMContentLoaded`/`load`.
