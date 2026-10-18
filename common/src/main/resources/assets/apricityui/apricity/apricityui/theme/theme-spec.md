# AUI 纯 CSS 主题规范

## 0. 三条原则

1. **接口固定，实现自由**。固化的只有三样：`--*` token 名、组件类名、状态钩子。
   怎么画随你——伪元素、渐变、`mask`、`clip-path`、`filter`、动画、布局手法都不限制。
2. **换皮肤 = 换样式表 + 换作用域类**。同一份 HTML，标记一行不改；交互状态的表现必须等价。
3. **契约分两层**：
   - **核心层**：所有主题都必须实现，用它的页面可以一键切换；
   - **扩展层**：编号变体（`-2`/`-3`）和各主题自己的装饰件，自由发挥，用它们的页面不保证跨主题。

参考实现：`theme/t01-ore/`（本规范的第一个符合者）。

---

## 1. 命名与作用域

**目录与入口**：`theme/<name>/<name>.css`，唯一必需文件；字体放同目录 `fonts/`。

**主题名必须是合法 CSS 标识符**：小写 kebab-case，**不能数字开头**。
`.01-ore-theme` 在标准 CSS 里是非法选择器，浏览器会整条丢弃（AUI 的解析器因为用 `[\w-]` 匹配类名所以能接受，但没必要踩这个坑——`t01-ore` 就是这么来的）。

**作用域类** `.<name>-theme` 挂在根元素上。**每条选择器都要以它开头**（`@font-face` / `@keyframes` / `@media` 本身除外，`@media` 内的规则同样要加）。
唯一例外：驱动结构而非皮肤的选择器（如展台的 `#<name>-modal-toggle:checked ~ …`）可以不带作用域。
这样一个页面里可以共存多个主题，互不污染。

**画布底色由页面铺，不由主题铺**。主题的作用域根块只设 `color` 等文字类属性，**不要写 `background`**——主题也可能用在 HUD / 悬浮层文档里，那些场景不该铺底。
页面自己写 `background-color: var(--canvas)`（用 token，不要写死颜色），这样换主题时底色才会跟着换。预览台 `t00-example.html` 和 `t01-ore/example.html` 都是这么做的。

**例外：整幅画布底纹，只允许落在文档根上**。有的风格（如 `t13-vaporwave` 的深紫渐变 + 网格地平线）本身就以"整页底纹"为识别特征，只给 `--canvas` 一个纯色不够用。这类主题可以铺底，但要同时满足四条，才不会伤到上面那条规则保护的对象：

1. 选择器**限定文档根**——`html.<name>-theme` / `body.<name>-theme`，不许写成裸的 `.<name>-theme`。作用域类挂在普通元素上时（HUD、浮层、预览盒）依旧透明无底；
2. 底纹走 token（`--canvas-art` / `--canvas-art-size` / `--canvas-art-repeat`），组件与页面都不硬编码；
3. 再提供一个**块级钩子**（如 `.vapor-canvas`），让任意一块单独拿到同一套底纹；
4. 页面里那行 `background-color: var(--canvas)` 保留——它是兜底色，主题缺席时页面仍然成立。

底纹层只能用 `linear-gradient` / `repeating-linear-gradient`（引擎没有 radial/conic，§7）。想让"渐变"跟随文档高度时别用 `100% 100%`（长文档会把渐变拉成一片死色），改用**首尾同色的 `repeating-linear-gradient` + 固定 px 周期**：任意高度都无缝平铺，视觉一致。

**字体（可选）**：惯例是两个角色各一个 `@font-face`（`font-weight: 400; font-style: normal;`）——正文字族给作用域根块和表单控件，标题族给 `h1–h6`、`.font-display`、按钮。族名属于主题内部实现，不进契约。引擎的 `@font-face` **只读 font-family 和第一个 `src` 的 url()**，`format()` 会被忽略。

---

## 2. Token 契约

命名规律：`--<语义>` → `--color-<语义>` → `--<组件>-<部位>-<状态>`，组件规则引用 token，不绕过去硬编码主题色。

