# 浏览器事件补全计划

本文档用于规划 ApricityUI 当前 Web 风格事件系统的补全方向。目标不是机械对齐浏览器标准，而是在现有 UI/脚本架构下，优先补齐最常用、最稳定、最容易形成正确心智模型的事件能力。

## 目标

- 统一事件模型，避免同类事件走不同派发路径
- 明确生命周期事件的状态流转和触发时机
- 优先补齐表单、滚动、窗口变化等高频事件
- 在不引入过多兼容负担的前提下，尽量靠近常见 Web 语义

## 当前现状

当前已经具备的事件能力主要包括：

- `document` 生命周期：`DOMContentLoaded`、`load`
- 鼠标相关：`mousemove`、`mousedown`、`mouseup`、`mouseover`、`mouseout`、`mouseenter`、`mouseleave`
- 键盘相关：`keydown`、`keyup`
- 焦点相关：`focus`、`blur`
- 表单局部：`change`（已覆盖 checkbox/radio 的典型路径）
- 窗口侧：`resize`

现阶段的主要问题不是“完全没有事件”，而是以下几点：

- 生命周期定义还不够系统，缺少文档级、窗口级、元素级边界说明
- `focus` / `blur` 走的是特化逻辑，没有完全复用统一派发链
- 滚轮输入和滚动结果目前混在 `"scroll"` 语义里
- 文本输入类控件缺少稳定的 `input` 事件
- 表单事件覆盖不均匀，`select` / `textarea` / 文本输入没有统一规则

## 设计原则

### 1. 先保真，再求全

优先实现用户最容易依赖的事件语义：

- 生命周期
- 输入
- 滚动
- 提交
- 窗口变化

不优先追求浏览器中边缘或历史遗留事件的全量兼容。

### 2. 区分“输入事件”和“结果事件”

这点非常重要。

- `wheel` 表示用户输入了滚轮动作
- `scroll` 表示某个滚动容器的滚动位置发生了变化
- `keydown` 表示键被按下
- `input` 表示控件值已经变化

不要把输入源事件和最终状态变化事件混在一个名字里。

### 3. 生命周期优先级最高

事件系统补全时，生命周期必须先定规则，再补其它事件。因为：

- 事件注册时机依赖生命周期
- 资源加载事件依赖生命周期
- 初始化脚本、副作用、延迟绑定都依赖生命周期
- 如果生命周期定义不清晰，后续事件再多也会难以预测

### 4. 尽量走统一派发链

除非事件天生不冒泡或不属于 DOM 树，否则应尽量复用统一派发入口，统一：

- `target`
- `currentTarget`
- capture / bubble
- `stopPropagation()`
- `preventDefault()`

`focus` / `blur`、未来的 `input` / `change` / `submit` 都应尽量遵守同一套派发模型。

## 生命周期规划

这一部分是本计划的重点。

## 生命周期状态机

建议把 `Document` 生命周期明确成一个小状态机，而不是仅靠几个散落的赋值点。

### 状态

- `loading`
- `interactive`
- `complete`
- `disposed`（内部状态，可不对外暴露）

其中：

- `loading` / `interactive` / `complete` 对外对应 `document.readyState`
- `disposed` 仅用于框架内部，表示文档已经被移除或不再参与事件、渲染、资源回调

### 状态流转

建议只允许以下路径：

1. `loading -> interactive`
2. `interactive -> complete`
3. `complete -> loading`（仅 `refresh()` 重建时允许）
4. `loading|interactive|complete -> disposed`

不建议出现：

- `interactive -> loading`（除非明确开始一次新的 refresh）
- `disposed -> loading`（销毁后应新建文档，而不是复活）

### 状态机约束

建议加几条硬约束：

- 每次 `refresh()` 必须生成新的生命周期轮次
- 同一轮次内，`DOMContentLoaded` 最多触发一次
- 同一轮次内，`load` 最多触发一次
- `disposed` 后不再接收异步资源回调、定时器副作用、输入事件

如果后续要支持异步资源事件，这个“轮次”概念会很重要，否则容易把旧文档回调打到新文档上。

## 生命周期分层

建议把生命周期拆成三层：

1. 文档生命周期
2. 元素生命周期
3. 窗口生命周期

三层不要混用。

### 文档生命周期

文档生命周期建议固定为：

1. `loading`
2. `interactive`
3. `complete`

对应语义如下：

- `loading`
  - `Document.refresh()` 开始
  - HTML 已开始重建
  - 脚本环境尚未完全就绪
- `interactive`
  - DOM 树已构建完成
  - DOM expanders 已执行
  - 样式已完成至少一轮稳定计算
  - 页面脚本可安全查询和绑定元素
- `complete`
  - 页面初始化脚本已执行完成
  - 初始生命周期事件已发出
  - 文档进入稳定运行态

### 文档生命周期事件

