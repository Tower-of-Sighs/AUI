# Extension Elements

Beyond standard HTML, AUI registers a set of extension tags. They are all ordinary DOM elements that participate normally in CSS, layout, hit-testing, and script manipulation. They solve one common class of problem: **drawing game resources and animations into the page**.

For the capability boundaries of standard elements see [HTML/CSS Coverage](html-css-coverage); for containers/slots/recipes see the [Container doc](container); for registering your own elements see the [Secondary Development doc](secondary-development) — none of that is repeated here.

## How to Choose

| Need | Use |
| --- | --- |
| Static images in the page directory (PNG/GIF/WebP) | Standard `<img>` / CSS `background-image` |
| Minecraft registry textures (items, block textures) | `<texture>` |
| Atlas frame-by-frame animation | `<sprite>` |
| Localized text | `<translation>` |
| Vector icons, lines, curves | `<svg>` |
| Pixel-level, chart, per-frame recomputed visuals | `<canvas>` |
| Item slots, inventories, recipe previews | `<container>` / `<slot>` / `<recipe>` (Container doc) |

All custom-drawn elements have no intrinsic size — remember to give them a stable `width`/`height` via CSS or attributes, otherwise the layout will jump once resources finish loading asynchronously. Don't `refresh()` every frame in scripts while waiting for resources — the framework marks a repaint once resources are ready.

## texture: Minecraft Textures

```html
<texture src="minecraft:textures/item/diamond.png" style="width: 32px; height: 32px;"></texture>
<texture src="examplemod:textures/block/machine.png" blur="true"></texture>
```

- `src` is a ResourceLocation in `namespace:path` form that goes straight through the MC texture system — it is **not** a page logical path, nor a disk path; for images in the page directory use `<img>` or `<sprite>`;
- `blur` only enables blurred rendering when written exactly as `true`;
- If src is invalid or the element has zero size, the texture is not drawn, but CSS backgrounds and borders still render; changing `src` takes effect immediately.

## sprite: Atlas Frame Animation

Plays a horizontal or vertical atlas as a `steps()` frame animation — good for loading spinners, button states, particles, idle motions:

```html
<sprite class="coin"
        src="images/coin-strip.png"
        steps="8"
        direction="right"
        duration="640ms"
        loop="infinite"></sprite>
```

`src` resolves against the current HTML's **logical path** (`images/x.png` inside `screens/home.html` resolves to `screens/images/x.png`; use `/images/x.png` to go back to the resource root), and HTTPS images are also allowed. This is exactly the opposite of texture's ResourceLocation — don't mix them up.

| Attribute | Default | Description |
| --- | --- | --- |
| `steps` | none | Positive integer frame count; treated as a static background if missing or invalid |
| `direction` | `right` | `right`/`left`/`up`/`down`; determines both the atlas layout and the frame movement direction |
| `duration` | `1s` | CSS time value (`250ms`, `1.5s`) |
| `loop` | `infinite` | `infinite` or a positive integer play count |
| `steps-mode` | `end` | `start` / `end`, corresponding to the CSS `steps()` timing mode |
| `autoplay` | `true` | `false`/`0`/`no`/`off` disable it; when disabled the `initialframe` frame is still shown |
| `initialframe` | `0` | Starting frame; out-of-range values are clamped to the last frame |
| `fit` | `none` | `none`/`contain`/`cover`/`stretch`, mapped to background-size |

For a horizontal atlas, single-frame size = width/steps × height; the reverse for vertical — every frame in the atlas must have the same size, otherwise the derivation will be wrong.

**Note**: Sprite manages `background-image/position/size/repeat` and the `animation` family of properties in the inline style, overriding your handwritten inline declarations of the same name (other CSS animations are merged and preserved). If you want full control over the background animation yourself, don't use sprite — use a plain div + CSS.

## translation: Localized Text

```html
<translation>container.apricityui.title</translation>
```

The text content is an MC translation key, rendered in the current language. textContent is the key itself. There is no parameter interpolation — assemble parameterized translations on the script side and use a normal text element.

## svg / path: Vector Graphics

An SVG subset, rasterized to a bitmap with 4x supersampling — suited for icons and simple decorations:

```html
<svg viewBox="0 0 64 64" width="64" height="64" style="color: #7dd3fc;">
    <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="4"></circle>
    <path d="M18 34 L28 44 L47 22 Z" fill="currentColor"></path>
</svg>
```

- Shapes: `circle`, `ellipse`, `rect`, `line`, `polyline`, `polygon`, `path`;
- Paint attributes: `fill`/`stroke` (including `none`), `stroke-width`, `stroke-linecap/join`, the `opacity` trio, `fill-rule` (supports evenodd), `currentColor` (reads the `color` of the element or an ancestor); attributes can be inherited from parents;
- path commands: M/L/H/V/C/S/Q/T/A/Z, both upper and lower case;
- `viewBox` is the internal coordinate system; element size is given via SVG attributes or CSS;
- **No** defs/use, gradients, filters, masks, text, transform, or external SVG. Flatten complex icons into pure paths on export, or switch to canvas.

SVG caches rasterization results and only redraws when attributes or the subtree change. Rasterizing large, complex paths is not cheap — watch it with frameTimingHud.

## canvas: Scripted Drawing

```html
<canvas id="chart" width="320" height="160"></canvas>
```

A standard Canvas 2D subset (Java2D backend). The `width`/`height` attributes set the bitmap size, CSS sets the display size, and a mismatch scales — don't assume changing only CSS changes the coordinate system. For full API support and limitations see the [Web API doc](web-api).

Frequent redraws of large canvases incur upload costs. Use svg for static vectors and texture for game textures — canvas is the most expensive of these; reserve it for visuals that truly need per-frame computation.

## FAQ

**texture doesn't show**: `src` was written as a logical path or file path — it must be `namespace:path`. To show images from the page directory use img.

**sprite doesn't animate**: steps doesn't match the atlas's actual frame count, direction doesn't match the atlas layout, or the element has no size. A static background is shown until the atlas finishes loading — that's normal.

**sprite's styles are overridden by my background / or the reverse**: managed properties only override same-named inline styles; those in stylesheets are unaffected. When troubleshooting, think of the managed-property list first.

**translation shows the raw key**: the key doesn't exist or the language file lacks the entry — consistent with vanilla behavior.

**svg's gradients/groups aren't drawn**: not supported. Flatten into paths or switch approaches.

**canvas content is gone after refresh**: refresh rebuilds the DOM, and the bitmap is cleared along with it. Put drawing logic in an initialization function and rerun it on `DOMContentLoaded`/`load`.
