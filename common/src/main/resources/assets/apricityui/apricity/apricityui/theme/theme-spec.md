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
| 选项组 | `.choice-list > .choice`（内含原生 `input` + 文本） | |
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
| 提示气泡 | `.tooltip > .tooltip-content` | 气泡里必须是真文本：引擎没有 `attr()`，伪元素取不到属性值 |
| 下拉 | `.dropdown > .dropdown-label` + `.dropdown-options > .dropdown-option` | 展开态支持 `.open` / `[data-state="open"]` / `[aria-expanded="true"]` / `details[open]`。注意引擎里 `details`/`summary` **没有原生展开行为**，那条只是样式钩子，`open` 属性得页面自己设 |
| 物品格 | `.inventory-grid > .slot` | 纯视觉方格；真实槽位用容器系统的 `<slot>` |
| 侧栏 | `.sidebar` + `.sidebar-mask` > `.sidebar-title` / `.sidebar-divider` / `.sidebar-item` | 遮罩同样必须独立 |
| 设置行 | `.settings-row`（容器 + 控件，文字直接写在容器里） | 成组时用 `.settings-list` 包住，相邻行自动合并描边；可选 `.settings-label` / `.settings-help` / `.settings-control` |
| 菜单 | `.menu` > `.menu-item` | 另有 `.menu-label` / `.menu-separator` / `.menu-shortcut`；活动 `.active` / `[aria-selected="true"]`，禁用 `.disabled` / `[disabled]` / `[aria-disabled="true"]` |
| 互斥按钮组 | `.segmented` > `.segmented-item` | `.toggle-group` / `.toggle-item` 是同一组件的别名；选中 `.active` / `[aria-pressed="true"]` / `[aria-selected="true"]` |
| 折叠面板 | `.accordion` > `.accordion-item` > `.accordion-header` + `.accordion-body` | 展开 `.open` / `[data-state="open"]`；箭头由 CSS 画 |
| 键值列表 | `.kv` > `.kv-row` > `.kv-key` + `.kv-value` | 值自动贴右，行间自带分隔线 |
| 快捷栏 | `.hotbar` > `.slot`（沿用现有槽位类） | 选中格 `.active`（白环用 `box-shadow` 画，引擎没有 `outline`）；堆叠数 `.slot-count` |
| 空状态 | `.empty-state` | 可选 `.empty-state-icon` / `-title` / `-text` / `-actions` |
| 骨架屏 | `.skeleton` + `.skeleton-title` / `-text` / `-block` / `-circle` | 扫光只能用 `transform` 动画（白名单外的属性会跳变） |
| 代码块 | `.code-block`（`pre` 或 `div`，内部 `code` 退掉行内皮肤） | 取代主题私有的 `<name>-code` |
| 带标签分隔线 | `.divider-label`（文字就是元素内容） | 两侧线由伪元素撑开，不需要额外元素 |
| 数值加减 | `.stepper` > `.stepper-button` + `.stepper-value` + `.stepper-button` | |
| 树 | `.tree` > `.tree-item` + `.tree-group(.open)` | 缩进靠嵌套 `.tree-group`；可选 `.tree-toggle` |
| 统计块 | `.stat` > `.stat-value` + `.stat-label` | 大数字用标题字族 |
| 连体按钮 | `.button-group` > `.button` | 后继按钮去掉左边框（引擎把负 margin 钳成 0，不能靠压边） |
| 纵向标签页 | `.tabs-vertical` > `.tab` | 活动指示条在左边，与横向 tab 的顶部指示条对应 |
| 可删除标签 | 现有 `.tag` + `.tag-close` | |
| 尺寸档 | `.modal-sm` / `.modal-lg`、`.drawer-sm` / `-lg` / `-short` / `-tall` | 只改尺寸，弹窗默认 520 |
| 工具条 | `.toolbar` > 任意控件 | 可选 `.toolbar-separator`（分隔线）与 `.toolbar-spacer`（把后面的项推到最右） |
| 步骤条 | `.steps` > `.step`（内含 `.step-number` + 可选 `.step-label`） | 状态 `.done` / `.active`；连接线由 CSS 生成，标记保持平铺 |
| 进度标签 | 现有 `.progress` 里加 `.progress-label` | 绝对居中、`pointer-events: none`；`.progress` 自身带 `position: relative` |
| 分裂按钮 | `.split-button` > `.button` + `.split-button-arrow` | 接缝靠去掉主按钮的右边框（引擎把负 margin 钳成 0） |
| 顶部加载条 | `.loading-bar`（单元素，不定态） | 扫光只用 `transform` 动画 |
| 可关闭标签 | 现有 `.tab` 里加 `.tab-close` | 与 `.tag-close` 同款 |
| 稀有度色 | `--rarity-common` / `-uncommon` / `-rare` / `-epic` + `.text-common` / `-uncommon` / `-rare` / `-epic` | MC 物品名配色 |

