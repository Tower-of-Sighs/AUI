# Browser-like Features

AUI is not a browser, but it equips pages with a layer of browser-style assistive behaviors: zoom, text selection, clipboard, default form actions, and scrolling. This page covers these behaviors themselves and their boundaries.

The full explanation of a page's three metas — viewport, font, and mouse interception — is in the [ApricityScreen docs](apricity-screen#page-meta-configuration), and the details of the DOM and JS API are in the [Web API docs](web-api); neither is repeated here.

## Page zoom

Both `ApricityScreen` and container Screens support browser-style zoom: Ctrl+wheel and Ctrl+`+`/`-` to zoom, Ctrl+`0` to restore the initial value. The range, step, and whether user zoom is allowed are all controlled by `zoom/min-zoom/max-zoom/zoom-step/user-scalable` in the `aui-viewport` meta.

Zooming does not stretch the picture — the framework recomputes the logical viewport, and render transforms and hit testing follow, so layout, mouse hit testing, and event coordinates always stay consistent.

Key points:

- The zoom value is stored per page path in `config/apricityui/viewport-zoom.properties` and is remembered when you reopen the page. Think of it first when troubleshooting "why is this page still zoomed in";
- `user-scalable=false` only disables user shortcuts; Java/DevTools `document.setViewportZoom(...)` is unrestricted;
- **Changing a meta's content at runtime does not re-apply it** — metas are only read when the Document is created and on `refresh()`;
- When an Overlay blocks zooming, enable `[input] viewportZoomPassThrough = true` in `config/apricityui-client.toml`; Overlays that haven't declared interception are skipped by the zoom logic. It only affects zoom — it does not let clicks pass through Overlays that genuinely intercept input.

## Two development keys

Both are rebindable MC keybinds; defaults are:

| Default key | Behavior |
| --- | --- |
| `END` | Client resource reload |
| Left `Alt` | Releases the native mouse while held |

**END** triggers a full reload: rescan resources, clear caches, and refresh all normal Documents and built-in tools. It is a development key, not a state-sync mechanism — Documents with `reloadPersistent=true` are skipped (see [Overlay docs](overlay-document)).

**Left Alt** is "hold to release", not a toggle: while in the world with no Screen open and no Overlay, holding it releases the mouse, and letting go restores the previous state. It is used to temporarily move the system cursor in debugging scenarios with in-world pages. It changes neither the viewport nor event coordinates.

## Text selection and copy

Selection targets **leaf elements** (no child elements, with actual text). CSS controls:

| CSS | Behavior |
| --- | --- |
| `user-select: text` | Drag to select |
| `user-select: all` | Click to select the whole block |
| `user-select: none` | Selection forbidden |

Put content you want people to copy into a single leaf element where possible — there is no full Selection spanning paragraphs or text nodes.

Shortcuts: drag to select, Ctrl+A select all, Ctrl+C copy, Esc clear; input controls additionally have Ctrl+X cut, Ctrl+V paste, Ctrl+Z undo. Clicking another element or another Document clears the previous selection — multiple highlights never linger.

`copy/cut/paste` are cancelable events; after `preventDefault()`, the framework skips the default clipboard action. There is no `navigator.clipboard` on pages; for direct read/write on the Java side, use:

```java
String value = Operation.getClipboardText();
Operation.setClipboardText(value);
```

## Keyboard and default form behavior

Keyboard event fields: `key/code/keyCode/scanCode/repeat/altKey/shiftKey/controlKey/metaKey`. Note it is `controlKey`, not `ctrlKey`.

The framework handles a batch of control default behaviors before scripts, consuming the native input on success:

- Button-like controls activate on Enter/Space;
- Text boxes submit their form on Enter; Enter in a textarea inserts a newline;
- number supports arrow-key and wheel stepping; range supports arrow keys;
- select has a full keyboard suite (arrows/Home/End/PgUp/PgDn/Enter/Space/Esc/prefix search);
- checkbox/radio toggle; file opens the system file picker; color opens the AUI color picker.

`preventDefault()` can intercept these default actions. Don't `click()` the same control again inside a listener — one keypress would trigger it twice.

## Mouse and scrolling

For mouse/pointer event types and coordinate rules, see the [Web API docs](web-api#events). Only scrolling is covered here:

- The wheel acts on the scrollable ancestor of the hit element by default;
- Shift+wheel prefers horizontal scrolling, falling back to vertical if there is no horizontal range;
- After a listener calls `preventDefault()`, the default scroll does not happen;
- Elements without a scroll range are never force-moved.

## Differences from a real browser

| Capability | AUI behavior |
| --- | --- |
| Network/navigation | fetch goes through the AUI resource bridge; location's navigation methods are no-ops |
| Clipboard | Ctrl shortcuts + Java's `Operation`; no `navigator.clipboard` |
| Text selection | Leaf elements and input controls, not cross-node Range |
| Meta | Read at creation/refresh; runtime DOM attribute changes are not re-applied |
| Coordinates | Events give logical coordinates — don't multiply by zoom again |
| File/color picking | MC/system pickers; no web permission model |

"Having a JS API with the same name" does not mean every browser spec detail is implemented. When reusing code across environments, write explicit fallbacks for missing capabilities.

## Common problems

**Ctrl+wheel zoomed the Overlay instead of the page**: the Overlay in front consumed the wheel. Enable `viewportZoomPassThrough`, or give that Overlay an explicit intercept/pass-through policy.

**Changed a meta and nothing happened**: metas are only read at creation and refresh. Call `refresh()` or go through the Java API.

**Ctrl+C didn't copy**: the element must be a selectable leaf element with an actual selection; input controls must have focus first; a listener that preventDefaults copy also blocks the default copy.

**Event coordinates don't line up with the crosshair**: check whether the code multiplies by renderScale / viewport zoom / devicePixelRatio more than once.

**Listeners gone after refresh**: expected behavior. Put initialization logic in `DOMContentLoaded`/`load` and rebind on every refresh.
