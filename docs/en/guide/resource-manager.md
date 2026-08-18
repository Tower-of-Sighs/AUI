# Resource Paths and the Built-in Resource Manager

AUI pages do not read disk files directly. All HTML, CSS, JS, images, and fonts are first mapped into a **logical resource space**, and the loader then finds the actual content from resource packs, the local directory, or the development directory.

## Logical Paths

When writing code and pages, use only one kind of path — the logical path:

```java
Document.create("screens/example.html");
```

```html
<link rel="stylesheet" href="../styles/common.css">
<img src="/images/logo.png">
```

Do not write paths like these:

```text
assets/apricityui/apricity/screens/example.html      ← the resource root is not a prefix
src/main/resources/...                                ← a disk path even less so
D:/work/...                                           ← don't even think about it
```

The rules are simple:

- Always use `/` as the separator;
- A relative path like `../styles/page.css` is relative to **the directory containing the current file** (a url in CSS is relative to the CSS file itself, not the HTML that references it);
- A leading `/` means the logical resource root, e.g. `/images/logo.png`;
- `.` and `..` are normalized;
- Remote resources must use `https://`.

## Where Resources Live

There are three sources, in ascending priority; for the same path, the one loaded later overrides the one loaded earlier:

| Source | Location | Writable |
| --- | --- | --- |
| Resource pack | `assets/apricityui/apricity/...` (packaged into the mod jar) | No |
| Local directory | `<instance dir>/apricity/...` (usually `run/apricity/` in a dev environment) | Yes |
| Development directory | `src/main/resources/assets/apricityui/apricity/...` | Yes |

So: use resource pack paths for packaged releases; to override a page without repackaging, drop a same-named file into `run/apricity/`; for day-to-day development, use the development directory under src. A typical development directory structure:

```text
src/main/resources/assets/apricityui/apricity/
├── global.css            ← auto-included in every page if present
├── global.js             ← executed on every Document refresh if present
├── screens/example.html
├── styles/common.css
├── scripts/page.js
├── images/logo.png
└── fonts/display.ttf
```

## Using Each Resource Type

**HTML**: The page entry point; must end with `.html`. `Document.create`, ApricityScreen, containers, and WorldWindows all reference it by logical path.

**CSS**: Inline `<style>` or external `<link rel="stylesheet">`; external sheets can also use `@import` (with a nesting depth limit; circular references are ignored and logged).

**JS**: Inline or `<script src="../scripts/page.js">`. Note that remote `https://` scripts are **not** downloaded or executed — don't expect browser-like behavior.

**Images**: Both `<img>` and CSS `url(...)` work; png / jpg / jpeg / bmp / gif / webp are supported, including animated GIFs.

**Fonts**: Load ttf / otf via CSS `@font-face`:

```css
@font-face {
    font-family: "display-font";
    src: url("../fonts/display.ttf") format("truetype");
}
```

## Remote HTTPS Resources

Remote references only accept `https://`. The entry points that support them: external CSS and `@import`, `<img>` and CSS image URLs, `@font-face` fonts, and the page's `fetch()`. Remote scripts and remote HTML are not supported.

The network policy is fixed — nothing to configure and no way to configure it:

| Item | Limit |
| --- | --- |
| Protocol | HTTPS only |
| Timeout | 3 seconds each for connect and read |
| Redirects / retries | Up to 3 / 1 |
| Concurrency / per-resource size | 4 / 8 MiB |
| Cache | 60 seconds in memory; 7 days on disk (`apricity/.cache/network/`) |

Failure reasons are written to the `[AUI Network]` log. Note that pressing END to reload does not clear the disk cache — when verifying new server content, either wait for expiry or manually delete the `.bin` files. There are no browser concepts like CORS, cookies, or permission prompts.

## Scanning and Reloading

At client startup, all HTML is scanned into a template table; `Document.create(path)` only builds pages from that table — if the template is not in the table, it returns null and logs.

**Pressing END (or calling `ClientLoader.reload()`) will**: rescan resources, clear image/style/network caches, and refresh all normal Documents and built-in tools. When you change HTML/CSS/JS during development, press END — this is the standard loop.

Two advanced usages:

- `document.setReloadPersistent(true)`: makes a Document skip the full refresh — good for tool pages that maintain their own state;
- `HTML.reload("screens/home.html")`: re-reads a single template without refreshing any Document; you must call `refresh()` manually afterwards.

Refresh rebuilds the DOM and invalidates all old Element references — a rule covered in the [lifecycle section](web-api#lifecycle-and-refresh).

## Built-in Resource Manager

Open with **F10** (rebindable in the MC controls settings). It is itself an AUI page (`devtools/resource.html`).

The UI has four areas: a resource tree on the left, a path navigator at the top, a file grid in the center, and a details panel on the right. It only shows the resource that is **in effect** after override merging — it does not display a pile of same-named cards to illustrate override relationships.

**Context menu**:

- Folder: OPEN, NEW FILE HERE;
- File: PREVIEW (HTML/images/fonts), REFERENCE (generate reference code), EDIT META (local HTML only), COPY PATH (logical path), COPY SOURCE (source path), OPEN FOLDER, PROPERTIES;
- Empty space: NEW FILE, GO UP, REFRESH.

**Preview**: Double-clicking an HTML file opens an interactive preview window (buttons can be clicked, inputs can be typed in), but changes made in the preview are not written back to the source file — use DevTools' save for structural changes, and EDIT META for meta changes. Double-clicking an image zooms it; fonts show Chinese and English samples.

**New HTML**: NEW FILE supports three content sources — importing a local file, the clipboard, and a blank template (with common meta optionally pre-configured). The save path must be a relative path ending in `.html`; `../` cannot escape. Saving automatically triggers a reload, so the new page is usable immediately.

**EDIT META**: Edits the AUI meta tags in the HTML head (`aui-viewport`, `aui-mouse-events`, and charset); non-AUI meta and the body are preserved as-is. Files from resource packs have no writable source, so this item is disabled for them. For the meaning of each meta, see the [ApricityScreen doc](apricity-screen#page-meta-configuration).

**REFERENCE**: Generates reference code and copies it to the clipboard in one click — images get both CSS background and `<img>` snippets, fonts get `@font-face` registration + `font-family` usage, and HTML gets various opening methods such as Screen / Overlay / WorldWindow / KubeJS.

**World window mode**: After setting `debug.resourceManagerWorldWindow = true` in `config/apricityui-client.toml`, the resource manager appears in the world as a WorldWindow (requires being in a world with no other Screen open).

## Troubleshooting

Logs are prefixed by stage — search `logs/latest.log`:

| Prefix | Covers |
| --- | --- |
| `[AUI Resource]` | Scanning, file reading |
| `[AUI HTML]` | Missing templates, parse failures |
| `[AUI CSS]` | Missing stylesheets, @import cycles |
| `[AUI JS]` | Missing or unreadable scripts |
| `[AUI Image]` / `[AUI Font]` | Decoding, upload failures |
| `[AUI Network]` | HTTPS download problems |
| `[AUI Document]` | DOM construction, script execution |

A few frequent issues:

- **`Document.create` returns null**: Is the path a logical path? Is the file under the resource root? Did you press END? Is there a `template resource is missing` in the log?
- **CSS/image 404**: Relative paths resolve against the current file — use enough `../` to cross directories, or start with `/` to go from the root;
- **Changed a file but the UI didn't change**: Press END. Pages with `setReloadPersistent(true)` must be refreshed manually;
- **EDIT META is greyed out**: This entry comes from a resource pack or remote source and has no writable local file.
