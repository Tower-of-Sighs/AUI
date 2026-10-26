# t02-create · 机械动力像素主题

这不是一套“网页皮肤”，而是把 Minecraft 模组 **Create（机械动力）** 的 GUI 语言搬进 AUI：

- 面板是**烧焦的深木 / 钢灰**，不是半透明玻璃；
- 强调色是**黄铜**，次强调色是**蓝图蓝**，表单是**羊皮纸**；
- 斜角是**2px 硬台阶**，不是圆角、模糊阴影和内发光；
- 像素纹理只用于 CSS 画不出来的东西：**斜角、铆钉、棋盘抖动、纸边、槽位内阴影**；
- 字体不设置 `font-family`，让引擎回落到 **Minecraft 自带字体**——Create 原版用的就是它。

主题 ID：`t02-create`，作用域类：`.t02-create-theme`，入口：
`apricityui/theme/t02-create/t02-create.css`。

配套展台：`apricityui/theme/t02-create/create-example.html`，七个分页覆盖全部核心组件，
顶栏切换分页，模态 / 抽屉 / 侧栏 / 通知 / 加载 / 标签页 / 折叠 / 下拉都有可直接点的交互。

---

## 1. 接入

```html
<link rel="stylesheet" href="/apricityui/theme/t02-create/t02-create.css">
<body class="t02-create-theme">
```

页面自己铺底色（主题根块刻意不铺，HUD / 浮层文档也不该被铺底）：

```html
<body class="t02-create-theme" style="background-color: var(--canvas)">
```

覆盖在游戏画面上的 HUD 可以只挂作用域类，让画布保持透明：

```html
<body class="t02-create-theme">
```

Java 内置切换器会扫描 `theme/` 下所有 `t` 开头的文件夹并自动列出主题，
右下角悬浮换肤按钮可以直接选到；预览台 `t00-example.html` 的下拉框仍是页面内的静态清单。

---

## 2. 硬性规则（违反其中一条就不再是 t02-create）

1. **2px 像素网格**。布局尺寸、间距、描边、滑块位移都用 2 的倍数；图标内部的勾、箭头、X 允许 1–3px 的像素笔触，但不参与布局。
2. **零圆角**。`--radius-sm/md` 都是 0；不要用 `border-radius: 50%` 做开关或单选。
3. **斜角方向固定**：左上受光、右下背光。按下时反过来（凹陷），不要只改颜色。
4. **9-slice 的边框宽度必须等于 slice**。例如 `border: 8px` 配 `border-image: … 8 / 8px`。
   改了任一边（比如 `border-left-width: 0`）只用于拼接控件，不能改主边框厚度，否则 2px 像素会被缩放出半像素。
5. **图片只做纹理细节**。不要往背景里贴齿轮、管道、铜板图案；主题的识别度来自材质层次，不来自贴图花纹。
6. **不设 `font-family`**。正文与标题都走 MC 字体；只有 `.code-block` 用 `monospace`。
7. **不用 web 效果**：毛玻璃、模糊投影、径向渐变、Mesh 渐变、外发光、大圆角、斜切 transform 全部禁止。
8. **彩色底上的字用 `--ink-on-accent`，深色面用 `--ink`，羊皮纸用 `--paper-ink`**。不要写死 `#fff`。
9. **焦点环必须用 `outline`**：`2px solid var(--focus)`，偏移 2px（整行控件用 `-2px` 内嵌）。
10. **禁用态要压过 hover / active**：同时列 `[disabled]`、`.disabled`、`[aria-disabled="true"]` 和它们的 `:hover`。

---

## 3. 色板

### 3.1 基础 token

