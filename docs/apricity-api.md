# ApricityUI 模组专属 API 文档

最后更新：2026-08-02

本文介绍 ApricityUI 在普通 HTML/CSS/JavaScript API 之外提供的模组专属接口，并按 **KJS** 和 **Java** 两部分组织。内容以当前源码中的实际实现为准。

ApricityUI 的 API 大致分成四层：

| 层 | 主要入口 | 用途 |
| --- | --- | --- |
| KJS 桥接 | `ApricityUI` | 在 KubeJS 客户端脚本或服务端脚本中创建 Document、打开页面、创建世界内窗口和绑定容器 |
| Java 统一入口 | `com.sighs.apricityui.ApricityUI` | 在模组代码中使用同样的 Document、Screen、容器和 WorldWindow 能力 |
| 页面运行时 | `Document`、`Element`、`ApricityViewport` | 操作 DOM、布局、事件、视口缩放和渲染状态 |
| Minecraft 集成 | `ApricityScreen`、`ApricityContainerScreen`、`WorldWindow`、AUI 元素 | 把页面接入 Screen、真实物品容器或 Minecraft 世界 |

本文不重复维护标准 Web API。页面脚本中的 DOM、事件、`fetch`、`localStorage`、Canvas 和 CSS 能力见 [Web API 文档](web-api.md)；浏览器辅助行为见 [浏览器辅助功能文档](browser-features.md)。

## 1. 使用前须知

### 1.1 资源路径

所有 API 使用 ApricityUI 的逻辑资源路径。例如资源文件位于：

~~~text
src/main/resources/assets/apricityui/apricity/screens/example.html
~~~

传给 API 的路径是：

~~~text
screens/example.html
~~~

不要传入下面这些完整物理路径：

~~~text
assets/apricityui/apricity/screens/example.html
file:/D:/work/AUI/src/main/resources/...
D:/work/AUI/src/main/resources/...
~~~

本地开发目录、游戏目录下的 `apricity/` 资源目录、资源包和模组内置资源会由 Loader 按当前优先级查找。资源形式、覆盖顺序和资源管理器见 [资源管理文档](resource-manager.md)。

### 1.2 客户端和服务端边界

KJS 中的 `ApricityUI` 不是一组在所有脚本类型中都可调用的空壳对象，而是根据脚本侧注册不同的方法：

| 脚本位置 | 可用能力 | 不应调用 |
| --- | --- | --- |
| KubeJS 客户端脚本 | Document、Toast、客户端 Screen、WorldWindow | 服务端玩家容器绑定 |
| KubeJS 服务端脚本 | `menu(player, path).bind(...)`、服务端打开菜单 | 客户端 Document、Toast、WorldWindow |
| Java 客户端代码 | `ApricityScreen`、Document、WorldWindow、UI 组件 | 把客户端对象从服务端线程使用 |
| Java 服务端代码 | `menu(player, path)`、绑定数据源 | 依赖 Minecraft 客户端类的对象 |

Java 统一入口类本身位于公共包中，但其中的方法仍有明确的运行侧要求。调用前应确认当前代码运行在客户端还是服务端。

### 1.3 创建、显示和销毁是三个不同动作

`Document.create(path)` 只负责创建并注册一个页面文档；它不会自动打开 Minecraft Screen。常见的显示方式如下：

| 目标 | 推荐方式 |
| --- | --- |
| 普通客户端 Screen | Java `new ApricityScreen(path)` 并 `Minecraft#setScreen` |
| 服务端发起的 UI-only 页面 | `ApricityUI.screen(path)` 或 KJS `ApricityUI.screen(path)` |
| 真实 Minecraft 容器 | `ApricityUI.menu(player, path).bind(...)` |
| Overlay / 持久页面 | `ApricityUI.createDocument(path)`，由文档持有者管理生命周期 |
| 世界内页面 | `ApricityUI.createWorldWindow(...)` |

`Document.createInWorld(path)` 只创建世界文档，不会自动把它放进 WorldWindow 列表；要显示它，应使用 `ApricityUI.createWorldWindow(...)` 或手动注册 `WorldWindow`。

## 2. KJS API

### 2.1 注册方式

KubeJS 插件会把两个 Java 绑定类注册为同一个全局名：

~~~javascript
ApricityUI
~~~

客户端绑定来自 `ApricityUIClientUtil`，服务端绑定来自 `ApricityUIServerUtil`。两侧方法名可能相同，但方法属于不同运行侧，不能据此跨侧调用。

### 2.2 客户端 Document API

以下方法在 KubeJS 客户端脚本中可用：

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `ApricityUI.getWindow()` | `Window` | 获取全局页面窗口对象 |
| `ApricityUI.createDocument(path)` | `Document` 或 `null` | 创建普通 Screen/Overlay 文档 |
| `ApricityUI.createInWorldDocument(path)` | `Document` 或 `null` | 创建标记为世界文档的 Document |
| `ApricityUI.removeDocument(path)` | 无 | 移除同路径的全部活动文档 |
| `ApricityUI.getDocument(path)` | `ArrayList<Document>` | 获取同路径的全部活动文档 |
| `ApricityUI.getDocumentByUUID(uuid)` | `Document` 或 `null` | 按 UUID 获取单个文档 |
| `ApricityUI.getCurrentScreenDocument()` | `Document` 或 `null` | 获取当前真正 `ApricityScreen` 绑定的文档 |
| `ApricityUI.getAllDocument()` | `List<Document>` | 获取全部活动文档 |

