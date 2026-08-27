# AUI 纯 CSS 主题规范

本文档是 AUI 自定义主题的**契约文档**。 目标是让主题**无缝切换**：同一份 HTML，换掉 CSS 文件、换作用域类，页面结构与交互状态表现完全一致，无需改动任何标记。

规范分四部分：主题框架、CSS 变量、组件系统、命名规范。

---

## 一、主题框架

### 1.1 目录结构

```
theme/<name>/
  <name>.css     # 入口样式表（唯一必需文件）
  fonts/         # 本地字体（可选，见 1.3）
```

加载方式（路径相对当前文档解析）：

```html
<link rel="stylesheet" href="<name>.css">
<body class="<name>-theme">…</body>
```

### 1.2 作用域类（Scope Class）

每个主题有一个根作用域类，命名为 `.<name>-theme`（如 `.ice-theme`）。
**每一条选择器都必须以作用域类开头**（`@font-face`、`@keyframes`、
`@media` 本身除外，`@media` 内的规则仍要加作用域）。
这保证多个主题的 CSS 可以共存于同一页面、互不污染。

### 1.3 字体（可选）

主题**可以不附带字体**——缺省时全部 `font-family` 声明回退到系统字体，
不影响结构与交互契约。

若附带字体，惯例是分两个角色、各自一个 `@font-face`
（`font-weight: 400; font-style: normal;`）：

| 角色 | 被谁引用 |
|---|---|
| 正文族 | 作用域根块、表单控件、代码 |
| 标题族 | `h1–h6`、`.font-display`、按钮、`.navbar-brand` 等强调处 |

族名用主题命名空间（如 `IceRegular` / `IceDisplay`），并在组件规则里
统一写成 `<族名>, sans-serif` 的兜底形式。字体族名属于主题内部实现，
不进 token、也不进契约——契约只要求"标题/强调处使用标题族"这一结构。

### 1.4 作用域根块（`.<name>-theme` 上的基础样式）

```css
.<name>-theme {
  /* —— 全部变量 token（见第二节）—— */
  min-width: 320px;
  min-height: 100%;
  margin: 0;
  color: var(--ink);
  font-family: <正文族>, sans-serif;
  font-size: var(--font-md);
  line-height: 1.5;
  letter-spacing: 0;
  caret-color: var(--green);
}
```

### 1.5 Reset 与基础排版（必须原样保留的规则组）

- `box-sizing: border-box` 作用于 `.<name>-theme *`（含 `::before/::after`）。
- `h1–h6, p, ul, ol, figure` 的 `margin-top: 0`。
- `h1–h6` 与 `.font-display`：标题族、weight 400、`line-height: 1.15`；
  字号阶梯 h1..h6 = 36 / 28 / 22 / 18 / 16 / 14px，`margin-bottom` 16/14/12/10/8/8。
- `p { margin-bottom: 16px }`、`a` 使用 `--info`、hover 下划线、`strong`、
  `small`/`.text-muted` 使用 `--ink-muted`。
- `code`/`kbd`、`.<name>-code`（pre-wrap 代码块）、`hr`/`.<name>-divider`、
  `img { image-rendering: pixelated }`。

`.<name>-code`、`.<name>-divider(-2)`、`.<name>-swatch*`、`.<name>-page`
是仅有的**带主题前缀的组件类名**（主题命名空间的一部分，见 4.7），
其余组件类一律不带主题前缀。

### 1.6 响应式断点（两个，固定）

- `@media (max-width: 900px)`：栅格 `.col-3 ~ .col-9` 塌成 `span 12`；
  `.navbar` 纵向排列、`.navbar-nav` 横向滚动；`.showcase-hero` 纵向；
  `.inventory-grid` 变 6 列。
- `@media (max-width: 560px)`：容器内边距 12px；h1 降 30px；
  `.grid` 单列、`[class*="col-"]` 全 `span 1`；`.split` 纵向；`.button` 通栏；
  `.inventory-grid` 变 4 列；扩展组件守卫（`.drawer-left/-right` 近全宽、
  `.dropdown` 通栏、`.tooltip-content` max-width 180px、`.banner` 边距收紧）。

新主题必须实现这两个断点，且覆盖同一批组件。

### 1.7 @keyframes（可选）

动画不是契约：组件的默认/显示/隐藏状态必须正确，是否带过渡与动效由主题自定。
若使用 `@keyframes`，动画名是全局命名空间，统一加 `<name>-` 前缀
（如 `ice-spin`），避免跨主题重名。

### 1.8 z-index 分层标尺（固定，跨组件不冲突的前提）