| token | 值 | 用途 |
| --- | --- | --- |
| `--canvas` | `#171411` | 页面底色（烟熏工作台） |
| `--surface` | `#2e2a24` | 卡片 / 面板 / 菜单 |
| `--surface-deep` | `#1f1b17` | 凹面、表格底、输入槽 |
| `--surface-soft` | `#4a443a` | 普通按钮、凸起面 |
| `--edge` | `#0a0806` | 外描边 |
| `--edge-light` | `#6f6759` | 受光描边 / 钢高光 |
| `--ink` | `#efe6d2` | 深色面上的正文 |
| `--ink-muted` | `#b8ab93` | 次要文字 |
| `--ink-dark` | `#2f1a08` | 浅色面上的正文 |
| `--gold` | `#c99a52` | 黄铜（主色） |
| `--gold-shadow` | `#704630` | 黄铜暗部 / 铜锈 |
| `--purple` | `#6475ad` | 蓝图蓝（次强调） |
| `--green` | `#2f8a5c` | Create 确认绿 |
| `--red` | `#a8453a` | 铜红（危险） |
| `--blue` | `#68849f` | 钢蓝（信息） |
| `--warning` | `#e4b763` | 警告黄铜 |
| `--focus` | `#fbe88a` | 焦点环（亮黄铜） |

### 3.2 材质专用

| token | 值 | 用途 |
| --- | --- | --- |
| `--paper` | `#e5d0b5` | 羊皮纸面（输入、tooltip） |
| `--paper-deep` | `#d7c5af` | 羊皮纸暗纹 |
| `--paper-ink` | `#311a00` | 羊皮纸上的字 |
| `--paper-edge` | `#5b3d09` | 纸边 / 装订线 |
| `--deep-dark` | `#120f0d` | 代码块 |
| `--ink-on-dark` | `#efe6d2` | 深色面上的字 |
| `--ink-on-accent` | `#2f1a08` | 黄铜 / 亮色上的字 |
| `--disabled-background` | `#2e2a24` | 禁用底 |
| `--disabled-foreground` | `#8a7d68` | 禁用字 |

黄铜的光阶取自 Create 的 `BRASS_FRAME`：`#704630 → #9c6846 → #bf8e55 → #c99a52 → #e4b763 → #f4c86a → #fbe88a`。
不要在组件里写这些中间值，用 `--gold` / `--gold-shadow` / `--gold-vip` 三个角色。

---

## 4. 图片材质

所有 PNG 都按 **1 art pixel = 2 CSS px** 绘制，`image-rendering: pixelated`。

| 文件 | 尺寸 | slice / border | 用在 |
| --- | --- | --- | --- |
| `frame-plate.png` | 40×40 | 8 / 8px | `.card`、`.menu`、`.toast`、`.dropdown-options` |
| `frame-brass.png` | 40×40 | 8 / 8px | `.modal`、`.drawer` |
| `button-plate.png` | 32×32 | 6 / 6px | `.button`、`.icon-button`、`.dropdown-label`、`.split-button-arrow` |
| `button-plate-down.png` | 32×32 | 6 / 6px | 上述控件的按下态（斜角翻转） |
| `button-brass.png` | 32×32 | 6 / 6px | `.button-primary` |
| `button-brass-down.png` | 32×32 | 6 / 6px | `.button-primary:active` |
| `input-parchment.png` | 32×32 | 6 / 6px | `.form-input/-select/-textarea`、`[contenteditable]` |
| `slot.png` | 24×24 | 4 / 4px | `.slot`、`<slot>`、`.inventory-grid` |
| `parchment.png` | 40×40 | 8 / 8px + repeat | `.tooltip-content` |
| `dither-brass.png` | 16×16 | 平铺 | `.card-header`、`.table th`、`.page-button.active`、`.segmented-item.active` |
| `grain.png` | 32×32 | 平铺 | `.card`、`.navbar`、`.sidebar`、`.toolbar` |

**为什么有的用 `repeat`**：`button-*` 的受光边里有一格一格的棋盘抖动，`stretch` 会把 2px 的格子拉成几十像素的色块；
`border-image-repeat: repeat` 保持抖动周期。`frame-plate` / `frame-brass` 的边是实色，`stretch` 反而更稳。

**不要在图片上画齿轮**。齿轮只出现在 `.button[data-state="loading"]::before` 和 `.spinner` 的 `clip-path` 里，
它是形状而不是贴图，因此能随主题色变化。

重新生成材质（2px 网格、Create 取色）：

```powershell
python build/t02-create-research/gen_theme_textures.py
```

---

## 5. 几何与排版

