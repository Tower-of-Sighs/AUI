STYLEKIT_STYLE_REFERENCE
style_name: 终端风
style_slug: developer-terminal
style_source: /styles/developer-terminal

# Hard Prompt

## 什么时候用
当你希望 AI 严格按风格规则生成代码时使用。它是生产界面最稳的默认选择。

## 怎么用
- 把完整提示词复制到 ChatGPT、Claude、Cursor 或其他编码助手。
- 在提示词后追加具体产品、页面或组件需求。
- 生成后按禁止项和交互状态检查，确认没有风格漂移。

请严格遵守以下风格规则并保持一致性，禁止风格漂移。

## 执行要求

- 优先保证风格一致性，其次再做创意延展。
- 遇到冲突时以禁止项为最高优先级。
- 输出前自检：颜色、排版、间距、交互是否仍属于该风格。

## Style Rules

你是一位专精于 Developer Terminal（终端风）的设计专家。生成的所有代码都必须严格遵守以下约束：

## 绝对禁止

- 禁止任何 sans-serif 或衬线字体（font-sans、font-serif）
- 禁止白色或浅色背景（bg-white、bg-gray-50）
- 禁止渐变（bg-gradient-*）与玻璃拟态（backdrop-blur）
- 禁止摄影图像与插画装饰（终端里只有字符）
- 禁止超过 4 种终端强调色（青/品红/琥珀/蓝灰之外不再加色）
- 禁止厚阴影 shadow-md 及以上（终端是平面的）

## 必须遵守

