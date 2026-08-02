# 内置 Ore 主题文档

最后更新：2026-08-02

Ore 是 ApricityUI 内置的一套纯 CSS 主题。它采用 Minecraft 风格的像素化边框、内嵌高光、深色石材表面和绿色/紫色/金色强调色，适合设置页、资源管理器、编辑器、容器界面和其他操作型 UI。

主题本身只负责样式，不提供 JavaScript 组件逻辑，也不会自动为按钮、标签页、弹窗或分页绑定业务行为。点击处理、页面切换、数据提交和状态管理仍然由 HTML、JavaScript 或 Java 代码负责。

## 1. 主题资源

主题资源位于源码目录：

~~~text
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/
~~~

运行时使用逻辑资源路径，不要把 `src/main/resources/assets/apricityui/apricity/` 写进 HTML 或 Java 参数。

| 资源 | 逻辑路径 | 用途 |
| --- | --- | --- |
| `ore.css` | `apricityui/theme/ore/ore.css` | 普通业务页面使用的主题入口 |
| `ore-edit.css` | `apricityui/theme/ore/ore-edit.css` | Ore 编辑器和可编辑画布使用的 token 化版本 |
| `example.html` | `apricityui/theme/ore/example.html` | 六个主题展示页面 |
| `ore-edit-example.html` | `apricityui/theme/ore/ore-edit-example.html` | 可编辑 token 的基线示例 |
| `fonts/minecraft-regular.otf` | `apricityui/theme/ore/fonts/minecraft-regular.otf` | `OreRegular` 正文字体 |
| `fonts/minecraft-ten.ttf` | `apricityui/theme/ore/fonts/minecraft-ten.ttf` | `OreDisplay` 标题/控件字体 |
| `readme.md` | `apricityui/theme/ore/readme.md` | 主题原始简要说明 |
| `license.txt` | `apricityui/theme/ore/license.txt` | Mozilla Public License 2.0 文本 |

### 1.1 主题契约

| 项目 | 约定 |
| --- | --- |
| 主题根类 | `.ore-theme` |
| CSS 自定义属性 | `--ore-*` |
| 正文字体 | `OreRegular` |
| 标题和主要控件字体 | `OreDisplay` |
| 普通入口 | `ore.css` |
| 许可证 | MPL-2.0 |

主题的基础元素和组件选择器都以 `.ore-theme` 为范围，因此不会主动重置同一 Document 中没有放在主题根节点下的其他 UI。展示页的 CSS-only 页面切换还包含针对 `#ore-page-*` 和 `#ore-modal-toggle` 的专用 ID 选择器，这些规则只服务于 `example.html` 的示例结构。

## 2. 快速开始

### 2.1 HTML 页面

如果页面与主题文件位于同一个逻辑目录，可以使用相对路径：

~~~html
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <link rel="stylesheet" href="ore.css">
</head>
<body class="ore-theme">
    <main class="container">
        <section class="card">
            <div class="card-header">SETTINGS</div>
            <div class="card-body">
                <p class="text-muted">A themed ApricityUI page.</p>
                <button class="button button-primary" type="button">
                    Apply
                </button>
            </div>
        </section>
    </main>
</body>
</html>
~~~

如果页面位于其他资源目录，使用主题的绝对逻辑路径：

~~~html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
~~~

`href="/apricityui/theme/ore/ore.css"` 中的 `/` 表示 ApricityUI 逻辑资源根，不是 Windows 磁盘根目录。CSS 内部的字体路径是相对于 `ore.css` 解析的，因此不需要额外配置字体路径。

### 2.2 在 ApricityScreen 中打开展示页

主题展示页可以直接作为普通 Screen 页面打开：

~~~java
import com.sighs.apricityui.instance.ApricityScreen;
import net.minecraft.client.Minecraft;

Minecraft.getInstance().setScreen(
        new ApricityScreen("apricityui/theme/ore/example.html")
);
~~~

也可以将主题 CSS 放进自己的页面，再使用自己的 HTML 逻辑路径：

~~~java
Minecraft.getInstance().setScreen(
        new ApricityScreen("screens/settings.html")
);
~~~

页面文件中引用：

~~~html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
    <!-- 页面内容 -->
</body>
~~~

Screen、Overlay 和资源路径的通用规则分别见 [ApricityScreen 使用文档](apricity-screen.md)、[Overlay Document 使用文档](overlay-document.md) 和 [资源管理文档](resource-manager.md)。

### 2.3 Overlay 页面

Ore 主题也可以用于 HUD、工具面板或常驻 Overlay：

~~~java
Document document = ApricityUI.createDocument("overlays/ore-status.html");
if (document == null) {
    return;
}
~~~

~~~html
<head>
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
</head>
<body class="ore-theme">
    <div class="panel p-3">
        <span class="text-muted">STATUS</span>
        <strong>Online</strong>
    </div>
</body>
~~~

`ore.css` 会将主题根设置为深色画布，并使用 `OreRegular` 作为默认字体。Overlay 页面仍然要根据用途决定 `aui-mouse-events` 是否拦截鼠标，不要因为加载主题就默认修改输入策略。

## 3. 字体和基础样式

### 3.1 两个本地字体

`ore.css` 内部包含两个 `@font-face` 声明：

~~~css
@font-face {
  font-family: OreRegular;
  src: url("fonts/minecraft-regular.otf") format("opentype");
}

