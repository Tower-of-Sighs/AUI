## ApricityUI

Build UIs with HTML + CSS + JS. The syntax follows Web standards as closely as possible.

Related links:
- CurseForge: https://curseforge.com/minecraft/mc-mods/apricityui
- Modrinth: https://modrinth.com/mod/apricityui
- Github: https://github.com/Tower-of-Sighs/AUI

Community:
- ApricityUI group (QQ): 211573328
- Discord: https://discord.gg/C8epbbwjrS

![icon](https://cdn.modrinth.com/data/cached_images/9513051c399c427a47a6a4fd3600f0e157ba8a42.png)

### Overview

ApricityUI was built around one goal: a UI framework that is low-friction, convenient, and broadly capable. That is why it takes the classic HTML + CSS + JS trio as its core.

The JS side currently depends on KubeJS (optional), which means modpack authors can draw whatever UI they like with an extremely low learning curve.  
How low? You can have AI generate everything ApricityUI-related for you. Web frameworks are popular enough that AI knows them well — the result may even look better than what you would draw yourself, and you can still read it.

Mods that need custom UI can also depend on ApricityUI to build highly extensible interfaces; the JS and Java APIs are broadly equivalent.  
Where does that extensibility show? Nested masks, smooth scrolling, rounded borders, frosted-glass backgrounds, custom animations, custom fonts, GIF playback — even filters, masks, and shadows nested inside one another. Features that take hundreds or thousands of lines elsewhere are a few lines here.

ApricityUI's syntax is essentially fixed and stays as close to Web standards as possible, so you can upgrade freely and use it across game versions without worrying about compatibility breaks.

From 1.2.5 onward, ApricityUI can also call the WebView built into your operating system, letting you embed a real website or web page inside the game that accepts only hardware input (video playback included).

If you are not sure what a Web framework is, at least remember its three well-known advantages:
- Rich functionality, from simple shape drawing to nested rendering effects — ApricityUI can even render onto a block in the world.
- Easy to use: most crash courses I have seen are under three hours (the shortest is ten minutes), and AI knows the stack inside out.
- Easy to debug: almost everything hot-reloads instantly, and convenient developer tools provide visual debugging. ApricityUI has both.

Note: ApricityUI does not bundle a Chrome runtime. It is built entirely from scratch in Java; as of 1.2.4 the jar is only 2.5 MB, it downloads nothing extra, and it is safe to use.

### Showcase

#### Built-in pure-CSS themes

The Ore and AE themes are built in, and more MC-flavored (or deliberately un-MC) general-purpose themes will follow. They all share the same CSS naming scheme, so switching between them is seamless.

- [永无止境载具](https://www.mcmod.cn/class/24495.html) 3D printer
![永无止境载具 3D printer](https://resource-api.xyeidc.com//client/members/pics/70c150b2)

- [自动连接纹理](https://www.mcmod.cn/class/29435.html) editor
![自动连接纹理 editor](https://resource-api.xyeidc.com//client/members/pics/1e5c695b)

#### Draw whenever you like

It integrates fully with Minecraft's rendering — draw wherever you want. KubeJS alone is enough to customize item tooltips and radial menus.

- [东方足道屿](https://www.bilibili.com/video/BV1yzGJ6hEcp/) item tooltip
![东方足道屿 item tooltip](https://resource-api.xyeidc.com//client/members/pics/aa944fd4)

- Simple radial menu (by 柳如烟001)
![Simple radial menu](https://resource-api.xyeidc.com//client/members/pics/44f43609)

#### Canvas

Canvas support that is good enough for charts, SVG drawing, complex nested effects, doodle boards, and similar everyday work.

- [食韵筑家](https://github.com/Skcycos/buildshop-1.21.1) stock market
![食韵筑家 stock market](https://resource-api.xyeidc.com//client/members/pics/3a3d61ca)
- [自动连接纹理](https://www.mcmod.cn/class/29435.html) drawing board
![自动连接纹理 drawing board](https://resource-api.xyeidc.com//client/members/pics/a1304ac0)

#### World windows

HTML can render at a position inside the world, with configurable angle, block pass-through, interaction distance, view distance, camera follow, LOD, and other details.

- [东方足道屿](https://www.bilibili.com/video/BV1yzGJ6hEcp/) maid foot massage
![东方足道屿 maid foot massage](https://resource-api.xyeidc.com//client/members/pics/f082d316)

- Mob health bars and item display (by ୧⍤⃝无月)
![Mob health bars and item display](https://resource-api.xyeidc.com//client/members/pics/c24fba74)

#### Native Minecraft elements

Manage container slots, items, fluids, textures, models, sprite animations, translation keys, and other native elements the HTML way.

- [食韵筑家](https://github.com/Skcycos/buildshop-1.21.1) building materials shop
![食韵筑家 building materials shop](https://resource-api.xyeidc.com//client/members/pics/41076420)

- 方可梦皮肤管理 (by 卡杨巴)
![方可梦皮肤管理](https://resource-api.xyeidc.com//client/members/pics/faf0264f)

#### Rich text editor

Rich text editors can be built in a data-driven way.

- Built-in rich text editor example
![Built-in rich text editor example](https://resource-api.xyeidc.com//client/members/pics/8da43f3f)

### WebView

So that different mods' UIs can stay independent while still interacting with each other, ApricityUI uses a multi-document architecture. That removes most of the point of the `iframe` tag, and Minecraft rarely needs nested interfaces anyway — so today ApricityUI's `iframe` tag is used entirely to embed WebView.

Put simply, creating a document with an `iframe` tag actually opens a WebView and draws it off-screen into the iframe's area.

WebView's drawbacks are obvious: from either Java or KubeJS it is hard to find an elegant way to interact with it logically, so ApricityUI currently only forwards hardware input to it.

Its high memory use also makes WebView a poor fit for overlays and world windows.

A small number of environments do not ship a WebView at all (Linux, for example), but this mod will **never** embed one.

The upside is that WebView partly compensates for ApricityUI's incomplete coverage of browser standards. It can draw complex pages that have little to do with the game, or open external sites directly — live documentation, changelogs, mod tutorial videos — and it supports building an in-world cinema.

![Watching a website video through WebView](https://resource-api.xyeidc.com//client/members/pics/50590958)

### For developers

Using ApricityUI requires no knowledge of the source code. If you need it, the official Maven is available:
```Groovy
repositories {
    maven {
        url "https://maven.sighs.cc/repository/maven-public/"
    }
}
dependencies {
    implementation 'com.sighs:ApricityUI-forge-1.20.1:1.2.6'
}
```

Developing Web apps with AI is very easy — you do not even need a SKILL, and the same goes for ApricityUI.

A simple example: one sentence generates an interface.

![AI-designed high-pressure furnace](https://resource-api.xyeidc.com//client/members/pics/dbf44a5c)
![AI-designed chat interface](https://resource-api.xyeidc.com//client/members/pics/78b66f86)

Generating random designs until you like one, then adapting the static template, is a high-efficiency workflow.

A freshly generated static template can be imported and previewed directly in the resource manager; images, audio, fonts, and other static assets can be previewed too, and the context menu offers quick reference.

To avoid key conflicts, starting with 1.2.4 the resource manager shortcuts are unbound by default — bind them yourself before debugging.

![Resource manager](https://resource-api.xyeidc.com//client/members/pics/50f75cde)

Since 1.2.4 the jar bundles the complete documentation, and the config file has options for automatic screenshots (up to 20 kept) and for reloading the matching interface as soon as a file changes. All of these are designed for AI.

Inside an AI coding agent you can phrase it this way to keep iterating on a design:

```
The task is to create or modify a UI; only HTML/CSS/JS may be changed.
Follow the guidance in the bundled documentation.
Known: the folder run/screenshots/aui outputs a game screenshot every second; static assets are watched automatically and trigger a reload on change.
Test loop: edit the html/css/js files -> watch the log for the reload message -> wait three seconds, then check the newest screenshot in the screenshots folder -> judge whether it matches the requirement; if not, keep editing, if yes, stop.
Target HTML file: run/apricity/test/quest.html
Required result: an in-game quest list, modern flat style, red and white palette
Reference image: run/screenshots/image_614748742442633.png
```

The full SKILL is documented in the [official docs](https://doc.sighs.cc/ApricityUI/skill) and is bundled in the jar.

Complex UI needs are rare in Minecraft. In general, letting AI read the bundled documentation is enough to finish most design work in one pass, and with a built-in theme the result already looks good.

Once the interface is roughly done, you can bring up debugging tools similar to a browser's — also unbound by default since 1.2.4.

In DevTools you can locate and pick elements on the page and edit styles and content directly, with undo and redo, then save the styles or the full DOM content.

Logs about unsupported features, syntax errors, internal exceptions, and similar problems are mirrored into the DevTools console.

![DevTools](https://resource-api.xyeidc.com//client/members/pics/1cf3492a)

You can also borrow ready-made styles from [Codepen](https://codepen.io/). If you hit a CSS property you need that is missing, open an issue on Github.

### Asset distribution

ApricityUI supports HTML, CSS, Javascript, TTF/OTF fonts, and most image formats including GIF as static assets; audio and video are planned.  
Assets live in the `apricity` folder of the version instance and in resource packs. Resource packs have lower priority, but the default global styles and built-in fonts ship in the mod's own resources.  
For modpack developers, the `apricity` folder is the recommended location. Hot reload is bound to `END` by default and usually takes under a second.

See the [Resource Manager](https://doc.sighs.cc/ApricityUI/guide/resource-manager) chapter of the official docs for details.

If you want to package and share, either a resource pack or a plain archive works. Since a resource pack also requires reloading the game, a simple archive is recommended.  
For mods that use ApricityUI as a dependency, the resource-pack form is better: the default load paths include the development resource-pack path with the highest priority, which is very convenient.  
Another option is network assets, such as image hosts or open-source static hosting sites. ApricityUI supports loading network assets asynchronously — just do not do anything bad with it.

ApricityUI also offers a retro "server sends HTML to the client to render" workflow, which lets you manage the client UI entirely from the server.

### Want to know more?

- ApricityUI also implements browser-style zoom: hold CTRL and scroll on a page to zoom in and out, so players can adapt to any window size.
- KubeJS is an optional dependency. Without it you cannot run the JavaScript written inside HTML, and the console is unavailable — but for mod developers that rarely matters, since DOM work can be done in Java.
- Hot reload is fast: twenty different QQ avatars plus three custom fonts, including inline JS, reload a whole document in about one second. If you write KubeJS client scripts you might even use it to cut corners — though the resource paths offer no PJS completion. Note that this reload does not reload resource packs.
- ApricityUI has no obvious performance bottleneck in normal use, and on 26.1 and above there is almost nothing to worry about. But the author cannot test every case — if you happen to hit a sudden stutter, please report it immediately.
- Lightweight ports of modern front-end frameworks such as Vue and Svelte are in active development. A simple Vue already has a PR from kltyton, and Svelte will be built in later. Join the group if you are interested!

### Gallery

- [win98](https://github.com/Ximelon0815/Arachne-Computer)
![win98](https://resource-api.xyeidc.com//client/members/pics/8b0f6624)

- [极械工坊](https://www.mcmod.cn/class/27007.html) vehicle settings
![极械工坊 vehicle settings](https://resource-api.xyeidc.com//client/members/pics/76ae0ed1)

- [FindMe](https://www.mcmod.cn/class/28285.html) companion management
![FindMe companion management](https://resource-api.xyeidc.com//client/members/pics/3b0f4d67)