资源缺失时 `createDocument` 和 `createInWorldDocument` 返回 `null`，调用 DOM 方法前应检查结果。不同实例即使使用同一个路径，也有不同 UUID，因此需要单实例管理时应保存返回的 Document 对象，而不是只保存路径。

示例：创建一个可在资源重载后保留的 Overlay 文档并更新文字：

~~~javascript
var overlay = ApricityUI.createDocument("overlays/status.html");
if (overlay != null) {
    overlay.setReloadPersistent(true);

    var status = overlay.getElementById("status");
    if (status != null) {
        status.setTextContent("已连接");
    }
}
~~~

`getCurrentScreenDocument()` 只识别 Java 客户端当前的 `ApricityScreen`。由 `ApricityUI.screen(path)` 或 `ApricityUI.menu(...).bind(...)` 打开的页面通常是 `ApricityContainerScreen`，因此这个方法对容器页面通常返回 `null`。容器页面应通过相应 Screen 的 `getLinkedDocument()` 在 Java 中访问，或在页面自己的脚本中使用全局 `document`。

### 2.3 客户端 Toast API

~~~javascript
var id1 = ApricityUI.toast("加载完成");
var id2 = ApricityUI.toast("保存失败", 5000);

ApricityUI.dismissToast(id1);
ApricityUI.clearToasts();
~~~

完整重载：

~~~javascript
var id = ApricityUI.toast(
    "资源已更新",       // message
    4200,                // durationMs；0 表示不自动关闭
    "#20242b",           // backgroundColor
    "#ffffff",           // textColor
    "#6fb4d6",           // borderColor
    true,                // dismissOnClick
    "font-size: 14px;"   // customStyle
);
~~~

方法签名：

~~~text
toast(message) -> String
toast(message, durationMs) -> String
toast(message, durationMs, backgroundColor, textColor, borderColor, dismissOnClick, customStyle) -> String
dismissToast(id)
clearToasts()
~~~

返回的字符串是 Toast ID，交给 `dismissToast` 使用，不要把它当作页面元素 ID。

### 2.4 客户端 Screen API

~~~javascript
ApricityUI.screen("screens/settings.html");
ApricityUI.closeScreen();
~~~

`screen(path)` 会从客户端向服务端发起打开请求，服务端最终创建的是 `ApricityContainerScreen` 的 UI-only 形式，不是客户端直接 `new ApricityScreen(path)` 的普通 Screen。页面没有容器声明时，它不会获得真实 Minecraft 物品槽。

旧接口仍存在但已废弃：

~~~javascript
ApricityUI.openScreen("screens/settings.html"); // deprecated
~~~

新代码使用 `screen(path)`。

### 2.5 客户端 WorldWindow API

常用重载如下：

~~~javascript
var worldWindow = ApricityUI.createWorldWindow(
    "world/notice.html",
    10.5, 64.0, -3.5,
    64
);

worldWindow.setFacing(true);
worldWindow.setFollow(true);
worldWindow.setFollowFactor(0.35);
worldWindow.setDepthTest(true);
worldWindow.setMaxDisplayDistance(96);
worldWindow.document.getElementById("title").setTextContent("基地");
~~~

当前绑定提供的创建方法：

~~~text
createWorldWindow(path, x, y, z, maxDistance)
createWorldWindow(path, x, y, z, maxDistance, maxDisplayDistance)
createWorldWindow(path, x, y, z, maxDistance, yaw, pitch)
createWorldWindow(path, x, y, z, maxDistance, yaw, pitch, roll)
~~~

角度单位是度。`maxDistance` 是准心射线可交互的最大距离；`maxDisplayDistance` 是相机距离超过后不再显示和交互的限制。

旧的显式宽高重载和 `createFollowFacingWorldWindow(...)` 已废弃。新代码创建普通 WorldWindow 后使用：

~~~javascript
worldWindow.setFollow(true);
worldWindow.setFacing(true);
~~~

清理窗口：

~~~javascript
ApricityUI.removeWorldWindow(worldWindow);
ApricityUI.clearWorldWindows();
~~~

创建方法会自动注册窗口；移除方法也会移除其 Document。不要在已经移除的窗口上继续修改 DOM。

### 2.6 KJS 服务端容器 API

推荐入口是 `menu(...).bind(...)`：

~~~javascript
ApricityUI.menu(player, "screens/inventory.html")
    .bind(function (binding) {
        binding
            .saveddata("machine_data", 9)
            .player();
    });
~~~

`bind` 回调接收 `BindingBuilder`，配置完成后立即向该玩家打开页面。