@font-face {
  font-family: OreDisplay;
  src: url("fonts/minecraft-ten.ttf") format("truetype");
}
~~~

因此主题不依赖 CDN 或外部网络。字体加载失败时会回退到 `sans-serif` 或 `monospace`，但文字宽度和视觉风格会发生变化。

### 3.2 根节点默认值

`.ore-theme` 会设置以下基础行为：

- `box-sizing:border-box` 传播到主题根及其伪元素；
- `min-width:320px`；
- `min-height:100%`；
- `margin:0`；
- 默认文字颜色为 `var(--ore-ink)`；
- 默认背景色为 `var(--ore-canvas)`；
- 默认字体为 `OreRegular`；
- 默认字号为 `var(--ore-font-md)`，即 `16px`；
- 默认行高为 `1.5`；
- 默认字距为 `0`；
- 插入光标颜色为 `var(--ore-green)`。

主题不会给业务页面强制设置 `height:100vh`。需要铺满 Screen 时，可以由页面根布局自行设置高度：

~~~css
.ore-screen-root {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
}
~~~

### 3.3 文字元素

标题 `h1` 到 `h6` 和 `.font-display` 使用 `OreDisplay`，字号如下：

| 元素 | 字号 | 下边距 |
| --- | ---: | ---: |
| `h1` | `36px` | `16px` |
| `h2` | `28px` | `14px` |
| `h3` | `22px` | `12px` |
| `h4` | `18px` | `10px` |
| `h5` | `16px` | `8px` |
| `h6` | `14px` | `8px` |

所有标题的字重为 `400`，行高约为 `1.15`。`p` 默认下边距为 `16px`；`small` 和 `.text-muted` 使用弱化文字颜色；`strong` 使用白色强调。

链接默认使用 `var(--ore-info)`，悬停时变为更亮的蓝色并显示下划线：

~~~html
<p>
    Read the <a href="/help.html">documentation</a>.
</p>
~~~

### 3.4 代码和分隔线

行内 `code` 和 `kbd` 使用深色表面、深色边框和 `OreRegular` 等宽回退字体：

~~~html
<p>Use <code>--ore-green</code> for the confirm color.</p>
<kbd>Ctrl</kbd> + <kbd>S</kbd>
~~~

块级代码可以使用 `.ore-code`：

~~~html
<pre class="ore-code"><code>body class="ore-theme"</code></pre>
~~~

`.ore-code` 支持预格式化换行、自动滚动和深色内嵌阴影。`hr` 或 `.ore-divider` 会显示为上下两条明暗边线。

图片默认 `max-width:100%`，并使用 `image-rendering:pixelated`，适合像素图标和 Minecraft 风格资源：

~~~html
<img src="/apricityui/theme/ore/example.png" alt="Ore icon">
~~~

## 4. 颜色和设计 token

所有稳定主题参数集中在 `.ore-theme` 的 `--ore-*` 自定义属性中。业务页面应优先引用 token，而不是复制主题内部的硬编码颜色，这样才能在 Ore 编辑器中统一调整，也能保持同一主题下的视觉一致性。

### 4.1 文字、表面和边缘

| Token | 默认值 | 用途 |
| --- | --- | --- |
| `--ore-ink` | `#f4f5f7` | 主文字 |
| `--ore-ink-muted` | `#b6bac1` | 弱化文字、辅助说明 |
| `--ore-ink-dark` | `#191a1c` | 浅色导航栏或浅色按钮上的深色文字 |
| `--ore-canvas` | `#202124` | 页面画布背景 |
| `--ore-surface` | `#48494a` | 卡片、面板表面 |
| `--ore-surface-deep` | `#313233` | 深色表面、输入框、标题区 |
| `--ore-surface-soft` | `#d0d1d4` | 浅色次级按钮表面 |
| `--ore-edge` | `#1e1e1f` | 主要边框 |
| `--ore-edge-light` | `#77797c` | 高亮边缘、辅助边界 |
| `--ore-focus` | `#ffffff` | 焦点轮廓和高亮点 |

### 4.2 操作色和状态色

| Token | 默认值 | 用途 |
| --- | --- | --- |
| `--ore-green` | `#3c8527` | 主操作、确认、活动状态 |
| `--ore-green-hover` | `#2a641c` | 主操作悬停 |
| `--ore-green-shadow` | `#1d4d13` | 主操作底部阴影 |
| `--ore-purple` | `#7345e5` | 次级操作、选择状态、编辑器选中框 |
| `--ore-purple-hover` | `#5d2cc6` | 次级操作悬停 |
| `--ore-purple-shadow` | `#4a1cac` | 次级操作阴影、编辑器调整手柄边框 |
| `--ore-gold` | `#f0b92d` | 辅助线、警告强调、悬停辅助状态 |
| `--ore-gold-shadow` | `#936715` | 金色辅助线阴影 |
| `--ore-red` | `#b33b31` | 危险操作 |
| `--ore-red-hover` | `#8b2923` | 危险操作悬停 |
| `--ore-red-shadow` | `#662019` | 危险操作阴影 |
| `--ore-blue` | `#2d78a8` | 信息徽章和蓝色强调 |

| Token | 默认值 | 用途 |
| --- | --- | --- |
| `--ore-success` | `#69ad45` | 成功反馈 |
| `--ore-warning` | `#f0b92d` | 警告反馈 |
| `--ore-danger` | `#d45b50` | 错误/危险反馈 |
| `--ore-info` | `#58a6d2` | 信息反馈和链接 |

