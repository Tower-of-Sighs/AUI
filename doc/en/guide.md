## Quick Start

### UI Types

ApricityUI currently has three common usage patterns:

1. Overlay
2. Screen
3. In-world image UI, meaning a UI rendered as a flat plane in the game world

The first two are the most common. The third is more specialized and presentation-oriented.

---

### 1. Overlay

Overlay is the lightest mode.

You only need to create a `Document`, and it is added to the render queue directly.

Common KJS or client-script entry points:

```javascript
let doc = ApricityUI.createDocument("demo/index.html")
ApricityUI.removeDocument("demo/index.html")
```

The Java side can now use the same main-class entry points:

```java
ApricityUI.createDocument("demo/index.html");
ApricityUI.removeDocument("demo/index.html");
```

If you prefer lower-level APIs, those still work:

```java
Document.create("demo/index.html");
Document.remove("demo/index.html");
```

This mode is especially suitable for:

- HUD
- Status tips
- Minimap sidebars
- Temporary floating panels
- Debug overlays

Its advantages are straightforward:

1. Easy to open and close
2. Multiple documents can coexist
3. No need to switch away from the current screen

Of course, if too many overlays exist, they will overlap, so positioning and layering still need to be managed by you.

---

### 2. Screen

Screen is the more standard "open a UI page" workflow.

If you only want to preview a UI and do not need real container binding, opening it directly on the client is enough.

KJS:

```javascript
ApricityUI.openScreen("demo/index.html")
ApricityUI.closeScreen()
```

Java:

```java
ApricityUI.openScreen("demo/index.html");
ApricityUI.closeScreen();
```

This is suitable for:

- Pure presentation panels
- Config pages
- Help pages
- Prototype previews with fake data

If you need real slots, real containers, or real inventory data, do not use the client-only path.

In that case, use the server-authoritative entry. Container information is driven by `<container>` element attributes in
the template, and the client `openScreen` automatically extracts container declarations and sends them to the server:

```javascript
// Container info is declared by <container> elements in the template
// Client openScreen automatically extracts and sends declarations to the server
ApricityUI.openScreen("demo/index.html")
```

Java uses the same API model:

```java
// Container info is declared by <container> elements in the template
// Client openScreen automatically extracts and sends declarations to the server
ApricityUI.openScreen("demo/index.html");
```

One key rule:

The top-level `container id` values in the template must match the names used in container declarations.

For example:

```html
<container id="main"></container>
<container id="player"></container>
```

Then the declarations must also use `main` and `player`.

---

### Common Container Declarations

Container declarations are driven by `<container>` element attributes (`id`, `bind`, `size`, `primary`) in the template.
Here are common examples:

#### 1. Player Inventory

```html
<container id="player" bind="player"></container>
```

#### 2. SavedData Container

```html
<container id="main" bind="saved_data" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

#### 3. Block Entity Inventory

```html
<container id="machine" bind="block_entity" size="9" primary="true"></container>
<container id="player" bind="player"></container>
```

#### 4. Entity Inventory

```html
<container id="entity_inv" bind="entity" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

For entity binding, the target entity must actually expose an item capability, otherwise the screen cannot open correctly.

---

### 3. In-World Image UI

This mode is suitable when the UI should appear as a flat plane in the world, for example:

- Sign-like information boards
- Machine-side display panels
- World hint panels
- Floating explanation cards

Creating an in-world document alone is not enough, because that only creates the document object.

To make it render, attach it to a `WorldWindow`:

```java
WorldWindow window = ApricityUI.createWorldWindow("demo/world.html", position, 180, 100, 16);
window.setMaxDisplayDistance(32);

ApricityUI.removeWorldWindow(window);
```

Equivalent KJS client API:

```javascript
let window = ApricityUI.createWorldWindow("demo/world.html", 0, 65, 0, 180, 100, 16)
window.setMaxDisplayDistance(32)

ApricityUI.removeWorldWindow(window)
```

This flat world-space UI supports:

1. World-coordinate positioning
2. Rotation
3. Scaling
4. Depth testing and occlusion
5. An independent maximum display distance

When `setMaxDisplayDistance()` has not been called, a window uses the global
default from `[worldWindow] maxDisplayDistance` in
`config/apricityui-client.toml`. The default is `128` blocks; set it to
`2147483647` for unlimited distance.
Calling `setMaxDisplayDistance(distance)` overrides the global value for that
instance; `clearMaxDisplayDistanceOverride()` restores the global default.

LOD is disabled by default. To enable it globally, set these values in the
`[worldWindow]` section of `config/apricityui-client.toml`:

```toml
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
```

With these defaults, distances up to 16 blocks use `FULL`, distances over 16
and up to 48 use `REDUCED`, and farther windows use `MINIMAL`. To enable LOD
for only one window, call:

```java
window.setDisplayPrecisionDistances(16, 48);

// Or force one level (also available from KubeJS as a string):
window.setDisplayPrecision(WorldWindowDisplayPrecision.REDUCED);
// window.setDisplayPrecision("minimal");
```

`REDUCED` keeps text and primary content while omitting shadows, filters,
backdrop filters, and clip-path effects. `MINIMAL` keeps only basic backgrounds
and borders. `maxDisplayDistance` still takes precedence and hides the complete
window beyond its limit.

