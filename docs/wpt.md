# WPT 布局对比

`wpt/` 把 Web Platform Tests 的 CSS 布局页面当基准：同一份 HTML 先在 Chromium 里采集元素几何快照，再让 AUI 客户端解析采集，两端按 DOM 顺序比 tag/id 和矩形（阈值 0.25 CSS 像素）。

**`pass` 只代表"AUI 和浏览器的几何快照一致"**，不代表通过 WPT 规范测试——testharness 断言、reftest 参考页、JS 语义都不在执行范围内。

## 一次性准备：corpus

`wpt/corpus` 是固定 revision 的 WPT sparse checkout（被 gitignore，要自己拉）：

```powershell
git clone --filter=blob:none --no-checkout https://github.com/web-platform-tests/wpt.git wpt/corpus
git -C wpt/corpus sparse-checkout init --cone
git -C wpt/corpus sparse-checkout set common css/CSS2 css/css-align css/css-box css/css-break css/css-contain css/css-display css/css-flexbox css/css-grid css/css-inline css/css-multicol css/css-overflow css/css-position css/css-sizing css/css-tables css/css-text css/css-transforms css/css-ui css/css-values css/css-writing-modes css/support fonts resources
git -C wpt/corpus checkout a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
```

revision 固定在 `wpt/config/runner.json` 的 `wptRevision`。别日常 `git pull`——上游变了结果就不可重复。

环境还需要：Node.js、Chrome/Chromium/Edge（找不到就设 `CHROME_PATH`）、Java 17 + Gradle wrapper。

## 跑

仓库根目录：

```powershell
node wpt/tools/run.mjs --mode inventory              # 只扫描建清单，不启动浏览器和 MC
node wpt/tools/run.mjs --mode incremental            # 日常用：只跑新的/变了的/没过的
node wpt/tools/run.mjs --mode incremental --limit 20 # 调试用小批量
node wpt/tools/run.mjs --mode full                   # 检查全部（仍复用已 pass 的快照）
```

- `--limit N` 取按路径排序后的前 N 个，**不是**指定某个文件，也没有随机采样。要缩小范围就改配置里的 `layoutDirectories` 或写个自定义 `--config`；
- incremental 复用规则：sourceHash 没变且浏览器/AUI 各自 pass 过的阶段直接跳过；
- 要完全重采：停掉所有相关进程后删 `wpt/output/` 再跑 full。这会丢掉历史结果，不是日常操作。

执行链：扫描建 inventory → 浏览器阶段（本地 HTTP server + headless Chrome 探针）→ 浏览器 pass 的进 AUI 阶段（`gradlew runWptClient` 起 MC 客户端逐批解析）→ 比较写结果。中途 kill 的话 results 可能有部分新结果，summary 还是上次的。

## 看结果

| 文件 | 内容 |
| --- | --- |
| `wpt/progress.md` | 人看的进度表（自动生成，别手改） |
| `wpt/output/results.json` | 逐用例结果，机器处理读这个 |
| `wpt/output/run-summary.json` | 最近一次运行摘要 |
| `wpt/output/browser/<hash>.json` | 浏览器快照 |
| `wpt/runtime/logs/latest.log` | AUI 客户端日志（排查 AUI 侧问题看它，搜 `[AUI WPT]`） |

最终状态：

| 状态 | 含义 |
| --- | --- |
| `pass` | 两端快照一致 |
| `layout-mismatch` | 都有快照但节点数/tag/id/矩形超阈值 |
| `aui-runtime-unsupported` | AUI 抛异常、没解析完、没回传 |
| `browser-test-failed` | Chrome 加载或探针失败 |
| `infra-blocked` | 页面需要 WPT server 或外部网络，主动跳过（**不是布局失败**） |
| `timeout` | 任一环节超时 |
| `pending` | 还没跑 |

**排查 mismatch 的标准动作**：从 results 找到 case → 看 `reason`（形如 `node-count browser=7 aui=14`、`node-3 rect[2] browser=120 aui=119.75`）→ 对比 `wpt/output/browser/` 里的快照和 results 内嵌的 aui 快照，按节点顺序找第一处差异。

node-count / identity 差异优先查 HTML 解析、head 节点、隐藏节点和默认样式——**别上来就改布局算法**。AUI 端目前不过滤 style/script/link 节点而浏览器端过滤，视觉相同也可能 mismatch。

页面分类是源码正则启发式：含 testharness.js 标记 testharness、含 server 依赖模式（`.sub.`、`?pipe=`、testdriver.js 等）或外部 HTTP 资源标记 infra-blocked。这些页面不会进浏览器批处理。

## 常见问题

**报 layout directory missing**：corpus 没拉或 sparse checkout 缺目录，`git -C wpt/corpus sparse-checkout list` 对一下。

**找不到浏览器**：设 `CHROME_PATH` 指向实际 exe。

**大量 infra-blocked**：分类结果，不是 Chrome 崩了。当前实现不会自己起 WPT server。

**AUI 没写结果**：手动跑 `./gradlew.bat runWptClient` 时没设 `AUI_WPT_CLIENT_INPUT/OUTPUT` 环境变量就不会进 WPT 流程——这是预期，正常由 runner 设。真出问题看 `wpt/runtime/logs/latest.log`。

**大量 aui-runtime-unsupported**：结合 results 的 `aui.reason` 和 runtime 日志分：页面用了 AUI 没实现的 CSS、refresh 抛异常、批次中断、watchdog 触发，是不同的问题。

## 注意

- 别同时跑多个 full 任务；
- 固定 revision、viewport、浏览器版本，结果才可重复；
- `wpt/output/` 是可重建缓存，`wpt/runtime/` 含日志和世界数据，都别提交；
- 清 output 前确认没有跑着的 Node/Gradle/MC 进程。
