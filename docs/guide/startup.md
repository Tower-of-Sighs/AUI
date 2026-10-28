# 启动阶段与启动加载屏

启动阶段指游戏窗口出现前后、资源还在加载的那段时间。这时候 AUI 自己的引擎——`Document`、布局、字体、CSS、脚本运行时——都还没起来，`<iframe>` 那条路走不通。但系统浏览器（Windows 上的 WebView2）随时可用，于是启动阶段留了一条独立通道：**用真正的浏览器画加载页**，框架只负责把画面变成一张纹理。

这一页讲的是启动阶段的入口、上下文和加载页工程。抓帧管线本身（共享内存、脏矩形、抓帧成本）和 `<iframe>` 是同一套，见 [WebView 与 iframe](webview)。

## 快速开始

```java
public final class MySplash {
    @StartupHook(priority = 100)
    public static void splash(StartupContext context) {
        // 传文件夹：入口固定 index.html
        context.loadProject("loading");
    }
}
```

工程放在 `<实例目录>/apricity/loading/index.html`，旁边的 `style.css`、`app.js`、`img/` 用相对路径引用即可。不想建工程、只有一页的话，直接给 HTML：

```java
context.showPage("""
        <!doctype html><html><body style="margin:0;height:100%;background:#101418;
          color:#eef;font:20px sans-serif;display:flex;align-items:center;
          justify-content:center">正在加载…</body></html>
        """);
```

不写任何代码也行：把工程放进 `<实例目录>/apricity/startup/`（入口 `index.html`），启动时自动用它；没有资产的单页写成 `apricity/startup.html` 同样认。没有工程时显示内置加载页。

## `@StartupHook` 的契约

```java
@StartupHook(priority = 100)
public static void splash(StartupContext context) { ... }
```

- 方法必须是 `static`，且**恰好一个参数**（`StartupContext` 或其父类型），否则记一条错误并跳过；
- 按 `priority` **从高到低**执行，同优先级按类名、方法名排序，保证每次启动顺序一致；多个 hook 都要设置页面时，**后执行的覆盖先执行的**；
- 在客户端 bootstrap 线程、**游戏窗口创建之前**执行，所以不要阻塞。最后一个 hook 返回后浏览器才开始启动，因此这里设置的页面就是它打开的那一页，不会先闪一下默认页；
- 某个 hook 抛异常只记日志，不影响启动；
- 扫描走 loader 的注解扫描器，hook 可以放在**任何 mod 的任何包**里；
- 只在客户端执行，专用服务器上永远看不到 `StartupContext`。

## `StartupContext`

页面控制之外，它还能读到启动阶段客户端已知的信息。所有方法线程安全且不抛异常：后端缺失、窗口还没建、服务还是 headless，都退化成中性值而不是让 hook 失败。

| 类别 | API | 说明 |
| --- | --- | --- |
| 页面 | `showPage(String html)` / `showPageUrl(String url)` | 设置或替换启动页；视图已就绪时是导航，可用来中途换页 |
| | `loadProject(String project)` | 加载文件夹工程，见下一节 |
| | `eval(String js)` | 往页面推脚本（进度条、文案）；视图未就绪时排队（上限 32 条），就绪后补发 |
| | `hide()` | 交还原版加载屏；之后再 `showPage`/`loadProject` 会重新接管 |
| | `webViewAvailable()` / `webViewUnavailableReason()` | 后端是否可用（非 Windows、缺 WebView2 Runtime 时为 false） |
| 几何 | `rasterWidth()` / `rasterHeight()` | 页面实际的抓帧分辨率，排版按它对（窗口未创建时是 1600×900 的临时值） |
| 客户端 | `windowWidth()` / `windowHeight()` | 物理像素；窗口还没创建时为 0 |
| | `guiWidth()` / `guiHeight()` / `guiScale()` | 缩放后的 GUI 尺寸与缩放倍率 |
| | `gameDirectory()` | 实例目录（`apricity/` 在这里），窗口创建前也可用 |
| | `elapsedMillis()` / `phase()` | 阶段起始到现在的毫秒数 / 阶段名（`startup`） |

因为 hook 在窗口创建之前跑，自适应排版请用 `rasterWidth()`/`rasterHeight()`，别用 `guiWidth()`（那时是 0）。页面是**只读展示**：启动阶段不转发鼠标和键盘，需要交互请等加载结束后的正常页面。

## 启动页工程

`loadProject(project)` 的参数是**文件夹**，入口固定 `index.html`。接受的形式：