| 层级 | 组件 |
|---|---|
| 9 / 10 | `.sidebar-mask` / `.sidebar` |
| 20 | `.dropdown-options` |
| 21 | `.drawer-overlay` |
| 30 | `.tooltip-content` |
| 40 | `.drawer` |
| 99 / 100 | `.sidebar-2-mask` / `.sidebar-2`、`.toast-area` |
| 900 | `.modal-backdrop` |
| 999 | `.loading-mask` |

### 1.9 引擎约束（AUI 解析器禁用语法）

所有主题必须遵守：

- 禁 `color-mix(`（所有混合色必须预先计算成静态值）；
- 禁 `:has(`、`@layer`、`@import`、`clip-path`；
- 颜色可以写 hex / `rgba()`；渐变可用 `linear-gradient` /
  `repeating-linear-gradient`；
- 步进动画用 `steps()`（如 `<name>-spin 800ms steps(8) infinite`）。

---

## 二、CSS 变量（Token 系统）

### 2.1 三段式结构

```
基础调色板（直接色值）
  → 语义别名（--color-* 用 var() 转发到基础色）
    → 组件 token（--<组件>-<部位/状态>，组件规则一律 var() 引用）
```

组件规则里**不允许出现绕过 token 的主题性颜色决策**；改皮肤 = 只改 token 值。
（少量结构性 rgba 高光/阴影如 `rgba(255,255,255,0.2)` 是组件造型的一部分，
可以直接写在组件规则里，新主题保留即可。）

### 2.2 基础 token（35 个，全部必填）

```css
--ink: #f4f5f7;            /* 主文字 */
--ink-muted: #b6bac1;      /* 次要文字 */
--ink-dark: #191a1c;       /* 浅底上的深色文字 */
--canvas: #202124;         /* 页面底色 */
--surface: #48494a;        /* 卡片/面板表面 */
--surface-deep: #313233;   /* 凹陷表面（输入框底、表头） */
--surface-soft: #d0d1d4;   /* 浅色表面（tertiary 按钮） */
--edge: #1e1e1f;           /* 主描边 */
--edge-light: #77797c;     /* 高光描边 */
--green: #3c8527;          /* 主色（品牌绿） */
--green-hover: #2a641c;
--green-shadow: #1d4d13;
--purple: #7345e5;         /* 次色 */
--purple-hover: #5d2cc6;
--purple-shadow: #4a1cac;
--gold: #f0b92d;
--gold-shadow: #936715;
--red: #b33b31;
--red-hover: #8b2923;
--red-shadow: #662019;
--blue: #2d78a8;
--success: #69ad45;
--warning: #f0b92d;
--danger: #d45b50;
--info: #58a6d2;
--focus: #ffffff;          /* 焦点框 */
--space-1: 4px;
--space-2: 8px;
--space-3: 16px;
--space-4: 24px;
--space-5: 32px;
--font-sm: 13px;
--font-md: 16px;
--font-lg: 20px;
--font-xl: 28px;
```

命名规律：`--<语义>`；成对交互色用 `-hover` / `-shadow`（shadow = 底部压边色）。
间距与字号各 5/4 档，新增档位须全主题同步。

### 2.3 语义别名与尺度 token（必填）

```css
--color-foreground: var(--ink);
--color-muted: var(--ink-muted);
--color-background: var(--canvas);
--color-surface: var(--surface);
--color-surface-deep: var(--surface-deep);
--color-border: var(--edge);
--color-border-light: var(--edge-light);
--color-focus: var(--focus);
--color-primary: var(--green);
--color-primary-hover: var(--green-hover);
--color-primary-active: var(--green-shadow);
--color-secondary: var(--purple);
--color-danger: var(--red);
--size-unit: 2px;          /* 控件几何基准单位 */
--motion-fast: 100ms;      /* 快速过渡时长 */
--radius-sm: 0;            /* 圆角（像素主题为 0） */
--radius-md: 0;
```

### 2.4 兼容层 `--ore-*`（35 个，逐字保留）

历史变量名，**新主题必须原样包含**，以 var() 转发到 2.2 的通用变量：

