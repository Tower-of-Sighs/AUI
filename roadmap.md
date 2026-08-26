# Ore CSS 主题扩充 Roadmap

## 1. 范围与硬约束

### 1.1 本路线负责什么

- 继续以 AUI 内置 Ore 主题为唯一核心和默认样式。
- 在现有 `ore.css` 中追加新的组件样式、状态样式和编号变体。
- 保留现有 HTML class、CSS 自定义属性、字体、尺寸和默认视觉。
- 吸收外部项目有价值的 token、CSS 属性组合、扁平视觉和静态组件样式。
- 为缺少样式的 Tooltip、Dropdown、Toast、Drawer、Loading、Icon Button、Switch、Slider 等补齐 CSS。
- 为同一个组件提供可并存的第二、第三套视觉，而不是切换全局主题。

### 1.2 明确不做什么

- 不拆出新的主题 CSS 文件。
- 不新增全局 `ore-flat`、`ore-modern` 等根节点主题类。
- 不修改或删除现有 `.button`、`.button-primary`、`.card`、`.panel`、`.form-input` 等 class。
- 不重写现有 `--ore-*` 公共变量的含义。
- 不加入 JS、Vue、Lit、Web Components 运行时或音频系统。
- 不把其他项目的完整 CSS、全局 reset 或框架适配层复制进来。
- 不为视觉差异重复维护一套新的业务 DOM 结构。

## 2. 兼容性基线

### 2.1 默认样式是第一套

没有后缀的现有样式视为第一套，行为和视觉保持不变：

```text
.button
.button-primary
.button-secondary
.button-tertiary
.button-danger
.button-normal
.card
.panel
.form-input
.form-select
.form-textarea
.tab
.modal
```

已有页面只加载 `ore.css` 且只使用旧 class 时，渲染结果不得因为本路线新增内容而改变。

### 2.2 新样式使用编号后缀

第二套和第三套样式使用组件级编号，不使用全局开关：

```text
.button-primary-2
.button-primary-3
.card-2
.card-3
.panel-2
.panel-3
.form-input-2
.form-input-3
.tab-2
.tab-3
```

示例：

```html
<button class="button button-primary">Default</button>
<button class="button button-primary-2">Flat</button>
<button class="button button-primary-3">Variant 3</button>
```

编号只代表视觉变体，不代表新的交互行为。没有明显设计差异的组件不强行增加 `-2` 或 `-3`。

### 2.3 现有变量必须继续可用

以下变量及其现有含义属于公共契约，不得删除或改义：

```text
--ore-ink
--ore-ink-muted
--ore-ink-dark
--ore-canvas
--ore-surface
--ore-surface-deep
--ore-surface-soft
--ore-edge
--ore-edge-light
--ore-green
--ore-green-hover
--ore-green-shadow
--ore-purple
--ore-purple-hover
--ore-purple-shadow
--ore-gold
--ore-red
--ore-blue
--ore-success
--ore-warning
--ore-danger
--ore-info
--ore-focus
--ore-space-1..5
--ore-font-sm/md/lg/xl
```

## 3. Token 扩充方案

### 3.1 语义 token 层

在 `.ore-theme` 中新增语义别名，旧变量作为底层兼容值：

```css
.ore-theme {
  --ore-color-foreground: var(--ore-ink);
  --ore-color-muted: var(--ore-ink-muted);
  --ore-color-background: var(--ore-canvas);
  --ore-color-surface: var(--ore-surface);
  --ore-color-surface-deep: var(--ore-surface-deep);
  --ore-color-border: var(--ore-edge);
  --ore-color-border-light: var(--ore-edge-light);
  --ore-color-focus: var(--ore-focus);

  --ore-color-primary: var(--ore-green);
  --ore-color-primary-hover: var(--ore-green-hover);
  --ore-color-primary-active: var(--ore-green-shadow);
  --ore-color-secondary: var(--ore-purple);
  --ore-color-danger: var(--ore-red);

  --ore-size-unit: 2px;
  --ore-motion-fast: 100ms;
  --ore-radius-sm: 0;
  --ore-radius-md: 0;
}
```

这些新变量只能补充语义，不得让旧变量失效。

### 3.2 组件变体 token

每个变体通过组件 token 描述完整状态，避免把颜色和阴影散落在规则中：