| 方法 | 绑定的 HTML 容器 ID | 说明 |
| --- | --- | --- |
| `player()` | `player` | 玩家背包，36 格 |
| `saveddata()` | `saved_data` | 默认数据名 `apricityui_data`，默认 9 格 |
| `saveddata(dataName)` | `saved_data` | 自定义 SavedData 名称，默认 9 格 |
| `saveddata(dataName, capacity)` | `saved_data` | 自定义数据名和容量 |
| `blockEntity(pos)` | `block_entity` | 根据方块坐标查找方块实体容量 |
| `blockEntity(pos, capacity)` | `block_entity` | 显式指定容量 |
| `entity(entityId)` | `entity` | 根据实体 ID 绑定实体容器 |
| `entity(entityId, capacity)` | `entity` | 显式指定容量 |

模板中的容器 ID 必须和表格中的绑定 ID 对应。`saveddata("machine_data")` 中的 `machine_data` 是服务端数据名，不是 HTML 的 `id`。

一个包含方块实体和玩家背包的例子：

~~~javascript
ApricityUI.menu(player, "screens/machine.html")
    .bind(function (binding) {
        binding
            .blockEntity(pos)
            .player();
    });
~~~

HTML 侧至少应有对应容器：

~~~html
<container id="block_entity" bind="block_entity"></container>
<container id="player" bind="player"></container>
~~~

容器完整的 HTML 属性、槽位布局、物品交互和服务端数据源见 [Apricity 容器文档](container.md)。

旧接口：

~~~javascript
ApricityUI.openScreen(player, "screens/inventory.html");
ApricityUI.openScreen(player, "screens/inventory.html", declarations);
~~~

这两个接口已废弃。新的服务端代码应使用 `menu(player, path).bind(...)`，这样绑定参数和数据源解析会由统一的构建器处理。

## 3. Java API

### 3.1 统一入口 ApricityUI

Java 侧的总入口是：

~~~java
import com.sighs.apricityui.ApricityUI;
~~~

#### Document 和窗口入口

~~~java
Window getWindow()
Document createDocument(String path)
Document createInWorldDocument(String path)
void removeDocument(String path)
ArrayList<Document> getDocument(String path)
Document getDocumentByUUID(String uuid)
List<Document> getAllDocument()
~~~

`getDocument(path)` 可能返回多个实例；`removeDocument(path)` 会删除同路径的全部实例。

#### Screen 和菜单入口

~~~java
void screen(String path)
PendingMenu menu(ServerPlayer player, String path)
void closeScreen()
~~~

`screen` 和 `closeScreen` 是客户端请求入口，`menu` 是服务端打开容器入口。`openScreen` 保留为兼容方法但已废弃。

#### WorldWindow 入口

推荐重载：

~~~java
WorldWindow createWorldWindow(String path, Vec3 position, int maxDistance)
WorldWindow createWorldWindow(String path, Vec3 position,
                              int maxDistance, int maxDisplayDistance)
WorldWindow createWorldWindow(String path, double x, double y, double z,
                              int maxDistance)
WorldWindow createWorldWindow(String path, double x, double y, double z,
                              int maxDistance, int maxDisplayDistance)
WorldWindow createWorldWindow(String path, Vec3 position,
                              int maxDistance, float yaw, float pitch)
WorldWindow createWorldWindow(String path, Vec3 position,
                              int maxDistance, float yaw, float pitch, float roll)
WorldWindow createWorldWindow(String path, Vec3 position,
                              int maxDistance, Vec3 eulerDegrees)
WorldWindow createWorldWindow(String path, Vec3 position,
                              int maxDistance, Quaternionf orientation)
~~~

管理方法：

~~~java
void removeWorldWindow(WorldWindow window)
void clearWorldWindows()
~~~

`ApricityUI.createWorldWindow(...)` 创建后会自动调用 `WorldWindow.addWindow(...)`。如果使用 `new WorldWindow(...)`，则必须自己调用 `WorldWindow.addWindow(window)`。

显式 `width`/`height` 的创建重载以及 `createFollowFacingWorldWindow(...)` 已废弃。视口尺寸应写入页面的 `aui-viewport` meta，再通过普通 WorldWindow 的 `setFollow`、`setFacing` 配置跟随和朝向。

Java 示例：

~~~java
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.WorldWindow;
import net.minecraft.world.phys.Vec3;

WorldWindow info = ApricityUI.createWorldWindow(
        "world/notice.html",
        new Vec3(10.5, 64.0, -3.5),
        64,
        96
);
info.setFacing(true);
info.setFollow(true);
info.setFollowFactor(0.35F);
info.document.getElementById("title").setTextContent("基地");
~~~

### 3.2 Document 生命周期

源码类：`com.sighs.apricityui.init.Document`。

#### 创建和查询

~~~java
Document document = Document.create("overlays/status.html");
Document worldDocument = Document.createInWorld("world/notice.html");

ArrayList<Document> samePath = Document.get("overlays/status.html");
Document byUuid = Document.getByUUID(document.getUuid().toString());
List<Document> all = Document.getAll();
~~~

也可以通过 `ApricityUI.createDocument` 等统一入口调用同样的能力。创建失败时返回 `null`，通常原因是模板资源不存在或无法读取。

#### 生命周期方法