### 4.3 间距和字号

| Token | 默认值 |
| --- | ---: |
| `--ore-space-1` | `4px` |
| `--ore-space-2` | `8px` |
| `--ore-space-3` | `16px` |
| `--ore-space-4` | `24px` |
| `--ore-space-5` | `32px` |
| `--ore-font-sm` | `13px` |
| `--ore-font-md` | `16px` |
| `--ore-font-lg` | `20px` |
| `--ore-font-xl` | `28px` |

自定义页面可以在主题根或后代节点覆写 token：

~~~html
<body class="ore-theme custom-screen">
    <style>
        .custom-screen {
            --ore-green: #4b9f32;
            --ore-space-3: 18px;
        }
    </style>
</body>
~~~

覆写应保持合法 CSS 值。颜色 token 可以使用十六进制、`rgb(...)`、`rgba(...)`、`hsl(...)`、`var(...)` 等 CSS 颜色表达式；间距和字号 token 应使用带单位的长度值。

## 5. 布局类

### 5.1 容器

`.container` 是居中的最大宽度容器：最大宽度 `1180px`，左右内边距 `20px`。

~~~html
<main class="container">
    <!-- 内容宽度最多 1180px，并在页面中居中 -->
</main>
~~~

`.container-fluid` 不限制最大宽度，只保留左右 `20px` 内边距：

~~~html
<main class="container-fluid">
    <!-- 使用完整可用宽度 -->
</main>
~~~

### 5.2 十二列 Grid

`.grid` 创建十二列网格，默认列间距和行间距为 `16px`：

~~~html
<div class="grid">
    <section class="col-8 card">
        <div class="card-body">Main content</div>
    </section>
    <aside class="col-4 card">
        <div class="card-body">Side content</div>
    </aside>
</div>
~~~

可用列类：

~~~text
col-1 col-2 col-3 col-4 col-5 col-6
col-7 col-8 col-9 col-10 col-11 col-12
col-full
~~~

`.col-full` 等价于 `col-12`。网格子项使用 `minmax(0, 1fr)`，长文本不会因为内容的最小宽度强行撑开列；需要继续控制长内容时，配合 `min-width:0` 或 `.w-full`。

### 5.3 Stack、Cluster 和 Split

| 类 | 行为 | 适用场景 |
| --- | --- | --- |
| `.stack` | 垂直 Flex，间距 `12px` | 表单组、纵向按钮和面板内容 |
| `.cluster` | 可换行的横向 Flex，间距 `10px` | 标签、徽章、按钮组 |
| `.split` | 两端对齐的横向 Flex，间距 `16px` | footer 左右操作、标题和操作按钮 |

~~~html
<div class="stack">
    <label class="form-label">Name</label>
    <input class="form-input" type="text">
    <div class="cluster">
        <span class="badge badge-success">READY</span>
        <button class="button button-primary">Save</button>
    </div>
</div>
~~~

`.split` 在窄屏中不会自动变成纵向布局；如果两个操作必须在手机宽度下上下排列，需要在页面 CSS 中增加自己的媒体查询。

## 6. 导航和文字组件

### 6.1 Navbar

`.navbar` 是浅色导航栏，默认具有底部边缘和内嵌阴影。品牌使用 `.navbar-brand`，导航项可以是 `button`、`a` 或 `label`：

~~~html
<nav class="navbar" aria-label="Main navigation">
    <span class="navbar-brand">ORE UI</span>
    <ul class="navbar-nav">
        <li><button class="active" type="button">Overview</button></li>
        <li><button type="button">Settings</button></li>
    </ul>
</nav>
~~~

主题只会为 `.active` 提供活动样式，不会自动切换 `active` 类。业务代码需要在切换页面时维护该类。

### 6.2 Breadcrumb

`.breadcrumb` 是无序或有序列表均可使用的面包屑容器，相邻项之间由 CSS 添加 `>`：

~~~html
<ol class="breadcrumb" aria-label="Breadcrumb">
    <li>ApricityUI</li>
    <li>Theme</li>
    <li>Ore</li>
</ol>
~~~

`.breadcrumb-page span` 默认隐藏，是 `example.html` 用于配合 CSS-only 页面切换的辅助规则。普通业务页面无需使用该类。

## 7. 按钮和操作状态

### 7.1 基础按钮

`.button`、`.form-button` 和 `.page-button` 共用像素化按钮基础样式。默认按钮使用绿色主操作样式：

~~~html
<button class="button" type="button">Create</button>
<button class="form-button" type="submit">Apply</button>
~~~

推荐显式写出变体：

| 类 | 视觉语义 |
| --- | --- |
| `.button-primary` | 主操作；当前 CSS 与普通 `.button` 使用同一绿色基线 |
| `.button-secondary` | 紫色次级操作 |
| `.button-tertiary` | 浅色低强调操作 |
| `.button-danger` | 红色危险操作 |
| `.button-normal` | 更接近原版 Minecraft 控件的灰色按钮 |
| `.button-small` | 紧凑按钮，最小高度 `32px` |
| `.button-wide` | 宽度 `100%` |

~~~html
<div class="cluster">
    <button class="button button-primary" type="button">Confirm</button>
    <button class="button button-secondary" type="button">Inspect</button>
    <button class="button button-tertiary" type="button">Cancel</button>
    <button class="button button-danger" type="button">Delete</button>
</div>
~~~

### 7.2 状态

