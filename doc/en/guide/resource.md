### Static Resources

ApricityUI mainly works with these resource types right now:
HTML, CSS, JS, images, and fonts.

Audio and video are not in scope yet.

---

### Where Resources Are Loaded From

There are three resource layers, from highest priority to lowest:

1. Development path: `src/main/resources/assets/apricityui/apricity/...`
2. Game instance directory: `apricity/...`
3. Mod resource pack: `assets/apricityui/apricity/...`

If the same path exists in all three layers, the topmost one wins.

This lookup order is intentionally designed for development and customization:

- During development, edit source resources directly for the highest priority.
- Modpack authors can override files in the instance-level `apricity` folder without repackaging the mod.
- The mod's bundled resources act as the fallback default.

---

### How To Write Paths

Relative paths are recommended.

For example, if an image is in the same directory as the HTML file:

```html
<img src="loading.gif" />
```

If CSS references a font in a child folder:

```css
@font-face {
    font-family: "myfont";
    src: url("fonts/myfont.ttf");
}
```

If you use a path starting with `/`, it is resolved from the Apricity resource root, not from a browser website root.

Relative traversal like `..` is also supported.

---

### How HTML, CSS, And JS Are Loaded

HTML itself is loaded through entry points such as `ApricityUI.createDocument(path)` or `ApricityUI.openScreen(path)`.

CSS and JS both support external and inline forms:

```html
<style src="panel.css"></style>
<style>
    .panel {
        width: 120px;
    }
</style>

<script src="panel.js"></script>
<script>
    // inline script
</script>
```

A few important notes:

1. External CSS is loaded asynchronously and also supports `@import`.
2. External JS is currently resolved as local resources. Do not interpret it like a browser script tag.
3. The HTML entry page itself is still a local resource, not a remote web page.

---

### Images And Fonts

Common image formats are already supported, including PNG, JPG, and GIF.

GIF support is real animation support, not a placeholder.

For fonts, ApricityUI supports `@font-face` with resources such as TTF and OTF. It also ships a built-in `lxgw` font.

One detail: the built-in font is a compact subset. It contains about 3500 commonly used Chinese characters and is optimized for practical usage and small size.

---

### Remote Resources

ApricityUI supports some remote resources, but not as a general "load everything from the internet" mechanism.

The stable use cases right now are:

1. Remote images
2. Remote CSS
3. Remote fonts

There are also several restrictions:

1. Only `https://` is allowed
2. Loading is asynchronous
3. There are size and content-type limits

So it is suitable for:

- Hosted images
- Online theme styles
- Online fonts

It is not suitable as a remote HTML page system, and remote JS should not be treated like browser-style script distribution.

Remote HTML and remote JS may be considered in the future.

---

### Hot Reload

The instance-level `apricity` folder exists specifically for hot reload and customization.

The most direct way is pressing `END`, which reloads static resources immediately and refreshes the current `Document`.

In debug mode, these file types are watched automatically:

- `.html`
- `.css`
- `.js`

The watcher covers:

1. `src/main/resources/assets/apricityui/apricity` in development
2. The instance-level `apricity` folder

The resource-pack layer is mainly for default resources, not the main hot-reload workflow.

---

### Debugging And Resource Inspection

ApricityUI includes a Resource Manager that shows you the final merged resource list directly.

This is useful because it tells you:

1. Whether the current file comes from the development directory, the instance directory, or the resource pack
2. Which resource finally overrides another at the same path
3. Which exact image, stylesheet, or HTML file is actually being used

If you are debugging "I changed the file but nothing happened", check this first.

---

### A Recommended Workflow

If you are a modpack author or prefer a front-end-style workflow, this is a practical setup:

1. Put resources in `apricity/modid/...` under the instance directory
2. Use relative paths for HTML, CSS, images, and fonts
3. Use `END` for hot reload while styling
4. Package them into a resource pack or mod resources only when needed

This is usually the most efficient workflow and the least likely to be disrupted by path issues.
