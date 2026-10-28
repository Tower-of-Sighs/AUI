# Startup phase and the startup loading screen

The startup phase is the stretch around the game window appearing, while resources are still loading. AUI's own engine — `Document`, layout, fonts, CSS, the script runtime — is not up yet, so the `<iframe>` path cannot work. The system browser (WebView2 on Windows) is available at any time, though, so the startup phase gets its own channel: **a real browser paints the loading page**, and the framework only has to turn that into one texture.

This page covers the startup entry point, its context, and loading-page projects. The capture pipeline itself (shared memory, dirty rectangles, capture cost) is the same one `<iframe>` uses — see [WebView and iframe](webview).

## Quick start

```java
public final class MySplash {
    @StartupHook(priority = 100)
    public static void splash(StartupContext context) {
        // a folder: the entry point is always index.html
        context.loadProject("loading");
    }
}
```

The project lives in `<instance>/apricity/loading/index.html`, and `style.css`, `app.js`, `img/` next to it resolve with plain relative paths. No project, just one page? Hand over the HTML:

```java
context.showPage("""
        <!doctype html><html><body style="margin:0;height:100%;background:#101418;
          color:#eef;font:20px sans-serif;display:flex;align-items:center;
          justify-content:center">Loading…</body></html>
        """);
```

No code at all also works: put a project in `<instance>/apricity/startup/` (entry point `index.html`) and it is used; a single self-contained page can be `apricity/startup.html`. Without either, a built-in page is shown.

## The `@StartupHook` contract

```java
@StartupHook(priority = 100)
public static void splash(StartupContext context) { ... }
```

- the method must be `static` and take **exactly one parameter** (`StartupContext` or a supertype); anything else is logged and skipped;
- hooks run in `priority` order, **highest first**, with ties broken by class and method name so the order is identical on every launch; when several hooks set a page, **the last one wins**;
- they run on the client bootstrap thread, **before the game window exists**, so they must not block. The browser starts right after the last hook returns, which makes the page configured here the page it opens — no flash of the default page;
- an exception from one hook is logged and does not affect the launch;
- discovery goes through the loader's annotation scanner, so hooks can live in **any mod's package**;
- client only: a dedicated server never sees a `StartupContext`.

## `StartupContext`

Besides page control it exposes what the client knows that early. Every method is thread-safe and never throws: a missing backend, a window that does not exist yet or a headless service degrades to a neutral value instead of failing the hook.

| Kind | API | Notes |
| --- | --- | --- |
| Page | `showPage(String html)` / `showPageUrl(String url)` | Set or replace the startup page; a navigation once the view is live, so a hook can switch pages mid-load |
| | `loadProject(String project)` | Loads a folder project, see the next section |
| | `eval(String js)` | Push a script into the page (progress, text). Queued (up to 32) until the view is ready, then flushed |
| | `hide()` | Hand the loading screen back to vanilla; a later `showPage`/`loadProject` takes over again |
| | `webViewAvailable()` / `webViewUnavailableReason()` | Backend availability (false off Windows or without the WebView2 Runtime) |
| Geometry | `rasterWidth()` / `rasterHeight()` | The resolution the page is actually captured at — lay the page out against these (a provisional 1600×900 before the window exists) |
| Client | `windowWidth()` / `windowHeight()` | Physical pixels; 0 before the window exists |
| | `guiWidth()` / `guiHeight()` / `guiScale()` | Scaled GUI size and scale factor |
| | `gameDirectory()` | The instance directory (`apricity/` lives here), available even before the window |
| | `elapsedMillis()` / `phase()` | Time since the phase started / phase name (`startup`) |

Hooks run before the window exists, so lay pages out against `rasterWidth()`/`rasterHeight()`, not `guiWidth()` (which is 0 at that point). The startup page is **display only**: mouse and keyboard are not forwarded during the startup phase; interactive UI belongs to normal pages after loading.

## Loading-page projects

`loadProject(project)` takes a **folder**, and its entry point is always `index.html`:

| Passed | Resolves to |
| --- | --- |
| `loading` / `startup/loading` | relative to the page root, `<instance>/apricity/` |
| `apricity/loading` | relative to the instance directory |
| `D:/.../apricity/loading` | an absolute folder |
| `mymod:apricity/loading` | a mod resource folder (an explicit namespace is used as an explicit resource path) |
| `https:` / `file:` / `data:` | a full URL, used as the page |

A folder counts as a project when it **exists, is not empty, and holds `index.html`**. Anything else means "the instance does not have it", and the same folder is taken from the jars / resource packs, **extracted whole into `apricity/` at the corresponding place**, and loaded from there:

- `mymod:apricity/loading` → `apricity/loading/`, `mymod:pages/splash` → `apricity/pages/splash/`;
- `index.html` comes with its `css/js/images` subfolders (4 MB per file, at most 128 files, 32 MB total);
- **existing files are never overwritten**: after the first extraction the folder belongs to the instance, your edits survive, and deleting it brings the jar's copy back;
- an empty folder, or one without `index.html`, is filled in with whatever is missing.

Because hooks run during mod construction, before Minecraft builds its resource manager, a jar project cannot be read yet: resolution is **retried every 500 ms** until the startup screen ends. A project that already exists in the instance resolves immediately and pays none of that.

## KubeJS

`@StartupHook` is a Java annotation, so scripts cannot use it. The script side is a **KubeJS event**, subscribed from `startup_scripts`:

```js
// kubejs/startup_scripts/loading.js
ApricityEvents.startup(event => {
    event.context.loadProject('loading')   // exactly the same rules as the Java side
    // keep the context if you want to push progress later:
    // global.loadingContext = event.context
})
```

- the event fires **just before the loading screen's first frame**, by which time `startup_scripts` are loaded, so the page a script sets is a **navigation**: the browser has already started with the built-in page, which flashes for about 0.2 s. Use `@StartupHook` when the page has to be the one it opens;
- `event.context` is the `StartupContext`: `loadProject` / `showPage` / `eval` / `hide` plus `rasterWidth()`, `gameDirectory()` and the other getters;
- the group is a startup event, so only `startup_scripts` can subscribe (not `client_scripts`) — which matches the phase it describes;
- KubeJS 1.18.2 predates event groups, so it can only use `ApricityUI.startup()` to reach the context;
- availability follows the target: 1.19.2 / 1.20.1 / 1.21.1 have the event, Fabric targets have no KubeJS integration.

## Turning it off and falling back

- `-Dapricityui.startupOverlay=false` disables the whole startup loading screen and restores the vanilla one;
- no WebView2 Runtime, a failed view, or a backend that cannot upload pixels: the startup page simply is not shown, and the vanilla loading screen stays;
- a failing hook affects nothing but itself;
- the browser is stopped about four seconds after the overlay stops being drawn, and the texture is kept for the next load (a resource reload draws the screen again).

## Limitations

- the startup page is **plain HTML** rendered by WebView2: AUI's `<texture>`, `<item>` and theme CSS do not apply, and AUI's script runtime does not run there — use standard HTML/CSS/JS;
- pages load over `file:` and the host passes `--allow-file-access-from-files`, so `fetch` and ES modules work on a local page; cross-directory access is still subject to the browser's origin rules;
- 26.1 builds its GUI as a deferred render state, so the startup page there is "one full-screen blit of a texture" and the fade-in alpha does not apply;
- the startup page is display-only and receives no input.

## Troubleshooting

The log namespace is `apricityui/startup`; the lines that matter:

```
startup hooks: 1/1 invoked in 5 ms
startup project: loading -> file:///.../apricity/loading/index.html
startup project (late): ...            # a jar project, extracted once the resource manager is up
startup project: extracted 12 file(s) into .../apricity/loading
startup loading screen: view 1600x900 ready
startup loading screen: texture 1600x900
startup loading screen: view stopped after 4000 ms without a draw
```

- `startup hooks: none registered`: no annotated method was found — check that it is `static`, takes exactly one parameter, and that its class is inside a scanned package;
- only the built-in page ever shows: look for `startup project never resolved`, usually a mistyped path or an empty folder in the instance;
- nothing at all: look for `startup loading screen disabled: ...`, usually a missing WebView2 Runtime (off Windows this is always the case).
