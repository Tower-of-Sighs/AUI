STYLEKIT_STYLE_REFERENCE
style_name: 手绘涂鸦风
style_slug: hand-drawn-doodle
style_source: /styles/hand-drawn-doodle

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

You are a Hand-Drawn Doodle design style frontend development expert. All generated code must strictly follow these constraints:

## 绝对禁止

- 禁止使用精确的几何形状和直角（rounded-none）
- 禁止使用渐变效果
- 禁止使用精确阴影（shadow-md 等）
- 禁止使用等宽字体
- 禁止使用实线边框（border-solid）
- 禁止使用大圆角（rounded-lg 及以上）

## 必须遵守

- 使用虚线边框（border-dashed）模拟手绘线条
- 使用奶白纸张色 #fffef5 背景
- 使用墨黑 #2c2c2c 作为主色
- 添加微妙旋转（rotate）模拟手绘不规则感
- 使用标记笔配色：红 #ff6b6b、蓝绿 #4ecdc4、黄 #ffd93d
- 使用无衬线字体，保持随意感
- 添加笔记本横线背景
- 使用胶带/图钉/回形针等装饰元素

## Color Palette

Primary:
- Ink Black: #2c2c2c (text, borders)
- Paper White: #fffef5 (backgrounds)
- Red Marker: #ff6b6b (accents, highlights)
- Teal Marker: #4ecdc4 (shadows, accents)
- Yellow Marker: #ffd93d (highlights, tape)

## Special Elements

- Notebook line backgrounds (repeating-linear-gradient)
- Red margin line decorations
- Wavy underlines (text-decoration-style: wavy)
- Marker highlight effects
- Tape, pushpin, and paperclip decorations on cards
- Subtle element rotations for sketchy feel
- Spiral binding holes on left edge
- Coffee stain ring decorations
- Hand-drawn stars and squiggles via SVG

## Animation & Interaction Rules

- Stop-Motion Jitter: hover 通过微小旋转与位移制造手绘颤动感，避免平滑机械运动。
- Ink Filling: 可增强边框粗细或颜色对比来模拟“补笔”，但仍保持 border-dashed 手绘语义。
- Offset Smudge: 仅使用硬边偏移阴影，hover 时改变偏移量与标记笔颜色，像二次描边。
- Paper Press: active 状态压平阴影并轻微缩放（如 scale-[0.98]），呈现手指按纸反馈。

---

# Hand-Drawn Doodle (手绘涂鸦风) Design System

> 手绘线条、涂鸦插画、不规则形状和手写字体。像在笔记本上随手画出的设计，充满创意和趣味性，传达温暖亲切的手工感。

## 核心理念

Hand-Drawn Doodle 风格模拟手工绘制的质感，营造温暖、亲切、创意十足的视觉体验。

核心理念：
- 笔记本纸张：奶白色背景模拟真实笔记本，带有蓝色横线和红色页边线
- 手绘线条：使用虚线边框模拟手绘笔触，避免精确几何
- 不规则形态：微妙的旋转和偏移营造手工感
- 标记笔配色：红、蓝绿、黄三色标记笔点缀
- 装饰元素：胶带、图钉、回形针、咖啡渍等纸张装饰
- 涂鸦点缀：随手画的星星、波浪线、箭头等装饰

设计原则：
- 视觉一致性：所有组件必须遵循统一的视觉语言，从色彩到字体到间距保持谐调
- 层次分明：通过颜色深浅、字号大小、留白空间建立清晰的信息层级
- 交互反馈：每个可交互元素都必须有明确的 hover、active、focus 状态反馈
- 响应式适配：设计必须在移动端、平板、桌面端上保持一致的体验
- 无障碍性：确保色彩对比度符合 WCAG 2.1 AA 标准，所有交互元素可键盘访问

---

## Token 字典（精确 Class 映射）

### 边框
```
宽度: border-2
颜色: border-[#2c2c2c]
圆角: rounded-sm
```

