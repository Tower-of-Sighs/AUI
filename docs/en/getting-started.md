# Getting Started

From zero to your first actually usable in-game UI.

One thing up front: this mod's pages are plain HTML/CSS/JS, and it ships with a built-in instruction manual for AI plus debugging support — **if you were already planning to have AI write your pages, install the mod in Section 1 and then jump straight to Section 8**; you don't need to read the parts in between. Sections 2–7 are for people who want to understand it themselves: confirm the mod works, write an HTML page, make it look good, open it in game, and drive it from code.

## 1. Installation

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/apricityui
- Modrinth: https://modrinth.com/mod/apricityui

Official Maven:
```groovy
repositories {
    maven {
        url "https://maven.sighs.cc/repository/maven-public/"
    }
}
dependencies {
    implementation 'com.sighs:ApricityUI-forge-1.20.1:1.2.0'
}
```

## 2. Confirm It's Working

Enter the game and press **F10** to open the built-in resource manager.

This resource manager is itself an ApricityUI page — if it renders and responds to clicks, the mod is up and running. While you're there, note three features you'll use over and over:

- **Double-click an HTML file**: interactive preview, identical to opening it for real;
- **Right-click empty space → NEW FILE**: create a new page; the template comes with the usual settings preconfigured;
- **Right-click a selected file → REFERENCE**: generates the "how to open this page" code, ready to copy.

Three always-on keys: **F10** resource manager, **F12** DevTools (page debugger), **END** reload all resources.

## 3. Your First Page

