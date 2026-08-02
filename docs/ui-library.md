# ApricityUI 内置 UI 库文档

最后更新：2026-08-02

源码包 `com.sighs.apricityui.ui` 是 ApricityUI 自带的 Java UI 组件库。它把常用的弹窗、菜单、提示、颜色编辑、文件选择和翻译功能直接挂到框架的 `Document`/`Element` DOM 上，供 DevTools、资源管理器以及业务代码复用。

本文对应的源码目录是：

~~~text
src/main/java/com/sighs/apricityui/ui/
~~~

包含以下类：

| 类 | 用途 |
| --- | --- |
| `DialogWindow` | 可拖动、可调整大小、可最大化的 Java 弹窗外壳 |
| `ContextMenu` | 固定定位的右键菜单/上下文菜单 |
| `ToastManager` | 全局 Toast 通知管理器 |
| `Tooltip` | 跟随鼠标的提示框和元素绑定工具 |
| `ColorPicker` | HEX、RGB、HSL 和 Alpha 颜色选择器 |
| `FilePicker` | 基于资源清单的 HTML/资源文件选择器 |
| `UiTranslations` | 为 Java 文本和无 DOM 文本解析翻译 key |

这些类属于 Java 侧组件，不是浏览器原生 HTML 元素，也不要求业务页面引入额外的 JavaScript 库。组件创建的元素仍然是 ApricityUI 的 DOM，会参与现有的布局、绘制、命中测试、焦点和事件流程。

## 1. 共同使用原则

### 1.1 Document 是组件宿主

大部分组件需要一个 `Document` 作为宿主：

~~~java
Document document = ApricityUI.createDocument("overlays/tool.html");
if (document == null || document.body == null) {
    return;
}

DialogWindow dialog = DialogWindow.open(
        document,
        DialogWindow.Options.of("SETTINGS", 480, 320, true),
        null
);
~~~

`DialogWindow`、`ContextMenu`、`Tooltip` 和 `ColorPicker.pickIn` 会把组件元素追加到传入 Document 的 `body`。调用方负责保证宿主 Document 在组件关闭前仍然有效，并在不再使用时移除自己的 Overlay Document。具体的 Document 创建和生命周期见 [Overlay Document 使用文档](overlay-document.md)。

### 1.2 独立模式和嵌入模式

部分组件提供两种入口：

| 模式 | 入口 | Document 所有者 | 适合场景 |
| --- | --- | --- | --- |
| 独立模式 | `ColorPicker.pick`、`FilePicker.pick` | 组件创建并管理内置 Document | 业务只关心结果，不需要自己管理 UI 宿主 |
| 嵌入模式 | `ColorPicker.pickIn`、`FilePicker.pickIn` | 调用方持有 | DevTools、编辑器或测试中复用已有页面 |

独立模式会使用 `devtools/color-picker.html` 或 `devtools/file-picker.html`。这些资源是框架内部模板，不应当把它们当成业务页面路径修改。

### 1.3 客户端线程

组件会创建 DOM、修改布局并注册客户端事件，因此应在 Minecraft 客户端线程调用。网络回调、异步加载回调或其他线程需要切回客户端线程：

~~~java
Minecraft.getInstance().execute(() -> {
    ToastManager.show("Loaded");
});
~~~

`CompletableFuture` 的完成回调也可能直接运行在完成 Future 的线程。如果回调中要修改 Minecraft Screen、Document 或组件，应再次使用 `Minecraft.getInstance().execute(...)`。

### 1.4 单活动实例

`ContextMenu`、`Tooltip`、`ColorPicker` 和 `FilePicker` 都维护一个全局活动实例。再次打开同类组件时，旧实例会被关闭；颜色选择器和文件选择器的旧 Future 会以 `Optional.empty()` 完成。

这意味着业务代码不应同时打开两个同类选择器，也不应把旧实例的 Future 当成新实例的结果使用。对应的状态查询和清理方法如下：

~~~java
ContextMenu.closeActive();
Tooltip.hide();
ColorPicker.closeActive();
FilePicker.closeActive();
~~~

## 2. DialogWindow

`DialogWindow` 是一个 Java 创建的通用弹窗外壳。它不规定业务内容，调用方通过 `content()` 追加内容，通过 `window()` 追加需要位于内容区之外的 footer 或控制条。

