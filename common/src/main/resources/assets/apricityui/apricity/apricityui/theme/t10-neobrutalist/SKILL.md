STYLEKIT\_STYLE\_REFERENCE

style\_name: 新野兽派

style\_slug: neo-brutalist

style\_source: /styles/neo-brutalist



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



\# Neo-Brutalist（新野兽派）设计系统



你是一个专精 Neo-Brutalist（新野兽派）网页设计的前端开发专家，生成的所有代码都必须严格遵循以下规范。



\## 绝对禁止



\- 禁止使用圆角 rounded-lg, rounded-md, rounded-xl

\- 禁止使用渐变 bg-gradient-\*

\- 禁止使用灰色边框 border-gray-\*, border-slate-\*

\- 禁止使用淡入淡出的半透明效果

\- 禁止使用 rounded-full（装饰圆除外）

\- 禁止按钮 active 状态位移量小于原始阴影像素值（未完全压平，失去碾压感）

\- 禁止 hover 背景色切换使用渐变或 opacity 过渡（必须是硬切，duration-150 ease-out）

\- 禁止按钮 hover 时仅用位移替代阴影消失（hover 应增大阴影强调力量，active 才是完全压平）



\## 必须遵守



\- 使用纯黑边框 border-black border-2 md:border-4

\- 使用硬边缘阴影 shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

\- 保持直角 rounded-none

\- 使用高对比度配色（黑白为主 + 鲜艳强调色）

\- 标题使用 font-black，正文使用 font-mono

\- 所有样式包含移动端和桌面端响应式值

\- 按钮 active:translate-x-\[6px] active:translate-y-\[6px] active:shadow-none，位移量必须等于原始阴影像素值（Physical Crushing，实体完全压平）