- 全站使用等宽字体 font-mono，无一例外
- 近黑背景 bg-[#0A0E12] 搭配荧光绿主文字 text-[#4AF626]
- 章节标题用 prompt 前缀开头：visitor@stylekit:~$ 命令名
- 标题或输入位使用闪烁光标块 w-2 h-4 bg-[#4AF626]
- 内容组织为命令 + 输出的成对结构，注释行用 # 开头并使用 text-[#6272A4]
- 列表使用 ls -la 式对齐输出，数据表使用 ps aux 式表头
- 状态用 exit code 徽标表达：[0] OK 绿色 / [1] ERR 品红
- 面板使用 border border-[#1F2937] rounded-sm，底部放 tmux 式状态栏
- 分隔使用 ASCII 线（──────）而非装饰性 divider

## 颜色语义
- #4AF626 绿：主体输出、成功状态、prompt 命令
- #8BE9FD 青：路径、链接、用户名、提示信息
- #FF79C6 品红：关键字、错误、危险操作
- #FFB86C 琥珀：字符串、数字、警告
- #6272A4 蓝灰：注释、次要信息、非活动项

## 动效与交互
- 光标闪烁使用 steps() 或 animate-pulse，周期约 1 秒
- hover 只改变背景透明度（hover:bg-[#4AF626]/10）或边框亮度，不做位移与缩放
- 可选微弱的 scanline 扫描线纹理（repeating-linear-gradient，透明度不超过 0.03）
- transition 统一使用 transition-colors duration-150

## 响应式
- 移动端：整页仍保持单列终端窗口，字号 text-xs/text-sm，表格横向滚动
- 桌面端：终端窗口最大宽度约 max-w-5xl 并居中，保持 80 列的排版质感

## 自检清单
1. 页面中没有任何非 mono 字体
2. 没有渐变、玻璃拟态、厚阴影或图片
3. 每个区块都能读成一条命令的输出
4. 颜色只承担语义，不做装饰
5. 底部有 tmux 状态栏，且至少存在一个闪烁光标

---

# Developer Terminal (终端风) Design System

> 把网站做成一场终端会话：荧光绿 mono 文字、prompt 前缀、命令与输出的成对结构、tmux 状态栏，用工具的真实感表达开发者身份。

## 核心理念

Developer Terminal 不是在模仿终端的样子，而是在借用终端的语言。对开发者来说，终端是每天生活的地方——最诚实、最高效、零装饰的工具。把个人网站做成一场终端会话，等于宣告：我的身份不需要营销页面来证明，工具本身就是我的名片。

核心理念：
- 内容即输出：每一段内容都是某条命令的执行结果。访客不是在"浏览页面"，而是在"读取会话"——标题是 prompt，正文是 stdout，错误提示是 stderr
- 工具的真实感：真实的终端没有渐变、没有阴影、没有摄影图。它只有等宽字符、光标和颜色语义。这种克制不是贫乏，而是专业性本身
- 颜色即语法高亮：荧光绿是主体输出，青色是路径与链接，品红是关键字与错误，琥珀是字符串与警告，蓝灰是注释。颜色不做装饰，只做语义
- 会话感：闪烁的光标暗示"会话仍在进行"，tmux 状态栏暗示"系统仍在运行"。页面不是静态文档，而是一个活着的进程

这种风格适用于开发者个人主页、技术博客、CLI 工具官网、API 文档、黑客马拉松页面，以及任何想向技术受众传递"我们说同一种语言"的产品。

终端美学的悖论在于：它看起来最"不设计"，却要求最严格的设计纪律——一旦出现一个 sans-serif 字符或一块渐变，整个幻觉就崩塌了。

---

## Token 字典（精确 Class 映射）

### 边框
```
宽度: border
颜色: border-[#1F2937]
圆角: rounded-sm
```

### 阴影
```
小: shadow-none
中: shadow-none
大: shadow-none
悬停: hover:shadow-none
聚焦: ring-1 ring-[#4AF626]/60
```

### 交互效果
```
悬停位移: （无）
悬停缩放: hover:bg-[#4AF626]/10
悬停透明度: （无）
过渡动画: transition-colors duration-150
按下状态: active:bg-[#4AF626]/20
```

### 字体
```
标题: font-mono font-bold tracking-tight
正文: font-mono
等宽: font-mono
```

### 字号
```
Hero: text-2xl md:text-4xl
H1: text-xl md:text-2xl
H2: text-lg md:text-xl
H3: text-base md:text-lg
正文: text-sm
小字: text-xs
```

### 间距
```
Section: py-8 md:py-12
容器: px-4 md:px-6
卡片: p-4
小间距: gap-2
中间距: gap-3
大间距: gap-5
```

### 颜色角色
```
背景主色: bg-[#0A0E12]
背景辅色: bg-[#0D141B]
背景强调色: bg-[#4AF626], bg-[#8BE9FD], bg-[#FF79C6], bg-[#FFB86C]
正文主色: text-[#4AF626]
正文辅色: text-[#8BE9FD]
正文弱化色: text-[#6272A4]
按钮主色: bg-[#4AF626] text-[#0A0E12] hover:bg-[#3FD41F]
按钮辅色: bg-transparent text-[#4AF626] border border-[#1F2937] hover:border-[#4AF626]/60
```

---

## [FORBIDDEN] 绝对禁止

以下 class 在本风格中**绝对禁止使用**，生成时必须检查并避免：

### 禁止的 Class
- `font-sans`
- `font-serif`
- `bg-white`
- `bg-gray-50`
- `rounded-lg`
- `rounded-xl`
- `rounded-2xl`
- `rounded-full`
- `shadow-md`
- `shadow-lg`
- `shadow-xl`
- `backdrop-blur`

### 禁止的模式
- 匹配 `^font-(sans|serif)`
- 匹配 `^bg-(white|gray-50|gray-100|slate-50)`
- 匹配 `^rounded-(lg|xl|2xl|3xl|full)`
- 匹配 `^shadow-(md|lg|xl|2xl)`
- 匹配 `^bg-gradient-`
- 匹配 `^backdrop-blur`

### 禁止原因
- `font-sans`: Developer Terminal is monospace-only; every glyph must be font-mono
- `bg-white`: Terminals are near-black; use bg-[#0A0E12] or bg-[#0D141B]
- `rounded-lg`: Terminal chrome stays flat and sharp; rounded-sm is the maximum radius
- `shadow-md`: Terminals are flat surfaces; no elevation shadows
- `backdrop-blur`: No glassmorphism; a terminal renders opaque character cells

> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。

---

## [REQUIRED] 必须包含

### 按钮必须包含
```
font-mono
text-sm
rounded-sm
transition-colors duration-150
```

### 卡片必须包含
```
font-mono
bg-[#0D141B]
border border-[#1F2937]
rounded-sm
```

### 输入框必须包含
```
font-mono
bg-[#0A0E12]
border border-[#1F2937]
rounded-sm
text-[#4AF626]
caret-[#4AF626]
placeholder:text-[#6272A4]
focus:outline-none focus:border-[#4AF626]/60
```

---

## [COMPARE] Developer Terminal 错误 vs 正确对比

以下错误示例只代表“未经过当前风格适配的通用默认值”，不要把错误示例当成视觉建议。

### 按钮

[WRONG] **错误示例**（通用组件库默认样式，不要直接复制）：
```html
<button class="{GENERIC_LIBRARY_BUTTON_DEFAULT}">
  点击我
</button>
```

[CORRECT] **正确示例**（使用当前风格的 token）：
```html
<button class="font-mono text-sm rounded-sm transition-colors duration-150 bg-[#4AF626] text-[#0A0E12] hover:bg-[#3FD41F]">
  点击我
</button>
```

### 卡片

[WRONG] **错误示例**（未经当前风格适配的通用卡片）：
```html
<div class="{GENERIC_LIBRARY_CARD_DEFAULT}">
  <h3>{TITLE}</h3>
</div>
```

[CORRECT] **正确示例**（使用当前风格的 card token）：
```html
<div class="font-mono bg-[#0D141B] border border-[#1F2937] rounded-sm p-4">
  <h3 class="font-mono font-bold tracking-tight text-base md:text-lg">{TITLE}</h3>
</div>
```

### 输入框

[WRONG] **错误示例**（未经当前风格适配的通用输入框）：
```html
<input class="{GENERIC_LIBRARY_INPUT_DEFAULT}" />
```

[CORRECT] **正确示例**（使用当前风格的 input token）：
```html
<input class="font-mono bg-[#0A0E12] border border-[#1F2937] rounded-sm text-[#4AF626] caret-[#4AF626] placeholder:text-[#6272A4] focus:outline-none focus:border-[#4AF626]/60" placeholder="{PLACEHOLDER}" />
```

---

## [TEMPLATES] Developer Terminal 页面骨架模板

以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：

### 导航栏骨架
```html
<nav class="bg-[#0A0E12] text-[#4AF626] border border-[#1F2937] px-4 md:px-6">
  <div class="flex items-center justify-between max-w-6xl mx-auto gap-3">
    <a href="/" class="font-mono font-bold tracking-tight text-base md:text-lg">
      {LOGO_TEXT}
    </a>
    <div class="flex gap-3 font-mono text-xs">
      {NAV_LINKS}
    </div>
  </div>
</nav>
```

### Hero 区块骨架
```html
<section class="bg-[#4AF626] text-[#4AF626] py-8 md:py-12 px-4 md:px-6">
  <div class="max-w-4xl mx-auto">
    <h1 class="font-mono font-bold tracking-tight text-2xl md:text-4xl">
      {HEADLINE}
    </h1>
    <p class="font-mono text-sm max-w-xl">
      {SUBHEADLINE}
    </p>
    <button class="font-mono text-sm rounded-sm transition-colors duration-150 bg-[#4AF626] text-[#0A0E12] hover:bg-[#3FD41F]">
      {CTA_TEXT}
    </button>
  </div>
</section>
```

### 卡片网格骨架
```html
<section class="bg-[#0A0E12] text-[#4AF626] py-8 md:py-12 px-4 md:px-6">
  <div class="max-w-6xl mx-auto">
    <h2 class="font-mono font-bold tracking-tight text-lg md:text-xl">{SECTION_TITLE}</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
      <!-- Card template - repeat for each card -->
      <div class="font-mono bg-[#0D141B] border border-[#1F2937] rounded-sm p-4">
        <h3 class="font-mono font-bold tracking-tight text-base md:text-lg">{CARD_TITLE}</h3>
        <p class="font-mono text-sm text-[#6272A4]">{CARD_DESCRIPTION}</p>
      </div>
    </div>
  </div>
</section>
```

### 表单输入骨架
```html
<input class="font-mono bg-[#0A0E12] border border-[#1F2937] rounded-sm text-[#4AF626] caret-[#4AF626] placeholder:text-[#6272A4] focus:outline-none focus:border-[#4AF626]/60" placeholder="{PLACEHOLDER}" />
```

### 页脚骨架
```html
<footer class="bg-[#0D141B] text-[#8BE9FD] py-8 md:py-12 px-4 md:px-6">
  <div class="max-w-6xl mx-auto">
    <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
      <div>
        <span class="font-mono font-bold tracking-tight text-base md:text-lg">{LOGO_TEXT}</span>
        <p class="font-mono text-xs">{TAGLINE}</p>
      </div>
      <div>
        <h4 class="font-mono font-bold tracking-tight text-base md:text-lg">{COLUMN_TITLE}</h4>
        <ul class="font-mono text-xs">
          {FOOTER_LINKS}
        </ul>
      </div>
    </div>
  </div>
</footer>
```

---

## [CHECKLIST] Developer Terminal 生成后自检清单

**输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：**

### Token 检查
- [ ] 按钮包含： `font-mono text-sm rounded-sm transition-colors duration-150`
- [ ] 卡片包含： `font-mono bg-[#0D141B] border border-[#1F2937] rounded-sm`
- [ ] 输入框包含： `font-mono bg-[#0A0E12] border border-[#1F2937] rounded-sm text-[#4AF626] caret-[#4AF626] placeholder:text-[#6272A4] focus:outline-none focus:border-[#4AF626]/60`

### 禁止项检查
- [ ] 没有使用 `font-sans`
- [ ] 没有使用 `font-serif`
- [ ] 没有使用 `bg-white`
- [ ] 没有使用 `bg-gray-50`
- [ ] 没有使用 `rounded-lg`
- [ ] 没有使用 `rounded-xl`
- [ ] 没有使用 `rounded-2xl`
- [ ] 没有使用 `rounded-full`

### 风格规则检查
- [ ] 全站使用等宽字体 font-mono，无一例外
- [ ] 近黑背景 bg-[#0A0E12] 搭配荧光绿主文字 text-[#4AF626]
- [ ] 章节标题用 prompt 前缀开头：visitor@stylekit:~$ 命令名
- [ ] 标题或输入位使用闪烁光标块 w-2 h-4 bg-[#4AF626]
- [ ] 内容组织为命令 + 输出的成对结构，注释行用 # 开头并使用 text-[#6272A4]

### 风格漂移检查
- [ ] 没有违反：禁止任何 sans-serif 或衬线字体（font-sans、font-serif）
- [ ] 没有违反：禁止白色或浅色背景（bg-white、bg-gray-50）
- [ ] 没有违反：禁止渐变（bg-gradient-*）与玻璃拟态（backdrop-blur）
- [ ] 没有违反：禁止摄影图像与插画装饰（终端里只有字符）
- [ ] 没有违反：禁止超过 4 种终端强调色（青/品红/琥珀/蓝灰之外不再加色）

### 通用交付检查
- [ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出
- [ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案
- [ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态
- [ ] 结果仍然能够一眼识别为 Developer Terminal

---

## [EXAMPLES] 示例 Prompt

### 1. 开发者个人主页

把个人主页做成一场登录后的终端会话

```
用 Developer Terminal 终端风创建一个开发者个人主页，要求：
1. Hero 是 boot sequence：Last login 行、[ OK ] 自检项、ASCII 站名、带闪烁光标的 prompt
2. 关于我 = whoami 命令的键值对输出，技能 = cat skills.txt
3. 项目列表 = ls -la ~/projects 的对齐输出，每行可点击
4. 联系方式 = printenv CONTACT_* 输出，邮箱与链接用青色 #8BE9FD
5. 底部 tmux 状态栏：会话名、窗口列表、时间
全程 font-mono，背景 #0A0E12，主文字 #4AF626，禁止渐变与图片
```

### 2. CLI 工具官网

命令行工具的产品页，安装命令即 CTA

```
用 Developer Terminal 终端风为一个 CLI 工具设计官网，要求：
1. 顶部会话标签栏（tmux 窗口式导航）：0:install* 1:docs 2:changelog
2. Hero 展示一条可复制的安装命令 npm install -g mytool，配 [0] OK 徽标
3. 功能区 = 三个"命令 + 输出"面板，演示核心用法
4. 版本更新 = git log --oneline 式列表，哈希用琥珀 #FFB86C
5. stderr 风格的注意事项区块（品红 #FF79C6 边框）
面板统一 border-[#1F2937] rounded-sm，分隔用 ASCII 线
```

### 3. 系统监控面板

htop / ps aux 语言的状态监控页

```
用 Developer Terminal 终端风创建一个系统监控面板，要求：
1. 服务列表 = ps aux 式表格：PID、SERVICE、CPU、MEM、STATUS 列
2. 资源占用 = ASCII 进度条 [████████░░] 67%，超过 80% 用琥珀色
3. 日志流 = tail -f 输出区，INFO 青色 / WARN 琥珀 / ERR 品红
4. 每个服务带 exit code 徽标 [0] OK 或 [1] ERR
5. 底部 tmux 状态栏显示 load、uptime、时钟
全 font-mono，禁止图表库，所有可视化用字符完成
```

## 绝对禁止（匹配即拒绝）

以下模式一旦出现，视为风格违规——不找借口，直接重写。

- 任何 sans-serif 或衬线字体（font-sans、font-serif）
- 白色或浅色背景（bg-white、bg-gray-50）
- 渐变（bg-gradient-*）与玻璃拟态（backdrop-blur）
- 摄影图像与插画装饰（终端里只有字符）
- 超过 4 种终端强调色（青/品红/琥珀/蓝灰之外不再加色）
- 厚阴影 shadow-md 及以上（终端是平面的）

## 自检清单（交付前逐条确认）

如果任何一条不通过，说明风格漂移了——修改后再交付。

- [ ] 没有紫色到蓝色的渐变
- [ ] 没有使用 Inter / Roboto / Geist 等过度使用的字体
- [ ] 没有嵌套卡片（卡片里面套卡片）
- [ ] 没有在彩色背景上放灰色文字
- [ ] 正文对比度满足 WCAG AA（≥4.5:1）
- [ ] 没有 bounce / elastic 缓动曲线
- [ ] 动效有 prefers-reduced-motion 备选方案
- [ ] 正文行宽不超过 65-75 个字符
- [ ] 没有单侧粗边框装饰（border-left/right accent stripe）
- [ ] 没有渐变文字（background-clip: text）
- [ ] 没有把玻璃态（glassmorphism）当作默认风格
- [ ] 没有 tiny uppercase tracked eyebrow 放在每个 section 标题上面
- [ ] 禁止任何 sans-serif 或衬线字体（font-sans、font-serif）
- [ ] 禁止白色或浅色背景（bg-white、bg-gray-50）
- [ ] 禁止渐变（bg-gradient-*）与玻璃拟态（backdrop-blur）
- [ ] 禁止摄影图像与插画装饰（终端里只有字符）
- [ ] 禁止超过 4 种终端强调色（青/品红/琥珀/蓝灰之外不再加色）