### 2.1 打开和关闭

~~~java
DialogWindow dialog = DialogWindow.open(
        document,
        DialogWindow.Options.of("EDIT META", 560, 360, true),
        () -> ToastManager.show("Dialog closed")
);

Element body = dialog.content();
Element label = Element.init(document.createElement("DIV"));
label.setTextContent("Edit the current document");
body.append(label);

if (dialog.isOpen()) {
    dialog.close();
}
~~~

`onClose` 在 `close()` 执行时调用，包括点击内置关闭按钮。关闭只会移除弹窗 DOM，不会自动移除宿主 Document。

### 2.2 Options

最常用的工厂方法是：

~~~java
DialogWindow.Options options = DialogWindow.Options.of(
        "SETTINGS", // title
        640,         // width，<= 0 时使用默认宽度
        420,         // height，<= 0 时按内容高度
        true         // resizable
);
~~~

`Options.of` 使用框架默认 CSS 类名。完整 record 字段如下：

| 字段 | 作用 |
| --- | --- |
| `title` | 标题文本，按普通文本写入，不作为 HTML 解析 |
| `width` | 初始宽度；非正数时按 viewport 计算，通常不超过 720 |
| `height` | 初始高度；非正数时不强制固定高度 |
| `resizable` | 是否创建八个方向的调整大小热区 |
| `overlayClass` | overlay CSS 类名 |
| `windowClass` | 窗口 CSS 类名 |
| `headingClass` | 标题栏 CSS 类名 |
| `titleClass` | 标题区域 CSS 类名 |
| `closeClass` | 关闭按钮 CSS 类名 |
| `contentClass` | `content()` 对应的内容区域 CSS 类名 |
| `titleIconClass` | 标题图标区域 CSS 类名；空字符串表示不创建图标区域 |
| `maximizable` | 是否显示最大化/恢复按钮 |

也可以使用完整构造函数覆写样式类：

~~~java
new DialogWindow.Options(
        "PREVIEW", 800, 560, true,
        "dialog-overlay show",
        "dialog resource-preview-window",
        "dialog-header",
        "dialog-title",
        "dialog-close",
        "dialog-body",
        "dialog-title-icon",
        true
);
~~~

### 2.3 拖动、调整大小和最大化

- 标题栏始终可以拖动窗口。
- `resizable == true` 时，窗口四角和四边都可以调整大小。
- 调整大小时最小宽度为 `360`，最小高度为 `240`。
- `maximizable == true` 时显示最大化按钮，最大化会填满宿主 Document viewport。
- 最大化状态下不能拖动或调整大小；再次点击按钮恢复打开前的位置和尺寸。

窗口位置使用宿主 Document 的逻辑 viewport 坐标，不是原始 Minecraft 屏幕像素坐标。页面有 `aui-viewport` 缩放时，不要把屏幕坐标直接写进窗口位置。

### 2.4 内容区和 footer

`content()` 返回标题栏下面的内容容器。需要固定在窗口底部的操作按钮应追加到 `window()`，而不是追加到内容容器：

~~~java
Element footer = Element.init(document.createElement("DIV"));
footer.setAttribute("class", "dialog-footer");

Element cancel = Element.init(document.createElement("BUTTON"));
cancel.setTextContent("CANCEL");
cancel.addEventListener("click", event -> dialog.close());
footer.append(cancel);

dialog.window().append(footer);
~~~

内容滚动、按钮样式和 footer 布局由调用方 CSS 负责。组件只负责窗口框架和指针交互。

## 3. ContextMenu

`ContextMenu` 创建一个固定定位的上下文菜单。它包含透明 backdrop，因此菜单打开时会接管当前 Document 的外部点击；点击菜单外部、滚轮或按 `Escape` 会关闭菜单。

### 3.1 最小示例

~~~java
ContextMenu menu = ContextMenu.show(
        document,
        new Position(mouseX, mouseY),
        List.of(
                ContextMenu.Item.header("FILE"),
                ContextMenu.Item.action("OPEN", () -> openFile()),
                ContextMenu.Item.action(
                        "COPY PATH",
                        ContextMenu.Icons.COPY,
                        "Ctrl+C",
                        () -> copyPath()
                ),
                ContextMenu.Item.separator(),
                ContextMenu.Item.action(
                        "DELETE",
                        ContextMenu.Icons.DELETE,
                        "Del",
                        () -> deleteFile()
                ).dangerous()
        )
);
~~~