建议维持并明确以下事件：

- `DOMContentLoaded`
- `load`

建议语义：

- `DOMContentLoaded`
  - 在 DOM 构建完成、样式第一轮稳定后触发
  - 此时 `document.body`、`querySelector()`、大部分布局相关查询都可用
  - 页面初始化逻辑应优先绑定在这里
- `load`
  - 在页面初始化脚本执行完成后触发
  - 表示当前文档已完成“框架侧加载”
  - 不强行承诺等价于浏览器里所有子资源都已加载完成

这点要在文档里明确，否则用户会误以为 `load` 等于浏览器语义下的“所有资源完成加载”。

### 生命周期事件的注册语义

建议明确以下行为：

- 在 `DOMContentLoaded` 之前注册监听器，可以收到 `DOMContentLoaded` 和 `load`
- 在 `DOMContentLoaded` 之后、`load` 之前注册监听器，只能收到 `load`
- 在 `load` 之后注册监听器，不补发历史事件

也就是说，当前阶段建议采用“事件实时派发，不做历史补发”的模型。

这个规则需要写进文档，不然脚本作者会误以为后注册也能自动收到初始化事件。

### 建议的生命周期时序

建议固定为：

1. `readyState = "loading"`
2. 构建 DOM
3. 执行 DOM 扩展
4. 完成首轮样式计算
5. 构建渲染列表
6. `readyState = "interactive"`
7. 触发 `DOMContentLoaded`
8. 执行页面初始化后的收尾逻辑
9. `readyState = "complete"`
10. 触发 `load`

如果现有实现中页面脚本是在 `interactive` 之后执行，那么文档中必须明确说明这一点，避免用户误解。

### 文档卸载类事件

建议暂时不优先实现：

- `beforeunload`
- `unload`

原因：

- 当前并没有真实浏览器导航模型
- 文档销毁与资源释放路径还不是标准网页语义
- 这两个事件很容易给用户造成“可以阻止离开/刷新”的错误预期

建议先在计划中标记为“保留项”，不进入第一阶段。

### 文档移除与销毁

虽然暂不实现 `beforeunload` / `unload`，但框架内部仍应定义文档销毁规则：

- `Document.remove(...)` 后，文档应从全局输入命中、渲染队列、窗口观察器中退出
- 文档相关的 `ResizeObserver` / `MutationObserver` / 资源异步回调需要可安全失效
- 若未来实现 `unload`，该时机只能在文档真正退出活动集合时触发

这部分不一定立即对外暴露事件，但内部语义要先定。

## 元素生命周期

元素生命周期建议暂时只定义框架内部语义，不急着公开太多事件名。

推荐内部区分：

- 已创建但未挂载
- 已挂载到文档树
- 已完成首次样式计算
- 已完成首次可见渲染
- 已从文档树移除

如果后续需要对外开放，优先考虑：

- `load`
- `error`

但仅限有明确资源语义的元素，如：

- `img`
- 未来可能存在的远程资源节点

不要给普通 `div`、`span` 生造浏览器并不存在的生命周期事件。

## 窗口生命周期

窗口层当前已有 `resize`，建议把职责收敛为：

- `resize`
- 未来可选：`visibilitychange` 的轻量替代方案

暂不建议补：

- `pageshow`
- `pagehide`
- `beforeunload`

因为这些都依赖更完整的页面导航和宿主窗口语义。

## 事件补全优先级

## 第一阶段

这一阶段优先解决“能否稳定写交互页面”的问题。

### 1. `input`

优先级最高。

覆盖范围：

- `input[type=text]`
- `textarea`
- `select`
- checkbox / radio 可视情况补充

触发原则：

- 只要值发生用户可感知变化，就派发 `input`
- 文本编辑中应即时触发
- `select` 切换选项时触发

补充约束：

- 脚本直接 `el.value = ...` 暂不自动派发 `input`
- 仅用户交互路径派发 `input`
- 如果后续需要对齐浏览器，可再评估脚本侧是否通过更高层 API 主动补发

### 2. 标准 `scroll`

当前应把 `scroll` 定义为“滚动结果事件”。

触发范围：

- 鼠标滚轮导致的位置变化
- `el.scrollTo()` / `el.scrollBy()`
- `document.scrollTo()` / `document.scrollBy()`
- `window.scrollTo()` / `window.scrollBy()`

触发对象：

- 实际发生滚动的元素
- 文档滚动最终落在 `body` 时，文档侧可同步提供一层转发

补充约束：

- 如果滚动目标位置未变化，不派发 `scroll`
- 平滑滚动过程中，是否逐帧派发 `scroll` 需要提前定规则

建议第一阶段采用：

- 目标滚动值变化即派发
- 若有插值动画，则每帧实际位置变化时派发 `scroll`

因为这更符合用户对“监听滚动中状态”的直觉。

