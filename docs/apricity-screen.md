# ApricityScreen 使用文档

ApricityScreen 是 ApricityUI 对 Minecraft Screen 的直接封装。它负责把一个 HTML 资源加载成 Document，绘制到当前 Minecraft 界面，并把鼠标、键盘、焦点和滚轮输入转发给这个文档。

本文只介绍客户端直接使用的 ApricityScreen。如果页面需要真实菜单、玩家背包或服务端容器槽位，请使用文末介绍的 ApricityContainerScreen 方案。

## 1. 两种 Screen 的区别

| 类型 | 创建方式 | 适用场景 | 是否有 Minecraft 菜单槽位 |
| --- | --- | --- | --- |
| ApricityScreen | 客户端直接 new ApricityScreen(path) | 纯 UI、设置页、调试页、客户端工具 | 否 |
| ApricityContainerScreen | ApricityUI.screen(path) 或 ApricityUI.menu(...).bind(...) | UI-only 菜单或真实容器绑定 | 前者没有真实槽位，后者可以有 |
| WorldWindow | ApricityUI.createWorldWindow(...) | 渲染在世界中的 HTML 窗口 | 否 |

当前实现中，ApricityUI.screen(path) 会发送请求给服务端，最终通过网络打开 ApricityContainerScreen。即使页面没有 container，它也是 UI-only 的容器 Screen，不是 ApricityScreen。

要得到真正的 ApricityScreen，需要在客户端直接设置 Minecraft 当前 Screen：

~~~java
Minecraft.getInstance().setScreen(
        new ApricityScreen("screens/example.html")
);
~~~

## 2. 最小可运行示例

### 2.1 HTML

将文件保存为：

~~~text
src/main/resources/assets/apricityui/apricity/screens/example.html
~~~

内容可以从下面的最小示例开始：

~~~html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        body {
            margin: 0;
            color: #eeeeee;
            background: #20242b;
            font-size: 16px;
        }

        .panel {
            width: 360px;
            margin: 40px auto;
            padding: 16px;
            background: #303640;
        }

        button {
            padding: 6px 12px;
        }
    </style>
</head>
<body>
    <main class="panel">
        <h2>ApricityScreen</h2>
        <p id="status">Ready</p>
        <button id="reload">Click me</button>
    </main>

    <script>
        document.getElementById("reload").addEventListener("click", function () {
            document.getElementById("status").textContent = "Clicked";
        });
    </script>
</body>
</html>
~~~

资源路径写的是逻辑路径 screens/example.html，不要把 assets/apricityui/apricity/ 写进 ApricityScreen 的构造参数。

### 2.2 Java 打开页面

~~~java
import com.sighs.apricityui.instance.ApricityScreen;
import net.minecraft.client.Minecraft;

public final class ExampleScreens {
    private ExampleScreens() {
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(
                new ApricityScreen("screens/example.html")
                        .setPauseGame(true)
                        .setShowDefaultBackground(false)
        );
    }
}
~~~

setPauseGame 和 setShowDefaultBackground 都返回当前 Screen，因此可以链式调用。

如果调用发生在非 Minecraft 客户端线程，应切回客户端线程：

~~~java
Minecraft minecraft = Minecraft.getInstance();
minecraft.execute(() -> minecraft.setScreen(
        new ApricityScreen("screens/example.html")
));
~~~

## 3. HTML 资源路径

HTML 的逻辑路径统一使用 /，例如：

~~~text
screens/example.html
devtools/resource.html
tests/form-controls-test.html
~~~

开发时常用的实际位置有：

~~~text
src/main/resources/assets/apricityui/apricity/screens/example.html
run/apricity/screens/example.html
~~~

资源加载层按以下顺序合并：

1. 模组或其他资源包中的 assets/apricityui/apricity/...。
2. 游戏实例目录的 apricity/...。
3. 开发环境资源目录 src/main/resources/assets/apricityui/apricity/...。

后加载的同路径资源会覆盖先加载的资源。因此开发目录通常优先级最高，实例目录适合在不重新打包模组的情况下覆盖资源。

