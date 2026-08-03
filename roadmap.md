# Ore 可视化编辑器 Roadmap

> 状态：设计冻结前的实施路线图
> 更新日期：2026-07-27
> 目标版本：Ore Editor 1.0
> 实施原则：先独立完成编辑器，再把 F12 作为入口接入；所有交互逻辑在 Java 侧；所有本地化文案使用 AUI 原生 `translation` 标签。

## 1. 最终目标

为 Ore 主题提供一套独立、可视化、组件化的页面编辑器。用户可以在左侧画布创建和嵌套 Flex 容器，从右侧拖入组件，调整 Flex 与绝对定位属性，编辑 Ore 主题变量，并将结果保存为可继续编辑的项目或导出为干净 HTML。

编辑器本身必须直接使用 Ore UI，而不是另外写一套“类似 Ore”的皮肤。工具栏、右侧编辑器、弹窗、按钮、输入框、下拉框、页签、面板、提示和状态控件都应复用 `ore.css` 的真实组件 class。编辑器界面与被编辑画布必须隔离：修改画布主题变量不能破坏编辑器控件的可读性与可操作性。

第一版完成时必须满足：

- 可从 F12 标题栏图标打开，但不复用 DevTools 的控制器、DOM 树、Inspector 或样式。
- 编辑器使用独立 HTML、独立 CSS、独立 Java 包和独立生命周期。
- 左侧为画布，右侧为编辑器，右侧默认宽度 `420px`，可在 `360px` 到 `560px` 之间调整。
- 所有新建容器均为标准 Flex 容器，并以 `position: relative` 作为绝对定位子元素的包含块。
- 支持组件从右侧拖入画布、嵌套容器命中、排序、跨容器移动和绝对定位。
- 编辑模式下以独立覆盖层显示 Flex 边界、轴线、换行、间距、目标容器和插入位置，不通过修改被编辑元素边框实现。
- 支持撤销、重做、保存项目、导出干净 HTML。
- 默认 `ore-edit.css` 与当前 `ore.css` 的视觉结果一致。
- 中文和英文界面完整可用，静态与 Java 动态生成的 DOM 文案均使用 `translation` 标签，不写死最终用户文案。
- 浏览器与 Minecraft 中的布局、交互和视觉效果按浏览器标准逐项比对。

## 2. 不可变约束

### 2.1 架构边界

- 保留现有 `ore.css`，不把编辑器变量化需求直接混入稳定主题。
- 新建 `ore-edit.css`，默认变量值必须复现 `ore.css` 当前效果。
- 新建 `ore-editor.html` 与 `ore-editor.css`，不把编辑器塞进 `devtools.html`。
- `ore-editor.html` 必须直接加载 Ore 主题；通用控件必须使用 `ore.css` 已有的 Ore UI class，不允许在 `ore-editor.css` 中重做一套按钮、输入框、下拉框、面板或弹窗皮肤。
- `ore-editor.css` 只负责编辑器专属结构：左右工作区、面板宽度、画布视口、palette 排布、拖放 ghost、Flex overlay、resize handle 和必要状态布局。
- Ore Editor 不依赖 `DevToolsController`、`DevToolsInspector`、`DevToolsDomTree` 或 DevTools 的 HTML/CSS。
- F12 只负责提供启动入口和传递当前 document 标识。
- HTML 只提供语义壳、固定挂载点、meta 和静态 `translation` 节点；事件绑定、动态 DOM 填充、状态管理、拖放和序列化在 Java 侧。
- 不引入依赖 KubeJS 的编辑器逻辑。
- 不调用、移动或模拟系统鼠标；只消费 AUI 自身鼠标事件。

### 2.2 浏览器语义

- Flex 排版、绝对定位包含块、尺寸计算、overflow、滚动条、命中测试和层叠顺序遵循浏览器标准。
- 发现 MC 与 Chrome 差异时，先用最小 HTML 复现并修底层框架；不在 Ore Editor 中增加只针对某个案例的补偿样式。
- 覆盖层不能参与正常布局，不能改变被编辑节点的尺寸、换行、滚动范围或命中区域。
- 拖放位置以实际布局结果为准，不以 DOM 顺序简单推测。
- `row-reverse`、`column-reverse`、`wrap-reverse`、`order` 和绝对定位子项必须进入测试矩阵。

### 2.3 视觉约束

- 方正几何，3px 深色边框，顶部/左侧内高光和底部深度阴影。
- 绿色用于创建与确认，紫色用于选择与编辑状态，金色用于布局提示，红色只用于删除、错误和非法状态。
- 不使用渐变、模糊阴影、现代圆角卡片或卡片套卡片。
- 图标按钮优先使用项目现有图标方案或 Lucide 语义；陌生图标必须有翻译后的 tooltip。
- 编辑器外壳使用 `ore.css` 的真实组件和冻结的 Ore UI token；画布使用正在编辑的 `--ore-*` token。
- 编辑器专属 CSS 不得覆盖 Ore 通用组件的基础视觉；确需新增状态时，只增加编辑器语义 class，并组合已有 Ore class。

### 2.4 Ore UI 复用边界

编辑器控件必须按下表直接复用 Ore UI：

| 编辑器区域 | 必须复用的 Ore UI 能力 |
| --- | --- |
| 顶部工具栏 | navbar、button、button-small、tooltip |
| ADD / INSPECT / THEME | tabs 或 Ore 导航按钮状态 |
| palette 项 | card/panel 的边框、表面、深度与交互状态 |
| 属性输入 | form-group、form-label、form-input、form-select、form-textarea |
| 开关与选项 | choice、checkbox/radio、segmented control 的 Ore 状态色 |
| Inspector 分组 | panel、panel-header、panel-body、divider |
| 操作区 | button-primary、button-secondary、button-danger、disabled 状态 |
| 保存与确认 | Ore dialog/panel、Ore button 与 choice 样式 |
| 状态反馈 | Ore alert、badge、progress、tooltip、toast |

允许 `ore-editor.css` 新增的内容：工作区尺寸与分栏、拖动/resize 命中区、画布网格、编辑辅助层、插入线、选中框、字段紧凑布局和节点层级布局。

