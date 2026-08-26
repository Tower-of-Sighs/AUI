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

展示页 `apricityui/theme/ore/example.html` 可以直接用 `new ApricityScreen(...)` 打开，七个页面把所有组件都演示了一遍——**先看它再看本文的类名清单**，比读表格直观。

## 设计 Token

所有可调参数是 `.ore-theme` 上的 `--ore-*` CSS 变量。业务页面引用 token 而不是硬编码颜色，这样才能统一调整：

| 分组 | Token |
| --- | --- |
| 文字 | `--ore-ink`（主文字 #f4f5f7）、`--ore-ink-muted`、`--ore-ink-dark` |
| 表面 | `--ore-canvas`（页面底 #202124）、`--ore-surface`、`--ore-surface-deep`、`--ore-surface-soft`、`--ore-edge`、`--ore-edge-light`、`--ore-focus` |
| 操作色 | `--ore-green`（主操作）+ `-hover`/`-shadow`、`--ore-purple`（次级）+ 同、`--ore-gold`、`--ore-red`（危险）+ 同、`--ore-blue` |
| 状态色 | `--ore-success` / `--ore-warning` / `--ore-danger` / `--ore-info` |
| 语义别名 | `--ore-color-foreground` / `--ore-color-primary`、`--ore-size-unit`（2px）、`--ore-motion-fast`（100ms） |
| 灰阶 | `--ore-gray-10..100`（#f4f6f9 → #1e1e1f 十档） |
| 色相阶梯 | `--ore-green-30..70`、`--ore-red-10..80`、`--ore-blue-10..30`、`--ore-yellow-10/20`、`--ore-orange-20`、`--ore-purple-10`、`--ore-gold-vip` |
| 禁用态 | `--ore-disabled-background` / `-border` / `-shadow` / `-foreground` |
| 遮罩 | `--ore-overlay`（0.7 黑）、`--ore-overlay-soft`（0.55 黑） |
| 间距 | `--ore-space-1..5` = 4/8/16/24/32px |
| 字号 | `--ore-font-sm/md/lg/xl` = 13/16/20/28px |

每个编号变体组件还有自己的一组 token（如 `--ore-button-primary-2-background` / `-hover` / `-active` / `-shadow`、`--ore-switch-width`、`--ore-tooltip-background` 等），命名规律是 `--ore-<组件>-<属性>`，直接在 ore.css 顶部 `.ore-theme` 块里查。

覆写方式（挂在主题根或自己的类上）：

```css
.custom-screen {
    --ore-green: #4b9f32;
    --ore-space-3: 18px;
}
```

**公共契约只有 `.ore-theme`、`--ore-*` 和下面列出的组件类**。旧版编辑器专用的 `ore-edit.css` 已弃用并删除，主题入口统一为 `ore.css`。

## 组件类速查

**布局**：`.container`（居中，max 1180px）/ `.container-fluid`；`.grid` 十二列 + `.col-1..12` / `.col-full`；`.stack`（纵向 12px）、`.cluster`（横向换行 10px）、`.split`（两端对齐）。

**导航**：`.navbar` + `.navbar-brand` + `.navbar-nav`（活动项自己维护 `.active`）；`.breadcrumb`（自动加 `>` 分隔）。

**按钮**：`.button` 默认绿色主操作；变体 `.button-primary/-secondary`（紫）/`tertiary`（浅色）/`-danger`（红）/`-normal`（原版灰）/`-small`/`-wide`。`:hover`/`:active`/`[disabled]` 状态齐全，但禁用要用真实 `disabled` 属性。编号变体：`-2` 扁平系（`.button-primary-2/-secondary-2/-danger-2/-purple-2`，2px 边框 + 底部内阴影），`-3` 单元斜面系（`.button-primary-3/-secondary-3/-danger-3`，顶部高亮条 + 底部厚阴影）；`data-state="loading"` 自带方块步进 spinner。`.icon-button` / `.icon-button-2` 是 36px 方形图标按钮。

**卡片/面板**：`.card` / `.panel` 等价，拆 `.card-header` / `-body` / `-footer`；顶部色条 `.card-accent-green/-purple/-gold`。变体 `.card-2`（katorlys 斜面 + 错位投影，配 `.card-description`）、`.panel-2`（mcui 面板，配 `.panel-subtitle`）。

**表单**：`.form-group` / `.form-label` / `.form-help` / `.form-input` / `.form-select` / `.form-textarea` / `.input-group`（输入框+按钮横排）；校验态 `.is-valid` / `.is-invalid`（只改边框，错误文案自己写）；radio/checkbox 用 `.choice-list` + `.choice` 包裹。编号变体 `.form-input-2` / `.form-select-2` / `.form-textarea-2` / `.form-help-2`（扁平深色、白色聚焦框、`[aria-invalid]` 红框）。

**选择控件**（纯 CSS 绘制，无图片资源）：

- 开关三兄弟：`.switch`（58px 渐变轨道）/ `.switch-2`（56px 双状态半块）/ `.switch-3`（52px 紧凑），内部都是 `<span class="switch-thumb">`（-2 另加两个 `.switch-2-status`）；开态用 `.on`、`:checked`、`[aria-checked="true"]` 或 `[data-state="on"]` 任一；`.switch-bounce-left/-right` 是回弹动画修饰类。
- `.checkbox`（20px）/ `.checkbox-2`（24px 描边款）、`.radio`（圆点）/ `.radio-2`（菱形旋转 45°），勾选用 `.on` / `:checked` / `[aria-checked="true"]`；禁用加 `.disabled` 或 `[disabled]`。
- `.slider`（8px 轨道 + `.slider-process` + `.slider-thumb`，可加 `.slider-segment` 刻度）/ `.slider-2`（12px 分段轨道），进度和位置用内联 `style="width:..%" / "left:..%"` 自己设。