`Position` 是传入 Document 的逻辑坐标。事件回调中的 `MouseEvent.clientX/clientY` 通常可以直接用于同一个 Document；如果坐标来自 Minecraft GUI 或另一个 Document，应先做坐标转换。

### 3.2 Item

`Item` 是不可变 record，使用静态工厂方法创建：

| 工厂方法 | 行为 |
| --- | --- |
| `Item.header(label)` | 创建不可点击的分组标题 |
| `Item.separator()` | 创建分隔线 |
| `Item.action(label, action)` | 创建普通操作项 |
| `Item.action(label, icon, action)` | 创建带 SVG 图标的操作项 |
| `Item.action(label, icon, shortcut, action)` | 同时显示图标和快捷键提示 |

操作项可以继续调用：

实际写法是先创建再修饰：

~~~java
ContextMenu.Item disabled = ContextMenu.Item
        .action("UNAVAILABLE", () -> {})
        .disabled();

ContextMenu.Item dangerous = ContextMenu.Item
        .action("REMOVE", () -> remove())
        .dangerous();
~~~

禁用项会保留在菜单中但不会响应点击；危险项使用危险操作样式。操作项被点击后，组件会先关闭菜单，再执行 `Runnable`。

### 3.3 图标和 Options

内置 `ContextMenu.Icons` 提供可复用的 SVG 字符串常量，包括：

~~~text
OPEN COPY REFERENCE PROPERTIES NEW_FILE NEW_FOLDER
RENAME EDIT DELETE REFRESH UP
~~~

需要自定义图标时，传入受信任的 SVG 字符串：

~~~java
ContextMenu.Item.action("CUSTOM", "<svg viewBox=\"0 0 14 14\">...</svg>", action);
~~~

`Options` 字段为：

| 字段 | 作用 |
| --- | --- |
| `className` | 菜单元素的 CSS 类名；空值使用默认类名 |
| `style` | 追加到菜单元素上的内联样式 |
| `onClose` | 菜单关闭回调 |

~~~java
ContextMenu.show(
        document,
        position,
        items,
        new ContextMenu.Options(
                "ctx-menu my-menu",
                "min-width:260px;",
                () -> logClose()
        )
);
~~~

### 3.4 位置和层级

菜单会根据测量结果限制在 viewport 内，默认距离边缘至少 4px。backdrop 和菜单使用较高层级，菜单本身设置为 top layer。打开菜单后，外部点击和滚轮事件不会继续传给下方页面。

需要主动关闭时使用：

~~~java
if (menu.isOpen()) {
    menu.close();
}
ContextMenu.closeActive();
~~~

## 4. ToastManager

`ToastManager` 是全局通知管理器。它把通知追加到持久化的内置 Overlay Document：

~~~text
devtools/toast.html
~~~

调用方不需要创建或持有这个 Document。Toast 默认出现在右上方，新消息插入到列表顶部。

### 4.1 显示普通文本

~~~java
String id = ToastManager.show("Saved");
String longLived = ToastManager.show("Loading...", 0);

ToastManager.dismiss(longLived);
~~~

默认持续约 `2600ms`，点击 Toast 可以关闭。传入 `durationMs <= 0` 时不会自动过期，必须显式调用 `dismiss(id)` 或 `clear()`。

`show` 返回 Toast ID；如果内置 Overlay 无法创建，可能返回空字符串，调用方不应把空字符串当成有效句柄。

### 4.2 ToastOptions

~~~java
ToastManager.ToastOptions options = new ToastManager.ToastOptions(
        4200,       // durationMs
        true,        // dismissOnClick
        "#ffffff",  // backgroundColor
        "#111111",  // textColor
        "#8b5cf6",  // borderColor
        "max-width:480px;"
);

String id = ToastManager.show("Custom notice", options);
~~~

`ToastOptions.defaults()` 等价于：

~~~java
new ToastManager.ToastOptions(2600, true, "", "", "", "")
~~~

