# WebView and iframe

AUI is not itself a browser — HTML parsing, CSS layout, and painting are all a self-built engine. But when a page needs to **actually run a web page** (a third-party site, video, a complex interface not worth reimplementing in AUI), the framework keeps an escape hatch: the `<iframe>` tag is backed by the **operating system's own web view** (WebView2 / Edge Runtime on Windows), rendered offscreen and painted into the page as a texture.

This chapter covers the full behaviour of `<iframe>`, the architecture and performance characteristics of the WebView backend, and what it can and cannot do. For the capability boundaries of standard elements see [HTML/CSS Coverage](html-css-coverage); for a quick look at `<iframe>` as an extension element see the [extension elements doc](extension-elements) — neither is repeated here.

## When to use it

| Need | Use |
| --- | --- |
| In-game HUDs, panels, settings pages | AUI native elements |
| Interacting with game data / items / block entities | AUI native elements + [containers](container) |
| Running a real web page, third-party web content | `<iframe>` |
| Playing video, reaching externally updated docs | `<iframe>` |

- The content of an `<iframe>` lives **outside AUI**: there is no AUI DOM inside it, and AUI scripts cannot read it. It is "a texture plus forwarded input", not a composable component;
- Each instance is a whole offscreen browser. Memory use is high, so it is **a poor fit for Overlays and in-world windows** — reserve it for full-screen pages and larger panels;
- Only Windows x64 has the backend; everywhere else it is an empty placeholder (see [Platform and availability](#platform-and-availability)).

## Quick start

```html
<iframe src="https://example.com/panel" style="width: 480px; height: 320px; border: 0;"></iframe>
<iframe src="file:///C:/pages/tool.html" style="width: 400px; height: 300px;"></iframe>
```

- `src` must be an **absolute URL** (`https:`, `file:`, …). The engine has no document base URL, so a relative path is handed to the browser as-is and resolves to nothing;
- a browser instance is only started when the `src` attribute is present; an `<iframe>` without `src` costs no process and is just a placeholder box;
- confirm the backend with `AuiServices.webView()` first (see [Platform and availability](#platform-and-availability)); when it is unavailable the element degrades to an empty box.

## Attributes

| Attribute | Values | Default | Notes |
| --- | --- | --- | --- |
| `src` | absolute URL | none | decides whether an instance is created; changing it navigates (reusing the instance) |
| `width` / `height` | number (px) | 300 / 150 | presentational hints, in effect only when CSS does not set the size |
| `capture` | `auto` / `lossless`(`png`) / `fast`(`jpeg`) / `stream`(`raw`) | `auto` | capture codec strategy, see [Capture and performance](#capture-and-performance) |
| `capture-scale` | float 0.25 – 1.0 | 1.0 | raster scale; below 0.25 it is clamped, invalid or ≤0 is treated as 1.0 |

There is no `zoom` attribute and no `focus` attribute — `focus=` is just an output field of `status()`. Changing `capture` at runtime applies immediately; changing `capture-scale` takes effect on the next tick, when the raster is recomputed.

## Sizing and layout

Implemented per the CSS 2.1 replaced-element rules, matching a browser:

```html
<iframe src="..."></iframe>                        <!-- 300x150, the default object size -->
<iframe src="..." width="400" height="200"></iframe><!-- attributes are a presentational hint -->
<iframe src="..." style="width:400px"></iframe>     <!-- 400x150, the other axis takes the default -->
<iframe src="..." style="width:100%;height:240px"></iframe> <!-- fill explicitly -->
```

- the UA default is `display: inline`, as in a browser; ask for `display: block` yourself if you want block layout;
- `width`/`height` attributes are **presentational hints**: author CSS overrides them, and otherwise they are the size source. Give only one axis and the other takes its default (300 or 150) — there is **no aspect-ratio derivation**, an iframe has no intrinsic ratio (write CSS `aspect-ratio` if you want one);
- block-level (or absolutely positioned) with `width: auto` uses the **intrinsic width instead of filling the parent**, and `inset: 0` will not stretch it either. Write `width: 100%` to fill;
- layout, clipping, transforms, stacking and hit testing all behave like any other texture-backed element such as canvas — because that is exactly what it is: the texture is stretched to the element's **content box** when painted, independent of the raster resolution.

## Page coordinates and sharpness

- the inner page's **CSS viewport equals the element's content box** in CSS pixels: `innerWidth`, `vw`/`vh` and media queries all resolve against it;
- the raster is captured at the content box's **device pixels** (content box × device scale) and the page's `devicePixelRatio` equals that scale, so it is 1:1 at real resolution and stays crisp at any screen scaling;
- the raster can be shrunk by three things: `capture-scale`, a 4096 px per-axis cap, and by-area downscaling (past 1.2 million pixels). When that happens WebView2's ZoomFactor (`zoom`, clamped hard to 0.25 – 5) is recomputed in step, so the **page's CSS viewport stays exact** and only sharpness is traded;
- the texture is always painted to the content box, so a smaller raster never misplaces the content — it only softens it.

## Rendering pipeline

The pixels of an `<iframe>` are not painted by the framework. They arrive like this:

1. the **host thread** runs the real web page inside an offscreen hidden window (`WS_POPUP`, parked at -32000,-32000 by default) through WebView2's CompositionController; every iframe in the process shares one browser process and profile;
2. the host periodically calls WebView2's `CapturePreview` and gets a PNG- or JPEG-encoded screenshot (both the capture and the encode happen in the browser process);
3. a **decode thread** (below-normal priority) does the WIC decode and channel byte swap, diffs the result against the canvas the renderer is known to hold on a 32×32 tile grid, and writes only the changed rectangles into a block of **shared memory**;
4. the render thread reads that shared memory in packets and writes only those rectangles into the texture, uploading only those regions.

The transfer cost therefore follows the **changed area**, not the canvas size. Each instance owns a host thread, a decode thread, a shared-memory section and a hidden window; the browser process and profile are shared.

## Capture and performance

- **A single capture costs about 20 ms, fixed.** WebView2 only offers a lossless (PNG) and a lossy (JPEG) format, and `CapturePreview` needs at least ~21 ms per call (measured at 400×300 and 1280×720: ~21 ms for the small one, ~28 ms for the large one — and the same numbers on a page that is not animating, so this is the readback + encode + IPC inside the browser process, not "waiting for a new composited frame"). Heavy pages cost more: a 900×700 page costs a ~110 ms PNG round trip (≈9 fps) against JPEG's ~31 ms; area costs about 20 ms + 28 ms per megapixel;
- `capture` defaults to **auto**: a page that is not changing uses the lossless codec (with the de-duplication below, that is free), and as soon as frames keep changing it switches to the fast codec — two publishes inside a 250 ms window flip it over, and 1.5 s of quiet flips it back. Pin it with `capture="lossless"` or `capture="fast"`;
- **captures are pipelined**: most of that ~21 ms is waiting, so up to four `CapturePreview` calls are in flight at once and the next starts while one is still out; a completion that arrives after a newer one has published is dropped (counted as `stale=`). Measured on an 800×600 page repainting at 60 fps: PNG 33→**61 fps** and JPEG 39→**61 fps**; 1280×720 went 30→**60 fps**, 1600×900 sits at about **59 fps**. The in-flight depth adapts: four overlap while a capture costs less than 45 ms, dropping to two past 60 ms;
- **two layers of de-duplication**: ① the host compares this frame's compressed payload byte-for-byte against the previous one and, when it is identical and the reader owes nothing, **skips the whole frame** (no decode, no transfer, no upload); ② a tile-by-tile diff on 32×32 blocks sends only the changed rectangles. Measured on an 800×600 page with a single 48×48 box moving: about **1.5%** of the canvas is dirty per frame (the same page captured with JPEG averages about **4%** of the canvas per upload, since JPEG noise dirties more tiles); a completely still page publishes **nothing at all**. Whole-canvas transfers only happen on the first frame, on a resize, and when the reader asks for a refresh;
- **the capture rate follows the element's draw rate**: measured, a 60 fps game gets 57 captures/s, a 30 fps game 27, a 20 fps game 20; the request interval follows the measured frame time, clamped to 16 – 50 ms (the 50 ms floor keeps the page from looking frozen);
- frames and updates are taken in the **render phase** (once per rendered frame), so the page is not capped by the 20 Hz logic tick; the capture loop pauses after two seconds without a draw;
- `capture-scale` is a **CPU and bandwidth** control, not a frame-rate one: 800×600 → 400×300 saves about 5 ms per capture, but the pipeline already hides that latency;
- `capture="stream"` tries to bypass the codecs and take the raw pixel stream — **it does not work here**: Windows.Graphics.Capture starts and delivers frames, but every frame is a **single flat colour** (the WebView2 content is attached through `IDCompositionTarget`, and neither WGC nor `PrintWindow` reports it). The host notices it is blind, disables itself and falls back to codec captures, noting `composition stream saw no content; fell back to the capture codecs` in `status()`; auto does **not** attempt it, so pin `capture="stream"` to try.

## Input

- **pointer move / press / release**: forwarded while the pointer is inside the content box, or while a button is down (drag pointer capture), with the coordinate clamped to the box; the pressed state travels with the moves (otherwise Chromium reads a drag as a hover). A drag keeps being forwarded once the cursor leaves the content box, the release is always delivered, and `mouseLeave` is held back while a button is down;
- **pointer moves are coalesced**: only the newest position is kept and forwarded once per host loop (during a drag more than half the samples are merged away, counted as `coalesced=`); buttons, wheel and leave carry order and are never coalesced;
- **wheel**: forwarded to the page; a page with its own wheel listener stops AUI from scrolling the parent container. Note the iframe only stops propagation and does not hand the wheel to the native side, so if the host document does **not** declare `aui-mouse-events: intercept` the wheel can still leak through to the game (see the [ApricityScreen meta section](apricity-screen#page-meta-configuration)). The wheel is converted by sign only, always **one notch**, horizontal wheel is not forwarded, and a `deltaY` of 0 counts as up; hovering an iframe and holding **Ctrl+wheel** is consumed by AUI's page zoom first and never reaches the page (unless the page's meta turns off `user-scalable`, see [Browser features](browser-features#page-zoom));
- **keyboard**: once the iframe is its document's focused element, `keydown`/`keyup` go to the page and are **swallowed**, so Minecraft hotkeys do not fire at the same time. Text only lands if an editable element **inside the page** holds DOM focus (click the page's input first), exactly as in a real browser; with nothing focused the characters are **dropped silently** (the page gives no feedback at all);
- **there is no native key injection API**: keys are synthesised as **DOM events inside the page**, so a browser default action never happens implicitly and the framework applies it explicitly (backspace/delete/arrows in inputs are handled; Enter inserts a newline in a `textarea` and submits the enclosing form in a single-line input, as in a browser); IME composition is delivered as already-committed text, with no composition process.

## Lifecycle

- after `src` is set, the instance is created on a **background thread** (a few hundred milliseconds the first time, without stalling a frame); `status()` reads `starting` in the meantime;
- changing `src` navigates the existing instance instead of rebuilding it; to force a reload, set the same value again with `setAttribute("src", ...)` (the element does not de-duplicate);
- `removeAttribute("src")`, removing the element from the document, and **closing the whole document** all release the instance (browser, host thread, decode thread, shared section);
- browser data (cookies, localStorage) lives under `game directory/apricity/.cache/webview` and survives restarts. It sits in the cache area **outside the page root** (next to the network cache at `apricity/.cache/network`): `apricity/` is what people author pages in and what the resource scan and dev reload walk, while a browser profile is tens of thousands of files of machine state that churns continuously (and whose cache entries carry exactly the `.html/.css/.js` extensions dev reload watches) — inside the resource tree it would be listed as resources and would trip spurious reloads. Directories whose name starts with a dot are skipped by both the scan and the watcher (see [Resource Manager](resource-manager));
- **an unavailable backend is sticky**: once creation fails or the instance goes invalid, that element stays a placeholder forever and is not retried (not even after `src` changes).

## Popups and navigation

- `window.open` / `target="_blank"` are **taken over by the current view and navigated in place** — a link goes where the user expects, inside the iframe, instead of producing a popup window on the user's desktop that they cannot see or move;
- `window.close()` is left alone (there is no window of ours to close).

## Platform and availability

- **Windows x64 only**: other platforms, and JVMs that are not 64-bit x86, have no backend;
- the system needs the **WebView2 Runtime** installed; a missing runtime, or one too old for offscreen hosting, fails as well;
- check with `AuiServices.webView()`:

```java
AuiServices.webView().isAvailable();        // is the backend usable
AuiServices.webView().backendName();        // "webview2"
AuiServices.webView().unavailableReason();  // why it is unavailable
```

  The reasons include: not Windows (`offscreen WebView2 hosting is Windows-only`), not 64-bit x86 (`needs a 64-bit x86 JVM`), the bundled native library missing, `native library not loaded`, `WebView2 runtime is not installed`, and a runtime that is too old;
- when unavailable, an `<iframe>` draws nothing, while its CSS background and border still render — it is an empty box, not a broken one.

## Limitations

- relative paths, `srcdoc` and `sandbox` are not supported;
- `window.parent` / `postMessage` inside the page point at the browser's own tree and are **not** wired to AUI; `contentWindow` / `contentDocument` are not exposed either;
- the page is composited opaque; there is no alpha channel;
- ZoomFactor is clamped by WebView2 to 0.25 – 5 and the SDK cannot widen it;
- **there is no scripting-facing webview API**: on the JS side only the generic DOM attributes / events are available (`getAttribute`/`setAttribute`); on the Java side there is no `reload()` / `navigate()` either — change the attribute instead.

## Debugging

`Iframe.status()` is the main entry point and reads in three shapes: `unavailable: <reason>`, `no view` / `starting`, or one full line. The fields in the full line:

| Field | Meaning |
| --- | --- |
| `ready=` | whether the WebView2 controller is ready |
| `nav=` | number of completed navigations |
| `capture=` / `done=` / `rejected=` | captures started / completed / failed or rejected |
| `stale=` | captured frames that came back too late behind a newer one |
| `decodeFail=` / `bytes=` | decode failures / the last frame's compressed payload bytes |
| `roundTrip=` / `decode=` / `period=` | last capture round trip / decode time / gap between capture starts (ms) |
| `loop=` / `fps=` | host loop Hz / frames published in the last second |
| `cmd=<last>/<max>ms` | command queue latency (current / peak) |
| `pending=` / `dropped=` / `coalesced=` | pending decodes / frames dropped under decode backlog / merged pointer moves |
| `raster=` / `format=` / `window=` | raster size / actual codec / host window size |
| `stream=<callbacks>/<frames>/<emptyCallbacks>/<blankFrames>@<W>x<H>` | raw pixel stream composite counters |
| `raster capped by area` | by-area downscaling was triggered |
| `box=` / `zoom=` / `focus=` / `buttons=` | content box size / current zoom / whether this iframe is the document's focused element / held mouse buttons |
| stream section | `stream=<W>x<H> packets=<applied>/<published> rects=… payload=<used>/<published>KB [resync=<n>]` |
| `channel=…` | the shared section's read/write positions, packets, rectangles, full frames and resyncs |

- the **frame timing HUD** (DevTools `F12`) gains a `stream=…` section plus the host's `fps/period/roundTrip/cmd/raster`, so the capture rate and input queueing are visible in game;
- how to read it: `fps=` is the capture rate, `period=` the gap between capture starts (much larger than `roundTrip=` means a scheduling problem, close to it means the capture itself is the ceiling), `cmd=` how long input queued (large means the UI thread is busy), `decode=` the decode time, and `stale=` / `dropped=` the lost frames.

## FAQ

**The page is blank**: check the backend first (`AuiServices.webView().unavailableReason()`). Not Windows, a missing WebView2 Runtime, or a non-x64 JVM all degrade to an empty box; a relative `src` resolves to nothing as well.

**Typing does nothing after clicking the input**: read `focus=` in `Iframe.status()`. `no` means the iframe is not its document's focused element (so AUI never forwards the characters); `yes` yet still nothing lands means the page itself has no focused element — usually because **the click missed the field**: a fixed-width site (mcmod.cn lays its content out at about 1200px) pushes its inputs past the right edge of a narrow viewport, and `box=` in `status()` is the page's CSS viewport, so anything smaller means the field has to be scrolled into view first.

**Ctrl+wheel zoomed the whole page instead of scrolling the web page**: Ctrl+wheel is consumed by AUI's viewport zoom before it is forwarded to the iframe. To let the wheel reach the page, turn off the host page's user zoom first (`<meta name="aui-viewport" content="user-scalable=false">`, see [Browser features](browser-features#page-zoom)).

**The wheel also switched the game hotbar**: the host document does not declare `aui-mouse-events: intercept`, so the native wheel leaked through to the game. Add that meta (see the [ApricityScreen meta section](apricity-screen#page-meta-configuration)).

**A large iframe looks soft**: the 4096 px per-axis cap or by-area downscaling kicked in (`raster capped by area` in `status()`). That is intentional — it protects the frame rate and trades only sharpness; the page's CSS viewport stays exact.

**Video stutters**: captures cost a fixed amount, and a heavy page on PNG drops to single-digit frame rates. Keep the default `capture="auto"` (it switches to JPEG), lower `capture-scale` if needed, or pin JPEG with `capture="fast"`.

**Changing `src` did nothing**: when the target URL equals the current one the element does not de-duplicate and still navigates; with no `src` attribute no instance is ever created. If the old content is still showing, check whether `nav=` in `status()` increased.
