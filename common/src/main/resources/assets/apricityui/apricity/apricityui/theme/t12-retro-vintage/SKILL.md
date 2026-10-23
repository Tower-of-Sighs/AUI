STYLEKIT\_STYLE\_REFERENCE

style\_name: 复古怀旧风

style\_slug: retro-vintage

style\_source: /styles/retro-vintage



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



风格：Retro Vintage（复古怀旧风）

类型：怀旧经典的设计美学



必须使用：

\- 复古配色：棕褐色、琥珀色、棕色调

\- 背景使用纸张纹理或做旧效果

\- 衬线字体或复古无衬线字体

\- 粗边框：border-2 或 border-4

\- 装饰性元素（角部装饰、分隔线）

\- 旧式排版：大写字母、tracking-widest

\- 图片滤镜：sepia、brightness-90



必须避免：

\- 现代渐变

\- 霓虹色/高饱和度色彩

\- 极简/扁平化的现代设计

\- 过大的圆角

\- 玻璃拟态效果

\- 厚重的动画



配色方案：

\- 主色：鞍棕色（#8b4513）

\- 背景：奶油色/羊皮纸色（#f5e6d3）

\- 强调色：铁锈红（#c94c4c）、森林绿（#2e4a3f）



字体排版：

\- 标题：font-serif，可选大写

\- 标签：text-xs uppercase tracking-\[0.2em]

\- 正文：font-serif，宽松行距



\## 绝对禁止



\- 禁止使用现代渐变效果

\- 禁止使用霓虹/高饱和度颜色

\- 禁止使用极简/扁平的现代设计语言

\- 禁止使用过于圆润的圆角

\- 禁止使用玻璃态效果

\- 禁止使用动效过多的交互

\- 禁止使用任何 `scale` 或 `translate-y` 动画（Antique Stillness — 旧纸张不会浮起或弹跳）

\- 禁止使用 `duration-150` 或更快的过渡（Slow Passage — 氧化是地质过程，不是点击事件）

\- 禁止 hover 时引入新的饱和颜色（只能加深现有的复古色调，不能替换为现代色）



\## 必须遵守



\- 使用复古色调 sepia, amber, brown 系列

\- 背景添加纸张纹理或做旧效果

\- 使用 serif 字体或复古无衬线字体

\- 边框使用 border-2 或 border-4 的粗边框

\- 添加装饰性边框元素（角花、分隔线）

\- 使用老式排版风格（大写标题、字间距）

\- 图片添加做旧滤镜 sepia brightness-90

\- Antique Stillness: zero `scale` or `translate-y` — old paper lies flat, it does not float or bounce

\- Ink \& Oxidation: hover slightly darkens background (`hover:bg-\[#eedbc2]`) and deepens text (`group-hover:text-\[#5c2e0a]`) — simulating paper yellowing and ink settling over decades

\- Slow Passage of Time: all transitions use `duration-700 ease-in-out` — historical materials change slowly, never use `duration-150` or faster

\- Corner Reveal: decorative corner ornaments use `opacity-30 group-hover:opacity-100 transition-opacity duration-700` — historical details emerge slowly as readers lean in



\## 动效与交互规则



\- 古董般的静止：绝对不使用任何 `scale` 或 `translate-y` 动效。陈旧的羊皮纸平铺在桌面上——它不会漂浮、上升，也不会弹跳。禁止使用：`hover:-translate-y-\*`、`hover:scale-\*`。

\- 墨迹与氧化：hover 时背景微妙地变暗（`hover:bg-\[#eedbc2]`），文字颜色随之加深（`group-hover:text-\[#5c2e0a]`）。这模拟的是纸张随时间泛黄、墨迹随时间加深的过程，而不是现代式的色彩切换。

\- 缓慢的时间流逝：所有过渡效果必须使用 `duration-700 ease-in-out` 或更长时长。历史材质的变化是缓慢的，切勿使用 `duration-150` 或更快的时长。

\- 边角显现：装饰性边角元素使用 `opacity-30 group-hover:opacity-100 transition-opacity duration-700`——仿佛读者需要凑近细看，才能发现隐藏的细节。



\---



\# Retro Vintage (复古怀旧风) Design System



> 怀旧复古的设计风格，老式排版、复古色调、手工质感元素。适合咖啡馆、复古品牌、独立杂志、音乐厂牌。