| 字段 | 说明 |
| --- | --- |
| `durationMs` | 自动过期时间；非正数表示持久通知 |
| `dismissOnClick` | 是否在点击时关闭 |
| `backgroundColor` | 覆盖背景色 |
| `textColor` | 覆盖文字颜色 |
| `borderColor` | 覆盖边框颜色，也会影响左侧标记色 |
| `customStyle` | 追加的原始内联 CSS |

颜色和 `customStyle` 来自代码时应使用受信任值。它们会直接写入 style 属性，不是 CSS 属性白名单 API。

### 4.3 翻译 Toast 和生命周期

翻译通知要传 key，而不是先把 key 当普通文本显示：

~~~java
String id = ToastManager.showTranslation("ore_editor.apricityui.notice.saved");
~~~

实现会在 Toast 内容中创建 `TRANSLATION` 节点，因此语言环境变化或框架翻译流程可以处理该节点。`ToastManager.tick()` 已由框架的客户端帧调度器调用，通常不需要业务代码手动调用。

手动清理全部 Toast：

~~~java
ToastManager.clear();
~~~

`dismiss` 会先进入约 `180ms` 的淡出状态，再由后续 tick 移除；`clear` 直接移除当前 Toast。

如果只需要把已有翻译节点追加到自定义内容，可以使用：

~~~java
Element part = ToastManager.createTranslationMessagePart(
        document,
        "devtools.apricityui.saved"
);
container.append(part);
~~~

## 5. Tooltip

`Tooltip` 提供直接显示和元素绑定两种模式。全局同一时间只保留一个活动 Tooltip。Tooltip 设置了 `pointer-events:none`，不会阻挡下方页面的鼠标输入。

### 5.1 直接显示

~~~java
Tooltip tooltip = Tooltip.show(
        document,
        new Position(mouseX, mouseY),
        "Copy the selected path"
);

Tooltip.moveActive(new Position(nextX, nextY));
Tooltip.hide();
~~~

指针位置是 Document 逻辑坐标。Tooltip 会根据自身尺寸自动在 viewport 内翻转和限制位置：靠近右边或下边时，会尝试显示在指针左侧或上方。

### 5.2 Options

~~~java
Tooltip.Options options = new Tooltip.Options(
        "aui-tooltip compact",
        "border-left-color:#dc2626;",
        12, 16, 240
);
Tooltip.show(document, pointer, "Warning", options);
~~~

| 字段 | 默认值 | 作用 |
| --- | --- | --- |
| `className` | `aui-tooltip` | Tooltip CSS 类名 |
| `style` | 空 | 追加内联样式 |
| `offsetX` | `14` | 指针到 Tooltip 的水平偏移 |
| `offsetY` | `18` | 指针到 Tooltip 的垂直偏移 |
| `maxWidth` | `320` | 最大宽度 |

### 5.3 绑定到元素

静态文本：

~~~java
Tooltip.Binding binding = Tooltip.bind(button, "Open resource manager");
~~~

动态文本：

~~~java
Tooltip.Binding binding = Tooltip.bind(
        button,
        () -> selectedPath == null ? "No file selected" : selectedPath,
        Tooltip.Options.defaults()
);
~~~

翻译 key：

~~~java
Tooltip.Binding binding = Tooltip.bindTranslation(
        button,
        "tooltip.apricityui.meta.viewport"
);
~~~

绑定会注册 `mouseenter`、`mousemove` 和 `mouseleave` 监听器。元素被移除或组件不再使用时必须关闭绑定：

~~~java
binding.close();
~~~

`Binding` 实现 `AutoCloseable`，可以用于资源管理代码的清理流程。关闭绑定也会隐藏由该元素拥有的活动 Tooltip。

### 5.4 Screen 坐标转换

如果鼠标位置来自 Minecraft Screen，而不是 Document 事件，使用：

~~~java
Tooltip.moveActiveFromScreen(new Position(screenMouseX, screenMouseY));
~~~

该方法会调用活动 Tooltip 所属 Document 的 `screenToDocumentPosition`。这是处理 `aui-viewport` 缩放、固定 viewport 或世界窗口坐标时的正确入口。

## 6. ColorPicker