```css
--ore-ink: var(--ink);
--ore-ink-muted: var(--ink-muted);
--ore-ink-dark: var(--ink-dark);
--ore-canvas: var(--canvas);
--ore-surface: var(--surface);
--ore-surface-deep: var(--surface-deep);
--ore-surface-soft: var(--surface-soft);
--ore-edge: var(--edge);
--ore-edge-light: var(--edge-light);
--ore-green: var(--green);
--ore-green-hover: var(--green-hover);
--ore-green-shadow: var(--green-shadow);
--ore-purple: var(--purple);
--ore-purple-hover: var(--purple-hover);
--ore-purple-shadow: var(--purple-shadow);
--ore-gold: var(--gold);
--ore-gold-shadow: var(--gold-shadow);
--ore-red: var(--red);
--ore-red-hover: var(--red-hover);
--ore-red-shadow: var(--red-shadow);
--ore-blue: var(--blue);
--ore-success: var(--success);
--ore-warning: var(--warning);
--ore-danger: var(--danger);
--ore-info: var(--info);
--ore-focus: var(--focus);
--ore-space-1..5: var(--space-1..5);
--ore-font-sm/md/lg/xl: var(--font-sm/md/lg/xl);
```

注意：`--ore-*` 是**历史兼容层**（唯一的"ore"前缀变量）；新主题自己的
**新变量一律不加主题前缀**，继续用通用名（`--ink`、`--button-…`）。

### 2.5 灰阶与彩色阶梯（直接色值，必填）

```css
--gray-10: #f4f6f9;  --gray-20: #e6e8eb;  --gray-30: #d0d1d4;  --gray-40: #b1b2b5;
--gray-50: #8c8d90;  --gray-60: #58585a;  --gray-70: #48494a;  --gray-80: #313233;
--gray-90: #242425;  --gray-100: #1e1e1f;
--green-30: #6cc349; --green-40: #52a535; --green-50: #3c8527; --green-60: #2a641c; --green-70: #1d4d13;
--red-10: #f46d6d;   --red-30: #df5050;   --red-50: #ca3636;   --red-60: #c02d2d;  --red-80: #ad1d1d;
--blue-10: #8cb3ff;  --blue-20: #2e6be5;  --blue-30: #1452cc;
--yellow-10: #ffe866; --yellow-20: #e5c317;
--orange-20: #d3791f;
--purple-10: #ac90f3;
--gold-vip: #fee039;
```

约定：同色系 `-10/-20/…` 数字越大越深（gray 从近白到近黑）。

### 2.6 通用状态 token

```css
--disabled-background: #d0d1d4;
--disabled-border: #8c8d90;
--disabled-shadow: #b1b2b5;
--disabled-foreground: #48494a;
--overlay: rgba(0, 0, 0, 0.7);        /* sidebar 遮罩 */
--overlay-soft: rgba(0, 0, 0, 0.55);  /* drawer 遮罩 */
```

**所有组件的 disabled 表现统一引用这四个 `--disabled-*`**，
不得各自为政；hover 在 disabled 上不得生效（见 3.2）。

### 2.7 组件 token（按组件分组，全部必填）

命名规律：`--<组件>-<部位>-<属性/状态>`，变体号并入组件名（`--button-primary-2-*`）。
`-hover` = 悬停、`-active` = 按下、`-shadow` = 底部压边、`-highlight` = 顶部高光、
`-foreground` = 前景文字、`-border` = 描边、`-track` = 轨道、`-thumb` = 滑块。

