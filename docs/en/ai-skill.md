# AUI Page Development and Debugging (For AI)

You are writing pages for, or debugging running pages of, the Minecraft mod ApricityUI (AUI). **This file covers every rule needed to produce working pages.** Finer details live in the GitHub repository (see the end of Step 7) — read them if you can fetch them; if you can't, this file is authoritative.

Setting the tone first: AUI pages are plain HTML/CSS/JS. Most commonly used browser features work, so just write them the normal web way. Only two caveats: don't use overly obscure features, and don't make structure and styling excessively complex. What genuinely needs attention is the mod's own stuff — paths, page configuration, the four UI forms, containers, debugging — none of which comes from web experience. It's all below.

## What This Mod Is

AUI lets you build Minecraft UIs with HTML/CSS/JS. It is not an embedded browser: HTML parsing, CSS layout, and rendering are a self-built engine, and page scripts are executed by Rhino (an old-school JS environment — `var` + plain `function` is the safest style). One HTML file is parsed into one Document, which is placed into one of four hosts for display.

Remember three in-game keys: F10 resource manager (double-click an HTML file for an interactive preview; right-click → REFERENCE to generate the open code), F12 DevTools (page debugger), END full resource reload.

## Step 1: What Environment Are You In?

### Environment A: Java mod development (AUI as a dependency)

Signs: you're in a mod project (build.gradle, Java sources present), with AUI as a dependency.

- Unified entry point: static methods of `com.sighs.apricityui.ApricityUI`: `createDocument(path)`, `screen(path)`, `menu(player, path).bind(...)`, `createWorldWindow(path, pos, distance)`, `getDocument(path)`, `getDocumentByUUID(uuid)`;
- **Thread rule**: all DOM/host operations must happen on the client thread. In async callbacks (network packets, thread pools), wrap with `Minecraft.getInstance().execute(...)` before touching a page, or you'll get sporadic crashes;
- **Null convention**: `createDocument` returns null when the resource is missing — null-check it;
- **Refresh invalidation**: page refresh/reload rebuilds the entire DOM; old Element references and listeners all go stale — re-query them.

### Environment B: Modpack / server, writing UI with KubeJS

Signs: you're in an instance directory or a modpack repo, writing scripts under `kubejs/`.

- The global object `ApricityUI` is already injected into KJS. The method sets of client scripts and server scripts **do not overlap**: the client side manages UIs (createDocument/screen/createWorldWindow), the server side manages containers (menu);
- **Creating ≠ showing**: `createDocument(path)` creates an Overlay and shows it immediately; calling `Document.createInWorld(path)` alone shows nothing; full-screen UIs need `screen(path)`; containers must be opened server-side with `menu(...).bind(...)`.

## Step 2: Path Rules

- All resources use **logical paths**: `screens/home.html`. No `assets/...` prefix, no disk paths;
- Page files actually live under `<game directory>/apricity/` (e.g. `<game directory>/apricity/screens/home.html`); after writing, press END so the mod picks them up;
- Inside a page, reference CSS/images/fonts with relative paths (relative to the current HTML); a leading `/` means the logical resource root;
- The only exception: a `<texture>`'s src is an MC ResourceLocation (`minecraft:textures/item/diamond.png`), not a logical path.

## Step 3: Page Metas (Required on Every Page)

Placed in `<head>`, read only at page creation and refresh:

```html
<meta name="aui-viewport" content="mode=browser">
<meta name="aui-mouse-events" content="intercept">
```

- **aui-viewport**: `mode=browser` follows the window (first choice for Screen); `mode=fixed,width=N,height=N` fixed logical size (**WorldWindow must use this**, otherwise the default width is over a thousand pixels and the panel becomes enormous in the world); `mode=gui` follows MC GUI scale (for compatibility with old pages). Zoom parameters `zoom/min-zoom/max-zoom/zoom-step/user-scalable` are optional;
- **aui-mouse-events**: the page only intercepts the mouse if you write `intercept`. **Any page with interactive elements must set it**, otherwise clicks fall through to the game instead of the page; purely presentational Overlays should not set it (let clicks pass through).

## Step 4: Choose a Host

The same HTML works across all four hosts; the difference is where it appears and who opens it:

| What you want | Host | How to open | Key points |
| --- | --- | --- | --- |
| Full-screen UI: settings pages, menus | Screen | KJS `ApricityUI.screen("screens/x.html")`; Java `Minecraft.getInstance().setScreen(new ApricityScreen(path))` | Configure metas as above |
| HUD, persistent status, notifications | Overlay | KJS/Java `ApricityUI.createDocument("overlays/x.html")` | Shows on creation; don't set intercept for purely presentational ones |
| Inventories, machines — operating on **real items** | Container Screen | **Only** server-side `ApricityUI.menu(player, path).bind(...)` | See Step 6 |
| Display screens in the world, labels above heads | WorldWindow | `ApricityUI.createWorldWindow("world/x.html", pos, 32)` (in Java the second parameter is a Vec3) | Page must use a `mode=fixed` viewport; shows on creation |

WorldWindow additions: the parameter order of `setRotation(Vec3)` is `(pitch, yaw, roll)` (easy to get backwards); `setFacing(true)` faces the player; `setFollow(true)` + `setFollowFactor(0.3)` follows the view (the above-head label usage); the distance constructor parameter is the interaction ray distance, while `setMaxDisplayDistance` is the display distance — don't mix them up.

**Driving an already-open page from game code**: `ApricityUI.getDocument(path)` returns a **list** (the same path can have multiple instances open):

```javascript
var docs = ApricityUI.getDocument("screens/hello.html");
if (docs.length > 0) {
    docs[0].getElementById("status").textContent = "HP: 20";
}
```

To manage a specific instance precisely: keep the object returned by `createDocument`, or use `getDocumentByUUID(uuid)`.

## Step 5: Writing the Page

Write it the normal web way, keeping in mind these mod-specific points:

**No browser default styles**: an `h1` looks the same as a `div`, and a `button` has no button appearance. Either write all styles explicitly, or include the Ore theme (recommended, see below).

**Hang initialization on DOMContentLoaded**: refresh rebuilds the page, and all old references go stale. Wrap initialization code in a function and attach it, so it re-runs on every rebuild:

```javascript
function init() {
    var btn = document.getElementById("ok");
    if (!btn) return;
    btn.addEventListener("click", function () { /* ... */ });
}
document.addEventListener("DOMContentLoaded", init);
```

**A few differences in the JS environment**: the keyboard modifier is `controlKey`, not `ctrlKey`; event coordinates are already page logical coordinates — **do not multiply** them by any scale factor; `fetch(url)` only supports single-argument GET, and `response.json()` is called synchronously inside `then`; there is no WebGL/XHR/WebSocket/full Promise. DOM query and mutation, event capture and bubbling, localStorage, Canvas 2D, timers, ResizeObserver/MutationObserver are all available.

**Extended elements** (MC-oriented tags beyond the standard ones; all are ordinary DOM elements, and all must be given CSS width and height):

| Need | Syntax | Key points |
| --- | --- | --- |
| Game textures | `<texture src="minecraft:textures/item/diamond.png">` | src is a ResourceLocation; `blur="true"` blurs |
| Atlas frame animation | `<sprite src="images/coin.png" steps="8" direction="right" duration="640ms" loop="infinite">` | src is a logical path; direction is the atlas layout direction |
| Localized text | `<translation>item.minecraft.diamond</translation>` | Follows the game language; no parameter interpolation |
| Vector icons | `<svg viewBox="0 0 24 24"><path d="..." fill="currentColor"></path></svg>` | Supports basic shapes and path; no gradients/defs/transform |
| Scripted drawing | `<canvas>` | 2D context, API close to the browser's |