### 阴影
```
小: shadow-[2px_2px_0px_#4ecdc4]
中: shadow-[3px_3px_0px_#4ecdc4]
大: shadow-[4px_4px_0px_#4ecdc4]
悬停: hover:shadow-[6px_6px_0px_#4ecdc4]
聚焦: focus:shadow-[2px_2px_0px_#ffd93d]
```

### 交互效果
```
悬停位移: hover:translate-x-[1px] hover:translate-y-[1px]
悬停缩放: （无）
悬停透明度: （无）
过渡动画: transition-all duration-200 ease-in-out
按下状态: active:translate-x-[2px] active:translate-y-[2px] active:shadow-none
```

### 字体
```
标题: font-sans font-bold
正文: font-sans
等宽: font-mono
```

### 字号
```
Hero: text-4xl md:text-6xl lg:text-8xl
H1: text-3xl md:text-5xl
H2: text-2xl md:text-4xl
H3: text-xl md:text-2xl
正文: text-sm md:text-base
小字: text-xs md:text-sm
```

### 间距
```
Section: py-12 md:py-20 lg:py-28
容器: px-4 md:px-8 lg:px-12
卡片: p-5 md:p-8
小间距: gap-3 md:gap-4
中间距: gap-4 md:gap-6
大间距: gap-6 md:gap-10
```

### 颜色角色
```
背景主色: bg-[#fffef5]
背景辅色: bg-[#f8f7f0]
背景强调色: bg-[#ff6b6b], bg-[#4ecdc4], bg-[#ffd93d]
正文主色: text-[#2c2c2c]
正文辅色: text-[#ff6b6b]
正文弱化色: text-[#2c2c2c]/45
按钮主色: bg-[#2c2c2c] text-[#fffef5] shadow-[3px_3px_0px_#ff6b6b]
按钮辅色: bg-[#ff6b6b] text-[#fffef5] shadow-[3px_3px_0px_#4ecdc4]
```

---

## [FORBIDDEN] 绝对禁止

以下 class 在本风格中**绝对禁止使用**，生成时必须检查并避免：

### 禁止的 Class
- `bg-gradient-to-r`
- `bg-gradient-to-b`
- `bg-gradient-to-br`
- `shadow-md`
- `shadow-lg`
- `shadow-xl`
- `font-mono`
- `backdrop-blur`
- `backdrop-blur-sm`
- `rounded-none`
- `rounded-lg`
- `rounded-xl`
- `rounded-2xl`
- `rounded-full`
- `border-solid`

### 禁止的模式
- 匹配 `^bg-gradient-`
- 匹配 `^shadow-(?:sm|md|lg|xl|2xl)$`
- 匹配 `^font-mono$`
- 匹配 `^backdrop-blur`
- 匹配 `^rounded-(?:none|lg|xl|2xl|full)$`

### 禁止原因
- `bg-gradient-to-r`: Hand-Drawn Doodle uses flat marker colors only, no gradients
- `shadow-md`: Hand-Drawn Doodle uses hard offset shadows (shadow-[Npx_Npx_0px_color]) to mimic marker strokes
- `font-mono`: Hand-Drawn Doodle uses casual sans-serif fonts, never technical monospace
- `backdrop-blur`: Hand-Drawn Doodle is opaque notebook paper, no blur or glass effects
- `rounded-none`: Hand-Drawn Doodle uses rounded-sm for a slightly imprecise hand-drawn feel
- `rounded-lg`: Hand-Drawn Doodle uses rounded-sm only - larger radii look too polished
- `border-solid`: Hand-Drawn Doodle uses border-dashed to simulate hand-drawn line strokes

> WARNING: 如果你的代码中包含以上任何 class，必须立即替换。

---

## [REQUIRED] 必须包含

### 按钮必须包含
```
rounded-sm
font-sans font-semibold
border-2 border-dashed
transition-all duration-200 ease-in-out
```

### 卡片必须包含
```
rounded-sm
bg-[#fffef5]
border-2 border-dashed border-[#2c2c2c]
shadow-[4px_4px_0px_#4ecdc4]
```

### 输入框必须包含
```
rounded-sm
border-2 border-dashed border-[#2c2c2c]
bg-[#fffef5]
font-sans
focus:outline-none
```

---