禁止 `ore-editor.css` 重新定义的内容：Ore 按钮 3D 深度、通用输入框边框与内阴影、select 外观、通用 panel/card 表面、字体体系、基础颜色体系、hover/active/focus/disabled 的通用视觉。

若现有 Ore UI 缺少编辑器需要的通用组件，应先把该组件作为 Ore 主题能力补入 `ore.css`/`ore-edit.css` 并在主题示例页中展示，再由编辑器复用；不得只在编辑器里做一次性版本。

## 3. 国际化与文案规则

### 3.1 强制要求

所有进入 DOM 的本地化文案必须使用 AUI 原生 `translation` 标签：

```html
<translation>ore_editor.apricityui.title</translation>
<button class="ore-action">
  <translation>ore_editor.apricityui.action.save_project</translation>
</button>
```

Java 动态创建 UI 时也必须创建 `TRANSLATION` 元素，而不是先把翻译键解析成 `String`：

```java
Element label = Element.init(document.createElement("TRANSLATION"));
label.setTextContent("ore_editor.apricityui.property.flex_direction");
```

具体实现统一收口到 `OreEditorDom.translation(document, key, className)`，但返回值仍然是真实的 `Translation` DOM 节点。业务类不得直接读取语言 JSON，不得在 palette、Inspector 或弹窗中写死中文/英文，也不得用 `Component.translatable(...).getString()` 提前固化当前语言。

`translation` 元素当前通过自身文本内容读取翻译键，暂不支持格式化参数。动态值采用可组合 DOM：静态语句使用 `translation`，路径、数量、尺寸、节点名等使用相邻的普通数据节点。例如“已选择 3 个节点”应拆为翻译标签与数值节点，不能拼接一段只适用于某种语序的字符串。若后续确实需要复数或参数换位，应先扩展 `Translation` 元素的参数能力并建立独立测试。

tooltip 的触发元素可以继续通过 `data-tooltip-key` 保存翻译键，但 Tooltip 展示层必须改为创建 `TRANSLATION` 内容节点，不能像当前实现一样先调用 `Component.translatable(...).getString()`。toast、右键菜单、确认弹窗等内置 UI 如果只能接收已经解析的 `String`，也应先增加接收翻译键或 `Translation` 节点的 API，再由控件创建 `translation` 标签；不在 Ore Editor 中绕过标签自行翻译。

必须翻译的内容包括：

- 标题、页签、分组标题、字段标签、按钮、菜单项。
- palette 中的容器与组件名称、简要说明。
- 所有 tooltip、空状态、错误、校验提示、toast、确认弹窗。
- 拖放时的目标提示、非法投放原因、绝对定位状态提示。
- 属性枚举的人类可读名称与说明。
- 保存、导出、覆盖、恢复、未保存变更等状态。
- 动态状态周围的静态文案；节点名、数量、尺寸、路径等数据使用独立节点组合。

CSS 属性名、HTML 标签名、标准枚举值、文件路径和数值不翻译，但它们周围的说明必须翻译。

### 3.2 `translation` 标签使用边界

- 标题、按钮、字段标签、选项名、说明、空状态和弹窗正文使用 `<translation>key</translation>`。
- Java 动态 DOM 使用 `OreEditorDom.translation(...)` 创建相同标签。
- SVG 图标不承载文案；图标按钮通过 `data-tooltip-key` 保存翻译键，Tooltip 打开后用该 key 创建 `TRANSLATION` 节点。
- `aria-label` 等不能包含子元素的属性，由通用可访问性绑定器根据同一个 key 填入；可见文案仍必须是 `translation` 标签。
- CSS 属性名、HTML 标签名、标准枚举值、文件路径、UUID 和纯数值属于数据，不放入 `translation`。
- 用户自行输入的组件内容不翻译，也不能被误当成翻译键。
- 保存和导出业务画布时，编辑器自身的 `translation` 节点不会进入输出；用户画布中明确创建的 translation 组件可以保留。

### 3.3 翻译键命名

统一使用 `ore_editor.apricityui.*`，tooltip 使用 `tooltip.apricityui.ore_editor.*`：

```text
ore_editor.apricityui.title
ore_editor.apricityui.mode.add
ore_editor.apricityui.mode.inspect
ore_editor.apricityui.mode.theme
ore_editor.apricityui.palette.containers
ore_editor.apricityui.palette.components
ore_editor.apricityui.container.row
ore_editor.apricityui.container.row.description
ore_editor.apricityui.component.button
ore_editor.apricityui.component.button.description
ore_editor.apricityui.property.flex_direction
ore_editor.apricityui.value.row
ore_editor.apricityui.action.undo
ore_editor.apricityui.action.redo
ore_editor.apricityui.action.save_project
ore_editor.apricityui.action.export_html
ore_editor.apricityui.error.invalid_drop
ore_editor.apricityui.toast.saved
tooltip.apricityui.ore_editor.absolute_position
```

### 3.4 语言资源验收

- `zh_cn.json` 与 `en_us.json` 同时添加同一组键。
- 增加测试比较两份语言文件中 `ore_editor.apricityui.*` 与 `tooltip.apricityui.ore_editor.*` 的键集合。
- 测试禁止值为空、禁止 `translation` 的渲染结果回退为键名。
- 中文界面作为默认人工验收界面，英文界面用于检查长文本布局和截断。
- 切换游戏语言后重新打开编辑器必须得到新语言，不缓存上次语言的最终字符串。
- 增加静态扫描：本地化 UI 挂载区不得出现未经豁免的硬编码英文或中文文本节点。
- 增加 DOM 测试：静态壳和 Java 动态生成界面的本地化节点 tagName 均为 `TRANSLATION`。

## 4. 目标文件结构

