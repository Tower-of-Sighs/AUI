# Changelog

## Unreleased

### Added

- Added a loader-independent Rhino host layer for built-in ECMAScript pages,
  including DOM host objects, Proxy/Reflect support, event callbacks,
  microtasks, animation frames, pointer capture, and script lifecycle cleanup.
- Added generic browser-compatible image, SVG, data-URI, audio, CSS transform,
  filter, form-control, text, and layout behavior required by Vue applications.
- Added the pinned mcui-oreui 1.2.2 Vue runtime as the only built-in Ore theme.
  The retained 32 components exclude SkinViewer and remain resource-level
  examples rather than Java-specific branches.
- Added one in-game mcui component overview alongside the existing pure-CSS Ore
  showcase, plus one self-contained customer demo (`mcui-oreui-customer-demo.html`).

### Changed

- Made Rhino a pure-Java runtime dependency for all loader targets while
  keeping KubeJS optional.
- Moved resource decoding and text raster work off the render hot path, with
  bounded main-thread publication and deterministic cache invalidation.
- Aligned generic DOM/CSS/layout semantics with Vue and mcui requirements,
  including intrinsic sizing, percentage/calc lengths, flex/grid behavior,
  stacking, clipping, transitions, generated content, and text line boxes.
- Updated Ore fonts, scoped component CSS, embedded runtime icons/audio, source
  records, license files, and integrity verification for reproducible review.

### Fixed

- Dispatch input events after text deletion so bound Vue values update immediately.
- Release AUI audio channels, buffers, and OpenAL context before Minecraft shuts down.
- Resolve percentage grid item widths against their grid area and wrap text within the assigned size.
- Fixed Forge mouse event listener wrapper collisions by using distinct subscriber names.
- Preserved native mouse consumption when a callback closes and disposes its document.
- Fixed text baseline instability, dynamic input raster updates, password and
  placeholder rendering, slider text/value updates, and fractional-pixel
  clipping.
- Fixed switch/icon edge clipping, dropdown stacking, tooltip/drawer/modal/pop
  hit testing, confirm-button activation, and pointer release/click routing.
- Fixed filter/color-matrix rendering, SVG sizing/rasterization, image cache
  lifecycle, transform hit testing, absolute-position invalidation, and
  nested overlay paint order.
- Removed frame-wide work that caused avoidable UI stalls during text input,
  slider dragging, pop display, and other reactive updates.

### Removed

- Removed legacy Ore editor assets and duplicate theme variants.
- Removed SkinViewer, generated per-component detail pages, documentation-shell
  runtime, optional browser-atlas experiments, physical-input capture tooling,
  verbose trace hooks, and generated validation residue.
- The Java/Rhino page runtime remains independent of a browser engine;
  the optional iframe element uses the system WebView through the native bridge.

### Upstream updates

### Changed

- `aui-viewport`: `mode` now defaults to `browser` instead of `gui`, so a page that omits the meta tag — or names a mode the resolver does not know, `default` included — lays out against the browser-style CSS viewport and follows the window instead of the Minecraft GUI scale. `mode=gui` (alias `mc`) still opts back into the GUI-sized viewport, and the headless resolution path follows the same split: `gui` keeps the plain available-size fallback, everything else uses the browser contract. Pages that silently relied on the old implicit GUI viewport must now say `mode=gui`; WorldWindow pages in particular must declare `mode=fixed`, since the undeclared default is now the browser width (1920) rather than a GUI-sized one.
- `<iframe>`: the WebView2 user data directory moved from `apricity/webview` to `apricity/.cache/webview`, and the resource scan, the static resource list and dev auto-reload now skip dot-prefixed directories. `apricity/` is the page root — every scan walks it, dev reload watches it for `.html`/`.css`/`.js` changes, and users keep it in version control — whereas a browser profile is tens of thousands of files of machine state that churns on its own, and its cache entries carry exactly the extensions dev reload watches, so it was listed as resources and could trip spurious reloads. It now sits next to the network cache, which was already in that cache area. An existing `apricity/webview` is orphaned: delete it, or move it to `apricity/.cache/webview` to keep its cookies.

### Fixed

- `<iframe>`: Enter did nothing in a single-line `<input>` (only `textarea` and `contenteditable` had a default-action branch), so a page's own search box could never be submitted from the keyboard even after the text landed. Enter now submits the enclosing form through `requestSubmit()` where available, so the page's `onsubmit` still runs.

## 1.2.5 - 2026-09-23

### Added

