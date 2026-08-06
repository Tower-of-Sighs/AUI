# WorldWindow — In-World Windows

WorldWindow renders an HTML Document as a flat plane inside the Minecraft world: info signs, machine external screens, entity overhead labels, floating hints. It is neither a Screen nor an Overlay — it has world coordinates, orientation, perspective scaling, and block occlusion, and interaction works via crosshair raycasts. The page itself is still a normal AUI page; DOM, events, and form capabilities all work.

## Minimal Example

World windows are recommended to use `mode=fixed` with an explicitly declared logical size; otherwise the browser-common 1920 width will turn straight into a gigantic world panel:

```html
<meta name="aui-viewport" content="mode=fixed,width=240,height=96,scale=1">
<meta name="aui-font-mode" content="web">
<meta name="aui-mouse-events" content="intercept">
```

Java creation (KubeJS client scripts use the same API, passing coordinates directly as x, y, z):

```java
WorldWindow window = ApricityUI.createWorldWindow(
        "world/notice.html",
        new Vec3(10.5, 65.0, -4.0),
        32                          // interaction ray distance
);
window.setMaxDisplayDistance(64);   // camera display distance
window.setDepthTest(true);

ApricityUI.removeWorldWindow(window);   // remove when no longer needed; the Document is destroyed with it
```

`ApricityUI.createWorldWindow(...)` creates the Document **and registers it**. Manually calling `new WorldWindow(...)` only creates without registering — you must call `WorldWindow.addWindow(window)` yourself. Calling only `Document.createInWorld(path)` gets you just a Document, and nothing will ever show. This is the standard answer to "the resource exists but there is no window in the world."

These are client APIs. If the position data lives on the server, sync it to the client yourself first.

## Lifecycle

- A refresh (END reload) rebuilds the DOM and reruns scripts, but **preserves the WorldWindow instance** — position, orientation, distance, and other settings are not reset;
- Old Element references become invalid after a refresh, as usual;
- Changing spatial properties like position, rotation, or depth does not require rebuilding the Document — just call the setters;
- Don't call `addWindow` twice on the same instance; it would draw twice and receive events twice.

## Size: viewport, Auto-Scaling, Manual Scaling

The window's logical size comes from the viewport meta (`getWidth()/getHeight()` read exactly that); don't pass width/height through the constructor — the legacy constructors with `width/height` are deprecated and override the viewport configuration.

| viewport mode | Suitability in the world |
| --- | --- |
| `fixed` | Preferred; stable logical size, easy to control physical size |
| `browser` / `window` | Default width is very large; the world plane becomes absurdly big |
| `gui` | Varies with the GUI scale setting; only use for compatibility with old pages |

**Auto-scaling**: if `setScale` has never been called, the framework computes a conservative world scale from the camera projection and distance and caches it, so the panel doesn't jump in size every frame. It is recomputed when the viewport changes or `setPosition` is called. `getScale()` may return a fallback value before the window's first successful render — don't read it right after creation.

**Manual scaling**: `window.setScale(0.02f)` means 1 logical pixel = 0.02 world units and disables auto-scaling; `clearScaleOverride()` restores automatic scaling. It only changes the physical ratio, not the DOM layout.

## Position and Orientation

The position is a world coordinate, and the plane is centered on that point:

```java
window.setPosition(entity.position().add(0, entity.getBbHeight() + 0.25, 0));  // above the entity's head
```

For rotation, prefer `setRotation(yaw, pitch, roll)` in degrees. **Pitfall**: the parameter order of `setRotation(Vec3)` is `(pitch, yaw, roll)`, not yaw first — this is for historical compatibility. KubeJS's three-argument overload is already converted for you at the binding layer; pass yaw, pitch, roll normally. For quaternions use `setOrientation(Quaternionf)` (it makes a copy).

`setFacing(true)` faces the camera every frame, suitable for labels; don't enable it for signs with a fixed orientation.