```text
targets/forge-1.20.1/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/
├─ ore.css                         # 现有稳定主题，不改语义
├─ ore-edit.css                    # 变量驱动的可编辑版本
└─ ore-edit-example.html           # 变量覆盖与回归示例

targets/forge-1.20.1/src/main/resources/assets/apricityui/apricity/editor/ore/
├─ ore-editor.html                 # 无业务 JS 的静态壳
└─ ore-editor.css                  # 仅编辑器布局、palette 排布和 overlay，不重做 Ore 控件

common/src/main/java/com/sighs/apricityui/editor/ore/
├─ OreEditor.java                  # 对外 open/close/toggle API
├─ OreEditorController.java        # 生命周期、绑定与顶层协调
├─ OreEditorDom.java               # DOM 辅助方法及 Translation 节点工厂
├─ OreEditorTranslationKeys.java   # 翻译键常量，避免业务类散落字符串
├─ OreEditorSession.java           # 当前项目、选择、模式、dirty 状态
├─ OreEditorDocumentStore.java     # 路径解析、读取、原子保存、导出
├─ OreEditorSerializer.java        # 项目 HTML 与干净 HTML 序列化
├─ model/
│  ├─ OreCanvasNode.java
│  ├─ OreContainerNode.java
│  ├─ OreComponentNode.java
│  ├─ OreNodeStyle.java
│  ├─ OreFlexStyle.java
│  ├─ OreAbsoluteStyle.java
│  ├─ OreThemeState.java
│  └─ OreEditorProject.java
├─ canvas/
│  ├─ OreCanvasController.java
│  ├─ OreCanvasRenderer.java
│  ├─ OreCanvasHitTester.java
│  ├─ OreFlexInsertionResolver.java
│  ├─ OreCanvasSelection.java
│  └─ OreCanvasOverlay.java
├─ drag/
│  ├─ OreDragController.java
│  ├─ OreDragSession.java
│  ├─ OreDragPayload.java
│  └─ OreDropCandidate.java
├─ history/
│  ├─ OreEditorHistory.java
│  ├─ OreEditorCommand.java
│  ├─ AddNodeCommand.java
│  ├─ RemoveNodeCommand.java
│  ├─ MoveNodeCommand.java
│  ├─ ReparentNodeCommand.java
│  ├─ UpdatePropertyCommand.java
│  └─ ToggleAbsoluteCommand.java
├─ palette/
│  ├─ OrePaletteController.java
│  ├─ OreContainerTemplate.java
│  ├─ OreComponentDefinition.java
│  └─ OreComponentRegistry.java
├─ inspector/
│  ├─ OreInspectorController.java
│  ├─ OreContainerInspector.java
│  ├─ OreComponentInspector.java
│  ├─ OreThemeInspector.java
│  └─ fields/
│     ├─ OreField.java
│     ├─ ColorField.java
│     ├─ LengthField.java
│     ├─ NumberField.java
│     ├─ SelectField.java
│     ├─ ToggleField.java
│     ├─ BoxModelField.java
│     ├─ FontField.java
│     ├─ ShadowField.java
│     └─ StateSelector.java
└─ dialog/
   ├─ OreSaveDialog.java
   ├─ OreExportDialog.java
   └─ OreUnsavedChangesDialog.java

src/test/java/com/sighs/apricityui/editor/ore/
├─ OreEditorTranslationTest.java
├─ OreEditorModelTest.java
├─ OreFlexInsertionResolverTest.java
├─ OreAbsolutePositionTest.java
├─ OreEditorHistoryTest.java
├─ OreEditorSerializerTest.java
├─ OreEditorDocumentStoreTest.java
└─ OreEditorIntegrationTest.java
```

类可以在实施过程中合并明显过薄的职责，但禁止把全部逻辑重新堆入单个 Controller。

## 5. HTML 壳与页面结构

`ore-editor.html` 必须包含：

```html
<meta charset="UTF-8">
<meta name="aui-font-mode" content="web">
<meta name="aui-viewport" content="mode=browser">
<meta name="aui-mouse-events" content="intercept">
<meta name="aui-ore-editor" content="1">
```

并直接加载 Ore UI 与编辑器专属结构样式：

```html
<link rel="stylesheet" href="../../apricityui/theme/ore/ore.css">
<link rel="stylesheet" href="ore-editor.css">
```

最终相对路径在实现时以 AUI 资源解析结果为准，但依赖方向固定为“编辑器加载 Ore”，不能把 Ore 通用样式复制进 `ore-editor.css`。

建议语义结构：

```text
body.ore-editor-shell
├─ header.editor-toolbar
│  ├─ brand/title
│  ├─ document path + dirty state
│  └─ undo / redo / preview / save / export / close
└─ main.editor-workspace
   ├─ section.canvas-region
   │  ├─ canvas-toolbar
   │  ├─ canvas-viewport
   │  │  ├─ canvas-document
   │  │  └─ canvas-overlay-root
   │  └─ zoom/status bar
   ├─ aside.editor-panel
   │  ├─ mode tabs: ADD / INSPECT / THEME
   │  ├─ panel-scroll-body
   │  └─ selection/footer status
   └─ panel-resize-handle
```

编辑器外壳根节点应用 `ore-theme`，实际控件同时携带对应 Ore class。画布根节点再建立独立变量作用域，例如 `#oreCanvasDocument`；THEME 模式只把 working token 写到该画布根节点。编辑器 toolbar、右栏和弹窗从冻结的 editor token 作用域取值，不继承画布 working token。

静态 HTML 保存稳定结构、挂载点以及适合静态声明的 `translation` 标签。动态 palette、属性表单和状态提示由 Java 注入，但其文案仍创建为 `TRANSLATION` 元素。按钮通过 `data-tooltip-key` 保存 key，Tooltip 展示层创建对应 `TRANSLATION` 节点；需要属性文本时由通用可访问性绑定器从同一 key 补齐。

## 6. 数据模型

### 6.1 节点模型

```java
sealed interface OreCanvasNode
        permits OreContainerNode, OreComponentNode {
    UUID id();
    UUID parentId();
    String displayName();
    OreNodeStyle style();
}
```

每个画布 DOM 节点同步携带：

```html
data-ore-node-id="..."
data-ore-node-type="container|component"
```

模型是编辑状态的唯一可信来源。DOM 是模型的投影，不能在 DOM 中产生模型不知道的持久修改。

### 6.2 容器模型

`OreContainerNode` 至少保存：

