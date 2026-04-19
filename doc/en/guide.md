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

In that case, use the server-authoritative entry:

```javascript
let plan = ApricityUI.bind()
    .primaryBind("main").savedData("apricityui_demo", "demo_key", 27)
    .bind("player").player()
    .build()

ApricityUI.openScreen(player, "demo/index.html", plan)
```

Java uses the same API model:

```java
OpenBindPlan plan = ApricityUI.bind()
    .primaryBind("main").savedData("apricityui_demo", "demo_key", 27)
    .bind("player").player()
    .build();

ApricityUI.openScreen(player, "demo/index.html", plan);
```

One key rule:

The top-level `container id` values in the template must match the names used in `OpenBindPlan`.

For example:

```html
<container id="main"></container>
<container id="player"></container>
```

Then the bind plan must also use `main` and `player`.

---

### Common Screen Bindings

#### 1. Player Inventory

```javascript
let plan = ApricityUI.bind()
    .primaryBind("player").player()
    .build()
```

#### 2. SavedData Container

```javascript
let plan = ApricityUI.bind()
    .primaryBind("main").savedData("apricityui_demo", "demo_key", 27)
    .bind("player").player()
    .build()
```

#### 3. Block Entity Inventory

```javascript
let plan = ApricityUI.bind()
    .primaryBind("machine").blockEntity(100, 64, 200, "up")
    .bind("player").player()
    .build()
```

#### 4. Entity Inventory

```javascript
let plan = ApricityUI.bind()
    .primaryBind("entity_inv").entity("00000000-0000-0000-0000-000000000000")
    .bind("player").player()
    .build()
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

ApricityUI.removeWorldWindow(window);
```

Equivalent KJS client API:

```javascript
let window = ApricityUI.createWorldWindow("demo/world.html", 0, 65, 0, 180, 100, 16)

ApricityUI.removeWorldWindow(window)
```

This flat world-space UI supports:

1. World-coordinate positioning
2. Rotation
3. Scaling
4. Depth testing and occlusion

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
4. Container titles only read the text of the first child element, not legacy title attributes.

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

### Suggested Starting Order

If you are not sure where to begin, this sequence is usually the smoothest:

1. Preview the visual design as an Overlay
2. Turn it into a Screen
3. Move it into the world only if needed with WorldWindow
4. Switch to FollowFacingWorldWindow only when stronger focus is needed
