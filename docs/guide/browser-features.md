# 浏览器辅助功能

AUI 不是浏览器，但给页面配了一层浏览器式的辅助行为：缩放、文字选择、剪贴板、表单默认动作、滚动。这篇讲这些行为本身和它们的边界。

页面的 viewport/字体/鼠标拦截三个 meta 的完整说明在 [ApricityScreen 文档](apricity-screen#页面-meta-配置)，DOM 和 JS API 的细节在 [Web API 文档](web-api)，这里都不重复。

## 页面缩放

`ApricityScreen` 和容器 Screen 都支持浏览器式缩放：Ctrl+滚轮、Ctrl+`+`/`-` 缩放，Ctrl+`0` 恢复初始值。范围、步进、是否允许用户缩放都由 `aui-viewport` meta 里的 `zoom/min-zoom/max-zoom/zoom-step/user-scalable` 控制。

缩放不是把画面拉伸——框架重算逻辑 viewport，渲染变换和命中测试跟着走，所以布局、鼠标命中、事件坐标始终一致。

几个要点：

- 缩放值按页面路径存到 `config/apricityui/viewport-zoom.properties`，重开页面会记住。排查"页面怎么还是放大的"时先想到它；
- `user-scalable=false` 只禁用户快捷键，Java/DevTools 的 `document.setViewportZoom(...)` 不受限；
- **运行时改 meta 的 content 不会重新应用**——meta 只在 Document 创建和 `refresh()` 时读取；
- Overlay 挡住缩放时，开 `config/apricityui-client.toml` 的 `[input] viewportZoomPassThrough = true`，没声明拦截的 Overlay 会被缩放逻辑跳过。它只影响缩放，不会让点击穿透真正拦截输入的 Overlay。

## 两个开发按键

都是可重绑定的 MC 按键，默认值如下：

| 默认键 | 行为 |
| --- | --- |
| `END` | 客户端资源重载 |
| 左 `Alt` | 按住时释放原生鼠标 |

**END** 触发完整重载：重扫资源、清缓存、刷新所有普通 Document 和内置工具。它是开发键，不是状态同步机制——`reloadPersistent=true` 的 Document 会被跳过（见 [Overlay 文档](overlay-document)）。

**左 Alt** 是"按住释放"，不是切换：在世界中、没开 Screen、没有 Overlay 时按住它释放鼠标，松开后恢复原状态。用来在世界内页面的调试场景里临时移动系统光标。它不改 viewport 也不改事件坐标。

## 文字选择与复制

选择面向**叶子元素**（没有子元素、有实际文本）。CSS 控制：

| CSS | 行为 |
| --- | --- |
| `user-select: text` | 拖拽选择 |
| `user-select: all` | 点击选中整段 |
| `user-select: none` | 禁止选择 |

想让人复制的内容尽量放在一个叶子元素里——没有跨段落、跨文本节点的完整 Selection。

快捷键：拖拽选择、Ctrl+A 全选、Ctrl+C 复制、Esc 清除；输入控件另有 Ctrl+X 剪切、Ctrl+V 粘贴、Ctrl+Z 撤销。点击其他元素或其他 Document 会清掉之前的选择，不会残留多处高亮。

`copy/cut/paste` 是可取消事件，`preventDefault()` 后框架不执行默认剪贴板动作。页面里没有 `navigator.clipboard`；Java 侧直接读写用：

```java
String value = Operation.getClipboardText();
Operation.setClipboardText(value);
```

## 键盘和表单默认行为

键盘事件字段：`key/code/keyCode/scanCode/repeat/altKey/shiftKey/controlKey/metaKey`。注意是 `controlKey` 不是 `ctrlKey`。

框架在脚本之前处理一批控件默认行为，成功时消费原生输入：

- 按钮类控件 Enter/Space 激活；
- 文本框 Enter 提交所在 form，textarea 的 Enter 换行；
- number 支持方向键和滚轮步进，range 支持方向键；
- select 全套键盘操作（方向/Home/End/PgUp/PgDn/Enter/Space/Esc/前缀搜索）；
- checkbox/radio 切换，file 走系统文件选择器，color 走 AUI 颜色选择器。

`preventDefault()` 可以拦这些默认动作。别在监听器里对同一个控件再 `click()`，会一次按键触发两次。

## 鼠标和滚动

鼠标/指针事件类型和坐标规则见 [Web API 文档](web-api#事件)。这里只说滚动：

- 滚轮默认作用于命中元素的可滚动祖先；
- Shift+滚轮优先横向滚动，没横向范围就回退纵向；
- 监听器 `preventDefault()` 后默认滚动不发生；
- 没有滚动范围的元素不会被强行移动。

## 和真实浏览器的差异

| 能力 | AUI 行为 |
| --- | --- |
| 网络/导航 | fetch 走 AUI 资源桥；location 的导航方法是空操作 |
| 剪贴板 | Ctrl 快捷键 + Java 的 `Operation`，没有 `navigator.clipboard` |
| 文本选择 | 叶子元素和输入控件，不是跨节点 Range |
| Meta | 创建/刷新时读取，运行时改 DOM 属性不重新应用 |
| 坐标 | 事件给的是逻辑坐标，别再乘缩放 |
| 文件/颜色选择 | MC/系统选择器，没有网页权限模型 |

"有同名 JS API"不等于浏览器规范细节都实现了。跨环境复用代码时，对缺的能力写显式降级。

## 常见问题

**Ctrl+滚轮缩放了 Overlay 而不是页面**：Overlay 在前面吃掉了滚轮。开 `viewportZoomPassThrough`，或给该 Overlay 明确拦截/穿透策略。

**改了 meta 没生效**：meta 只在创建和 refresh 时读。`refresh()` 或走 Java API。

**Ctrl+C 没复制到**：元素得是可选择叶子元素且真有选区；输入控件要先拿焦点；监听器 preventDefault 了 copy 也会拦住默认复制。

**事件坐标和准心对不上**：检查代码是不是重复乘了 renderScale / viewport zoom / devicePixelRatio。

**刷新后监听器没了**：预期行为。初始化逻辑放进 `DOMContentLoaded`/`load`，每次刷新重新绑。
