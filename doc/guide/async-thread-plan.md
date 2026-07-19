# AUI 异步线程计划

## 目标

AUI 可以注册自己的后台线程池，用来处理不依赖 Minecraft 主线程、不触碰 OpenGL、不直接修改 live DOM 的工作。目标是把 IO、解析、解码和纯计算从 MC client tick/render 热路径移走，同时保持所有 Document、Element、layout、render cache 的最终应用仍在主线程完成。

核心模型：

```text
AUI Worker Thread Pool
  -> 生成不可变结果
  -> Main Thread Apply Queue
  -> FrameTaskScheduler 按预算应用
  -> Document / Element / RenderQueue 生效
```

## 基本原则

- 后台线程只处理纯数据，不直接读写 live `Document`、`Element`、`RenderElement` cache。
- 所有 Minecraft client API、OpenGL、texture upload、事件派发、focus、hover、scroll、layout commit 都必须留在主线程。
- 后台任务输入必须是快照数据，例如 path、String、byte[]、资源版本号、document generation。
- 后台任务输出必须是不可变结果，例如 parsed tree、parsed stylesheet、decoded image bytes、resource list。
- 主线程 apply 时必须检查 generation，过期结果直接丢弃。
- apply 阶段也要走 `FrameTaskScheduler`，避免后台计算完成后又在主线程一次性提交大量 DOM 或缓存。

## 可以异步化的工作

- 资源扫描和资源列表构建。
- HTML 文本读取和 tokenize/parse 的纯数据阶段。
- CSS 文件读取、解析、selector index 预构建。
- JS 文本读取和全局脚本拼接准备。
- 图片读取、远程下载、格式解码。
- 字体文件读取和字体元信息解析。
- URL、路径、资源层级归并等纯计算。
- DevTools / ResourceManager 的大型列表数据准备。

## 不应直接异步化的工作

- 修改 `Document` / `Element` 树。
- 调用 `Element.init(...)`、`append(...)`、`remove(...)` 等 live DOM 操作。
- 读写 `RenderElement` 的 size、position、box、computedStyle、background、cursor 等 cache。
- 执行 layout、hit-test、paintList commit。
- 调用 Minecraft client 对象、resource manager 的非线程安全路径。
- OpenGL、texture upload、render call。
- 派发 DOM event、修改 focus/active/hover/selection。
- 执行用户 JS，除非后续引入明确的 JS worker 隔离模型。

## 线程组件设计

### `AuiWorker`

固定大小线程池，线程名建议为：

```text
ApricityUI-Worker-1
ApricityUI-Worker-2
...
```

建议默认线程数：

```text
max(1, min(2, availableProcessors / 2))
```

初期不要开太多线程，避免和 MC、资源加载、显卡驱动线程抢 CPU。

### `AuiTask<T>`

后台任务只做纯计算：

```text
Input snapshot -> Result
```

任务不持有 `Element` 引用。需要关联 document 时，只记录：

- document uuid
- document path
- refresh generation
- resource generation

### `AuiMainThreadQueue`

后台任务完成后，把 apply 动作投递到主线程队列。apply 动作也不应一次性无限执行，而是交给 `FrameTaskScheduler` 分帧消费。

### `FrameTaskScheduler`

现有主线程预算调度器继续作为 apply 层入口。后台结果到达后，只注册一个 frame task，由它按预算逐步应用。

## 推荐落地阶段

### Phase 1: 统一线程池和 apply 队列

- 新增 `AuiWorker`。
- 新增主线程 apply queue。
- 所有异步结果带 generation。
- `FrameScheduler.tick()` 中按顺序执行：
  1. async resource apply
  2. `AuiMainThreadQueue`
  3. `FrameTaskScheduler`
  4. document commit

### Phase 2: 资源列表异步化

优先把 ResourceManager / Loader 的静态资源列表准备放到 worker。

后台做：

- 扫描可用资源。
- 合并 resource pack/local/dev folder 层级。
- 排序。
- 生成不可变 `List<StaticResourceEntry>`。

主线程做：

- generation 检查。
- 替换缓存引用。
- 分帧创建 UI rows。

### Phase 3: HTML/CSS 解析异步化

后台做：

- 读取 HTML/CSS 字符串。
- HTML tokenize/parse 成纯数据树。
- CSS parse 成 rule list。
- selector index 预构建。

主线程做：

- 将纯数据树转换为 live `Element`。
- 运行 DOM expander。
- 提交样式和 layout。
- 触发 lifecycle event。

注意：`DOMContentLoaded` / `load` 只能在主线程完整 apply 后触发。

### Phase 4: 图片/字体解码统一化

已有 `ImageAsyncHandler`、`StyleAsyncHandler`、`NetworkAsyncHandler` 可以逐步迁到统一 worker 模型。

后台做：

- 网络下载。
- 图片解码。
- 字体解析。

主线程做：

- texture upload。
- 注册 font texture。
- 标记相关 document dirty。

### Phase 5: 初始化分帧 apply

在 Document 初始化中引入 loading/commit gate：

- 初始化 job 未完成前，document 不参与正常输入 hit-test。
- 可以显示旧画面或 loading 占位。
- 完成后一次性切换到新 document state，或按受控阶段切换。

只有完成这个 gate 后，才安全拆分 `Document.refresh()`。

## 性能边界

异步线程能减少主线程卡顿，但不能替代缓存和分帧 commit。

仍然必须保留：

- hit-test cache。
- layout/position/size cache。
- paintList cache。
- 主线程预算 apply。
- generation 过期丢弃。

不能把 layout 直接丢到后台线程，除非未来把 DOM/style/layout 数据结构整体改成不可变 snapshot，并且 render/input 都读同一个 committed snapshot。

## 失败处理

- worker 任务异常：记录一次 warn，丢弃该任务结果。
- generation 不匹配：静默丢弃。
- document 已关闭：静默丢弃。
- 资源读取失败：返回失败结果，由主线程决定显示 fallback 或 toast。

## 验证指标

每次异步化后用 JFR 验证：

- `Render thread` / client tick 中是否减少 long task。
- AUI 热点是否从 IO/parse/decode 转移出去。
- 主线程 apply 是否仍有单帧尖峰。
- 是否出现 layout/hit-test/render cache dirty 后的错位或事件滞后。

当前优先观察：

- `MouseEvent.hitTest`
- `Position.of`
- `Size.computeSize`
- `Style.finalizeComputedValues`
- `Loader.loadFilesystemStaticResources`
- `ResourceManager.refresh`