### 2.1 必填：基础色（26）

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
--green: #3c8527;  --green-hover: #2a641c;  --green-shadow: #1d4d13;
--purple: #7345e5; --purple-hover: #5d2cc6; --purple-shadow: #4a1cac;
--gold: #f0b92d;   --gold-shadow: #936715;
--red: #b33b31;    --red-hover: #8b2923;    --red-shadow: #662019;
--blue: #2d78a8;
--success: #69ad45;  --warning: #f0b92d;  --danger: #d45b50;  --info: #58a6d2;
--focus: #ffffff;   /* 焦点框 */
```

成对交互色用 `-hover`（悬停）/ `-shadow`（底部压边色）。

### 2.2 必填：尺度与语义别名

```css
--space-1..5: 4px / 8px / 16px / 24px / 32px;
--font-sm|md|lg|xl: 13px / 16px / 20px / 28px;
--color-foreground: var(--ink);        --color-muted: var(--ink-muted);
--color-background: var(--canvas);     --color-surface: var(--surface);
--color-surface-deep: var(--surface-deep);
--color-border: var(--edge);           --color-border-light: var(--edge-light);
--color-focus: var(--focus);
--color-primary: var(--green);         --color-primary-hover: var(--green-hover);
--color-primary-active: var(--green-shadow);
--color-secondary: var(--purple);      --color-danger: var(--red);
--size-unit: 2px;                      /* 控件几何基准 */
--motion-fast: 100ms;                  /* 快速过渡时长 */
--radius-sm: 0;  --radius-md: 0;       /* 像素主题为 0，圆角主题自己给 */
```

### 2.3 必填：灰阶、色相阶梯、禁用、遮罩

```css
--gray-10..100: #f4f6f9 #e6e8eb #d0d1d4 #b1b2b5 #8c8d90 #58585a #48494a #313233 #242425 #1e1e1f;
--green-30|40|50|60|70: #6cc349 #52a535 #3c8527 #2a641c #1d4d13;
--red-10|30|50|60|80:   #f46d6d #df5050 #ca3636 #c02d2d #ad1d1d;
--blue-10|20|30:        #8cb3ff #2e6be5 #1452cc;
--yellow-10|20:         #ffe866 #e5c317;
--orange-20: #d3791f;  --purple-10: #ac90f3;  --gold-vip: #fee039;
--disabled-background: #d0d1d4;  --disabled-border: #8c8d90;
--disabled-shadow: #b1b2b5;      --disabled-foreground: #48494a;
--overlay: rgba(0,0,0,0.7);      --overlay-soft: rgba(0,0,0,0.55);
```

阶梯编号越大越深。**所有组件的禁用态统一引用这四个 `--disabled-*`**，不许各自为政。

### 2.4 组件 token

命名 `--<组件>-<部位>-<状态>`，变体号并入组件名（`--button-primary-2-shadow`，`--checkbox-2-size`）。

| 后缀 | 含义 | 后缀 | 含义 |
| --- | --- | --- | --- |
| `-hover` | 悬停 | `-border` | 描边 |
| `-active` | 按下 | `-foreground` | 前景文字 |
| `-shadow` | 底部压边 | `-track` / `-thumb` | 轨道 / 滑块 |
| `-highlight` | 顶部高光 | `-fill` | 填充 |
| `-background` | 背景 | `-icon` | 图标线条色 |

规则：

- 组件的**主题色决策一律走 token**，禁止绕过 token 写主题性颜色；改皮肤 = 只改 token 值；
- 结构性高光/阴影（如 `rgba(255,255,255,.2)` 这种造型的一部分）可以直接写在组件规则里；
- **新 token 不加主题前缀**，用通用名。
- 主题内部可以重写自己组件的 token（如 `.switch.on { --switch-icon: #fff }`），但只限组件自身作用域。

### 2.5 主题自带的辅助 token（推荐）

契约之外可以用**通用名**自由增加 token（不加主题前缀）。做第二个主题时先把这几类定义好，组件规则一律引用，
以后换深度语言/换字族只改这几个值：

| 用途 | 建议 token | 为什么需要 |
| --- | --- | --- |
| 深色或彩色底上的文字 | `--ink-on-dark` | 浅色主题里"白字"不能写死 `#fff`，要能一处改 |
| **亮**彩色底上的文字 | `--ink-on-accent` | 强调色比正文浅时（深色方案的常见做法，如 Modrinth 深色把品牌绿从 `#00af5c` 提到 `#1bd96a`）彩色底上的字要翻成深色，和 `--ink-on-dark` 取值相反，一个 token 覆盖不了两个角色 |
| 深度语言 | `--elev-ring`（如 `0 0 0 1px` 环）、`--elev-whisper`（柔和投影） | 组件规则统一引用，换"环 / 投影 / 斜角"只改这里 |
| 深色面 | `--deep-dark`、`--dark-surface` | 代码块、深色章节、小芯片 |
| 字体族 | `--font-display` / `--font-body` / `--font-mono` | 把"标题族 / 正文字族"这条契约变成可引用的变量 |

**"彩色底上的文字"是两个角色，不是一个。** `--ink-on-dark` 管的是"深色面（代码块、深色芯片）上的字"；
强调色填充上的字是另一回事——强调色一旦比正文浅（深色方案常这么做，为了在小字上够亮），它上面就得写**深色**
字。预览台的工具栏 `.t00-tab.active` 就踩过这个：它写死 `#fff` 配 `var(--green)`，主题换成亮绿强调色后
白字只有 1.88:1。定义 `--ink-on-accent` 之后这类"外壳跟着主题走"的地方才有一处可改的落点。

**字体栈必须自带 CJK 回退**。只写 `Georgia` 这类拉丁衬线时，中文标题会落到系统 CJK 字体（通常是雅黑/黑体这类
无衬线），"衬线标题"的设定在中文页面里直接失效。标题栈里至少补 `"Noto Serif SC", "Source Han Serif SC",
"Songti SC", SimSun, STSong`。

**换色要按"角色"换，不能按 hex 批量替换**。同一个 hex 在不同位置承担不同角色：`#1e1e1f` 既可能是描边
（`--edge`）也可能是深色文字（某个 `--*-foreground`）；`#ffffff` 既可能是浅色面、也可能是深底上的文字。
按 hex 全局替换会把"深色文字"换成奶油色，页面立刻出现成片看不见的字。正确顺序是：**先把组件规则里的硬编码色
全部 `var(--…)` 化，再改 token 定义**。

---

## 3. 核心层：组件与最小标记

**这张表是契约的核心。**它规定每个组件"最少要有多少标记"——主题可以多接受（忽略多余子元素），但**不许要求比这更多**。

| 组件 | 最小标记 | 补充 |
| --- | --- | --- |
| 开关 | `<span class="switch"></span>` | 文本可直接跟在元素里；`[variant="icons"]`、`[color="secondary\|destructive\|dungeons\|legends\|realms\|gold"]` |
| 复选 / 单选 | `<span class="checkbox"></span>` / `.radio`（`-2` 同） | 勾选标记自己画，不要图片资源 |
| 滑杆 | `<div class="slider" style="--value:40%"></div>` | `--value` 同时驱动填充与滑块；刻度 `.slider-segment` 可选；配套件：`.slider-value`（数值胶囊）、`.slider-scale`（两端标签）、`.slider-plain`（只显滑块、不填充） |
| 进度 | `<div class="progress" style="--value:82%"></div>` | `.progress-purple` 变体 |
| 按钮 | `<button class="button">` | 变体 `.button-primary/-secondary/-tertiary/-danger/-normal/-small/-wide`；加载态 `[data-state="loading"]` |
| 图标按钮 | `<button class="icon-button"></button>` | |
| 卡片 / 面板 | `.card` + `.card-header/-body/-footer` | 子块可选；顶部色条 `.card-accent-green/-purple/-gold` |
| 表单 | `.form-group > .form-label` + `.form-input/-select/-textarea` + `.form-help` | 校验态 `.is-valid`/`.is-invalid`、`[aria-invalid="true"]` |
| 输入组 | `.input-group > .form-input + .button` | |
| 选项组 | `.choice-list > .choice`（内含原生 `input` + 文本） | `.choice input` 必须显式设置尺寸、margin 和主题 `accent-color`，不能漏成浏览器默认蓝色；保留原生选择和禁用行为 |
| 表格 | `.table-wrap > .table > thead/tbody > tr > th/td` | 必须是真 `<table>`（引擎没有表格布局）；**列数由标记决定，不设上限** |
| 列表 | `.list-group > .list-group-item`（`.active`） | |
| 徽章 | `.badge`（单元素） | `.badge-success/-warning/-danger/-purple` |
| 标签 | `.tag`（单元素） | `.tag-primary/-informative/-notice/-warning/-realms`、`.tag-outlined` |
| 横幅 | `.banner`（单元素） | `.banner-information/-important` |
| 提示条 | `.alert`（单元素） | `.alert-success/-warning/-danger/-info` |
| 导航 | `.navbar > .navbar-brand` + `.navbar-nav` | 活动项 `.active` |
| 面包屑 | `.breadcrumb > 任意子元素` | 分隔符由 CSS 生成，不写进标记 |
| 标签页 | `.tabs > .tab`（`.active`） | 切换逻辑自己写 |
| 分页 | `.pagination > .page-button`（`.active`） | |
| 模态 | `.modal-backdrop(.open) > .modal-dismiss` + `.modal > .modal-header/-body/-footer` | `.modal-dismiss` 是点击遮罩关闭的载体，必须是真元素 |
| 抽屉 | `.drawer(.open)` + `-left/-right/-top/-bottom` > `.drawer-header`(> `.drawer-title` + `.drawer-close`)/`-body`/`-footer`，配 `.drawer-overlay` | 遮罩必须是独立元素：伪元素做不到（见 §7） |
| 通知 | `.toast-area > .toast`（`.show`） | `.toast-success/-warning/-danger/-info/-vip/-debug` |
| 加载 | `.loading-mask`（自带转圈，可空） | `.spinner` / `.spinner-text` / `.loading-error-text` 可选 |
| 提示气泡 | `.tooltip > .tooltip-content` | 气泡里必须是真文本：引擎没有 `attr()`。默认隐藏，支持悬浮、焦点和显式展开；气泡宽度不能被短触发器挤窄，四方向均以触发器定位 |
| 下拉 | `.dropdown > .dropdown-label` + `.dropdown-options > .dropdown-option` | 支持 `.open` / `.show` / `[data-state="open"]` / `[aria-expanded="true"]` / `:focus-within` / `details[open]`；展开箭头同步转向，选中项自绘勾选标记；短标签不能挤窄菜单，长列表须限高滚动。引擎没有 `details`/`summary` 原生展开行为，`open` 属性由页面设置 |
| 物品格 | `.inventory-grid > .slot` | 纯视觉方格；真实槽位用容器系统的 `<slot>` |
| 侧栏 | `.sidebar` + `.sidebar-mask` > `.sidebar-title` / `.sidebar-divider` / `.sidebar-item` | 遮罩同样必须独立 |
| 设置行 | `.settings-row`（容器 + 控件，文字直接写在容器里） | 成组时用 `.settings-list` 合并相邻描边；可选 `.settings-label` / `.settings-help` / `.settings-control`。说明在标签下方，控制列里的滑杆须获得可用宽度，激活行须有可见反馈 |
| 菜单 | `.menu` > `.menu-item` | 另有 `.menu-label` / `.menu-separator` / `.menu-shortcut`；活动 `.active` / `[aria-selected="true"]`，禁用 `.disabled` / `[disabled]` / `[aria-disabled="true"]` |
| 互斥按钮组 | `.segmented` > `.segmented-item` | `.toggle-group` / `.toggle-item` 是同一组件的别名；选中 `.active` / `[aria-pressed="true"]` / `[aria-selected="true"]` |
| 折叠面板 | `.accordion` > `.accordion-item` > `.accordion-header` + `.accordion-body` | 展开 `.open` / `[data-state="open"]`；箭头由 CSS 画 |
| 键值列表 | `.kv` > `.kv-row` > `.kv-key` + `.kv-value` | 值自动贴右，行间自带分隔线 |
| 快捷栏 | `.hotbar` > `.slot`（沿用现有槽位类） | 选中格 `.active`（白环：`outline` + `outline-offset` 或 `box-shadow` 都行，前者更贴合"环"的语义）；堆叠数 `.slot-count` |
| 空状态 | `.empty-state` | 可选 `.empty-state-icon` / `-title` / `-text` / `-actions` |
| 骨架屏 | `.skeleton` + `.skeleton-title` / `-text` / `-block` / `-circle` | 扫光只能用 `transform` 动画（白名单外的属性会跳变） |
| 代码块 | `.code-block`（`pre` 或 `div`，内部 `code` 退掉行内皮肤） | 取代主题私有的 `<name>-code` |
| 带标签分隔线 | `.divider-label`（文字就是元素内容） | 两侧线由伪元素撑开，不需要额外元素 |
| 数值加减 | `.stepper` > `.stepper-button` + `.stepper-value` + `.stepper-button` | |
| 树 | `.tree` > `.tree-item` + `.tree-group(.open)` | 缩进靠嵌套 `.tree-group`；可选的空 `.tree-toggle` 必须自绘箭头，收起向右、展开向下，与文字对齐；不能套用关闭按钮方框样式 |
| 统计块 | `.stat` > `.stat-value` + `.stat-label` | 大数字用标题字族 |
| 连体按钮 | `.button-group` > `.button` | 后继按钮去掉左边框（引擎把负 margin 钳成 0，不能靠压边） |
| 纵向标签页 | `.tabs-vertical` > `.tab` | 活动指示条在左边，与横向 tab 的顶部指示条对应 |
| 可删除标签 | 现有 `.tag` + `.tag-close` | |
| 尺寸档 | `.modal-sm` / `.modal-lg`、`.drawer-sm` / `-lg` / `-short` / `-tall` | 只改尺寸，弹窗默认 520 |
| 工具条 | `.toolbar` > 任意控件 | 可选 `.toolbar-separator`（分隔线）与 `.toolbar-spacer`（把后面的项推到最右） |
| 步骤条 | `.steps` > `.step`（内含 `.step-number` + 可选 `.step-label`） | 状态 `.done` / `.active`；连接线由 CSS 生成，标记保持平铺 |
| 进度标签 | 现有 `.progress` 里加 `.progress-label` | 绝对居中、`pointer-events: none`；`.progress` 自身带 `position: relative` |
| 分裂按钮 | `.split-button` > `.button` + `.split-button-arrow` | 空 `.split-button-arrow` 必须自绘箭头，不要求页面填入箭头字符；接缝靠去掉主按钮的右边框（引擎把负 margin 钳成 0） |
| 顶部加载条 | `.loading-bar`（单元素，不定态） | 扫光只用 `transform` 动画 |
| 可关闭标签 | 现有 `.tab` 里加 `.tab-close` | 与 `.tag-close` 同款 |
| 稀有度色 | `--rarity-common` / `-uncommon` / `-rare` / `-epic` + `.text-common` / `-uncommon` / `-rare` / `-epic` | MC 物品名配色 |

**只兜底"页面躲不掉"的那几样**（核心层）：真实滚动条（`scrollbar-width` —— AUI 接受长度值用来定粗细 —— + `scrollbar-color` + `::-webkit-scrollbar*`）、placeholder（`:placeholder-shown`，**没有 `::placeholder`**）、选中文字底色（引擎扩展属性 `selection-color`）、`[contenteditable]` 的输入框皮肤。这四样无论页面怎么写都会出现，所以主题必须兜。

**独立原生 checkbox / radio / range 不要求完整重绘**：引擎主要通过 `accent-color` 定制其强调色，部分内部外观仍是固定的（range 轨道、未勾选的 checkbox / radio 底色与描边、number 的 spinner、select 弹出层与 `optgroup`）。需要完整主题造型时，使用 `.slider` / `.checkbox` / `.radio` / `.form-input` 等组件。

**`.choice input` 是明确的例外**：选项组契约本来就使用原生 input，主题必须提供尺寸与强调色，不能以“不兜底原生控件”为由遗漏。可参照 `t01-ore` 设置 `width: 20px; height: 20px; margin: 0; accent-color: var(--green)`；尺寸与配色可按主题调整，不要为上色破坏原生勾选、单选互斥、键盘和禁用行为。

`.scrollbar` / `.scrollbar-2` 这类自绘滚动条仍属扩展层，另一个主题可以不实现。

**常用组合别再造新类**：按键绑定行 = `.settings-row` + `kbd`；物品提示 = `.tooltip` + `.kv`；确认框 = `.modal` + `.button-group`；通知堆 = `.toast-area` + `.toast`；状态点 = `.badge-2`；工具条里的按钮 = `.toolbar` + `.icon-button`。

---

## 4. 状态钩子

**属性/伪类写法与类名写法必须等价**（同样的视觉、同样的优先级）：

| 状态 | 属性 / 伪类 | 类名 |
| --- | --- | --- |
| 悬停 / 按下 | `:hover` / `:active`、`[aria-pressed="true"]` | — |
| 选中（开关/复选/单选） | `:checked`、`[checked]`、`[aria-checked="true"]`、`[data-state="on"]` | `.on` |
| 激活（tab/列表/导航/分页项） | `[aria-selected="true"]`、`[data-state="active"]` | `.active` |
| 展开（下拉/气泡/抽屉/通知/侧栏/遮罩） | `[data-state="open"]`、`[aria-expanded="true"]`、`details[open]` | `.open` / `.show` |
| 收起（加载遮罩） | `[data-state="closed"]` | `.hidden` |
| 选中项（下拉 option） | `[aria-selected="true"]` | `.selected` |
| 校验 | `[aria-invalid="true"]` | `.is-valid` / `.is-invalid` |
| 禁用 | `[disabled]`、`[aria-disabled="true"]` | `.disabled` |
| 加载（按钮） | `[data-state="loading"]` | — |

硬性规则：

1. **禁用永远赢过 hover / active**。禁用规则要同时列出 `[disabled]`、`.disabled`、`[aria-disabled="true"]`，并补一组 `:hover` 后缀的重复选择器；
   - 重复的那一组要**覆盖被 hover 改过的每一个属性**（颜色、背景、阴影、`transform`），否则从参考实现继承来的
     hover 规则会漏过来：实践中遇到过"禁用滑块的圆钮在 hover 时描边变色"，就是守卫只写了 `transform` 和
     `box-shadow`、没写 `border-color`；
   - 断言方式：hover 一个禁用控件，前后计算样式应当**逐字段相同**；
   - **伪元素按用途处理**：滑块、单选圆点可以填色；箭头、勾线和文字勾号的背景应保持透明，只调整描边或文字色。
     不得用统一的 `::before/::after { background-color: … }` 把所有禁用图标填成色块。组件修正规则也要覆盖三种禁用钩子及其 `:hover`，并检查宿主和伪元素；
2. **焦点态是硬要求**：每个可交互组件都要有焦点环，**用 `outline` 画**（引擎已实现，见 §7）。几个必须知道的坑：
   - `outline` 画在边框盒**外侧**，不占布局也不影响盒模型，所以焦点规则**不必再重复组件自己的 `box-shadow`**——
     这正是过去用阴影画环时最难受的地方（`box-shadow` 是整条列表替换）。带斜角变体的 `.button-*-2` / `-3`
     现在只要加一行 `outline`，原来的斜角阴影原样保留；
   - 间距和内嵌环都用 `outline-offset` 表达：外环 `outline-offset: 0`；要"留白环"就写 `outline-offset: <gap>px`
     并把宽度减去 gap（`0 0 0 2px <底>, 0 0 0 5px <focus>` ⇔ `outline: 3px solid <focus>; outline-offset: 2px`）；
     要内嵌环就写负值（`inset 0 0 0 2px <focus>` ⇔ `outline: 2px solid <focus>; outline-offset: -2px`）。
     列表、菜单、树这类整行控件习惯用内嵌环，按钮和方块类内外都行，同一主题内保持一致即可；
   - `outline` **不在 transition 白名单里**（切换直接跳变，这与焦点环该有的即时感一致），所以别往 transition 里写
     `outline`；也不要在任何地方写 `outline: none`——那是自断焦点环；
   - **宿主必须可聚焦**。用 `div` 承载 `.menu-item` / `.tree-item` / `.accordion-header` / `.slot` 这类交互件时要加 `tabindex`，或直接改用 `<button>`——否则键盘用户根本碰不到，环也永远不会出现；
3. 只在状态真的存在时给视觉反馈，不要用 `:hover` 假装选中；
4. **在已有 `transform` 上叠加**：滑块、气泡这类靠 `translate(-50%,-50%)` 居中的元素，加缩放必须写进同一个声明
   （`transform: translate(-50%,-50%) scale(1.18)`）；只写 `scale()` 会把元素甩出原位；
5. **状态要覆盖内部标记**：展开菜单时箭头同步转向，选中 option 时出现勾选标记；设置行的激活框与内部控件的选中态必须能同时辨认。
   加载按钮要在普通、深色、hover 和禁用状态下检查转圈是否可见，不能把主描边色直接当作所有背景上的图标色。

---

## 5. 工具类与响应式

工具类名固定：`.text-left/-center/-right`、`.text-success/-warning/-danger/-info`、`.font-sm/-lg/-display`、`.hidden`、`.invisible`、`.w-full`、`.m-0`、`.mt-1..4` / `.mb-1..4`（4/8/16/24px）、`.p-1..4`。
布局件固定：`.container`（居中限宽）/ `.container-fluid`、`.grid` 十二列 + `.col-1..12`、`.stack`（纵）、`.cluster`（横换行）、`.split`（两端对齐）。

断点值不强制，但**主题必须在窄容器下可用**。惯用 `900px` 与 `560px`（`t01-ore`/`ore` 都用这对），沿用便于页面自己写断点覆写。注意 `@media` 只支持 `min/max-width|height`、`orientation` 和 `and`。

---

## 6. 明确不限制的部分

- **DOM 结构**：除 §3 的最小标记外，随便加包裹层、随便用伪元素顶掉子元素；
- **绘制手法**：多层背景、`mask`、`clip-path`、`filter`、`transform`、`@keyframes` 随你用；
- **参数传递**：没有子元素可挂的地方，用内联自定义属性传参（`style="--value:40%"`）。伪元素能继承宿主的自定义属性，这是"零结构"的正规手段；
- **编号变体**：`-2` / `-3` 是扩展位，视觉自由；也可以完全不提供；
- **圆角、边框宽度、阴影形状**等结构性数值自由（颜色仍走 token）；
- **动画**：是否带过渡与动效由主题自定，不属于契约。

**差异化不等于换色。** 只改 token 值不算另一个主题——同名组件在结构上会高度重合。同样自由、而且**应该发挥**的还有：

- **结构**：标题是独立色带还是活在留白里、表格有没有竖向格线、hover 是填充还是只变色、提示是左侧粗色条还是
  淡色底加同色淡描边、浮层是深色条还是浅色弹层、进度是厚槽还是细胶囊；
- **排版**：哪个字族用在哪些组件（标题族给标题与展示位，控件与正文走正文字族），标题阶梯与行高；
- **交互**：hover / 按下 / 焦点的表现方式（例如滑块 hover 做成"放大 + 阴影扩散"而不是变色）；
- **几何**：圆角尺度、间距节奏、控件高度。

**够不够不一样要客观判断**：拿同一个组件在两个主题下各取一次计算样式签名与截图，比对差异字段与像素差异
百分比。**每个组件都该有实质差异**；只剩背景色不同、尺寸与结构全同的组件，就是还没做完（方法见 §9）。

---

## 7. 引擎能力与坑（写主题前必读）

权威清单见仓库文档 `docs/guide/html-css-coverage.md`，这里只列**最容易被主题作者踩到**的：

| 坑 | 事实 | 对策 |
| --- | --- | --- |
| `outline` | 支持（画在边框盒外、不占布局，线型只画实线） | 焦点框直接用它，`outline-offset` 可为负 |
| `text-shadow` | **不支持** | 文字立体感只能用伪元素叠层或跳过 |
| `border-style` / `outline-style` | 不支持，全部画成实线 | dashed/dotted 直接用 |
| `calc()` | **只支持加减**，不能乘除嵌套 | 数值提前算好 |
| 渐变 | 只有 `linear-gradient` / `repeating-linear-gradient` 和 `url()`；**无 radial/conic** | 复杂形状优先 `mask` / `clip-path` |
| `content` | 只支持**单个引号字符串**；无 `attr()` / `counter()` / 多段拼接 | 动态文案必须有真元素 |
| 伪元素 | 每元素仅 `::before` + `::after` | 需要第三层就保留元素或改用背景分层 |
| `transition` | 可动画属性是白名单：opacity、宽高、transform、filter、color、background-color、四边、margin/padding、border-width、border-radius、box-shadow | **`visibility` 不在名单里**，淡入淡出用 opacity |
| `:focus-visible` | 退化为 `:focus` | 差别不大，可用 |
| `:has()` | 不支持 | 别依赖 |
| `@media` | 只有 `min/max-width|height`、`orientation`、`and` | |
| `@supports` / `@layer` / `@container` | 不支持（`@import` 反而支持） | |
| UA 样式 | **没有**：`h1` 和 `div` 视觉上没区别，`ul` 没圆点，`table` 没有表格布局 | 一切自己写 |
| 负 `margin` | 被钳成 0 | 别用负间距 |
| `transform` | 无 `skew` / `matrix` / `perspective` | |
| Grid | 无 `areas` / `auto-flow` / 命名线 / 隐式轨道 | `grid-template-columns` + `span N` 够用 |
| 命名色 | 只有 26 个；**`currentColor` 解析为黑** | 用 hex，或用 `svg` 内的 currentColor |
| `font-style` | 只认 `oblique`，`italic` 无效 | |
| 变量 | `--x` 可写在样式表也可写在内联 style，能被后代和伪元素继承 | 传参靠它 |
| 自绘滚动条 | `::-webkit-scrollbar*` 只读 `width`/`height`、`background-color`、`border-radius`、`display:none` | 立体斜角滑块做不出来 |
| 遮罩做不了伪元素 | 宿主自身背景画在负 `z-index` 子层**之下**，`z-index:-1` 的伪元素会盖住面板 | 遮罩必须是独立元素 |

---

## 8. 新主题 checklist

1. 建 `theme/<name>/<name>.css`，命名合法（不以数字开头），作用域类 `.<name>-theme`，全文选择器都以它开头；
2. 抄一份参考实现（`t01-ore`）的样式表，**先只换 token 值**，跑通再改造型；
3. 逐个过 §2 的必填 token——**不加、不删、不改名**；
4. 按 §3 核对每个核心组件：**用最小标记能渲染出正确视觉**，多写的子元素忽略即可；
5. 按 §4 补状态钩子，尤其是禁用要压过 hover；
6. 按 §7 过一遍引擎坑（焦点框要用 `outline` 画，不要退回 `box-shadow`，见 §4）；
   逐个体检焦点态与禁用态：每个可交互组件都要有焦点环（`outline` + `outline-offset`）和禁用态，禁用压过 hover，宿主用 `div` 的记得加 `tabindex` 或改 `<button>`；
7. 验证：用同一份最小标记的 fixture 换主题目检，再检查 §9 的组合场景；打开公共预览台 `t00-example.html` 和主题自带的 `example.html`（如有）逐页检查。类名齐全、分区有高度、页面无溢出，并不能证明内部图标和控件正确。

### 从现有主题派生一个新主题

抄参考实现是最省力的起步方式，但要抄对：

1. **先换命名空间再动手**：复制样式表，全文替换作用域类、主题前缀类（`.<name>-code` 等）、示例页 id
   （`#<name>-page-*`）、keyframes 前缀；**只有 §2.4 的兼容层变量逐字不动**；
2. **第一轮只改 token 值**：前提是组件规则里已经没有硬编码色（§2.5）。此时主题应该"能跑但还不好看"，属正常中间态；
3. **第二轮改造型，几何必须重算**：像素风主题的几何在扁平主题里会出洋相——
   - **上下不对称的 padding**（`7px 16px 13px`，本来是给"按下时上下互换"的斜角用的）会变成"文字偏高、下重上轻"；
   - 奇偶对齐的尺寸（`44px` 槽位、2px 基准单位）、`inset` 斜角阴影、双层分隔线、`inset 0 4px` 顶部指示条，
     都属于像素语言，要整体换成新主题的深度语言（环 / 柔和投影 / 单条发丝线 / 2px 下划线）；
   - 字体族、行高、标题阶梯按新主题的排版体系重设——方向往往不是"换个颜色"，而是"更大更松"；
4. **深色 → 浅色单独过一遍**（反向同理）：逐项确认"深底上的文字色"、"禁用与次要文字"、"深色容器的 token"
   （抽屉 / 侧栏 / toast / 气泡 / 代码块是保持深色还是转浅色，一次决定，不要一半深一半浅）；
   浮层的判断标准：**大容器跟页面走，小芯片（提示、通知）可以保留深色**；
5. **新造的可复用类要回流契约**：做主题时发现的通用件（如 `.slider-value`、`.slider-scale`）要同时补进 §3 核心层，
   并让其它主题也实现——否则预览台在别的主题下会出现裸标记；
6. **用脚本派生时，把各层分开**：命名替换 / token 替换 / 造型覆盖 / 浅色修正各成一段并写注释，并保留生成脚本；
   否则后来的人改一层时会被后一层无声覆盖。

**可选**：若沿用 `#<name>-page-*` / `#<name>-modal-toggle` 这套展台 id 约定，可以直接复用同一套七页展台骨架，主题之间可以对着同一份页面比。

---

## 9. 预览与回归

`theme/t00-example.html` 是**跨主题预览台**，也是这套规范的验收工具：

- 它只使用 §3 的核心类与 `--*` token，五个分页（基础 / 导航与表面 / 表单与控件 / 数据与反馈 / 结构）覆盖全部核心组件；工具栏一个 `<select>` 切主题，**同一时刻只加载所选主题那条样式表**（`<head>` 里只有一条默认表，切主题时先加载新表、再换根元素的作用域类、最后摘掉旧表）；
- 页面上还装着内置的 `<theme-switcher/>`（Java 元素：右下角悬浮圆钮，可拖动，点开是内置主题清单）。它和工具栏 `<select>` 是两条并存的换肤路径，共用同一套约定——作用域类 + 带 `data-theme-css` 标记的 `<link>`；谁切都会按标记摘掉对方留下的旧表，并按内置主题表清掉根元素上的旧作用域类，所以两条路径不会各说各话。内置切换器换完派发 `aui-theme-change`，页面脚本据此同步自己的主题状态；
- **加一个主题**：在页面脚本的 `THEMES` 数组、工具栏 `<option>`、以及 Java 侧 `ThemeSwitcher.BUILT_IN_THEME_IDS` 里各加一行即可，样式表不用在页面里声明（脚本和切换器都按 id 去增删 `<link href="<name>/<name>.css">`；变体主题照旧写 `baseScope`，基主题那张表会被一起加载）；
- **引擎侧**：运行时插 `<link rel=stylesheet>` 会正常加载——元素进树时引擎就给它挂表，并把 `load` / `error` 派发回这个 link（CSS 在工作线程读盘解析，主线程应用进级联之后才派发 `load`）；移除 `<link>` 会同步把那张表从级联里摘掉。按需增删表在浏览器（含 Playwright 验收）和引擎里都成立；
- 它同时是**一致性检查表**：某个组件在预览台里空白、错位或颜色不对，就是那个主题没实现或实现错了；
- 分页按**功能**划分，不按"这批是新加的"划分——新增组件请挂到它所属的那一页，别开新页；
- 预览台外壳用的是它私有的 `.t00-*` 样式，**不属于契约**，也刻意不跟随主题，保证主题样式再坏也能操作；
- 主题自带的 `example.html` 属于主题自己，可沿用 `#<name>-page-*` / `#<name>-modal-toggle` 这套七页展台约定。

### 回归与审计

主题写完不等于写对。改完样式后按下面四类做一遍**客观检查**，比"盯着看觉得还行"可靠得多：

1. **一致性**：逐分页统计活动分区数、每个分区的渲染高度、零尺寸的可见元素数；有 `display:none` 的子分区被
   同一分页的两个主题共用时，确认切换后没有"该显示却没显示"。**出现空白分区往往是组件根本没实现**，
   而不是被隐藏了；
2. **对比度审计**：遍历所有带文字且可见的元素，取它的 `color` 与"沿父链向上第一个非透明背景色"，
   算 WCAG 对比度，列出低于 `3.0` 的（禁用态可豁免）。**深色主题转浅色时必做**——一次能抓出几十处
   "以为改了其实没改"的不可见文字，且能精确定位到类名；

   **文字之外，还要量"图元压在另一个图元上"的那一对**：开关的钮 / 轨道、滑杆的钮 / 轨、进度的填充 / 轨、
   勾选的勾 / 框。上面那条审计只看文字，会放过"深钮压深轨"这类控件本身读不出来的情况——t12 的开关开态
   就是这样过的关：轨道 vs 页面 5.79:1 合格，钮 vs 轨道只有 **1.89:1**，整只糊成一团。
   判定门槛：钮与轨道（含各个状态）≥ `2.5:1`，实测工具见 §9.4 的 `state-diff.mjs`。
3. **跨主题差异**：同一个组件在两个主题下各取一次**计算样式签名**（高度 / padding / 边框 / 圆角 / 背景 /
   文字色 / 字族 / 字重 / 阴影）并各截一张图，比对"有多少字段不同"与"像素差异百分比"。**只剩颜色不同的，
   就是还太像**——差异化要靠结构、排版、交互、几何一起发挥（见 §6）；

   **怎么量（可复用的两个脚本）**：

   - `.tmp/theme-diff.mjs <主题A> <主题B>`：对 §3 里的核心组件逐个取样，每份签名包含计算样式字段
     **加该元素 `::before`/`::after` 的字段**（装饰件的结构往往全在伪元素上——`.divider-label` 的双规则
     就是一例，不采伪元素会误判成"没差别"），另外把元素自身截图二值化成剪影与行/列墨迹剖面。
     判定看两个量：**关键结构字段差**（宽高 / 内外边距 / 四边边框 / 圆角 / 阴影 / 背景图 / display / gap /
     outline / flex 对齐）与**剖面差**。字体族、行高、字距对无文字的控件只是继承噪音，单列不计入判定。
     门槛：关键结构字段差 ≤ 2 **且** 剖面差 < 0.15 才算"还太像"，且只在两个主题的组件尺寸接近时才有意义。
     纯横条的组件（进度条）剪影 IoU 会失真得离谱（0.76 其实差得很远），所以 IoU 只作参考，不作判定。
   - `.tmp/ore-isms.py <主题>`：从参考实现派生的主题专用。把"像素语言"逐类数出来——3px 粗边框、
     上下不对称的内边距、42px 定高、`0 0 0 Npx` 硬盒环、`inset` 斜面、单侧粗色条、`border-radius: 50%`。
     它是**文本级**检查，会把你后续层覆盖掉的死声明也算进来：跑完要看一遍命中的声明，别只看计数。
4. **状态与交互**：`hover` / 按下 / 焦点 / 禁用各量一次计算样式，含"禁用不许有任何反馈"（同 §4 规则 1）；
   焦点环要能被键盘 `Tab` 到——宿主不可聚焦的（如用 `div` 当按钮）先补 `tabindex` 或改成 `<button>`；
   量一下焦点元素的 `outline-style` / `outline-width` / `outline-offset`，确认环确实是 `outline` 画的而不是阴影。

   **`.tmp/state-diff.mjs` 把这一条做成脚本**：在同一主题内逐个状态取计算样式与默认态比字段差异，
   **差异为 0 就是"状态没实现"**——这类 bug 跨主题比对抓不到（两个主题都没状态），单看渲染也容易漏
   （默认态看起来完全正常）。t12 这轮就是靠它发现开关 / 标签页 / 折叠箭头 / 树箭头等一整批状态，
   根因是机械层把状态规则的声明删空后留下了 `selector { }` 空壳。两个取样要点：
   默认态取样**必须排除已经带状态的元素**（否则会拿状态元素和自己比，永远 0 差异）；
   状态挂在祖先上的（`.accordion-item.open .accordion-header`）要按祖先判断。

两个容易忽略的细节：

- **预览台的说明文字不要写主题专属词汇**。展台是跨主题共用的，深色像素主题里写"斜面按钮"这种描述，
  换到浅色扁平主题下就自相矛盾；
- **`<button>` 在浏览器里有 UA 默认 padding，AUI 里没有**。写主题时要显式 `padding: 0`（或补齐），
  否则同一个类在预览台和引擎里高度对不上。

### 必测的最小标记与组合场景

以下项目来自 `t10-neobrutalist`、`t11-hand-drawn-doodle` 的实际遗漏。**选择器存在、CSS 可解析或独立组件正常，都不等于组合场景通过**。
参考 oreui 时要核对组件子元素、伪元素、尺寸分配和状态规则，不能只比对类名清单。

| 场景 | 已出现的问题 | 验收要求 |
| --- | --- | --- |
| 四方向 tooltip + 短按钮 | 气泡受按钮宽度限制；预览强制全展开，误把重叠当成需要拉开按钮 | 无展开属性时默认隐藏；真实悬浮只显示对应气泡，移开且失焦后隐藏；键盘聚焦可显示；上下居中、左右贴近触发器，文字保持可读宽度 |
| 空 `.tree-toggle` / `.split-button-arrow` | 只有空方框或按钮，没有箭头 | 不增加字符或子元素就能看到箭头；树的展开状态同步转向；图标与文字对齐，禁用后仍可辨认 |
| `.choice` 内原生 checkbox / radio | 缺少尺寸与 `accent-color`，退回默认蓝色 | 计算样式使用主题强调色；检查选中、未选中、键盘操作、单选互斥和禁用；不只检查外层 label |
| 下拉菜单 | 缺选中勾、展开箭头不变、聚焦不展开；短标签压窄选项、长列表无滚动 | 分别验证每个展开钩子与 `.selected` / `[aria-selected="true"]`；用短标签和多条选项检查菜单宽度、限高、滚动及关闭后的隐藏状态 |
| 禁用图标与深色加载按钮 | 通用伪元素填色盖住箭头或勾号；加载标记与背景同色 | 箭头/勾线内部透明，填充型圆点仍保留填充；三个禁用钩子在 hover 前后表现一致；加载标记与实际按钮背景有可辨识对比 |
| `.settings-row > .settings-control > .slider` | 独立滑杆正常，放进 flex 控制列后只剩约 4px 边框，轨道和填充消失 | 在不同控制列宽度下测轨道实际宽度；滑杆应使用控制列的可用空间，填充比例及滑块位置仍随 `--value` 改变；同时覆盖 `.slider-2` 和直接放进设置行的用法 |
| `.settings-list` + 标签、说明与控件 | `.settings-help` 挤在标签同一行，激活行无反馈 | 说明文字在标签下方；合并描边完整；激活行与内部选中控件同时可辨；窄屏换行后控件不被挤没或撑出容器 |

**组合布局要显式分配空间**。例如设置行使用 flex 布局时，空滑杆内部只有绝对定位的伪元素，不会自动撑出轨道宽度。
可给 `.settings-row .slider` / `.slider-2` 设置 `flex: 1 1 0; min-width: 0`，并让 `.settings-control` 能收缩；保留滑块在两端所需的空间。
这是布局策略示例，不固定各主题的轨道尺寸或间距。

**交互验收不能靠修改展台掩盖缺陷**。tooltip 的常显布局只用于单独检查显式展开状态，不能替代实际 hover / focus 测试；
不得用强制展开、拉大按钮间距或额外标记来让错误样式看起来正常。修复应落在主题规则中，公共预览保持真实交互。

至少检查 1440px、390px、320px 视口，并补充窄控制列、短触发器、长选项列表等局部约束。
对滑杆等组件断言**内部几何**，而不只是“整个分区高度大于零”；查看修复后截图，确认图标形状、文字换行和定位。

引擎侧已有 `TreeThemeTest`、`ThemeDetailTest`，覆盖两个新主题的树箭头、生成图标、禁用悬浮、下拉状态和设置行滑杆布局。
在仓库根目录运行：

```powershell
.\gradlew.bat -p targets/forge-1.20.1 test --tests com.sighs.apricityui.webapi.TreeThemeTest --tests com.sighs.apricityui.webapi.ThemeDetailTest --console plain
```

这些测试不等于全主题或游戏内视觉验收。浏览器交互/截图、引擎回归和 Minecraft 客户端目检分别记录结果；未执行的检查明确说明。