**数据展示**：

- `.table-wrap` + `.table`——**内置表格按四列 grid 布局**，列数不对要在自己的 CSS 里覆写 `tr` 的 `grid-template-columns`，thead/tbody 保持一致；
- `.badge` + `.badge-success/-warning/-danger/-purple`；圆点款 `.badge-2` + `.badge-2-green/-blue/-yellow/-red`；
- 标签 `.tag` + `.tag-primary/-informative/-notice/-warning/-realms`（可加 `.tag-outlined`）；色块款 `.tag-2` + `.tag-2-green/-blue/-yellow/-red/-black`；
- `.alert` + `.alert-success/-warning/-danger/-info`（只有左侧色条，没图标没关闭按钮）；横幅 `.banner` + `.banner-information/-important`；
- `.progress` > `.progress-bar`（宽度自己设 style），`.progress-purple` 变体；变体 `.progress-2`（凹槽轨道）+ `.progress-2-danger` / `.progress-2-indeterminate`（不定态条纹动画）；
- `.list-group` > `.list-group-item`（`.active` 绿色）；变体 `.list-group-2` > `.list-group-2-item`（mcui 斜面行）；
- 分隔线 `.ore-divider` / `.ore-divider-2`（内阴影双线）。

**反馈组件**：

- 提示气泡 `.tooltip` > `.tooltip-content`，`:hover` / `:focus-within` / `[data-state="open"]` 任一触发，方向 `.tooltip-bottom/-left/-right`（默认上）；蓝底变体 `.tooltip-2`；
- 下拉 `.dropdown` > `.dropdown-label` + `.dropdown-options` > `.dropdown-option`（`.selected` 带对勾），展开用 `.open` / `[data-state="open"]` / `:focus-within` / `details.dropdown[open]`，箭头随 `[aria-expanded="true"]` 翻转；深色菜单变体 `.dropdown-2`；
- 通知 `.toast`（进栈用 `.toast-area` 包裹，单条展示加 `.show` / `[data-state="open"]`）+ `.toast-success/-warning/-danger/-info/-vip/-debug`；描边款 `.toast-2` + `.toast-2-secondary/-primary/-informative/-notice/-warning/-realms`；
- 加载 `.loading-mask`（全屏遮罩，`.hidden` / `[data-state="closed"]` 收起）> `.spinner`（`.spinner-small/-large`）+ `.spinner-text` / `.loading-error-text`；
- 抽屉 `.drawer` + `.drawer-left/-right/-top/-bottom`（`.open` / `[data-state="open"]` 展开，配 `.drawer-overlay` 遮罩）> `.drawer-header`（`.drawer-title` + `.drawer-close`）/ `-body` / `-footer`。

**MC 风格**：`.inventory-grid`（九列 44px 格）+ `.slot`。**这只是视觉方格**，真实槽位用容器系统的 `<slot>`，见[容器文档](container)。

**Tabs / Modal / 分页**：`.tabs` + `.tab`（`.active` 紫边）、`.modal-backdrop.open` + `.modal` + `-header/-body/-footer`、`.pagination` + `.page-button`。同样**只有样式**：切换面板、开关 modal、Escape/遮罩点击、页码逻辑全部自己写。要现成的弹窗行为用 Java 侧的 [DialogWindow](ui-library)。Tab 编号变体：`.tab-2`（暗色斜面，`.active` / `[aria-selected="true"]` / `[data-state="active"]` 绿底）、`.tab-3`（底部 2px 指示条）。

**侧栏 / 滚动条**：`.sidebar`（238px 左侧滑入，`.open` 展开，配 `.sidebar-mask`）> `.sidebar-title` / `.sidebar-divider` / `.sidebar-item`；变体 `.sidebar-2`（240px 浅色底、`.active` 绿字，配 `.sidebar-2-mask` / `.sidebar-2-header` / `.sidebar-2-item`）；`.sidebar-button` 是侧栏里的斜面按钮。自定义滚动条 `.scrollbar`（22px 斜面滑块）/ `.scrollbar-2`（18px 半透明细条），内部 `.scrollbar-track` + `.scrollbar-thumb`（-2 同名），滑块高度用内联 style 设。

**工具类**：`.text-left/-center/-right`、`.text-success/-warning/-danger/-info/-muted`、`.font-sm/-lg/-display`、`.hidden`、`.invisible`、`.w-full`、`.m-0`、间距 `mt/mb/p-1..4`（4/8/16/24px）。

**响应式**：内置 900px 和 560px 两个断点（网格折叠、navbar 纵向、按钮全宽等）。复杂表格和固定宽度弹窗在小窗口下自己检查。

## 状态约定

新组件统一吃两套状态写法，按你的宿主环境选：

- 属性态：`:checked`、`[disabled]` / `[aria-disabled="true"]`、`[aria-pressed]`、`[aria-selected="true"]`、`[aria-expanded="true"]`、`[aria-invalid="true"]`、`[aria-checked="true"]`、`[data-state="open|closed|on|off|active|loading"]`、`details[open]`；
- 类名态：`.on` / `.active` / `.show` / `.open` / `.disabled` / `.hidden`，用于纯静态标记或 AUI 不方便挂属性的场景。

禁用态（`[disabled]`、`.disabled`、`[aria-disabled]`）优先级最高，会盖掉 `:hover` / `:active`。

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
