STYLEKIT\_STYLE\_REFERENCE

style\_name: 机甲风

style\_slug: mecha

style\_source: /styles/mecha



\# Hard Prompt



\## 什么时候用

当你希望 AI 严格按风格规则生成代码时使用。它是生产界面最稳的默认选择。



\## 怎么用

\- 把完整提示词复制到 ChatGPT、Claude、Cursor 或其他编码助手。

\- 在提示词后追加具体产品、页面或组件需求。

\- 生成后按禁止项和交互状态检查，确认没有风格漂移。



请严格遵守以下风格规则并保持一致性，禁止风格漂移。



\## 执行要求



\- 优先保证风格一致性，其次再做创意延展。

\- 遇到冲突时以禁止项为最高优先级。

\- 输出前自检：颜色、排版、间距、交互是否仍属于该风格。



\## Style Rules



You are a Mecha design style frontend development expert. All generated code must strictly follow these constraints:



\## 绝对禁止



\- 禁止使用柔和的圆角设计

\- 禁止使用柔和的粉色或浅色调

\- 禁止使用花哨的渐变或毛玻璃效果

\- 禁止使用手写体或花体字



\## 必须遵守



\- 使用军绿、深蓝海军色为底色

\- 添加警告黄和危险红的强调色

\- 使用等宽字体和大写字母

\- 设计直角无圆角的面板元素

\- 添加技术标注和编号装饰

\- 使用硬边阴影和边框线条



\## Color Palette



Primary:

\- Navy: #1a2744

\- Military Green: #4a5c3a

\- Warning Yellow: #fbbf24

\- Danger Red: #ef4444



\## Special Elements



\- Tech grid backgrounds

\- Warning stripe patterns

\- Status indicators with square dots

\- Technical annotations and unit numbers

\- Panel borders with offset outlines



\## Animation \& Interaction Rules



\- Hydraulic Rigidness: 交互应使用 duration-100\~150 + ease-linear 的机械硬切，不使用柔软弹簧缓动。

\- Armor Shifting: hover 可通过 clip-path 缺角变化与边框厚度突变，模拟装甲板咬合滑动。

\- Hazard Flashing: 关键控件在 hover 需要出现警告色脉冲（红/黄）与斜纹提示，强化工业警戒感。

\- Tactical Lock-on: active 使用干脆的直线位移与阴影回落，像扣动重型扳机后的锁定反馈。



\## Layout \& Spacing

\- Section padding: py-16 md:py-24

\- Card padding: p-6 md:p-8

\- Gap between cards: gap-6 md:gap-8

\- Max content width: max-w-6xl mx-auto



\## Responsive Design

\- Mobile-first approach with Tailwind breakpoints

\- Stack elements vertically on mobile (flex-col), row on desktop (md:flex-row)

\- Reduce font sizes on mobile: text-3xl md:text-5xl for headings

\- Touch-friendly targets: min 44px for interactive elements



\## Self-Check Verification

After generating code, verify:

1\. All interactive elements have hover/focus/active states

2\. Color contrast meets WCAG 2.1 AA (4.5:1 for text)

3\. Layout is responsive across breakpoints

4\. Typography hierarchy is clear (h1 > h2 > h3 > body)

5\. Spacing is consistent using the defined scale

6\. All animations respect prefers-reduced-motion



\---



\# Mecha (机甲风) Design System



> 灵感源自高达/EVA等机甲动画的设计风格，科技面板、警告标识、机械质感，军绿和深蓝底色搭配警告黄和危险红，充满工业力量感。



\## 核心理念



Mecha（机甲风）是源自日本机甲动画（高达、EVA等）的设计美学，融合军事工业风和科幻面板界面。



核心理念：

\- 装甲面板：模拟机甲外壳的分块面板设计

\- 警告系统：黄色警告和红色危险的信号系统

\- 军事工业：军绿、深蓝海军色的工业配色

\- 技术标注：等宽字体的技术参数和编号标识



设计原则：

\- 视觉一致性：所有组件必须遵循统一的视觉语言，从色彩到字体到间距保持谐调

\- 层次分明：通过颜色深浅、字号大小、留白空间建立清晰的信息层级

\- 交互反馈：每个可交互元素都必须有明确的 hover、active、focus 状态反馈

\- 响应式适配：设计必须在移动端、平板、桌面端上保持一致的体验

\- 无障碍性：确保色彩对比度符合 WCAG 2.1 AA 标准，所有交互元素可键盘访问



\---



\## Token 字典（精确 Class 映射）



\### 边框