```css
/* 按钮 -2/-3 */
--button-2-border: #1e1e1f;
--button-2-foreground: #ffffff;
--button-primary-2-background: #3c8527;   --button-primary-2-background-hover: #2a641c;
--button-primary-2-background-active: #1d4d13; --button-primary-2-shadow: #1d4d13;
--button-secondary-2-background: #d0d1d4; --button-secondary-2-background-hover: #b1b2b5;
--button-secondary-2-background-active: #b1b2b5; --button-secondary-2-shadow: #58585a;
--button-secondary-2-foreground: #1e1e1f;
--button-danger-2-background: #ca3636;    --button-danger-2-background-hover: #c02d2d;
--button-danger-2-background-active: #ad1d1d;  --button-danger-2-shadow: #ad1d1d;
--button-purple-2-background: #7345e5;    --button-purple-2-background-hover: #5d2cc6;
--button-purple-2-background-active: #4a1cac;  --button-purple-2-shadow: #4a1cac;
--button-primary-3-background: #3c8527;   --button-primary-3-background-hover: #52a535;
--button-primary-3-background-active: #2a641c; --button-primary-3-shadow: #1d4d13;
--button-primary-3-highlight: rgba(255, 255, 255, 0.2);
--button-secondary-3-background: #d0d1d4; --button-secondary-3-background-hover: #f4f6f9;
--button-secondary-3-background-active: #b1b2b5; --button-secondary-3-shadow: #58585a;
--button-secondary-3-foreground: #1e1e1f;
--button-danger-3-background: #ca3636;    --button-danger-3-background-hover: #df5050;
--button-danger-3-background-active: #c02d2d;  --button-danger-3-shadow: #ad1d1d;

/* 卡片 / 面板 -2 */
--card-2-background: #48494a;  --card-2-border: #1e1e1f;  --card-2-highlight: #6c6d6e;
--card-2-shadow: #333334;      --card-2-muted: #d0d1d4;
--panel-2-background: #313233; --panel-2-header-background: #48494a;
--panel-2-border: #1e1e1f;     --panel-2-elevation: rgba(0, 0, 0, 0.25);
--panel-2-muted: #d0d1d4;

/* 输入 -2 */
--input-2-background: #313233; --input-2-border: #1e1e1f;  --input-2-inner-shadow: #242425;
--input-2-caret: #6cc349;      --input-2-invalid: #cf4a4a; --input-2-valid: #6cc349;
--input-2-help: #b1b2b5;

/* 标签页 -2/-3 */
--tab-2-background: #48494a;   --tab-2-background-hover: #67686a;  --tab-2-border: #131313;
--tab-2-active-background: #3c8527; --tab-2-active-background-hover: #2a641c;
--tab-2-active-shadow: #2a641c;
--tab-3-background: #48494a;   --tab-3-background-hover: #58585a;
--tab-3-background-active: #313233;  --tab-3-indicator: #ffffff;

/* 进度条 -2 */
--progress-2-track: #1e1e1f;   --progress-2-border: #131313;
--progress-2-bar: #3c8527;     --progress-2-bar-shadow: #2a641c;
--progress-2-bar-danger: #c33636; --progress-2-bar-danger-shadow: #ad1d1d;

/* 开关 */
--switch-track-background: #8c8d90;  --switch-track-highlight: #a3a4a6;
--switch-track-border: #1e1e1f;
--switch-active-track-background: #3c8527; --switch-active-track-highlight: #639d52;
--switch-thumb-background: #d0d1d4;  --switch-thumb-background-hover: #f4f6f9;
--switch-thumb-background-pressed: #b1b2b5;
--switch-thumb-highlight: #e3e3e5;   --switch-thumb-highlight-hover: #fbfbfd;
--switch-thumb-highlight-pressed: #d0d1d3; --switch-thumb-highlight-focus: #ecedee;
--switch-thumb-shadow: #58585a;      --switch-thumb-border: #1e1e1f;
--switch-thumb-focus-ring: transparent;
--switch-icon: #242425;

/* 复选 / 单选 */
--checkbox-size: 20px;
--checkbox-background: #8c8d90;  --checkbox-background-hover: #b1b2b5;
--checkbox-background-active: #58585a;
--checkbox-checked: #3c8527;     --checkbox-checked-hover: #2a641c;
--checkbox-checked-active: #1d4d13;
--checkbox-border: #1e1e1f;      --checkbox-mark: #ffffff;
--checkbox-2-size: 24px;         --checkbox-2-checked-hover: #52a535;
--radio-dot: #ffffff;            --radio-2-size: 18px;

/* 滑杆 */
--slider-track: #8c8d90;         --slider-track-border: #1e1e1f;
--slider-process: #3c8527;       --slider-segment: #1e1e1f;
--slider-thumb-background: #d0d1d4; --slider-thumb-background-hover: #b1b2b5;
--slider-thumb-shadow: #58585a;  --slider-2-segment-gap: rgba(0, 0, 0, 0.35);

/* 提示气泡 */
--tooltip-background: #1f1f1f;   --tooltip-border: #ffffff;
--tooltip-shadow: #000000;       --tooltip-foreground: #ffffff;
--tooltip-2-background: #2e6be5; --tooltip-2-border: #1e1e1f;

/* 下拉 */
--dropdown-label-background: #d0d1d4; --dropdown-label-background-hover: #b1b2b5;
--dropdown-label-foreground: #1e1e1f;
--dropdown-menu-background: #58585a;  --dropdown-menu-border: #1e1e1f;
--dropdown-option-separator: #8c8d90;
--dropdown-2-menu-background: #1e1e1f;
--dropdown-2-option-background: #313233; --dropdown-2-option-background-hover: #242425;

/* 通知 */
--toast-background: #1f1f1f;  --toast-foreground: #ffffff;
--toast-success: #6cc349;     --toast-warning: #ffe866;
--toast-danger: #f46d6d;      --toast-info: #8cb3ff;  --toast-vip: #fee039;
--toast-2-background: #1e1e1f; --toast-2-secondary: #313233;
--toast-2-dark-foreground: #1e1e1f;

/* 加载 */
--loading-background: #48494a;
--spinner-track: rgba(255, 255, 255, 0.25); --spinner-head: #ffffff;
--loading-error: #c02d2d;

/* 抽屉 */
--drawer-background: #313233;  --drawer-border: #1e1e1f;
--drawer-header-background: #48494a; --drawer-close-hover: #58585a;

/* 图标按钮 */
--icon-button-background: #48494a; --icon-button-background-hover: #58585a;
--icon-button-background-active: #313233; --icon-button-border: #1e1e1f;
--icon-button-highlight: #6c6d6e;  --icon-button-shadow: #333334;

/* 侧边栏 */
--sidebar-width: 238px;    --sidebar-background: #313233;
--sidebar-border: #1e1e1f; --sidebar-item-hover: #58585a;
--sidebar-2-width: 240px;  --sidebar-2-background: #48494a;
--sidebar-2-border: #333334; --sidebar-2-active: #3c8527;

/* 滚动条 */
--scrollbar-width: 22px;       --scrollbar-track: #58585a;
--scrollbar-thumb: #e6e8eb;    --scrollbar-thumb-border: #000000;
--scrollbar-thumb-shadow: #58585a; --scrollbar-thumb-highlight: #f9fafa;
--scrollbar-2-width: 18px;     --scrollbar-2-thumb: rgba(255, 255, 255, 0.4);

/* 标签 / 横幅 / 列表 -2 */
--tag-background: #1e1e1f;  --tag-foreground: #ffffff; --tag-dark-foreground: #1e1e1f;
--banner-neutral: #1e1e1f;  --banner-information: #2e6be5; --banner-important: #ffe866;
--list-2-background: #313233; --list-2-item-hover: #48494a; --list-2-item-active: #242425;
```