| 方法 | 行为 |
| --- | --- |
| `refresh()` | 重新读取 HTML、CSS 和脚本并重建 DOM、样式和绘制列表 |
| `Document.refreshAll()` | 刷新全部未设置 reload-persistent 的活动文档 |
| `remove()` | 移除当前实例并释放其生命周期状态 |
| `Document.remove(path)` | 移除同路径的全部实例 |
| `Document.remove(uuid)` | 按 UUID 移除单个实例 |
| `setReloadPersistent(boolean)` | 控制全局资源重载时是否保留该文档 |
| `isActive()` | 判断文档是否仍可交互 |
| `isDisposed()` | 判断文档是否已经销毁 |
| `getRefreshGeneration()` | 获取当前 DOM 刷新代数 |
| `isCurrentGeneration(generation)` | 判断异步回调是否仍属于当前 DOM 代数 |

刷新会保留 Document 对象和 UUID，但会重建内部 DOM。刷新前保存的旧 `Element` 引用不能在刷新后继续使用；异步回调应保存刷新代数并在写入前检查：

~~~java
long generation = document.getRefreshGeneration();

// 异步操作完成后回到客户端线程，再检查代数
if (document.isActive() && document.isCurrentGeneration(generation)) {
    Element status = document.getElementById("status");
    if (status != null) {
        status.setTextContent("完成");
    }
}
~~~

#### DOM 操作

`Document` 暴露了页面运行时需要的 DOM 创建和查询入口：

~~~java
Element byId = document.getElementById("status");
Element first = document.querySelector(".panel");
List<Element> panels = document.querySelectorAll(".panel");

Element button = document.createElement("button");
button.setTextContent("确定");
button.setAttribute("type", "button");
document.body.appendChild(button);

TextNode text = document.createTextNode("文本");
CommentNode comment = document.createComment("debug");
DocumentFragment fragment = document.createDocumentFragment();
~~~

常用方法还包括 `getElementsByClassName`、`getElementsByTagName`、`getElementsByName`、`createHTML`、`appendChild`、`prepend` 和 `removeElement`。具体元素属性、样式、事件和选择器能力见 [Web API 文档](web-api.md)。

#### 渲染和命中测试

高级渲染宿主可以使用：

~~~java
document.setManuallyRendered(true);
ArrayList<RenderNode> paintList = document.getPaintList();
Element hit = document.hitTest(new Position(mouseX, mouseY));
document.markDirty(Drawer.RELAYOUT | Drawer.REPAINT);
document.markDirty(element, Drawer.REPAINT | Drawer.REORDER);
~~~

`setManuallyRendered(true)` 表示由拥有者负责把文档绘制到目标表面，避免它被全局文档绘制流程重复绘制。WorldWindow 和其他自定义渲染宿主应明确管理这个状态。

#### 坐标和鼠标拦截

~~~java
Position documentPosition = document.screenToDocumentPosition(screenPosition);
Position screenPosition = document.documentToScreenPosition(documentPosition);

boolean intercept = document.interceptsMouseEvents();
boolean hit = document.interceptsMouseEventsAt(screenPosition);
double scaleX = document.getViewportScaleX();
double scaleY = document.getViewportScaleY();
~~~

页面可以通过下面的 meta 控制 Overlay 是否拦截鼠标：

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

缺省值是不拦截。`block`、`true`、`yes`、`on` 和 `1` 也会被解析为拦截值；其他值按不拦截处理。

#### 视口和缩放

~~~java
ApricityViewport viewport = document.getViewport();
boolean changed = document.handleViewportZoom(true);
boolean reset = document.resetViewportZoom();
boolean editorChanged = document.setViewportZoom(1.25D);
~~~

`handleViewportZoom` 会尊重页面的 `user-scalable` 配置；`setViewportZoom` 是编辑器/DevTools 使用的直接设置入口，可以设置不依赖用户滚轮权限的缩放值。页面的 `aui-viewport` 语法见 [浏览器辅助功能文档](browser-features.md) 和 [WorldWindow 文档](world-window.md)。

### 3.3 ApricityScreen

源码类：`com.sighs.apricityui.instance.ApricityScreen`。

`ApricityScreen` 是客户端直接创建的普通 Minecraft Screen，不绑定真实 Minecraft 容器：

~~~java
import com.sighs.apricityui.instance.ApricityScreen;
import net.minecraft.client.Minecraft;

ApricityScreen screen = new ApricityScreen("screens/settings.html")
        .setPauseGame(true)
        .setShowDefaultBackground(false);
Minecraft.getInstance().setScreen(screen);
~~~

公开方法：

~~~java
ApricityScreen setPauseGame(boolean pauseGame)
ApricityScreen setShowDefaultBackground(boolean showDefaultBackground)
boolean isPauseGame()
boolean isShowDefaultBackground()
Document getLinkedDocument()
boolean handleViewportZoom(boolean zoomIn)
boolean resetViewportZoom()
~~~

页面在 Screen 初始化时创建并绑定 Document，在 Screen 关闭或移除时销毁。`getLinkedDocument()` 在初始化前可能为 `null`，资源缺失时也可能为 `null`。

不要把 `ApricityUI.screen(path)` 和 `new ApricityScreen(path)` 混为一谈：前者是网络请求并由服务端打开 `ApricityContainerScreen`，后者是客户端直接设置普通 Screen。