\- Hover 时瞬间切换高对比背景色（如 hover:bg-\[#ffff00]）并增大阴影（Brutal Snap，禁止使用渐变或 opacity 过渡）

\- 卡片 hover 使用 ease-out duration-150，保持生猛的碰撞感

\- 按钮 hover 时增大阴影并向左上角偏移：hover:-translate-y-1 hover:-translate-x-1 hover:shadow-\[10px\_10px\_0px\_0px\_rgba(0,0,0,1)]



\## 风格身份

\- \*\*名称\*\*：Neo-Brutalist / Web Brutalism

\- \*\*类别\*\*：表现力（Expressive）、高对比度（High-Contrast）

\- \*\*本质\*\*：原始、诚实、不加掩饰——功能优先于形式，拒绝精致化

\- \*\*情绪基调\*\*：大胆、对抗性强、顽劣中带侵略性、反企业气质

\- \*\*灵感来源\*\*：建筑野兽派、朋克 zine 刊物、早期网页、瑞士海报设计



\---



\## 核心视觉原则



\### 1. 边框系统（关键）

```

REQUIRED: Pure black borders

border-black border-2 md:border-4



NEVER use: border-gray-\*, border-slate-\*, border-neutral-\*

```



\### 2. 阴影系统（仅限硬边缘）

```

REQUIRED FORMAT:

shadow-\[Xpx\_Xpx\_0px\_0px\_rgba(0,0,0,1)]



Examples:

Mobile: shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)]

Desktop: shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]



FORBIDDEN: shadow-sm, shadow-md, shadow-lg, shadow-xl, shadow-2xl

(No blur allowed — hard edges only)

```



\### 3. 圆角

```

REQUIRED: rounded-none

Sharp corners everywhere



FORBIDDEN: rounded-lg, rounded-md, rounded-xl, rounded-2xl

(Exception: rounded-full for intentional decorative circles only)

```



\### 4. 字体排印

```

HEADINGS: font-black (900 weight)

BODY: font-mono

LABELS: font-mono uppercase tracking-wider

```



\---



\## 交互规范



\### 按钮状态（Physical Crushing）

| 状态 | 效果 | 实现方式 |

|------|------|----------|

| Default | 带阴影浮起 | shadow-\[6px\_6px\_0px\_0px\_rgba(0,0,0,1)] |

| Hover | 阴影增大 + 偏移 | hover:shadow-\[10px\_10px\_0px\_0px...] hover:-translate-y-1 hover:-translate-x-1 |

| Active | 完全压平 | active:translate-x-\[6px] active:translate-y-\[6px] active:shadow-none |



\*\*关键\*\*：active 位移量必须等于原始阴影像素值，这样才能实现「physical crushing（实体压平）」效果——按钮被完整地压入表面。



\### 卡片 Hover（Brutal Snap）

```jsx

<div className="group bg-white border-4 border-black

&#x20; shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

&#x20; hover:shadow-\[12px\_12px\_0px\_0px\_rgba(255,0,110,1)]

&#x20; hover:-translate-y-1 hover:-translate-x-1

&#x20; hover:bg-\[#ffff00]

&#x20; transition-all duration-150 ease-out">

&#x20; ...

</div>

```



\*\*关键\*\*：hover 背景色切换必须是瞬间硬切，禁止渐变、禁止 opacity 淡出；duration-150 ease-out 仅用于位置和阴影的过渡。



\---



\## 动效规则



\### 交互物理

\- \*\*Physical Crushing\*\*：按钮 active 位移量必须等于阴影偏移量。以 6px 阴影为例：`active:translate-x-\[6px] active:translate-y-\[6px] active:shadow-none`。

\- \*\*Brutal Snap\*\*：hover 时背景瞬间切换为高对比色，硬切、无渐隐：`hover:bg-\[#ffff00]`

\- \*\*Zero Rounding Easing\*\*：所有过渡统一使用 `ease-out duration-150`，保持生猛的碰撞质感，拒绝柔和的弹簧物理效果。

\- \*\*Heavy Focus\*\*：卡片 hover 时阴影增大，同时阴影颜色切换为强调色（粉色/品红）。



\### 时长指南

| 交互 | 时长 | 缓动 |

|------|------|------|

| Hover 位移变换 | 150ms | ease-out |

| Active 按压 | 瞬间 | — |

| 阴影变化 | 150ms | ease-out |

| 颜色瞬切 | 0ms | 瞬间 |



\---



\## 配色方案



\### 主色

| Token | 值 | 用途 |

|-------|-----|------|

| 黑 | #000000 | 边框、文字、阴影 |

| 白 | #ffffff | 背景 |



\### 强调色

| Token | 色值 | 用途 |

|-------|------|------|

| 粉色 | #ff006e | CTA、hover 效果 |

| 绿色 | #ccff00 | 成功状态、Hero 背景 |

| 蓝色 | #00d9ff | 链接、信息提示 |

| 黄色 | #ff9500 | 标签、警示 |

| 亮黄 | #ffff00 | hover 背景 |



\### 阴影颜色

```

Default: rgba(0,0,0,1)

Hover accent: rgba(255,0,110,1)

```



\---



\## 字体排印



| 元素 | 类名 |

|------|------|

| H1 | font-black text-4xl md:text-6xl lg:text-8xl tracking-tight leading-tight |

| H2 | font-black text-2xl md:text-4xl |

| H3 | font-black text-xl md:text-2xl |

| 正文 | font-mono text-sm md:text-base leading-relaxed |

| 标签 | font-mono text-xs uppercase tracking-wider |

| CTA | font-black uppercase text-lg |



\---



\## 响应式指南



\### 缩放比例

移动端数值约为桌面端数值的 50%。



\### 边框

```

border-2 md:border-4

```



\### 阴影

```

shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] 

md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

```



\### 间距

```

p-4 md:p-8

py-12 md:py-24

gap-4 md:gap-8

```



\### 字体排印

```

text-sm md:text-base

text-xl md:text-3xl

text-4xl md:text-6xl lg:text-8xl

```



\---



\## 组件模板



\### 按钮

```jsx

<button className="

&#x20; bg-\[#ff006e] text-white font-black uppercase text-lg

&#x20; px-8 py-4 border-4 border-black

&#x20; shadow-\[6px\_6px\_0px\_0px\_rgba(0,0,0,1)]

&#x20; hover:shadow-\[10px\_10px\_0px\_0px\_rgba(0,0,0,1)]

&#x20; hover:-translate-y-1 hover:-translate-x-1

&#x20; active:translate-x-\[6px] active:translate-y-\[6px]

&#x20; active:shadow-none

&#x20; transition-all duration-150 ease-out">

&#x20; Click Hard

</button>

```



\### 卡片

```jsx

<div className="group bg-white border-4 border-black

&#x20; shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

&#x20; hover:shadow-\[12px\_12px\_0px\_0px\_rgba(255,0,110,1)]

&#x20; hover:-translate-y-1 hover:-translate-x-1

&#x20; hover:bg-\[#ffff00]

&#x20; transition-all duration-150 ease-out

&#x20; p-8 cursor-pointer">

&#x20; <h3 className="font-black text-xl mb-2 

&#x20;   group-hover:tracking-wider transition-all duration-150">

&#x20;   Neo-Brutalism

&#x20; </h3>

&#x20; <p className="font-mono text-base text-gray-700">

&#x20;   Raw, bold, unapologetic design.

&#x20; </p>

</div>

```



\---



\## 禁止模式



| 模式 | 原因 |

|------|------|

| rounded-lg, rounded-md, rounded-xl | 软化了野兽派的锐利边缘 |

| shadow-lg, shadow-xl, shadow-2xl | 模糊违反了硬边缘原则 |

| bg-gradient-\* | 渐变过于精致光滑 |

| border-gray-\*, border-slate-\* | 必须使用纯黑边框 |

| 淡入淡出/opacity 过渡 | 必须使用硬切换 |

| rounded-full（一般用途） | 仅限装饰性圆形 |

| Active 位移量 < 阴影像素值 | 无法实现完全压平的碾压感 |

| Hover opacity 淡出 | 必须瞬间切换到新颜色 |



\---



\## 自检清单



输出代码前，请核对以下各项：

\- \[ ] 没有圆角（有意为之的装饰性圆形除外）

\- \[ ] 阴影是硬边缘格式：shadow-\[Xpx\_Xpx\_0px\_0px\_rgba...]

\- \[ ] 边框是纯黑色：border-black

\- \[ ] 按钮 active 位移量 = 原始阴影像素值

\- \[ ] hover 背景变化是瞬间完成的（硬切，无渐隐）

\- \[ ] 过渡统一使用 duration-150 ease-out

\- \[ ] 标题使用 font-black，正文使用 font-mono

\- \[ ] 边框、阴影、间距均带有响应式 md: 前缀

\- \[ ] 移动端数值 ≈ 桌面端数值的 50%



\---



\# Neo-Brutalist (新野兽派) Design System



> 大胆的黑色粗边框、硬边缘阴影、无圆角、高对比度配色。源于建筑野兽派，强调功能与原始美学。



\## 核心理念



Neo-Brutalist（新野兽派）设计风格源于建筑领域的野兽派运动，强调原始、未经修饰的功能美学。在 Web 设计中，这种风格通过大胆的黑色边框、硬边缘阴影、锐利的直角和高对比度的配色方案来表达。



核心理念：

\- 功能优先：每个元素都有明确的目的

\- 诚实表达：不掩饰结构，不伪装功能

\- 大胆直接：用视觉冲击力传达信息

\- 反对圆滑：拒绝过度精致，拥抱粗犷



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

宽度: border-2 md:border-4

颜色: border-black

圆角: rounded-none

```



\### 阴影

```

小: shadow-\[2px\_2px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)]

