# Mod-Specific API (KJS / Java Entry Points)

Beyond the in-page script API, AUI provides two layers of mod interfaces: KubeJS bindings (global `ApricityUI`) and a unified Java entry point (`com.sighs.apricityui.ApricityUI`). For in-page DOM, events, fetch, and Canvas, see the [Web API docs](web-api); for the semantics of each page host, see the corresponding topic doc — this page does not repeat them.

## Three Things to Get Straight First

**Paths**: All APIs use logical paths (`screens/example.html`) — never write the `assets/apricityui/apricity/` prefix, and certainly never a disk path. See the [Resource Management doc](resource-manager) for the rules.

**Side**: The KJS `ApricityUI` registers two different sets of methods in client scripts and server scripts; even methods with the same name cannot be called cross-side:

| Script location | Can use | Cannot touch |
| --- | --- | --- |
| Client scripts | Document, Toast, screen, WorldWindow | Server-side container binding |
| Server scripts | `menu(player, path).bind(...)` | Document, Toast, WorldWindow |

**Creating ≠ showing**: `createDocument(path)` only creates and registers a Document (an Overlay will render it automatically); it does not open a Screen. For a Screen use `new ApricityScreen(path)` or `ApricityUI.screen(path)`; for a container use `menu(...).bind(...)`; for a world window use `createWorldWindow(...)`. `createInWorldDocument(path)` only creates a world Document — it does not display itself as a window.

## KJS Client API

**Document**:

```javascript
var doc = ApricityUI.createDocument("overlays/status.html");   // returns null if the resource is missing
ApricityUI.getDocument("overlays/status.html");                // all instances of the same path, returned as a list
ApricityUI.getDocumentByUUID(uuid);
ApricityUI.getAllDocument();
ApricityUI.getCurrentScreenDocument();   // only has a value when the current screen really is an ApricityScreen
ApricityUI.removeDocument("overlays/status.html");             // removes all instances of the same path
ApricityUI.getWindow();
```

You can create multiple instances at the same path, each with a different UUID; to manage an individual instance, keep the returned Document object. `getCurrentScreenDocument()` returning null for a container Screen is normal — what `screen(path)` opens is a container Screen.

**Toast**:

```javascript
var id = ApricityUI.toast("Load complete");
var id = ApricityUI.toast("Save failed", 5000);
var id = ApricityUI.toast("Resource updated", 4200, "#20242b", "#ffffff", "#6fb4d6", true, "font-size: 14px;");
//                        message        duration(0=no auto-close) bg  text  border  click-to-close  custom style
ApricityUI.dismissToast(id);
ApricityUI.clearToasts();
```

The return value is a Toast ID, not an element ID.

**Screen**:

```javascript
ApricityUI.screen("screens/settings.html");   // opens a UI-only container Screen via the server
ApricityUI.closeScreen();
```

`screen(path)` does not open an ApricityScreen directly on the client — see the [ApricityScreen doc](apricity-screen) for the difference. The old `openScreen(path)` is deprecated.

**WorldWindow**:

```javascript
var win = ApricityUI.createWorldWindow("world/notice.html", 10.5, 64.0, -3.5, 64);
// can also add maxDisplayDistance, or yaw, pitch[, roll] (in degrees)
win.setFacing(true);
win.setFollow(true);
win.setFollowFactor(0.35);
win.document.getElementById("title").setTextContent("Base");

ApricityUI.removeWorldWindow(win);
ApricityUI.clearWorldWindows();
```

Creation registers it immediately; removal destroys the Document along with it. For the full distance, LOD, and occlusion semantics, see the [WorldWindow doc](world-window).

## KJS Server API

There is a single entry point — containers:

```javascript
ApricityUI.menu(player, "screens/machine.html")
    .bind(function (binding) {
        binding.blockEntity(pos)
            .slot("#fuel")
            .filter(FilterUtil.item(Items.COAL).or(FilterUtil.tag("c:coals")))
            .player();
    });
```

| BindingBuilder method | Corresponding HTML container id |
| --- | --- |
| `player()` | `player` |
| `saveddata()` / `saveddata(name)` / `saveddata(name, cap)` | `saved_data` |
| `blockEntity(pos)` / `blockEntity(pos, cap)` | `block_entity` |
| `entity(id)` / `entity(id, cap)` | `entity` |