| 传入 | 解析为 |
| --- | --- |
| `loading` / `startup/loading` | 相对页面根 `<实例目录>/apricity/` |
| `apricity/loading` | 相对实例目录 |
| `D:/.../apricity/loading` | 绝对路径 |
| `mymod:apricity/loading` | mod 资源目录（显式命名空间按显式资源路径用） |
| `https:` / `file:` / `data:` | 整条 URL 原样当页面用 |

判定规则：**文件夹存在 + 非空 + 里面有 `index.html`**，三者缺一就认为"实例里没有这个工程"，去 jar / 资源包里找同名目录，找到就**整个文件夹解压到 `apricity/` 下对应位置**再加载：

- `mymod:apricity/loading` → `apricity/loading/`，`mymod:pages/splash` → `apricity/pages/splash/`；
- `index.html` 连同 `css/js/图片` 子目录一起解开，单文件 ≤4MB、每次最多 128 个、总计 ≤32MB；
- **已存在的文件不会被覆盖**：第一次解压后文件夹就归实例所有，你的修改会保留；想拿回 jar 里的新版本，把文件删掉重启即可；
- 空文件夹、或非空但缺 `index.html` 的文件夹会被"缺什么补什么"地补全。

因为 hook 跑在 mod 构造期、那时游戏还没建资源管理器，jar 里的工程当场读不到，所以解析会**每 500ms 重试一次**直到启动屏结束。实例里已有的工程是同步命中的，没有这个延迟。

## KubeJS 侧

`@StartupHook` 是 Java 注解，脚本用不了。脚本侧是一个 **KubeJS 事件**，监听写在 `startup_scripts` 里：

```js
// kubejs/startup_scripts/loading.js
ApricityEvents.startup(event => {
    event.context.loadProject('loading')   // 规则和 Java 侧完全一样
    // 之后还要推进度，就把上下文存下来：
    // global.loadingContext = event.context
})
```

- 事件在**启动加载屏第一帧即将绘制时**触发（那时 `startup_scripts` 已经加载完），所以脚本设的页面是**导航**：浏览器已经用内置页起来了，会先闪一下内置页（约 0.2 秒）。要"打开的第一页就是它"，用 `@StartupHook`；
- `event.context` 就是 `StartupContext`：`loadProject` / `showPage` / `eval` / `hide` 和 `rasterWidth()`、`gameDirectory()` 这些都能用；
- 事件组是 startup 类型，只有 `startup_scripts` 能订阅（`client_scripts` 里没有）——这和它代表的时机一致；
- 1.18.2 的 KubeJS 还没有事件组 API，只能用 `ApricityUI.startup()` 直接取上下文；
- 可用性跟着 target 走：1.19.2 / 1.20.1 / 1.21.1 有事件，Fabric targets 没有 KubeJS 集成。

## 关闭与降级

- `-Dapricityui.startupOverlay=false` 关掉整个启动加载屏，回到原版；
- 没有 WebView2 Runtime、创建失败、后端不支持像素上传：都只是不显示启动页，原版加载屏照常；
- 一个 hook 出错不影响其它 hook，也不影响启动；
- 启动屏停止绘制约 4 秒后浏览器被关掉，纹理留下给下一次加载（资源重载也会再画一次）。

## 限制

- 启动页是 WebView2 渲染的**纯 HTML**：不认 AUI 的 `<texture>`、`<item>`、主题 CSS，也不跑 AUI 的脚本运行时。要什么效果直接用标准 HTML/CSS/JS；
- 页面以 `file:` 加载，宿主带 `--allow-file-access-from-files`，所以 `fetch`、ES module 在本地页上照常可用；跨目录访问仍受浏览器同源规则约束；
- 26.1 的 GUI 是延迟渲染状态，启动页在那里是"整屏 blit 一个纹理"，淡入使用的 alpha 在 26.1 上不生效；
- 启动页只做展示，不接收输入。

## 排查

日志命名空间是 `apricityui/startup`，关键几行：

```
startup hooks: 1/1 invoked in 5 ms
startup project: loading -> file:///.../apricity/loading/index.html
startup project (late): ...            # jar 里的工程，等资源管理器就绪后才解压出来
startup project: extracted 12 file(s) into .../apricity/loading
startup loading screen: view 1600x900 ready
startup loading screen: texture 1600x900
startup loading screen: view stopped after 4000 ms without a draw
```

- 只有 `startup hooks: none registered`：没有找到注解方法——检查方法是否 `static`、参数是否只有一个、以及那个类是否在模组扫描范围里；
- 一直只有内置页：看 `startup project never resolved` 的警告，通常是路径/资源名写错，或实例目录里那个文件夹是空的；
- 完全没画面：看 `startup loading screen disabled: ...`，通常是缺少 WebView2 Runtime（非 Windows 平台本来就是空的）。
