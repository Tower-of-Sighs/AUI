# Built-in UI Library

`com.sighs.apricityui.ui` is the framework's built-in Java component library: dialogs, context menus, toasts, tooltips, a color picker, a file picker, and translation utilities. DevTools and the resource manager are built with them, and your own code can reuse them directly. Components create ordinary AUI DOM that participates in layout, rendering, and hit testing — no page needs to include any JS library.

## Four General Rules

**Host**: `DialogWindow`, `ContextMenu`, `Tooltip`, and `ColorPicker.pickIn` append their elements to the body of the Document you pass in. The host Document must stay valid until the component closes; remove it yourself when done.

**Standalone / embedded**: `ColorPicker.pick` and `FilePicker.pick` are standalone mode (the component builds its own built-in template Document); `pickIn` is embedded mode (drawn into your Document). Standalone templates are internal framework resources — don't modify them as if they were your pages.

**Threads**: Components build DOM and register events on the client thread. Future callbacks run on the completion thread; touch the UI inside them only after `Minecraft.getInstance().execute(...)`.

**Single instance**: `ContextMenu`, `Tooltip`, `ColorPicker`, and `FilePicker` each have only one active instance globally at a time — opening a new one closes the old (a picker's old Future completes with `Optional.empty()`). Cleanup entry points: `ContextMenu.closeActive()`, `Tooltip.hide()`, `ColorPicker.closeActive()`, `FilePicker.closeActive()`.

## DialogWindow: Dialog Shell

```java
DialogWindow dialog = DialogWindow.open(
        document,
        DialogWindow.Options.of("SETTINGS", 480, 320, true),  // title, width, height, resizable
        () -> ToastManager.show("closed")                      // onClose, nullable
);
dialog.content().append(myContent);   // content area
dialog.window().append(myFooter);     // put the button bar pinned to the bottom here
dialog.close();
```

- Non-positive width/height means compute from defaults/content; `resizable` enables hot zones in all eight directions (minimum 360×240); the full Options constructor can also override the CSS class names of each part and enable a maximize button;
- The title bar is draggable; dragging and resizing are disabled while maximized;
- The position is in Document logical coordinates — don't write screen coordinates directly into it when the page has viewport scaling;
- Closing only removes the dialog DOM and leaves the host Document untouched; content scrolling and footer layout are your own CSS's job (for a fixed-height window, remember to give the content `overflow:auto; min-height:0`).

## ContextMenu: Context Menu

```java
ContextMenu.show(document, new Position(mouseX, mouseY), List.of(
        ContextMenu.Item.header("FILE"),
        ContextMenu.Item.action("OPEN", () -> openFile()),
        ContextMenu.Item.action("COPY PATH", ContextMenu.Icons.COPY, "Ctrl+C", () -> copyPath()),
        ContextMenu.Item.separator(),
        ContextMenu.Item.action("DELETE", ContextMenu.Icons.DELETE, "Del", () -> deleteFile()).dangerous()
));
```

- Item factories: `header` / `separator` / `action(label, [icon,] [shortcut,] action)`; `.disabled()` disables, `.dangerous()` applies danger styling; after a click, the menu closes first, then the action runs;
- `ContextMenu.Icons` provides a set of built-in SVG constants (OPEN/COPY/DELETE/RENAME, etc.), and you can also pass a custom SVG string;
- `Options(className, style, onClose)` overrides styling and the close callback;
- It comes with a transparent backdrop: clicking outside the menu, scrolling, or Esc all close it, and outside clicks are intercepted; the menu is automatically clamped inside the viewport;
- `Position` uses Document logical coordinates — mouse coordinates from a Screen must first be converted with `document.screenToDocumentPosition(...)`.

## ToastManager: Notifications

```java
String id = ToastManager.show("Saved");
String id2 = ToastManager.show("Loading...", 0);   // duration<=0 never auto-closes; you must dismiss it
ToastManager.dismiss(id2);
ToastManager.clear();
```

Toasts are hosted on a framework-owned built-in Overlay — you don't manage a host. Defaults are about 2600ms and click-to-dismiss. For full customization, use `ToastOptions(durationMs, dismissOnClick, backgroundColor, textColor, borderColor, customStyle)`.

For translated notifications, use `showTranslation(key)` — it creates a TRANSLATION node that follows language switches; don't pass a key to the plain `show()`, which displays it as literal text.

## Tooltip: Tooltips

```java
// show directly (global single instance; pointer-events:none so it doesn't block input)
Tooltip.show(document, new Position(x, y), "Copy the selected path");
Tooltip.moveActive(new Position(x2, y2));
Tooltip.hide();

// bind to an element
Tooltip.Binding binding = Tooltip.bind(button, "Open resource manager");
Tooltip.Binding b2 = Tooltip.bind(button, () -> computeText(), Tooltip.Options.defaults());  // dynamic text
Tooltip.Binding b3 = Tooltip.bindTranslation(button, "tooltip.my.key");                       // translation key
binding.close();   // must be closed before the element is removed or the page reloads
```

It flips automatically to stay inside the viewport, showing to the left/above the pointer near the right/bottom edges. `Options(className, style, offsetX, offsetY, maxWidth)` defaults to 14/18/320.

When the mouse position comes from an MC Screen rather than Document events, use `Tooltip.moveActiveFromScreen(...)`, which converts coordinates internally — this is the correct entry point on scaled pages.

## ColorPicker: Color Picker

```java
ColorPicker.pick("#8b5cf6").thenAccept(result ->
        Minecraft.getInstance().execute(() ->
                result.ifPresent(color -> applyColor(color))));

// embed: the anchor decides the popup position (prefers right, falls back to left)
ColorPicker.pickIn(document, anchorElement, currentColor).thenAccept(...);
```

Returns `CompletableFuture<Optional<String>>`: APPLY → `of(value)`; CANCEL, clicking outside, or being superseded by a new picker → `empty()`. **The cancel branch must be handled** — don't only write the success path.

Three editing modes — HEX / RGB / HSL — plus Alpha; the result format follows the current mode (`#rrggbb[aa]` / `rgb[a](...)` / `hsl[a](...)`). Unparseable initial values fall back to black. The eyedropper button is a placeholder; there is no screen color-picking feature.

## FilePicker: Resource File Picker

This is not a system file dialog — it lists the resources scanned by AUI (resource packs, the local apricity directory, the development directory):

```java
FilePicker.pick(FilePicker.Options.html("SELECT HTML", false))   // false=exclude resource pack files
        .thenAccept(result -> result.ifPresent(sel ->
                Minecraft.getInstance().execute(() -> openHtml(sel.path()))));
```

- Options factories: `html(title, includeResourcePack)` / `htmlTranslation(key, ...)` / `any(title, ...)`; or `new Options(title, Set.of(".css"), includeResourcePack)` directly (an empty extension set = accept everything; the new-file entry is only offered when html is included);
- Result `Selection(path, layer, localPath)`: `path` is the logical path, `layer` is the source layer, `localPath` is the local absolute path — **localPath is null for resource pack files**, so null-check before writing;
- Cancellation (close/cancel buttons/clicking the backdrop/closeActive) completes with `empty()`;
- `pickIn(document, options, ClientLoader.listFinalStaticResources())` is the embedded mode, for DevTools, editors, and tests.

## UiTranslations: Java-Side Translation

```java
String title = UiTranslations.translate("devtools.apricityui.edit_meta");
button.setAttribute("aria-label", title);
```

Use this where a TRANSLATION node can't be placed (aria-label, window titles, logs). Resolution order: MC `Component.translatable` → built-in en_us.json → returns the original key if not found (never returns null). For DOM content use `<translation>`; Toast/Tooltip/FilePicker each have their own `*Translation` entry points.

## Z-Order and Cleanup

Internal z-order is roughly: Tooltip 11000 > ContextMenu 9500 > DialogWindow 9000. This is only meaningful within the same Document — don't expect it to out-draw other Documents or world windows; for cross-Document occlusion, adjust how the host is created.

Clean up in reverse order before destroying the host:

```java
tooltipBinding.close();
if (dialog != null && dialog.isOpen()) dialog.close();
ContextMenu.closeActive();
ColorPicker.closeActive();
FilePicker.closeActive();
document.remove();
```

## FAQ

**A Future never completes**: The user never finished, or the host was removed directly without closeActive. Handle the cancel branch.

**Menu/Tooltip position is offset**: Screen coordinates were used as logical coordinates. Use `screenToDocumentPosition` or `moveActiveFromScreen`.

**Dialog content is clipped**: DialogWindow only provides the frame — add `overflow:auto` and flex properties to the content area yourself.

**Files missing in FilePicker**: Were the resources scanned? Does the extension filter match? Resource pack files require `includeResourcePackFiles=true`.

**Can't write back to a selected resource pack file**: Expected behavior — it's a read-only source. Pick files from the local directory for editing.

**A translation key displays as-is**: Wrong entry point — the plain-text entries don't resolve keys; use the respective Translation variants.
