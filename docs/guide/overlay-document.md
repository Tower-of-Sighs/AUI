# Overlay Document 使用文档

Overlay 是不依附于任何 Minecraft Screen 的 Document：`ApricityUI.createDocument(path)` 创建后加入全局列表，由客户端在 GUI/HUD 绘制阶段自动渲染。适合做 HUD、Toast、提示条、浮动面板、全屏遮罩、开发工具。不打开新 Screen，也没有容器槽位——要槽位请走[容器文档](container)。

## 和 ApricityScreen 的区别

| | Overlay | ApricityScreen |
| --- | --- | --- |
| 创建 | `ApricityUI.createDocument(path)` | `new ApricityScreen(path)` |
| 替换当前 Screen | 否 | 是 |
| 游戏内无 Screen 时 | 显示 | — |
| 打开 Screen 后 | 普通 Overlay 隐藏；持久化的继续显示 | 作为当前 Screen 绘制 |
| 典型用途 | HUD、Toast、常驻工具条 | 设置页、全屏 UI |

## 最小示例

`overlays/status.html`：

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
</head>
<body>
    <div id="status">Loading...</div>
</body>
</html>
```

Java 侧管理：

```java
private static Document document;

public static void open() {
    if (document != null && document.isActive()) return;
    document = ApricityUI.createDocument("overlays/status.html");
    if (document == null) return;
    Element status = document.getElementById("status");
    if (status != null) status.setTextContent("Ready");
}

public static void close() {
    if (document == null) return;
    document.remove();   // 光把变量置 null 不会移除 Document
    document = null;
}
```

创建和修改必须在客户端线程；网络回调里先 `Minecraft.getInstance().execute(...)`。KubeJS 客户端脚本里是同一组 API（`ApricityUI.createDocument(...)`），页面自己的脚本则直接用页面内的 `document`。

## API

```java
Document ApricityUI.createDocument(String path)        // 资源不存在返回 null
void     ApricityUI.removeDocument(String path)        // 移除该路径的全部实例
ArrayList<Document> ApricityUI.getDocument(String path)
Document ApricityUI.getDocumentByUUID(String uuid)
List<Document> ApricityUI.getAllDocument()
```

同一路径可以建多个实例，所以 `getDocument` 返回列表、`removeDocument(path)` 会一刀切。只想关自己那个，就保存返回值调 `document.remove()`。

Document 常用方法：`getPath()`、`getUuid()`、`isActive()`、`isDisposed()`、`getRefreshGeneration()`、`getElementById()`、`querySelector(All)`、`remove()`、`refresh()`、`setReloadPersistent(boolean)`。

## 显示时机与持久化

| 当前状态 | 普通 Overlay | `reloadPersistent=true` |
| --- | --- | --- |
| 游戏内，无 Screen | 显示 | 显示 |
| 打开原版 Screen | 隐藏 | 显示 |
| 打开 ApricityScreen / 容器 Screen | 隐藏 | 由该 Screen 代为绘制 |

```java
overlay.setReloadPersistent(true);   // Toast、全局通知这类要一直显示
```

持久化做两件事：打开 Screen 时继续绘制；END 重载时跳过全量刷新。它**不是**永生——`remove()` 照样销毁；也**不是**缓存开关——END 之后资源文件的修改不会自动应用到它身上，要手动 `refresh()` 或重建。

还有个 `setManuallyRendered(true)`：把 Document 移出全局绘制和输入分发，由调用方自己画。这是给自定义渲染宿主（预览窗口之类）用的，普通 Overlay 别碰，否则页面创建成功但永远不会出现。

## 生命周期要点

- **创建即解析**：`createDocument` 立即解析 HTML/CSS/JS、算布局、跑脚本、派发 DOMContentLoaded/load。开销不小，打开时建一次，别每帧建。
- **修改**：改 DOM 后框架走增量更新。批量修改尽量一次做完，别拆成很多帧。
- **移除**：`document.remove()` 清理焦点、hover、Observer 等状态。和 Screen 不同，它**不会**向 body 派发 unload——有清理逻辑就自己显式调。
- **重载**：END 会 refresh 所有普通 Overlay，DOM 和 JS 状态重建，旧 Element 引用失效。要保留的状态放 Java/KubeJS 侧，`load` 里写回。

## 层级：Document 之间和之内

**同一个 Document 内**就是普通 CSS：`position` + `z-index`。但 z-index 管不了跨 Document 的层级。

**不同 Document 之间**按根节点/body 的 `translateZ` 排序，数值大的靠前：

```css
body { transform: translateZ(100px); }
```

只在 html 或 body 上设一次（别两个都设），不同职责的 Overlay 规划好互不冲突的层级范围。相同 translateZ 时按创建顺序，后建的在前——别依赖这个。

**Document 内的 top layer**：弹窗、下拉菜单这类要跳出父容器 overflow 裁剪的元素，Java 侧标一下：

```java
dialog.setTopLayer(true);
```

它保留原来的 DOM 关系和事件路径，只是在普通内容之后绘制、不被祖先裁剪。只影响本 Document 内部。

## 输入与穿透

输入按从前到后的顺序分发给各 Document，没命中或没消费就往下传。事件坐标是逻辑坐标，别乘缩放。

两个常用模式：

**模态遮罩**——meta 开拦截（见 [ApricityScreen 的 meta 章节](apricity-screen#页面-meta-配置)），遮罩盖满视口：

```html
<meta name="aui-mouse-events" content="intercept">
```

```css
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.55); }
```

**信息展示层**——容器穿透、内容可点，Toast 的标准结构：

```css
.toast-layer { pointer-events: none; }
.toast { pointer-events: auto; }
```

Ctrl+滚轮的缩放目标按最上层命中 Document 选；`config/apricityui-client.toml` 里 `[input] viewportZoomPassThrough = true` 时，没声明 intercept 的持久化 Overlay 会被缩放逻辑跳过。

## 常见模式

**HUD**：普通 Overlay，不开持久化。打开背包自动隐藏，关掉自动回来。

**Toast/全局通知**：一个持久化 Overlay，往里追加/移除消息节点。内置 ToastManager 就是这个模式——别每条消息建一个 Document。

**模态对话框**：持久化 Overlay + 全屏遮罩 + top layer 对话框三层。关对话框优先只移除对话框节点，整个 Overlay 不用了才 remove Document。

**多实例浮窗**：同模板 create 两次就是两个独立实例，各自存引用、各自 `remove()`。

## 常见问题

**Overlay 不显示**：是不是客户端线程调的？路径对不对？返回了 null 没有？是不是开着 Screen 而没开持久化？是不是误设了 `manuallyRendered`？根元素是不是零尺寸或被隐藏？

**盖住了错的页面**：查 body 的 translateZ，子元素 z-index 跨 Document 无效。

**点不动或穿透**：查 intercept meta、pointer-events、元素有没有实际尺寸、是不是有更上层的 Document 接住了事件。

**END 后没更新**：开了持久化就是预期行为，手动 refresh 或重建。

**关了还响应事件**：你大概只是把变量置了 null，没调 `document.remove()`。

## 性能建议

- 每类 Overlay 复用一个 Document，高频更新只改文本/class/属性；
- Toast、弹窗尽量收在同一个宿主 Document 里管，减少跨 Document 命中测试；
- 持久化 Overlay 保持轻量——它在所有 Screen 上都会画；
- 纯装饰层 `pointer-events: none`，少参与输入分发。