`ColorPicker` 是全局颜色选择器，支持 HEX、RGB、HSL 三种编辑模式和 Alpha 通道。

### 6.1 独立选择器

~~~java
ColorPicker.pick("#8b5cf6").thenAccept(result ->
        Minecraft.getInstance().execute(() ->
                result.ifPresent(color -> applyColor(color))
        )
);
~~~

返回值类型是：

~~~java
CompletableFuture<Optional<String>>
~~~

点击 `APPLY` 时返回 `Optional.of(value)`；点击 `CANCEL`、点击选择器外部、调用 `closeActive()` 或被另一个颜色选择器替换时返回 `Optional.empty()`。

### 6.2 嵌入现有 Document

~~~java
CompletableFuture<Optional<String>> result = ColorPicker.pickIn(
        toolDocument,
        colorSwatch,
        colorSwatch.getAttribute("data-color")
);

result.thenAccept(value -> value.ifPresent(next -> {
    // 更新业务值和色块
}));
~~~

`anchor` 可以为 `null`。非空时选择器会优先显示在锚点右侧；右侧空间不足时显示到左侧，并限制在 viewport 内。嵌入模式不会移除调用方的 Document。

### 6.3 编辑模式和结果格式

界面提供 `HEX`、`RGB`、`HSL` 标签，透明度可以在每个模式中编辑。结果格式与当前选中的模式对应：

| 模式 | 不透明结果 | 带 Alpha 结果 |
| --- | --- | --- |
| HEX | `#rrggbb` | `#rrggbbaa` |
| RGB | `rgb(r, g, b)` | `rgba(r,g,b,a)` |
| HSL | `hsl(h, s%, l%)` | `hsla(h, s%, l%, a)` |

初始值可以使用常见的 `#rgb`、`#rgba`、`#rrggbb`、`#rrggbbaa`、`rgb/rgba(...)` 或 `hsl/hsla(...)` 形式。无法解析时会回退到黑色不透明状态。

选择器还提供复制当前值按钮。屏幕取色按钮目前只显示 `EYEDROPPER NOT SUPPORTED`，不能把它当作已经实现的系统屏幕取色功能。

### 6.4 状态和清理

~~~java
if (ColorPicker.isOpen()) {
    ColorPicker.closeActive();
}
~~~

独立模式会删除自己创建的 `devtools/color-picker.html` Document；嵌入模式只删除选择器元素并标记宿主 Document 需要更新。

## 7. FilePicker

`FilePicker` 是资源管理器后端清单上的通用文件选择器。它不是原生文件系统对话框，而是显示 ApricityUI 已扫描到的静态资源：资源包、本地 `apricity` 目录和开发资源目录等。资源层和路径规则见 [资源形式、资源路径、资源管理和内置资源管理器](resource-manager.md)。

### 7.1 选择 HTML

~~~java
FilePicker.pick(FilePicker.Options.html("SELECT HTML", false))
        .thenAccept(result -> result.ifPresent(selection ->
                Minecraft.getInstance().execute(() -> openHtml(selection))
        ));
~~~

`false` 表示不显示资源包文件；传入 `true` 才会将 `RESOURCE_PACK` 层加入候选。选择器仍会显示本地和开发目录中的资源。

### 7.2 Options 工厂

~~~java
FilePicker.Options html = FilePicker.Options.html(
        "SELECT TEMPLATE", false
);

FilePicker.Options localizedHtml = FilePicker.Options.htmlTranslation(
        "devtools.apricityui.select_html", false
);

FilePicker.Options any = FilePicker.Options.any(
        "SELECT RESOURCE", true
);
~~~

| 工厂 | 扩展名过滤 | 标题 |
| --- | --- | --- |
| `html(title, includeResourcePackFiles)` | 仅 `html` | 普通文本标题 |
| `htmlTranslation(titleKey, includeResourcePackFiles)` | 仅 `html` | `TRANSLATION` key |
| `any(title, includeResourcePackFiles)` | 不过滤扩展名 | 普通文本标题；标题为空时使用内置翻译 |

也可以直接构造 `Options`：

~~~java
new FilePicker.Options(
        "SELECT CSS",
        Set.of(".css", "scss"),
        true
);
~~~