- `:hover` 会改变颜色或边缘表现；
- `:active` 会调整上下内边距和内嵌阴影，形成按下效果；
- `[disabled]` 或 `.disabled` 会变成灰色禁用样式；
- 主题不会自动设置 `aria-disabled`、提交状态或业务权限。

禁用按钮应使用真实 HTML 属性：

~~~html
<button class="button" type="button" disabled>
    Unavailable
</button>
~~~

`.button-normal` 是独立的原版风格分支，使用 `OreRegular`，而不是主要按钮的 `OreDisplay`。

## 8. Card、Panel 和表单

### 8.1 Card 和 Panel

`.card` 与 `.panel` 具有相同的表面、边框和内嵌阴影。两者都可以拆分为 header、body、footer：

~~~html
<section class="card card-accent-purple">
    <div class="card-header">RESOURCE DETAILS</div>
    <div class="card-body">
        <p>Current resource information.</p>
    </div>
    <div class="card-footer split">
        <span class="text-muted">Read only</span>
        <button class="button button-small">Close</button>
    </div>
</section>
~~~

| 类 | 作用 |
| --- | --- |
| `.card`、`.panel` | 外层表面和边框 |
| `.card-header`、`.panel-header` | 深色标题区，最小高度 `46px` |
| `.card-body`、`.panel-body` | `16px` 内边距内容区 |
| `.card-footer`、`.panel-footer` | 顶部边线和 footer 表面 |
| `.card-accent-green` | 顶部边框使用绿色 |
| `.card-accent-purple` | 顶部边框使用紫色 |
| `.card-accent-gold` | 顶部边框使用金色 |

主题只对单个 card/panel 进行框定。页面大区不需要再包一层装饰性卡片；可以使用 `.container`、`.grid` 和普通布局元素组织页面。

### 8.2 表单字段

表单常用类：

~~~html
<div class="form-group">
    <label class="form-label" for="worldName">World name</label>
    <input class="form-input" id="worldName" type="text">
    <div class="form-help">Shown in the world list.</div>
</div>

<select class="form-select">
    <option>Survival</option>
</select>

<textarea class="form-textarea"></textarea>
~~~

| 类 | 作用 |
| --- | --- |
| `.form-group` | 字段之间的 `16px` 下间距 |
| `.form-label` | 块级标签，标签到控件间距 `6px` |
| `.form-help` | 弱化帮助文本 |
| `.form-input` | 文本、数字等输入框 |
| `.form-select` | 下拉框 |
| `.form-textarea` | 多行输入框，默认最小高度 `116px` |
| `.form-button` | 表单提交按钮风格 |
| `.input-group` | 输入框和按钮横向组合 |
| `.choice-list` | radio/checkbox 可换行组 |
| `.choice` | 单个选项的对齐和间距 |

控件默认深色背景、三像素边框和内嵌阴影；悬停时边框变亮，焦点时使用白色边框和绿色 outline：

~~~html
<div class="input-group">
    <input class="form-input" value="/locate structure">
    <button class="button button-secondary" type="button">Run</button>
</div>
~~~

### 8.3 校验、禁用和选择控件

输入框可以通过类表达业务校验状态：

~~~html
<input class="form-input is-valid" value="Available">
<input class="form-input is-invalid" value="Already exists">
<input class="form-input" value="Locked" disabled>
~~~

`.is-valid` 使用 `--ore-success`，`.is-invalid` 使用 `--ore-danger`。主题只改变边框，不会生成错误文案；错误说明应配合 `.form-help text-danger` 等类自行输出。

radio 和 checkbox 使用浏览器/AUI 控件并设置 `accent-color:var(--ore-green)`：

~~~html
<div class="choice-list">
    <label class="choice">
        <input type="radio" name="mode" checked>
        Survival
    </label>
    <label class="choice">
        <input type="radio" name="mode">
        Creative
    </label>
</div>
~~~

## 9. 数据展示组件

### 9.1 Table

表格建议放在 `.table-wrap` 中，以便宽度不足时滚动：

~~~html
<div class="table-wrap">
    <table class="table">
        <thead>
            <tr><th>Resource</th><th>Amount</th><th>Status</th><th>Layer</th></tr>
        </thead>
        <tbody>
            <tr><td>Iron</td><td>128</td><td>Stable</td><td>32</td></tr>
        </tbody>
    </table>
</div>
~~~

主题将 `thead`、`tbody` 和行设置为 block/grid 组合，并按四列等分：

~~~css
.ore-theme .table tr {
    grid-template-columns: repeat(4, minmax(0, 1fr));
}
~~~

因此内置 `.table` 最适合四列数据。需要两列、三列或五列时，应在业务 CSS 中覆写行的 `grid-template-columns`，并确保 `thead` 与 `tbody` 使用相同列定义。

### 9.2 Badge

徽章用于状态、分类和小型标签：

~~~html
<div class="cluster">
    <span class="badge badge-success">Stable</span>
    <span class="badge badge-warning">Low</span>
    <span class="badge badge-danger">Full</span>
    <span class="badge badge-purple">Rare</span>
</div>
~~~

基础 `.badge` 使用蓝色，变体如下：

| 类 | 背景 |
| --- | --- |
| `.badge-success` | `--ore-green` |
| `.badge-warning` | `--ore-gold`，文字为深色 |
| `.badge-danger` | `--ore-red` |
| `.badge-purple` | `--ore-purple` |