HTML、CSS、JS 和图片的相对路径以当前资源所在目录为基准。HTML 入口必须以 .html 结尾。通过网络请求打开页面时，路径还必须经过规范化校验：不能包含 ..，不能使用不完整的文件名路径。

资源会在客户端初始化时扫描。修改本地 HTML、CSS 或 JS 后，按 END 会触发客户端资源重载，当前普通 Document 会重新执行解析、样式计算和脚本。

## 4. ApricityScreen API

### 4.1 构造和显示选项

~~~java
public ApricityScreen(String templatePath)
public ApricityScreen setPauseGame(boolean pauseGame)
public ApricityScreen setShowDefaultBackground(boolean showDefaultBackground)
public boolean isPauseGame()
public boolean isShowDefaultBackground()
~~~

| 方法 | 默认值 | 说明 |
| --- | --- | --- |
| ApricityScreen(path) | 无 | 创建 Screen 对象；此时还没有创建 Document |
| setPauseGame(value) | false | 控制 isPauseScreen()，决定打开页面时是否暂停游戏 |
| setShowDefaultBackground(value) | false | 是否先绘制 Minecraft 的标准 Screen 背景 |
| isPauseGame() | - | 读取暂停设置 |
| isShowDefaultBackground() | - | 读取背景设置 |

默认情况下，页面不暂停游戏，也不绘制 Minecraft 默认背景。纯 UI 页面通常保持默认值；设置页或需要暂停世界的页面可以显式设置 true。

### 4.2 Document 和缩放

~~~java
public Document getLinkedDocument()
public boolean handleViewportZoom(boolean zoomIn)
public boolean resetViewportZoom()
~~~

getLinkedDocument() 返回当前 Screen 在 init() 中创建的 Document。以下时机可能返回 null：

- Screen 刚构造但还没有被 Minecraft 初始化；
- HTML 资源不存在或解析失败；
- Screen 已关闭或被移除。

handleViewportZoom 和 resetViewportZoom 会遵守 HTML 中 aui-viewport 的 user-scalable、min-zoom、max-zoom 和 zoom-step 设置。缩放成功返回 true，没有 Document、禁止用户缩放或已经到达边界时返回 false。

如果需要由 Java 或开发工具设置任意缩放值，可以从 Document 调用：

~~~java
Document document = screen.getLinkedDocument();
if (document != null) {
    document.setViewportZoom(1.25d);
}
~~~

setViewportZoom 是编辑器控制接口，即使 user-scalable=false 也可以设置；它仍然会被 min-zoom 和 max-zoom 限制。

## 5. Screen 和 Document 生命周期

生命周期的关键关系如下：

~~~text
new ApricityScreen(path)
        |
        | Minecraft.setScreen(...)
        v
init()
  -> Document.create(path)
  -> 解析 HTML、CSS、JS
  -> DOMContentLoaded
  -> load
        |
        v
render() / 输入事件 / resize()
        |
        v
onClose()
  -> body unload
  -> Document.remove()
  -> 清理 viewport 覆盖和光标
~~~

具体行为：

1. 构造函数只保存模板路径，不读取 HTML。
2. init() 创建并绑定新的 Document；如果 init() 被重复调用，旧 Document 会先被移除。
3. Document 创建后会执行 HTML 解析、CSS 计算、DOM 扩展器和页面脚本。
4. DOMContentLoaded 和 load 在页面脚本执行后依次触发。
5. 窗口大小改变时，resize() 会重新应用 viewport，并请求布局刷新。
6. 正常关闭时，onClose() 会先向 body 触发 unload，再移除 Document。
7. removed() 会兜底移除 Document，即使调用路径没有经过完整的关闭流程。

不要在 Screen 构造函数中缓存 getLinkedDocument()。应在 init() 之后读取，并考虑 resize、重新初始化和资源重载导致的 DOM 重建。

如果逻辑需要识别同一个 Document 是否已经被重载，可以保存：

