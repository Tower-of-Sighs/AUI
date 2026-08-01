# Overlay Document 使用文档

ApricityUI 的 Overlay 是一个不依附于 Minecraft Screen 的普通 Document。通过 Document.create(path) 创建后，它会被加入全局 Document 列表，并在客户端 GUI 或游戏 HUD 的绘制阶段自动渲染。

Overlay 适合状态栏、提示、Toast、HUD、浮动面板、全屏遮罩和开发工具。它不负责服务端菜单槽位，也不需要打开一个新的 Minecraft Screen。

## 1. Overlay 和 Screen 的区别

| 项目 | Overlay Document | ApricityScreen |
| --- | --- | --- |
| 创建方式 | ApricityUI.createDocument(path) | new ApricityScreen(path) |
| 是否替换当前 Minecraft Screen | 否 | 是 |
| 无 Screen 时是否显示 | 是 | 不适用 |
| Minecraft Screen 打开后 | 普通 Overlay 默认隐藏；持久化 Overlay 继续显示 | 作为当前 Screen 绘制 |
| 是否有 Menu 或真实槽位 | 否 | 否 |
| 典型用途 | HUD、Toast、弹窗、常驻工具条 | 设置页、独立页面、全屏 UI |

Overlay 的 Document 不需要手动调用绘制函数。正常情况下，创建后由客户端的全局绘制流程自动绘制。

如果页面需要玩家背包、方块实体容器或服务端数据源，应使用 ApricityContainerScreen，而不是 Overlay。

## 2. 最小示例

### 2.1 HTML 资源

将文件保存为：

~~~text
src/main/resources/assets/apricityui/apricity/overlays/status.html
~~~

~~~html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
</head>
<body>
    <div id="status" class="status">Loading...</div>
</body>
</html>
~~~

Overlay 的资源逻辑路径是 overlays/status.html。不要把 assets/apricityui/apricity/ 写进路径参数。

### 2.2 Java 创建和修改

~~~java
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public final class StatusOverlay {
    private static Document document;

    private StatusOverlay() {
    }

    public static void open() {
        if (document != null && document.isActive()) return;

        document = ApricityUI.createDocument("overlays/status.html");
        if (document == null) return;

        Element status = document.getElementById("status");
        if (status != null) {
            status.setTextContent("Ready");
        }
    }

    public static void setText(String text) {
        if (document == null || document.isDisposed()) return;

        Element status = document.getElementById("status");
        if (status != null) {
            status.setTextContent(text == null ? "" : text);
        }
    }

    public static void close() {
        if (document == null) return;
        document.remove();
        document = null;
    }
}
~~~

创建和修改必须在 Minecraft 客户端线程执行。若调用来自网络回调或其他线程，应使用 Minecraft.getInstance().execute(...) 切回客户端线程。

### 2.3 KubeJS 客户端脚本

~~~javascript
var overlayDocument = ApricityUI.createDocument("overlays/status.html");

if (overlayDocument !== null) {
    var status = overlayDocument.getElementById("status");
    if (status !== null) {
        status.textContent = "Ready";
    }
}
~~~

KubeJS 中的 ApricityUI 客户端绑定提供同一组 Overlay API。HTML 页面自身的脚本则直接使用页面内的 document 对象，不需要再次调用 createDocument。

## 3. Overlay API

### 3.1 创建、查询和移除

~~~java
Document ApricityUI.createDocument(String path)
void ApricityUI.removeDocument(String path)
ArrayList<Document> ApricityUI.getDocument(String path)
Document ApricityUI.getDocumentByUUID(String uuid)
List<Document> ApricityUI.getAllDocument()
~~~

| API | 说明 |
| --- | --- |
| createDocument(path) | 创建并立即解析、布局和加入绘制队列；资源不存在时返回 null |
| removeDocument(path) | 移除指定路径的所有 Document |
| getDocument(path) | 返回同一路径的所有活动 Document，因此返回值是列表 |
| getDocumentByUUID(uuid) | 按 UUID 获取一个活动 Document |
| getAllDocument() | 获取当前所有活动 Document，包括 Overlay、Screen 绑定文档和其他文档 |

同一个路径可以创建多个 Document。因为 removeDocument(path) 会移除该路径的全部实例，如果只想关闭自己创建的实例，应保存返回值并调用 document.remove()。

