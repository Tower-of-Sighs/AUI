# ApricityScreen Usage Guide

ApricityScreen is AUI's wrapper around a Minecraft Screen: it loads an HTML file into a Document, draws it onto the current screen, and forwards mouse, keyboard, and wheel input to it.

## Three Page Hosts

AUI pages can be hosted by different hosts; the DOM API is exactly the same, and the only difference is "where the page appears and who provides the data":

| Host | How to create | Use case |
| --- | --- | --- |
| ApricityScreen | `new ApricityScreen(path)` directly on the client | Pure UI: settings pages, debug pages, client tools |
| ApricityContainerScreen | `ApricityUI.screen(path)` or `ApricityUI.menu(...)` | Needs real container slots or server data |
| WorldWindow | `ApricityUI.createWorldWindow(...)` | Windows rendered in the world |

Note: `ApricityUI.screen(path)` goes through a network request and ultimately opens an ApricityContainerScreen, even if the page has no container. To get a real ApricityScreen, you must call setScreen directly on the client. Containers and WorldWindows have their own documentation; this guide only covers ApricityScreen.

## Minimal Example

Put the HTML at `src/main/resources/assets/apricityui/apricity/screens/example.html`:

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body { margin: 0; color: #eee; background: #20242b; font-size: 16px; }
        .panel { width: 360px; margin: 40px auto; padding: 16px; background: #303640; }
    </style>
</head>
<body>
    <main class="panel">
        <h2>ApricityScreen</h2>
        <p id="status">Ready</p>
        <button id="reload">Click me</button>
    </main>
    <script>
        document.getElementById("reload").addEventListener("click", function () {
            document.getElementById("status").textContent = "Clicked";
        });
    </script>
</body>
</html>
```

Open it from Java:

```java
Minecraft.getInstance().setScreen(
        new ApricityScreen("screens/example.html")
                .setPauseGame(true)
                .setShowDefaultBackground(false)
);
```

The path is the logical path `screens/example.html` — do not include the `assets/apricityui/apricity/` prefix. For how logical paths are resolved and how resource directories are organized, see the [Resource Management documentation](resource-manager). If the call happens on another thread, first switch back to the client thread with `minecraft.execute(...)`.

## API

```java
public ApricityScreen(String templatePath)
public ApricityScreen setPauseGame(boolean pauseGame)           // default false; whether to pause the game
public ApricityScreen setShowDefaultBackground(boolean show)    // default false; whether to draw the vanilla MC background
public Document getLinkedDocument()
public boolean handleViewportZoom(boolean zoomIn)
public boolean resetViewportZoom()
```

`getLinkedDocument()` returns null when the Screen has not yet been initialized by Minecraft, when the HTML is missing or fails to parse, or after the Screen is closed. Don't cache it in the constructor.

`handleViewportZoom` / `resetViewportZoom` are bounded by the meta values `user-scalable`, `min-zoom`, and `max-zoom`. To set an arbitrary zoom value from Java, use `document.setViewportZoom(1.25)` — it is not restricted by `user-scalable=false`, but is still clamped into the min/max range.

## Lifecycle

```text
new ApricityScreen(path)      // only stores the path, doesn't read the HTML
  -> setScreen -> init()      // creates the Document, parses HTML/CSS/JS
  -> DOMContentLoaded -> load
  -> render() / input / resize()
  -> onClose()                // dispatches unload to body first, then removes the Document
```

A few easy traps:

- `init()` may be called repeatedly (e.g. on resize), and each call rebuilds the Document;
- `Document.refresh()` keeps the Document itself but rebuilds the DOM and re-runs scripts. After a refresh, all old Element references and event listeners become invalid — use `document.getRefreshGeneration()` / `isCurrentGeneration(gen)` to check the generation;
- When overriding `init()` / `onClose()` / `removed()`, you must call super, otherwise the Document is never created or never cleaned up;
- Don't call `Document.create(same path)` again in a subclass — that leaves two Documents alive at once, doubling both rendering and input.

## Page Meta Configuration

These three meta tags are AUI's page-level configuration, **shared by all hosts** (Screen, Overlay, Container, and WorldWindow all read them). The full explanation is maintained only here and is not repeated in other documents.

### aui-viewport: Logical Viewport

```html
<meta name="aui-viewport" content="mode=browser">
```

`content` is a comma-separated key-value list. `mode` defaults to `gui`:

| Mode | Aliases | Behavior |
| --- | --- | --- |
| `gui` | mc, default | Uses the Minecraft GUI size as the logical viewport; suits small MC-style UIs |
| `browser` | css, web | Uses the CSS viewport width, scaled to the window width; suits web-like settings pages |
| `window` | native, screen, fullscreen | Uses a CSS width derived from the monitor; horizontal layout is not recomputed when the window changes |
| `fixed` | — | Fixed design size: `mode=fixed,width=427,height=249,scale=fit` |

In fixed mode, `scale` can be a number (`scale=1`), `fit` (fit proportionally into the window, alias `contain`), `gui` (alias `mc`), or `window` (alias `native`). `width`/`height` default to 427×249.

All modes support zoom parameters:

```html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
```

| Option | Default | Notes |
| --- | --- | --- |
| `zoom` | 1 | Initial zoom; also the reset target of Ctrl+0 |
| `min-zoom` / `max-zoom` | 0.5 / 3 | User zoom range |
| `zoom-step` | 0.1 | Zoom step per increment |
| `user-scalable` | true | Whether shortcut-key zoom is allowed |

When zooming is allowed: Ctrl+wheel and Ctrl+`+`/`-` zoom, Ctrl+`0` resets. The zoom value is stored per page path in `config/apricityui/viewport-zoom.properties`, so reopening the page remembers the last value.

### aui-font-mode: Font Mode

```html
<meta name="aui-font-mode" content="web">
```

| Value | Default font size | Suitable for |
| --- | ---: | --- |
| `mc` | 9 | Small controls in native MC style |
| `web` | 16 | Layout at web-logical font sizes |
| `web-scaled` | 16 | Web font sizes + MC glyph proportions when drawing; **default value** |

It affects the default font size and how text is rasterized; an explicit `font-size` in CSS always wins. Pages migrated from browser designs generally use the `web` + `mode=browser` combination.

### aui-mouse-events: Input Interception

```html
<meta name="aui-mouse-events" content="intercept">
```

`intercept` (also accepts `block`, `true`, `yes`, `on`, `1`) prevents mouse events over hit regions from being passed to the underlying Minecraft input or other Documents. Without it, HTML events are still dispatched — the native input just isn't force-consumed.

Two things to note: interception works per hit region — invisible, clipped, or `pointer-events: none` elements do not gain hits from it; if you want the whole page to consume input, make sure the interactive area covers the viewport. This is a "whether to consume native input" switch, not an event listener switch.

## Input Events

In the page, it's the familiar pattern:

```javascript
button.addEventListener("click", function (event) { ... });
input.addEventListener("input", function () { console.log(input.value); });
```

Mouse, wheel, keyboard, focus, and form events are all available. Event coordinates are already Document logical coordinates — **do not multiply them by GUI scale or page zoom again**. For the full list of event types, fields, and pitfalls, see the [Web API documentation](web-api).

The Ctrl+wheel zoom target is the topmost Document under the mouse. For overlay pages, the client config `[input] viewportZoomPassThrough = true` (`config/apricityui-client.toml`) allows zoom to pass through overlays that did not declare intercept.

## Getting the Current Screen's Document

Java:

```java
if (Minecraft.getInstance().screen instanceof ApricityScreen screen) {
    Document document = screen.getLinkedDocument();
}
```

KubeJS client script:

```javascript
const document = ApricityUI.getCurrentScreenDocument();
```

Both only have a value when the current Screen really is an ApricityScreen. What `ApricityUI.screen(path)` opens is a container Screen, which returns null — don't use path matching as a substitute for this method.

## When You Need a Container

ApricityScreen has no Menu and no real slots. If the page needs to operate the player inventory, block entity containers, or server data, go through the container entry point:

```java
ApricityUI.menu(player, "screens/inventory.html").bind(binding -> binding.player());
```

Or from a client script, `ApricityUI.screen("screens/inventory.html")`. See the [Container documentation](container) for details.

## END Reload

During development, pressing END rescans resources and refreshes all normal Documents: scripts re-run and the DOM is rebuilt. So top-level JS variables, dynamically added nodes, and input field values are all lost — data that must survive should live on the Java/KubeJS side and be written back to the page on `load`. `document.setReloadPersistent(true)` lets a standalone Overlay skip the reload, but don't do this for Screen-bound Documents — it easily leaves page code and resource versions out of sync.

## FAQ

**Blank page**: check in order — is the path a logical path; is the file under `assets/apricityui/apricity/` or `run/apricity/`; is the extension `.html`; did you press END after editing; search the log for `[AUI Resource]` / `[AUI HTML]` / `[AUI Document]`.

**Mouse events not firing**: first check whether the element is really under the mouse and whether it is excluded by `display:none` / clipping / `pointer-events`; then check whether the page is covered by a higher Document. Do not manually multiply event coordinates by renderScale.

**Ctrl+wheel zoomed the wrong page**: check whether there is an overlay under the mouse, the `viewportZoomPassThrough` config, and the page's own `user-scalable` setting.

**State lost after END**: expected behavior, see the previous section.

**Layout/text offset after window changes**: pick one viewport mode and let the framework handle resize; don't manually compensate for scale in both CSS and Java at the same time.

## Performance Tips

- Create only one Document per Screen; update existing elements instead of calling `Document.create()` every frame;
- Use CSS transition/animation for animations — don't rebuild the body every frame;
- Reuse nodes for long lists;
- END is a development reload key, not a runtime state-sync mechanism.