## Follow and Facing

Two independent switches:

| Configuration | Effect | Scenario |
| --- | --- | --- |
| Both off | Fixed position, fixed orientation | Signs, machine external screens |
| Follow only | Position partially follows along the view direction | Panels that need some sense of direction yet stay readable |
| Facing only | Fixed position, always faces you | Fixed-point labels |
| Both on | Partial follow + faces you | Entity overhead info, floating hints |

Follow does not bind to the camera: it projects the base position onto the view direction and then moves closer by `followFactor` (0~1). 0.3 is a common value for entity labels. When the base position is behind the camera it won't be force-pulled in front, avoiding the panel flying through your face when turning around. The old `FollowFacingWorldWindow` subclass is deprecated — it's just a normal window with these two switches on.

## The Two Distances

| Parameter | What it controls |
| --- | --- |
| `maxDistance` (constructor parameter / `setMaxDistance`) | How far the crosshair/mouse ray can reach |
| `maxDisplayDistance` (`setMaxDisplayDistance`) | Beyond this camera distance the window is neither rendered nor hit |

When no instance-level display distance is set, the global config `config/apricityui-client.toml`'s `[worldWindow] maxDisplayDistance` is used (default 128; set `2147483647` for unlimited). `clearMaxDisplayDistanceOverride()` restores the global value. With Follow enabled, the distance is computed from this frame's followed position.

## Occlusion and Z-Fighting

`setDepthTest(true)` (default): blocks can occlude the window; a window behind a wall is neither rendered nor interactive — on hit, a block-visibility raycast is performed from the camera to the hit point. Turning it off makes an information layer that always floats above world surfaces, still limited by display distance and frustum culling.

The entire Document is clipped to the window rectangle; children's shadows and filters won't draw outside it.

Multiple coplanar windows, or windows flush against block surfaces, will Z-fight. First give the window position a small offset; if that's not enough, tune the dynamic depth step:

```java
window.setDynamicDepthStep(0.00035f, 0.003f, 2.0f, 32.0f);  // near step, far step, near distance, far distance
```

There is also a global `[worldWindow] depthOffsetScale`. Prefer the defaults and don't crank it up right away, or the window will visibly drift relative to world surfaces.

## Distance LOD

Three precision levels plus an automatic one: `FULL` (draws everything), `REDUCED` (keeps text and main content), `MINIMAL` (only background and borders), `AUTO` (automatic by distance, the default).

LOD is globally disabled by default (AUTO equals FULL). To enable:

```toml
[worldWindow]
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
```

Within 16 blocks FULL, 16~48 REDUCED, beyond 48 MINIMAL; beyond `maxDisplayDistance` it simply isn't displayed (not downgraded to MINIMAL).

Per-window override: `window.setDisplayPrecisionDistances(16, 48)`, or force a level with `window.setDisplayPrecision("reduced")` (the string supports auto/full/reduced/minimal). Setting it back to `AUTO` clears the instance thresholds.

**LOD only affects drawing**: layout, animations, events, and hit-testing all keep running. It is not a "disable distant windows" mechanism — use `maxDisplayDistance` to cut off distant windows you don't want to interact with.

## Input: Crosshair, Events, Consumption

On every render the framework saves that frame's projection matrices; when input arrives it unprojects through the same transforms into Document coordinates and hitTests — so hits on rotated, perspective, and following windows are all accurate.

**In first person with the mouse grabbed, the interaction point is the crosshair at screen center**, not the GLFW virtual cursor. If you want players to click a panel, place the panel where the crosshair ray can reach.

The event types are the usual set (mousemove/down/up/click/dblclick/contextmenu/wheel/over/out/enter/leave + pointer compatibility). `clientX/clientY` are already unprojected into Document logical coordinates — **don't multiply them by world scale, renderScale, or devicePixelRatio again**.

