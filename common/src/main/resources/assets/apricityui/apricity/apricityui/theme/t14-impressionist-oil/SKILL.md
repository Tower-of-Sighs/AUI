STYLEKIT\_STYLE\_REFERENCE

style\_name: 油画印象派风

style\_slug: impressionist-oil

style\_source: /styles/impressionist-oil



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



你是一位专精于油画印象派风格（Impressionist Oil）的前端开发专家。生成的所有代码都必须严格遵循以下规范：



\## 绝对禁止



\- 禁止使用纯平色块（应有纹理感和渐变）

\- 禁止使用霓虹色或荧光色

\- 禁止使用等宽字体（font-mono）

\- 禁止使用大写文字（uppercase）

\- 禁止按钮缺少 active:translate-y-\[3px]（实色阴影不做陷入感 = 按钮失真）

\- 禁止 focus:ring 缺少 focus:ring-offset-\[#f5f0e1]（画布色背景下焦点环需与元素分离）

\- 禁止动画 duration 低于 300ms（印象派节奏是缓慢流动的）



\## 必须遵守



\- 使用温暖的画布色 bg-\[#f5f0e1] 作为背景

\- 按钮使用 linear-gradient 模拟颜料管挤出的渐变质感

\- 使用 layered box-shadow（实色底部 + 模糊扩散）模拟厚涂阴影

\- 卡片使用 repeating-linear-gradient 作为背景纹理模拟笔触方向

\- 添加 radial-gradient 光斑叠加模拟斑驳光影

\- 采用粗体衬线字体 font-serif font-bold 表达艺术感

\- 边角使用 rounded-lg 保持柔和的画布边缘

\- 按钮 hover:brightness-110 hover:contrast-125（颜料在阳光下闪耀）

\- 按钮 active:translate-y-\[3px]（与 4px 实色阴影配合产生按压陷入感）

\- 卡片使用 group 类，笔触底划线从 w-16 扩展至 group-hover:w-24（duration-500）

\- focus:ring-2 focus:ring-\[#e8a87c] focus:ring-offset-2 focus:ring-offset-\[#f5f0e1]



\## 配色方案



主色：

\- 暖橙：#e8a87c

\- 画布米白：#f5f0e1

\- 朱红：#c0392b

\- 深蓝：#2c3e50

\- 青绿：#1abc9c

\- 金光：#f5d88a



\## 独特元素（印象派专属）



1\. 笔触纹理：repeating-linear-gradient 使用 25-40deg 角度，配 0.02 透明度的色标

2\. 斑驳光影：多个 radial-gradient(circle Npx at X% Y%, rgba(...,0.05-0.08)) 散布在画面各处

3\. 厚涂阴影：box-shadow 叠层，由实色底层 + 模糊扩散层组成



\## 动效与交互规则



\### 光影舞动（按钮 Hover）

\- hover:brightness-110 hover:contrast-125 —— 模拟阳光照亮颜料的效果

\- 搭配 hover:-translate-y-0.5 产生轻微上浮

\- 禁止使用纯色 hover（会破坏印象派光影游戏的理念）



\### 厚涂陷落（Active 按压）

\- active:translate-y-\[3px] —— 按钮陷入 4px 实色朱红阴影层

\- 禁止单独使用 active:scale-\*（缩放无法模拟厚涂颜料的物理深度）

\- translate 位移必须与实色阴影的偏移量相符或接近（目前为 4px）



\### 笔触显现（卡片下划线）

\- 卡片标题下划线：w-16 h-\[3px] bg-\[#e8a87c] rounded-full

\- group-hover 时：group-hover:w-24 —— 笔触像颜料扩散一样延展

\- 过渡：duration-500 ease-out（缓慢、富有画意的节奏）

\- 卡片容器上必须使用 group 类



\### 缓速缓动标准

\- 最短时长：300ms（印象派的节奏是缓慢流动的）

\- 按钮过渡：duration-300 ease-out

\- 下划线展开：duration-500 ease-out

\- 禁止使用小于 300ms 的时长



\### 焦点环

\- focus:ring-2 focus:ring-\[#e8a87c] focus:ring-offset-2 focus:ring-offset-\[#f5f0e1]

\- 必须包含 ring-offset-\[#f5f0e1] —— 画布米白背景需要匹配的焦点环偏移



\---



\# Impressionist Oil (油画印象派风) Design System



> 受莫奈、雷诺阿等印象派大师启发，大胆的笔触纹理、斑驳光影、点彩色彩和温暖的画布质感，呈现如油画般的浓郁视觉体验。



\## 核心理念



油画印象派风格汲取19世纪法国印象派绘画的精髓，强调光影变化和色彩的即兴表达。



核心理念：

\- 斑驳光影：使用 multiple radial-gradient 叠加模拟阳光穿过树叶的斑驳效果

\- 笔触纹理：通过 repeating-linear-gradient 以不同角度叠加创造画布上可见的笔触

\- 厚涂堆叠：layered box-shadow（0\_3px\_0 色块 + blur shadow）模拟颜料的厚实体积感

\- 画布质感：温暖的米白底色 #f5f0e1 配以 feTurbulence 织纹滤镜

\- 色彩调和：暖橙 #e8a87c、朱红 #c0392b、深蓝 #2c3e50、青绿 #1abc9c、金光 #f5d88a



\---



\## Token 字典（精确 Class 映射）



\### 边框

```

宽度: border

颜色: border-\[#e8a87c]/25

圆角: rounded-lg

```



\### 阴影

```

小: shadow-\[0\_2px\_0\_rgba(192,57,43,0.10),0\_3px\_8px\_rgba(232,168,124,0.15)]

中: shadow-\[0\_3px\_0\_rgba(192,57,43,0.12),0\_5px\_16px\_rgba(232,168,124,0.20)]

大: shadow-\[0\_4px\_0\_rgba(192,57,43,0.15),0\_8px\_24px\_rgba(44,62,80,0.12)]

悬停: hover:shadow-\[0\_4px\_0\_rgba(192,57,43,0.15),0\_10px\_28px\_rgba(44,62,80,0.12)]

聚焦: focus:shadow-\[0\_0\_0\_3px\_rgba(232,168,124,0.12)]

```



\### 交互效果

```

悬停位移: （无）

悬停缩放: hover:brightness-110

悬停透明度: （无）

过渡动画: transition-all duration-300 ease-in-out

按下状态: active:translate-y-\[2px]

```



\### 字体

```

标题: font-serif font-bold tracking-wide

正文: font-serif

等宽: font-mono

```



\### 字号

```

Hero: text-5xl md:text-7xl lg:text-8xl

H1: text-3xl md:text-5xl

H2: text-2xl md:text-3xl

H3: text-xl md:text-2xl

正文: text-sm md:text-base

小字: text-xs md:text-sm

```



\### 间距

```

Section: py-14 md:py-20 lg:py-28

容器: px-6 md:px-10 lg:px-14

卡片: p-6 md:p-8

小间距: gap-4 md:gap-5

中间距: gap-5 md:gap-6

大间距: gap-6 md:gap-10

```



\### 颜色角色

```

背景主色: bg-\[#f5f0e1]

背景辅色: bg-\[#ede5d0]

背景强调色: bg-\[#e8a87c]/15, bg-\[#c0392b]/10, bg-\[#1abc9c]/10, bg-\[#f5d88a]/10

正文主色: text-\[#2c3e50]

正文辅色: text-\[#e8a87c]

正文弱化色: text-\[#2c3e50]/45

按钮主色: text-\[#2c3e50] shadow-\[0\_4px\_0\_#c0392b,0\_6px\_16px\_rgba(232,168,124,0.30)]

按钮辅色: text-\[#f5f0e1] shadow-\[0\_4px\_0\_#1abc9c,0\_6px\_16px\_rgba(44,62,80,0.30)]

```



\---



\## \[FORBIDDEN] 绝对禁止



以下 class 在本风格中\*\*绝对禁止使用\*\*，生成时必须检查并避免：



\### 禁止的 Class

\- `rounded-none`

\- `rounded-sm`

\- `shadow-\[2px\_2px\_0px`

\- `shadow-\[3px\_3px\_0px`

\- `shadow-\[4px\_4px\_0px`

\- `font-mono`

\- `bg-black`

\- `bg-\[#0a0a1a]`

\- `uppercase`

\- `rounded-full`



\### 禁止的模式

\- 匹配 `^shadow-\\\[\\d+px\_\\d+px\_0px`

\- 匹配 `^font-mono$`

\- 匹配 `^bg-black$`

\- 匹配 `^uppercase$`

\- 匹配 `^rounded-full$`



\### 禁止原因

\- `font-mono`: Impressionist Oil uses serif fonts for artistic painterly feel

\- `bg-black`: Impressionist Oil uses warm canvas backgrounds (#f5f0e1), never dark

\- `rounded-none`: Impressionist Oil uses rounded-lg for soft painterly edges

\- `uppercase`: Impressionist Oil uses elegant mixed-case serif, never aggressive uppercase

\- `rounded-full`: Impressionist Oil uses rounded-lg, not fully circular shapes



> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。



\---



\## \[REQUIRED] 必须包含



\### 按钮必须包含

```

rounded-lg

font-serif font-bold

transition-all duration-300 ease-in-out

```



\### 卡片必须包含

```

rounded-lg

bg-\[#f5f0e1]

border border-\[#e8a87c]/25

```



\### 输入框必须包含

```

rounded-lg

border-2 border-\[#e8a87c]/25

bg-\[#f5f0e1]

font-serif

focus:outline-none

```



\---



\## \[COMPARE] Impressionist Oil 错误 vs 正确对比



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

<button class="rounded-lg font-serif font-bold transition-all duration-300 ease-in-out text-\[#2c3e50] shadow-\[0\_4px\_0\_#c0392b,0\_6px\_16px\_rgba(232,168,124,0.30)]">

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

<div class="rounded-lg bg-\[#f5f0e1] border border-\[#e8a87c]/25 p-6 md:p-8">

&#x20; <h3 class="font-serif font-bold tracking-wide text-xl md:text-2xl">{TITLE}</h3>

</div>

```



\### 输入框



\[WRONG] \*\*错误示例\*\*（未经当前风格适配的通用输入框）：

```html

<input class="{GENERIC\_LIBRARY\_INPUT\_DEFAULT}" />

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 input token）：

```html

<input class="rounded-lg border-2 border-\[#e8a87c]/25 bg-\[#f5f0e1] font-serif focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\---



\## \[TEMPLATES] Impressionist Oil 页面骨架模板



以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：



\### 导航栏骨架

```html

<nav class="bg-\[#f5f0e1] text-\[#2c3e50] border border-\[#e8a87c]/25 px-6 md:px-10 lg:px-14">

&#x20; <div class="flex items-center justify-between max-w-6xl mx-auto gap-5 md:gap-6">

&#x20;   <a href="/" class="font-serif font-bold tracking-wide text-xl md:text-2xl">

&#x20;     {LOGO\_TEXT}

&#x20;   </a>

&#x20;   <div class="flex gap-5 md:gap-6 font-serif text-xs md:text-sm">

&#x20;     {NAV\_LINKS}

&#x20;   </div>

&#x20; </div>

</nav>

```



\### Hero 区块骨架

```html

<section class="bg-\[#e8a87c]/15 text-\[#2c3e50] py-14 md:py-20 lg:py-28 px-6 md:px-10 lg:px-14">

&#x20; <div class="max-w-4xl mx-auto">

&#x20;   <h1 class="font-serif font-bold tracking-wide text-5xl md:text-7xl lg:text-8xl">

&#x20;     {HEADLINE}

&#x20;   </h1>

&#x20;   <p class="font-serif text-sm md:text-base max-w-xl">

&#x20;     {SUBHEADLINE}

&#x20;   </p>

&#x20;   <button class="rounded-lg font-serif font-bold transition-all duration-300 ease-in-out text-\[#2c3e50] shadow-\[0\_4px\_0\_#c0392b,0\_6px\_16px\_rgba(232,168,124,0.30)]">

&#x20;     {CTA\_TEXT}

&#x20;   </button>

&#x20; </div>

</section>

```



\### 卡片网格骨架

```html

<section class="bg-\[#f5f0e1] text-\[#2c3e50] py-14 md:py-20 lg:py-28 px-6 md:px-10 lg:px-14">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <h2 class="font-serif font-bold tracking-wide text-2xl md:text-3xl">{SECTION\_TITLE}</h2>

&#x20;   <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 md:gap-6">

&#x20;     <!-- Card template - repeat for each card -->

&#x20;     <div class="rounded-lg bg-\[#f5f0e1] border border-\[#e8a87c]/25 p-6 md:p-8">

&#x20;       <h3 class="font-serif font-bold tracking-wide text-xl md:text-2xl">{CARD\_TITLE}</h3>

&#x20;       <p class="font-serif text-sm md:text-base text-\[#2c3e50]/45">{CARD\_DESCRIPTION}</p>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</section>

```



\### 表单输入骨架

```html

<input class="rounded-lg border-2 border-\[#e8a87c]/25 bg-\[#f5f0e1] font-serif focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\### 页脚骨架

```html

<footer class="bg-\[#ede5d0] text-\[#e8a87c] py-14 md:py-20 lg:py-28 px-6 md:px-10 lg:px-14">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <div class="grid grid-cols-1 md:grid-cols-3 gap-6 md:gap-10">

&#x20;     <div>

&#x20;       <span class="font-serif font-bold tracking-wide text-xl md:text-2xl">{LOGO\_TEXT}</span>

&#x20;       <p class="font-serif text-xs md:text-sm">{TAGLINE}</p>

&#x20;     </div>

&#x20;     <div>

&#x20;       <h4 class="font-serif font-bold tracking-wide text-xl md:text-2xl">{COLUMN\_TITLE}</h4>

&#x20;       <ul class="font-serif text-xs md:text-sm">

&#x20;         {FOOTER\_LINKS}

&#x20;       </ul>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</footer>

```



\---



\## \[CHECKLIST] Impressionist Oil 生成后自检清单



\*\*输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：\*\*



\### Token 检查

\- \[ ] 按钮包含： `rounded-lg font-serif font-bold transition-all duration-300 ease-in-out`

\- \[ ] 卡片包含： `rounded-lg bg-\[#f5f0e1] border border-\[#e8a87c]/25`

\- \[ ] 输入框包含： `rounded-lg border-2 border-\[#e8a87c]/25 bg-\[#f5f0e1] font-serif focus:outline-none`



\### 禁止项检查

\- \[ ] 没有使用 `rounded-none`

\- \[ ] 没有使用 `rounded-sm`

\- \[ ] 没有使用 `shadow-\[2px\_2px\_0px`

\- \[ ] 没有使用 `shadow-\[3px\_3px\_0px`

\- \[ ] 没有使用 `shadow-\[4px\_4px\_0px`

\- \[ ] 没有使用 `font-mono`

\- \[ ] 没有使用 `bg-black`

\- \[ ] 没有使用 `bg-\[#0a0a1a]`



\### 风格规则检查

\- \[ ] 使用温暖的画布色 bg-\[#f5f0e1] 作为背景

\- \[ ] 按钮使用 linear-gradient 模拟颜料管挤出的渐变质感

\- \[ ] 使用 layered box-shadow（实色底部 + 模糊扩散）模拟厚涂阴影

\- \[ ] 卡片使用 repeating-linear-gradient 作为背景纹理模拟笔触方向

\- \[ ] 添加 radial-gradient 光斑叠加模拟斑驳光影



\### 风格漂移检查

\- \[ ] 没有违反：禁止使用纯平色块（应有纹理感和渐变）

\- \[ ] 没有违反：禁止使用霓虹色或荧光色

\- \[ ] 没有违反：禁止使用等宽字体（font-mono）

\- \[ ] 没有违反：禁止使用大写文字（uppercase）

\- \[ ] 没有违反：禁止按钮缺少 active:translate-y-\[3px]（实色阴影不做陷入感 = 按钮失真）



\### 通用交付检查

\- \[ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出

\- \[ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案

\- \[ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态

\- \[ ] 结果仍然能够一眼识别为 Impressionist Oil



\---



\## \[EXAMPLES] 示例 Prompt



\### 1. 印象派画廊页面



油画风格的艺术画廊展示，带有斑驳光影和笔触纹理



```

Use Impressionist Oil style to create a gallery page:

1\. Background: canvas cream #f5f0e1 with repeating-linear-gradient crosshatch texture overlay

2\. Hero: painting frame border with inset box-shadow, multiple radial-gradient dappled light spots

3\. Cards: repeating-linear-gradient brushstroke texture at varied angles, layered box-shadows

4\. Buttons: linear-gradient fills with 0 4px 0 solid + blur shadow

5\. Warm palette: #e8a87c, #c0392b, #2c3e50, #1abc9c, #f5d88a

6\. Font: serif bold throughout

```



\### 2. SaaS 着陆页



生成 油画印象派风风格的 SaaS 产品着陆页



```

Create a SaaS landing page using Impressionist Oil style with hero section, feature grid, testimonials, pricing table, and footer.

```



\### 3. 作品集展示



生成 油画印象派风风格的作品集页面



```

Create a portfolio showcase page using Impressionist Oil style with project grid, about section, contact form, and consistent visual language.

```



\## 绝对禁止（匹配即拒绝）



以下模式一旦出现，视为风格违规——不找借口，直接重写。



\- 使用纯平色块（应有纹理感和渐变）

\- 使用霓虹色或荧光色

\- 使用等宽字体（font-mono）

\- 使用大写文字（uppercase）

\- 按钮缺少 active:translate-y-\[3px]（实色阴影不做陷入感 = 按钮失真）

\- focus:ring 缺少 focus:ring-offset-\[#f5f0e1]（画布色背景下焦点环需与元素分离）

\- 动画 duration 低于 300ms（印象派节奏是缓慢流动的）



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

\- \[ ] 禁止使用纯平色块（应有纹理感和渐变）

\- \[ ] 禁止使用霓虹色或荧光色

\- \[ ] 禁止使用等宽字体（font-mono）

\- \[ ] 禁止使用大写文字（uppercase）

\- \[ ] 禁止按钮缺少 active:translate-y-\[3px]（实色阴影不做陷入感 = 按钮失真）