### 3.4 ApricityContainerScreen

源码类：`com.sighs.apricityui.instance.ApricityContainerScreen`。

这个 Screen 由 `ApricityUI.menu(...).bind(...)` 的网络流程创建，继承 Minecraft 的 `AbstractContainerScreen`，同时绘制 HTML Document 和真实菜单槽位。

主要公开方法：

~~~java
Document getLinkedDocument()
int getGuiLeft()
int getGuiTop()
int findSlotIndexAt(double mouseX, double mouseY)
boolean isSlotPointerInteractable(net.minecraft.world.inventory.Slot slot)
boolean handleViewportZoom(boolean zoomIn)
boolean resetViewportZoom()
~~~

业务代码通常不需要直接构造它。推荐通过 `PendingMenu` 创建真实容器；如果需要从网络层或自定义服务端逻辑直接控制，可以调用 `ApricityScreenNetworkHandler.openScreen(...)`。

### 3.5 容器绑定 API

#### 推荐入口：PendingMenu 和 BindingBuilder

~~~java
ApricityUI.menu(player, "screens/machine.html")
        .bind(binding -> binding
                .blockEntity(pos)
                .player());
~~~

`BindingBuilder` 的链式方法：

~~~java
BindingBuilder player()
BindingBuilder saveddata()
BindingBuilder saveddata(String dataName)
BindingBuilder saveddata(String dataName, int capacity)
BindingBuilder blockEntity(BlockPos pos)
BindingBuilder blockEntity(BlockPos pos, int capacity)
BindingBuilder entity(int entityId)
BindingBuilder entity(int entityId, int capacity)
~~~

每次绑定都会生成固定的容器 ID：`player`、`saved_data`、`block_entity` 或 `entity`。第一个自定义数据容器通常会成为主容器；主容器用于确定菜单的主要槽位区域。

#### 高级入口：ApricityScreenNetworkHandler

需要动态生成声明或自己维护参数时，可以直接使用：

~~~java
ApricityScreenNetworkHandler.openScreen(
        player,
        "screens/machine.html",
        declarations,
        argsById
);
~~~

`argsById` 的参数名称由数据源约定：

| 绑定类型 | 参数 |
| --- | --- |
| `saved_data` | `data_name` |
| `block_entity` | `x`、`y`、`z` |
| `entity` | `entity_id` |
| `player` | 不需要额外参数 |

网络处理器会在服务端解析数据源，不能把未验证的客户端输入直接当成方块、实体或数据存储的权限依据。完整容器声明和槽位行为见 [容器文档](container.md)。

### 3.6 WorldWindow

源码类：`com.sighs.apricityui.instance.WorldWindow`。

普通构造器：

~~~java
new WorldWindow(String path, Vec3 position, int maxDistance)
new WorldWindow(String path, double x, double y, double z, int maxDistance)
new WorldWindow(String path, Vec3 position, int maxDistance, float yaw, float pitch)
new WorldWindow(String path, Vec3 position, int maxDistance,
                float yaw, float pitch, float roll)
new WorldWindow(String path, Vec3 position, int maxDistance, Vec3 eulerDegrees)
new WorldWindow(String path, Vec3 position, int maxDistance, Quaternionf orientation)
~~~

直接构造的窗口不会自动加入全局列表：

~~~java
WorldWindow window = new WorldWindow(path, position, 64);
WorldWindow.addWindow(window);
// 不再使用时：
WorldWindow.removeWindow(window);
~~~

更推荐使用 `ApricityUI.createWorldWindow(...)`，因为它会自动注册。

#### 位置、朝向和跟随

~~~java
window.setPosition(new Vec3(x, y, z));
window.setRotation(90.0F, 0.0F, 0.0F); // yaw, pitch, roll，单位为度
window.setRotation(new Vec3(pitch, yaw, roll));
window.setOrientation(quaternion);

window.setFollow(true);
window.setFacing(true);
window.setFollowFactor(0.3F);
~~~

注意两个 `setRotation` 重载的参数顺序不同：浮点参数是 `(yaw, pitch, roll)`，`Vec3` 重载按源码约定是 `(pitch, yaw, roll)`。

#### 尺寸、距离和遮挡

~~~java
window.setScale(0.02F);
window.clearScaleOverride();
window.setDepthTest(true);
window.setMaxDistance(64);
window.setMaxDisplayDistance(128);
window.clearMaxDisplayDistanceOverride();
~~~

新窗口默认根据 Document 的 `aui-viewport` 和相机自动适配尺寸。`setScale` 是兼容旧行为的显式世界缩放；调用 `clearScaleOverride` 后恢复自动适配。

#### 显示精度和动态深度

~~~java
window.setDisplayPrecision(WorldWindowDisplayPrecision.FULL);
window.setDisplayPrecision("reduced");
window.setDisplayPrecisionDistances(16, 48);
WorldWindowDisplayPrecision effective = window.getEffectiveDisplayPrecision();

window.setDynamicDepthStep(0.00035F, 0.003F, 2.0F, 64.0F);
~~~