The argument of `saveddata("machine_data")` is a server-side data name, not an HTML id. A non-player step uses `.slot("slot.fuel").filter(FilterUtil)` to restrict insertion into slots matched by an existing CSS selector; `#fuel` and `slot[slot-index="0"]` are also valid, and an HTML id is not required. `player()` exposes neither `slot(...)` nor `.filter(...)`. `FilterUtil` provides `ANY`, `NONE`, `EMPTY`, `item`, `tag`, `custom`, `allOf`, `anyOf`, and `not`, while `and` / `or` / `negate` compose existing filters. Filtering affects only this menu's insertion path and retains the underlying inventory restrictions. For the complete rules on container ids, slots, and data sources, see the [Container doc](container). The old `openScreen(player, ...)` is deprecated.

## Java API

The unified entry point `com.sighs.apricityui.ApricityUI` can do everything the KJS bindings can:

```java
// Document / Overlay
Document doc = ApricityUI.createDocument("overlays/status.html");
ApricityUI.getDocument(path);  ApricityUI.removeDocument(path);
ApricityUI.getDocumentByUUID(uuid);  ApricityUI.getAllDocument();

// Screen / container
ApricityUI.screen("screens/settings.html");                    // client request, UI-only
ApricityUI.menu(serverPlayer, "screens/machine.html")          // server side, real container
        .bind(binding -> binding.blockEntity(pos).player());
ApricityUI.closeScreen();

// WorldWindow
WorldWindow win = ApricityUI.createWorldWindow("world/notice.html", position, 64);
ApricityUI.removeWorldWindow(win);
```

The details are spread across the topic docs — don't look for them here:

- Document lifecycle, refresh generation, DOM operations → [Overlay doc](overlay-document) and [Web API doc](web-api)
- `new ApricityScreen(path)`, pause/background/scale → [ApricityScreen doc](apricity-screen)
- Container declarations, advanced `ApricityScreenNetworkHandler.openScreen(...)` → [Container doc](container)
- WorldWindow rotation, Follow/Facing, LOD, coordinate conversion → [WorldWindow doc](world-window)
- Loader / ClientLoader / HTML resource loading → [Resource Management doc](resource-manager)
- Custom element registration (`@ElementRegister`, scan packages) → [Secondary Development doc](secondary-development)
- Built-in components such as DialogWindow, ContextMenu, ToastManager, ColorPicker → [Built-in UI Library](ui-library)

## Threads, Null Values, and Refresh

**Threads**: Creating Documents, modifying the DOM, opening/closing Screens, and operating on WorldWindows must all happen on the client thread; in network callbacks and Futures, wrap UI work in `Minecraft.getInstance().execute(...)` first. The server-side `menu` is called on the server thread.

**Null values**: These APIs express failure with null — don't use try-catch instead of null checks: `createDocument` (missing template), `getElementById` (element does not exist or the reference is stale), `getCurrentScreenDocument` (wrong Screen type), WorldWindow projection/hit tests (invisible, occluded, out of range).

**Refresh**: `refresh()` is a full page rebuild, not an update mechanism. For high-frequency data, change the textContent/attributes of existing elements. After a refresh, all old Elements, listeners, and Observers become invalid; in async callbacks, store `getRefreshGeneration()` first, then verify with `isCurrentGeneration(gen)` before writing.

When updating the DOM from Java in a way that may trigger script helper logic, wrap it in `Document.runWithContext(document, () -> ...)` to establish the current Document context.

## Client Config Keys

Config file `config/apricityui-client.toml`, read from `ApricityUIConfig.CLIENT` on the Java side:

| Key | Purpose |
| --- | --- |
| `debug.autoReload` | Auto-reload when the development directory changes |
| `debug.frameTimingHud` | Frame timing HUD |
| `debug.remoteDebug` | Local external debugger |
| `debug.resourceManagerWorldWindow` | Open the resource manager as a world window |
| `input.viewportZoomPassThrough` | Ctrl+scroll zoom passes through unintercepted Overlays |
| `worldWindow.maxDisplayDistance` | Default display distance for world windows |
| `worldWindow.lodEnabled` / `fullDetailDistance` / `reducedDetailDistance` | World window LOD |
| `worldWindow.depthOffsetScale` | World window depth offset scale |