### 9.3 Alert

`.alert` 使用左侧粗色条区分反馈类型：

~~~html
<div class="alert alert-success">The resource was saved.</div>
<div class="alert alert-warning">This action cannot be undone.</div>
<div class="alert alert-danger">The file could not be written.</div>
<div class="alert alert-info">A reload is required.</div>
~~~

变体只负责左侧颜色，不会添加图标、关闭按钮或自动消失。

### 9.4 Progress

进度条由外层 `.progress` 和内层 `.progress-bar` 组成。内层宽度由业务代码设置：

~~~html
<div class="progress" aria-label="Loading progress">
    <div class="progress-bar" style="width:65%"></div>
</div>

<div class="progress progress-purple">
    <div class="progress-bar" style="width:40%"></div>
</div>
~~~

`.progress-purple` 只改变内部进度条为紫色。应同时提供可访问的文本、`aria-valuenow` 等属性，主题不会自动补充。

### 9.5 List group

`.list-group` 适合资源列表、服务器列表和设置项：

~~~html
<ul class="list-group">
    <li class="list-group-item active">
        <span>Builders</span>
        <span>18 / 40</span>
    </li>
    <li class="list-group-item">
        <span>Archive</span>
        <span class="text-muted">Offline</span>
    </li>
</ul>
~~~

`.list-group-item` 默认左右对齐，悬停变亮，`.active` 使用绿色。若内容可能过长，给子项设置 `min-width:0` 和文本截断规则。

## 10. Inventory Slot、Tabs、Modal 和分页

### 10.1 Inventory Slot

`.inventory-grid` 默认是九列、每格 `44px`、间距 `3px` 的网格；`.slot` 是单格外观：

~~~html
<div class="inventory-grid" aria-label="Inventory">
    <div class="slot">1</div>
    <div class="slot">2</div>
    <div class="slot">3</div>
</div>
~~~

主题的 `.slot` 只是视觉方格，不会自动绑定 Minecraft 真实槽位。真实容器页面需要使用 HTML `slot` 和 ApricityContainerScreen 的容器绑定机制，见 [Apricity 容器文档](container.md)。

### 10.2 Tabs

`.tabs` 是标签页底边容器，`.tab` 是单个标签。`.active` 和 `:hover` 都使用紫色顶部内嵌边：

~~~html
<div class="tabs" role="tablist">
    <button class="tab active" type="button" role="tab" aria-selected="true">
        Overview
    </button>
    <button class="tab" type="button" role="tab" aria-selected="false">
        Equipment
    </button>
</div>
~~~

主题不切换内容面板，也不会自动维护 `aria-selected`。键盘导航、活动标签和对应面板需要业务逻辑实现。

### 10.3 Modal 结构

`.modal-backdrop` 默认隐藏，加上 `.open` 后显示；`.modal` 位于遮罩上方：

~~~html
<div class="modal-backdrop open" role="dialog" aria-modal="true" aria-labelledby="confirmTitle">
    <div class="modal">
        <div class="modal-header split">
            <span id="confirmTitle">Save changes?</span>
            <button class="button button-normal button-small" type="button">X</button>
        </div>
        <div class="modal-body">
            <p>Apply the current changes?</p>
        </div>
        <div class="modal-footer split">
            <button class="button button-tertiary" type="button">Cancel</button>
            <button class="button button-primary" type="button">Apply</button>
        </div>
    </div>
</div>
~~~

主题定义了 `z-index:900`、居中布局和 `520px` 默认宽度，但不负责打开、关闭、焦点陷阱、Escape 键或遮罩点击逻辑。需要完整的 Java 弹窗行为时使用 [UI 库文档中的 `DialogWindow`](ui-library.md)。

### 10.4 Pagination

`.pagination` 是居中的可换行列表，`.page-button` 是页码按钮：

~~~html
<ul class="pagination" aria-label="Pages">
    <li><button class="page-button" type="button" disabled>&lt;</button></li>
    <li><button class="page-button active" type="button">1</button></li>
    <li><button class="page-button" type="button">2</button></li>
    <li><button class="page-button" type="button">&gt;</button></li>
</ul>
~~~

`.active` 或 `:hover` 使用紫色，`[disabled]` 使用灰色。页码计算和数据切换由业务代码提供。

## 11. Utility 类

主题提供一组轻量工具类：

| 类 | 效果 |
| --- | --- |
| `.text-left` | 左对齐 |
| `.text-center` | 居中对齐 |
| `.text-right` | 右对齐 |
| `.text-success` | 成功色文字 |
| `.text-warning` | 警告色文字 |
| `.text-danger` | 危险色文字 |
| `.text-info` | 信息色文字 |
| `.text-muted` | 弱化文字 |
| `.font-sm` | `--ore-font-sm` |
| `.font-lg` | `--ore-font-lg` |
| `.font-display` | `OreDisplay` 标题字体 |
| `.hidden` | `display:none` |
| `.invisible` | `visibility:hidden` |
| `.w-full` | `width:100%` |
| `.m-0` | `margin:0` |

间距工具使用主题 token：

~~~text
mt-1 = 4px    mt-2 = 8px    mt-3 = 16px   mt-4 = 24px
mb-1 = 4px    mb-2 = 8px    mb-3 = 16px   mb-4 = 24px
p-1  = 4px    p-2  = 8px    p-3  = 16px   p-4  = 24px
~~~

例如：

