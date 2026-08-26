# 内置 Ore 主题

Ore 是框架自带的纯 CSS 主题：MC 风格像素边框、深色石材表面、绿/紫/金强调色，适合设置页、编辑器、容器界面这类操作型 UI。**它只管样式**——点击、切换、提交、状态管理都是你自己的 JS/Java 的事。

## 接入

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
    <button class="button button-primary">Apply</button>
</body>
```

- href 开头的 `/` 是 AUI 逻辑资源根，不是磁盘根；
- 所有规则以 `.ore-theme` 为作用域，不影响根节点之外的 UI；
- 自带两个本地字体（`OreRegular` 正文、`OreDisplay` 标题/控件），不依赖网络，加载失败回退系统字体；
- 默认深色画布背景、16px 字号、box-sizing 传播，但不会帮你铺满屏幕——要满屏自己加 `min-height:100vh`；
- 许可证 MPL-2.0，随模组分发时保留主题目录里的 `license.txt`。

展示页 `apricityui/theme/ore/example.html` 可以直接用 `new ApricityScreen(...)` 打开，六个页面把所有组件都演示了一遍——**先看它再看本文的类名清单**，比读表格直观。

## 设计 Token

所有可调参数是 `.ore-theme` 上的 `--ore-*` CSS 变量。业务页面引用 token 而不是硬编码颜色，这样在 Ore 编辑器里能统一调：

| 分组 | Token |
| --- | --- |
| 文字 | `--ore-ink`（主文字 #f4f5f7）、`--ore-ink-muted`、`--ore-ink-dark` |
| 表面 | `--ore-canvas`（页面底 #202124）、`--ore-surface`、`--ore-surface-deep`、`--ore-surface-soft`、`--ore-edge`、`--ore-edge-light`、`--ore-focus` |
| 操作色 | `--ore-green`（主操作）+ `-hover`/`-shadow`、`--ore-purple`（次级）+ 同、`--ore-gold`、`--ore-red`（危险）+ 同、`--ore-blue` |
| 状态色 | `--ore-success` / `--ore-warning` / `--ore-danger` / `--ore-info` |
| 间距 | `--ore-space-1..5` = 4/8/16/24/32px |
| 字号 | `--ore-font-sm/md/lg/xl` = 13/16/20/28px |

覆写方式（挂在主题根或自己的类上）：

```css
.custom-screen {
    --ore-green: #4b9f32;
    --ore-space-3: 18px;
}
```

**公共契约只有 `.ore-theme`、`--ore-*` 和下面列出的组件类**。`ore-edit.css` 里的 `--ore-edit-*` 是生成细节，别依赖。

## 组件类速查

**布局**：`.container`（居中，max 1180px）/ `.container-fluid`；`.grid` 十二列 + `.col-1..12` / `.col-full`；`.stack`（纵向 12px）、`.cluster`（横向换行 10px）、`.split`（两端对齐）。

**导航**：`.navbar` + `.navbar-brand` + `.navbar-nav`（活动项自己维护 `.active`）；`.breadcrumb`（自动加 `>` 分隔）。

**按钮**：`.button` 默认绿色主操作；变体 `.button-primary/-secondary`（紫）/`tertiary`（浅色）/`-danger`（红）/`-normal`（原版灰）/`-small`/`-wide`。`:hover`/`:active`/`[disabled]` 状态齐全，但禁用要用真实 `disabled` 属性。

**卡片/面板**：`.card` / `.panel` 等价，拆 `.card-header` / `-body` / `-footer`；顶部色条 `.card-accent-green/-purple/-gold`。

**表单**：`.form-group` / `.form-label` / `.form-help` / `.form-input` / `.form-select` / `.form-textarea` / `.input-group`（输入框+按钮横排）；校验态 `.is-valid` / `.is-invalid`（只改边框，错误文案自己写）；radio/checkbox 用 `.choice-list` + `.choice` 包裹。

**数据展示**：

- `.table-wrap` + `.table`——**内置表格按四列 grid 布局**，列数不对要在自己的 CSS 里覆写 `tr` 的 `grid-template-columns`，thead/tbody 保持一致；
- `.badge` + `.badge-success/-warning/-danger/-purple`；
- `.alert` + `.alert-success/-warning/-danger/-info`（只有左侧色条，没图标没关闭按钮）；
- `.progress` > `.progress-bar`（宽度自己设 style），`.progress-purple` 变体；
- `.list-group` > `.list-group-item`（`.active` 绿色）。

**MC 风格**：`.inventory-grid`（九列 44px 格）+ `.slot`。**这只是视觉方格**，真实槽位用容器系统的 `<slot>`，见[容器文档](container)。

**Tabs / Modal / 分页**：`.tabs` + `.tab`（`.active` 紫边）、`.modal-backdrop.open` + `.modal` + `-header/-body/-footer`、`.pagination` + `.page-button`。同样**只有样式**：切换面板、开关 modal、Escape/遮罩点击、页码逻辑全部自己写。要现成的弹窗行为用 Java 侧的 [DialogWindow](ui-library)。

**工具类**：`.text-left/-center/-right`、`.text-success/-warning/-danger/-info/-muted`、`.font-sm/-lg/-display`、`.hidden`、`.invisible`、`.w-full`、`.m-0`、间距 `mt/mb/p-1..4`（4/8/16/24px）。

**响应式**：内置 900px 和 560px 两个断点（网格折叠、navbar 纵向、按钮全宽等）。复杂表格和固定宽度弹窗在小窗口下自己检查。

## Ore 编辑器

`ore-edit.css` 是 token 化的主题变体（从 ore.css 生成），配合可视化编辑器用，**普通页面用 ore.css 就行**，别两个都引。

Java 侧入口 `OreEditor`：`open()` / `toggle()` / `close()` / `isOpen()` / `getDocument()` / `openHtml(Path)`（只接受本地文件，资源包 HTML 不能直接编辑保存）。

编辑器 THEME 面板把 35 个可编辑 token 分五组（Typography/Surfaces/Actions/Feedback/Spacing），调色带 Alpha 滑块，非法值标 `is-invalid` 不应用；支持单 token/分组/全部重置，进 Undo/Redo 历史。

**保存与导出**：

- 项目存 `<游戏目录>/apricity/ore-projects/untitled.ore.json`（theme 覆盖 + 文档元数据，编辑器装饰节点不写入）；
- 导出 HTML 到同目录：token 覆盖写进 body 内联 style，head 自动补 `ore-edit.css` 引用，导出物是**不依赖编辑器的普通 AUI 页面**；
- `openHtml` 导入时会剥掉 `<script>`（不当编辑器代码执行），body 内联 style 里 `--ore-*` 开头的属性会被读成主题覆盖——想让导入器认你的主题值，写 body inline style 里。

## 自定义建议

- 业务样式挂在 `.ore-theme` 作用域下，继续用 token；
- 覆写组件时把 `:hover`/`:active`/`[disabled]` 一起补，只改默认背景会出现状态不一致；
- 业务 CSS 在 ore.css 之后引入，同优先级覆写；
- 覆写 token 用合法 CSS 值（颜色任意 CSS 颜色表达式，间距带单位）。

## 常见问题

**没效果**：三查——link 路径对不对、顶层有没有 `class="ore-theme"`、资源重扫了没。

**字体不像 MC**：主题目录的 `fonts/` 资源没解析到，看日志。有颜色但字体不对基本就是它。

**按钮点了没反应 / Modal 关不掉 / Tab 不切换**：预期行为，Ore 是纯 CSS。交互自己写，或用 [内置 UI 库](ui-library)。

**表格列错位**：内置 table 是四列 grid，见上面的覆写说明。

**编辑器改的颜色导出后没了**：导出 HTML 的 body 内联 style 和 ore-edit.css 引用别手删，`ore-theme` 类别替换掉。