**只兜底"页面躲不掉"的那几样**（核心层）：真实滚动条（`scrollbar-width` —— AUI 接受长度值用来定粗细 —— + `scrollbar-color` + `::-webkit-scrollbar*`）、placeholder（`:placeholder-shown`，**没有 `::placeholder`**）、选中文字底色（引擎扩展属性 `selection-color`）、`[contenteditable]` 的输入框皮肤。这四样无论页面怎么写都会出现，所以主题必须兜。

**原生表单控件不兜，也不该兜**：`accent-color` 是引擎唯一会读的属性，而决定它们长相的部分全是写死的（range 轨道 `#DDD6FE`、未勾选的 checkbox / radio 是 `#FFFFFF` 底 + `#767676` 描边、number 的 spinner、select 弹出层与 `optgroup`）。既然怎么调都变不成主题的样子，页面就该用 `.slider` / `.checkbox` / `.radio` / `.form-input` 这些组件，别指望给原生控件上色。

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
2. **焦点态是硬要求**：每个可交互组件都要有焦点环，且只能用 `box-shadow` 画（引擎没有 `outline`）。三个必须知道的坑：
   - `box-shadow` 是**整条列表替换**，所以焦点规则要重复该组件自己的内阴影，例如 `box-shadow: inset 0 5px #242425, 0 0 0 2px var(--green)`；带分变体斜角的（`.button-*-2` / `-3`）可以让焦点环临时盖掉它，不必为每个变体重写一遍；
   - **宿主必须可聚焦**。用 `div` 承载 `.menu-item` / `.tree-item` / `.accordion-header` / `.slot` 这类交互件时要加 `tabindex`，或直接改用 `<button>`——否则键盘用户根本碰不到，环也永远不会出现；
   - 列表、菜单、树这类行用内嵌环（`inset 0 0 0 2px`），按钮和方块类内外环都行，同一主题内保持一致即可；
3. 只在状态真的存在时给视觉反馈，不要用 `:hover` 假装选中；
4. **在已有 `transform` 上叠加**：滑块、气泡这类靠 `translate(-50%,-50%)` 居中的元素，加缩放必须写进同一个声明
   （`transform: translate(-50%,-50%) scale(1.18)`）；只写 `scale()` 会把元素甩出原位。

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
6. 按 §7 过一遍引擎坑（至少确认焦点框没写成 `outline`）；
   逐个体检焦点态与禁用态：每个可交互组件都要有焦点环（`box-shadow`，见 §4）和禁用态，禁用压过 hover，宿主用 `div` 的记得加 `tabindex` 或改 `<button>`；
7. 验证：用同一份最小标记的 fixture 换主题目检；再打开自带 `example.html` 逐页看一遍（展台不属于契约，但它是最好的回归样本）。

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

- 它只使用 §3 的核心类与 `--*` token，五个分页（基础 / 导航与表面 / 表单与控件 / 数据与反馈 / 结构）覆盖全部核心组件；工具栏一个 `<select>` 切主题，切换只换根元素的作用域类，CSS 一次性全加载；
- **加一个主题**：`<head>` 里补一条 `<link rel="stylesheet" href="<name>/<name>.css">`，再在页面脚本的 `THEMES` 数组和工具栏 `<option>` 里各加一行即可；
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
3. **跨主题差异**：同一个组件在两个主题下各取一次**计算样式签名**（高度 / padding / 边框 / 圆角 / 背景 /
   文字色 / 字族 / 字重 / 阴影）并各截一张图，比对"有多少字段不同"与"像素差异百分比"。**只剩颜色不同的，
   就是还太像**——差异化要靠结构、排版、交互、几何一起发挥（见 §6）；
4. **状态与交互**：`hover` / 按下 / 焦点 / 禁用各量一次计算样式，含"禁用不许有任何反馈"（同 §4 规则 1）；
   焦点环要能被键盘 `Tab` 到——宿主不可聚焦的（如用 `div` 当按钮）先补 `tabindex` 或改成 `<button>`。

两个容易忽略的细节：

- **预览台的说明文字不要写主题专属词汇**。展台是跨主题共用的，深色像素主题里写"斜面按钮"这种描述，
  换到浅色扁平主题下就自相矛盾；
- **`<button>` 在浏览器里有 UA 默认 padding，AUI 里没有**。写主题时要显式 `padding: 0`（或补齐），
  否则同一个类在预览台和引擎里高度对不上。