Two ways to create one: use NEW FILE with a template in F10; or manually create `hello.html` under `<game directory>/apricity/screens/` (in a dev environment that's `run/apricity/`), then go back into the game and press END so the mod picks it up.

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body { margin: 0; color: #eee; background: #20242b; font-size: 16px; }
        .panel { width: 360px; margin: 60px auto; padding: 16px; background: #303640; }
    </style>
</head>
<body>
    <main class="panel">
        <h2>Hello, ApricityUI</h2>
        <p id="status">Waiting for click</p>
        <button id="btn" type="button">Click me</button>
    </main>
    <script>
        document.getElementById("btn").addEventListener("click", function () {
            document.getElementById("status").textContent = "Clicked";
        });
    </script>
</body>
</html>
```

It's just an ordinary web page. Only three things need explaining:

**The two metas are page configuration**: `aui-viewport` gives the page a browser-style logical viewport; `aui-mouse-events=intercept` makes the page intercept the mouse — without this line, clicks may not reach the page. Full explanation in the [meta section of ApricityScreen](guide/apricity-screen#page-meta-configuration); for now it's enough to know each one controls one thing.

**There are no browser default styles**: `h2`, `p`, and `button` come with no appearance at all — font size, color, and spacing are all yours to write. For which CSS works and which gets ignored, see [HTML/CSS Coverage](guide/html-css-coverage).

**Scripts run on Rhino**: the API is a subset of the browser's; the recommended style is `var` + plain `function`. See [Web API](guide/web-api) for the capability list.

**Paths**: the mod locates files by **logical path**, not disk location. If the file is at `<game directory>/apricity/screens/hello.html`, you write `screens/hello.html` in code — no `assets/...` prefix, no drive letters. Referencing CSS and images from inside a page works the same way. See [Resource Management](guide/resource-manager) for the rules.

When you're done writing, press END (or refresh in F10), then double-click `screens/hello.html` in the resource manager to preview: a centered panel; click the button and the text changes to "Clicked".

## 4. Make It Look Good with an Ore Theme

The page runs, but its styling is bare. AUI includes one mcui-oreui-based Ore UI:

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
```

Use the mcui-oreui DOM contract, for example
`<button class="btn middle_btn primary_btn">` and
`<section class="mc-panel">`. Open `apricityui/theme/ore/example.html` in F10
for the complete component and interaction showcase. See
[ore-theme.md](guide/ore-theme) for paths, tokens, the Vue runtime boundary,
and licensing.

## 5. Actually Opening the Page

Previewing is just for looks. To make the page pop up on its own on a key press, on world join, or on block right-click: right-click the page file in F10 → **REFERENCE**, and the open code is generated straight into your clipboard — paste it into your logic-side code to trigger it.

The same HTML has four kinds of hosts; REFERENCE lists all the ways to open it. Choose by scenario:

| Host | Scenario | Docs |
| --- | --- | --- |
| Screen | Full-screen UI: settings pages, menus | [apricity-screen.md](guide/apricity-screen) |
| Overlay | Floating layer: HUD, persistent status, notifications | [overlay-document.md](guide/overlay-document) |
| Container Screen | Inventories, machines — operating on real items | [container.md](guide/container) |
| WorldWindow | Display screens in the world, labels above entity heads | [world-window.md](guide/world-window) |

The HTML is written the same way for all four hosts; the only differences are where the page appears and who supplies the data. Multiple pages can be alive at once: a full-screen UI open, overlays on the HUD still running, and windows floating in the world — none of them interfere with each other.

## 6. Driving the Page from Outside

The page's internal `<script>` manipulating the DOM was covered above. External code (KubeJS scripts or Java) first gets hold of the page, then uses the same API:

```javascript
// KubeJS client script
var docs = ApricityUI.getDocument("screens/hello.html");   // note: returns a list
if (docs.length > 0) {
    var status = docs[0].getElementById("status");
    status.textContent = "HP: 20";
    status.setAttribute("class", "warning");
}
```

```java
// Java
List<Document> docs = ApricityUI.getDocument("screens/hello.html");   // also a list
if (!docs.isEmpty()) {
    Element status = docs.get(0).getElementById("status");
    status.setTextContent("HP: 20");
    status.setAttribute("class", "warning");
}
```

`getDocument(path)` returns a list because **the same path can have multiple instances open** — to manage a specific one precisely, keep the object returned by `createDocument` when you create it, or use `getDocumentByUUID`. DOM operations are identical on both sides: `getElementById` / `querySelector` to find elements, `textContent` (`setTextContent` in Java) to change text, `setAttribute` to change attributes, `addEventListener` to bind events.

Two pitfalls:

- **The page must exist first**. If it has never been opened, `getDocument` returns an empty list — call `createDocument` first or wait for the page to load;
- **All old references go stale after a refresh**. Reloading rebuilds the entire page, so confirm the generation before touching the DOM in async callbacks — see ["Threads, Nulls, and Refresh" in the mod API](guide/apricity-api#threads-null-values-and-refresh).

Full API: [apricity-api.md](guide/apricity-api).

## 7. Day-to-Day Changes: Use DevTools

The workflow for editing pages is **press F12 and edit live in game while watching**, not edit file → END → look → edit again. DevTools is basically the MC version of browser developer tools:

- **DOM tree**: expand level by level on the left; hovering highlights that element's margin/border/padding/content regions on the page; right-click to add child elements, hide, delete, or copy outerHTML and selector;
- **Pick mode**: after activating it the cursor becomes a crosshair; moving over the page highlights the hit element in real time, and one click locates it in the tree;
- **Three inspector panels**: Attributes edits attributes; Styles edits inline styles (individual declarations can be temporarily disabled, color values come with a color picker), and below that is the **list of matched CSS rules** — which rule wins, what overrides it, and which file it comes from, all at a glance; when styling looks wrong, look here first; Box Model shows the box model numbers;
- **Mistakes are undoable**: Ctrl+Z / Ctrl+Shift+Z; edit history is kept per document;
- **Happy with it? Hit save to write back to the source file**: if you only changed styles, only the modified CSS rules are written back (even across multiple CSS files); if you changed structure, check "save DOM tree" to serialize the entire current DOM back to HTML. Read-only files from resource packs are refused with an explanation — nothing gets written anywhere weird;
- **Console**: collects `console.log` output and errors from page scripts, filterable by level and searchable by keyword; the input box accepts restricted commands (`$("#save")` to query elements, `tree`, `count()`, and so on), not an arbitrary JS interpreter;
- **Meta editing**: directly edit the current page's charset, the three aui-* metas, and runtime zoom, without hand-editing the HTML head;
- **Settings**: toggle debug configs like `autoReload` right in the panel, no digging through toml files.

To re-run the current page, use the "reload document" button in the toolbar; **END is the fallback full reload for when you've changed source files and need it to take effect globally**.

Either kind of reload rebuilds the page, and every old element reference in your scripts goes stale — put initialization logic in `DOMContentLoaded` so it rebinds on every rebuild. See the [lifecycle section of Web API](guide/web-api#lifecycle-and-refresh). For the full feature list see [devtools.md](guide/devtools).

## 8. Let AI Write It for You

The mod ships with a complete set of AI-assisted development support. Set it up once, and most of the work of writing pages can be handed to AI afterwards.

**Step 1: give the skill to the AI.** [docs/ai-skill.md](ai-skill) is a self-contained instruction manual written for AI — path rules, metas, the four hosts, containers, and the debugging workflow are all in there. Three options: paste it into the conversation, put it in a directory the AI can read, or give the GitHub link directly (`https://github.com/Tower-of-Sighs/AUI/blob/snow/docs/ai-skill.md`). Once given, you don't need to relay the rules yourself anymore.

**Step 2: turn on two switches** (`config/apricityui-client.toml`):

```toml
[debug]
autoReload = true
aiAutoScreenshot = true
```

- `autoReload`: when the AI finishes editing a file and saves, it takes effect in game immediately — CSS changes only re-attach styles without even losing page state; HTML/JS changes only refresh the affected pages;
- `aiAutoScreenshot`: automatically takes a screenshot every second into `<game directory>/screenshots/aui/`, so the AI can read the image itself to confirm rendering results instead of you describing "what it looks like".

**Step 3: just ask normally.** The loop from then on: you say what to change → the AI edits files under `<game directory>/apricity/` → it takes effect automatically → the AI checks the screenshots and digs through `logs/latest.log` to self-verify. If conditions allow, the AI can also connect via MCP directly to the running page (tools are under `tools/` in the GitHub repo, not distributed with the mod — use them if you can get them) to query the DOM and click buttons for interaction verification; not being able to connect doesn't affect the main workflow.

## 9. Building UIs with AI + an Ore Theme

Require the AI to read [ore-theme.md](guide/ore-theme), then `readme.md`,
`source.md`, `ore.css`, `ore-components.css`, and `example.html` under
`apricityui/theme/ore/`. Do not infer component structure from a short class list.

What to tell the AI, roughly:

> Following ai-skill.md, build an AUI settings page with the only built-in Ore UI. Read its documentation, CSS, and example in full, and implement behavior with Rhino-compatible JavaScript or Java.

Override `.ore-theme` `--ore-*` / `--mc-*` tokens in application CSS loaded after the theme. Do not edit the bundled jar theme.

## 10. How to Troubleshoot

In this order:

1. **F12 DevTools**: for styling problems, look at the Inspector's "matched rules" list — which rule wins, what overrides it, which file it comes from; for structure problems, use pick mode to click an element and locate it straight in the DOM tree; script errors and console output are all in the console tab. Feature details in Section 7 and [devtools.md](guide/devtools);
2. **Check the logs**: search `logs/latest.log` for the `[AUI HTML]`, `[AUI JS]`, `[AUI CSS]` prefixes — errors come with resource paths;
3. **Out-of-game debugging**: the mod can run a local debug service; `tools/` ships a Node client and an MCP bridge, so AI tools can connect directly to running pages to query the DOM and simulate clicks. How to enable and use it: see [tools.md](guide/tools).

## What's Next

- Making pages look proper: [Ore Theme](guide/ore-theme) → [HTML/CSS Coverage](guide/html-css-coverage) → [Web API](guide/web-api);
- Advanced hosts: [Screen](guide/apricity-screen), [Overlay](guide/overlay-document), [WorldWindow](guide/world-window), [Containers](guide/container) (most advanced, involves the server side);
- Full mod-side API: [apricity-api.md](guide/apricity-api);
- Handing development to AI: [ai-skill.md](ai-skill), usage in Sections 8 and 9;
- Map of all docs: [overview.md](guide/overview).