**Ore theme**: a built-in MC-style CSS theme (pixel borders, dark surfaces, green/purple/gold accents). One include line gives you a full set of component styles — **don't write styles from scratch**:

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
```

**Before using Ore, read the complete resources. Do not infer its appearance from the class-name summary below.** Obtain them in this order:

1. If the current workspace is an AUI checkout, read these local source files in full:
   - Documentation: `docs/guide/ore-theme.md`
   - Complete theme source: `common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css`
   - Complete component example: `common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/example.html`
2. If those local files are unavailable, retrieve and read the complete online files (not truncated excerpts):
   - Documentation: [ore-theme.md](https://raw.githubusercontent.com/Tower-of-Sighs/AUI/refs/heads/snow/docs/guide/ore-theme.md)
   - `ore.css`: [complete theme source](https://raw.githubusercontent.com/Tower-of-Sighs/AUI/refs/heads/snow/common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css)
   - `example.html`: [complete component example](https://raw.githubusercontent.com/Tower-of-Sighs/AUI/refs/heads/snow/common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/example.html)
3. Only when neither the local nor online resources can be obtained should this section's quick reference be used as a fallback. In that case, do not invent undocumented classes, tokens, or component behavior.

While reading, verify the `.ore-theme` root rules, `--ore-*` tokens, complete component DOM structures, state and variant classes, default dimensions and backgrounds, responsive rules, and browser-support limitations. Business CSS should use theme tokens and add only layout or domain-specific differences; do not redraw existing components such as `.card` and `.progress`. For overlays, specifically check whether the theme root paints a full-page background.

Class-name quick reference: `.button button-primary/-secondary/-tertiary/-danger`, `.card` + `.card-header/-body/-footer`, `.form-group/.form-label/.form-input`, `.table` (fixed four columns — for a different column count override `grid-template-columns` on `tr`), `.badge`, `.alert`, `.progress` > `.progress-bar`, `.container`, `.stack`/`.cluster`, `.text-center/.text-muted`, `.mt-1..4`, etc. Ore is styles only, no behavior — write your own JS for tab switching, modal toggling, and the like. The runtime demo of every component is available in game: press F10 and double-click `apricityui/theme/ore/example.html`.

## Step 6: Container Pages (Real Items)

HTML declares the structure, the server binds the data source — both halves are indispensable:

```html
<container id="saved_data" bind="saved_data" primary="true" size="9"></container>
<container id="player" bind="player" layout="preset:player"></container>
```

**Fixed container ids** (the easiest pitfall to fall into):

| Server-side bind method | Required HTML id |
| --- | --- |
| `player()` | `player` |
| `saveddata(name, cap)` | `saved_data` |
| `blockEntity(pos)` | `block_entity` |
| `entity(id)` | `entity` |

If the ids don't match → all slots degrade into display-only slots. The first parameter of `saveddata("machine_data", 9)` is the server-side data name, not the HTML id.

Rules:

- An empty container with `bind` and `size` auto-generates its slots; `bind="player"` automatically gets 36 slots, and `layout="preset:player"` arranges them in the vanilla inventory layout;
- When hand-writing `<slot slot-index="N">`, N is the local index within the container — either hand-write all of them or auto-generate all of them, don't mix;
- Slots opened UI-only (`screen(path)`) are all display-type — **a real container must be opened server-side with `menu(player, path).bind(b -> b.blockEntity(pos).player())`**;
- The shift-click direction is determined by the server-side bind order (the first non-player bind is primary); the HTML `primary="true"` cannot change that;
- `<recipe type="crafting_shaped">recipeID</recipe>` generates a recipe preview — purely presentational, occupies no slot.

## Step 7: Debugging (Focus: Features the Mod Provides for AI Debugging)

### Built-in capabilities (available as soon as the mod is installed — prefer this set)

AUI ships a built-in closed loop for the "AI on the outside, game running" scenario: **edit files → auto reload → auto screenshots → read logs**. Both switches are under `[debug]` in `config/apricityui-client.toml`:

```toml
[debug]
autoReload = true         # watches file changes, hot-reloads automatically
aiAutoScreenshot = true   # auto screenshot every second
```

**File hot reload (autoReload)**: when enabled, the mod continuously watches `.html/.css/.js` files under the resource directory; saving takes effect immediately, no one needs to go into the game and press END. Reloads are page-precise: a CSS change only re-attaches styles to the pages that reference it (upstream files in the `@import` chain count too), with **DOM and JS state fully preserved** — tweaking styles won't lose the page's live state; an HTML/JS change only refreshes the corresponding pages; a newly created HTML file only registers a template and touches no pages; changing a file not referenced by any open page does nothing at all. This is the AI's development loop: directly edit the page files under `<game directory>/apricity/`, the changes take effect automatically, then verify via screenshots.

**Auto screenshots (aiAutoScreenshot)**: when enabled, **a screenshot of the game is taken automatically every second**, written to `<game directory>/screenshots/aui/` (only the latest 20 are kept). Just read the newest PNG in that directory to see the page's actual rendered result — whether the layout is right, whether styles took effect, what an error looks like — without asking the user to describe it.

**Logs**: script errors and CSS parsing problems are all in `logs/latest.log`; search for the `[AUI JS]`/`[AUI CSS]`/`[AUI HTML]` prefixes — errors come with resource paths.

These three need no external tools and are the debugging baseline.

### Direct MCP connection to running pages (external tool — use it if you can get it, skip it if you can't)

The mod has a built-in debug service (`[debug] remoteDebug = true`, local `ws://127.0.0.1:25321/apricity`, connection credentials written to `run/apricity/debug.json` — **do not commit or share it**), but **the client tools for connecting to it are not distributed with the mod**. They live in the `tools/` directory of the GitHub repository [Tower-of-Sighs/AUI](https://github.com/Tower-of-Sighs/AUI):