~~~java
long generation = document.getRefreshGeneration();
if (document.isCurrentGeneration(generation)) {
    // Document 仍然处于活动状态，且没有被 refresh() 重建
}
~~~

Document.refresh() 会保留 Document 本身和 UUID，但会重建 DOM，重新执行页面脚本，并递增 refresh generation。重新打开 Screen 或 Screen 再次 init() 时，通常会得到新的 Document UUID。

## 6. Viewport 配置

在 head 中加入：

~~~html
<meta name="aui-viewport" content="mode=browser">
~~~

content 是逗号或分号分隔的键值列表。没有 mode 时默认为 gui。

### 6.1 模式

| 模式 | 别名 | 行为 |
| --- | --- | --- |
| gui | mc、default | 使用 Minecraft GUI 尺寸作为逻辑 viewport，兼容旧页面 |
| browser | css、web | 使用 CSS viewport 宽度，并按当前 GUI 窗口宽度缩放；未指定高度时按窗口高度推导 |
| window | native、screen、fullscreen | 使用监视器推导的 CSS 宽度和当前窗口高度，保持固定的渲染比例，窗口改变时横向布局更稳定 |
| fixed | 无 | 使用显式的逻辑宽度、高度和缩放比例 |

推荐选择：

- Minecraft 风格的小型界面：mode=gui。
- 类网页的设置页、开发工具：mode=browser。
- 需要固定设计稿尺寸：mode=fixed,width=427,height=249。
- 希望窗口改变时 CSS 横向布局不随宽度重新计算：mode=window。

### 6.2 fixed 模式

~~~html
<meta name="aui-viewport"
      content="mode=fixed,width=427,height=249,scale=fit">
~~~

fixed 支持：

| 选项 | 示例 | 说明 |
| --- | --- | --- |
| width | width=427 | 逻辑宽度，默认 427 |
| height | height=249 | 逻辑高度，默认 249 |
| scale 数值 | scale=1 | 使用指定渲染比例 |
| scale=fit | scale=fit | 等比缩放，完整放入当前 GUI 窗口 |
| scale=contain | scale=contain | fit 的兼容别名 |
| scale=gui | scale=gui | 使用 GUI 坐标比例 |
| scale=window | scale=window | 使用窗口比例 |
| scale=mc | scale=mc | gui 的兼容别名 |
| scale=native | scale=native | window 的兼容别名 |

### 6.3 用户缩放

所有 viewport 模式都支持：

~~~html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
~~~

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| zoom | 1 | 初始缩放，也作为 Ctrl+0 的重置目标 |
| min-zoom | 0.5 | 最小用户缩放 |
| max-zoom | 3 | 最大用户缩放 |
| zoom-step | 0.1 | 每次缩放的步进 |
| user-scalable | true | 是否允许用户通过快捷键缩放 |

允许用户缩放时，ApricityScreen 支持：

- Ctrl + 鼠标滚轮：放大或缩小；
- Ctrl + = 或 Ctrl + +：放大；
- Ctrl + -：缩小；
- Ctrl + 0：恢复到 meta 中的 zoom 值。

缩放值按模板路径保存到客户端配置目录下的 apricityui/viewport-zoom.properties，因此重新打开同一路径页面时可能保留上次缩放值。修改 meta 后，新的范围会对已保存值重新限制。

## 7. 字体模式

在 head 中加入：

~~~html
<meta name="aui-font-mode" content="web">
~~~

| 值 | 默认字体大小 | 适用情况 |
| --- | ---: | --- |
| mc | 9 | 兼容 Minecraft 风格的旧 UI |
| web | 16 | 按网页常见的逻辑字号布局 |
| web-scaled | 16 | 网页逻辑字号，并按 ApricityUI 的 Minecraft 字形比例绘制；默认值 |

aui-font-mode 影响默认字号、根字号和文字栅格化比例。显式设置 CSS font-size 后，字号仍以 CSS 声明为准，但文字的基础渲染模式仍来自该 meta。

如果页面是从浏览器设计稿迁移，通常使用：