`WorldWindowDisplayPrecision` 支持 `AUTO`、`FULL`、`REDUCED` 和 `MINIMAL`。`AUTO` 使用客户端配置或实例距离阈值；显式精度会覆盖全局策略。

#### 投影、命中和关联查询

~~~java
Position documentPosition = window.getDocumentPositionAtScreen(screenPosition);
Position screenPosition = window.projectDocumentPosition(documentPosition);
WorldWindow.ScreenRect rect = window.projectDocumentRect(x, y, width, height);

Position pointer = window.getRealPos();
WorldWindow owner = WorldWindow.findByDocument(document);
~~~

这些方法依赖窗口最近一次世界渲染保存的投影矩阵。没有完成世界渲染、窗口不在可显示距离内、被方块遮挡或超出 `maxDistance` 时，命中/投影可能返回 `null`。

`window.document` 是公开字段，可以直接访问页面 DOM。窗口销毁时应先停止使用该字段，再移除窗口，因为 `removeWindow` 同时会移除关联 Document。

`FollowFacingWorldWindow` 仍用于兼容旧代码，但新代码应使用普通 `WorldWindow` 加 `setFollow(true)` 和 `setFacing(true)`。

### 3.7 视口 API：ApricityViewport

页面可以通过 meta 声明逻辑视口：

~~~html
<meta name="aui-viewport"
      content="mode=fixed,width=240,height=96,scale=fit,
               zoom=1,min-zoom=0.5,max-zoom=3,zoom-step=0.1,
               user-scalable=true">
~~~

Java 侧可以读取和解析它：

~~~java
ApricityViewport viewport = ApricityViewport.resolve(path, minecraft.getWindow());
ApricityViewport.Spec spec = ApricityViewport.spec(path);
ApricityViewport.State state = spec.createState(path);

boolean zoomed = state.zoomIn();
double zoom = state.zoom();
boolean changed = state.setZoom(1.25D);
~~~

主要模式：

| 模式 | 行为 |
| --- | --- |
| `gui` | 使用 Minecraft GUI 缩放后的逻辑尺寸 |
| `browser` / `css` / `web` | 固定 CSS 宽度并适配当前窗口 |
| `window` / `native` / `screen` / `fullscreen` | 浏览器式窗口视口 |
| `fixed` | 使用显式 `width`、`height` 和 `scale` |

同一路径的缩放状态会被保存并在后续创建时恢复。`user-scalable=false` 会阻止 `zoomIn`、`zoomOut` 和 `resetZoom`，但不会阻止 DevTools 使用 `Document.setViewportZoom` 或 `State.setZoom`。

### 3.8 资源加载 API

#### Loader

~~~java
InputStream Loader.getResourceStream(String path)
boolean Loader.isRemotePath(String path)
String Loader.resolve(String context, String raw)
List<Path> Loader.getWatchRoots()
Path Loader.getGameDirectory()
~~~

`Loader.resolve` 用于根据当前 HTML 路径解析相对引用：

~~~java
String resolved = Loader.resolve("screens/settings.html", "../styles/common.css");
// styles/common.css
~~~

`isRemotePath` 当前识别 `https://` 路径。远程资源能否真正读取还取决于对应的异步网络管线和安全限制；普通页面资源应优先使用模组逻辑路径。

#### ClientLoader

客户端资源管理和开发工具可使用：

~~~java
ClientLoader.reload();
List<Loader.StaticResourceEntry> resources = ClientLoader.listFinalStaticResources();
ClientLoader.invalidateStaticResourceCache();
String globalCss = ClientLoader.readGlobalCSS();
~~~

`ClientLoader.reload()` 会在客户端调度完整资源和 Document 重载，不应在每帧调用。它会影响现有 Document、WorldWindow、DevTools 和资源管理器。

#### HTML

~~~java
HTML.scan();
boolean reloaded = HTML.reload(path);
String source = HTML.getTemple(path);
String meta = HTML.findMetaContent(path, "aui-viewport");
HTML.DocumentRoot root = HTML.create(document, path);
Element element = HTML.createElement(document, "<div>内容</div>");
~~~

`getTemple` 是当前源码中已经存在的拼写形式，普通业务代码不建议直接依赖它；读取资源优先使用 `Loader` 或 `Document.create`。`HTML.create` 适用于需要自己控制 HTML 解析阶段的高级宿主，`HTML.createElement` 的参数是 HTML 片段而不是单独的标签名。

### 3.9 AUI 专属 DOM 元素

ApricityUI 会通过 `@ElementRegister` 扫描并注册扩展元素。当前常用的模组专属标签包括：

| 标签 | Java 类型 | 用途 |
| --- | --- | --- |
| `<container>` | `com.sighs.apricityui.instance.element.Container` | 声明 Minecraft 容器区域 |
| `<slot>` | `com.sighs.apricityui.instance.element.Slot` | 显示或绑定物品槽 |
| `<recipe>` | `Recipe` | 配方/物品展示 |
| `<translation>` | `Translation` | 使用 Minecraft 翻译 key 的文本 |
| `<texture>` | `Texture` | Minecraft 纹理 |
| `<sprite>` | `Sprite` | Sprite 图集资源 |
| `<canvas>` | `Canvas` | AUI Canvas 绘制上下文 |
| `<svg>` / `<path>` | `Svg` / `Path` | AUI SVG 绘制 |