扩展名会自动转为小写并移除开头的点。空集合表示接受所有扩展名。只有过滤条件允许 `html` 时，界面才会提供新建 HTML 的入口。

### 7.3 Selection

完成选择后得到：

~~~java
public record Selection(
        String path,
        Loader.ResourceLayer layer,
        Path localPath
) {
}
~~~

字段含义：

| 字段 | 说明 |
| --- | --- |
| `path` | 资源逻辑路径，例如 `screens/home.html` |
| `layer` | 资源来源层，例如 `RESOURCE_PACK`、`LOCAL_FOLDER`、`DEV_FOLDER` |
| `localPath` | 可解析到本地文件时的绝对路径；资源包或无法解析时为 `null` |

资源包文件通常只有逻辑路径，不能假设 `localPath` 一定存在。要打开本地文件、写回 HTML 或编辑 Meta，必须先检查：

~~~java
selection.localPath();
~~~

### 7.4 选择、取消和新建

用户可以从左侧路径树、顶部路径导航和当前目录文件列表中浏览。单击选择文件，双击或点击确认按钮完成选择；关闭按钮、取消按钮、点击遮罩和 `closeActive()` 都会以 `Optional.empty()` 完成 Future。

~~~java
CompletableFuture<Optional<FilePicker.Selection>> future =
        FilePicker.pick(FilePicker.Options.html(null, false));

future.thenAccept(result -> {
    if (result.isEmpty()) {
        // 用户取消，或选择器无法打开
        return;
    }
    FilePicker.Selection selection = result.get();
    if (selection.localPath() == null) {
        ToastManager.show("Selected resource is read-only");
    }
});
~~~

HTML 过滤器允许新建 HTML 时，选择器会将新文件写入有效的本地资源目录，并刷新静态资源缓存。资源包来源是只读的，不会因为选择它而获得写入能力。

### 7.5 嵌入模式和测试数据

`pickIn` 用于把选择器渲染到调用方 Document，并显式传入资源条目：

~~~java
CompletableFuture<Optional<FilePicker.Selection>> future = FilePicker.pickIn(
        toolDocument,
        FilePicker.Options.html("SELECT", false),
        ClientLoader.listFinalStaticResources()
);
~~~

这个入口适合 DevTools、编辑器和测试。嵌入模式不会创建或删除外部 `devtools/file-picker.html` Document，但仍然遵循全局单活动实例规则。

### 7.6 内置模板缺失时的行为

独立模式优先加载 `devtools/file-picker.html`。如果模板不存在或找不到预期 DOM 节点，组件会尝试使用内嵌 fallback 结构；如果宿主 Document 无效，则直接返回已完成的空 Optional Future。

业务代码不应依赖 fallback 的内部 CSS 类或 DOM 结构。若要定制文件选择器外观，应使用 `pickIn` 并在自己的宿主页面中提供对应模板，或者维护调用方自己的选择界面。

## 8. UiTranslations

`UiTranslations.translate` 用于 Java 代码中无法直接放置 `TRANSLATION` DOM 节点的文本，例如 `aria-label`、窗口标题、Toast 普通文本或日志提示。

~~~java
String title = UiTranslations.translate("devtools.apricityui.edit_meta");
button.setAttribute("aria-label", title);
~~~

解析顺序是：

1. 在 Minecraft 客户端运行时尝试 `Component.translatable(key)`。
2. 客户端翻译不可用时读取内置 `en_us.json`。
3. 找不到 key 时返回原始 key，保证调用方不会得到 `null`。

内置 fallback 文件为：

~~~text
src/main/resources/assets/apricityui/lang/en_us.json
~~~

它适合 Java 侧的静态 UI 文本。HTML 内容或需要在 DOM 中保留翻译 key 时，应使用 `<translation>`/`TRANSLATION` 节点，或使用 `ToastManager.showTranslation`、`Tooltip.bindTranslation`、`FilePicker.Options.htmlTranslation` 等专用入口。

## 9. 坐标、层级和输入

### 9.1 逻辑坐标和屏幕坐标

UI 组件的位置参数通常是宿主 Document 的逻辑坐标。页面设置 `aui-viewport`、缩放值或世界窗口变换后，逻辑坐标与 Minecraft 屏幕像素可能不同。

转换关系由 Document 提供：