~~~html
<meta name="aui-font-mode" content="web">
<meta name="aui-viewport" content="mode=browser">
~~~

如果页面是 Minecraft 原生风格的小控件，使用 mc 往往更容易与现有尺寸对齐。

## 8. 鼠标、滚轮和键盘事件

### 8.1 鼠标事件

常用事件包括：

~~~javascript
const button = document.getElementById("button");

button.addEventListener("click", function (event) {
    event.preventDefault();
});

button.addEventListener("mousedown", function (event) {
    console.log(event.clientX, event.clientY, event.button);
});

button.addEventListener("wheel", function (event) {
    console.log(event.deltaY);
});
~~~

支持常见的 mousemove、mousedown、mouseup、click、dblclick、wheel、mouseover、mouseout、mouseenter 和 mouseleave，并提供对应的 pointer 兼容事件。

viewport 的缩放和偏移会在命中测试前自动反变换。事件回调中的坐标是 Document 的逻辑坐标，通常不需要自行乘以 renderScale。

### 8.2 事件拦截

如果页面需要阻止鼠标事件继续传给下方的 Minecraft 输入或其他 Document，可以在 HTML 中设置：

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

intercept 也接受 block、true、yes、on 和 1。默认不设置时，AUI 仍会尝试派发 HTML 鼠标事件，但不会对所有命中区域强制消费原生 Minecraft 输入。

拦截是按命中区域生效的。若要让整个页面都接收输入，应确保页面的可交互区域覆盖整个 viewport，并且没有被 display、visibility、裁剪或 pointer 设置排除。

### 8.3 滚轮缩放和穿透

Ctrl + 鼠标滚轮的缩放目标按鼠标所在的最前层 Document 选择。对持久化 overlay，客户端配置可以允许缩放目标穿透未声明拦截的 overlay：

~~~toml
[input]
viewportZoomPassThrough = true
~~~

配置文件通常是：

~~~text
run/config/apricityui-client.toml
~~~

要让一个 overlay 成为稳定的缩放目标，或者不允许输入穿透，可以在页面中使用：

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

user-scalable=false 只禁止用户快捷键缩放，不会禁止开发工具或 Java 代码通过 setViewportZoom 设置值。

### 8.4 键盘和焦点

页面支持 keydown、keyup、focus、blur 以及表单控件的 input、change、提交等事件。

文本输入控件获得焦点后，AUI 会处理字符输入、退格、删除、左右移动、选择，以及常用的剪贴板快捷键：

~~~javascript
const input = document.getElementById("name");

input.addEventListener("input", function () {
    console.log(input.value);
});

input.focus();
~~~

需要注意：

- keydown 中调用 preventDefault() 可以阻止对应的默认处理；
- 页面脚本不要假设存在浏览器线程或真实 DOM；
- Minecraft 的 Screen 快捷键和 HTML 键盘事件共用输入入口，处理文本输入时应让输入控件保持焦点；
- 如果自定义 Screen 还要处理原生按键，应先确认该按键没有被 HTML 文档消费。

## 9. 获取当前 Screen 的 Document

### 9.1 Java

~~~java
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ApricityScreen;
import net.minecraft.client.Minecraft;

if (Minecraft.getInstance().screen instanceof ApricityScreen screen) {
    Document document = screen.getLinkedDocument();
    if (document != null && document.body != null) {
        document.body.setAttribute("data-state", "open");
    }
}
~~~

getLinkedDocument() 只表示当前这个 ApricityScreen 的 Document，不会从所有 Document 中按路径猜测目标。

### 9.2 KubeJS 客户端脚本

客户端绑定提供：

~~~javascript
const document = ApricityUI.getCurrentScreenDocument();
if (document !== null) {
    const button = document.getElementById("button");
}
~~~

该方法只在当前 Screen 真的是 ApricityScreen 时返回 Document。通过 ApricityUI.screen(path) 打开的页面通常是 ApricityContainerScreen，此时会返回 null。

如果只是需要获取或创建普通 Overlay Document，可以使用：