```

宽度: border-2

颜色: border-\[#4a5c3a]

圆角: rounded-none

```



\### 阴影

```

小: shadow-\[2px\_2px\_0px\_rgba(251,191,36,0.3)]

中: shadow-\[4px\_4px\_0px\_rgba(251,191,36,0.3)]

大: shadow-\[6px\_6px\_0px\_rgba(251,191,36,0.4)]

悬停: hover:shadow-\[6px\_6px\_0px\_rgba(251,191,36,0.5)]

聚焦: focus:shadow-\[0\_0\_8px\_rgba(251,191,36,0.4)]

```



\### 交互效果

```

悬停位移: hover:translate-x-\[2px] hover:translate-y-\[2px]

悬停缩放: （无）

悬停透明度: （无）

过渡动画: transition-all duration-200 ease-in-out

按下状态: active:translate-x-\[4px] active:translate-y-\[4px] active:shadow-none

```



\### 字体

```

标题: font-mono font-bold uppercase tracking-widest

正文: font-mono

等宽: font-mono

```



\### 字号

```

Hero: text-4xl md:text-6xl lg:text-8xl

H1: text-3xl md:text-5xl

H2: text-2xl md:text-4xl

H3: text-xl md:text-2xl

正文: text-sm md:text-base

小字: text-xs md:text-sm

```



\### 间距

```

Section: py-12 md:py-20 lg:py-28

容器: px-4 md:px-8 lg:px-12

卡片: p-5 md:p-8

小间距: gap-3 md:gap-4

中间距: gap-4 md:gap-6

大间距: gap-6 md:gap-10

```



\### 颜色角色

```

背景主色: bg-\[#1a2744]

背景辅色: bg-\[#4a5c3a]

背景强调色: bg-\[#fbbf24], bg-\[#ef4444]

正文主色: text-\[#fbbf24]

正文辅色: text-\[#4a5c3a]

正文弱化色: text-\[#4a5c3a]/70

按钮主色: bg-\[#fbbf24] text-\[#1a2744] border-2 border-\[#1a2744]

按钮辅色: bg-\[#4a5c3a] text-\[#fbbf24] border-2 border-\[#fbbf24]/50

```



\---



\## \[FORBIDDEN] 绝对禁止



以下 class 在本风格中\*\*绝对禁止使用\*\*，生成时必须检查并避免：



\### 禁止的 Class

\- `rounded-lg`

\- `rounded-xl`

\- `rounded-2xl`

\- `rounded-full`

\- `bg-white`

\- `bg-\[#faf6f0]`

\- `bg-\[#fffbf0]`

\- `font-serif`

\- `text-\[#ff00ff]`

\- `text-\[#00ffff]`

\- `shadow-md`

\- `shadow-lg`

\- `backdrop-blur`

\- `bg-pink-`

\- `bg-rose-`

\- `text-pink-`



\### 禁止的模式

\- 匹配 `^rounded-(?:lg|xl|2xl|full)$`

\- 匹配 `^bg-(?:white|pink-|rose-)`

\- 匹配 `^font-serif$`

\- 匹配 `^shadow-(?:md|lg|xl)$`

\- 匹配 `^backdrop-blur`



\### 禁止原因

\- `rounded-lg`: Mecha uses sharp angular shapes (rounded-none) for an industrial feel

\- `bg-white`: Mecha uses dark backgrounds (navy, green) for a military aesthetic

\- `font-serif`: Mecha uses monospace fonts for a technical/military look

\- `shadow-md`: Mecha uses hard-edge offset shadows, not soft ones

\- `backdrop-blur`: Mecha style is opaque and solid, no transparency effects



> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。



\---



\## \[REQUIRED] 必须包含



\### 按钮必须包含

```

rounded-none

font-mono font-bold uppercase tracking-widest

border-2

transition-all duration-200 ease-in-out

```



\### 卡片必须包含

```

rounded-none

bg-\[#1a2744]

border-2 border-\[#4a5c3a]

shadow-\[4px\_4px\_0px\_rgba(251,191,36,0.3)]

```



\### 输入框必须包含

```

rounded-none

border-2 border-\[#4a5c3a]

bg-\[#1a2744]/80

text-\[#fbbf24]

font-mono

focus:outline-none

```



\---



\## \[COMPARE] Mecha 错误 vs 正确对比



以下错误示例只代表“未经过当前风格适配的通用默认值”，不要把错误示例当成视觉建议。



\### 按钮



\[WRONG] \*\*错误示例\*\*（通用组件库默认样式，不要直接复制）：