- 稳定 UUID、父节点 UUID、子节点顺序。
- `flex-direction`、`flex-wrap`、`justify-content`、`align-items`、`align-content`。
- `row-gap`、`column-gap`。
- width、height、min/max width、min/max height。
- margin、padding、border、background、overflow。
- 编辑器显示名和可选 HTML 语义标签。
- 容器锁定状态；根容器默认锁定且不可删除。

所有新容器固定具备 `display:flex` 和 `position:relative`。第一版不允许用户把容器改成 Grid、block 或 static，以保证拖放算法边界清晰。

### 6.3 组件模型

`OreComponentNode` 至少保存：

- 组件类型、内容数据、语义属性和状态样式。
- Flex item 属性：`order`、`flex-grow`、`flex-shrink`、`flex-basis`、`align-self`。
- width/height 与 min/max 约束。
- margin、padding、颜色、边框、字体、阴影。
- `positionMode = FLOW | ABSOLUTE`。
- absolute 时的 top/right/bottom/left、z-index 和有效锚边。
- 从 absolute 返回 flow 时需要恢复的父容器与 flow index。
- Default/Hover/Active/Focus/Disabled 五种样式状态。

### 6.4 主题状态

`OreThemeState` 保存 `ore-edit.css` 暴露的变量值和默认值。变量按以下组组织：

- Palette：文字、画布、表面、边缘、绿色、紫色、金色、红色、蓝色、状态色。
- Typography：display/body font、字号层级、行高、字重。
- Depth：按钮底部深度、内高光、按下偏移。
- Border：基础宽度、focus 宽度、边缘颜色。
- Spacing：`--ore-space-1` 到 `--ore-space-5` 及默认 gap。
- Component defaults：按钮高度、输入框高度、panel padding、表格行高等。

## 7. 右侧编辑器详细设计

### 7.1 固定框架

- 默认宽度 `420px`；用户拖动左侧 resize handle 调整到 `360px..560px`。
- toolbar、模式栏、Inspector、状态栏和弹窗直接组合 Ore UI class；不得用编辑器私有 CSS 模仿 Ore 控件。
- 顶部模式栏和底部状态栏固定，只有中部内容滚动。
- 模式为 `ADD`、`INSPECT`、`THEME`，显示文本必须翻译。
- 模式切换不丢失当前画布选择。
- 字段修改后只局部更新相关字段和画布节点，不重建整列。
- 长中文和长英文均不得覆盖按钮、图标或数值输入。

### 7.2 ADD 模式

二级切换：`CONTAINERS | COMPONENTS`。

容器模板：

| 模板 | 初始属性 |
| --- | --- |
| Row | `row / nowrap / flex-start / stretch` |
| Column | `column / nowrap / flex-start / stretch` |
| Wrap Row | `row / wrap` |
| Wrap Column | `column / wrap` |
| Reverse | `row-reverse / nowrap` |
| Center | `row / nowrap / center / center` |
| Split | `row / nowrap / space-between / center` |

组件初始清单：

- Button、Input、Textarea、Select、Checkbox、Radio。
- Card、Panel、Tabs、Modal。
- Alert、Toast-like notice、Badge、Progress。
- Table、List、Divider。
- Heading、Paragraph、Image、Icon、Slot。
- 自定义 HTML 容器作为后续扩展项，不进入第一阶段 MVP。

palette 使用双列方形或接近方形的 Ore 风格块，每项包含图标、翻译后的名称和微型预览。hover tooltip 显示翻译后的用途说明；拖动时显示独立 drag ghost，不直接拖动原 palette 节点。

支持搜索和分类折叠作为 1.0 后半阶段功能：搜索匹配翻译后的名称、组件 ID 和 HTML 标签名。

### 7.3 INSPECT：容器

容器被选中时显示以下区域：

1. Identity
   - 翻译后的类型、可编辑显示名、节点 UUID 前八位。
   - 锁定、复制、删除操作；根容器隐藏删除。
2. Flex Direction
   - 四段式图标控件：row、row-reverse、column、column-reverse。
3. Wrap
   - nowrap、wrap、wrap-reverse。
4. Alignment
   - `justify-content` 可视化主轴选择。
   - `align-items` 可视化交叉轴选择。
   - `align-content` 仅在多行容器有效；无效时禁用并用 tooltip 说明原因。
5. Gap
   - row-gap、column-gap；提供联动开关。
6. Size
   - width/height、min/max，支持 px、%、auto、fit-content 等当前框架已正确支持的标准值。
7. Spacing
   - margin 与 padding 四边编辑、联动和单边展开。
8. Surface
   - background、border、color、shadow。
9. Overflow
   - visible、hidden、auto、scroll；横纵轴可分开设置。

### 7.4 INSPECT：组件

组件被选中时显示：

1. Content
   - 依据组件类型显示文本、placeholder、选项列表、图片路径、链接等。
2. Layout Item
   - order、grow、shrink、basis、align-self。
3. Position
   - `ABSOLUTE POSITION` 开关。
   - flow 时显示 Flex item 属性；absolute 时显示 top/right/bottom/left、width/height、z-index 和锚点状态。
4. Size & Spacing
   - width/height/min/max、margin、padding。
5. Appearance
   - 颜色、背景、边框、字体、阴影、透明度。
6. State
   - Default/Hover/Active/Focus/Disabled 状态选择器。
7. Actions
   - 复制、锁定、上移/下移、移至父容器、删除。

### 7.5 THEME 模式

THEME 直接编辑当前项目的全局 `--ore-*` 变量，不修改编辑器外壳 token。

每个变量行需要：

- 翻译后的名称。
- 当前值和必要的单位控件。
- 默认值对照。
- 单项重置按钮。
- 翻译后的 tooltip，说明影响范围。

每个分组有“重置本组”，底部有“重置全部”。重置必须可撤回。颜色修改实时预览，但拖动颜色/数值控件期间只在结束时生成一条历史记录。

### 7.6 复用字段组件