~~~javascript
var instances = ApricityUI.getDocument("overlays/status.html");
for (var i = 0; i < instances.length; i++) {
    instances[i].remove();
}
~~~

### 3.2 Document 常用方法

~~~java
String document.getPath()
UUID document.getUuid()
boolean document.isActive()
boolean document.isDisposed()
long document.getRefreshGeneration()
Element document.getElementById(String id)
Element document.querySelector(String selector)
List<Element> document.querySelectorAll(String selector)
void document.remove()
void document.refresh()
void document.setReloadPersistent(boolean persistent)
boolean document.isReloadPersistent()
~~~

Document 是可操作的 DOM 根对象。推荐通过选择器找到已有节点，再修改 textContent、属性、class 或 style；不要为了更新一行状态反复创建和销毁完整 Document。

## 4. Overlay 的显示时机

Overlay 的显示规则容易和 ApricityScreen 混淆：

| 当前状态 | 普通 Overlay | reloadPersistent=true 的 Overlay |
| --- | --- | --- |
| 游戏中，没有 Minecraft Screen | 显示 | 显示 |
| 打开原版 Screen | 隐藏 | 显示 |
| 打开 ApricityScreen | 隐藏 | 由 ApricityScreen 绘制 |
| 打开 ApricityContainerScreen | 隐藏 | 由 ApricityContainerScreen 绘制 |

普通 Overlay 默认适合游戏 HUD。设置 reloadPersistent 后，它会继续显示在原版 Screen、ApricityScreen 或容器 Screen 上，适合 Toast、DevTools 工具条和全局通知。

~~~java
Document overlay = ApricityUI.createDocument("overlays/notification.html");
if (overlay != null) {
    overlay.setReloadPersistent(true);
}
~~~

持久化只影响资源重载和 Screen 存在时的绘制，不会让 Document 永久存在。调用 overlay.remove() 后它仍然会被销毁。

### 4.1 手动渲染模式

setManuallyRendered(true) 是给自定义渲染宿主使用的高级开关。设置后，Document 会从全局 Overlay 绘制流程和全局输入分发流程中排除，调用方必须自己负责绘制和输入转发。

普通 Overlay 不要设置这个值。只有在自定义 Screen、预览窗口或世界窗口已经拥有独立渲染流程时，才使用手动渲染模式；否则 Document 会创建成功但不会出现在画面中。

## 5. 生命周期

创建过程大致如下：

~~~text
ApricityUI.createDocument(path)
        |
        v
检查 HTML 资源
        |
        v
创建 Document 并加入全局列表
        |
        v
解析 HTML、CSS、JS，计算初始布局
        |
        v
DOMContentLoaded -> load
        |
        v
全局 Overlay 绘制和输入分发
        |
        v
document.remove()
        |
        v
清理焦点、鼠标状态和 MutationObserver
~~~

### 5.1 创建

Document.create(path) 会立即执行 HTML 资源解析、CSS 处理、DocumentExpander、页面脚本和初始布局。HTML 不存在时返回 null，并写入错误日志。

不要在每帧调用 createDocument。创建操作会解析资源并构建完整 DOM，适合在打开 Overlay 时执行一次。

### 5.2 修改

修改 DOM 或样式后，框架会通过脏标记安排增量样式、布局和绘制更新。动态列表可以复用节点：

~~~javascript
var label = overlayDocument.getElementById("value");
if (label !== null) {
    label.textContent = String(newValue);
}
~~~

批量修改大量节点时，尽量在同一个客户端任务中完成，避免把一组相关更新拆成很多帧。

### 5.3 移除

document.remove() 是关闭单个 Overlay 的首选方式：

~~~java
if (overlay != null) {
    overlay.remove();
    overlay = null;
}
~~~

Document.remove() 会使 Document 进入 disposed 状态，清理焦点、按下元素、hover 状态和 MutationObserver。它不会像 ApricityScreen.onClose() 那样额外替 body 触发 unload 事件。

如果 Overlay 需要自己的关闭事件，应在业务代码中显式执行清理：

~~~javascript
function closeOverlay() {
    if (overlayDocument !== null) {
        overlayDocument.remove();
        overlayDocument = null;
    }
    // 清理 JavaScript 定时器、外部引用和业务状态
}
~~~

### 5.4 重载

普通 Overlay 会参与 ClientLoader 的全局刷新：

1. END 重新扫描 HTML、CSS 和 JS；
2. Document.refresh() 清空旧 DOM 和渲染缓存；
3. 重新执行页面脚本；
4. 重新触发 DOMContentLoaded 和 load。

