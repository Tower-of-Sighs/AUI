## 晴雪UI

可以使用HTML+CSS+JS构建UI，语法尽可能遵循Web标准。

相关链接：
- CurseForge: https://curseforge.com/minecraft/mc-mods/apricityui
- Modrinth: https://modrinth.com/mod/apricityui
- Github: https://github.com/Tower-of-Sighs/AUI

社区：
- 晴雪UI交流群：211573328
- Discord：https://discord.gg/C8epbbwjrS

![icon](https://cdn.modrinth.com/data/cached_images/9513051c399c427a47a6a4fd3600f0e157ba8a42.png)

### 基本内容

晴雪UI用 HTML、CSS 和 Java 构建 Minecraft UI；部分 loader target 还支持页面 JavaScript。Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.1 支持页面脚本；Fabric targets 当前不执行页面脚本。KubeJS 的 `ApricityUI` 模组绑定只在 Forge 1.20.1 与 NeoForge 1.21.1 提供。详见[总览](guide/overview#loader-与脚本支持)。

HTML/CSS 页面仍能在没有页面脚本的 target 中渲染；需要交互逻辑时，请确认目标 target 支持页面脚本，或从 Java 侧操作 DOM。
它的上手门槛很低：你可以用常见的 Web 技术编写页面，也可以借助 AI 生成 HTML/CSS，再按项目需要调整。

有自定义 UI 需求的模组，也可以将晴雪 UI 作为依赖：模组侧可用 Java API 集成；支持页面脚本的 target 上，还可用 JavaScript 操作 DOM。
拓展性表现在哪呢？遮罩嵌套、平滑滚动、圆角边框、毛玻璃背景、自定义动画、自定义字体、GIF动图，甚至是滤镜、遮罩和阴影互相嵌套，这些需要几百上千行才能实现的功能，对于晴雪UI，都只要几行就能搞定！

此外，晴雪UI会尽可能遵循 Web 标准，但它实现的是浏览器 API 和 CSS 的子集；具体支持范围请查阅[HTML/CSS 覆盖面](guide/html-css-coverage)和[Web API](guide/web-api)。不同 Minecraft target 也会有各自的加载器适配差异。

从 1.2.5 起，晴雪UI可通过 `<iframe>` 调用操作系统的 WebView，在游戏页面中显示真实网站并转发鼠标、键盘和滚轮输入。当前后端仅支持 Windows x64 且需要 WebView2 Runtime；详情见[WebView 文档](guide/webview)。

如果你不知道Web框架是什么东西，至少知道其广为人知的三大优势：
- 功能丰富，从简单的图形绘制到各种渲染效果嵌套，对于晴雪UI，甚至能渲染在世界里的某个方块上！
- 用法简单，我在b站上看到的速成课大部分都在三小时以内，最短的十分钟，别忘了AI对它也是如数家珍。
- 调试方便，Web框架中的一切几乎都支持瞬间热重载，并且还有便捷的开发者工具提供可视化调试，晴雪UI也都有。

注：普通 AUI 页面由 Java 实现，不会启动 Chromium 浏览器进程；`<iframe>` 使用系统提供的 WebView2，只有需要嵌入真实网页时才使用它。

### 使用案例展示

#### 内置纯CSS主题

目前已内置Ore主题和AE主题，未来会加入更多贴合（或不贴合）MC风格的通用主题，均使用同一套CSS标识命名方式，无缝切换。

- [永无止境载具]( https://www.mcmod.cn/class/24495.html)3D打印机
![永无止境载具3D打印机](https://resource-api.xyeidc.com//client/members/pics/70c150b2)

- [自动连接纹理](https://www.mcmod.cn/class/29435.html)编辑器
![自动连接纹理编辑器](https://resource-api.xyeidc.com//client/members/pics/1e5c695b)

#### 自由的绘制时机

页面可作为 Screen、HUD Overlay、容器界面或世界内窗口渲染。模组侧可以通过 Java API 集成；支持 KubeJS 的 target 也可从 KubeJS 调用相关功能。

- [东方足道屿](https://www.bilibili.com/video/BV1yzGJ6hEcp/)物品提示框
![东方足道馆物品提示框](https://resource-api.xyeidc.com//client/members/pics/aa944fd4)

- 简易轮盘菜单（作者：柳如烟001）
![简易轮盘菜单](https://resource-api.xyeidc.com//client/members/pics/44f43609)

#### Canvas画布

还算够用的Canvas能力支持，完全能胜任图表绘制、Svg图像绘制、复杂特效嵌套、涂鸦板等常用功能。

- [食韵筑家](https://github.com/Skcycos/buildshop-1.21.1)股市风云
![食韵筑家股市风云](https://resource-api.xyeidc.com//client/members/pics/3a3d61ca)
- [自动连接纹理](https://www.mcmod.cn/class/29435.html)绘制画板
![自动连接纹理绘制画板](https://resource-api.xyeidc.com//client/members/pics/a1304ac0)

#### 世界内窗口

html可以渲染在世界内的某个位置，可以配置角度、方块穿透、交互距离、可见距离、视角跟随、LOD等细节属性。

- [东方足道屿](https://www.bilibili.com/video/BV1yzGJ6hEcp/)女仆搓脚
![东方足道屿女仆搓脚](https://resource-api.xyeidc.com//client/members/pics/f082d316)

- 生物血条和物品显示（作者：୧⍤⃝无月）
![生物血条和物品显示](https://resource-api.xyeidc.com//client/members/pics/c24fba74)

#### MC原生元素

支持以HTML的方式管理容器槽位、物品、流体、材质、模型、雪碧图动画、翻译键等原生元素。

- [食韵筑家](https://github.com/Skcycos/buildshop-1.21.1)建材商店
![食韵筑家建材商店](https://resource-api.xyeidc.com//client/members/pics/41076420)

- 方可梦皮肤管理（作者：卡杨巴）
![方可梦皮肤管理](https://resource-api.xyeidc.com//client/members/pics/faf0264f)

#### 富文本编辑器

支持以数据驱动的方式实现富文本编辑器。

- 内置富文本编辑器示例
![内置富文本编辑器示例](https://resource-api.xyeidc.com//client/members/pics/8da43f3f)

### Webview相关功能

为了不同模组的UI之间既能互相独立又支持彼此交互，晴雪UI采用了多document架构，也因此iframe标签的用途被削减了大半，再加上MC中极少会有界面嵌套需求，目前晴雪UI的iframe标签完全用于接入Webview。

简单来说，使用iframe标签创建一个document时，实际上会打开一个Webview并以离屏渲染的方式绘制到iframe标签的内部区域中。

WebView 页面与 AUI DOM 相互隔离；AUI 当前会转发鼠标、键盘和滚轮输入，但没有提供跨页面 DOM 或脚本桥接 API。

而较高的内存占用，也使得Webview难以胜任Overlay和世界内窗口的绘制方式。

WebView 后端目前仅提供给 Windows x64 + WebView2 Runtime。其他平台上 `<iframe>` 会退化为空占位盒子。

调用Webview的好处在于，它在一定程度上弥补了晴雪UI对浏览器标准支持不够全面的缺陷，它可以用于绘制与游戏本身相关度低的复杂页面，或直接访问外部网站，如访问实时更新的文档、更新日志、模组教程视频等，也支持制作世界内放映厅。

![Webview观看网站视频](https://resource-api.xyeidc.com//client/members/pics/50590958)

### 开发者须知

使用晴雪UI无需了解任何源码，如有需要，可以使用官方Maven：
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

借助AI开发Web应用非常非常简单，甚至不需要SKILL，对于晴雪UI也是如此。

简单的例子，一句话生成界面：

![AI设计高压熔炉](https://resource-api.xyeidc.com//client/members/pics/dbf44a5c)
![AI设计聊天界面](https://resource-api.xyeidc.com//client/members/pics/78b66f86)

先反复随机生成出喜欢的设计稿，再对静态模板进行改造，是一个效率比较高的方案。

初步生成的静态模板可以直接在资源管理器中导入和预览，图片、音频、字体等静态资源也可以预览，右键菜单也提供了快捷引用的功能。

为了不造成按键冲突，1.2.4版本开始，资源管理器快捷键默认无绑定，调试前需要自行绑定快捷键。

![资源管理器](https://resource-api.xyeidc.com//client/members/pics/50f75cde)

从1.2.4版本开始，jar包中内置了完整的文档，并且配置文件中有每秒自动截图（最多保存20张）的选项和读取文件变更立即重载对应界面的选项，这些都是为AI设计的。

在AI编程智能体中，你可以用这种说法引导AI往一个方向持续迭代界面设计：

```
需求是创建或修改UI，只需要修改html/css/js。
遵从内置文档的引导。
已知：run/screenshots/aui文件夹中每一秒都会输出游戏截图；静态资源会自动监听变更，并触发重载。
测试流程：修改html/css/js文件 -> 监听日志中的重载消息 -> 等待三秒后检查截图文件夹中的游戏截图 -> 判断截图是否满足需求效果，若不满足就继续修改html/css/js文件，若满足，结束流程。
目标html文件：run/apricity/test/quest.html
需要满足的效果是：游戏中的任务列表，现代扁平风格，红白配色
效果参考图：run/screenshots/image_614748742442633.png
```

完整SKILL详见[官方文档](https://doc.sighs.cc/ApricityUI/skill)，已内置在jar包中。

MC的环境中复杂UI的需求较少，一般而言，只要让AI阅读内置文档即可一次性完成大部分设计工作，在使用了内置主题的情况下，样式的美观程度也大有保障。

在界面大体完成之后，可以唤出与浏览器中类似的调试工具，1.2.4版本后同样默认无快捷键绑定。

调试工具中可以直接定位并抓取页面上的元素，并直接修改样式和内容，支持撤销和重做，修改完成后可以直接保存样式或保存完整的DOM内容。

不支持的特性、语法错误、内部异常等问题的相关日志，也会同步输出在调试工具的控制台中。

![调试工具](https://resource-api.xyeidc.com//client/members/pics/1cf3492a)

去[Codepen](https://codepen.io/)抄现成的样式也可以，如果遇上了需要但没有的CSS属性，可以到Github上提issue。

### 资源分发

晴雪UI支持 HTML、CSS、JavaScript（取决于 target）、TTF/OTF 字体、包括 GIF 在内的图片，以及 OGG/WAV 音频。视频网页可通过 Windows 上的 WebView `<iframe>` 播放；AUI 原生页面没有 `<video>` 实现。
存放资源的地方有版本实例下的apricity文件夹和资源包，资源包的优先级较低，但默认全局样式和内置字体都存放在模组本体的资源包中。
对于整合包开发者，推荐使用 `apricity/` 文件夹作为页面资源目录。资源重载操作默认未绑定，可在 MC 控制设置里自行绑定；也可启用自动热重载。更多信息见[资源管理](guide/resource-manager)。

详情请查询官方文档的[资源管理](https://doc.sighs.cc/ApricityUI/guide/resource-manager)章节。

如果需要打包分享，资源包或普通压缩包都可以，考虑到资源包还得重载游戏，还是推荐简单压缩。  
不过，对于使用晴雪UI作为前置的模组来说，资源包形式比较好。默认加载路径有包含开发环境下的资源包路径，并且优先级最高，肥肠方便。  
还有一种方式是使用网络资源，比如图床或开源静态资源托管网站，晴雪UI是支持异步加载网络资源的，但不要做坏事哦。

此外，晴雪UI还提供了复古感十足的“服务端发送HTML给客户端渲染”方案，可以完全在服务端管理客户端用户界面。

### 还想了解更多？

- 其实晴雪UI还做了浏览器的缩放功能，对着页面按住CTRL+滚轮即可进行放大缩小，对于玩家，可以轻松调整适配任何尺寸的窗口。
- 页面脚本的能力随 loader target 不同而不同；依赖 Vue、Svelte 等完整浏览器运行时的前端框架不能直接假定兼容。请先查阅 Web API 和 CSS 支持清单。

### 画廊

- [win98](https://github.com/Ximelon0815/Arachne-Computer)
![win98](https://resource-api.xyeidc.com//client/members/pics/8b0f6624)

- [极械工坊](https://www.mcmod.cn/class/27007.html)载具设置
![极械工坊载具设置](https://resource-api.xyeidc.com//client/members/pics/76ae0ed1)

- [FindMe](https://www.mcmod.cn/class/28285.html)伙伴管理
![FindMe伙伴管理](https://resource-api.xyeidc.com//client/members/pics/3b0f4d67)