- `ColorField`：色块、文本值、透明度；校验标准 CSS 颜色。
- `LengthField`：数字、单位下拉、关键字选择；不把 `auto` 伪装成数值。
- `NumberField`：min/max/step、键盘微调。
- `SelectField`：使用 AUI 浏览器标准 select 实现，选项与说明均翻译。
- `ToggleField`：明确 on/off 状态，不只依赖颜色。
- `BoxModelField`：四边值、联动状态、可视化方向。
- `FontField`：字体族、字号、行高、字重、对齐。
- `ShadowField`：inset、offset、blur、spread、color 的结构化编辑。
- `StateSelector`：组件伪类状态切换，显示当前状态是否有覆盖。

字段组件统一处理 focus、disabled、invalid、keyboard、tooltip 和提交时机，业务 Inspector 不自行重复绑定底层事件。

## 8. 画布与覆盖层

### 8.1 画布视口

- 画布区域可平移与缩放，但 1.0 首先保证 100% 比例编辑正确。
- 缩放只作用于画布呈现层；命中测试必须把屏幕坐标逆变换为画布坐标。
- 画布根容器始终存在、可选中、不可删除。
- 空容器显示翻译后的空状态提示，但提示不能成为可导出的内容。
- 编辑器辅助节点统一标记 `data-ore-editor-ui`，序列化时排除。

### 8.2 Flex 覆盖层

选中或拖放命中的 Flex 容器时，覆盖层绘制：

- 内容盒虚线边界。
- 主轴与交叉轴方向。
- 每条 flex line。
- row-gap 与 column-gap 区域。
- 当前 drop target 高亮。
- 插入位置线或插入占位轮廓。

覆盖层是单独的绝对定位根节点，使用读取到的最终布局矩形绘制。它不得：

- 给目标元素增加 border/outline/padding。
- 修改目标元素 class 或 inline style 来显示辅助线。
- 捕获本应到达画布的鼠标事件。
- 进入保存或导出结果。

### 8.3 选择和 hover

- 普通移动：高亮鼠标所在最深可编辑节点。
- 容器选择：同时显示容器边界与 Flex 辅助信息。
- 组件选择：显示组件盒模型与 resize/absolute 操作柄。
- hover 高亮和 selected 高亮使用同一套几何计算，但颜色与优先级不同。
- 拖放期间 drop target 高亮优先于普通 hover。

## 9. 拖放与命中算法

### 9.1 状态机

```text
IDLE
  -> PRESSED        鼠标按下 palette 或现有节点
  -> DRAGGING       超过拖动阈值
  -> TARGETING      已找到合法容器与插入点
  -> COMMITTING     mouseup 提交一次命令
  -> IDLE

任意状态 -> CANCELLED -> IDLE
```

Esc、源节点失效、document 关闭或失焦都必须取消拖动并清理 ghost、overlay 与 pointer 状态。

### 9.2 帧率

- 鼠标位置由 ScreenEvent/渲染帧路径刷新，不走 20 TPS tick。
- 每帧最多执行一次 hit-test 和一次 overlay 几何更新。
- `mousemove` 只记录最新坐标并标记 dirty，不连续触发完整 DOM 重建。
- mouseup 合并连续变化为一条历史命令。

### 9.3 容器命中

1. 用屏幕坐标命中画布内最深元素。
2. 沿父链向上找到最深的 `OreContainerNode`。
3. 排除锁定、不可接收、会造成祖先插入自身的容器。
4. 检查 pointer 是否位于容器可见裁剪区域。
5. 若没有子容器命中，回退到根容器。

### 9.4 插入位置

`OreFlexInsertionResolver` 使用实际最终矩形，而非仅使用 DOM index：

- 忽略 `position:absolute` 子项。
- 按 flex line 分组，再在目标 line 内计算主轴位置。
- 处理 row/column、reverse、wrap/wrap-reverse。
- 将 `order` 纳入视觉顺序。
- gap 区域映射到相邻插入点。
- 嵌套容器优先接收；只有指针离开其有效区域才交给父容器。
- 现有节点拖动和 palette 新节点使用相同解析器。

### 9.5 拖动提交

- palette -> canvas：`AddNodeCommand`。
- 同容器重排：`MoveNodeCommand`。
- 跨容器移动：`ReparentNodeCommand`。
- 非法目标：不修改模型，显示翻译后的原因。
- drop 后选择新节点，并保持其在画布和 Inspector 中可见。

## 10. 绝对定位

### 10.1 从 Flex 切换到 absolute

切换前读取组件当前 border box 和父容器 padding box：

```text
left = child.borderBox.left - parent.paddingBox.left
top  = child.borderBox.top  - parent.paddingBox.top
```

然后：

- 保存原 parent ID、flow index、order、grow、shrink、basis、align-self。
- 设置 `position:absolute`。
- 默认激活 top + left 锚边，并写入计算出的偏移。
- 保持当前视觉位置，不能出现跳动。

### 10.2 absolute 编辑

- 可在画布直接拖动，更新当前有效锚边。
- 支持 top/right/bottom/left、width/height、z-index。
- 同时设置左右或上下时遵循标准 stretch 计算；Inspector 清楚显示约束关系。
- resize 后跟随锚边更新，不通过隐藏 transform 实现定位。
- 父容器尺寸变化后按 CSS 标准重新布局。

### 10.3 返回 Flex

- 移除 absolute 定位值。
- 恢复保存的 flow 属性。
- 优先恢复原 flow index；若原位置已失效，限制到当前子项范围。
- 一次切换是一条可撤回命令。

## 11. 历史记录与快捷键

命令类型至少包括：

- AddNode、RemoveNode、DuplicateNode。
- MoveNode、ReparentNode。
- UpdateContainerFlex、UpdateComponentProperty。
- ToggleAbsolute、UpdateAbsolutePosition。
- UpdateThemeVariable、ResetThemeGroup。
- RenameNode、UpdateContent。

规则：

- `Ctrl+Z` 撤回，`Ctrl+Y` 与 `Ctrl+Shift+Z` 重做。
- 连续拖动、resize、slider 和颜色拖动在结束时只产生一条记录。
- 文本输入按 focus session 合并，blur/Enter 时提交。
- 新命令提交后清空 redo 分支。
- 历史记录限定合理上限，优先保存结构化差异，不保存整页 DOM 快照。
- undo/redo 后选择状态尽量恢复到受影响节点。
- 工具栏按钮的 enabled 状态与 history 实时同步，按钮 tooltip 使用翻译键。