~~~java
Position local = document.screenToDocumentPosition(screen);
Position screen = document.documentToScreenPosition(local);
~~~

常见对应关系：

| 场景 | 处理 |
| --- | --- |
| `MouseEvent.clientX/clientY` 来自同一个 Document | 通常直接使用 |
| Minecraft Screen 的鼠标坐标 | 先 `screenToDocumentPosition` |
| Tooltip 跟随 Minecraft 鼠标 | 使用 `Tooltip.moveActiveFromScreen` |
| 跨 Document 显示菜单或 Tooltip | 先转换到目标 Document 坐标 |

### 9.2 top layer 和 z-index

组件通过 top layer 或高 z-index 避免被普通页面内容遮挡，大致层级如下：

| 组件 | 内部层级 |
| --- | --- |
| `Tooltip` | `11000` |
| `ContextMenu` 菜单/backdrop | `9500` |
| `DialogWindow` overlay | `9000` |
| 内置文件选择器模板 | 模板中的 `8000` |

这些值只在同一 Document 和当前渲染层中提供相对顺序。业务代码不应通过创建另一个 Toast 或 Dialog 来假设它一定会压过所有世界窗口、原版 GUI 或其他 Document。需要解决跨 Document 遮挡时，应调整宿主和 overlay 的创建方式。

### 9.3 输入拦截

- ContextMenu 的 backdrop 会拦截菜单外区域的鼠标输入，并在关闭前停止传播。
- DialogWindow 的 overlay/window 由其 CSS 和事件绑定决定是否拦截底层输入；关闭按钮不会把点击继续传给标题栏拖动逻辑。
- Tooltip 是 `pointer-events:none`，不会拦截输入。
- Toast 的列表容器不接收指针事件，但单个可点击 Toast 会接收点击。
- ColorPicker 和 FilePicker 的独立模板使用交互式 overlay；取消或点击外部会关闭。

宿主 Document 本身是否拦截 Minecraft 鼠标事件由 `aui-mouse-events` 等页面配置决定，不能只通过打开一个 Java 组件改变底层 Screen 的事件策略。相关配置见 [浏览器辅助功能文档](browser-features.md)。

## 10. 生命周期和资源清理

推荐把组件句柄和绑定放在与宿主 Document 同一生命周期的控制器中：

~~~java
final class ToolController implements AutoCloseable {
    private final Document document;
    private DialogWindow dialog;
    private Tooltip.Binding tooltipBinding;

    @Override
    public void close() {
        if (tooltipBinding != null) tooltipBinding.close();
        if (dialog != null && dialog.isOpen()) dialog.close();
        ContextMenu.closeActive();
        ColorPicker.closeActive();
        FilePicker.closeActive();
        Tooltip.hide(document);
        document.remove();
    }
}
~~~

注意事项：

- 关闭组件不等于关闭宿主 Document。
- 关闭宿主 Document 前先关闭绑定和活动组件，避免旧事件回调继续访问已移除元素。
- `FilePicker` 和 `ColorPicker` 的取消结果必须正常处理，不要只处理 `Optional.isPresent()` 的成功分支而遗留加载状态。
- Toast 不需要逐个保存 DOM 元素；保存 `show` 返回的 ID 即可主动关闭。
- `Tooltip.Binding` 必须调用 `close()`，特别是列表重建、页面刷新和 DevTools 切换目标 Document 时。
- Java UI 操作应在客户端线程执行。

## 11. 典型组合示例

下面的示例展示一个按钮同时使用 Tooltip、ContextMenu、ColorPicker 和 DialogWindow。示例假设 `document`、`button` 和 `colorSwatch` 已经存在。

~~~java
Tooltip.Binding help = Tooltip.bindTranslation(
        button,
        "tooltip.apricityui.meta.viewport"
);

button.addEventListener("contextmenu", event -> {
    event.preventDefault();
    ContextMenu.show(
            document,
            new Position(event.clientX, event.clientY),
            List.of(
                    ContextMenu.Item.header("ACTION"),
                    ContextMenu.Item.action("EDIT", () -> openEditor()),
                    ContextMenu.Item.action("CLOSE", () -> closeEditor())
            )
    );
});

