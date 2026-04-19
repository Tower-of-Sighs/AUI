## ApricityUI

Build Minecraft UIs with HTML + CSS + JS. The syntax follows Web standards as closely as possible.

Cross-version support is in progress. The current plan is to support versions from 1.16 to 26.1.

Related links:
- CurseForge: https://curseforge.com/minecraft/mc-mods/apricityui
- Modrinth: https://modrinth.com/mod/apricityui
- GitHub: https://github.com/Tower-of-Sighs/AUI

![icon](https://cdn.modrinth.com/data/cached_images/9513051c399c427a47a6a4fd3600f0e157ba8a42.png)

### Overview

ApricityUI was built with a simple goal: a UI framework that is low-friction, convenient, and flexible. That is why it uses the classic trio of HTML + CSS + JS as its core.

The JavaScript layer currently depends on KubeJS, but it is optional. This means modpack authors can build rich UIs with a very low learning curve.  
How low? You can realistically ask an AI to generate most ApricityUI code for you. Web technologies are common enough that AI models usually understand them well, and the output is still readable.

If your mod needs custom UI, you can also use ApricityUI as a dependency to build highly extensible interfaces. The JS-side and Java-side APIs are broadly equivalent.  
That extensibility shows up in places like nested masks, smooth scrolling, rounded borders, frosted-glass backgrounds, custom animations, custom fonts, and GIF playback. Features that often take hundreds of lines elsewhere can usually be done here in just a few lines.

ApricityUI also keeps its syntax relatively stable and tries to stay close to Web standards, so upgrading across game versions is generally much less painful.

If you are not familiar with Web-based UI, at least remember these three advantages:
- Rich functionality, from simple shapes to layered rendering effects. ApricityUI can even render UI onto objects in the world.
- Straightforward usage. Most Web UI crash courses are short, and AI tools are already very familiar with this stack.
- Convenient debugging. Hot reload and visual debugging tools are standard ideas in Web development, and ApricityUI has them too.

Note: ApricityUI does not ship a Chrome runtime. It is implemented from scratch in Java and stays under 1 MB.

### Simple Examples

- Box model support, box shadows, adjustable border colors and sizes, and configurable rounded corners.
- A reasonably standard Flex layout model.
- Four custom fonts at the same time, including the vanilla font.
- Containers with built-in overflow masking.

![img_1.png](https://resource-api.xyeidc.com//client/members/pics/4ca88cb2)

- In-world rendering at a specific position and angle, with occlusion support.
- `border-image` support for flexible 9-slice textures.
- Nestable clipping masks, including custom shapes such as triangles.

![img_2.png](https://resource-api.xyeidc.com//client/members/pics/35cb1bb9)

- GIF image support.
- Custom animations and transitions.
- Built-in custom input boxes.

(This is an animated image. Refresh if it is not moving.)

![test1.gif](https://resource-api.xyeidc.com//client/members/pics/0cca76ee)

- Support for native Minecraft elements, demonstrated here with a custom container.

![img_3.png](https://resource-api.xyeidc.com//client/members/pics/3e2c842f)

- Custom themes, demonstrated here with the built-in AE theme.

![img4.png](https://resource-api.xyeidc.com//client/members/pics/8b59a171)

For more details, see [Simple Examples](./guide/example.md).

If you do not want to write styles by hand, let AI generate them or borrow ideas from [CodePen](https://codepen.io/).

If you run into a missing CSS property that you need, open an issue on GitHub.

### Resource Distribution

ApricityUI currently supports static assets such as HTML, CSS, JavaScript, TTF/OTF fonts, and most common image formats including GIF. Audio and video may be supported in the future.  
Assets can come from the instance-level `apricity` folder or from resource packs. Resource packs have lower priority, but the built-in global styles and built-in fonts are shipped with the mod resources.

For modpack development, the recommended place is the instance `apricity` folder. By default, pressing `END` triggers hot reload, and reload time is usually within one second.

See the documentation for details.

For distribution, either a resource pack or a normal archive works. Since resource packs still require a game-side reload, a simple archive is often more convenient.  
For mods that depend on ApricityUI directly, resource-pack style packaging is usually cleaner. Development resource paths are already included in the default search paths with the highest priority.  
Network assets are also supported, such as images hosted remotely or public static asset services, and they are loaded asynchronously. Use that responsibly.

ApricityUI also has a more retro-style server-to-client HTML rendering workflow, where the server sends HTML to the client for rendering. It is not a priority feature yet, so it will land in a later release.

### More Notes

- ApricityUI includes a browser-like devtools panel. Press `F12` to open it. It currently supports visual editing for `attribute`, `innerText`, and `style`, and also allows JavaScript debugging from the console.
- KubeJS is optional. Without it, JavaScript embedded in HTML cannot run and the devtools console is unavailable, but that is usually acceptable for Java-based mod UI.
- Hot reload is fast. Even a document with many avatars, several custom fonts, and inline JS can often reload in about one second. Note that this does not reload the resource pack itself.
- Performance is generally decent. On a complex styled example, FPS dropped from about 400 to 300 in testing.
- Lightweight ports of modern front-end frameworks such as Vue and Svelte are being explored, though progress is still slow.
- AI is very capable with HTML/CSS/JS, but ApricityUI is still not a full browser, so you should provide an ApricityUI capability list before relying on AI for complex output.
- Community contributions are welcome. There are many HTML tags and CSS properties to support, and feature requests are also useful.

### Want To Try It?

- ApricityUI Chinese modding group: `211573328`
- Developer and contributor group: `895176696`
- Discord: https://discord.gg/C8epbbwjrS

### AI Automation

Follow the guidance in [agent.md](./agent.md).

The folder `run/screenshots/aui` outputs game screenshots every second and keeps up to 20 images.

In debug mode, static assets are watched automatically and reloaded when changed.

Suggested workflow:
Modify HTML/CSS/JS files -> watch the log for `[DebugReload] change detected:` and `[DebugReload] reload completed` -> wait three seconds -> inspect the latest screenshots in the screenshot folder -> decide whether the result matches the requirement -> continue editing if needed.

In the example workflow for this conversation:
- Only modify HTML and CSS.
- The target HTML file is `src/main/resources/assets/apricityui/apricity/devtools/index.html`.
- The target result is a clean and attractive debug tool UI with a moderate size.
- The game is already running, so start the iteration flow and refine it several times.