## [COMPARE] Hand-Drawn Doodle 错误 vs 正确对比

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
<button class="rounded-sm font-sans font-semibold border-2 border-dashed transition-all duration-200 ease-in-out bg-[#2c2c2c] text-[#fffef5] shadow-[3px_3px_0px_#ff6b6b]">
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
<div class="rounded-sm bg-[#fffef5] border-2 border-dashed border-[#2c2c2c] shadow-[4px_4px_0px_#4ecdc4] p-5 md:p-8">
  <h3 class="font-sans font-bold text-xl md:text-2xl">{TITLE}</h3>
</div>
```

### 输入框

[WRONG] **错误示例**（未经当前风格适配的通用输入框）：
```html
<input class="{GENERIC_LIBRARY_INPUT_DEFAULT}" />
```

[CORRECT] **正确示例**（使用当前风格的 input token）：
```html
<input class="rounded-sm border-2 border-dashed border-[#2c2c2c] bg-[#fffef5] font-sans focus:outline-none" placeholder="{PLACEHOLDER}" />
```

---

## [TEMPLATES] Hand-Drawn Doodle 页面骨架模板

以下骨架只使用当前风格的 token。替换 `{PLACEHOLDER}` 时，不要移除或替换这些 token：

### 导航栏骨架
```html
<nav class="bg-[#fffef5] text-[#2c2c2c] border-2 border-[#2c2c2c] px-4 md:px-8 lg:px-12">
  <div class="flex items-center justify-between max-w-6xl mx-auto gap-4 md:gap-6">
    <a href="/" class="font-sans font-bold text-xl md:text-2xl">
      {LOGO_TEXT}
    </a>
    <div class="flex gap-4 md:gap-6 font-sans text-xs md:text-sm">
      {NAV_LINKS}
    </div>
  </div>
</nav>
```

### Hero 区块骨架
```html
<section class="bg-[#ff6b6b] text-[#2c2c2c] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">
  <div class="max-w-4xl mx-auto">
    <h1 class="font-sans font-bold text-4xl md:text-6xl lg:text-8xl">
      {HEADLINE}
    </h1>
    <p class="font-sans text-sm md:text-base max-w-xl">
      {SUBHEADLINE}
    </p>
    <button class="rounded-sm font-sans font-semibold border-2 border-dashed transition-all duration-200 ease-in-out bg-[#2c2c2c] text-[#fffef5] shadow-[3px_3px_0px_#ff6b6b]">
      {CTA_TEXT}
    </button>
  </div>
</section>
```

### 卡片网格骨架
```html
<section class="bg-[#fffef5] text-[#2c2c2c] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">
  <div class="max-w-6xl mx-auto">
    <h2 class="font-sans font-bold text-2xl md:text-4xl">{SECTION_TITLE}</h2>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
      <!-- Card template - repeat for each card -->
      <div class="rounded-sm bg-[#fffef5] border-2 border-dashed border-[#2c2c2c] shadow-[4px_4px_0px_#4ecdc4] p-5 md:p-8">
        <h3 class="font-sans font-bold text-xl md:text-2xl">{CARD_TITLE}</h3>
        <p class="font-sans text-sm md:text-base text-[#2c2c2c]/45">{CARD_DESCRIPTION}</p>
      </div>
    </div>
  </div>
</section>
```

### 表单输入骨架
```html
<input class="rounded-sm border-2 border-dashed border-[#2c2c2c] bg-[#fffef5] font-sans focus:outline-none" placeholder="{PLACEHOLDER}" />
```

### 页脚骨架
```html
<footer class="bg-[#f8f7f0] text-[#ff6b6b] py-12 md:py-20 lg:py-28 px-4 md:px-8 lg:px-12">
  <div class="max-w-6xl mx-auto">
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 md:gap-10">
      <div>
        <span class="font-sans font-bold text-xl md:text-2xl">{LOGO_TEXT}</span>
        <p class="font-sans text-xs md:text-sm">{TAGLINE}</p>
      </div>
      <div>
        <h4 class="font-sans font-bold text-xl md:text-2xl">{COLUMN_TITLE}</h4>
        <ul class="font-sans text-xs md:text-sm">
          {FOOTER_LINKS}
        </ul>
      </div>
    </div>
  </div>