```text
--ore-button-primary-2-background
--ore-button-primary-2-background-hover
--ore-button-primary-2-background-active
--ore-button-primary-2-foreground
--ore-button-primary-2-border
--ore-button-primary-2-shadow
--ore-button-primary-2-highlight
--ore-button-primary-2-disabled-background
--ore-button-primary-2-disabled-foreground
```

其他组件遵循同样模式：

```text
--ore-card-2-background
--ore-card-2-border
--ore-card-2-shadow
--ore-panel-2-background
--ore-input-2-background
--ore-input-2-focus-border
--ore-tab-2-active-background
```

### 3.3 token 来源取舍

| 来源 | 纳入内容 | 处理方式 |
| --- | --- | --- |
| 内置 Ore | 现有颜色、字体、间距、阴影 | 原样保留，作为第一套默认值 |
| Katorlys | 语义颜色、状态色、focus、disabled、loading token | 改写成 `--ore-*`，不使用 `color-mix()` |
| Spectrollay | 扁平边框、表面、按钮阴影、控件尺寸 | 转为 `-2` 组件 token |
| ParaOre | Sass 中的间距、边框、Scrollbar 尺寸 | 只提取实际使用的静态值 |
| mcui | Panel、Drawer、Loading、Toast、Icon 的局部颜色 | 转为独立组件 token |

### 3.4 已弃用编辑器变体

- 旧版编辑器专用样式已弃用并删除，不再作为资源或兼容入口发布。
- 所有主题 token 和组件样式统一维护在 `ore.css`，业务页面只应引用该文件。
- 不再新增或维护编辑器专用变量及其测试；已有页面应迁移到 `--ore-*` token。

## 4. 组件扩充清单

### 4.1 按钮

- [ ] 保留所有现有按钮类型和状态规则。
- [ ] 增加 `.button-primary-2`、`.button-secondary-2`、`.button-danger-2` 等扁平变体。
- [ ] 只有存在真实视觉差异时才增加 `-3`。
- [ ] 补齐 hover、active、focus、disabled、loading 的 token 和属性规则。
- [ ] 不复制新的按钮 DOM 结构。

### 4.2 表面组件

- [ ] 为 `.card`、`.panel` 增加 `-2` 扁平变体。
- [ ] 复用现有 header、body、footer 结构。
- [ ] 统一边框、内高光、内阴影和外阴影 token。
- [ ] 检查长标题、长文本和窄窗口下的溢出。

### 4.3 表单组件

- [ ] 为 `.form-input`、`.form-select`、`.form-textarea` 增加扁平变体。
- [ ] 补齐 hover、focus、disabled、valid、invalid 状态。
- [ ] 统一 placeholder、caret、selection 和 help text 颜色。
- [ ] 保持现有 `.form-group`、`.form-label`、`.form-help` 兼容。

### 4.4 选择和导航

- [ ] 补充 Checkbox、Radio、Switch、Slider 的完整静态状态。
- [ ] 为 `.tab` 增加 `-2` 变体，支持 `.active` 和 `[aria-selected="true"]`。
- [ ] 补充 Dropdown 展开状态和选中状态样式。
- [ ] 保持 `.navbar`、`.breadcrumb`、`.pagination` 的旧结构。

### 4.5 反馈组件

- [ ] 增加 Tooltip 的 hover/focus-within 样式。
- [ ] 增加 Toast/Pop 的 success、warning、danger、info 变体。
- [ ] 增加 Loading Mask 和 Spinner 的静态样式。
- [ ] 增加 Drawer 的 open、closed、overlay、header、body、footer 状态。
- [ ] 增加 Icon Button 的 normal、hover、active、disabled 样式。

### 4.6 布局和 Minecraft 扩展

- [ ] 选择性补充 Sidebar 和 Scrollbar 的第二套样式。
- [ ] 只保留有实际用途的紧凑布局规则。
- [ ] 如需要，增加 Minecraft 文本格式化后的颜色和装饰 class。
- [ ] Icon 只处理静态尺寸、对齐、像素化和 hover，不处理图标加载逻辑。

## 5. 状态属性规范

所有新样式必须同时考虑伪类、HTML 属性和 ARIA 状态：