~~~javascript
const documents = ApricityUI.getDocument("screens/example.html");
const document = ApricityUI.createDocument("overlays/status.html");
~~~

这两个 API 与当前 ApricityScreen 的绑定关系不同，不应使用路径匹配来代替 getCurrentScreenDocument()。

## 10. 需要容器或槽位时的写法

ApricityScreen 不继承 AbstractContainerScreen，也没有 Menu 和真实槽位。如果页面需要玩家背包、方块实体容器或服务端数据源，应使用菜单入口：

~~~java
ApricityUI.menu(player, "screens/inventory.html")
        .bind(binding -> binding.player());
~~~

或者在客户端请求打开页面：

~~~javascript
ApricityUI.screen("screens/inventory.html");
~~~

后者会走服务端网络处理器；模板中的 container 声明决定需要绑定哪些数据源。相关页面实际由 ApricityContainerScreen 承载。

不要把以下需求塞进 ApricityScreen：

- 直接操作 menu.slots；
- 依赖服务端 authoritative inventory；
- 使用 AbstractContainerScreen 的槽位点击和拖拽状态；
- 期望 ApricityUI.getCurrentScreenDocument() 在容器 Screen 上返回值。

## 11. 继承 ApricityScreen 的注意事项

### 11.1 覆盖 init

覆盖 init() 时必须先调用 super.init()，否则 Document 不会创建：

~~~java
@Override
protected void init() {
    super.init();
    Document document = getLinkedDocument();
    if (document != null) {
        // 添加自定义的客户端侧初始化
    }
}
~~~

不要在子类中再次调用 Document.create(templatePath)，否则会同时存在两个同路径 Document，导致绘制和输入重复。

### 11.2 覆盖 render

默认 render() 会负责：

- 绘制可选的 Minecraft 默认背景；
- 应用 viewport 的 render scale 和 scissor；
- 绘制主 Document；
- 绘制持久化 Screen Document；
- 绘制资源预览和伪光标；
- 结束缓冲区批次和帧计时。

如果必须自定义渲染，至少要保留 Document 的 viewport 缩放、裁剪和正确的 buffer flush，否则会出现显示尺寸与鼠标命中坐标不一致。

### 11.3 覆盖关闭流程

覆盖 onClose() 或 removed() 时必须调用父类实现：

~~~java
@Override
public void onClose() {
    // 自定义清理
    super.onClose();
}
~~~

onClose() 会触发 unload、移除 Document、清理 viewport 覆盖并恢复默认光标；removed() 会移除 Document 并清理 viewport 覆盖，但不额外触发 unload。漏掉 super 可能留下已失效的 Document 或输入状态。

## 12. 重载、状态和持久化

按 END 重载时，客户端会：

1. 重新扫描 HTML、CSS、JS；
2. 清理异步资源和字体缓存；
3. 调用普通 Document 的 refresh()；
4. 重新执行页面脚本和生命周期事件；
5. 刷新 DevTools 和资源管理器。

因此以下状态默认不会保留：

- JavaScript 顶层变量；
- 动态创建但没有重新创建的 DOM 节点；
- 输入框当前值；
- 页面脚本注册的运行时对象。

如果某个独立 Overlay Document 需要在 END 时保留，可以调用：

~~~java
document.setReloadPersistent(true);
~~~

但 ApricityScreen 绑定的 Document 通常不应设置为持久化：Screen 关闭时仍会移除它，且持久化 Document 会跳过普通资源重载，容易让页面代码和资源版本不一致。

开发阶段如果发现 END 后页面出现空白、重复监听器或状态复位，优先检查页面脚本是否假设只执行一次。refresh() 每次都会重新执行 HTML 内联脚本和外链脚本。

## 13. HTML 文本转义

普通 HTML 文本支持常见字符引用和数字字符引用：

~~~html
<p>&lt;button&gt; &amp; &quot;文字&quot;</p>
<p>&#x4F60;&#x597D;</p>
~~~