- You're working in a clone of this repo (local `tools/` exists) → use them directly;
- Not in one → try fetching from GitHub (raw files look like `https://raw.githubusercontent.com/Tower-of-Sighs/AUI/snow/tools/apricity-mcp/server.mjs`; requires Node 20+ and `npm install`);
- **If you can't fetch them, give up on this route** — the built-in trio already covers "see the rendered result, see errors, edit files to verify"; when you need interaction verification like clicking buttons, ask the user to operate F12 DevTools on your behalf.

When available, two ways to connect:

1. **MCP bridge** `tools/apricity-mcp/server.mjs`, configured into an MCP client (env pointing at debug.json). Tools: `apricity_documents` (list Documents to get targetIds), `apricity_snapshot` (DOM tree), `apricity_query`/`apricity_inspect` (query elements), `apricity_wait_for` (wait for elements), `apricity_hover/click/fill` (simulate input);
2. **Node client** `tools/apricity-debug-client.mjs`: `connect()` reads debug.json by default; `documents()` → `attach(targetId)` → `page.locator("#save").click()`.

**Debugging discipline** (applies only after connecting to the debug service):

- targetIds go stale after a page reloads/rebuilds — every autoReload trigger changes the targetIds, so **re-call `documents()` before operating**; never hardcode them;
- The protocol **has no evaluate** — you cannot execute arbitrary JS. Ways to verify logic: modify the page to add console output (takes effect immediately with hot reload), or observe DOM results via inspect/query;
- click requires the element to be visible, have size, and have an unobstructed center point; fill only works on editable input/textarea;
- If the page isn't open, you can't reach it — have the user open the page (or provide the open code) first, then debug.

### Typical AI Debugging Workflow

1. Confirm `autoReload` and `aiAutoScreenshot` are on;
2. Edit page files → auto reload takes effect;
3. Read the latest screenshot in `screenshots/aui/` to see the rendered result;
4. For errors, look in `logs/latest.log`;
5. If you have MCP, use it to query the DOM and simulate operations for interaction verification; if not, ask the user to open F12 DevTools and look for you — its DOM tree, pick mode (click a page element to locate it in the tree), the Inspector's matched CSS rules list (which rule wins, what overrides it, which file it comes from), and the console (script output and errors) cover most troubleshooting. You tell the user what to look at and have them relay the results back to you.

### Detailed Docs Are Also on GitHub

Finer material (full mod API, CSS support checklist, extended-element attribute tables, complete parameters for each host, etc.) is in the `docs/` directory of the same GitHub repository. **If you can access GitHub, pull the relevant topic docs and read them; if you can't, this file is authoritative** — this file covers every rule needed to produce working pages.

## Pre-Delivery Self-Check

1. Are the three metas configured? Does any interactive page have `intercept`? Do WorldWindow pages use `mode=fixed`?
2. Are all paths logical paths? Are texture srcs ResourceLocations?
3. Is initialization hung on `DOMContentLoaded`? Did you avoid writing `ctrlKey`? Did you avoid multiplying event coordinates by a scale?
4. Is styling done with Ore rather than from scratch?
5. Container pages: do the fixed ids match the server-side binds? Are real containers opened server-side?
6. Has it actually been run and the rendered result inspected (autoReload + auto screenshots, plus interaction verification via MCP if available — don't just deliver code)?
