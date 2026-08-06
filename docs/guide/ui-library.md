# 内置 UI 库

`com.sighs.apricityui.ui` 是框架自带的 Java 组件库：弹窗、右键菜单、Toast、Tooltip、颜色选择器、文件选择器、翻译工具。DevTools 和资源管理器就是用它们搭的，业务代码可以直接复用。组件创建的都是普通 AUI DOM，参与布局、绘制、命中测试，不需要页面引任何 JS 库。

## 四条通用规则

**宿主**：`DialogWindow`、`ContextMenu`、`Tooltip`、`ColorPicker.pickIn` 把元素追加到你传入的 Document 的 body。宿主 Document 得在组件关闭前一直有效，用完自己 remove。

**独立 / 嵌入**：`ColorPicker.pick`、`FilePicker.pick` 是独立模式（组件自建内置模板 Document）；`pickIn` 是嵌入模式（画进你的 Document）。独立模板是框架内部资源，别当业务页面改。

**线程**：组件建 DOM、注册事件，都在客户端线程调。Future 回调跑在完成线程上，回调里碰 UI 先 `Minecraft.getInstance().execute(...)`。

**单实例**：`ContextMenu`、`Tooltip`、`ColorPicker`、`FilePicker` 全局同时只有一个活动实例，再开一个旧的被关（选择器的旧 Future 以 `Optional.empty()` 完成）。清理入口：`ContextMenu.closeActive()`、`Tooltip.hide()`、`ColorPicker.closeActive()`、`FilePicker.closeActive()`。

## DialogWindow：弹窗外壳

```java
DialogWindow dialog = DialogWindow.open(
        document,
        DialogWindow.Options.of("SETTINGS", 480, 320, true),  // 标题, 宽, 高, 可调整大小
        () -> ToastManager.show("closed")                      // onClose，可空
);
dialog.content().append(myContent);   // 内容区
dialog.window().append(myFooter);     // 要固定在底部的按钮条放这里
dialog.close();
```

- 宽高传非正数表示按默认/内容计算；`resizable` 开八方向热区（最小 360×240）；Options 完整构造器还能覆写各部位 CSS 类名、开最大化按钮；
- 标题栏可拖动；最大化时不能拖动和调整；
- 位置是 Document 逻辑坐标，页面有 viewport 缩放时别把屏幕坐标直接写进去；
- 关闭只移除弹窗 DOM，不动宿主 Document；内容滚动、footer 布局是你自己的 CSS 的事（固定高度窗口记得给内容 `overflow:auto; min-height:0`）。

## ContextMenu：右键菜单

```java
ContextMenu.show(document, new Position(mouseX, mouseY), List.of(
        ContextMenu.Item.header("FILE"),
        ContextMenu.Item.action("OPEN", () -> openFile()),
        ContextMenu.Item.action("COPY PATH", ContextMenu.Icons.COPY, "Ctrl+C", () -> copyPath()),
        ContextMenu.Item.separator(),
        ContextMenu.Item.action("DELETE", ContextMenu.Icons.DELETE, "Del", () -> deleteFile()).dangerous()
));
```

- Item 工厂：`header` / `separator` / `action(label, [icon,] [shortcut,] action)`；`.disabled()` 禁用、`.dangerous()` 危险样式；点击后先关菜单再执行 action；
- `ContextMenu.Icons` 有一组内置 SVG 常量（OPEN/COPY/DELETE/RENAME 等），也可以传自定义 SVG 字符串；
- `Options(className, style, onClose)` 覆写样式和关闭回调；
- 自带透明 backdrop：点菜单外、滚轮、Esc 都关闭，且拦截外部点击；菜单自动限制在 viewport 内；
- `Position` 用 Document 逻辑坐标——来自 Screen 的鼠标坐标先 `document.screenToDocumentPosition(...)` 转换。

## ToastManager：通知

```java
String id = ToastManager.show("Saved");
String id2 = ToastManager.show("Loading...", 0);   // 时长<=0 不自动关，必须 dismiss
ToastManager.dismiss(id2);
ToastManager.clear();
```

挂在框架自持的内置 Overlay 上，你不用管宿主。默认约 2600ms、点击关闭。完整定制用 `ToastOptions(durationMs, dismissOnClick, backgroundColor, textColor, borderColor, customStyle)`。

翻译通知用 `showTranslation(key)`——它创建 TRANSLATION 节点跟随语言切换；别把 key 传给普通 `show()`，那是当字面文本显示的。

## Tooltip：提示框

