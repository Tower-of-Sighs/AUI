# ApricityUI Overview

ApricityUI is a Minecraft mod for building game UIs with HTML, CSS, and JavaScript. It is not an embedded browser; AUI implements its own HTML parsing, CSS layout, and rendering. Page-script support depends on the loader target; see the compatibility matrix below.

This page is a map of all capabilities; each direction links to its dedicated topic document.

## Where a page can live

An HTML page (Document) has four kinds of hosts, covering every Minecraft UI scenario:

| Host | Scenario | Doc |
| --- | --- | --- |
| `ApricityScreen` | Full-screen GUI: settings pages, main-menu-style interfaces | [ApricityScreen](apricity-screen) |
| `ApricityContainerScreen` | Container interfaces: inventories, machines, storage, with real slots | [Container docs](container) |
| `WorldWindow` | In-world planes: info boards, machine exterior screens, entity overhead labels | [WorldWindow](world-window) |
| Overlay Document | Overlays: HUD, toasts, persistent panels | [Overlay docs](overlay-document) |

The same page structure and styling can be used in all four hosts. Script execution depends on the target, while display position and input handling depend on the host.

Page behavior is controlled by two metas — logical viewport (`aui-viewport`) and mouse interception (`aui-mouse-events`). The full explanation is consolidated in [the meta section of ApricityScreen](apricity-screen#page-meta-configuration).

## Loader and script support

The repository currently contains these targets. This table reflects the source tree and CI configuration; it does not imply that every target is currently published:

| Target | Minecraft | Page scripts | KubeJS `ApricityUI` bindings | CI JDK |
| --- | --- | --- | --- | --- |
| Forge | 1.20.1 | Supported | Provided | 21 |
| Fabric | 1.20.1 | Not currently executed | Not provided | 17 |
| Fabric | 1.21.1 | Not currently executed | Not provided | 21 |
| NeoForge | 1.21.1 | Supported | Provided | 21 |
| Fabric | 26.1 | Not currently executed | Not provided | 25 |
| NeoForge | 26.1 | Supported | Not provided | 25 |

The Java common API and HTML/CSS rendering are shared across these targets. Fabric targets currently do not execute page scripts; where page-script support is unavailable, pages still render but `<script>` does not run. For Java-side interaction or a target with page-script support, see [Mod-specific API](apricity-api).

## What you can use in a page

**HTML/CSS**: selector support is nearly complete; layout is a common subset (flex, grid, and limited table-row layout work; there is no float, sticky, or complete table algorithm); the painting layer is broad — shadows, filters, clip-path, transforms, and animations all work. Note that **there is no UA default stylesheet**: `h1` looks the same as `div`, and you write all styles yourself. Full list: [HTML/CSS coverage](html-css-coverage).

**JavaScript / Web API**: on targets with page-script support, DOM query and mutation, events, forms, fetch, localStorage, Canvas 2D, Observers, timers, and audio APIs are available. This is a subset of browser-style APIs, not a full browser. Which ones are available, lightweight, or absent: [Web API](web-api).

**Minecraft elements**: `<item>` and `<ingredient>` display items, `<recipe>` previews recipes, and `<container>/<slot>` provide containers and slots. See [Minecraft Item and Recipe Elements](mc-elements) and the [Container guide](container). Other extension tags are listed in [Extension Elements](extension-elements).

**Browser-style assistive behaviors**: Ctrl+wheel zoom, text selection and copy, clipboard, default form keys, scrolling: [Browser features](browser-features).

**Themes**: [Ore](ore-theme) and [McUI](mcui-theme) implement the same component classes and token contract; pages switch by changing the CSS and root scope class. The syntax-adapted Vue and mcui-oreui component bundles are optional resources, not theme dependencies.

## Containers: working with real items

Container pages can bind HTML slots to real data sources — player inventories, block entity capabilities, entity capabilities, and world-level SavedData persistent inventories. HTML handles structure and styling, while the server-side menu handles item logic and security checks; shift-click, dragging, and permissions all follow MC's native menu rules. There is only one proper way to open one: the server-side `ApricityUI.menu(player, path).bind(...)`. Details: [Container docs](container).

## Where resources come from

Pages and resources (CSS, images, fonts, data JSON) are referenced by **logical paths**, such as `screens/home.html`. Resources have three tiers of sources: built into the mod jar, resource packs, and the local `apricity/` directory; upper tiers override lower ones. Remote resources go through a restricted HTTPS pipeline. Bind the **Reload Resources** and **Open Resource Manager** actions in Minecraft's Controls settings; both are unbound by default. The Resource Manager can browse, preview, create files, edit metas, and check references. Rules: [Resource Management](resource-manager).

## How to open a page

**Java**: the unified entry point is `com.sighs.apricityui.ApricityUI` — `createDocument`, `new ApricityScreen(path)`, `menu(player, path).bind(...)`, `createWorldWindow(...)`.

**KubeJS**: the global `ApricityUI` client/server bindings are available only on Forge 1.20.1 and NeoForge 1.21.1, with method sets isolated by side (client manages Document/Toast/WorldWindow; server manages containers). Mods can also register their own KJS bindings in supported environments.

Full API tables and thread/null/refresh rules: [Mod-specific API](apricity-api).

## Debugging and tooling

**In-game DevTools**: DOM tree, element picking, Attributes/Styles/Box Model inspection, runtime style and structure edits, saving back to source files, meta editing, and a restricted console. Bind **Toggle DevTools** in Controls settings; it is unbound by default. See [DevTools](devtools).

**External debug protocol**: with `remoteDebug` enabled in game, a local WebSocket (`127.0.0.1:25321`) can query the DOM, read styles, and simulate clicks and input. The repo ships a Node client and an MCP bridge, so AI tools can connect directly to a running page. Two screenshot scripts are also included for visual regression. See [Additional Tools](tools).

**Frame timing HUD**: `debug.frameTimingHud` shows AUI render timing and batch statistics for locating performance problems. See [Secondary Development](secondary-development).

**WPT layout comparison**: takes Web Platform Tests CSS layout pages and captures geometry snapshots in both Chromium and AUI, then diffs them to verify the layout engine's browser consistency. See [WPT](wpt).

## Extension points for mod authors

- Register your own HTML tags: `@ElementRegister` + package scanning — custom-painted or purely semantic elements both work;
- Register your own KubeJS global objects: `@KJSBindings`;
- Reuse the built-in Java component library directly: DialogWindow, ContextMenu, ToastManager, Tooltip, ColorPicker, FilePicker: [Built-in UI Library](ui-library).

Thread rules, refresh generations, registration details: [Secondary Development](secondary-development).

## Project structure

The repository uses a `common + targets` multi-loader structure: `common/` is loader-agnostic shared code (compilable and testable standalone), and `targets/<loader>-<mc version>/` are standalone Gradle projects (Forge 1.18.2 / 1.19.2 / 1.20.1, Fabric 1.20.1 / 1.21.1 / 26.1, NeoForge 1.21.1 / 26.1), with loader bindings sunk behind SPI. For build commands, CI, and release workflow, see the root [README](../../../README).

## Documentation map

| Topic | Doc |
| --- | --- |
| Full-screen pages; authoritative reference for the three metas | [apricity-screen.md](apricity-screen) |
| Overlay / HUD | [overlay-document.md](overlay-document) |
| Containers and real slots | [container.md](container) |
| Minecraft item and recipe elements | [mc-elements.md](mc-elements) |
| In-world windows | [world-window.md](world-window) |
| Page JS / DOM API | [web-api.md](web-api) |
| HTML/CSS support | [html-css-coverage.md](html-css-coverage) |
| Extension tags | [extension-elements.md](extension-elements) |
| WebView / iframe | [webview.md](webview) |
| Zoom, selection, clipboard, and other assistive behaviors | [browser-features.md](browser-features) |
| Resource paths and the Resource Manager | [resource-manager.md](resource-manager) |
| KJS / Java mod API | [apricity-api.md](apricity-api) |
| Ore theme, visual editor, and optional mcui-oreui Vue component library | [ore-theme.md](ore-theme) |
| McUI theme and theme-switching contract | [mcui-theme.md](mcui-theme) |
| Java component library | [ui-library.md](ui-library) |
| In-game DevTools | [devtools.md](devtools) |
| Custom elements / KJS bindings / frame timing | [secondary-development.md](secondary-development) |
| External debug protocol, MCP, screenshot tools | [tools.md](tools) |
| WPT layout comparison | [wpt.md](wpt) |
| Page development and debugging | [Getting started](../getting-started), Sections 8 and 9 |
