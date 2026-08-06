# ApricityUI Overview

ApricityUI is a Minecraft mod: write game UIs with the HTML, CSS, JavaScript trio. It is not an embedded browser — no Chromium. HTML parsing, CSS layout, and painting are all handled by a self-built engine, and page scripts run on Rhino. The payoff is lightness: no browser process is spawned, pages are drawn directly into Minecraft's render pipeline, and they can read and write in-game items, block entities, and world data.

This page is a map of all capabilities; each direction links to its dedicated topic document.

## Where a page can live

An HTML page (Document) has four kinds of hosts, covering every Minecraft UI scenario:

| Host | Scenario | Doc |
| --- | --- | --- |
| `ApricityScreen` | Full-screen GUI: settings pages, main-menu-style interfaces | [ApricityScreen](apricity-screen) |
| `ApricityContainerScreen` | Container interfaces: inventories, machines, storage, with real slots | [Container docs](container) |
| `WorldWindow` | In-world planes: info boards, machine exterior screens, entity overhead labels | [WorldWindow](world-window) |
| Overlay Document | Overlays: HUD, toasts, persistent panels | [Overlay docs](overlay-document) |

The same page runs in all four hosts: the same DOM, CSS, and scripting capabilities — only the display position and input path differ.

Page behavior is controlled by three metas — logical viewport (`aui-viewport`), font mode (`aui-font-mode`), and mouse interception (`aui-mouse-events`). The full explanation is consolidated in [the meta section of ApricityScreen](apricity-screen#page-meta-configuration).

## What you can use in a page

**HTML/CSS**: selector support is nearly complete; layout is a common subset (flex and grid work; no float, sticky, or table layout); the painting layer is broad — shadows, filters, clip-path, transforms, and animations all work. Note that **there is no UA default stylesheet**: `h1` looks the same as `div`, and you write all styles yourself. Full list: [HTML/CSS coverage](html-css-coverage).

**JavaScript / Web API**: DOM query and mutation, events (both capture and bubble phases), forms and constraint validation, fetch, localStorage, Canvas 2D, Observers, and timers all work. It is a subset of browser-style APIs, not a full browser — no WebGL, XHR, history, or full Promise. Which ones are available, which are lightweight shims, and which are absent: [Web API](web-api).

**Extension elements**: a set of MC-oriented tags beyond the standard ones — `<texture>` (game textures), `<sprite>` (atlas frame animation), `<translation>` (localization), `<svg>` (vector icons), `<container>/<slot>/<recipe>` (item slots). Usage: [Extension elements](extension-elements).

**Browser-style assistive behaviors**: Ctrl+wheel zoom, text selection and copy, clipboard, default form keys, scrolling: [Browser features](browser-features).

**Ore theme**: a built-in MC-style pure-CSS theme (pixel borders, dark surfaces, green/purple/gold accent colors). Include one line of CSS to get a full set of button, card, form, table, and badge styles, plus a companion **visual editor** that lets you drag pages, tune tokens, and export HTML in-game: [Ore theme](ore-theme).

## Containers: working with real items

Container pages can bind HTML slots to real data sources — player inventories, block entity capabilities, entity capabilities, and world-level SavedData persistent inventories. HTML handles structure and styling, while the server-side menu handles item logic and security checks; shift-click, dragging, and permissions all follow MC's native menu rules. There is only one proper way to open one: the server-side `ApricityUI.menu(player, path).bind(...)`. Details: [Container docs](container).

## Where resources come from

Pages and resources (CSS, images, fonts, data JSON) are referenced by **logical paths**, such as `screens/home.html`. Resources have three tiers of sources: built into the mod jar, resource packs, and the local `apricity/` directory; upper tiers override lower ones. Remote resources go through a restricted HTTPS whitelist pipeline. Press `END` for a full reload. Press `F10` in game to open the **Resource Manager**: browse, preview, create files, edit metas, and check references. Rules: [Resource Management](resource-manager).

## How to open a page

**Java**: the unified entry point is `com.sighs.apricityui.ApricityUI` — `createDocument`, `new ApricityScreen(path)`, `menu(player, path).bind(...)`, `createWorldWindow(...)`.

**KubeJS**: a global `ApricityUI` is injected into client/server scripts, with method sets isolated by side (client manages Document/Toast/WorldWindow; server manages containers). Mods can also register their own KJS bindings.

Full API tables and thread/null/refresh rules: [Mod-specific API](apricity-api).

## Debugging and tooling

**In-game DevTools** (`F12`): DOM tree, element picking, Attributes/Styles/Box Model inspection, runtime style and structure edits, saving back to source files, meta editing, and a restricted console. See [DevTools](devtools).

**External debug protocol**: with `remoteDebug` enabled in game, a local WebSocket (`127.0.0.1:25321`) can query the DOM, read styles, and simulate clicks and input. The repo ships a Node client and an MCP bridge, so AI tools can connect directly to a running page. Two screenshot scripts are also included for visual regression. See [Additional Tools](tools).

**Frame timing HUD**: `debug.frameTimingHud` shows AUI render timing and batch statistics for locating performance problems. See [Secondary Development](secondary-development).

**WPT layout comparison**: takes Web Platform Tests CSS layout pages and captures geometry snapshots in both Chromium and AUI, then diffs them to verify the layout engine's browser consistency. See [WPT](wpt).

## Extension points for mod authors

- Register your own HTML tags: `@ElementRegister` + package scanning — custom-painted or purely semantic elements both work;
- Register your own KubeJS global objects: `@KJSBindings`;
- Reuse the built-in Java component library directly: DialogWindow, ContextMenu, ToastManager, Tooltip, ColorPicker, FilePicker: [Built-in UI Library](ui-library).

Thread rules, refresh generations, registration details: [Secondary Development](secondary-development).

## Project structure

The repository uses a `common + targets` multi-loader structure: `common/` is loader-agnostic shared code (compilable and testable standalone), and `targets/<loader>-<mc version>/` are standalone Gradle projects (currently Forge 1.20.1), with loader bindings sunk behind SPI. For build commands, CI, and release workflow, see the root [README](../../../README).

## Documentation map

| Topic | Doc |
| --- | --- |
| Full-screen pages; authoritative reference for the three metas | [apricity-screen.md](apricity-screen) |
| Overlay / HUD | [overlay-document.md](overlay-document) |
| Containers and real slots | [container.md](container) |
| In-world windows | [world-window.md](world-window) |
| Page JS / DOM API | [web-api.md](web-api) |
| HTML/CSS support | [html-css-coverage.md](html-css-coverage) |
| Extension tags | [extension-elements.md](extension-elements) |
| Zoom, selection, clipboard, and other assistive behaviors | [browser-features.md](browser-features) |
| Resource paths and the Resource Manager | [resource-manager.md](resource-manager) |
| KJS / Java mod API | [apricity-api.md](apricity-api) |
| Ore theme and visual editor | [ore-theme.md](ore-theme) |
| Java component library | [ui-library.md](ui-library) |
| In-game DevTools | [devtools.md](devtools) |
| Custom elements / KJS bindings / frame timing | [secondary-development.md](secondary-development) |
| External debug protocol, MCP, screenshot tools | [tools.md](tools) |
| WPT layout comparison | [wpt.md](wpt) |
| AI development and debugging rules (skill doc for AI) | [ai-skill.md](../ai-skill) |