## 12. 保存、项目格式与导出

### 12.1 两种输出

**保存项目**：

- 保留 `data-ore-node-id`、节点类型和必要的编辑器 metadata。
- 保留 `<meta name="aui-ore-editor" content="1">`。
- 允许下次打开恢复层级、选择器映射、状态样式和主题变量。

**导出干净 HTML**：

- 删除内部 UUID、编辑辅助属性、overlay、ghost 和空状态。
- 保留标准 HTML、class、data 属性、实际业务属性和必要样式。
- 引用 `ore-edit.css` 或选择冻结变量为导出 CSS；对话框中明确说明差异。

### 12.2 写入边界

- 复用并抽象现有安全路径解析思路，但不依赖 DevTools 私有类。
- 只允许 Apricity 目录下的 HTML 与开发环境 resource 写入。
- 生产环境遵循现有 Apricity 可写目录规则。
- resource pack 资源只读。
- 路径规范化后必须验证仍位于允许根目录内。
- 保存采用 UTF-8，并尽量使用临时文件 + replace 的原子写入策略。
- 保存成功后使静态资源缓存失效。

### 12.3 未保存状态

- 任一模型命令后 session 标记 dirty。
- 保存成功记录新的 clean revision。
- 关闭、切换项目或返回 DevTools 前弹出翻译后的未保存确认。
- 错误信息不直接展示底层异常堆栈；日志保留详细异常，UI 展示可行动的本地化信息。

## 13. F12 接入

完成独立编辑器后再接入：

1. 在 DevTools 标题栏增加 Ore/调色板图标按钮。
2. 图标不显示文字，提供 `tooltip.apricityui.ore_editor.open`。
3. 点击时传入当前 target document 的 path 与 UUID。
4. 相同 path 的多个 document 继续以 UUID 前四位区分。
5. 调用类似 `OreEditor.open(currentInspectedDocument)` 的公共入口。
6. Ore Editor 打开后可隐藏或关闭 DevTools；退出编辑器时可选择恢复之前的 DevTools 状态。
7. 没有可编辑 document 时按钮禁用，并通过 tooltip 说明原因。

DevTools 只知道 `OreEditor.open(...)`，不得直接访问编辑器 session、model 或 Inspector。

## 14. 性能预算

- 打开编辑器时不扫描无关 document 的完整 DOM。
- 首次打开只构建当前项目模型与可见 UI。
- palette 定义静态缓存，显示时生成 `TRANSLATION` 节点；不缓存已经解析的语言字符串。
- Inspector 只更新受影响字段，不因一个数值变化重建整个右栏。
- 覆盖层以帧为单位合并更新，同一帧多次 dirty 只绘制一次。
- 拖动期间不序列化 HTML、不写文件、不重建完整模型。
- 大项目可引入画布外节点的渲染裁剪，但不能改变布局测量结果。
- 性能基线：200 个节点可正常选择和编辑；拖动过程跟随渲染帧，无 20 TPS 阶梯感；打开编辑器不出现秒级主线程阻塞。

需要记录的指标：

- 打开耗时、模型构建耗时、首帧可交互时间。
- 单帧 hit-test、overlay 更新和局部 render 耗时。
- 200/500 节点时的内存与历史记录大小。
- 保存和导出耗时。

## 15. 浏览器标准验证矩阵

### 15.1 Flex

- row、column、row-reverse、column-reverse。
- nowrap、wrap、wrap-reverse。
- 所有 justify-content、align-items、align-content 常用值。
- 单行/多行、不同尺寸子项、不同 `order`。
- grow/shrink/basis 与 min/max size 组合。
- row-gap、column-gap、百分比尺寸和 overflow。

### 15.2 Position

- relative 容器中的 absolute 子项。
- top+left、right+bottom、左右同时设置、上下一起设置。
- 固定尺寸与 auto 尺寸。
- 父容器 padding/border 不为零。
- z-index 与 transform/translateZ 参与层叠。
- absolute 与 Flex 子项混合时的插入位置。

### 15.3 输入与交互

- 单击选择、双击、右键、拖动阈值、Esc 取消。
- 快速移入移出不残留 hover。
- 快速拖动不丢 mouseup，不留下 ghost。
- select popup 多次开关位置稳定。
- 输入框 focus 不发生文字位移。
- textarea overflow/resize 符合浏览器行为。
- 滚动条占据布局空间且可拖动。

### 15.4 视觉

- `ore.css` 与默认 `ore-edit.css` 在 Chrome 中逐页截图对比。
- 同一 `ore-edit-example.html` 在 Chrome 和 MC 中截图对比。
- 重点检查边框、inset shadow、active 位移、字体基线、行高、ellipsis、scrollbar 和 tooltip 定位。
- 截图必须使用相同 viewport、相同内容、相同交互状态和相同字体资源。
- 任何明显差异先形成最小复现，再判断是主题 CSS、编辑器 CSS 还是 AUI 底层问题。

## 16. 测试策略

### 16.1 单元测试

- 数据模型父子关系、循环插入保护、根节点约束。
- Flex visual order 与插入 index 解析。
- absolute 切换前后视觉坐标换算。
- history 合并、分支清除、undo/redo 对称性。
- 项目序列化往返一致性。
- 干净 HTML 不包含内部标记。
- 保存路径 traversal、只读资源和生产/开发环境边界。
- 中英文翻译键集合一致，静态与动态本地化 DOM 均为 `TRANSLATION` 元素。

### 16.2 集成测试

- 打开/关闭/重复打开生命周期。
- 从 palette 拖入根容器和嵌套容器。
- 同容器重排与跨容器移动。
- reverse/wrap/order 下的 drop。
- Flex -> absolute -> Flex 的位置和历史恢复。
- Inspector 字段修改映射到模型和 DOM。
- theme variable 修改只影响画布。
- 保存项目后重新打开并恢复。
- 导出 HTML 能由普通 AUI document 正常打开。

### 16.3 人工与截图验收