reloadPersistent=true 的 Document 会被 Document.refreshAll() 跳过。因此持久化 Overlay 在 END 后保留动态 DOM 和运行时状态，但资源文件的修改也不会自动应用。

持久化 Overlay 如果需要重新读取资源，可以在确认业务状态允许丢失后手动调用 refresh()。更稳妥的方式是把可恢复状态保存到 Java 或 KubeJS 数据，再在 load 事件中重新写入 DOM。

## 6. 页面模板结构

推荐让 Overlay HTML 自己提供一个明确的根容器：

~~~html
<body>
    <div id="overlay-root">
        <div id="panel"></div>
    </div>
</body>
~~~

全屏 Overlay 通常使用：

~~~css
html, body {
    width: 100%;
    height: 100%;
    margin: 0;
}

#overlay-root {
    position: fixed;
    inset: 0;
}
~~~

普通 HUD 不需要覆盖全屏，可以只给面板设置 fixed 或 absolute 定位：

~~~css
#status {
    position: fixed;
    right: 16px;
    top: 16px;
}
~~~

Overlay 的固定定位基于当前 Document 的 viewport。viewport 的 mode、字号和缩放配置与 ApricityScreen 相同，完整说明见：

[ApricityScreen 使用文档](apricity-screen.md)

Overlay 常用的 meta：

~~~html
<meta name="aui-font-mode" content="web">
<meta name="aui-viewport" content="mode=browser">
~~~

## 7. Document 之间的层级

### 7.1 同一个 Document 内

同一个 Document 内按普通 CSS 绘制规则处理。可以使用 position、z-index、transform 和 top layer：

~~~css
.hud {
    position: fixed;
    z-index: 10;
}

.modal {
    position: fixed;
    inset: 0;
    z-index: 9000;
}
~~~

z-index 只负责当前 Document 内的层叠关系，不能把一个 Document 放到另一个 Document 上面。

### 7.2 不同 Document 之间

当前实现按文档根节点和 body 的 translateZ 计算顶层 Document 顺序。需要让一个独立 Overlay 位于另一个 Overlay 上方时，应在根元素或 body 上设置不同的 translateZ：

~~~css
body {
    transform: translateZ(100px);
}
~~~

数值越大，Document 越靠前。相同 translateZ 时通常保留 Document 的创建顺序，后创建的文档位于更前面。不要只提高子元素的 z-index 来解决跨 Document 层级问题。

使用 translateZ 时建议只在 html 或 body 中设置一次，避免同时在两者上设置后产生意外的叠加值。

### 7.3 Document 内的 top layer

Java 可以把某个元素标记为当前 Document 的 top layer：

~~~java
Element dialog = Element.init(document.createElement("div"));
dialog.setAttribute("class", "modal");
dialog.setTopLayer(true);
document.body.appendChild(dialog);
~~~

top layer 元素仍保留原来的 DOM 父子关系和事件路径，但会在普通 Document 内容之后绘制，并且不继承祖先的 overflow 裁剪。它只影响当前 Document，不会改变不同 Document 之间的层级。

适合使用 top layer 的场景：

- 同一 Overlay 内的弹窗；
- 下拉菜单和选择器；
- 需要超出父容器 overflow 范围显示的浮层；
- 编辑器的选择框和确认对话框。

## 8. 输入事件和穿透

### 8.1 默认分发

客户端会按前到后的顺序检查可交互 Document。一个 Overlay 没有命中元素，或事件没有被消费时，输入可以继续传给后面的 Document。

常用事件：

~~~javascript
var button = overlayDocument.getElementById("close");

button.addEventListener("click", function (event) {
    closeOverlay();
});

button.addEventListener("mousemove", function (event) {
    button.classList.add("hover");
});
~~~

viewport 缩放会在命中测试前自动反变换。事件回调里的 clientX 和 clientY 是当前 Document 的逻辑坐标，不要再次手动乘 renderScale。

### 8.2 阻止底层输入

需要让 Overlay 成为模态遮罩时，设置：

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

并让遮罩覆盖整个 viewport：

~~~css
.modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, .55);
    pointer-events: auto;
}
~~~

aui-mouse-events=intercept 会在命中可交互元素时消费原生鼠标输入。它不是事件监听器开关，也不能让 display:none、visibility:hidden 或被裁剪的元素命中。