~~~html
<section class="card mt-4">
    <div class="card-body p-3 text-center">
        <span class="text-warning font-lg">Pending</span>
    </div>
</section>
~~~

## 12. 响应式行为

`ore.css` 内置两个断点：`900px` 和 `560px`。

### 12.1 900px 以下

- `col-3` 到 `col-9` 会折叠为十二列宽度；
- navbar 变为纵向布局；
- navbar 导航区域允许水平滚动；
- showcase hero 变为纵向排列；
- inventory grid 从九列变为六列。

### 12.2 560px 以下

- `.container` 和 `.container-fluid` 左右内边距变为 `12px`；
- `h1` 缩小为 `30px`；
- `.grid` 变为单列；
- 带 `col-*` 的元素变为单列；
- `.split` 变为纵向排列并拉伸；
- `.button` 宽度变为 `100%`；
- inventory grid 变为四列；
- showcase mark 缩小到 `88px`。

自定义页面如果使用 `.ore-showcase`，可以直接获得展示页的这套响应式规则。普通页面的复杂表格、长按钮组和固定宽度弹窗仍需要自己检查小窗口布局。

## 13. 内置展示页面

### 13.1 `example.html`

展示页位于：

~~~text
apricityui/theme/ore/example.html
~~~

它包含六个 CSS-only 页面：

| 页面 | 内容 |
| --- | --- |
| Foundations | 主题介绍、颜色 token、字体和基础元素 |
| Actions | 按钮变体、按钮尺寸、Tabs 和分页 |
| Forms | 输入框、选择框、textarea、校验和组合控件 |
| Data | Card、Table、List、Badge、Slot |
| Feedback | Alert、Progress、状态反馈和 Modal |
| Layout | 十二列 Grid、工具类和接入示例 |

展示页使用 radio/checkbox 和 CSS 选择器切换页面、打开 Modal。这些行为是展示页自己的实现，不是 `ore.css` 提供的通用运行时功能。

### 13.2 `ore-edit-example.html`

可编辑 token 基线页位于：

~~~text
apricityui/theme/ore/ore-edit-example.html
~~~

它使用：

~~~html
<link rel="stylesheet" href="ore-edit.css">
<body class="ore-theme ore-edit-theme ore-showcase">
~~~

该页面用于确认 token 化主题与普通 `ore.css` 的视觉结果一致，不是业务编辑器的入口。需要使用可视化编辑器时，应通过 Ore 编辑器入口打开 `editor/ore/ore-editor.html`，而不是手动把编辑器辅助节点拼接到业务页面。

## 14. Ore 编辑器和 `ore-edit.css`

### 14.1 两个 CSS 文件的边界

| 文件 | 主要用途 | 变量形式 |
| --- | --- | --- |
| `ore.css` | 普通主题页面 | 直接使用颜色、尺寸和 `--ore-*` token |
| `ore-edit.css` | 可编辑画布、编辑器基线 | 将稳定值映射到额外的 `--ore-edit-*` 变量，再提供同样的 `--ore-*` 接口 |

`ore-edit.css` 的注释说明它由生成脚本从 `ore.css` 生成。它不是一个新的视觉主题，而是为了让编辑器能够改变 token，同时保持其他组件样式与普通 Ore 主题一致。

普通业务页面优先使用 `ore.css`。Ore 编辑器的画布使用 `.ore-theme ore-edit-theme`，并加载 `ore-edit.css`。其中 `.ore-edit-theme` 是编辑器的标记类，实际主题规则仍然主要以 `.ore-theme` 为选择器范围。

### 14.2 编辑器页面资源

内置编辑器模板：

~~~text
apricityui/editor/ore/ore-editor.html
apricityui/editor/ore/ore-editor.css
~~~

模板的 head 同时加载：

~~~html
<link rel="stylesheet" href="../../apricityui/theme/ore/ore.css">
<link rel="stylesheet" href="../../apricityui/theme/ore/ore-edit.css">
<link rel="stylesheet" href="ore-editor.css">
~~~

画布节点为：

~~~html
<div id="editorCanvas"
     class="ore-theme ore-edit-theme editor-canvas"
     data-editor-canvas="1">
</div>
~~~

`ore-editor.css` 只负责编辑器工作区、侧栏、选中框、悬停框、Flex 辅助线和调整手柄；按钮、表单、卡片、表格等基础控件仍由 Ore 主题提供。

### 14.3 主题编辑面板

Ore 编辑器的 `THEME` 模式将 35 个可编辑 token 分为五组：

| 分组 | token 数量 | 内容 |
| --- | ---: | --- |
| Typography | 7 | 文字颜色、弱化颜色、深色文字、四档字号 |
| Surfaces | 7 | 画布、表面、深色表面、浅色表面、边缘和焦点 |
| Actions | 12 | 绿色、紫色、金色、红色、蓝色及对应悬停/阴影 |
| Feedback | 4 | success、warning、danger、info |
| Spacing | 5 | 五档间距 |