- 游戏窗口使用最大化，不使用 fullscreen，不固定为 `1280x720`。
- 以中文验证正常使用流程，以英文验证长文本和回退。
- Chrome 与 MC 分别截取 ADD、容器 INSPECT、组件 INSPECT、THEME、拖放、absolute 和弹窗状态。
- 每次底层框架修复后重跑受影响的资源管理器、DevTools、Ore 示例页回归，防止共享布局或事件行为退化。

## 17. 分阶段实施计划

### Phase 0：冻结基线与拆分风险

- [ ] 保存当前 `ore.css` 在 Chrome 与 MC 的各页面截图。
- [ ] 记录 viewport、GUI scale、语言、窗口尺寸和字体加载状态。
- [ ] 建立最小 Flex/absolute/drag 测试页。
- [ ] 列出当前 AUI 对 CSS variable、Flex、absolute、overflow、select、cursor 的支持缺口。
- [ ] 确认当前 dirty worktree 中与编辑器无关的修改，实施期间不回退它们。

完成标准：视觉基线可复现；底层缺口有独立测试，不把已知框架问题混进编辑器实现。

### Phase 1：`ore-edit.css` 变量化

- [ ] 从 `ore.css` 提取可编辑 token，保持选择器和默认视觉不变。
- [ ] 区分基础 token、语义 token和组件 token。
- [ ] 新建 `ore-edit-example.html` 覆盖全部变量组。
- [ ] 自动检查 CSS 中应变量化但仍写死的关键颜色/尺寸。
- [ ] Chrome 像素对比 `ore.css` 与默认 `ore-edit.css`。
- [ ] MC 中复查现有 Ore 浏览器差异清单。

完成标准：未覆盖变量时，两份主题在相同页面中像素一致；变量覆盖不泄漏到编辑器 UI。

### Phase 2：独立编辑器外壳与国际化基础

- [ ] 创建 `ore-editor.html`、`ore-editor.css` 和 Java 包。
- [ ] 让编辑器直接加载 `ore.css`，建立 editor frozen token 与 canvas working token 两个作用域。
- [ ] 建立 Ore UI 复用清单，删除 `ore-editor.css` 中任何重复实现的通用按钮、表单、panel、dialog 与状态样式。
- [ ] 实现 open/close/toggle 与 document 生命周期。
- [ ] 实现固定 toolbar、左右布局、右栏 resize、中央滚动区域。
- [ ] 实现 `OreEditorDom.translation(...)` 与 `OreEditorTranslationKeys`。
- [ ] 修改 Tooltip 的翻译内容渲染，使 `data-tooltip-key` 最终创建 `TRANSLATION` 节点而非提前解析字符串。
- [ ] 扩展仅接收 `String` 的内置 toast、菜单和弹窗接口，使其能以翻译键创建 `TRANSLATION` 节点。
- [ ] 建立首批中英文翻译键和键完整性测试。
- [ ] 为所有图标绑定本地化 tooltip。

完成标准：独立打开编辑器即可看到直接由 Ore UI 组件组成的空画布与双语右栏；HTML 无业务 JS、无写死的最终 UI 文案；静态和动态 DOM 文案均由 `translation` 标签渲染；禁用 `ore.css` 后编辑器通用控件应明确失去主题视觉，以此证明没有复制一套 Ore 皮肤。

### Phase 3：模型、画布投影与选择

- [ ] 实现项目、容器、组件、样式模型。
- [ ] 创建不可删除的根 Flex 容器。
- [ ] 建立 model -> DOM 的稳定映射与局部更新。
- [ ] 实现 canvas hit-test、hover、select 和 breadcrumb/selection 状态。
- [ ] 实现非布局型选择覆盖层。

完成标准：可通过测试数据渲染嵌套容器和组件，hover/选择准确且覆盖层不影响任何布局尺寸。

### Phase 4：ADD palette 与基础拖放

- [ ] 注册容器模板和首批组件定义。
- [ ] Java 动态生成双列 palette。
- [ ] 实现拖动状态机、drag ghost、帧级位置更新。
- [ ] 实现最深容器命中和基础 row/column 插入。
- [ ] 实现 drop 后选中新节点。

完成标准：可把容器和组件拖入根容器或嵌套容器；快速拖动无迟滞、无残留、无系统鼠标调用。

### Phase 5：完整 Flex 算法与辅助层

- [ ] 实现 wrap line 分组。
- [ ] 实现 reverse、wrap-reverse、order。
- [ ] 忽略 absolute 子项。
- [ ] 实现 gap 与插入指示。
- [ ] 实现现有节点重排与跨容器移动。
- [ ] 绘制主/交叉轴、flex line 和 gap overlay。

完成标准：Flex 验证矩阵全部通过；Chrome 与 MC 的视觉顺序和 drop 结果一致。

### Phase 6：容器 Inspector

- [ ] 实现通用字段组件基础层。
- [ ] 实现 direction/wrap segmented control。
- [ ] 实现 alignment 可视化控件。
- [ ] 实现 gap、size、spacing、surface、overflow。
- [ ] 实现字段校验、disabled 原因和翻译 tooltip。
- [ ] 实现局部更新，避免整栏重建。

完成标准：可完全通过右栏配置 Flex 容器；非法值不会污染模型；每项可由键盘操作。

### Phase 7：组件 Inspector 与状态样式

- [ ] 实现组件类型相关 Content 编辑器。
- [ ] 实现 Flex item 属性。
- [ ] 实现 Appearance 与 StateSelector。
- [ ] 实现复制、删除、锁定和层级操作。
- [ ] 首批组件达到可用状态并有默认 Ore markup。

完成标准：首批组件可以创建、编辑内容和视觉状态，导出的标准 HTML 能正确还原。

### Phase 8：绝对定位

- [ ] 实现 visual-position-preserving toggle。
- [ ] 实现 offset、anchor、size 和 z-index 字段。
- [ ] 实现画布直接拖动与 resize。
- [ ] 实现返回 Flex 的 flow index 恢复。
- [ ] 覆盖父 padding/border 与双边约束测试。

完成标准：切换模式不跳位；resize 后组件按标准锚边响应；Flex 与 absolute 往返可撤回。

### Phase 9：THEME 编辑