示例：

~~~html
<container id="player" bind="player">
    <slot index="0"></slot>
</container>
<translation>container.apricityui.title</translation>
<texture src="minecraft:textures/item/diamond.png"></texture>
~~~

这些元素仍然是 ApricityUI DOM 的一部分，可以使用 `querySelector`、事件和 CSS；它们的 Minecraft 特有属性和数据行为见 [容器文档](container.md) 与 [UI 库文档](ui-library.md)。

### 3.10 内置 Java UI 组件

`com.sighs.apricityui.ui` 包提供了可复用的 Java UI 组件：

| 类 | 主要入口 |
| --- | --- |
| `DialogWindow` | `open(...)` |
| `ContextMenu` | `show(...)`、`closeActive()` |
| `ToastManager` | `show(...)`、`dismiss(...)`、`clear()` |
| `Tooltip` | `show(...)`、`bind(...)`、`hide()` |
| `ColorPicker` | `pick(...)`、`pickIn(...)` |
| `FilePicker` | `pick(...)`、`pickIn(...)` |
| `UiTranslations` | `translate(key)` |

这些组件需要客户端线程和有效的 Document 宿主；完整参数、Future 返回值和单实例规则见 [内置 UI 库文档](ui-library.md)。

### 3.11 客户端配置 API

配置入口是 `ApricityUIConfig.CLIENT`。配置文件由 Forge 管理，Java 侧可读取以下配置：

~~~java
ApricityUIConfig.CLIENT.debugAutoReload.get();
ApricityUIConfig.CLIENT.viewportZoomPassThrough.get();
ApricityUIConfig.CLIENT.worldWindowMaxDisplayDistance();
ApricityUIConfig.CLIENT.worldWindowLodEnabled();
ApricityUIConfig.CLIENT.worldWindowFullDetailDistance();
ApricityUIConfig.CLIENT.worldWindowReducedDetailDistance();
~~~

主要配置项：

| 配置键 | 作用 |
| --- | --- |
| `debug.autoReload` | 开发目录变化时自动重载 |
| `debug.aiAutoScreenshot` | AI 辅助截图 |
| `debug.frameTimingHud` | 显示帧耗时监视器 |
| `debug.remoteDebug` | 启用本地外部调试器 |
| `debug.resourceManagerWorldWindow` | 在世界内显示资源管理器 |
| `input.viewportZoomPassThrough` | Ctrl+滚轮缩放是否穿透不拦截鼠标的持久 Overlay |
| `worldWindow.depthOffsetScale` | 世界窗口深度偏移比例 |
| `worldWindow.maxDisplayDistance` | 世界窗口默认显示/交互距离 |
| `worldWindow.lodEnabled` | 是否启用世界窗口距离 LOD |
| `worldWindow.fullDetailDistance` | 完整精度距离 |
| `worldWindow.reducedDetailDistance` | 降低精度距离 |

## 4. 常用完整示例

### 4.1 KJS 客户端 Overlay

~~~javascript
var overlay = ApricityUI.createDocument("overlays/quest.html");
if (overlay == null) {
    console.error("Quest overlay resource is missing");
} else {
    overlay.setReloadPersistent(true);

    var title = overlay.getElementById("title");
    if (title != null) {
        title.setTextContent("任务追踪");
    }
}
~~~

### 4.2 KJS 客户端 Toast 和当前普通 Screen

~~~javascript
var toastId = ApricityUI.toast("设置已保存", 3000);
var current = ApricityUI.getCurrentScreenDocument();
if (current != null) {
    var message = current.getElementById("message");
    if (message != null) {
        message.setTextContent("当前页面已更新");
    }
}
~~~

此示例中的 `current` 在 `ApricityContainerScreen` 上通常为 `null`，这是当前 API 的类型边界，不是获取失败。

### 4.3 KJS 服务端真实容器

~~~javascript
ApricityUI.menu(player, "screens/machine.html")
    .bind(function (binding) {
        binding.blockEntity(pos).player();
    });
~~~

KubeJS 事件和命令的注册方式取决于所使用的 KubeJS 版本；`ApricityUI.menu(...).bind(...)` 的绑定部分保持不变。

### 4.4 Java 客户端普通 Screen

~~~java
public static void openSettings() {
    Minecraft.getInstance().setScreen(
            new ApricityScreen("screens/settings.html")
                    .setPauseGame(false)
                    .setShowDefaultBackground(false)
    );
}
~~~

### 4.5 Java 动态更新 Document

~~~java
Document document = ApricityUI.createDocument("overlays/status.html");
if (document != null) {
    document.setReloadPersistent(true);
    Document.runWithContext(document, () -> {
        Element status = document.getElementById("status");
        if (status != null) {
            status.setTextContent("服务器在线");
            status.setAttribute("data-state", "ready");
        }
    });
}
~~~

`runWithContext` 会临时设置当前 Document 上下文，执行完毕后恢复之前的上下文，适合会触发脚本/DOM 辅助逻辑的 Java 调用。