可编辑项的默认值与 [颜色和设计 token](#4-颜色和设计-token) 相同。颜色 token 提供颜色输入和 Alpha 滑块，普通 CSS token 提供文本输入。编辑器会检查 CSS 值的基本格式，非法值会标记为 `is-invalid`，不会直接应用。

每个 token 都有独立的重置按钮；每个分组可以整体重置；底部还有重置全部主题按钮。主题修改进入 Ore 编辑器的 Undo/Redo 历史，并会将文档标记为未保存。

### 14.4 OreTheme 模型

编辑器项目使用 `OreTheme` 保存覆盖值：

~~~java
OreTheme theme = project.theme();
theme.set("--ore-green", "#4b9f32");
theme.set("--ore-font-md", "17px");

String inlineCss = theme.toCss();
// --ore-green:#4b9f32;--ore-font-md:17px;
~~~

调用 `set(token, null)` 或传入空值会删除覆盖；`reset()` 会删除全部覆盖。`toCss()` 只生成当前覆盖项，不会生成默认 token。

源码位置：

~~~text
src/main/java/com/sighs/apricityui/editor/ore/model/OreTheme.java
~~~

### 14.5 编辑器中的运行方式

Java 侧可以使用 `OreEditor` 门面：

~~~java
import com.sighs.apricityui.editor.ore.OreEditor;

if (!OreEditor.isOpen()) {
    OreEditor.open();
}
~~~

常用 API：

| API | 行为 |
| --- | --- |
| `OreEditor.open()` | 打开内置 Ore 编辑器 |
| `OreEditor.toggle()` | 切换打开/关闭 |
| `OreEditor.close()` | 关闭；有未保存更改时先确认 |
| `OreEditor.isOpen()` | 查询编辑器是否打开 |
| `OreEditor.getDocument()` | 获取编辑器 Document；未打开时为 `null` |
| `OreEditor.getSession()` | 获取编辑器会话状态 |
| `OreEditor.loadSavedProject()` | 加载编辑器保存的项目 |
| `OreEditor.openHtml(Path)` | 打开本地 HTML 为可编辑 Ore 项目 |

Ore 编辑器与内置 DevTools 的关系和打开入口可以参考 [DevTools 文档](devtools.md)。DevTools 中的 Ore 文件选择入口只接受本地 HTML，资源包 HTML 没有本地写入路径时不能直接编辑保存。

## 15. Ore 项目保存和 HTML 导出

### 15.1 编辑器项目 JSON

点击 Ore 编辑器的保存操作时，如果当前不是从某个本地 HTML 打开的项目，项目会保存为：

~~~text
<Minecraft 游戏目录>/apricity/ore-projects/untitled.ore.json
~~~

项目 JSON 使用格式标识和版本号：

~~~json
{
  "format": "ore-editor-project",
  "version": 1,
  "root": {},
  "theme": {},
  "documentMetadata": {}
}
~~~

其中 `theme` 保存 token 覆盖，`documentMetadata` 保存 doctype、head 内容、body 脚本及 HTML/body 属性。编辑器装饰节点、选中框和拖动辅助节点不会写入项目。

### 15.2 导出 HTML

导出文件默认写入：

~~~text
<Minecraft 游戏目录>/apricity/ore-projects/untitled.html
~~~

导出器会：

1. 从项目树生成普通 HTML 元素和嵌套结构；
2. 保留项目的 doctype、HTML 属性、body 属性、head 内容和 body 脚本；
3. 确保 body 含有 `ore-theme` 类；
4. 将 `OreTheme.toCss()` 生成的 token 覆盖写入 body 内联 style；
5. 在 head 中没有 `ore-edit.css` 时追加该主题样式表；
6. 为组件的 hover、active、focus、disabled 样式生成临时 CSS 规则；
7. 不写入编辑器 ID、选中框、拖动 ghost 和辅助线。

导出是可运行的普通 AUI HTML，不需要 Ore 编辑器才能显示。但是它默认带有 `ore-theme` 和 `ore-edit.css`，如果要把导出文件作为长期维护的普通页面，可以根据项目需求保留 token 化 CSS，或手动整理 head 中的主题引用。

### 15.3 打开本地 HTML 的边界

`OreEditor.openHtml(Path)` 只接受真实存在的本地常规文件。导入时：

- `<script>` 会在临时解析文档中被移除，避免把用户文件脚本当作编辑器导入阶段代码执行；
- body 下可解析的元素会转为 Ore 容器或组件节点；
- `script`、`style`、`link` 节点不作为画布节点；
- body 内联 style 中名称以 `--ore-` 开头的属性会作为主题覆盖读入；
- 外部 `ore.css` 计算出来的默认值不会被当作 body 内联覆盖读入；
- head、doctype、HTML/body 属性和 body 脚本会被保存到项目元数据。

因此，想让导入器识别自定义主题值，应写在 body 的内联 style 中，或者在 Ore 编辑器的 Theme 面板中设置：

~~~html
<body class="ore-theme" style="--ore-green:#4b9f32;">
    <main>Editable content</main>
</body>
~~~

## 16. 自定义和覆写建议

### 16.1 通过自定义类扩展

推荐让业务样式继续挂在 `.ore-theme` 下：

~~~css
.ore-theme .resource-toolbar {
    display: flex;
    align-items: center;
    gap: var(--ore-space-2);
    padding: var(--ore-space-3);
    border: 2px solid var(--ore-edge);
    color: var(--ore-ink);
    background: var(--ore-surface-deep);
}
~~~

这样能避免无意中影响同一 Document 中的其他工具层，也能继续使用主题 token。

### 16.2 覆写已有组件

如果需要改变按钮尺寸，应覆盖业务页面自己的作用域，并补齐交互状态：

~~~css
.ore-theme .resource-toolbar .button {
    min-width: 128px;
}

.ore-theme .resource-toolbar .button:hover {
    background: var(--ore-green-hover);
}
~~~

不要只覆盖默认背景而忘记 `:hover`、`:active` 和 `[disabled]`，否则控件会出现状态不一致。

### 16.3 引入顺序

普通页面通常只需要：

~~~html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<link rel="stylesheet" href="screens/settings.css">
~~~

自定义业务 CSS 应放在主题之后，以便使用同等选择器优先级进行覆写。Ore 编辑器模板是例外，它需要同时加载 `ore.css`、`ore-edit.css` 和编辑器专属 CSS；普通业务页面不应无理由同时加载两个主题入口。

### 16.4 不要依赖硬编码内部值

以下是主题的公共契约：`.ore-theme`、`--ore-*` 和文档列出的组件类。`ore-edit.css` 内部的 `--ore-edit-color-*`、`--ore-edit-size-*`、`--ore-edit-alpha-*` 是生成实现细节，不应作为普通业务页面的稳定 API。

## 17. 许可证和分发

Ore CSS 文件和两个字体资源的目录旁边提供了 `license.txt`，主题 README 说明其 CSS 源自 Minecraft-CSS，并按 Mozilla Public License 2.0 分发。使用、修改或随模组分发 Ore 资源时，应保留许可证和源代码声明，不要删除 `license.txt` 或 CSS 文件顶部的来源说明。

许可证文件：

~~~text
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/license.txt
~~~

本文不替代许可证原文。分发修改后的 Ore CSS、字体或包含它们的更大作品时，以随资源提供的 MPL-2.0 文本和适用法律为准。

## 18. 常见问题

### 18.1 加载 CSS 后页面没有 Ore 风格

检查三项：

1. `<link>` 是否指向逻辑路径 `apricityui/theme/ore/ore.css`；
2. 顶层元素是否有 `class="ore-theme"`；
3. 当前 HTML 是否已经完成资源重新扫描。

正确的最小组合是：

~~~html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
    <button class="button button-primary">Test</button>
</body>
~~~

### 18.2 字体没有加载

不要把字体路径写成项目磁盘路径。`ore.css` 已经通过相对 `fonts/` 路径引用字体。查看日志中的资源加载错误，并确认主题目录包含：

~~~text
fonts/minecraft-regular.otf
fonts/minecraft-ten.ttf
~~~

主题仍会回退到系统字体，因此“页面有颜色但字体不像 Ore”通常是字体资源没有正确解析。

### 18.3 `.button` 没有点击行为

这是预期行为。Ore 是纯 CSS 主题，只定义外观、悬停、按下和禁用状态。需要业务代码注册 click 监听器，或使用 ApricityUI 内置的 Java/DOM 组件。

### 18.4 Modal 加上 `.open` 仍然无法关闭

`.modal-backdrop.open` 只负责显示。关闭需要移除 `.open` 或由业务代码移除/隐藏节点；焦点返回、Escape 和遮罩点击都要自行处理。想要 Java 生命周期、拖动、最大化和调整大小，使用 `DialogWindow`。

### 18.5 表格列错位

内置 `.table` 按四列网格布局。检查 `thead` 和 `tbody` 是否拥有相同数量的单元格，并在自定义列数时同时覆写 `tr` 的 `grid-template-columns`。

### 18.6 Ore 编辑器中的颜色改了但导出后不生效

检查导出 HTML 是否包含：

~~~html
<link rel="stylesheet" href=".../ore-edit.css">
<body class="ore-theme" style="--ore-green:...;">
~~~

编辑器主题覆盖写入 body 内联 style；如果手动删除了这些属性，页面会恢复 CSS 默认值。也要确认 body 的 `ore-theme` 类没有被替换掉。

### 18.7 主题页面重载后样式变化

资源修改后已有 Document 不一定自动变成新 DOM。开发时使用页面或 DevTools 的 reload 能力重新读取 HTML/CSS；如果是持久化 Overlay，还要确认你的刷新策略会重新应用样式。详见 [DevTools 文档](devtools.md) 和 [浏览器辅助功能文档](browser-features.md)。

## 19. 相关源码和测试

主题资源：

~~~text
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore-edit.css
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/example.html
src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore-edit-example.html
src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.html
src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.css
~~~

编辑器实现：

~~~text
src/main/java/com/sighs/apricityui/editor/ore/OreEditor.java
src/main/java/com/sighs/apricityui/editor/ore/OreEditorController.java
src/main/java/com/sighs/apricityui/editor/ore/model/OreTheme.java
src/main/java/com/sighs/apricityui/editor/ore/persistence/OreEditorProjectCodec.java
src/main/java/com/sighs/apricityui/editor/ore/persistence/OreEditorHtmlImporter.java
src/main/java/com/sighs/apricityui/editor/ore/persistence/OreEditorHtmlExporter.java
~~~

相关测试：

~~~text
src/test/java/com/sighs/apricityui/theme/OreEditThemeTest.java
src/test/java/com/sighs/apricityui/editor/ore/OreEditorThemeTest.java
src/test/java/com/sighs/apricityui/webapi/LayoutPositionTest.java
~~~

相关文档：

- [ApricityScreen 使用文档](apricity-screen.md)
- [Apricity 容器使用文档](container.md)
- [Overlay Document 使用文档](overlay-document.md)
- [内置 UI 库文档](ui-library.md)
- [内置 DevTools 文档](devtools.md)
- [资源形式、资源路径、资源管理和内置资源管理器](resource-manager.md)