如果 Overlay 只是显示信息，不应阻挡底层交互，可以让容器穿透、让实际内容接收输入：

~~~css
.toast-layer {
    pointer-events: none;
}

.toast {
    pointer-events: auto;
}
~~~

这也是 Toast 常用的结构：空白区域不挡鼠标，消息卡片本身可以点击关闭。

### 8.3 滚轮和页面缩放

未设置 intercept 的 Overlay 对 wheel 事件默认允许继续向下分发。设置 intercept 后，命中 Overlay 的滚轮可以被它消费。

Ctrl + 鼠标滚轮的 viewport 缩放目标也会按顶层命中 Document 选择。持久化 Overlay 是否允许缩放穿透由客户端配置控制：

~~~toml
[input]
viewportZoomPassThrough = true
~~~

当该配置为 true 时，未设置 aui-mouse-events=intercept 的持久化 Overlay 可以被缩放目标选择逻辑跳过，从而缩放下面的页面。需要 Overlay 自己优先接收缩放时，设置 intercept。

### 8.4 焦点

Overlay 中的 input、textarea 和 select 可以获得焦点。点击另一个 Document 时，AUI 会清理其他 Document 的焦点和文本选择。

关闭 Overlay 前建议主动清除业务引用；Document 被移除后，保存的 Element 引用不应继续使用。

## 9. 常见 Overlay 模式

### 9.1 HUD 状态栏

HUD 通常是一个普通 Overlay，不设置 reloadPersistent：

~~~java
Document hud = ApricityUI.createDocument("overlays/hud.html");
~~~

它只在游戏内没有其他 Minecraft Screen 时显示。打开背包或设置界面后隐藏，关闭 Screen 后自动恢复。

### 9.2 全局通知或 Toast

Toast、错误提示和加载进度需要在其他 Screen 上也显示，应使用一个持久化 Overlay：

~~~java
Document notifications = ApricityUI.createDocument("overlays/notifications.html");
if (notifications != null) {
    notifications.setReloadPersistent(true);
}
~~~

同一类通知应复用一个 Document，在其中追加和移除消息节点，不要为每条消息创建一个新的 Document。ApricityUI 内置 ToastManager 采用的就是这种模式。

### 9.3 模态对话框

模态对话框通常有三层：

1. 持久化 Overlay Document；
2. 全屏遮罩元素；
3. 设置为 top layer 的对话框元素。

~~~java
Element shade = Element.init(document.createElement("div"));
shade.setAttribute("class", "modal-overlay");

Element dialog = Element.init(document.createElement("div"));
dialog.setAttribute("class", "modal-dialog");
dialog.setTopLayer(true);

shade.appendChild(dialog);
document.body.appendChild(shade);
~~~

如果要关闭对话框，优先移除对话框 DOM 节点；只有整个 Overlay 不再使用时才移除 Document。

### 9.4 多实例浮窗

同一个 HTML 模板可以创建多个实例，但每个实例应使用独立的状态和引用：

~~~java
Document left = ApricityUI.createDocument("overlays/window.html");
Document right = ApricityUI.createDocument("overlays/window.html");
~~~

不要用 removeDocument("overlays/window.html") 关闭其中一个，因为它会关闭两个实例。使用各自的 Document.remove()。

## 10. KubeJS 管理示例

~~~javascript
var overlayDocument = null;

function openStatusOverlay() {
    if (overlayDocument !== null && overlayDocument.isActive()) return;

    overlayDocument = ApricityUI.createDocument("overlays/status.html");
    if (overlayDocument === null) {
        console.error("Unable to create status overlay");
        return;
    }

    var status = overlayDocument.getElementById("status");
    if (status !== null) status.textContent = "Connected";
}

function updateStatusOverlay(value) {
    if (overlayDocument === null || overlayDocument.isDisposed()) return;

    var status = overlayDocument.getElementById("status");
    if (status !== null) status.textContent = String(value);
}

function closeStatusOverlay() {
    if (overlayDocument === null) return;
    overlayDocument.remove();
    overlayDocument = null;
}
~~~

脚本热重载后，JavaScript 全局变量会重新初始化。需要跨脚本重载保存的状态应放到 KubeJS 的持久化数据或 Java 侧对象，不要只保存在 overlayDocument 变量中。

## 11. 资源重载和持久化取舍