- [ ] 实现全部变量分组。
- [ ] 实现单项、分组和全局重置。
- [ ] 实现颜色、字体、深度、边框和间距编辑。
- [ ] 保证画布变量不会影响编辑器外壳。
- [ ] 为每项补齐中英文说明。

完成标准：主题变量实时生效，默认重置回到 `ore-edit.css` 基线，所有变化进入历史记录。

### Phase 10：历史、保存与导出

- [ ] 实现 command history 和输入合并策略。
- [ ] 接入撤回/重做快捷键及按钮状态。
- [ ] 实现项目 HTML serializer/deserializer。
- [ ] 实现 clean HTML exporter。
- [ ] 实现安全路径解析、原子写入、确认和未保存提示。
- [ ] 保存/导出 toast 与错误全部翻译。

完成标准：保存项目可无损重开；导出结果不含编辑器内部节点；撤回重做覆盖所有编辑操作。

### Phase 11：F12 入口

- [ ] 添加独立 Ore Editor 图标。
- [ ] 传递当前 document path 与 UUID。
- [ ] 处理无 document、只读 document、相同 path 多实例。
- [ ] 处理 DevTools 隐藏、恢复和编辑器关闭流程。

完成标准：F12 只承担入口职责，移除 DevTools 后 Ore Editor 仍可通过公共 API 独立运行。

### Phase 12：性能与回归

- [ ] 采集 200/500 节点性能数据。
- [ ] 优化帧级命中、overlay 更新和 Inspector 局部刷新。
- [ ] 运行全部 Gradle 测试。
- [ ] 回归资源管理器、DevTools、Ore 示例页、select、tooltip、scrollbar。
- [ ] 完成 Chrome/MC 全状态截图对比。
- [ ] 对底层差异建立独立测试后按浏览器标准修复。

完成标准：功能、性能、视觉与回归门槛全部满足，没有依靠编辑器私有补偿掩盖底层错误。

## 18. 风险与应对

| 风险 | 表现 | 应对 |
| --- | --- | --- |
| CSS variable 支持不完整 | 变量嵌套、fallback 或动态更新错误 | 先建独立 CSS variable 测试，修底层解析与失效传播 |
| Flex 几何信息不足 | overlay 与插入位置猜测错误 | 从最终布局盒与 line 信息建立只读调试接口 |
| 鼠标事件频率受 tick 限制 | 拖动阶梯感、mouseup 丢失 | 接入 ScreenEvent/渲染帧路径，事件只记录最新状态 |
| DOM 全量重建 | 闪白、hover 丢失、输入焦点丢失 | 模型 diff + 局部 DOM patch，稳定复用节点 |
| 编辑变量污染编辑器 | 字体、颜色或尺寸使右栏不可用 | 编辑器 frozen token 与 canvas working token 分层 |
| 编辑器只是模仿 Ore | 控件细节逐渐与主题分叉 | 直接加载 `ore.css` 并复用 Ore class，编辑器 CSS 禁止重做通用控件 |
| HTML 序列化破坏源码 | 属性顺序、head/meta 或样式丢失 | 结构化 serializer + round-trip 测试 + 保存前确认 |
| 中英文键不同步 | 显示键名或混杂英文 | 键集合自动测试，所有 UI 构建只接受翻译 key 并创建 `TRANSLATION` 节点 |
| reverse/order 插入歧义 | 视觉位置与 DOM 位置不一致 | 独立 resolver，使用 visual order 后映射回模型 index |
| absolute 切换跳位 | 开关后组件突然移动 | 基于 parent padding box 计算 offset，提交前后比对矩形 |
| 大项目卡顿 | 打开、拖动、检查时掉帧 | 测量优先，帧合并、局部更新、缓存静态定义 |

## 19. 1.0 完成定义

以下条件必须同时满足，才能标记 Ore Editor 1.0 完成：

- [ ] `ore.css` 未被编辑器需求破坏。
- [ ] 默认 `ore-edit.css` 与 `ore.css` 视觉一致。
- [ ] 编辑器本身直接使用 Ore UI；通用控件来自 `ore.css`，不是 `ore-editor.css` 中的仿制样式。
- [ ] 编辑器独立于 DevTools 实现，F12 仅为入口。
- [ ] HTML 不含业务 JS，业务逻辑全部在 Java。
- [ ] 中文与英文全部可用，静态及动态 DOM 的本地化文案全部使用 `translation` 标签。
- [ ] Flex 容器创建、嵌套、完整属性编辑可用。
- [ ] palette 拖入、重排、跨容器移动可用。
- [ ] reverse、wrap、order、gap 下插入正确。
- [ ] 绝对定位开关、拖动、resize 和恢复 flow 可用。
- [ ] overlay 不参与布局，盒模型与 Flex 提示准确。
- [ ] 组件内容、布局、外观和状态样式可编辑。
- [ ] 全局 Ore 变量可编辑和重置。
- [ ] 所有编辑可撤回和重做。
- [ ] 项目保存可恢复，干净 HTML 导出无内部标记。
- [ ] 写入范围和路径安全测试通过。
- [ ] 200 节点基准下无明显主线程长卡顿，拖动跟随渲染帧。
- [ ] Gradle 测试全部通过。
- [ ] Chrome 与 MC 截图逐项比对完成，差异已修复或记录为有独立复现的底层缺口。
- [ ] 资源管理器、DevTools、Ore 示例页等共享能力回归通过。

## 20. 推荐执行顺序

严格按照以下依赖链推进，不提前把 F12、保存或复杂组件接到不稳定的画布核心上：

```text
视觉基线
  -> ore-edit.css
  -> 独立外壳 + translation 标签体系
  -> 数据模型 + DOM 投影
  -> 选择/命中 + overlay
  -> palette 拖放
  -> 完整 Flex resolver
  -> 容器 Inspector
  -> 组件 Inspector
  -> absolute
  -> theme variables
  -> history
  -> save/export
  -> F12 entry
  -> 性能、截图与全量回归
```

每个 Phase 只有在其“完成标准”满足后才能进入下一阶段。发现浏览器标准差异时，暂停上层功能扩展，为底层框架增加最小复现与回归测试，再恢复路线图。