### 4.6 Java 服务端容器

~~~java
public static void openMachine(ServerPlayer player, BlockPos pos) {
    ApricityUI.menu(player, "screens/machine.html")
            .bind(binding -> binding
                    .blockEntity(pos)
                    .player());
}
~~~

### 4.7 Java 世界内窗口

~~~java
public static WorldWindow createNotice(Vec3 position) {
    WorldWindow window = ApricityUI.createWorldWindow(
            "world/notice.html", position, 64, 96);
    window.setFacing(true);
    window.setFollow(true);
    window.setFollowFactor(0.25F);
    return window;
}

public static void removeNotice(WorldWindow window) {
    ApricityUI.removeWorldWindow(window);
}
~~~

## 5. 线程、刷新和错误处理

### 5.1 客户端线程

创建 Document、修改 DOM、打开/关闭 Screen、操作 WorldWindow 和使用 UI 组件都应在 Minecraft 客户端线程执行：

~~~java
Minecraft.getInstance().execute(() -> {
    Document document = ApricityUI.createDocument("overlays/status.html");
    if (document != null) {
        Element status = document.getElementById("status");
        if (status != null) status.setTextContent("完成");
    }
});
~~~

网络回调、Future 完成回调和文件监视器回调不应直接修改客户端 DOM。服务端 `menu` 调用则应在服务端逻辑线程执行。

### 5.2 资源缺失和异常

应处理这些可观察结果：

- `Document.create` / `createInWorld` 返回 `null`：模板缺失或不可用；
- `getElementById`、`querySelector` 返回 `null`：元素不存在，或刷新后引用已失效；
- `getDocumentByUUID` / `getCurrentScreenDocument` 返回 `null`：文档已销毁、Screen 类型不匹配或尚未初始化；
- WorldWindow 的投影和命中方法返回 `null`：窗口不可见、未完成投影捕获、被遮挡或超出距离；
- 容器绑定无法解析：服务端会拒绝打开菜单并写入 AUI 日志；
- HTML、CSS 或 JavaScript 解析异常：刷新流程会记录带路径和阶段的错误日志，当前 Document 可能处于不完整状态。

不要用捕获异常来代替空值检查，也不要在资源缺失时继续访问 `document.body`。

### 5.3 刷新代价和引用失效

`refresh()` 是完整模板刷新，会重新解析 HTML、CSS、脚本、扩展元素和绘制列表。它不是只更新一条文字的轻量操作。对于频繁变化的数据，优先修改已有元素的 `textContent`、属性或样式，并让框架处理脏标记。

页面重载后旧的 `Element`、`TextNode`、事件监听器和 MutationObserver 都不应继续使用。异步任务必须使用 `getRefreshGeneration()` / `isCurrentGeneration(...)` 防止旧回调写入新 DOM。

### 5.4 API 的稳定性边界

以下 API 主要面向框架扩展、DevTools 或自定义渲染宿主：

- `Document` 的渲染队列、样式提交和脏标记方法；
- `HTML.create`、`HTML.getTemple` 和 Loader 的开发目录扫描方法；
- `ApricityScreenNetworkHandler` 的声明和参数映射入口；
- `WorldWindow` 的投影矩阵、深度步进和显示精度方法；
- `ApricityUIRegistry` 的反射注册机制。

它们是当前源码提供的公开 Java 方法，但不应假定它们与标准浏览器或未来版本完全兼容。业务页面优先使用 `Document`/`Element` 的稳定 DOM 方法，业务容器优先使用 `PendingMenu`，世界窗口优先使用 `ApricityUI.createWorldWindow`。

## 6. 源码和相关文档

主要源码入口：

- [ApricityUI.java](../src/main/java/com/sighs/apricityui/ApricityUI.java)
- [ApricityUIClientUtil.java](../src/main/java/com/sighs/apricityui/util/kjs/ApricityUIClientUtil.java)
- [ApricityUIServerUtil.java](../src/main/java/com/sighs/apricityui/util/kjs/ApricityUIServerUtil.java)
- [Document.java](../src/main/java/com/sighs/apricityui/init/Document.java)
- [ApricityScreen.java](../src/main/java/com/sighs/apricityui/instance/ApricityScreen.java)
- [ApricityContainerScreen.java](../src/main/java/com/sighs/apricityui/instance/ApricityContainerScreen.java)
- [WorldWindow.java](../src/main/java/com/sighs/apricityui/instance/WorldWindow.java)
- [ApricityViewport.java](../src/main/java/com/sighs/apricityui/instance/ApricityViewport.java)
- [BindingBuilder.java](../src/main/java/com/sighs/apricityui/instance/network/handler/BindingBuilder.java)

相关专题：

- [Web API](web-api.md)
- [ApricityScreen](apricity-screen.md)
- [Overlay Document](overlay-document.md)
- [Apricity 容器](container.md)
- [WorldWindow](world-window.md)
- [资源管理和资源管理器](resource-manager.md)
- [内置 UI 库](ui-library.md)
- [扩展元素](extension-elements.md)
- [二次开发](secondary-development.md)
- [浏览器辅助功能](browser-features.md)