- `<iframe>` embeds a real web page: the element is backed by the system WebView (WebView2 / Edge Runtime on Windows) running in a hidden offscreen window, and its pixels stream back into a texture, so layout, clipping, transforms, stacking and hit testing treat it like any other texture-backed element such as canvas. `src` must be an absolute URL, and an iframe without `src` starts no browser. Sizing follows the CSS 2.1 replaced-element rules (300×150 default object size, `width`/`height` attributes as presentational hints, no intrinsic ratio). Pointer and keyboard input is forwarded to the page, and keys are swallowed once the element has focus. Windows x64 with the WebView2 Runtime only — elsewhere the element degrades to an empty box and `AuiServices.webView().unavailableReason()` explains why.
- `<iframe>` capture controls: `capture` (`stream`/`raw`, `lossless`/`png`, `fast`/`jpeg`, anything else = auto) selects the codec strategy — auto keeps the lossless codec while the page is still and switches to the fast one while frames keep changing — and `capture-scale` (0.25–1) trades sharpness for capture cost. `Iframe.status()` reports the host's capture and navigation counters, timing, command-queue latency and the incremental stream's send/receive statistics, and the frame-timing HUD shows the same stream line in game.
- CSS scrollbar styling: `scrollbar-width` (`auto`/`thin`/`none`/`<length>`, where `none` hides the bar but keeps scrolling) and `scrollbar-color` (`auto` or `<thumb color> <track color>`), plus the `::-webkit-scrollbar`, `-track`, `-thumb`, `-thumb:hover` and `-corner` pseudo-elements with `width`/`height`/`background-color`/`border-radius`/`display:none`.

### Changed

- `<iframe>` pixels now arrive as an incremental image stream instead of polled whole frames. The native host diffs every capture against the canvas the renderer holds on a 32×32 tile grid and publishes only the rectangles that changed into a page-file-backed shared section (`native/webview/src/frame_channel.{h,cpp}`); `FrameUpdateChannel` reads those packets on the render thread and `Iframe` rewrites and re-uploads only those regions of its texture. The per-frame whole-canvas JNI copy, the second full-frame copy into a staging array, the full-frame `memcmp` de-duplication and the full-texture upload are all gone, and nothing is ever dropped or reordered: a packet that does not fit the arena is left for the next publish, which re-diffs against what the reader is known to have. Measured on an 800×600 page with one 48×48 box moving, about 1.5% of the canvas is dirty per frame (≈4% average upload under the JPEG codec) and a completely static page publishes nothing at all. The old `AuiWebViewService.View#pollFrame()`/`Frame` API is replaced by `View#channel()`.

### Fixed

