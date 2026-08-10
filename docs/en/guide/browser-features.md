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

Selection is **document-level**: one selection can span inline children (e.g. `<div>Hello <b>World</b></div>`) and multiple sibling elements, and distinct units are joined with a newline when copied. CSS controls:

| CSS | Behavior |
| --- | --- |
| `user-select: text` | Drag to select |
| `user-select: all` | Click to select the whole block |
| `user-select: none` | Selection forbidden |

This is AUI's own selection implementation, not browser Selection/Range — pages cannot obtain `Selection`/`Range` objects (see the [Web API docs](web-api)); cross-unit joining and other details should not be written against browser specs.

Shortcuts: drag to select, Ctrl+A select all, Ctrl+C copy, Esc clears the selection; input controls additionally have Ctrl+X cut, Ctrl+V paste, Ctrl+Z undo. Focus changes do not clear the selection. It is cleared only by Esc, by clicking a non-selectable area, or by starting a new selection elsewhere; clicking inside the already-selected text does not collapse it — it becomes the start of a selection drag.

Double-click selects the whitespace-delimited word under the caret; triple-click selects the whole unit (paragraph). Copied text preserves the original whitespace — consecutive spaces are not collapsed (`<div>a  b</div>` copies as `a  b`); a `<br>` acts as a line break; soft-wrapped lines do not produce newline characters in the copied text; distinct block units join with a newline. `text-align: justify` renders left-aligned, and caret positioning follows the same rule.

Selection rendering: selected glyphs keep their **original font color** (no longer forced to white), and the highlight background uses `selection-color` (default `#0078D7`, so the classic white-on-blue look is unchanged). The highlight follows the **actually rendered lines**: under `line-clamp` it only covers the visible lines, and under `text-overflow: ellipsis` it stops at the real text instead of the synthetic `...`. Clamping/ellipsizing affects only painting, not the selection — `selectAllInnerText()` on a clamped or ellipsized element still copies the full underlying text.

Mouse behaviors: middle-clicking an editable input pastes the current document selection at the caret (Linux primary-selection style; it shares the keyboard insertion path, so maxLength is respected and input events are dispatched, and an existing selection inside the input is replaced). Dragging from inside an existing selection (past the ~4px slop) drags the selected text itself: dropping it on an editable input copies it in (the source selection stays intact), while dropping on a non-editable target cancels the drag and keeps the selection. Text selection does not trigger container auto-scrolling.

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
| Text selection | Document-level selection spanning inline children and multiple elements; no Selection/Range JS API |
| Meta | Read at creation/refresh; runtime DOM attribute changes are not re-applied |
| Coordinates | Events give logical coordinates — don't multiply by zoom again |
| File/color picking | MC/system pickers; no web permission model |

"Having a JS API with the same name" does not mean every browser spec detail is implemented. When reusing code across environments, write explicit fallbacks for missing capabilities.

## Common problems

**Ctrl+wheel zoomed the Overlay instead of the page**: the Overlay in front consumed the wheel. Enable `viewportZoomPassThrough`, or give that Overlay an explicit intercept/pass-through policy.

**Changed a meta and nothing happened**: metas are only read at creation and refresh. Call `refresh()` or go through the Java API.

**Ctrl+C didn't copy**: the element must be a selectable unit with an actual selection; input controls must have focus first; a listener that preventDefaults copy also blocks the default copy.

**Event coordinates don't line up with the crosshair**: check whether the code multiplies by renderScale / viewport zoom / devicePixelRatio more than once.

**Listeners gone after refresh**: expected behavior. Put initialization logic in `DOMContentLoaded`/`load` and rebind on every refresh.