The default scale is `0.02f`, roughly meaning 50 pixels per block.

So when designing world-space UI, do not copy oversized browser layouts directly.

---

### 4. FollowFacingWorldWindow

This is a specialized extension of `WorldWindow`.

Instead of pinning a plane rigidly into the world, it keeps a base position while partially following the player's view and always facing the camera.

It is useful for cases such as:

- Labels above entities
- Floating info cards
- Observation-oriented test panels
- World hints that should stay easy to read

Java:

```java
FollowFacingWorldWindow window = ApricityUI.createFollowFacingWorldWindow(
    "demo/follow.html",
    position,
    180,
    100,
    16,
    0.3f
);
```

KJS:

```javascript
let window = ApricityUI.createFollowFacingWorldWindow(
    "demo/follow.html",
    0, 65, 0,
    180, 100,
    16,
    0.3
)
```

The last parameter, `followFactor`, is clamped to `0.0 ~ 1.0`.

A practical interpretation:

1. `0` means almost no follow behavior, only normal facing
2. `1` means strong follow toward the projected view position
3. `0.2 ~ 0.5` usually feels the most natural

---

### Which One Should You Use?

If you just want something in a corner of the screen, use Overlay.

If you need a proper interactive interface, use Screen.

If you want the UI to appear in the world, use WorldWindow.

If you also want it to drift slightly with the viewpoint and always face the player, use FollowFacingWorldWindow.

For most projects, Overlay and Screen are enough.

---

### Important Container Semantics

These matter a lot, especially for Screen:

1. `slot` is now unified into one tag. Inside a container it defaults to real bound slots, outside a container it defaults to virtual slots.
2. A `bind="player"` container automatically gets 36 player slots if no explicit bound slots are declared.
3. `recipe` is display-only and never participates in real container binding.
4. `container` has no built-in title mechanism; write and lay out titles as ordinary DOM nodes.

---

### A Useful Size Reminder

Under Minecraft's default GUI scale, the commonly usable GUI area is roughly `427 * 240`.

Compared with the same size in a browser preview, it will feel noticeably larger in-game.

So when designing UI:

1. Make panels slightly tighter than browser mockups
2. Keep font size, padding, and corner radius restrained
3. Small and clear is usually better than large and screen-filling

This is useful for both Overlay and Screen.

---

### Screen Viewport Meta

Screen documents can choose their logical root viewport with an `aui-viewport` meta tag:

```html
<meta name="aui-viewport" content="mode=gui">
```

Supported modes:

1. `mode=gui`: default Minecraft GUI-sized viewport, compatible with the old behavior.
2. `mode=browser`, `mode=css`, or `mode=web`: use a browser-like CSS viewport with a monitor-derived fixed width. The height follows the current window's CSS height, while the render scale stays fixed, so resizing the window does not change horizontal layout.
3. `mode=window` or `mode=native`: use the monitor CSS viewport and fit it into the current window. The logical CSS size stays stable while the render scale follows the window size.
4. `mode=screen` or `mode=fullscreen`: compatibility aliases for `mode=window`.
5. `mode=fixed,width=427,height=249`: use an explicit fixed logical viewport.

Fixed mode also accepts `scale=1`, `scale=gui`, `scale=window`, or `scale=fit`.

All screen viewport modes accept browser-like zoom options:

- `zoom=1`: initial zoom.
- `min-zoom=0.5`: minimum user zoom.
- `max-zoom=3`: maximum user zoom.
- `zoom-step=0.1`: Ctrl zoom increment.
- `user-scalable=true`: whether Ctrl zoom is allowed.

When user scaling is enabled, Screen documents support:

- `Ctrl + mouse wheel`: zoom in/out.
- `Ctrl + =` or `Ctrl + +`: zoom in.
- `Ctrl + -`: zoom out.
- `Ctrl + 0`: reset to the meta `zoom` value.

Examples:

```html
<meta name="aui-viewport" content="mode=window">
<meta name="aui-viewport" content="mode=browser">
<meta name="aui-viewport" content="mode=fixed,width=427,height=249">
<meta name="aui-viewport" content="mode=fixed,width=1920,height=1080,scale=fit">
<meta name="aui-viewport" content="mode=gui,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1">
```

---

### Scrollbar Styling

ApricityUI currently treats browser scrollbar pseudo-elements as unsupported CSS extensions:

```css
::-webkit-scrollbar {}
::-webkit-scrollbar-track {}
::-webkit-scrollbar-thumb {}
```

Documents can still scroll normally through `overflow`, `scrollTop`, `scrollLeft`, mouse wheel input, and the framework's native scroll handling. Styling the scrollbar thumb/track with `::-webkit-scrollbar*` is ignored for now.

If a page needs a fully custom visual scrollbar, build it as ordinary DOM elements synchronized with scroll position instead of relying on WebKit scrollbar pseudo-elements.

---

### Suggested Starting Order

If you are not sure where to begin, this sequence is usually the smoothest:

1. Preview the visual design as an Overlay
2. Turn it into a Screen
3. Move it into the world only if needed with WorldWindow
4. Switch to FollowFacingWorldWindow only when stronger focus is needed