```text
:hover
:active
:focus-visible
:checked
[disabled]
[aria-disabled="true"]
[aria-pressed="true"]
[aria-selected="true"]
[aria-expanded="true"]
[aria-invalid="true"]
[data-state="open"]
[data-state="active"]
[data-state="loading"]
details[open]
```

### 5.1 状态优先级

统一采用以下优先级：

```text
默认
  -> hover
  -> active / pressed
  -> focus-visible
  -> selected / expanded
  -> disabled
```

disabled 状态必须覆盖 hover 和 active，不能出现禁用按钮仍然变色或下沉的情况。

### 5.2 CSS-only 边界

- CSS 只负责显示状态。
- 状态由已有页面逻辑、原生控件或外部调用设置。
- CSS 不实现 Modal、Tabs、Dropdown 的状态切换逻辑。
- 不用隐藏的 JS、内联脚本或音频资源弥补 CSS 缺口。

## 6. 外部样式移植规则

### 6.1 必须移植的内容

- 可独立使用的颜色、边框、阴影、尺寸和排版属性组合。
- 与现有 Ore DOM 结构兼容的组件规则。
- 具有明确视觉差异的 Flat 组件样式。
- AUI 解析器能够处理的伪类、媒体查询和属性选择器。

### 6.2 直接舍弃的内容

- 全局 `body`、`html`、`main`、`a`、`button` reset。
- 框架运行时、Vue/Lit 组件、事件处理代码。
- 只为原项目页面结构服务的固定 ID 和全局布局。
- 与内置 Ore 视觉和功能重复的按钮、卡片、表单样式。
- 音频、浏览器存储、网络加载和第三方资源依赖。

### 6.3 AUI CSS 兼容要求

- [ ] 展平外部 `@import`。
- [ ] 不保留未处理的 `@layer`。
- [ ] 不使用 `color-mix()`，预先写成静态颜色。
- [ ] 不使用 `:has()`。
- [ ] 所有新增选择器挂在 `.ore-theme` 下。
- [ ] 不引入新的全局 reset。
- [ ] 检查渐变、阴影、变量、伪类和媒体查询由 AUI 解析器支持。
- [ ] 外部字体和图片不重复打包，复用现有 Ore 资源。

## 7. 实施阶段

### Phase 0：现状冻结与清单

- [ ] 记录 `ore.css` 当前公开 class、token、媒体查询和组件结构。
- [ ] 为现有组件建立默认计算样式基线。
- [ ] 标记不可改变的旧版 class 和变量。
- [ ] 建立外部主题组件、token、状态的对照表。

完成标准：能够证明新增规则不会改变未使用新 class 的旧页面。

### Phase 1：公共 token 扩充

- [ ] 在 `.ore-theme` 中加入语义 token 别名。
- [ ] 为按钮、卡片、面板、输入框、Tabs 定义组件变体 token。
- [ ] 保留旧变量原值和原含义。
- [ ] 禁止 `color-mix()`、未展开 `@layer` 和不支持的现代语法。
- [ ] 运行 Ore 主题扩充回归测试。

完成标准：新组件规则只依赖 token，旧页面计算样式保持一致。

### Phase 2：按钮和表面变体

- [ ] 实现按钮 `-2` 变体。
- [ ] 评估并实现必要的 `-3` 变体。
- [ ] 实现 Card、Panel、Input 的 `-2` 变体。
- [ ] 完整覆盖 hover、active、focus、disabled。
- [ ] 检查按钮文字、图标、长文本和窄窗口布局。

完成标准：同一 HTML 只替换 class 后可以得到不同视觉，不需要新 DOM。

### Phase 3：缺失组件样式

- [ ] Tooltip
- [ ] Dropdown
- [ ] Toast/Pop
- [ ] Drawer
- [ ] Loading/Spinner
- [ ] Icon Button
- [ ] Switch、Slider、Radio、Checkbox
- [ ] 第二套 Scrollbar 和 Sidebar

完成标准：每个新增组件都有默认、hover、active、focus、disabled 或 open 状态规则，并有 HTML 示例。

### Phase 4：扁平样式扩充

- [ ] 将 Spectrollay 的扁平视觉转换为编号组件样式。
- [ ] 将 Katorlys 的状态层次转换为现有组件 token。
- [ ] 将 ParaOre 的紧凑尺寸用于明确的 `-2` 或 `-3` 组件，而不是全局覆盖。
- [ ] 将 mcui 的 Panel、Drawer、Loading、Toast 静态样式转换为 AUI class。
- [ ] 删除所有重复或没有明显差异的变体。