colorSwatch.addEventListener("click", event ->
        ColorPicker.pickIn(document, colorSwatch, currentColor)
                .thenAccept(result -> result.ifPresent(next -> {
                    currentColor = next;
                    colorSwatch.setAttribute("style", "background:" + next + ";");
                }))
);

void openEditor() {
    DialogWindow dialog = DialogWindow.open(
            document,
            DialogWindow.Options.of("EDITOR", 520, 360, true),
            () -> ToastManager.show("Editor closed")
    );
    dialog.content().append(textInput);
}
~~~

实际项目中应把 `help` 和 `dialog` 保存到控制器字段，页面销毁时显式清理。示例中的 `event` 类型和 DOM 构造方式也应按照业务模块已有的辅助方法处理。

## 12. 常见问题

### 12.1 Future 一直没有结果

检查是否真的打开了组件，以及是否在关闭、取消和页面销毁时处理了 Future。`ColorPicker` 和 `FilePicker` 只有用户应用或取消后才完成；如果宿主 Document 被直接移除而没有调用 `closeActive()`，业务层可能只看到一个未完成 Future。

### 12. 菜单或 Tooltip 偏移

通常是把 Minecraft 屏幕坐标当成了 Document 坐标。对于缩放页面，调用 `screenToDocumentPosition`，或使用 `Tooltip.moveActiveFromScreen`。世界窗口还需要使用对应窗口的 Document 变换。

### 12. 弹窗内容被截断

`DialogWindow` 只提供窗口框架。固定高度窗口的 `content()` 会使用 flex 布局，业务内容需要自己设置 `overflow:auto`、`min-height:0` 和合适的 flex 属性。

### 12. FilePicker 中看不到文件

检查资源是否已经被 `ClientLoader` 扫描、扩展名是否匹配，以及 `includeResourcePackFiles` 是否为 `true`。资源包文件和本地文件可能拥有相同逻辑路径，选择时要同时检查 `path` 和 `layer`。

### 12. 选择了资源包文件却无法写回

这是预期行为。`RESOURCE_PACK` 通常是只读来源，`Selection.localPath()` 可以为 `null`。需要编辑 Meta 或写入 HTML 时，应选择本地 `apricity`/开发目录中的文件。

### 12. 翻译 key 直接显示出来

Java 文本使用 `UiTranslations.translate`；DOM 内容使用 `TRANSLATION` 节点；Toast 和 Tooltip 分别使用 `showTranslation`、`bindTranslation`。不要把翻译 key 传给普通的 `show(String)` 或 `Tooltip.show(..., String)`，那两个入口会把参数当作已经翻译好的普通文本。

## 13. 相关源码和测试

核心实现：

~~~text
src/main/java/com/sighs/apricityui/ui/DialogWindow.java
src/main/java/com/sighs/apricityui/ui/ContextMenu.java
src/main/java/com/sighs/apricityui/ui/ToastManager.java
src/main/java/com/sighs/apricityui/ui/Tooltip.java
src/main/java/com/sighs/apricityui/ui/ColorPicker.java
src/main/java/com/sighs/apricityui/ui/FilePicker.java
src/main/java/com/sighs/apricityui/ui/UiTranslations.java
~~~

内置模板和翻译资源：

~~~text
src/main/resources/assets/apricityui/apricity/devtools/toast.html
src/main/resources/assets/apricityui/apricity/devtools/color-picker.html
src/main/resources/assets/apricityui/apricity/devtools/file-picker.html
src/main/resources/assets/apricityui/lang/en_us.json
~~~

相关测试：

~~~text
src/test/java/com/sighs/apricityui/webapi/ContextMenuTest.java
src/test/java/com/sighs/apricityui/webapi/TooltipTest.java
src/test/java/com/sighs/apricityui/ui/file/FilePickerTest.java
src/test/java/com/sighs/apricityui/ui/toast/ToastManagerTest.java
~~~

相关框架文档：

- [ApricityScreen 使用文档](apricity-screen.md)
- [Overlay Document 使用文档](overlay-document.md)
- [Apricity 容器使用文档](container.md)
- [浏览器辅助功能文档](browser-features.md)
- [内置 DevTools 文档](devtools.md)
- [资源形式、资源路径、资源管理和内置资源管理器](resource-manager.md)