### 3. `wheel`

建议新增标准 `wheel`，与 `scroll` 分开。

建议字段：

- `deltaX`
- `deltaY`
- `deltaMode`
- `clientX`
- `clientY`
- `button`
- 修饰键状态

补充建议：

- 保留现有底层滚轮入口
- 对脚本层新增标准 `wheel`
- 内部先派发 `wheel`，再根据默认行为驱动滚动，再派发 `scroll`

推荐顺序：

1. `wheel`
2. 若未 `preventDefault()`，执行滚动默认行为
3. 若滚动位置变化，派发 `scroll`

### 4. `change`

统一规则：

- checkbox / radio：值变化后触发
- `select`：选中项变化后触发
- 文本输入：在“提交确认”或失焦后触发，而不是每次按键都触发

### 5. `submit`

如果表单接口继续往 Web 风格补齐，`submit` 非常关键。

建议行为：

- 支持 `form.addEventListener("submit", ...)`
- 支持 `preventDefault()`
- 支持由按钮点击、回车确认、脚本调用触发

建议最小范围：

- 优先支持 `form`
- `input[type=submit]` / `button[type=submit]` 可后补
- 先把事件模型打通，再做完整表单提交流程

## 第二阶段

### 6. `dblclick`

常用，成本低，容易定义。

### 7. `contextmenu`

右键交互常见，尤其是游戏 UI / 工具型界面。

### 8. `focusin` / `focusout`

如果需要可冒泡的焦点事件，这组比继续扩展 `focus` / `blur` 更有价值。

## 第三阶段

### 9. Pointer Events

建议未来补一整组，而不是零散补一个两个：

- `pointerdown`
- `pointerup`
- `pointermove`
- `pointerover`
- `pointerout`
- `pointerenter`
- `pointerleave`
- `pointercancel`

适用时机：

- 未来需要兼容触控、手写笔、虚拟指针、多输入源
- 不希望再维护一套平行的 mouse-only 语义

### 10. 资源事件

资源型元素优先考虑：

- `img load`
- `img error`

这类事件对 UI 组件化比 `unload` 更实际。

## 生命周期相关的具体实施建议

## 第一项：先收敛文档生命周期定义

先不要着急继续加事件名，先固定以下规则：

- `readyState` 只有 `loading -> interactive -> complete`
- `DOMContentLoaded` 和 `load` 的边界写进文档
- 明确 `load` 不承诺等同浏览器里的“全部子资源完成”
- 明确 refresh 轮次与旧回调失效规则

这是第一优先级。

## 第二项：统一生命周期事件的派发入口

当前生命周期、焦点、普通事件的派发路径不完全一致。建议做一次收敛：

- 文档生命周期事件尽量通过统一事件对象派发
- 焦点事件尽量复用统一事件链
- 对不冒泡事件，只调整 `bubbles = false`，不要额外走另一套半独立逻辑

这样后续排查事件问题会简单很多。

建议约束：

- `focus` / `blur` 默认不冒泡
- 如果后续补 `focusin` / `focusout`，再提供冒泡版本
- 生命周期事件默认不冒泡

## 第三项：定义元素级资源加载边界

如果未来要补 `img load` / `img error`，需要先定义：

- 资源开始请求的时机
- 解码成功的时机
- 首次可用于布局/绘制的时机
- 失败是否可重试

否则 `load` 事件会变得很飘。

## 推荐的事件对象补充

为了后续扩展，建议逐步补齐这些基础字段：

- `timeStamp`
- `eventPhase`
- `composed` 可暂缓
- `relatedTarget`（对 `mouseover` / `mouseout` / `mouseenter` / `mouseleave` 很有用）
- `isTrusted`（第一阶段可固定为用户输入 true / 脚本构造 false，实在不想扩展也可后补）

对特化事件建议逐步补齐：

- `KeyboardEvent.key`
- `KeyboardEvent.code`
- `KeyboardEvent.repeat`
- `MouseEvent.buttons`
- `WheelEvent.deltaX`
- `WheelEvent.deltaY`

## 测试建议

每补一类事件，都建议追加独立回归页，而不是只在现有 example 里手点验证。

至少补三类测试：

1. 生命周期测试
   - 记录 `readyState`
   - 记录 `DOMContentLoaded`
   - 记录 `load`
   - 校验触发顺序
   - 校验 refresh 后是否只触发当前轮次事件
   - 校验 load 后新增监听器不会收到历史补发

2. 表单测试
   - 文本输入 `input`
   - 文本输入 `change`
   - `select change`
   - checkbox/radio 的 `input` / `change`

3. 滚动测试
   - `wheel`
   - `scroll`
   - 脚本触发滚动
   - 冒泡和目标元素校验

4. 焦点测试
   - `focus`
   - `blur`
   - 多 document 切换焦点
   - 失焦后选区清理行为