- 基准单位 `--size-unit: 2px`；所有 `padding` / `gap` / 高度优先用 4 的倍数。
- 按钮：默认 40px 高、`padding: 0 16px`、6px 材质边框；`.button-small` 30px；`.button-wide` 最小 200px。
- 输入：40px 高；textarea 最小 96px；内容行高 28px（40 − 上下 6px 边框）。
- 槽位：44px；快捷栏 40px；`.inventory-grid` 间距 6px。
- 卡片：8px 材质边框，header / body / footer 分别 10–14px 内边距。
- 字体阶梯：13 / 16 / 20 / 28px；`h1` 带 4px 黄铜下划线，`h2` 带 2px 钢线。
- 动效一律 `steps()`（80ms / 140ms / 900ms），不要缓动曲线；这是 GUI，不是网页。
- **浏览器预览会显示衬线字体**，因为主题刻意不设 `font-family`，而浏览器没有 MC 字体。验收时临时加
  `.t02-create-theme{font-family:Consolas,monospace}` 只用于看布局；游戏内不要加，否则会丢 MC 字体。

---

## 6. 组件速查

最小标记与 `theme-spec.md` §3 完全一致，下面是这套皮肤的读法：

| 组件 | 在这套主题里的样子 |
| --- | --- |
| `.button` | 钢灰凸面 + 6px 斜角；hover 提亮，active 斜角翻转并下沉 2px |
| `.button-primary` | 黄铜面 + 暗字；这是“建造 / 确认 / 组装”动作 |
| `.button-secondary` | 蓝图蓝面 + 米白字 |
| `.button-tertiary` | 凹面 + 次要字，像未点亮的仪表按钮 |
| `.button-danger` | 铜红面 |
| `.icon-button` | 40×40 方块，同一套钢斜角 |
| `.form-input` | 羊皮纸 + 深棕内阴影，placeholder `#8a6f4e` |
| `.checkbox` / `.radio` | CSS 自绘：20×20 凹槽；勾是黄铜折线，单选是 8×8 黄铜方点 |
| `.switch` | 52×26 凹槽 + 20×18 滑钮；开启时滑钮黄铜、右移 22px |
| `.slider` | 22px 凹槽，黄铜填充 + 竖条滑块；`--value` 同时驱动两者 |
| `.progress` | 22px 凹槽，黄铜斜纹填充；`.progress-label` 是居中的深色小牌 |
| `.card` | 钢框 + 铆钉 + 表面细颗粒；`.card-header` 是黄铜抖动条 |
| `.table` | 真实表格自动列宽；表头黄铜抖动，行间 2px 深描边，偶数行微亮 |
| `.badge` / `.tag` | 直角小方块；亮底上的字自动转深色 |
| `.alert` / `.banner` | 左侧 6px 色条，不再是网页式淡色底 |
| `.navbar` | 深色底 + 4px 黄铜底边；激活项用黄铜下划线 |
| `.tab.active` | 顶部 4px 黄铜条 + 凹面底 |
| `.modal` / `.drawer` | 黄铜 8px 外框 + 抖动标题条 + 无模糊投影 |
| `.tooltip-content` | 240px 宽的羊皮纸，8px 纸边；四方向由 `.tooltip-bottom/-left/-right` 控制 |
| `.menu` / `.dropdown-options` | 钢框深底；激活项左侧 4px 黄铜条 |
| `.slot` | 4px 内凹槽；`slot-count` 是右下角小牌 |
| `.hotbar > .slot.active` | 3px 亮黄铜内嵌环 |
| `.empty-state-icon` | 黄铜色，不再用暗淡的铜锈色（对比度 ≥ 3:1） |
| `.skeleton` | 钢灰块 + 硬边扫光（`transform`） |
| `.code-block` | 近黑底 + 羊皮纸字；`code` 退掉行内皮肤 |
| 稀有度 | `.text-common` 米白、`-uncommon` 黄铜、`-rare` 青、`-epic` 紫 |

典型配方：

```html
<div class="card">
  <div class="card-header">Assembly Line</div>
  <div class="card-body">
    <div class="settings-list">
      <div class="settings-row">
        <span class="settings-label">Rotation Speed</span>
        <span class="settings-control"><span class="slider-value">32</span></span>
      </div>
    </div>
    <div class="progress mt-3" style="--value: 64%"><span class="progress-label">64%</span></div>
  </div>
  <div class="card-footer">16 RPM · 4 shafts</div>
</div>
```

---

## 7. 定制与派生