```java
// 直接显示（全局单实例，pointer-events:none 不挡输入）
Tooltip.show(document, new Position(x, y), "Copy the selected path");
Tooltip.moveActive(new Position(x2, y2));
Tooltip.hide();

// 绑定到元素
Tooltip.Binding binding = Tooltip.bind(button, "Open resource manager");
Tooltip.Binding b2 = Tooltip.bind(button, () -> computeText(), Tooltip.Options.defaults());  // 动态文本
Tooltip.Binding b3 = Tooltip.bindTranslation(button, "tooltip.my.key");                       // 翻译键
binding.close();   // 元素移除或页面刷新前必须关
```

自动在 viewport 内翻转，靠近右/下边缘时显示到指针左/上方。`Options(className, style, offsetX, offsetY, maxWidth)` 默认 14/18/320。

鼠标位置来自 MC Screen 而非 Document 事件时，用 `Tooltip.moveActiveFromScreen(...)`，它内部做坐标转换——这是缩放页面下的正确入口。

## ColorPicker：颜色选择器

```java
ColorPicker.pick("#8b5cf6").thenAccept(result ->
        Minecraft.getInstance().execute(() ->
                result.ifPresent(color -> applyColor(color))));

// 嵌入：anchor 决定弹出位置（优先右侧，不够就左侧）
ColorPicker.pickIn(document, anchorElement, currentColor).thenAccept(...);
```

返回 `CompletableFuture<Optional<String>>`：APPLY → `of(value)`；CANCEL、点外部、被新选择器顶替 → `empty()`。**取消分支必须处理**，别只写成功路径。

HEX / RGB / HSL 三种编辑模式 + Alpha；结果格式跟随当前模式（`#rrggbb[aa]` / `rgb[a](...)` / `hsl[a](...)`）。初始值无法解析时回退黑色。取色按钮是占位，没有屏幕取色功能。

## FilePicker：资源文件选择器

不是系统文件对话框——它列的是 AUI 扫描到的资源（资源包、本地 apricity 目录、开发目录）：

```java
FilePicker.pick(FilePicker.Options.html("SELECT HTML", false))   // false=不含资源包文件
        .thenAccept(result -> result.ifPresent(sel ->
                Minecraft.getInstance().execute(() -> openHtml(sel.path()))));
```

- Options 工厂：`html(title, includeResourcePack)` / `htmlTranslation(key, ...)` / `any(title, ...)`；或直接 `new Options(title, Set.of(".css"), includeResourcePack)`（扩展名空集=全部接受；含 html 时才提供新建入口）；
- 结果 `Selection(path, layer, localPath)`：`path` 逻辑路径、`layer` 来源层、`localPath` 本地绝对路径——**资源包文件的 localPath 是 null**，要写文件先判空；
- 取消（关闭/取消按钮/点遮罩/closeActive）以 `empty()` 完成；
- `pickIn(document, options, ClientLoader.listFinalStaticResources())` 嵌入模式，给 DevTools、编辑器、测试用。

## UiTranslations：Java 侧翻译

```java
String title = UiTranslations.translate("devtools.apricityui.edit_meta");
button.setAttribute("aria-label", title);
```

给放不了 TRANSLATION 节点的地方用（aria-label、窗口标题、日志）。解析顺序：MC `Component.translatable` → 内置 en_us.json → 找不到返回原 key（永不返回 null）。DOM 内容用 `<translation>`，Toast/Tooltip/FilePicker 各有 `*Translation` 专用入口。

## 层级与清理

组件内部层级大致：Tooltip 11000 > ContextMenu 9500 > DialogWindow 9000。这只在同一 Document 内有意义，别指望它压过其他 Document 或世界窗口——跨 Document 遮挡要调整宿主创建方式。

宿主销毁前按反序清理：

```java
tooltipBinding.close();
if (dialog != null && dialog.isOpen()) dialog.close();
ContextMenu.closeActive();
ColorPicker.closeActive();
FilePicker.closeActive();
document.remove();
```

## 常见问题

**Future 一直没结果**：用户没点完，或宿主被直接 remove 而没 closeActive。取消分支要处理。

**菜单/Tooltip 位置偏移**：屏幕坐标当逻辑坐标用了。`screenToDocumentPosition` 或 `moveActiveFromScreen`。

**弹窗内容被截断**：DialogWindow 只给框架，内容区自己加 `overflow:auto` 和 flex 属性。

**FilePicker 里看不到文件**：资源被扫描了吗？扩展名过滤匹配吗？资源包文件要 `includeResourcePackFiles=true`。

**选了资源包文件写不回**：预期行为，它是只读来源。要编辑选本地目录的文件。

**翻译 key 原样显示**：用错了入口——普通文本入口不解析 key，用各自的 Translation 变体。