另有一个**局部覆盖**模式：`.switch` 在 on/disabled 状态规则内重写 `--switch-icon`，
即"状态规则可以重写组件自己的 token"，这是允许的，但只限组件自身作用域内。

---

## 三、组件系统

### 3.1 编号变体规则

同一组件的不同视觉代际用连字符数字后缀区分，**可叠加在同一代基础类上**：

- 无后缀 = 第一代（如 `.button`、`.tab`、`.progress`、`.tooltip`、`.dropdown`、
  `.checkbox`、`.radio`、`.slider`、`.sidebar`、`.scrollbar`、`.list-group`、`.form-input`）；
- `-2` = 第二代视觉（`.button-primary-2`、`.tab-2`、`.progress-2`、`.checkbox-2`、
  `.radio-2`、`.slider-2`、`.sidebar-2`、`.scrollbar-2`、`.list-group-2`、
  `.form-input-2`、`.card-2`、`.panel-2`、`.tooltip-2`、`.dropdown-2`、`.toast-2`、
  `.tag-2`、`.badge-2`、`.<name>-divider-2`、`.icon-button-2`、`.form-help-2`）；
- `-3` = 第三代视觉（`.button-primary-3`、`.tab-3`）。

用法两种：
1. 完整变体类（自带全部样式）：`<button class="button-primary-2">`。
2. 基础类 + 变体补丁类（继承基础类、局部覆写）：
   `<div class="card card-2">`、`<div class="tooltip tooltip-2">`、
   `<div class="dropdown dropdown-2">`。

新主题必须为三代全部实现，视觉可自由发挥，类名与适用元素不可变。

### 3.2 状态钩子（完整清单）

伪类 / 属性 / 类三种写法**必须等价支持**（同样的视觉、同样的优先级）：

| 状态 | 类 | 属性 / 伪类 |
|---|---|---|
| 悬停 | — | `:hover` |
| 按下 | — | `:active`、`[aria-pressed="true"]`、`[pressed]`（switch） |
| 焦点 | — | `:focus` / `:focus-visible` / `:focus-within`（按组件语义） |
| 选中（checkbox/radio/switch 的 on） | `.on` | `:checked`、`[checked]`、`[aria-checked="true"]`、`[data-state="on"]` |
| 激活（tab/list/nav/page 项） | `.active` | `[aria-selected="true"]`、`[data-state="active"]` |
| 展开（dropdown/tooltip/drawer/toast/sidebar/mask） | `.open` / `.show` | `[data-state="open"]`、`[aria-expanded="true"]`、`details.dropdown[open]` |
| 关闭/隐藏（loading-mask） | `.hidden` | `[data-state="closed"]` |
| 选中项（dropdown-option） | `.selected` | `[aria-selected="true"]` |
| 校验 | `.is-valid` / `.is-invalid` | `[aria-invalid="true"]` |
| 禁用 | `.disabled` | `[disabled]`、`[aria-disabled="true"]` |
| 加载（button） | — | `[data-state="loading"]` |