- **只换色**：改根块的 token，不要动组件规则。主色是 `--gold`，黄铜暗部是 `--gold-shadow`，亮部用 `--gold-vip`。
- **做一块新面板**：

  ```css
  .t02-create-theme .machine-panel {
    border: 8px solid transparent;
    border-image: url("frame-plate.png") 8 / 8px stretch;
    background-color: var(--surface-deep);
    image-rendering: pixelated;
  }
  ```

  如果要黄铜框，换成 `frame-brass.png`；不要在组件里另写一套斜角。
- **加一张新材质**：以 2 的倍数作画，切片厚度 = art 边框厚度 × 2；边框宽度必须等于 slice。
  有抖动的边记得 `border-image-repeat: repeat`。
- **派生新主题**：复制样式表 → 全文替换作用域类与 keyframes 前缀 → 先只改 token 值跑通 →
  再改结构。不要在本主题上叠加“网页化”改造（圆角、模糊、渐变网格），那会直接毁掉像素语言。

---

## 8. 引擎坑（写这套皮肤时踩过的）

| 坑 | 做法 |
| --- | --- |
| `border-image` 的 slice 与 `border-width` 不一致 | 会缩放像素纹理；保持相等，拼接控件才用 `border-left-width: 0` |
| 抖动边被拉成长条 | `border-image-repeat: repeat`，不要 `stretch` |
| `text-shadow` 不可用 | 文字立体感靠背景条、描边和色阶，不要仿 web 投影 |
| `currentColor` 解析为黑 | 关闭图标、箭头一律写具体 token |
| 没有 radial / conic 渐变 | 棋盘、条纹全部用 `repeating-linear-gradient` 或图片 |
| 负 `margin` 被钳成 0 | 滑块居中用 `transform: translateX(-50%)`，不要负 margin |
| `border-radius` 不可做圆环 | 开关、单选、槽位全部直角 |
| `@media` 只认宽高 / orientation | 响应式只写 `max-width`；不要依赖 `prefers-*` |
| 自绘滚动条只能改颜色 / 宽度 | 滚动条是“黄铜条 + 深槽”，不做斜角滑块 |
| `font-family` 一设就丢 MC 字体 | 主题不设正文 `font-family`；只有代码块用等宽 |
| 工具类被组件自己的 `display` 覆盖 | `.hidden` / `.invisible` 必须放在样式表最末尾（本主题已这么做） |

---

## 9. 参考来源

研究基于 Create 6.0.10（1.21.1 / NeoForge）的 GUI 图集，以及下面这些附属与材质包。
**有 GUI 图集的样本逐像素取色并拆过边框层次**；没有独立 GUI 的包只作为材质 / 色板参照，
表里会注明，不把“看过 jar 里的物品贴图”说成“看过 GUI”。

### 附属模组（11）

| 模组 | 版本 | 取到的信息 |
| --- | --- | --- |
| Create | 6.0.10 (1.21.1 NeoForge) | `widgets.png` 的 18×18 按钮、22×22 hotslot、`value_settings.png` 的黄铜框、`player_inventory.png` 的原版灰斜角、`toolbox/sequencer` 的深木面板 |
| Create: Connected | 1.3.3 | 暗木 sequencer 面板 `#3a2411 / #442b15` |
| Create Crafts & Additions | 1.7.2 | 仪表 / 电机 / 电容等材质色阶（无独立 GUI 图集） |
| Create: Steam 'n' Rails | 1.7.3 | 深木控件 `#3d2012 / #492f1b`，列车 UI 的灰阶 |
| Create: Enchantment Industry | 2.5.4b | 近黑描边与暗色图标底色 |
| Create: Central Kitchen | 2.6.2b | 无独立 GUI；铜制厨具材质用于校验黄铜暖色 |
| Create Deco | 2.1.3 | 无独立 GUI；工业建筑色阶（钢灰、铜、砖）作为材质参照 |
| Create: Design n' Decor | 2.2b | 无独立 GUI；黄铜家具与铜绿细节作为材质参照 |
| Create: Diesel Generators | 1.3.15 | 筛选器 UI 的灰底 + 铜红强调 `#d8735a` |
| Create: New Age | 1.2.0 | 无独立 GUI；能源机器材质色阶 |
| Create: Slice & Dice | 4.3.4 | 无独立 GUI；料理材质暖色，用于校验暗底上的暖色不刺眼 |