```html

<button class="{GENERIC\_LIBRARY\_BUTTON\_DEFAULT}">

&#x20; 点击我

</button>

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 token）：

```html

<button class="rounded-none font-mono font-bold uppercase tracking-widest border-2 transition-all duration-200 ease-in-out bg-\[#fbbf24] text-\[#1a2744] border-2 border-\[#1a2744]">

&#x20; 点击我

</button>

```



\### 卡片



\[WRONG] \*\*错误示例\*\*（未经当前风格适配的通用卡片）：

```html

<div class="{GENERIC\_LIBRARY\_CARD\_DEFAULT}">

&#x20; <h3>{TITLE}</h3>

</div>

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 card token）：

```html

<div class="rounded-none bg-\[#1a2744] border-2 border-\[#4a5c3a] shadow-\[4px\_4px\_0px\_rgba(251,191,36,0.3)] p-5 md:p-8">

&#x20; <h3 class="font-mono font-bold uppercase tracking-widest text-xl md:text-2xl">{TITLE}</h3>

</div>

```



\### 输入框



\[WRONG] \*\*错误示例\*\*（未经当前风格适配的通用输入框）：

```html

<input class="{GENERIC\_LIBRARY\_INPUT\_DEFAULT}" />

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 input token）：

```html

<input class="rounded-none border-2 border-\[#4a5c3a] bg-\[#1a2744]/80 text-\[#fbbf24] font-mono focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\---



\## \[TEMPLATES] Mecha 页面骨架模板



以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：



\### 导航栏骨架

```html

<nav class="bg-\[#1a2744] text-\[#fbbf24] border-2 border-\[#4a5c3a] px-4 md:px-8 lg:px-12">

&#x20; <div class="flex items-center justify-between max-w-6xl mx-auto gap-4 md:gap-6">

&#x20;   <a href="/" class="font-mono font-bold uppercase tracking-widest text-xl md:text-2xl">

&#x20;     {LOGO\_TEXT}

&#x20;   </a>

&#x20;   <div class="flex gap-4 md:gap-6 font-mono text-xs md:text-sm">

&#x20;     {NAV\_LINKS}

&#x20;   </div>

&#x20; </div>

</nav>

```



\### Hero 区块骨架

```html

<section class="bg-\[#fbbf24] text-\[#fbbf24] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-4xl mx-auto">

&#x20;   <h1 class="font-mono font-bold uppercase tracking-widest text-4xl md:text-6xl lg:text-8xl">

&#x20;     {HEADLINE}

&#x20;   </h1>

&#x20;   <p class="font-mono text-sm md:text-base max-w-xl">

&#x20;     {SUBHEADLINE}

&#x20;   </p>

&#x20;   <button class="rounded-none font-mono font-bold uppercase tracking-widest border-2 transition-all duration-200 ease-in-out bg-\[#fbbf24] text-\[#1a2744] border-2 border-\[#1a2744]">

&#x20;     {CTA\_TEXT}

&#x20;   </button>

&#x20; </div>

</section>

```



\### 卡片网格骨架

```html

<section class="bg-\[#1a2744] text-\[#fbbf24] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <h2 class="font-mono font-bold uppercase tracking-widest text-2xl md:text-4xl">{SECTION\_TITLE}</h2>

&#x20;   <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">

&#x20;     <!-- Card template - repeat for each card -->

&#x20;     <div class="rounded-none bg-\[#1a2744] border-2 border-\[#4a5c3a] shadow-\[4px\_4px\_0px\_rgba(251,191,36,0.3)] p-5 md:p-8">

&#x20;       <h3 class="font-mono font-bold uppercase tracking-widest text-xl md:text-2xl">{CARD\_TITLE}</h3>

&#x20;       <p class="font-mono text-sm md:text-base text-\[#4a5c3a]/70">{CARD\_DESCRIPTION}</p>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</section>

```



\### 表单输入骨架

```html

<input class="rounded-none border-2 border-\[#4a5c3a] bg-\[#1a2744]/80 text-\[#fbbf24] font-mono focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\### 页脚骨架

```html

<footer class="bg-\[#4a5c3a] text-\[#4a5c3a] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <div class="grid grid-cols-1 md:grid-cols-3 gap-6 md:gap-10">

&#x20;     <div>

&#x20;       <span class="font-mono font-bold uppercase tracking-widest text-xl md:text-2xl">{LOGO\_TEXT}</span>

&#x20;       <p class="font-mono text-xs md:text-sm">{TAGLINE}</p>

&#x20;     </div>

&#x20;     <div>