硬性规则：

1. **disabled 永远赢过 hover/active**——每个组件的禁用规则必须同时列出
   `[disabled]`、`.disabled`、`[aria-disabled="true"]`，**并带 `:hover` 后缀的
   重复一组**，统一引用 `--disabled-*` token。
2. 焦点框统一：`outline: 2px solid var(--color-focus)`，按钮类 `outline-offset: 2px`，
   列表/菜单项 `outline-offset: -2px`；输入类 focus 时同时改 `border-color`。
3. `.switch` 支持 `[color="secondary|destructive|dungeons|legends|realms|gold"]`
   配色属性（只换 on 态轨道与图标色）和 `[variant="icons"]` / `.switch-icons`
   图标变体。

### 3.3 组件清单与结构契约

下面每个组件给出：宿主类（推荐元素）、内部子结构类、说明。
**HTML 结构属于契约**——子类名、层级、状态类挂哪一层都不能改。

**排版**：`h1–h6`、`.font-display`、`p`、`a`、`strong`、`small`/`.text-muted`、
`code`/`kbd`、`.<name>-code`（内含 `code`）、`hr`/`.<name>-divider`/`.<name>-divider-2`、
`.text-left/-center/-right`、`.text-success/-warning/-danger/-info`、
`.font-sm`/`.font-lg`。

**布局**：`.container`（max-width 1180px）、`.container-fluid`、
`.grid` + `.col-1..12`/`.col-full`（12 栅格，gap 16px）、
`.stack`（纵向 flex）、`.cluster`（横向换行）、`.split`（两端分布）。

**导航**：`.navbar` > `.navbar-brand` + `.navbar-nav`（项为 button/a/label）；
`.breadcrumb`（`li + li::before` 出 ">" 分隔，`.breadcrumb-page span` 默认隐藏）。

**按钮**：`.button`（默认主色，等价 `.button-primary`）、`.form-button`、
`.page-button` 共享同一基底（min-height 42px、标题族、text-shadow、
active 时 padding 上下互换 2px 模拟按下）。
变体：`.button-secondary`（紫）、`.button-tertiary`（浅灰）、`.button-danger`（红）、
`.button-normal`（MC 经典灰）、`.button-small`、`.button-wide`。
-2 代（扁平风）：`.button-primary-2` / `-secondary-2` / `-danger-2` / `-purple-2`。
-3 代（单元高光风）：`.button-primary-3` / `-secondary-3` / `-danger-3`。
`[data-state="loading"]`：文字透明化 + `::after` spinner 覆盖（secondary/tertiary
用深色 spinner）。
图标按钮：`.icon-button`（36px 暗色凹面）/ `.icon-button-2`（浅色凸面）。

**表面**：`.card` / `.panel` 同构，子结构 `.-header` / `.-body` / `.-footer`
（如 `.card-header`）；`.card-2`（补丁类）、`.panel-2`（额外有 `.panel-subtitle`）；
`.card-accent-green/-purple/-gold`（改顶边色）；
`.card-2` 内部还有 `.card-description`/`.card-muted`/`.card-media`。

**表单**：`.form-group` > `.form-label` + 控件 + `.form-help`/`.form-help-2`；
控件 `.form-input` / `.form-select` / `.form-textarea`（-2 代：
`.form-input-2` / `.form-select-2` / `.form-textarea-2`）；
`.input-group`（输入 + 按钮横排，`.form-input` flex:1，`.button` min-width 96px）；
`.choice-list` > `.choice`（原生 checkbox/radio + label）；`.choice-row`（-2 代行内标签）。

**选择控件**（全部纯 CSS 绘制、无图片资源）：
- `.switch`（span）> `.switch-control` > `.switch-status` + `.switch-button`；
  几何 56×30（thumb 30×30 + status 26×26），on 态靠 `order` 换侧；
  `.switch-bounce-left` / `.switch-bounce-right` 反馈动画类。
- `.checkbox` 20px / `.checkbox-2` 24px，on 态 `::after` 旋转勾。
- `.radio` 20px 方块 + 白点 / `.radio-2` 18px 菱形（`rotate(45deg)` +
  `::after` 三条纹反向旋转回正）。