中: shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

大: shadow-\[6px\_6px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[12px\_12px\_0px\_0px\_rgba(0,0,0,1)]

悬停: hover:shadow-none

聚焦: focus:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:focus:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

```



\### 交互效果

```

悬停位移: hover:translate-x-\[2px] hover:translate-y-\[2px] md:hover:translate-x-1 md:hover:translate-y-1

悬停缩放: （无）

悬停透明度: （无）

过渡动画: transition-all duration-200

按下状态: active:translate-x-\[4px] active:translate-y-\[4px]

```



\### 字体

```

标题: font-black tracking-tight

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

Section: py-12 md:py-24 lg:py-32

容器: px-4 md:px-8 lg:px-12

卡片: p-4 md:p-6

小间距: gap-2 md:gap-4

中间距: gap-4 md:gap-6

大间距: gap-6 md:gap-8

```



\### 颜色角色

```

背景主色: bg-white

背景辅色: bg-black

背景强调色: bg-\[#ff006e], bg-\[#ccff00], bg-\[#00d9ff], bg-\[#ff9500]

正文主色: text-black

正文辅色: text-white

正文弱化色: text-gray-700

按钮主色: bg-\[#ff006e] text-white

按钮辅色: bg-black text-white

```



\---



\## \[FORBIDDEN] 绝对禁止



以下 class 在本风格中\*\*绝对禁止使用\*\*，生成时必须检查并避免：



\### 禁止的 Class

\- `rounded-sm`

\- `rounded`

\- `rounded-md`

\- `rounded-lg`

\- `rounded-xl`

\- `rounded-2xl`

\- `rounded-3xl`

\- `shadow-sm`

\- `shadow`

\- `shadow-md`

\- `shadow-lg`

\- `shadow-xl`

\- `shadow-2xl`

\- `border-gray-100`

\- `border-gray-200`

\- `border-gray-300`

\- `border-gray-400`

\- `border-gray-500`

\- `border-slate-100`

\- `border-slate-200`



\### 禁止的模式

\- 匹配 `^rounded-(?!none)`

\- 匹配 `^shadow-(?!\\\[|none)`

\- 匹配 `^bg-gradient-`

\- 匹配 `^border-gray-`

\- 匹配 `^border-slate-`



\### 禁止原因

\- `rounded-lg`: Neo-Brutalist uses sharp corners only (rounded-none)

\- `shadow-lg`: Neo-Brutalist uses hard-edge shadows only (shadow-\[Xpx\_Xpx\_0px\_0px\_...])

\- `bg-gradient-to-r`: Neo-Brutalist uses solid colors, no gradients

\- `border-gray-300`: Neo-Brutalist uses pure black borders (border-black)



> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。



\---



\## \[REQUIRED] 必须包含



\### 按钮必须包含

```

rounded-none

border-2 md:border-4

border-black

shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

hover:shadow-none

hover:translate-x-\[2px] hover:translate-y-\[2px]

transition-all duration-200

font-black

```



\### 卡片必须包含

```

rounded-none

border-2 md:border-4

border-black

shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

bg-white

```



\### 输入框必须包含

```

rounded-none

border-2 md:border-4

border-black

font-mono

focus:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)]

focus:outline-none

```



\---



\## \[COMPARE] Neo-Brutalist 错误 vs 正确对比



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

<button class="rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] hover:shadow-none hover:translate-x-\[2px] hover:translate-y-\[2px] transition-all duration-200 font-black bg-\[#ff006e] text-white">

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

<div class="rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] bg-white p-4 md:p-6">

&#x20; <h3 class="font-black tracking-tight text-xl md:text-2xl">{TITLE}</h3>

</div>

```



\### 输入框



\[WRONG] \*\*错误示例\*\*（未经当前风格适配的通用输入框）：

```html

<input class="{GENERIC\_LIBRARY\_INPUT\_DEFAULT}" />

```



\[CORRECT] \*\*正确示例\*\*（使用当前风格的 input token）：

```html

<input class="rounded-none border-2 md:border-4 border-black font-mono focus:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\---



\## \[TEMPLATES] Neo-Brutalist 页面骨架模板



以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：



\### 导航栏骨架

```html

<nav class="bg-white text-black border-2 md:border-4 border-black px-4 md:px-8 lg:px-12">

&#x20; <div class="flex items-center justify-between max-w-6xl mx-auto gap-4 md:gap-6">

&#x20;   <a href="/" class="font-black tracking-tight text-xl md:text-2xl">

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

<section class="bg-\[#ff006e] text-black py-12 md:py-24 lg:py-32 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-4xl mx-auto">

&#x20;   <h1 class="font-black tracking-tight text-4xl md:text-6xl lg:text-8xl">

&#x20;     {HEADLINE}

&#x20;   </h1>

&#x20;   <p class="font-mono text-sm md:text-base max-w-xl">

&#x20;     {SUBHEADLINE}

&#x20;   </p>

&#x20;   <button class="rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] hover:shadow-none hover:translate-x-\[2px] hover:translate-y-\[2px] transition-all duration-200 font-black bg-\[#ff006e] text-white">

&#x20;     {CTA\_TEXT}

&#x20;   </button>

&#x20; </div>

</section>

```



\### 卡片网格骨架

```html

<section class="bg-white text-black py-12 md:py-24 lg:py-32 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <h2 class="font-black tracking-tight text-2xl md:text-4xl">{SECTION\_TITLE}</h2>

&#x20;   <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">

&#x20;     <!-- Card template - repeat for each card -->

&#x20;     <div class="rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] bg-white p-4 md:p-6">

&#x20;       <h3 class="font-black tracking-tight text-xl md:text-2xl">{CARD\_TITLE}</h3>

&#x20;       <p class="font-mono text-sm md:text-base text-gray-700">{CARD\_DESCRIPTION}</p>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</section>

```



\### 表单输入骨架

```html

<input class="rounded-none border-2 md:border-4 border-black font-mono focus:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] focus:outline-none" placeholder="{PLACEHOLDER}" />

```



\### 页脚骨架

```html

<footer class="bg-black text-white py-12 md:py-24 lg:py-32 px-4 md:px-8 lg:px-12">

&#x20; <div class="max-w-6xl mx-auto">

&#x20;   <div class="grid grid-cols-1 md:grid-cols-3 gap-6 md:gap-8">

&#x20;     <div>

&#x20;       <span class="font-black tracking-tight text-xl md:text-2xl">{LOGO\_TEXT}</span>

&#x20;       <p class="font-mono text-xs md:text-sm">{TAGLINE}</p>

&#x20;     </div>

&#x20;     <div>

&#x20;       <h4 class="font-black tracking-tight text-xl md:text-2xl">{COLUMN\_TITLE}</h4>

&#x20;       <ul class="font-mono text-xs md:text-sm">

&#x20;         {FOOTER\_LINKS}

&#x20;       </ul>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</footer>

```



\---



\## \[CHECKLIST] Neo-Brutalist 生成后自检清单



\*\*输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：\*\*



\### Token 检查

\- \[ ] 按钮包含： `rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] hover:shadow-none hover:translate-x-\[2px] hover:translate-y-\[2px] transition-all duration-200 font-black`

\- \[ ] 卡片包含： `rounded-none border-2 md:border-4 border-black shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)] bg-white`

\- \[ ] 输入框包含： `rounded-none border-2 md:border-4 border-black font-mono focus:shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] focus:outline-none`



\### 禁止项检查

\- \[ ] 没有使用 `rounded-sm`

\- \[ ] 没有使用 `rounded`

\- \[ ] 没有使用 `rounded-md`

\- \[ ] 没有使用 `rounded-lg`

\- \[ ] 没有使用 `rounded-xl`

\- \[ ] 没有使用 `rounded-2xl`

\- \[ ] 没有使用 `rounded-3xl`

\- \[ ] 没有使用 `shadow-sm`



\### 风格规则检查

\- \[ ] 使用纯黑边框 border-black border-2 md:border-4

\- \[ ] 使用硬边缘阴影 shadow-\[4px\_4px\_0px\_0px\_rgba(0,0,0,1)] md:shadow-\[8px\_8px\_0px\_0px\_rgba(0,0,0,1)]

\- \[ ] 保持直角 rounded-none

\- \[ ] 使用高对比度配色（黑白为主 + 鲜艳强调色）

\- \[ ] 标题使用 font-black，正文使用 font-mono



\### 风格漂移检查

\- \[ ] 没有违反：禁止使用圆角 rounded-lg, rounded-md, rounded-xl

\- \[ ] 没有违反：禁止使用渐变 bg-gradient-\*

\- \[ ] 没有违反：禁止使用灰色边框 border-gray-\*, border-slate-\*

\- \[ ] 没有违反：禁止使用淡入淡出的半透明效果

\- \[ ] 没有违反：禁止使用 rounded-full（装饰圆除外）



\### 通用交付检查

\- \[ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出

\- \[ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案

\- \[ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态

\- \[ ] 结果仍然能够一眼识别为 Neo-Brutalist



\---



\## \[EXAMPLES] 示例 Prompt



\### 1. SaaS 产品着陆页



包含 Hero、特性卡片、定价表、CTA



```