&#x20;       <h4 class="font-mono font-bold uppercase tracking-widest text-xl md:text-2xl">{COLUMN\_TITLE}</h4>

&#x20;       <ul class="font-mono text-xs md:text-sm">

&#x20;         {FOOTER\_LINKS}

&#x20;       </ul>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</footer>

```



\---



\## \[CHECKLIST] Mecha 生成后自检清单



\*\*输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：\*\*



\### Token 检查

\- \[ ] 按钮包含： `rounded-none font-mono font-bold uppercase tracking-widest border-2 transition-all duration-200 ease-in-out`

\- \[ ] 卡片包含： `rounded-none bg-\[#1a2744] border-2 border-\[#4a5c3a] shadow-\[4px\_4px\_0px\_rgba(251,191,36,0.3)]`

\- \[ ] 输入框包含： `rounded-none border-2 border-\[#4a5c3a] bg-\[#1a2744]/80 text-\[#fbbf24] font-mono focus:outline-none`



\### 禁止项检查

\- \[ ] 没有使用 `rounded-lg`

\- \[ ] 没有使用 `rounded-xl`

\- \[ ] 没有使用 `rounded-2xl`

\- \[ ] 没有使用 `rounded-full`

\- \[ ] 没有使用 `bg-white`

\- \[ ] 没有使用 `bg-\[#faf6f0]`

\- \[ ] 没有使用 `bg-\[#fffbf0]`

\- \[ ] 没有使用 `font-serif`



\### 风格规则检查

\- \[ ] 使用军绿、深蓝海军色为底色

\- \[ ] 添加警告黄和危险红的强调色

\- \[ ] 使用等宽字体和大写字母

\- \[ ] 设计直角无圆角的面板元素

\- \[ ] 添加技术标注和编号装饰



\### 风格漂移检查

\- \[ ] 没有违反：禁止使用柔和的圆角设计

\- \[ ] 没有违反：禁止使用柔和的粉色或浅色调

\- \[ ] 没有违反：禁止使用花哨的渐变或毛玻璃效果

\- \[ ] 没有违反：禁止使用手写体或花体字



\### 通用交付检查

\- \[ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出

\- \[ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案

\- \[ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态

\- \[ ] 结果仍然能够一眼识别为 Mecha



\---



\## \[EXAMPLES] 示例 Prompt



\### 1. 机甲控制台



机甲风格的控制面板界面



```

Use Mecha style to create a control panel interface:

1\. Background: dark navy with tech grid lines

2\. Title: mono font in warning yellow

3\. Cards: angular panels with green borders

4\. Warning stripes and status indicators

5\. Overall military-industrial mecha aesthetic

```



\### 2. SaaS 着陆页



生成 机甲风风格的 SaaS 产品着陆页



```

Create a SaaS landing page using Mecha style with hero section, feature grid, testimonials, pricing table, and footer.

```



\### 3. 作品集展示



生成 机甲风风格的作品集页面



```

Create a portfolio showcase page using Mecha style with project grid, about section, contact form, and consistent visual language.

```



\## 绝对禁止（匹配即拒绝）



以下模式一旦出现，视为风格违规——不找借口，直接重写。



\- 使用柔和的圆角设计

\- 使用柔和的粉色或浅色调

\- 使用花哨的渐变或毛玻璃效果

\- 使用手写体或花体字



\## 自检清单（交付前逐条确认）



如果任何一条不通过，说明风格漂移了——修改后再交付。



\- \[ ] 没有紫色到蓝色的渐变

\- \[ ] 没有使用 Inter / Roboto / Geist 等过度使用的字体

\- \[ ] 没有嵌套卡片（卡片里面套卡片）

\- \[ ] 没有在彩色背景上放灰色文字

\- \[ ] 正文对比度满足 WCAG AA（≥4.5:1）

\- \[ ] 没有 bounce / elastic 缓动曲线

\- \[ ] 动效有 prefers-reduced-motion 备选方案

\- \[ ] 正文行宽不超过 65-75 个字符

\- \[ ] 没有单侧粗边框装饰（border-left/right accent stripe）

\- \[ ] 没有渐变文字（background-clip: text）

\- \[ ] 没有把玻璃态（glassmorphism）当作默认风格

\- \[ ] 没有 tiny uppercase tracked eyebrow 放在每个 section 标题上面

\- \[ ] 禁止使用柔和的圆角设计

\- \[ ] 禁止使用柔和的粉色或浅色调

\- \[ ] 禁止使用花哨的渐变或毛玻璃效果

\- \[ ] 禁止使用手写体或花体字