- `.slider` 8px 高轨道 > `.slider-process` + `.slider-thumb`（28px）+
  `.slider-segment`（刻度）；`.slider-2` 12px 分段轨道（repeating-linear-gradient）
  > `.slider-2-process` + `.slider-2-thumb`（29px）。thumb 用 `left` 定位 +
  `translate(-50%,-50%)`。

**反馈**：
- `.progress` > `.progress-bar`（`.progress-purple` 变体）；
  `.progress-2`（凹陷轨道）> `.progress-bar`/`.progress-2-bar`，
  变体 `.progress-2-danger`、`.progress-2-indeterminate`（条纹动画）。
- `.tooltip`（span，相对定位）> `.tooltip-content`（绝对定位，默认
  `opacity: 0; visibility: hidden`，hover/focus-within/`[data-state="open"]` 显示）；
  方向类 `.tooltip-bottom` / `.tooltip-left` / `.tooltip-right`；补丁类 `.tooltip-2`。
- `.dropdown`（min-width 200px）> `.dropdown-label`（`::after` 箭头，open 态翻转）
  + `.dropdown-options`（`display: none` → open 时 `block`，max-height 162px 滚动）
  > `.dropdown-option`（`.selected` 出右侧勾）；补丁类 `.dropdown-2`（深色菜单）。
  也支持 `details.dropdown[open]` 原生写法。
- `.toast-area`（fixed 底部居中容器）> `.toast`（默认透明 + 下移 20px，
  `.show`/`[data-state="open"]` 浮入）；色变体 `.toast-success/-warning/-danger/-info/-vip/-debug`；
  `.toast-2` + `.toast-2-secondary/-primary/-informative/-notice/-warning/-realms`。
- `.loading-mask`（fixed 全屏 z-999，`.hidden`/`[data-state="closed"]` 淡出）>
  `.spinner`（32px，`-small` 16px / `-large` 60px）+ `.spinner-text` +
  `.loading-error-text`。
- `.badge` + `.badge-success/-warning/-danger/-purple`；`.badge-2`（6px 方点）+
  `.badge-2-green/-blue/-yellow/-red`。
- `.tag` + `.tag-outlined` + `.tag-primary/-informative/-notice/-warning/-realms`；
  `.tag-2` + `.tag-2-black` / `.tag-2-green/-blue/-yellow/-red`。
  （tag/badge-2/toast-2 的色系变体共享同一组背景色合并选择器。）
- `.banner` + `.banner-information` / `.banner-important`。
- `.alert` + `.alert-success/-warning/-danger/-info`（8px 左边条着色）。

**结构组件**：
- 模态：`.modal-backdrop`（fixed z-900，默认 `display:none`，`.open` → flex）
  > `.modal-dismiss`（absolute inset 0 点击层）+ `.modal` >
  `.modal-header` / `.modal-body` / `.modal-footer`。
  纯 CSS 驱动示例：`#<name>-modal-toggle:checked ~ #exampleModal { display:flex }`。
- `.drawer-overlay` + `.drawer`（fixed z-40，flex 纵向）方向类
  `.drawer-left/-right/-top/-bottom`（关闭时各自 translate 出屏，`.open` 归位）；
  子结构 `.drawer-header` > `.drawer-title` + `.drawer-close`、`.drawer-body`、
  `.drawer-footer`。
- `.sidebar-mask` + `.sidebar`（fixed 左 z-10，关闭 translateX(-105%)）>
  `.sidebar-title`、`.sidebar-divider`、`.sidebar-item`；
  `.sidebar-2-mask` + `.sidebar-2` > `.sidebar-2-header`、`.sidebar-2-item`
  （active 为文字变色而非底变）；`.sidebar-button`。
- `.scrollbar` > `.scrollbar-track` + `.scrollbar-thumb`（22px 宽立体滑块）；
  `.scrollbar-2` > `.scrollbar-2-track` + `.scrollbar-2-thumb`（18px 细条）。

**数据展示**：`.table-wrap` > `.table`（thead/tbody block、tr grid 4 列，
th/td 2px 分隔线，tbody hover 行变色）；`.list-group` > `.list-group-item`
（`.active` 绿底）；`.list-group-2` > `.list-group-2-item`（内凹 hover 分割线）；
`.pagination` > `.page-button`（`.active` 紫底）；`.inventory-grid` > `.slot`
（9×44px 物品格，44px 方槽、白边 hover）；`.<name>-swatch` + 6 个色变体。
`.input-group`、`.choice-list` 见表单节。