显示结果分别包含 <button> & "文字" 和中文字符。script、style 等原始文本区域不会按普通正文方式解码，以免改变脚本和样式内容。

## 14. 常见问题排查

### 页面空白

按顺序检查：

1. 构造参数是否是 screens/example.html 这种逻辑路径；
2. 文件是否位于 src/main/resources/assets/apricityui/apricity/ 或 run/apricity/；
3. 文件扩展名是否为 .html；
4. 资源是否在 END 后重新扫描；
5. 日志中是否出现 [AUI Resource]、[AUI HTML] 或 [AUI Document] 错误。

缺少 HTML 资源会记录 template resource is missing；空文件、错误标签嵌套、CSS/JS 抽取失败和脚本异常也会记录对应阶段和路径。

### 鼠标事件没有触发

检查：

- 鼠标是否真的落在元素的布局盒内；
- 元素是否被 display:none、visibility:hidden、裁剪或不可交互样式排除；
- 是否在自定义 Screen 中遗漏了父类的输入处理；
- 页面是否被另一个更前面的 Document 覆盖；
- 是否把屏幕坐标再次乘了 renderScale，导致手动坐标偏移。

aui-mouse-events=intercept 主要控制原生事件消费，不是事件监听器开关。没有设置它时，HTML 事件仍可能触发；设置它也不能让不可见元素命中。

### Ctrl+滚轮缩放了错误的页面

检查：

1. 鼠标位置是否同时命中了持久化 overlay；
2. config/apricityui-client.toml 的 [input] viewportZoomPassThrough 是否符合预期；
3. 覆盖层是否设置了 aui-mouse-events=intercept；
4. 页面是否设置了 user-scalable=false。

### 调整窗口后文字或布局偏移

不要在 CSS 和 Java 中同时手动补偿 viewport 缩放。先选择一个明确的 aui-viewport 模式，并让 ApricityScreen 的默认 resize() 执行。只有在自定义渲染时，才需要同步使用 Document 的 viewport 变换。

### END 后页面状态消失

这是普通 Document 的预期行为：END 会调用 refresh()，DOM 和页面脚本都会重建。需要保留的数据应保存到 KubeJS/Java 状态中，并在 load 或 DOMContentLoaded 中重新写回页面，而不是依赖 JavaScript 顶层变量。

### unload 没有触发

ApricityScreen.onClose() 会向 body 触发 unload。如果代码直接走了非标准移除流程，只能保证 removed() 清理 Document，不保证额外触发同一个生命周期事件。页面关闭清理逻辑应尽量绑定在标准关闭流程中，并避免把关键持久化数据只放在 unload。

## 15. 性能建议

- 一个 Screen 创建一次 Document，运行过程中优先修改已有元素、属性、class 和文本，不要每帧重新 Document.create()。
- 动画优先使用 CSS transition/animation 或已有的增量更新机制。
- 不要在每帧遍历完整 DOM 并重建整个 body。
- 页面尺寸稳定时优先使用合适的 fixed 或 browser viewport，减少频繁的布局尺寸变化。
- END 只用于开发重载，不要把它当作运行时状态同步机制。
- 大量动态列表应复用节点，避免反复创建和销毁同规模的 DOM。

## 16. 相关源码

- [ApricityScreen.java](../src/main/java/com/sighs/apricityui/instance/ApricityScreen.java)
- [ApricityContainerScreen.java](../src/main/java/com/sighs/apricityui/instance/ApricityContainerScreen.java)
- [ApricityViewport.java](../src/main/java/com/sighs/apricityui/instance/ApricityViewport.java)
- [Document.java](../src/main/java/com/sighs/apricityui/init/Document.java)
- [HTML.java](../src/main/java/com/sighs/apricityui/resource/HTML.java)
- [ApricityUIClientUtil.java](../src/main/java/com/sighs/apricityui/util/kjs/ApricityUIClientUtil.java)
- [ApricityScreenNetworkHandler.java](../src/main/java/com/sighs/apricityui/instance/network/handler/ApricityScreenNetworkHandler.java)