</footer>
```

---

## [CHECKLIST] Hand-Drawn Doodle 生成后自检清单

**输出代码前，逐项验证当前风格的 token 和规则。如有违反，先修正再交付：**

### Token 检查
- [ ] 按钮包含： `rounded-sm font-sans font-semibold border-2 border-dashed transition-all duration-200 ease-in-out`
- [ ] 卡片包含： `rounded-sm bg-[#fffef5] border-2 border-dashed border-[#2c2c2c] shadow-[4px_4px_0px_#4ecdc4]`
- [ ] 输入框包含： `rounded-sm border-2 border-dashed border-[#2c2c2c] bg-[#fffef5] font-sans focus:outline-none`

### 禁止项检查
- [ ] 没有使用 `bg-gradient-to-r`
- [ ] 没有使用 `bg-gradient-to-b`
- [ ] 没有使用 `bg-gradient-to-br`
- [ ] 没有使用 `shadow-md`
- [ ] 没有使用 `shadow-lg`
- [ ] 没有使用 `shadow-xl`
- [ ] 没有使用 `font-mono`
- [ ] 没有使用 `backdrop-blur`

### 风格规则检查
- [ ] 使用虚线边框（border-dashed）模拟手绘线条
- [ ] 使用奶白纸张色 #fffef5 背景
- [ ] 使用墨黑 #2c2c2c 作为主色
- [ ] 添加微妙旋转（rotate）模拟手绘不规则感
- [ ] 使用标记笔配色：红 #ff6b6b、蓝绿 #4ecdc4、黄 #ffd93d

### 风格漂移检查
- [ ] 没有违反：禁止使用精确的几何形状和直角（rounded-none）
- [ ] 没有违反：禁止使用渐变效果
- [ ] 没有违反：禁止使用精确阴影（shadow-md 等）
- [ ] 没有违反：禁止使用等宽字体
- [ ] 没有违反：禁止使用实线边框（border-solid）

### 通用交付检查
- [ ] 响应式布局在手机、平板和桌面下稳定，没有横向溢出
- [ ] 所有交互元素有清晰焦点、可访问名称和 reduced-motion 方案
- [ ] 文本对比度达到 WCAG AA，且没有用颜色单独传递状态
- [ ] 结果仍然能够一眼识别为 Hand-Drawn Doodle

---

## [EXAMPLES] 示例 Prompt

### 1. 手绘涂鸦着陆页

笔记本风格的创意着陆页，带涂鸦装饰

```
Use Hand-Drawn Doodle style to create a landing page:
1. Background: paper-white with notebook lines and red margin
2. Title: bold sans-serif with subtle rotation like handwriting
3. Cards: dashed borders with marker-color shadows, tape/pin decorations
4. Use only marker colors (red, teal, yellow) for accents
5. Add notebook elements: spiral holes, coffee stains, tape strips
6. Overall hand-crafted, sketchy, creative notebook feel
```

### 2. SaaS 着陆页

生成 手绘涂鸦风风格的 SaaS 产品着陆页

```
Create a SaaS landing page using Hand-Drawn Doodle style with hero section, feature grid, testimonials, pricing table, and footer.
```

### 3. 作品集展示

生成 手绘涂鸦风风格的作品集页面

```
Create a portfolio showcase page using Hand-Drawn Doodle style with project grid, about section, contact form, and consistent visual language.
```

## 绝对禁止（匹配即拒绝）

以下模式一旦出现，视为风格违规——不找借口，直接重写。

- 使用精确的几何形状和直角（rounded-none）
- 使用渐变效果
- 使用精确阴影（shadow-md 等）
- 使用等宽字体
- 使用实线边框（border-solid）
- 使用大圆角（rounded-lg 及以上）

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
- [ ] 禁止使用精确的几何形状和直角（rounded-none）
- [ ] 禁止使用渐变效果
- [ ] 禁止使用精确阴影（shadow-md 等）
- [ ] 禁止使用等宽字体
- [ ] 禁止使用实线边框（border-solid）