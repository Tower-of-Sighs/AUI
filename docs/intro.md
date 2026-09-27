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

晴雪UI开发的初心是低门槛且便捷全能的UI框架，因此选择了经典的HTML+CSS+JS三剑客作为内核。

其中JS的部分暂时依赖于KubeJS(非必须)，这意味着整合包作者也可以使用本模组尽情绘制UI，并且上手难度极低。  
有多低呢？你完全可以让AI为你生成所有关于晴雪UI的内容，Web框架非常流行，因此AI也非常熟悉，甚至画出来比你画的要好看，而且你还能看得懂！

有自定义UI需求的模组，也可以将晴雪UI作为依赖来绘制可拓展性超强的UI，JS与Java的写法大体上是等效的。  
拓展性表现在哪呢？遮罩嵌套、平滑滚动、圆角边框、毛玻璃背景、自定义动画、自定义字体、GIF动图，甚至是滤镜、遮罩和阴影互相嵌套，这些需要几百上千行才能实现的功能，对于晴雪UI，都只要几行就能搞定！

此外，晴雪UI的语法标准基本上是固定的，会尽可能遵循Web标准，因此你可以放心地更新、放心地跨版本使用，而不用担心新版本的兼容性问题。

在1.2.5及以上版本，晴雪UI还具备了调用电脑操作系统内置的Webview的能力，允许你在游戏中嵌入一个只能接受硬件输入的真实网站或网页（可以播放视频）。

如果你不知道Web框架是什么东西，至少知道其广为人知的三大优势：
- 功能丰富，从简单的图形绘制到各种渲染效果嵌套，对于晴雪UI，甚至能渲染在世界里的某个方块上！
- 用法简单，我在b站上看到的速成课大部分都在三小时以内，最短的十分钟，别忘了AI对它也是如数家珍。
- 调试方便，Web框架中的一切几乎都支持瞬间热重载，并且还有便捷的开发者工具提供可视化调试，晴雪UI也都有。

注：晴雪UI不批发Chrome内核，完全由Java从头开始构建，截止1.2.4版本，jar大小仅2.5MB，且不会下载任何额外内容，请放心食用。

### 使用案例展示

#### 内置纯CSS主题

目前已内置Ore主题和AE主题，未来会加入更多贴合（或不贴合）MC风格的通用主题，均使用同一套CSS标识命名方式，无缝切换。

- [永无止境载具]( https://www.mcmod.cn/class/24495.html)3D打印机
![永无止境载具3D打印机](https://resource-api.xyeidc.com//client/members/pics/70c150b2)

- [自动连接纹理](https://www.mcmod.cn/class/29435.html)编辑器
![自动连接纹理编辑器](https://resource-api.xyeidc.com//client/members/pics/1e5c695b)

#### 自由的绘制时机

完全融入MC的渲染方式，想在哪渲染就在哪渲染，仅通过KubeJS即可自定义物品提示框和轮盘菜单。

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

### Webview相关功能

为了不同模组的UI之间既能互相独立又支持彼此交互，晴雪UI采用了多document架构，也因此iframe标签的用途被削减了大半，再加上MC中极少会有界面嵌套需求，目前晴雪UI的iframe标签完全用于接入Webview。

简单来说，使用iframe标签创建一个document时，实际上会打开一个Webview并以离屏渲染的方式绘制到iframe标签的内部区域中。

Webview的缺陷是显而易见的，不管是Java还是KubeJS，都很难找到优雅的方式来与其进行逻辑交互，因而晴雪UI中目前也仅支持转发硬件输入到Webview中。

而较高的内存占用，也使得Webview难以胜任Overlay和世界内窗口的绘制方式。

此外，极少部分运行环境并不自带Webview，例如Linux，但本模组**绝对不会**考虑内嵌Webview

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
    implementation 'com.sighs:ApricityUI-forge-1.20.1:1.2.5'
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

晴雪UI支持的静态资源有HTML、CSS、Javascript、TTF/OTF字体以及包括GIF在内的大部分图片格式，未来还会支持音频和视频。  
存放资源的地方有版本实例下的apricity文件夹和资源包，资源包的优先级较低，但默认全局样式和内置字体都存放在模组本体的资源包中。  
对于整合包开发者，推荐使用apricity文件夹作为静态资源存放位置，默认按END键热重载，一般重载时间一秒以内。

详情请查询官方文档的[资源管理](https://doc.sighs.cc/ApricityUI/guide/resource-manager)章节。

如果需要打包分享，资源包或普通压缩包都可以，考虑到资源包还得重载游戏，还是推荐简单压缩。  
不过，对于使用晴雪UI作为前置的模组来说，资源包形式比较好。默认加载路径有包含开发环境下的资源包路径，并且优先级最高，肥肠方便。  
还有一种方式是使用网络资源，比如图床或开源静态资源托管网站，晴雪UI是支持异步加载网络资源的，但不要做坏事哦。

此外，晴雪UI还提供了复古感十足的“服务端发送HTML给客户端渲染”方案，可以完全在服务端管理客户端用户界面。

### 还想了解更多？

- 其实晴雪UI还做了浏览器的缩放功能，对着页面按住CTRL+滚轮即可进行放大缩小，对于玩家，可以轻松调整适配任何尺寸的窗口。
- 其实KJS是可选的前置，不装的话没法解析HTML里写的JavaScript，也没法使用调试台，但对于模组开发者来说，影响不大，完全可以使用Java写DOM操作。
- 其实晴雪UI的热重载非常快，二十张不同QQ头像+三个自定义字体，包含内嵌JS在内，重载整个document只需要一秒。如果你在写KJS的客户端脚本，没准可以用来偷懒，不过晴雪UI的资源路径内没有PJS的补全。注：此处说的重载不会重载资源包。
- 其实晴雪UI常规状态下并无明显的性能瓶颈，尤其在26.1及以上版本，几乎没有性能忧虑，但作者很难测试到所有情况，如果你偶然发现有突发卡顿，请立即反馈。
- 其实Vue、Svelte等现代前端框架的丐版移植正在绝赞进行中，目前简单的Vue已有kltyton贡献的PR，而Svelte会在将来内置，有兴趣的话欢迎加群共同探讨！

### 画廊

- [win98](https://github.com/Ximelon0815/Arachne-Computer)
![win98](https://resource-api.xyeidc.com//client/members/pics/8b0f6624)

- [极械工坊](https://www.mcmod.cn/class/27007.html)载具设置
![极械工坊载具设置](https://resource-api.xyeidc.com//client/members/pics/76ae0ed1)

- [FindMe](https://www.mcmod.cn/class/28285.html)伙伴管理
![FindMe伙伴管理](https://resource-api.xyeidc.com//client/members/pics/3b0f4d67)

有个标签禁用缩放
ctrl shift i