- `<iframe>`: a page that opened a link in a new window produced a real popup on the user's desktop, from a browser instance they cannot see or move. `NewWindowRequested` is now handled and the navigation happens in the same view, which is where the user expects a link inside a game UI to go.
- Closing a page left its web view running: removing an element always notified it (`onDisconnectedFromDocument`), but closing the whole document did not, so the offscreen browser, host thread, decode thread and shared section outlived the page. `Document.disposeLifecycle()` now notifies every element, and `Iframe` also checks for a disposed document.
- Mouse moves carried no held-button state, so Chromium read a drag as a hover: scrollbar thumbs inside the page would not follow the pointer and dragging a text selection broke. The pressed state now travels with the moves, a drag keeps being forwarded past the edge of the content box (clamped), the release is always delivered, and `mouseLeave` waits for the button to come up.
- `Iframe.status()` now reports `focus=` (whether the element is its document's focused element, which is what keyboard forwarding depends on) and `buttons=` (how many are held).
- DevTools no longer answers to a hardcoded `Ctrl+Shift+I`. The combination could not be rebound and fired alongside the *Toggle DevTools* keybind, so the panel is now toggled only through that keybind (unbound by default, so no key opens it until you bind one).
- Fixed `clip-path` geometry: `polygon()` now accepts the `nonzero`/`evenodd` prefix (both fill as nonzero), concave polygons are filled correctly via ear clipping, and `circle()`/`ellipse()` without an explicit radius resolve to `closest-side`.
- Fixed full-cost rendering inside the WorldWindow visibility hysteresis band. A window just past the maximum display distance stays visible for stability, but was still drawn at full LOD precision every frame, which read as a frame drop whenever the player stood at the boundary; the band now downgrades to background and border only.

### Performance

- `<iframe>` capture no longer wastes an interval while one is in flight, reads a sub-tick clock instead of `GetTickCount64`, and raises the host thread's timer resolution with `timeBeginPeriod(1)`. Those three together were the real ~32 fps ceiling: the wait granularity and a whole extra interval per frame were being charged to every capture even though the codec round trip is what actually limits it.
- Input no longer waits behind decoding. The capture completion handler used to run WIC decoding, the channel byte-order swap, the tile diff and the publish inline — on WebView2's UI thread, the same one that pumps pointer and wheel input. At 1200×900 that was 2–20 ms of pump per frame, which is what made dragging and scrolling feel sticky; those steps now run on a separate decode thread at below-normal priority, and the host thread's queue latency (`cmd=` in `Iframe.status()`) sits at 0–1 ms.
- Pointer moves are coalesced to the newest position and forwarded once per host loop instead of one cross-process `SendMouseInput` per sample; during a drag more than half the samples are merged away (`coalesced=`). Buttons, wheel and leave keep their order.
- The captured raster is capped at 1.2 million pixels (measured cost is ≈20 ms + 28 ms per megapixel), so a GUI-scaled full-screen iframe no longer asks for four million pixels every frame. The zoom is derived from the raster actually used, so the page's CSS viewport stays exact and only sharpness is traded; `Iframe.status()` reports `raster capped by area` when it applies.
- The `<iframe>` capture rate now follows the element's draw rate (a 60 fps game captures at 57/s, a 30 fps game at 27/s, a 20 fps game at 20/s). A capture reads the composited surface back through the GPU the game is drawing on, so asking for frames the display cannot show only competes with the game for it; the request rate tracks the measured frame time, with a 50 ms floor so the page never looks frozen.
- The in-flight capture depth adapts to the measured latency: four captures overlap below 45 ms, two above 60 ms, so a loaded GPU is not handed a queue of readbacks on top of the game's own work.
- The frame-timing HUD now reports the live image stream (`stream=WxH packets=… rects=… payload=…KB`) alongside the host's capture rate, cadence, round trip, input queue latency and raster size, so an in-game profile no longer needs a debugger attached.
- Up to four `CapturePreview` calls are now in flight at once. A single capture costs a fixed ~21 ms (measured identically on a static page and at 400×300, so it is the browser process's readback/encode/IPC, not a wait for a new composited frame), and that cost is mostly waiting, so overlapping multiplies throughput by about four while adding only a few milliseconds of latency. A capture that completes after a newer one has already published is dropped rather than applied out of order (`stale=` in `Iframe.status()`), which keeps the canvas monotonic. Measured on an 800×600 page repainting at 60 fps: PNG 33→61 fps, JPEG 39→61 fps; 1280×720 30→60 fps; 1600×900 ≈59 fps.
- Removed a redundant shared `BufferSource` flush per painted item node. The item backend already ends on `BufferSource.endBatch()` (shared batches first, then fixed ones), so the flush the paint node performed beforehand could only repeat work without affecting paint order.
- Skip the whole decoration path when an item has nothing to draw (no durability bar, no cooldown, no count/overlay text and no registered item decorator). This drops a per-item `GuiGraphics` allocation and an unconditional shared-buffer flush for every plain item.
- Reuse the item element's private stack copy on the paint path instead of deep-copying the `ItemStack` (and, on 1.20.1, its NBT and capabilities) once per item per frame.
- Frame-timing HUD now reports shared-buffer flush counts (`sb`) and per-item draw timing (`item`), the two numbers needed to profile pages with many `<item>` nodes. The never-incremented `imm` counter was dropped.
- Transform-only style changes no longer force a full-document relayout. A `:hover` rule that only touches `transform`/`transform-origin` marks `COMMIT_LAYOUT` without `RELAYOUT`, but the geometry commit treated both flags alike and rebuilt every element's rect (re-running layout measurement for the whole document). Such batches now take the targeted `commitTransforms` path that motion already used, so only the affected subtrees are refreshed.
- Text measurement no longer allocates a cache key and a boxed `Double` per line-width lookup. The `LinkedHashMap<LineMeasureKey, Double>` (synchronized, and with the key record rebuilt on every probe) is replaced by a set-associative table with zero-allocation hits, the per-instance memo maps are gone, and single-code-point strings are reused. Measured on a 120-node text page: 7.45 MB/frame → 0.14 MB/frame, with bit-identical layout output.
- Dropped per-element work that was computed and thrown away: the wrap branch of `Flex.computeContentSize`/`getOrComputeLayout` no longer builds participants it never reads, `getScrollbarPseudoStyles` no longer allocates a capturing lambda on every call, the gradient paths in `Graph` no longer allocate a capturing `Runnable`, and `FontDrawer` no longer reads the environment on every drawn run.
- Frame-timing HUD additionally reports full layout commits (`ly`) and targeted transform commits (`tf`), making it possible to tell a perpetual relayout apart from a transform-only change.

## 1.2.4 - 2026-08-31

### Added

- Added CSS `mask` support, multiple mask layers, mask compositing, and related clipping behavior.
- Added `contrast()`, `saturate()`, and `sepia()` filters.
- Added `mix-blend-mode`, `isolation`, HDR colors, and linear-light color handling.
- Expanded the Canvas 2D API with common drawing methods, conic gradients, path clipping, `ImageData`, and hit testing.
- Added slot item filters and composable `FilterUtil` rules across supported loaders.
- Expanded the Ore theme, documented the theme token convention, and unified the built-in `ore.css`.
- Added `word-break` support and improved text selection, context-menu selection, and word/paragraph dragging.

### Performance

- Improved canvas dirty-region tracking and texture uploads.
- Changed `mousemove` dispatch to a fixed 60 Hz schedule.
- Optimized page startup, transition warm-up, layout commits, font loading, hit testing, and caches.
- Avoided text-layout cache invalidation on every frame of hover color animations.
- Resolved animated inherited styles in depth order and changed `Size.getScaleHeight` to iterative ancestor resolution to avoid recursive calls and repeated work.

### Fixed

- Fixed a one-frame black flash when mask clipping is initialized.
- Fixed inherited animated text colors not being applied consistently while preserving text layout caches.
- Fixed several mask, stacking, blend-mode, isolation, selection, and Ore theme edge cases.