\## 核心理念



Retro Vintage 风格从20世纪中期的设计美学中汲取灵感，通过复古排版、做旧纹理和怀旧色调创造温暖的时光感。



核心理念：

\- 时光沉淀：设计带有岁月的温度和故事

\- 手工温度：避免过于数字化的冷感

\- 经典永恒：使用经过时间检验的设计元素

\- 文化底蕴：传达历史感和文化认同



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

颜色: border-\[#8b4513]

圆角: rounded-none

```



\### 阴影

```

小: shadow-\[0\_2px\_4px\_rgba(139,69,19,0.1)]

中: shadow-\[0\_4px\_8px\_rgba(139,69,19,0.15)]

大: shadow-\[0\_8px\_16px\_rgba(139,69,19,0.2)]

悬停: hover:shadow-\[0\_4px\_12px\_rgba(139,69,19,0.2)]

聚焦: focus:shadow-\[0\_2px\_8px\_rgba(139,69,19,0.15)]

```



\### 交互效果

```

悬停位移: （无）

悬停缩放: （无）

悬停透明度: hover:opacity-85

过渡动画: transition-colors duration-200

按下状态: active:opacity-75

```



\### 字体

```

标题: font-serif uppercase tracking-widest

正文: font-serif

等宽: font-mono

```



\### 字号

```

Hero: text-4xl md:text-6xl lg:text-7xl

H1: text-3xl md:text-5xl

H2: text-2xl md:text-3xl

H3: text-lg md:text-xl

正文: text-sm md:text-base

小字: text-xs md:text-sm

```



\### 间距

```

Section: py-14 md:py-24 lg:py-32

容器: px-6 md:px-10 lg:px-16

卡片: p-6 md:p-8

小间距: gap-3 md:gap-5

中间距: gap-5 md:gap-8

大间距: gap-8 md:gap-12

```



\### 颜色角色

```

背景主色: bg-\[#f5e6d3]

背景辅色: bg-\[#e8d5c0]

背景强调色: bg-\[#c94c4c], bg-\[#2e4a3f], bg-\[#d4a373]

正文主色: text-\[#8b4513]

正文辅色: text-\[#f5e6d3]

正文弱化色: text-\[#8b4513]/60

按钮主色: bg-\[#8b4513] text-\[#f5e6d3]

按钮辅色: bg-transparent text-\[#8b4513] border-2 border-\[#8b4513]

```



\---



\## \[FORBIDDEN] 绝对禁止



以下 class 在本风格中\*\*绝对禁止使用\*\*，生成时必须检查并避免：



\### 禁止的 Class

\- `bg-gradient-to-r`

\- `bg-gradient-to-b`

\- `bg-gradient-to-l`

\- `rounded-xl`

\- `rounded-2xl`

\- `rounded-full`

\- `bg-\[#ff00ff]`

\- `bg-\[#00ffff]`

\- `bg-\[#ff71ce]`

\- `text-\[#ff00ff]`

\- `text-\[#00ffff]`

\- `shadow-\[0\_0\_`

\- `backdrop-blur`

\- `font-sans`

\- `bg-white`



\### 禁止的模式

\- 匹配 `^bg-gradient-`

\- 匹配 `^rounded-(?:xl|2xl|3xl|full)$`

\- 匹配 `^bg-(?:pink|cyan|violet|fuchsia)-`

\- 匹配 `^text-(?:pink|cyan|violet|fuchsia)-`

\- 匹配 `^shadow-\\\[0\_0\_`

\- 匹配 `^backdrop-blur`



\### 禁止原因

\- `bg-gradient-to-r`: Retro Vintage uses solid vintage tones, not modern gradients

\- `rounded-xl`: Retro Vintage uses minimal rounding to maintain classic feel

\- `bg-\[#ff00ff]`: Retro Vintage uses warm brown/cream palette, not neon colors

\- `shadow-\[0\_0\_`: Retro Vintage avoids neon glow shadows

\- `font-sans`: Retro Vintage uses serif typography (font-serif)

\- `bg-white`: Retro Vintage uses cream/parchment (#f5e6d3), not pure white



> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。



\---



\## \[REQUIRED] 必须包含



\### 按钮必须包含

```

font-serif uppercase tracking-widest

transition-colors duration-200

```



\### 卡片必须包含

```

border-2 border-\[#8b4513]

bg-\[#f5e6d3]

font-serif

```



\### 输入框必须包含

```

border-2 border-\[#8b4513]

bg-transparent

text-\[#8b4513]

font-serif

focus:outline-none

focus:bg-\[#8b4513]/5

transition-colors duration-200

```



\---



\## \[COMPARE] Retro Vintage 错误 vs 正确对比



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

<button class="font-serif uppercase tracking-widest transition-colors duration-200 bg-\[#8b4513] text-\[#f5e6d3]">

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

<div class="border-2 border-\[#8b4513] bg-\[#f5e6d3] font-serif p-6 md:p-8">

&#x20; <h3 class="font-serif uppercase tracking-widest text-lg md:text-xl">{TITLE}</h3>

</div>

```



\### 输入框



\[WRONG] \*\*错误示例\*\*（未经当前风格适配的通用输入框）：

```html

<input class="{GENERIC\_LIBRARY\_INPUT\_DEFAULT}" />

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 input token）：

```html

<input class="border-2 border-\[#8b4513] bg-transparent text-\[#8b4513] font-serif focus:outline-none focus:bg-\[#8b4513]/5 transition-colors duration-200" placeholder="{PLACEHOLDER}" />

```



\---



\## \[TEMPLATES] Retro Vintage 页面骨架模板



以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：



\### 导航栏骨架

```html

<nav class="bg-\[#f5e6d3] text-\[#8b4513] border-2 border-\[#8b4513] px-6 md:px-10 lg:px-16">

&#x20; <div class="flex items-center justify-between max-w-6xl mx-auto gap-5 md:gap-8">

&#x20;   <a href="/" class="font-serif uppercase tracking-widest text-lg md:text-xl">

&#x20;     {LOGO\_TEXT}

&#x20;   </a>

&#x20;   <div class="flex gap-5 md:gap-8 font-serif text-xs md:text-sm">

&#x20;     {NAV\_LINKS}

&#x20;   </div>

&#x20; </div>

</nav>

```



\### Hero 区块骨架

```html

<section class="bg-\[#c94c4c] text-\[#8b4513] py-14 md:py-24 lg:py-32 px-6 md:px-10 lg:px-16">

&#x20; <div class="max-w-4xl mx-auto">

&#x20;   <h1 class="font-serif uppercase tracking-widest text-4xl md:text-6xl lg:text-7xl">

&#x20;     {HEADLINE}

&#x20;   </h1>

&#x20;   <p class="font-serif text-sm md:text-base max-w-xl">

&#x20;     {SUBHEADLINE}

&#x20;   </p>

&#x20;   <button class="font-serif uppercase tracking-widest transition-colors duration-200 bg-\[#8b4513] text-\[#f5e6d3]">

&#x20;     {CTA\_TEXT}

&#x20;   </button>

&#x20; </div>

</section>

```



\### 卡片网格骨架

```html

<section class="bg-\[#f5e6d3] text-\[#8b4513] py-14 md:py-24 lg:py-32 px-6 md:px-10 lg:px-16">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <h2 class="font-serif uppercase tracking-widest text-2xl md:text-3xl">{SECTION\_TITLE}</h2>

&#x20;   <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 md:gap-8">

&#x20;     <!-- Card template - repeat for each card -->

&#x20;     <div class="border-2 border-\[#8b4513] bg-\[#f5e6d3] font-serif p-6 md:p-8">

&#x20;       <h3 class="font-serif uppercase tracking-widest text-lg md:text-xl">{CARD\_TITLE}</h3>

&#x20;       <p class="font-serif text-sm md:text-base text-\[#8b4513]/60">{CARD\_DESCRIPTION}</p>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</section>

```



\### 表单输入骨架

```html

<input class="border-2 border-\[#8b4513] bg-transparent text-\[#8b4513] font-serif focus:outline-none focus:bg-\[#8b4513]/5 transition-colors duration-200" placeholder="{PLACEHOLDER}" />

```



\### 页脚骨架

```html

<footer class="bg-\[#e8d5c0] text-\[#f5e6d3] py-14 md:py-24 lg:py-32 px-6 md:px-10 lg:px-16">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <div class="grid grid-cols-1 md:grid-cols-3 gap-8 md:gap-12">

&#x20;     <div>

&#x20;       <span class="font-serif uppercase tracking-widest text-lg md:text-xl">{LOGO\_TEXT}</span>

&#x20;       <p class="font-serif text-xs md:text-sm">{TAGLINE}</p>

&#x20;     </div>

&#x20;     <div>

&#x20;       <h4 class="font-serif uppercase tracking-widest text-lg md:text-xl">{COLUMN\_TITLE}</h4>

&#x20;       <ul class="font-serif text-xs md:text-sm">

&#x20;         {FOOTER\_LINKS}

&#x20;       </ul>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</footer>

```



\---



\## \[CHECKLIST] Retro Vintage 生成后自检清单



\*\*输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：\*\*



\### Token 检查

\- \[ ] 按钮包含： `font-serif uppercase tracking-widest transition-colors duration-200`

\- \[ ] 卡片包含： `border-2 border-\[#8b4513] bg-\[#f5e6d3] font-serif`

\- \[ ] 输入框包含： `border-2 border-\[#8b4513] bg-transparent text-\[#8b4513] font-serif focus:outline-none focus:bg-\[#8b4513]/5 transition-colors duration-200`



\### 禁止项检查

\- \[ ] 没有使用 `bg-gradient-to-r`

\- \[ ] 没有使用 `bg-gradient-to-b`

\- \[ ] 没有使用 `bg-gradient-to-l`

\- \[ ] 没有使用 `rounded-xl`

\- \[ ] 没有使用 `rounded-2xl`

\- \[ ] 没有使用 `rounded-full`

\- \[ ] 没有使用 `bg-\[#ff00ff]`

\- \[ ] 没有使用 `bg-\[#00ffff]`



\### 风格规则检查

\- \[ ] 使用复古色调 sepia, amber, brown 系列

\- \[ ] 背景添加纸张纹理或做旧效果

\- \[ ] 使用 serif 字体或复古无衬线字体

\- \[ ] 边框使用 border-2 或 border-4 的粗边框

\- \[ ] 添加装饰性边框元素（角花、分隔线）



\### 风格漂移检查

\- \[ ] 没有违反：禁止使用现代渐变效果

\- \[ ] 没有违反：禁止使用霓虹/高饱和度颜色

\- \[ ] 没有违反：禁止使用极简/扁平的现代设计语言

\- \[ ] 没有违反：禁止使用过于圆润的圆角

\- \[ ] 没有违反：禁止使用玻璃态效果



\### 通用交付检查

\- \[ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出

\- \[ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案

\- \[ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态

\- \[ ] 结果仍然能够一眼识别为 Retro Vintage



\---



\## \[EXAMPLES] 示例 Prompt



\### 1. 咖啡馆网站



生成复古咖啡馆网站



```

Create a coffee shop website using Retro Vintage style:

\- Hero with sepia-toned coffee imagery

\- Menu section with decorative borders

\- About us with vintage typography

\- Contact with old-style form styling

\- Paper texture backgrounds

\- Ornamental corner decorations

\- Serif fonts throughout

```



\### 2. SaaS 着陆页



生成 复古怀旧风风格的 SaaS 产品着陆页



```

Create a SaaS landing page using Retro Vintage style with hero section, feature grid, testimonials, pricing table, and footer.

```



\### 3. 作品集展示



生成 复古怀旧风风格的作品集页面



```

Create a portfolio showcase page using Retro Vintage style with project grid, about section, contact form, and consistent visual language.

```



\## 绝对禁止（匹配即拒绝）



以下模式一旦出现，视为风格违规——不找借口，直接重写。



\- 使用现代渐变效果

\- 使用霓虹/高饱和度颜色

\- 使用极简/扁平的现代设计语言

\- 使用过于圆润的圆角

\- 使用玻璃态效果

\- 使用动效过多的交互

\- 使用任何 `scale` 或 `translate-y` 动画（Antique Stillness — 旧纸张不会浮起或弹跳）

\- 使用 `duration-150` 或更快的过渡（Slow Passage — 氧化是地质过程，不是点击事件）



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

\- \[ ] 禁止使用现代渐变效果

\- \[ ] 禁止使用霓虹/高饱和度颜色

\- \[ ] 禁止使用极简/扁平的现代设计语言

\- \[ ] 禁止使用过于圆润的圆角

\- \[ ] 禁止使用玻璃态效果