用 Neo-Brutalist 风格生成一个 SaaS 产品着陆页，要求：

1\. Hero 区域：大标题使用 font-black，鲜艳背景色（如 #ccff00），黑色粗边框按钮

2\. 特性区域：3 个卡片，每个有硬边缘阴影，hover 时阴影变为粉色

3\. 定价表：3 列，中间推荐列用强调色背景

4\. CTA：全宽黑色背景，白色大标题，粉色按钮

所有元素必须：无圆角、黑色粗边框、硬边缘阴影、hover 位移效果

```



\### 2. 博客文章页



包含标题、作者信息、正文、相关文章



```

用 Neo-Brutalist 风格创建一个博客文章页面，要求：

1\. 顶部：超大标题 font-black，作者信息带头像

2\. 正文：使用 font-mono，段落间距适中

3\. 侧边栏：相关文章卡片，有硬边缘阴影

4\. 分享按钮：图标按钮带黑色边框，hover 位移

所有元素遵循 Neo-Brutalist 规范：无圆角、纯黑边框、高对比配色

```



\### 3. 作品集展示



网格布局展示项目作品



```

用 Neo-Brutalist 风格设计一个作品集页面，要求：

1\. 导航栏：左侧 Logo 用 font-black，右侧链接 font-mono

2\. Hero：个人介绍，使用鲜艳背景色块