### 材质包（11）

| 材质包 | 取到的信息 |
| --- | --- |
| Create Tweaks & Compats | 附属方块/物品重绘；用于确认调色板跨附属一致 |
| Create Style Sophisticated Backpacks | 背包方块/物品贴图；无 GUI 图层 |
| Tom's Create Storage (Create Simple Storage) | **最关键的暗色参照**：终端 `#2b2b2d` + 铜 `#dc854e` + 黄铜斜角 |
| Create: LabPBR | PBR 金属粗糙度/高光图，用于校验黄铜亮/暗阶 |
| Create: Reimagined | 重绘方块/物品仍保留的 2–3 色斜角 |
| Create Darkmode | 灰面板整体降到 `#4f4f4f`、槽位 `#383838`、高光 `#666666`，证明 Create 语言可以整体变暗 |
| Create Touch-Ups | 方块材质的铜/安山岩细微色差 |
| Create Applied Energistics 2 | AE2 终端/线缆材质；暗面板上的铜强调 |
| Bare Metals | 同一套 GUI 布局下的裸金属色阶 |
| Create: ComputerCraft | 36×36 的九宫格边框与“黑描边 + 2–3px 斜面”结构 |
| Create Style Sophisticated Storages | 装饰台/限量木桶 GUI 的灰底 + 棕铜色 |

---

## 10. 验收记录

浏览器（Chromium / Playwright），预览台 `theme/t00-example.html`，1440 / 390 / 320 三个视口：

- **主题加载**：`t02-create` 样式表与 11 张 PNG 全部 200；无 console error / pageerror。
- **布局**：所有可见交互控件尺寸 > 8×8；1440 / 390 / 320 均无横向溢出。
- **对比度**：遍历 687 个可见文字节点（含表格、卡片、浮层），低于 WCAG 3.0 的 **0 处**（禁用态单独豁免）。
- **状态**：
  - 禁用按钮 / 图标按钮 / 页码 / 开关 / 复选 / 菜单项 / 滑杆：hover 前后计算样式逐字段相同；
  - 焦点：`outline: 2px solid #fbe88a`，offset 2px；
  - 开关拇指 4px → 28px（轨道 52px、拇指 20px）；复选 / 单选勾透明度 0 → 1；树箭头 0° → 90°；
  - tooltip 四方向：240px 宽，不再被短触发器挤窄；上/下/左/右偏移分别为 −57 / 38 / −248 / +53px（以触发器为基准）；
  - 下拉在 `.open` / `[aria-expanded="true"]` / `:focus-within` 下都能展开；220px 宽、限高 240、`overflow: auto`；
  - 表格自动列宽 482 / 462 / 140（三列内容不等长）；
  - 进度条 82%：填充 888.9px / 轨 1088px；滑杆填充与滑块 left 同为 244px（492px 轨道）；设置行内滑杆宽 440px。
- **9-slice 像素抽样**：
  - `button-brass`：上/左 `#fbe88a`，下/右 `#704630`，外圈 `#0a0806`；
  - `frame-plate` 卡片：上/左 `#6f6759`，下/右 `#14110e`，角上黄铜铆钉 `#f4c86a`；
  - `input-parchment`：上/左 `#a8835a`，下/右 `#fff4dd`，中心 `#e5d0b5`；
  - `slot`：左上 `#16130f`，右下 `#7a7264`；
  - `parchment`：纸面 `#e5d0b5`，纸边 `#5b3d09`，边缘抖动 `#d7c5af`（`repeat` 后仍是 2px 周期）。
- **自带展台**：`create-example.html` 七个分页在 1440 / 390 / 320 下均无横向溢出；
  392 个文字节点对比度 0 失败；模态、抽屉、侧栏、通知、加载、标签页、折叠、下拉、开关交互全部通过。
- **未做**：没有在 NeoForge 客户端里跑实际 Screen。浏览器截图与像素抽样见
  `build/t02-create-validation/sections/*.png`、`build/t02-create-validation/elements/*.png`；
  交付前建议在游戏里打开 `apricityui/theme/t00-example.html` 目检一次 MC 字体与 GUI 缩放。

按需求未编写 Java 测试。
