# 浏览器事件计划附录：生命周期与实施检查

本文档是 `browser-event-plan.md` 的补充附录，重点补充生命周期状态机、事件矩阵、异步安全约束，以及真正开始实现时可直接对照的检查表。

## 生命周期事件矩阵

建议把文档生命周期事件固定成下面这张矩阵。

| 场景 | readyState | 允许查询 DOM | 允许绑定事件 | 会触发的生命周期事件 |
| --- | --- | --- | --- | --- |
| `refresh()` 刚开始 | `loading` | 否 | 否 | 无 |
| DOM 构建完成，expand/style 首轮完成 | `interactive` | 是 | 是 | `DOMContentLoaded` |
| 初始化脚本与首轮收尾完成 | `complete` | 是 | 是 | `load` |
| 文档被移除 | 内部 `disposed` | 否 | 否 | 当前阶段无对外事件 |

配套约束：

- `DOMContentLoaded` 之前不承诺 DOM 查询稳定
- `interactive` 开始后，脚本可以安全读取 `document.body`、执行 `querySelector`
- `load` 之后才算“当前文档初始化完成”
- `disposed` 后文档对象即使还被脚本持有，也不应继续接收框架驱动事件

## 生命周期与异步任务

后续实现时，生命周期必须和异步任务解绑清楚。

建议规则：

- 资源预取、定时器、异步回调在触发前先校验文档轮次
- 若轮次已过期，直接丢弃回调
- 若文档已 `disposed`，直接丢弃回调

推荐在文档对象上统一暴露：

- 当前生命周期轮次
- 当前是否仍为活动文档
- 一个用于异步回调校验的轻量 helper

这能避免以下问题：

- 旧文档的图片加载回调修改新文档状态
- refresh 期间旧定时器继续写 DOM
- 旧 observer 在文档销毁后继续回调

## refresh 语义

`refresh()` 建议被视为“一次完整重建”，而不是局部热更新。

推荐语义：

- 旧 DOM 树失效
- 生命周期轮次递增
- `readyState` 回到 `loading`
- 旧生命周期事件不补发到新树
- 新树重新经历 `interactive -> complete`

需要明确一个边界：

- refresh 不是导航
- refresh 也不是增量 patch
- refresh 是“同一路径文档的一次新实例化过程”

## 代码级接口建议

建议在代码里显式增加生命周期入口，避免在多个位置直接散写字符串状态：

- `beginRefreshLifecycle()`
- `enterInteractive()`
- `enterComplete()`
- `disposeLifecycle()`

不一定非要使用这些名字，但需要有明确的单一入口。

同时建议统一以下行为：

- `dispatchEvent()` 返回值语义
- `preventDefault()` 对可取消事件的影响
- 脚本构造事件和框架内部事件的字段完整度

## 实现检查表

### 生命周期

- [ ] `readyState` 只通过统一入口修改
- [ ] `refresh()` 会递增生命周期轮次
- [ ] 每轮最多一次 `DOMContentLoaded`
- [ ] 每轮最多一次 `load`
- [ ] `complete` 之后不会补发历史生命周期事件
- [ ] `disposed` 后文档退出全局活动集合

### 派发一致性

- [ ] 生命周期事件走统一事件对象
- [ ] `focus` / `blur` 语义与其它事件一致
- [ ] 非冒泡事件仍正确设置 `target/currentTarget`
- [ ] `dispatchEvent()` 返回值规则明确

### 输入与表单

- [ ] 文本用户输入触发 `input`
- [ ] 文本失焦或确认触发 `change`
- [ ] `select` 改值触发 `change`
- [ ] 值未变化时不触发 `change`
- [ ] `submit` 支持 `preventDefault()`

### 滚动

- [ ] 用户滚轮先触发 `wheel`
- [ ] 未被取消时才执行默认滚动
- [ ] 滚动位置变化后触发 `scroll`
- [ ] 脚本滚动也能触发 `scroll`

### 异步安全

- [ ] observer 回调校验文档轮次
- [ ] 资源回调校验文档轮次
- [ ] 定时器副作用校验文档活动状态
- [ ] remove / dispose 后旧回调自动失效

## 补充测试项

### `refresh` 与异步回调测试

- refresh 前发起异步资源请求
- refresh 后旧回调不得污染新文档
- remove 后 observer 不再回调

### 生命周期监听器时机测试

- `DOMContentLoaded` 前注册，能收到两个事件
- `DOMContentLoaded` 后注册，只能收到 `load`
- `load` 后注册，不收到历史补发