## 代码落点建议

为了避免实现时到处散改，建议按层分工：

### 文档生命周期

- `src/main/java/com/sighs/apricityui/init/Document.java`

负责：

- `readyState`
- 生命周期轮次
- `DOMContentLoaded` / `load`
- 文档销毁时的清理入口

### 统一 DOM 事件派发

- `src/main/java/com/sighs/apricityui/init/Event.java`
- `src/main/java/com/sighs/apricityui/init/EventRegistry.java`

负责：

- capture / target / bubble
- 通用字段扩展
- 非冒泡事件的一致化派发

### 鼠标 / 滚轮 / 滚动

- `src/main/java/com/sighs/apricityui/event/MouseEvent.java`
- `src/main/java/com/sighs/apricityui/init/Operation.java`
- `src/main/java/com/sighs/apricityui/init/ScrollModel.java`

负责：

- `wheel`
- `scroll`
- 默认滚动行为
- 滚动事件与滚动模型联动

### 键盘 / 文本输入

- `src/main/java/com/sighs/apricityui/event/KeyEvent.java`
- `src/main/java/com/sighs/apricityui/init/Operation.java`
- `src/main/java/com/sighs/apricityui/element/AbstractText.java`
- `src/main/java/com/sighs/apricityui/element/Input.java`
- `src/main/java/com/sighs/apricityui/element/TextArea.java`
- `src/main/java/com/sighs/apricityui/element/Select.java`

负责：

- `input`
- `change`
- 回车确认
- 文本编辑路径

### 焦点

- `src/main/java/com/sighs/apricityui/init/FocusRing.java`

负责：

- `focus` / `blur`
- 焦点切换副作用
- 与统一派发链的对齐

### 窗口级事件

- `src/main/java/com/sighs/apricityui/init/Window.java`

负责：

- `resize`
- 后续可能扩展的窗口可见性或宿主侧事件

## 分阶段任务清单

### M1 生命周期收敛

- 文档中固定生命周期状态机
- 在 `Document` 中显式维护生命周期轮次
- 统一 `DOMContentLoaded` / `load` 触发时机
- 定义 refresh 后旧回调失效规则

### M2 焦点与派发统一

- 把 `focus` / `blur` 收到统一事件派发路径
- 补必要的非冒泡处理
- 为后续 `focusin` / `focusout` 预留结构

### M3 输入与表单事件

- 文本类控件补 `input`
- 统一 `change`
- 补最小可用 `submit`

### M4 滚轮与滚动

- 拆分 `wheel` 与 `scroll`
- 定义默认行为和 `preventDefault()`
- 统一脚本滚动与用户滚动的事件语义

### M5 扩展交互事件

- `dblclick`
- `contextmenu`
- 评估 Pointer Events

## 风险点

### 1. 旧脚本兼容性

如果当前测试页把滚轮事件直接写成 `"scroll"`，在拆分成 `wheel + scroll` 后，旧脚本可能行为变化。

建议过渡方案：

- 暂时保留旧 `"scroll"` 输入路径
- 文档标记为兼容层
- 新增标准 `wheel`
- 后续再决定是否移除旧语义

### 2. 焦点事件回归风险

`focus` / `blur` 一旦改成统一派发，可能影响：

- 当前直接遍历监听器的调用顺序
- `currentTarget` 值
- 文本选区清理时机

这部分必须补回归测试后再动。

### 3. 生命周期触发时机漂移

如果未来把脚本执行位置、异步资源预取、DOM 扩展顺序改了，`DOMContentLoaded` / `load` 的实际含义很容易漂。

建议把触发条件写成代码注释，并在测试页里固定断言顺序。

## 推荐实施顺序

建议按以下顺序推进：

1. 固定生命周期语义与文档说明
2. 统一生命周期/焦点事件派发模型
3. 实现 `input`
4. 拆分 `wheel` 和 `scroll`
5. 统一 `change`
6. 补 `submit`
7. 补 `dblclick` / `contextmenu`
8. 规划 Pointer Events
9. 视资源系统成熟度补 `img load` / `img error`

## 验收标准

完成第一阶段后，至少应满足：

- 用户能稳定依赖 `DOMContentLoaded` / `load`
- 文本输入控件能稳定触发 `input`
- 真实滚轮输入对应 `wheel`
- 滚动位置变化对应 `scroll`
- `change` 在表单控件上的触发时机一致
- 事件日志顺序可预测，且与文档一致

## 结论

这套事件系统下一步最该做的，不是继续零散加几个鼠标事件名，而是先把生命周期和事件分层定稳。

优先级判断如下：

1. 生命周期定义
2. `input`
3. `wheel` / `scroll` 拆分
4. `change` / `submit`
5. 其它交互事件

只要这几个点定下来，后续事件补全就会变成线性工作，不会反复返工。