3\. 作品网格：2-3 列布局，每个卡片有缩略图和标题

4\. 卡片效果：黑色粗边框，hover 时阴影变色 + 轻微上移

5\. 联系区：简洁表单，输入框 focus 时出现硬阴影

```



\## 绝对禁止（匹配即拒绝）



以下模式一旦出现，视为风格违规——不找借口，直接重写。



\- 使用圆角 rounded-lg, rounded-md, rounded-xl

\- 使用渐变 bg-gradient-\*

\- 使用灰色边框 border-gray-\*, border-slate-\*

\- 使用淡入淡出的半透明效果

\- 使用 rounded-full（装饰圆除外）

\- 按钮 active 状态位移量小于原始阴影像素值（未完全压平，失去碾压感）

\- hover 背景色切换使用渐变或 opacity 过渡（必须是硬切，duration-150 ease-out）

\- 按钮 hover 时仅用位移替代阴影消失（hover 应增大阴影强调力量，active 才是完全压平）



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

\- \[ ] 禁止使用圆角 rounded-lg, rounded-md, rounded-xl

\- \[ ] 禁止使用渐变 bg-gradient-\*

\- \[ ] 禁止使用灰色边框 border-gray-\*, border-slate-\*

\- \[ ] 禁止使用淡入淡出的半透明效果

\- \[ ] 禁止使用 rounded-full（装饰圆除外）