完成标准：Flat 视觉只能通过组件 class 选择性使用，不能改变其他组件。

### Phase 5：响应式和可读性

- [ ] 检查 320px、560px、900px、1200px 宽度（媒体查询与固定尺寸约束）。
- [ ] 检查按钮、标签、表格单元格和表单提示文字是否溢出（`overflow-wrap`/`max-width`）。
- [ ] 检查 `-2`、`-3` 变体是否改变固定尺寸组件的布局稳定性。
- [ ] 检查 Tooltip、Toast、Drawer 在窄窗口的边界。
- [ ] 检查文本颜色和 focus ring 的对比度。

完成标准：新增变体不引起布局跳动、文字遮挡或不可见 focus 状态。

### Phase 6：示例和文档

- [ ] 在现有 Ore 示例页中增加变体展示区域。
- [ ] 每个新增 class 给出最小 HTML 示例。
- [ ] 记录编号含义、适用组件和可覆盖 token。
- [ ] 记录哪些属性由外部逻辑设置，CSS 本身不负责切换。
- [ ] 更新 `docs/guide/ore-theme.md` 的 token 和组件清单。

完成标准：开发者只看 `ore.css` 和主题文档就能知道默认样式与编号变体的用法。

### Phase 7：回归与发布

- [ ] 运行主题相关 Gradle 测试（覆盖 CSS 语法、组件 selector 及状态）。
- [ ] 检查旧版示例页和新变体示例页。
- [ ] 检查 CSS 解析日志，不保留未处理 at-rule 或未知关键语法。
- [ ] 对新增 token 做默认值和覆盖值测试。
- [ ] 对新增组件做 disabled、focus、hover、active、open 状态测试。
- [ ] 检查资源路径、字体路径和本地资源加载。
- [ ] 在发布说明中列出新增 class 和 token，不改变旧版 API。

完成标准：旧页面无回归，新样式可按 class 使用，`ore.css` 可被 AUI 正常加载和解析。

## 8. 目标文件边界

```text
common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/
  ore.css              # 唯一扩充文件
  example.html         # 增加变体和状态示例
  readme.md            # 更新使用说明
```

不新增 Flat 专用 CSS 文件，不新增 JS 文件，不新增音频资源。

## 9. 完成定义

以下条件全部满足后，主题扩充才算完成：

- [ ] 现有 `.ore-theme` 页面视觉和计算样式不变。
- [ ] 所有旧 class 和旧 `--ore-*` token 继续有效。
- [ ] 新变体全部写入 `ore.css`，通过编号 class 使用。
- [ ] 至少完成按钮、Card、Panel、Input、Tab 的 `-2` 变体。
- [ ] 只保留具有明确价值的 `-3` 变体。
- [ ] 新增 Tooltip、Dropdown、Toast、Drawer、Loading 等缺失 CSS。
- [ ] 新增状态覆盖 hover、active、focus、disabled、selected、expanded 或 loading。
- [ ] 所有新规则使用 token，不把外部硬编码颜色散落到组件中。
- [ ] 不存在全局 `ore-flat` 或其他全局皮肤开关。
- [ ] 不包含 JS、Vue、Lit、音频或第三方运行时。
- [ ] AUI CSS 解析器能够处理所有新增语法。
- [ ] 示例、文档和回归测试覆盖默认样式及编号变体。
- [ ] 删除已弃用的编辑器样式、示例和专用测试，文档入口统一为 `ore.css`。

## 10. 推荐执行顺序

```text
冻结旧版 class 和 token
  -> 建立组件/token/状态对照表
  -> 扩充语义 token
  -> button-primary-2 / button-primary-3
  -> card-2 / panel-2 / form-input-2
  -> tooltip / dropdown / toast / drawer / loading
  -> switch / slider / radio / checkbox / scrollbar
  -> 响应式与可读性回归
  -> 示例与文档
  -> AUI 解析和主题测试
```

每一阶段都必须以“新增 class 才产生新增效果、旧 class 不产生变化”为验收原则。没有明确设计收益的组件和 token 不纳入实现。