| 需求 | reloadPersistent |
| --- | --- |
| 只在游戏 HUD 显示的状态栏 | false |
| 打开背包、设置页时仍显示的通知 | true |
| 需要 END 后同步 HTML/CSS/JS 修改的开发页面 | false |
| 由 Java 动态维护 DOM，重载时不希望丢失状态 | true，但资源修改不会自动应用 |

持久化 Overlay 不是缓存开关，也不是线程安全保证。它只是告诉全局 refreshAll 跳过此 Document，并在 Minecraft Screen 打开时继续绘制。

开发时如果需要调试一个持久化 Overlay 的资源文件，可以暂时设置 false 后按 END，或者关闭并重新创建该 Document。正式运行时应明确选择“资源热重载”还是“保留运行时 DOM 状态”。

## 12. 常见问题排查

### Overlay 没有显示

检查：

1. 是否在客户端调用 createDocument；
2. 资源路径是否是逻辑路径，例如 overlays/status.html；
3. HTML 是否位于资源扫描目录；
4. Document.create 是否返回 null；
5. 当前是否打开了 Minecraft Screen，而 Document 没有设置 reloadPersistent；
6. Document 是否被设置为 manuallyRendered=true；
7. 根元素是否被 display:none、visibility:hidden 或零尺寸样式隐藏。

缺失资源、HTML 解析异常、CSS 处理异常和 JS 执行异常会输出 AUI 日志。先按路径搜索 AUI Resource、AUI HTML 和 AUI Document 日志。

### Overlay 盖住了错误的页面

检查 Document 根节点或 body 的 translateZ。跨 Document 时，子元素 z-index 不起作用。相同 translateZ 的多个 Document 建议不要依赖创建顺序，应该给不同职责的 Overlay 设置明确的层级值。

### 点击穿透或无法点击

检查：

- 是否设置了 aui-mouse-events=intercept；
- 遮罩或内容是否设置了 pointer-events:none；
- 目标元素是否有实际布局尺寸；
- 是否有更前面的 Document 命中了鼠标；
- 是否手动对 clientX/clientY 做了错误的缩放转换。

### Toast 总在最上层

检查 Toast Document 的 body 是否有较大的 translateZ，以及客户端配置中的 viewportZoomPassThrough。Toast 需要显示在其他页面上时设置 reloadPersistent 是合理的，但它不应该通过异常大的层级值阻止所有其他工具层。应为 Toast、DevTools 和模态对话框规划互不冲突的层级范围。

### END 后 Overlay 没有更新

如果 Document 设置了 reloadPersistent=true，refreshAll 会跳过它。这是预期行为。需要重新读取资源时，关闭后重新 createDocument，或在资源重载完成后显式调用 refresh()。

### 关闭后仍然响应事件

确认调用的是保存实例上的 document.remove()，而不是只把 JavaScript 变量设为 null。变量置空不会从全局 Document 列表中移除对象。

## 13. 性能建议

- 每类 Overlay 尽量复用一个 Document，不要每帧 createDocument。
- 高频更新只改文本、class、属性或少量节点。
- 动态列表复用节点，避免每次刷新都重建整个 body。
- Toast、通知和弹窗在同一个宿主 Document 内管理，减少跨 Document 命中测试和层级竞争。
- 不要每帧调用 refresh()；refresh() 会重建 DOM、重新执行脚本并重新计算布局。
- 持久化 Overlay 应保持轻量，因为它会在各种 Minecraft Screen 上继续绘制。
- 不需要交互的装饰层使用 pointer-events:none，减少输入命中和事件分发。

## 14. 相关文档和源码

- [ApricityScreen 使用文档](apricity-screen.md)
- [tools 使用文档](tools.md)
- [wpt 使用文档](wpt.md)
- [ApricityUI.java](../src/main/java/com/sighs/apricityui/ApricityUI.java)
- [ApricityUIClientUtil.java](../src/main/java/com/sighs/apricityui/util/kjs/ApricityUIClientUtil.java)
- [Document.java](../src/main/java/com/sighs/apricityui/init/Document.java)
- [Client.java](../src/main/java/com/sighs/apricityui/instance/Client.java)
- [Base.java](../src/main/java/com/sighs/apricityui/render/Base.java)
- [DocumentLayerOrder.java](../src/main/java/com/sighs/apricityui/render/DocumentLayerOrder.java)
- [ToastManager.java](../src/main/java/com/sighs/apricityui/ui/ToastManager.java)