**工具类**：`.hidden`（display:none）、`.invisible`、`.w-full`、`.m-0`、
`.mt-1..4` / `.mb-1..4`（4/8/16/24px）、`.p-1..4`（4/8/16/24px）。

### 3.4 示例页机制（非主题契约，可选）

主题的 `example.html` 用纯 CSS 实现七页切换，依赖以下钩子。新主题若想让
同一份 example.html 直接可用，需原样实现；自写示例页则可忽略。
（注意：这一组 `#<name>-page-*` / `#<name>-modal-toggle` 选择器是
**故意不加作用域**的 id 选择器——它们驱动的是结构而非皮肤，
是全规范中 1.2 规则的唯一例外。）

- `.showcase-page-toggle` / `.showcase-modal-toggle`（隐藏的 radio/checkbox，`display:none`）；
- `.<name>-page`（默认 `display:none`）+ `#<name>-page-<页面名>:checked ~ .showcase-main
  .<name>-page[data-page="<n>"] { display: block }`，七个页面：
  foundations / actions / forms / data / feedback / layout / variants；
- `:checked ~ .navbar label[for=…]` 同步导航高亮、
  `:checked ~ .showcase-main [data-breadcrumb=…]` 同步面包屑；
- 布局壳：`.<name>-theme.<name>-showcase`（100vh flex 纵列）> `.navbar` +
  `.showcase-main`（> `.breadcrumb` + 各 `.<name>-page`）；
- 展示装饰：`.showcase-hero`、`.showcase-mark`、`.showcase-section` +
  `.showcase-section-title` + `.showcase-kicker`、`.showcase-footer`。

---

## 四、命名规范

1. **全部小写连字符**（kebab-case）：`.form-input`、`.button-secondary`、
   `.list-group-2-item`；禁止驼峰与下划线。
2. **组件-部位**：子结构 = `<组件>-<部位>`（`.card-header`、`.drawer-close`、
   `.slider-thumb`、`.dropdown-option`）。
3. **组件-变体**：语义变体直接跟在组件名后（`.button-danger`、`.toast-success`、
   `.card-accent-gold`）；颜色变体 `-green/-blue/-yellow/-red/-purple/-gold`。
4. **编号变体**：`-2` / `-3` 后缀表视觉代际（见 3.1），变量侧同样并入
   （`--checkbox-2-size`）。
5. **变量**：`--<语义>`（基础）/ `--color-<语义>`（语义别名）/
   `--<族>-<n>0`（阶梯）/ `--<组件>-<部位>-<属性|状态>`（组件 token）；
   新变量**不带主题前缀**——唯一的例外是 2.4 的冻结兼容层。
6. **状态类**：`.on` `.active` `.open` `.show` `.selected` `.disabled` `.hidden`
   `.is-valid` `.is-invalid`——只准复用这张表，新状态优先落到 `[data-state]`。
7. **主题命名空间**：作用域类 `.<name>-theme`；主题前缀组件类仅限
   `.<name>-code` / `.<name>-divider(-2)` / `.<name>-swatch*` / `.<name>-page`；
   示例页 id 用 `#<name>-page-*` / `#<name>-modal-toggle`；
   keyframes 与字体族名若使用，同样加 `<name>` 前缀（均可选，见 1.3 / 1.7）。
8. **工具类**：`.text-*`、`.font-sm/-lg`、`.w-full`、`.m-0`、`.mt-*`、`.mb-*`、
   `.p-*`、`.hidden`、`.invisible`——短名、无语义色之外的变体。

---

## 五、新主题 checklist

1. 复制任意现有主题的样式表（`theme/<已有名>/<已有名>.css`）为
   `<name>/<name>.css`；
2. 全文替换主题命名空间：作用域类 `.<已有名>-theme` → `.<name>-theme`，
   主题前缀组件类（`.<已有名>-code` 等）、示例页 id（`#<已有名>-page-*` 等）、
   keyframes 前缀一并替换——**只有 2.4 兼容层变量保持原样、逐字不动**；
3. 字体可选：要么删掉 `@font-face` 与 `fonts/`（回退系统字体），
   要么换 `fonts/` 与 `src`、族名改用新命名空间；
4. 逐个改第二节列出的全部 token 值——**不加、不删、不改名**；
5. 需要差异化造型时，在组件规则里只改"结构性"数值（圆角、边框宽度、
   阴影形状），颜色一律继续走 token；
6. 验证：仿照现有主题的样式回归测试（`common` 测试目录）为新主题写一份，
   确认每个组件的计算样式与 token 一致；再用同一份 `example.html` 换主题
   文件目检全部七页。
