# Built-in DevTools

An in-game page debugger that can inspect normal Screens, Overlays, container pages, and — when hit conditions are met — WorldWindow documents: view the DOM, edit styles, check logs, and save changes back to source files. The external debug protocol and Node tools are a separate chain; see [Additional Tools](tools).

Open/close: `F12` or `Ctrl+Shift+I` (rebindable in MC's controls settings). On the Java side, use `DevTools.toggle()` / `ensureOpen()` / `selectDocument(doc)` / `selectElement(element)`.

## Target document

DevTools itself is also a Document (`devtools/devtools.html`), and what it inspects is another **target document**. When opened, it auto-selects a target in this order: current ApricityScreen → current container Screen → most recently created valid document. Use the dropdown below the toolbar to switch manually — multiple instances can share the same path and are distinguished by UUID prefix.

The close button to the right of the dropdown **removes the target page** (calls `remove()`); it does not "stop inspecting". Use the dropdown to look at a different page — don't click it.

## UI at a glance

**Toolbar** (left to right): drag panel, save, reload document, Meta edit, pick, console/inspector toggle, settings, close.

**DOM tree** (left): click to select an element; hovering highlights that element's margin/border/padding/content regions on the target page. Right-click menu: copy outerHTML, copy selector, add child element, hide (runtime `display:none`), copy, delete, attributes panel. The root element cannot be deleted.

**Pick mode**: after clicking the pick button, the cursor becomes a crosshair; moving over the target page highlights the hit element, and clicking selects it and exits. It only hits the currently selected target document; WorldWindow targets go through world projection, so the crosshair must actually hit the plane for picking to work.

**Inspector** (three tabs on the right):

- **Attributes**: edit, add, and delete attributes; Enter commits;
- **Styles**: inline style editing (individual declarations can be temporarily disabled; color values have a color picker), read-only computed sizes, and the list of matched CSS rules (including source file, override relationships, and !important);
- **Box Model**: read-only margin/border/padding/content values; go back to Styles to change sizes.

## Runtime editing and saving

All edits **take effect immediately but are not written to files**. History is kept per document; Ctrl+Z undoes and Ctrl+Shift+Z / Ctrl+Y redoes (when an input box has focus, the shortcuts belong to the input box).

Saving writes back to the source file, and only to **writable local resources**: resources from resource packs, remote sources, or the production-environment dev directory are all refused; failures show a Toast explaining why, and nothing is ever written to a guessed path.

The "Save DOM tree" option in the save confirmation window decides how much is written:

- Unchecked: only the modified CSS rules are written back (possibly across multiple CSS files);
- Checked: the current DOM is serialized back to HTML, and element additions/removals and attribute changes are written too.

After saving, the source file has changed but the current document **is not rebuilt automatically** — click "Reload document" or press END to verify the final result. Note that reloading discards all unsaved runtime changes, and all old Element references become invalid.

## Meta editor

The toolbar Meta button edits the current HTML's metas (only for writable local source files). You can change the charset, the three aui-* metas, and the current runtime zoom (the ZOOM field, applied immediately via `setViewportZoom`). For the meaning of the three metas, see [the meta section of ApricityScreen](apricity-screen#page-meta-configuration). Other meta tags are preserved as-is. Saving triggers a resource reload.

## Console

The top-bar button switches to the console. Logs come from page script console output, forwarded client logs, and DevTools itself, with level filtering, keyword search, and clear supported.

The input box is **not an arbitrary JS interpreter** — it is a restricted command set:

| Command | Effect |
| --- | --- |
| `help` | Command help |
| `clear` / `cls` | Clear |
| `$("#save")` / `$$(".btn")` | Query the first / all matching elements |
| `select(12)` | Select an element by pre-order number (starting from 1) |
| `inspect` | Toggle pick mode |
| `copy(text)` | Copy to clipboard |
| `dir(obj)` / `keys(obj)` / `table(arr)` | Object overview / key names / table |
| `count()` / `tree` | Node statistics / text DOM tree |
| `echo` / `warn` / `error <text>` | Write a log |

Plus simple arithmetic and literals. Enter executes, Up/Down arrows cycle history, Ctrl+L clears. To drive real script logic, check page logs or use the [external debug tools](tools).

## Settings

The settings button directly edits `config/apricityui-client.toml`: debug switches (autoReload, frameTimingHud, remoteDebug, Resource Manager WorldWindow mode), input (viewportZoomPassThrough), worldWindow (distance, LOD, depth offset). For the meaning of each key, see [the config table in the mod API docs](apricity-api#client-config-keys).

## A standard debugging workflow

1. Open the target page, press F12, and confirm the dropdown has the right instance selected;
2. First check the Console for script errors;
3. Locate the element via picking or the DOM tree, and troubleshoot through the Attributes / Styles / Box Model layers;
4. Edit styles directly to verify hypotheses, using undo to compare;
5. Save once confirmed (check DOM tree if you changed structure);
6. Reload the document and verify the final result after the source file is parsed.

## Common problems

**No target available**: is the page still active and does it have a body? A page that was removed must be recreated.

**My changes reverted**: runtime edits don't write to files — you must save. Resource-pack and remote CSS can't be written.

**References invalid after reload**: expected behavior; query the elements again.

**Pick highlight doesn't line up**: for normal pages, check zoom and viewport; for WorldWindow, check window position, distance, occlusion, and the crosshair. Event coordinates are already document coordinates — don't multiply by zoom again.

**The console won't run my JS**: it isn't a JS console; see the command table above.
