# Overlay Document Usage Documentation

An Overlay is a Document not attached to any Minecraft Screen: `ApricityUI.createDocument(path)` creates one and adds it to a global list, and the client renders it automatically during the GUI/HUD draw phase. It suits HUDs, Toasts, notification bars, floating panels, fullscreen masks, and dev tools. It does not open a new Screen and has no container slots — for slots, see the [container documentation](container).

## Differences from ApricityScreen

| | Overlay | ApricityScreen |
| --- | --- | --- |
| Creation | `ApricityUI.createDocument(path)` | `new ApricityScreen(path)` |
| Replaces the current Screen | No | Yes |
| In-game with no Screen | Visible | — |
| After a Screen opens | Normal Overlays hide; persistent ones stay visible | Drawn as the current Screen |
| Typical uses | HUD, Toast, persistent toolbars | Settings pages, fullscreen UI |

## Minimal Example

`overlays/status.html`:

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
</head>
<body>
    <div id="status">Loading...</div>
</body>
</html>
```

Java-side management:

```java
private static Document document;

public static void open() {
    if (document != null && document.isActive()) return;
    document = ApricityUI.createDocument("overlays/status.html");
    if (document == null) return;
    Element status = document.getElementById("status");
    if (status != null) status.setTextContent("Ready");
}

public static void close() {
    if (document == null) return;
    document.remove();   // just setting the variable to null does not remove the Document
    document = null;
}
```

Creation and modification must happen on the client thread; in network callbacks, wrap with `Minecraft.getInstance().execute(...)` first. KubeJS client scripts use the same set of APIs (`ApricityUI.createDocument(...)`), while a page's own script uses the in-page `document` directly.

## API

```java
Document ApricityUI.createDocument(String path)        // returns null if the resource doesn't exist
void     ApricityUI.removeDocument(String path)        // removes all instances of that path
ArrayList<Document> ApricityUI.getDocument(String path)
Document ApricityUI.getDocumentByUUID(String uuid)
List<Document> ApricityUI.getAllDocument()
```

The same path can create multiple instances, which is why `getDocument` returns a list and `removeDocument(path)` removes them all at once. To close only your own instance, keep the returned value and call `document.remove()`.

Common Document methods: `getPath()`, `getUuid()`, `isActive()`, `isDisposed()`, `getRefreshGeneration()`, `getElementById()`, `querySelector(All)`, `remove()`, `refresh()`, `setReloadPersistent(boolean)`.

## Display Timing and Persistence

| Current state | Normal Overlay | `reloadPersistent=true` |
| --- | --- | --- |
| In-game, no Screen | Visible | Visible |
| Vanilla Screen open | Hidden | Visible |
| ApricityScreen / container Screen open | Hidden | Drawn on behalf of that Screen |

```java
overlay.setReloadPersistent(true);   // for things like toasts and global notifications that must stay visible
```

Persistence does two things: keeps drawing while a Screen is open, and skips the full refresh on an END reload. It is **not** immortality — `remove()` still destroys it; and it is **not** a caching switch — after an END reload, changes to the resource files are not automatically applied to it; you must `refresh()` manually or recreate it.

There is also `setManuallyRendered(true)`: it removes the Document from global drawing and input dispatch so the caller draws it themselves. This is meant for custom render hosts (preview windows and the like). Don't touch it for normal Overlays, or the page will be created successfully but never appear.

## Lifecycle Notes

- **Parsing on creation**: `createDocument` immediately parses the HTML/CSS/JS, computes layout, runs scripts, and dispatches DOMContentLoaded/load. The cost is not small — create once on open, don't create every frame.
- **Modification**: after DOM changes the framework does incremental updates. Do batch modifications in one go; don't spread them across many frames.
- **Removal**: `document.remove()` cleans up focus, hover, Observers, and other state. Unlike Screens, it does **not** dispatch unload to the body — invoke any cleanup logic explicitly yourself.
- **Reload**: an END reload refreshes all normal Overlays; the DOM and JS state are rebuilt and old Element references become invalid. Keep the state you want to preserve on the Java/KubeJS side and write it back in `load`.

## Layering: Between and Within Documents

**Within a single Document** it's plain CSS: `position` + `z-index`. But z-index cannot control layering across Documents.

**Between different Documents**, ordering follows the root/body node's `translateZ`; larger values are in front:

```css
body { transform: translateZ(100px); }
```

Set it once on either html or body (not both), and plan non-conflicting layer ranges for Overlays with different responsibilities. With equal translateZ, ordering falls back to creation order, later ones in front — don't rely on this.

**Top layer within a Document**: elements like popups and dropdown menus that need to escape ancestor overflow clipping can be marked from the Java side:

```java
dialog.setTopLayer(true);
```

It keeps the original DOM relationships and event paths, only drawing after normal content and without being clipped by ancestors. It only affects the inside of its own Document.

## Input and Pass-Through

Input is dispatched to Documents front to back; if it isn't hit or consumed it passes further down. Event coordinates are logical coordinates — don't multiply by any scale.

For a fullscreen Canvas in an Overlay, or when reading the mouse yourself: size from `document.getViewportSize()` and read the cursor via `document.getMouseDocumentPosition()` (at GUI scale ≥ 6 MC GUI coordinates and document coordinates diverge — see [Coordinate Conversion in gui Mode](apricity-screen#coordinate-conversion-in-gui-mode)).

Two common patterns:

**Modal mask** — enable interception via meta (see the [ApricityScreen meta section](apricity-screen#page-meta-configuration)) and cover the whole viewport with the mask:

```html
<meta name="aui-mouse-events" content="intercept">
```

```css
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.55); }
```

**Info display layer** — the container passes through while the content is clickable; the standard Toast structure:

```css
.toast-layer { pointer-events: none; }
.toast { pointer-events: auto; }
```

Ctrl+scroll zoom targets the topmost hit Document; when `config/apricityui-client.toml` has `[input] viewportZoomPassThrough = true`, persistent Overlays that didn't declare intercept are skipped by the zoom logic.

## Common Patterns

**HUD**: a normal Overlay without persistence. It hides automatically when the inventory opens and comes back when it closes.

**Toast/global notifications**: one persistent Overlay to which message nodes are appended and removed. The built-in ToastManager uses exactly this pattern — don't create one Document per message.

**Modal dialog**: three layers — a persistent Overlay + a fullscreen mask + a top-layer dialog. When closing the dialog, prefer removing just the dialog node; only remove the Document when the whole Overlay is no longer needed.

**Multi-instance floating windows**: creating from the same template twice gives two independent instances; keep a reference to each and `remove()` each individually.

## FAQ

**Overlay doesn't show**: was it called on the client thread? Is the path correct? Did it return null? Is a Screen open while persistence is off? Did you accidentally set `manuallyRendered`? Is the root element zero-sized or hidden?

**It covers the wrong page**: check the body's translateZ; child z-index has no effect across Documents.

**Unclickable or passing through**: check the intercept meta, pointer-events, whether the element has an actual size, and whether a higher Document caught the event.

**No update after END reload**: with persistence on, that's expected behavior — refresh manually or recreate.

**Still responds to events after closing**: you probably only set the variable to null without calling `document.remove()`.

## Performance Tips

- Reuse one Document per Overlay type; high-frequency updates should only change text/class/attributes;
- Keep Toasts and popups managed inside a single host Document to reduce cross-Document hit-testing;
- Keep persistent Overlays lightweight — they draw on top of every Screen;
- Purely decorative layers should use `pointer-events: none` to stay out of input dispatch.
