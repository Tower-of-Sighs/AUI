# Secondary Development: Custom Elements and KubeJS Bindings

For mod authors who want to add things to AUI. Three extension points: custom DOM elements, KubeJS global bindings, and the frame-timing HUD. For page-side APIs see the [Web API doc](web-api); for usage of the built-in extension tags see the [Extension Elements doc](extension-elements).

## Two Boundaries to Hold First

**Client thread**: Document, Element, layout, Screen, and WorldWindow are all client-side UI state. When coming back from network callbacks, Futures, or async tasks, switch threads first:

```java
Minecraft.getInstance().execute(() -> {
    Document document = ApricityUI.createDocument("overlays/status.html");
    if (document != null && document.body != null) {
        document.body.setTextContent("ready");
    }
});
```

Resource decoding can be async; DOM commits and texture uploads must happen on the client/render thread.

**Refresh generation**: `refresh()` rebuilds the entire tree — every Element reference and listener you stored becomes invalid. Store the generation in async callbacks and verify it on return:

```java
long generation = document.getRefreshGeneration();
Minecraft.getInstance().execute(() -> {
    if (!document.isCurrentGeneration(generation)) return;
    Element element = document.getElementById("status");
    if (element != null) element.setTextContent("loaded");
});
```

## Registering Custom Elements

Extend `Element`, add `@ElementRegister`, and provide a `public (Document)` constructor:

```java
@ElementRegister(MyPanel.TAG_NAME)
public final class MyPanel extends Element {
    public static final String TAG_NAME = "MY-PANEL";

    public MyPanel(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        // attributes, children, and listeners are only migrated by this point; initial attributes can't be read in the constructor
        String mode = getAttribute("mode");
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        // custom drawing; the phases are SHADOW / BODY / BORDER
        super.drawPhase(poseStack, phase);
    }
}
```

Then register the scan package during mod initialization (constructor or earlier):

```java
ApricityUIRegistry.scanPackage("com.example.mod.ui");
// or scanPackages("com.example.mod.ui", "com.example.mod.client.element");
```

Key points and pitfalls:

- Tag names are registered uppercase and are case-insensitive; **include a mod prefix** (`EXAMPLE-PANEL`) to avoid collisions; re-registering the same tag overwrites the earlier one, and scan order is not a stable priority;
- Scanning relies on Forge `ModFileScanData` and only covers mod classes Forge has scanned; it must be called before AUI element registration — registering after the first Document is created won't retroactively convert already-parsed pages;
- Don't read attributes in the constructor; do initialization in `onInitFromDom`; instantiation failure falls back to a plain Element (the page still works, the extended behavior is gone), but exceptions in `onInitFromDom` and drawing have no such safety net;
- Element registration is not hot-reloadable — restart the client after changing registration logic; END only rescans resources;
- Elements that don't need custom drawing don't need to override `drawPhase`; CSS works as usual;
- When doing custom drawing: get sizes from `Box.of(this)` / `getBoundingClientRect()`; handle zero size and not-yet-ready resources first; don't create DynamicTextures, parse strings, or trigger layout every frame; once resources are asynchronously ready, update internal state and call `document.markDirty(this, ...)`.

## Registering KubeJS Bindings

Add `@KJSBindings` to a static-method class, and the class enters scripts as a global object:

```java
@KJSBindings(value = "ExampleAui", modId = "examplemod", isClient = true)
public final class ExampleAuiBindings {
    private ExampleAuiBindings() {}

    public static String hello(String name) {
        return "Hello, " + name;
    }
}
```

```javascript
// in the page script
console.log(ExampleAui.hello("Apricity"));
```

Register during mod initialization: `KubeJS.scanPackage("com.example.mod.kjs")`.

- If `value` is empty, the simple class name is used; if `modId` is filled in, registration only happens when that mod is loaded;
- `isClient = true` goes into client scripts (Document/Toast/WorldWindow and the like), `false` into server scripts (containers, player data) — it is a registration filter, **not** a side-safety guarantee: if a client binding class references MC client classes, don't register it on the server side;
- Give global names a mod prefix; express failure with null/Optional and document it clearly;
- Keep binding methods public and static, use parameter and return types Rhino can convert reliably; don't shove complex DOM traversal into per-frame script calls.

After changing annotations or scan packages, restart KubeJS/the client — END only reloads page resources.

## frameTimingHud: Frame-Timing HUD

`config/apricityui-client.toml`:

```toml
[debug]
frameTimingHud = true
```

Shows the most recent 120 AUI frame samples in the top-left corner:

```text
max 2.31 ms  min 0.42 ms  avg 0.88 ms  g 12 img 3 imm 1
```

`max/min/avg` are AUI document rendering times; `g`/`img`/`imm` are the latest frame's flush counts for Graph batches, image batches, and immediate drawing. It only measures the AUI drawing segment — it is not total frame time or FPS, and does not include script execution cost.

How to use it: keep the page stable until the window fills up → note `avg`/`max` and the batch counts → change only one variable → compare again. High `g/img/imm` means batches are being interrupted or aren't being merged — it's a clue for locating the problem, not a conclusion. Extension elements should cache invariant geometry/texture state; don't do heavy work in `drawPhase`.

## Common Failures

**Custom tag is still a plain Element**: is the class inside the scanned package or its subpackages? Does it have a `public (Document)` constructor? Was scanPackage called early enough? Restart the client to verify after changes.

**Page initialization errors after registration**: read the AUI error log with the path and tag name. Is attribute initialization written in the constructor — move it to `onInitFromDom`. When drawing, check sizes, resource handles, and document validity.

**KJS global object doesn't exist**: is KubeJS loaded? Was scanPackage called? Is the mod for modId present? Does the script's runtime side match `isClient`? Restart after changes.

**HUD values fluctuate up and down**: built-in pages like DevTools and the resource manager themselves change batching. Use the 120-frame rolling avg for trends and max for spikes — don't treat single-frame fluctuation as a regression.