To consume native input (clickable world buttons usually need this), add `<meta name="aui-mouse-events" content="intercept">` in the HTML; see the [ApricityScreen meta section](apricity-screen#page-meta-configuration) for the rules.

Two limitations:

- While a Minecraft Screen is open, mouse/wheel dispatch to world windows is blocked by Screen input priority. World windows are not Overlays, and `reloadPersistent` can't make them draw over a Screen either;
- When multiple windows' projections overlap there is no topmost ordering — **every hit window receives the event**. By design, don't let interactive areas overlap.

## Coordinate Conversion API

For debugging; don't build your own 2D scale conversions:

| Method | Purpose |
| --- | --- |
| `getDocumentPositionAtScreen(pos)` | GUI coordinates → Document coordinates; returns null when not rendered, out of bounds, out of range, occluded, or when the matrix is not invertible |
| `projectDocumentPosition(pos)` | Document coordinates → GUI coordinates (GUI-scaled, not framebuffer pixels) |
| `projectDocumentRect(x,y,w,h)` | Document rect → conservative GUI bounding box (larger than the actual area after rotation; cannot be used for precise hit-testing) |
| `getRealPos()` / `getRealPos(screenPos)` | The current mouse/crosshair mapped to this Document's event coordinates; null on miss |

## Dynamic Updates

`window.document` is a public field; modify the DOM directly:

```java
Element status = window.document.getElementById("status");
status.setTextContent("HP: " + health);
```

For high-frequency updates only change the necessary elements; don't rebuild the Document every tick.

## Complete Patterns

**Fixed info sign**: fixed viewport + `setRotation` for a fixed orientation + depth test on + a suitable display distance.

**Entity overhead label**: position above the head, `setFollow(true)` + `setFollowFactor(0.3f)` + `setFacing(true)`; update the position in a client tick while the entity moves, and `removeWorldWindow` immediately when the entity is gone.

**Large numbers of windows**: limit the count, keep `maxDisplayDistance` modest, and enable LOD. LOD only saves drawing, not layout — actively remove distant instances.

**Debugging**: the in-game command `/aui worldwindow` creates a test window in front of the crosshair, letting you tune distance, depth, LOD, scale, and Follow/Facing in real time. There's also an acceptance test page: place an armor stand named `auitest` in the world, and the test generator will create a Follow/Facing window above its head.

## FAQ

**No window in the world**: most likely you only called `Document.createInWorld` or forgot `addWindow`.

**Panel too big/too small**: check the logical width/height of the viewport meta; are you using browser/window mode; did you call `setScale`; are you still using the legacy width/height constructor.

**Blocked by walls / unresponsive**: depth test is on by default. If `setDepthTest(false)` makes it interactive, occlusion was the cause. Also distinguish `maxDistance` (ray) from `maxDisplayDistance` (display).

**Mouse and crosshair don't line up**: in first person, trust the crosshair. Don't multiply event coordinates by any scale.

**Wrong rotation direction**: use `setRotation(yaw, pitch, roll)`; if you use the Vec3 overload, remember the order is `(pitch, yaw, roll)`; with Facing enabled, a fixed orientation naturally has no effect.

**Distant windows go blank while logic still runs**: that's LOD's MINIMAL level. To draw everything force FULL; to hide, tune the display distance or remove it — MINIMAL is not a hiding mechanism.

**Multiple windows respond to the same click**: there is no topmost ordering; stagger the interactive areas.

**Flickering when flush against surfaces**: Z-fighting; first move the position, then fine-tune `setDynamicDepthStep`.

## Performance Tips

- Fixed viewport + small logical size; don't paste a full web-sized page into the world;
- For large numbers of windows always configure display distance and LOD;
- High-frequency data should only change text/attributes;
- Remove windows immediately when entities are destroyed or chunks unload;
- Use large transparent areas, complex filters, shadows, and high-frequency animations sparingly;
- Purely display windows don't need intercept.
