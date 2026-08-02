# WPT 目录完整说明

本文档说明 ApricityUI 仓库中 `wpt/` 目录的用途、结构、配置、执行流程、输出格式、AUI 客户端桥接和故障排查。

WPT 是 Web Platform Tests。当前 ApricityUI 集成的目标是把 WPT CSS 布局页面作为浏览器布局参考：先在 Chromium 中采集几何快照，再让 AUI 解析同一份 HTML 并采集几何快照，最后比较两端的 DOM 顺序和矩形数据。

本文档描述当前代码已经实现的行为。`wpt/README.md` 还包含未来的 manifest、测试分层和接入规划；其中提到但当前不存在的 `config/cases/`、`capture-baseline.mjs`、`report.mjs` 和参数化 `WptLayoutCase` 不应当当作现有命令使用。

## 1. 能力边界

当前流程可以：

- 扫描配置的 WPT CSS 目录。
- 识别 `.html`、`.htm` 和 `.xhtml` 文件。
- 计算源码 SHA-256，并生成分类清单。
- 对不需要 WPT server 或外部网络的页面启动本地 HTTP server。
- 用 Chromium 注入探针，采集元素矩形、滚动尺寸和部分计算样式。
- 通过 Forge 的 `runWptClient` 启动 AUI 客户端，解析同一页面并采集元素矩形。
- 以 0.25 CSS 像素的硬编码阈值比较浏览器和 AUI 的节点结果。
- 写出 inventory、逐用例结果、运行摘要和进度表。

当前流程不会：

- 执行 `testharness.js` 的 WPT 断言。
- 执行标准 reftest 参考页比较。
- 启动完整的 WPT server 或模拟 `testdriver.js`。
- 处理所有浏览器 API、外部资源、字体和 JavaScript 语义。
- 进行 PNG 视觉差异比较。
- 通过命令行直接选择任意一个具体的 WPT 路径。

因此，最终状态为 `pass` 只代表“当前浏览器快照与 AUI 快照比较通过”，不代表该页面完整通过了 WPT 规范测试。

## 2. 目录结构

~~~text
wpt/
├── README.md                 WPT 集成设计和范围说明
├── progress.md               runner 生成的进度表
├── .gitignore                忽略本地 corpus、output、runtime
├── config/
│   └── runner.json           默认 runner 配置
├── tools/
│   ├── run.mjs               主入口、扫描、调度、比较和写结果
│   ├── browser-adapter.mjs   Chromium/Edge 适配器
│   └── aui-adapter.mjs       Gradle/AUI 客户端适配器
├── corpus/                   WPT sparse checkout，本地且被忽略
├── output/                   快照、结果和临时文件，被忽略
└── runtime/                  Forge/Minecraft WPT 客户端运行目录，被忽略
~~~

### 2.1 config

目前只有 `config/runner.json`。它是 WPT runner 配置，不是 Minecraft 的 `config/apricityui-client.toml`。

### 2.2 tools

三个脚本组成当前执行链：

- `run.mjs`：解析参数、扫描文件、建立 inventory、保留旧结果、调用两个适配器、比较快照并写文件。
- `browser-adapter.mjs`：查找浏览器、启动本地 server、注入几何探针、保存浏览器快照。
- `aui-adapter.mjs`：创建临时 TSV，调用 `runWptClient`，解析 AUI 返回结果并处理批次重试。

当前没有独立的 inventory/report/baseline 脚本，相关逻辑都在上述文件中。

### 2.3 corpus

`corpus/` 是固定 revision 的 WPT 上游工作副本。它不是仓库源码，`wpt/.gitignore` 的 `/corpus/` 规则会忽略它。

runner 会检查 corpus 根目录和所有配置的布局目录是否存在；缺少任意一个目录都会直接失败。

### 2.4 output

`output/` 是生成结果目录：

- `inventory.json`：当前 corpus 的分类清单。
- `results.json`：逐页面的阶段结果和最终状态。
- `run-summary.json`：最近一次运行的摘要。
- `browser/`：内容哈希命名的浏览器 JSON 快照。
- `aui/`：AUI 适配器临时输入和输出目录。

AUI snapshot 当前内嵌在 `results.json`，不会为每个 AUI 页面单独写持久化 JSON。正常完成后 AUI 临时 TSV 会删除；中断时可能残留。

### 2.5 runtime

`runtime/` 是 Gradle `wptClient` run 使用的 Minecraft/Forge `gameDirectory`，可能包含模组、配置、日志、缓存和世界数据。它不是 WPT 脚本目录，也不应提交。

排查 AUI 运行问题时重点查看：

~~~text
wpt/runtime/logs/latest.log
wpt/runtime/logs/debug.log
~~~

### 2.6 progress.md

`progress.md` 由 runner 生成，文件头注明不要手工编辑。它适合人工查看状态，机器处理应读取 `wpt/output/results.json`。

## 3. Corpus 范围和 revision

当前默认 WPT revision：

~~~text
a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
~~~

默认候选布局目录：

| 方向 | 目录 |
| --- | --- |
| CSS2 | `css/CSS2` |
| 对齐和盒模型 | `css/css-align`、`css/css-box` |
| 断行、包含和显示 | `css/css-break`、`css/css-contain`、`css/css-display` |
| Flex 和 Grid | `css/css-flexbox`、`css/css-grid` |
| Inline 和多栏 | `css/css-inline`、`css/css-multicol` |
| 溢出、定位和尺寸 | `css/css-overflow`、`css/css-position`、`css/css-sizing` |
| 表格和文本 | `css/css-tables`、`css/css-text` |
| 变换、UI 和值 | `css/css-transforms`、`css/css-ui`、`css/css-values` |
| 书写模式 | `css/css-writing-modes` |

sparse checkout 还需要公共资源：

~~~text
common
css/support
fonts
resources
~~~

`layoutDirectories` 决定哪些目录中的 HTML 成为候选用例；公共资源不会独立成为用例，但本地 server 会从整个 corpus 根目录提供它们。

当前 `progress.md` 的一次完整 inventory 是 19,775 个候选页面。`wpt/README.md` 记录的 19,873 个 HTML/XHTML 是更宽口径的 corpus 文件统计，两者不应混用。实际数量以重新生成的 inventory 为准。

### 3.1 创建 corpus

在仓库根目录 PowerShell 中执行：

~~~powershell
git clone --filter=blob:none --no-checkout https://github.com/web-platform-tests/wpt.git wpt/corpus
git -C wpt/corpus sparse-checkout init --cone
git -C wpt/corpus sparse-checkout set common css/CSS2 css/css-align css/css-box css/css-break css/css-contain css/css-display css/css-flexbox css/css-grid css/css-inline css/css-multicol css/css-overflow css/css-position css/css-sizing css/css-tables css/css-text css/css-transforms css/css-ui css/css-values css/css-writing-modes css/support fonts resources
git -C wpt/corpus checkout a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
~~~

### 3.2 校验 corpus

~~~powershell
git -C wpt/corpus rev-parse HEAD
git -C wpt/corpus sparse-checkout list
git check-ignore -v wpt/corpus/.git/HEAD
~~~

应确认：

- HEAD 是固定 SHA。
- sparse list 包含全部配置目录和公共资源目录。
- corpus 被 `wpt/.gitignore` 忽略。

### 3.3 更新 revision

只有进行明确的 WPT 基线维护时才更新：

~~~powershell
git -C wpt/corpus fetch origin master
git -C wpt/corpus log --oneline HEAD..origin/master
git -C wpt/corpus merge --ff-only origin/master
git -C wpt/corpus rev-parse HEAD
~~~

之后同步修改 `wpt/config/runner.json` 的 `wptRevision`，重新运行 inventory，并审查用例数、分类和结果变化。当前 runner 不会验证 Git HEAD 是否等于配置里的 revision，只会把配置值写入输出元数据。

不要在日常 CI 中使用不固定的 `git pull`；上游变化会破坏结果可重复性。

## 4. 环境要求

| 工具 | 用途 |
| --- | --- |
| Node.js | 运行 `wpt/tools/*.mjs` |
| Git | 创建和维护 WPT sparse checkout |
| Chrome、Chromium 或 Edge | 浏览器快照采集 |
| Java 17、Gradle wrapper | 启动 AUI WPT 客户端 |
| Forge/Minecraft 运行依赖 | 运行 AUI Document 和布局代码 |

Windows 使用仓库的 `gradlew.bat`。浏览器可以通过 `CHROME_PATH` 指定：

~~~powershell
$env:CHROME_PATH = 'C:Program FilesGoogleChromeApplicationchrome.exe'
node wpt/tools/run.mjs --mode incremental --limit 10
~~~

浏览器查找顺序是：

1. `CHROME_PATH`。
2. `C:Program FilesGoogleChromeApplicationchrome.exe`。
3. `C:Program Files (x86)GoogleChromeApplicationchrome.exe`。
4. `C:Program FilesMicrosoftEdgeApplicationmsedge.exe`。
5. `C:Program Files (x86)MicrosoftEdgeApplicationmsedge.exe`。

找不到时会报 `No Chromium executable found. Set CHROME_PATH.`。

根目录 `build.gradle` 定义了：

~~~groovy
wptClient {
    client()
    gameDirectory = file('wpt/runtime')
}
~~~

所以 AUI 端对应的 Gradle 命令是：

~~~powershell
.gradlew.bat runWptClient --console plain --no-daemon
~~~

首次执行可能需要下载 Gradle、Minecraft、Forge 和模组依赖。

## 5. runner.json 配置

当前配置的主要结构：

~~~json
{
  "corpus": "../corpus",
  "output": "../output",
  "progress": "../progress.md",
  "wptRevision": "a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3",
  "layoutDirectories": ["css/CSS2", "css/css-align"],
  "viewport": {
    "width": 800,
    "height": 600,
    "dpr": 1
  },
  "execution": {
    "browserWorkers": 16,
    "auiWorkers": "auto",
    "cacheByContentHash": true,
    "browserAdapter": null,
    "auiAdapter": null
  }
}
~~~

完整目录列表以仓库中的 `wpt/config/runner.json` 为准。

### 5.1 路径解析

`corpus`、`output`、`progress` 相对于配置文件所在目录解析，不是相对于当前 shell 目录解析。默认配置在 `wpt/config/` 中，因此 `../corpus` 指向 `wpt/corpus`。

自定义配置示例：

~~~powershell
node wpt/tools/run.mjs --config wpt/config/runner.local.json --mode inventory
~~~

### 5.2 viewport

| 字段 | 当前作用 |
| --- | --- |
| `width` | 浏览器窗口和 AUI viewport override 宽度 |
| `height` | 浏览器窗口和 AUI viewport override 高度 |
| `dpr` | 当前只保存在配置中；浏览器强制 scale factor 为 1，AUI 端只接收宽高 |

当前不能通过 `dpr` 真正改变 AUI 设备像素比，浏览器探针通常记录 DPR 1。

### 5.3 execution

| 字段 | 当前实现 |
| --- | --- |
| `browserWorkers` | 传给浏览器适配器，外层 Chrome batch 并发最多限制为 4 |
| `auiWorkers` | 未被当前 runner 使用，AUI 批次串行运行 |
| `cacheByContentHash` | 当前代码始终按 sourceHash 保留结果，该字段不是开关 |
| `browserAdapter` | 只写入 run summary，不会动态加载模块 |
| `auiAdapter` | 只写入 run summary，不会动态加载模块 |

## 6. 命令和运行模式

从仓库根目录运行：

~~~powershell
node wpt/tools/run.mjs --help
node wpt/tools/run.mjs --mode inventory
node wpt/tools/run.mjs --mode incremental
node wpt/tools/run.mjs --mode full
~~~

参数也支持空格分隔：

~~~powershell
node wpt/tools/run.mjs --mode incremental --limit 20 --config wpt/config/runner.json
~~~

支持的参数：

| 参数 | 说明 |
| --- | --- |
| `--mode inventory|incremental|full` | 运行模式，默认 `inventory` |
| `--limit N` | 正整数，按排序后的候选集合取前 N 个 |
| `--config PATH` | 自定义 JSON 配置 |
| `--help`、`-h` | 打印用法 |

### 6.1 inventory

默认模式只做扫描和结果整理：

1. 读取配置。
2. 递归扫描所有 layout directory。
3. 收集 HTML/XHTML。
4. 计算 sourceHash 和分类。
5. 从旧结果中保留仍然有效的记录。
6. 写 inventory、results、summary 和 progress。

它不会启动 Chrome 或 Minecraft。inventory 模式中的 `--limit` 只影响 summary 的 `selectedCases`，不会减少扫描数量。

### 6.2 incremental

选择以下页面：

- 没有旧结果。
- sourceHash 变化。
- 旧最终状态不是 `pass`。

浏览器已有 `pass` 时会复用；AUI 已有 `pass` 时也会复用。浏览器成功后才进入 AUI 阶段。

这是日常开发的推荐模式。

### 6.3 full

full 把全部 inventory 作为请求集合，但仍会复用已有浏览器/AUI `pass`。所以它表示“检查全部页面”，不保证每个页面每个阶段都无条件重新采集。

需要完全重采集时，确认没有运行中的任务后清理 ignored 的 `wpt/output/`，再执行 full。这样会丢失本地快照和历史结果，不应作为日常操作。

### 6.4 limit

runner 先按路径排序：

- inventory：扫描全部，只记录前 N 个 selected。
- incremental：从 changed 集合取前 N 个。
- full：从完整 inventory 取前 N 个。

`--limit` 不是随机采样，也不能指定任意路径。当前没有 `--case` 参数；需要缩小范围时使用自定义 `layoutDirectories`，或后续为 runner 增加显式路径过滤。

## 7. 执行流程

~~~text
读取参数和配置
        |
扫描 layoutDirectories，建立 inventory
        |
读取旧 results.json，并按 sourceHash 保留结果
        |
inventory ------------------------------> 写出结果并结束
        |
incremental/full
        |
筛选 requested cases
        |
浏览器：本地 HTTP + Chrome + 探针
        |
浏览器 pass 的 cases
        |
AUI：Gradle runWptClient + Minecraft
        |
比较浏览器和 AUI snapshot
        |
checkpoint，最后写 inventory/results/summary/progress
~~~

runner 的 inventory 扫描使用 32 个并发 worker。结果文件使用临时文件和 rename 原子替换；执行中浏览器阶段结束后和每个 AUI 批次结束后都会写 checkpoint。

如果进程中途终止，`results.json` 可能保留部分最新结果，但 `run-summary.json` 可能仍是上次正常结束的数据。

### 7.1 文件和哈希

只扫描普通文件，扩展名为：

~~~text
.html
.htm
.xhtml
~~~

结果 ID 是相对于 corpus 根目录的正斜杠路径，例如：

~~~text
css/css-grid/grid-model/grid-inline-001.html
~~~

源码以 UTF-8 读取，sourceHash 为源码 SHA-256，不是 Git commit hash。

### 7.2 结果复用

旧记录只有在 sourceHash 相同且 status 属于支持集合时才会保留。支持的 status：

~~~text
pass
layout-mismatch
aui-runtime-unsupported
browser-test-failed
infra-blocked
timeout
pending
~~~

如果旧浏览器结果是 infra-blocked，而新分类已不再需要 server 或外部网络，旧 browser/aui 字段会删除，状态改为 pending，reason 为 `dependency-classification-changed`。

## 8. 页面分类

分类是源码正则启发式，不是完整 HTML/CSS 解析。inventory 项结构：

~~~json
{
  "id": "css/css-flexbox/example.html",
  "sourceHash": "...",
  "type": "layout-page",
  "requiresServer": false,
  "hasExternalDependency": false,
  "features": ["flex"]
}
~~~

### 8.1 type

优先级：

1. `manual`。
2. `reftest`。
3. `testharness`。
4. `layout-page`。

规则：

- 路径包含 manual，或 meta flags content 包含 manual，标记 manual。
- link rel 包含 match/mismatch，标记 reftest。
- 源码包含 testharness.js 或 testharnessreport.js，标记 testharness。
- 其他为 layout-page。

type 只是 inventory 元数据，不会触发 testharness 或 reftest 执行。

### 8.2 requiresServer

源码命中以下模式之一就标记服务端依赖：

~~~text
.sub.
{{
?pipe=
wptserve
testdriver.js
web-platform.test
~~~

浏览器阶段会直接返回：

~~~text
status = infra-blocked
reason = requires-server-or-external-network
~~~

### 8.3 hasExternalDependency

以下资源引用会标记外部依赖：

- script、img、iframe、source、video、audio 的 HTTP(S) src。
- stylesheet link 的 HTTP(S) href。

这些页面同样不会进入 Chromium 批处理。

### 8.4 features

当前识别的标签：

| 标签 | 典型匹配 |
| --- | --- |
| flex | display:flex、display:inline-flex、flex-* |
| grid | display:grid、display:inline-grid、grid-* |
| position | position |
| overflow | overflow、overflow-x、overflow-y |
| sizing | box-sizing、aspect-ratio、min/max width/height |
| transform | transform、transform-origin |
| writing-modes | writing-mode、direction |
| multicol | columns、column-count/width/gap/rule |
| table | display:table |
| contain | contain、contain-intrinsic-* |

features 只用于诊断，不是执行过滤条件；没有依赖阻断的页面仍会继续运行。

## 9. Chromium 适配器

### 9.1 本地 server

适配器在 127.0.0.1 的随机端口启动 Node HTTP server：

- `/__aui_wpt_batch?id=...` 返回批处理页面。
- 其他路径映射到 corpus 相对路径。
- 使用 path.resolve 后检查路径仍在 corpus 根目录，阻止路径逃逸。
- 支持 HTML、CSS、JS、JSON、SVG、PNG、WOFF、WOFF2 等 content type。
- 返回 no-store cache header。
- 读取失败返回 404。

它不使用 file URL，也不模拟 WPT server 模板或外部服务。

### 9.2 探针

批处理页将 WPT 页面加载到隐藏 iframe，并添加 `__aui_wpt_probe=1`。server 只对该参数下的 HTML/XHTML 注入探针。

探针放在 body 闭合标签前，页面 load 后等待 50 ms，遍历 `document.querySelectorAll('*')`，采集：

- index、tag、id、className。
- rect：x、y、width、height，四舍五入 4 位。
- scroll：scrollWidth、scrollHeight、clientWidth、clientHeight。
- computed：display、position、boxSizing、overflowX、overflowY。

快照示例：

~~~json
{
  "viewport": [776, 501, 1],
  "nodes": [
    {
      "index": 0,
      "tag": "html",
      "id": null,
      "className": "",
      "rect": [0, 0, 776, 263],
      "scroll": [776, 501, 776, 501],
      "computed": {
        "display": "block",
        "position": "static",
        "boxSizing": "content-box",
        "overflowX": "visible",
        "overflowY": "visible"
      }
    }
  ]
}
~~~

### 9.3 批处理和超时

- 每个 Chrome batch 最多 50 个页面。
- 每个 batch 内最多 8 个 iframe worker。
- 外层并发为 `min(4, workerCount)`。
- `browserWorkers` 为 auto/null 时使用 availableParallelism，最终限制 1 到 4。
- 显式 browserWorkers 至少为 1。

默认 browserWorkers=16 的实际含义是最多 4 个 Chrome batch 进程，不是 16 个 Chrome 进程。

关键 Chrome 参数：

~~~text
--headless=new
--disable-gpu
--disable-background-networking
--disable-default-apps
--force-device-scale-factor=1
--window-size=width,height
--virtual-time-budget=30000
--dump-dom
~~~

每个 Chrome 进程超时 45 秒；单 iframe 超时 4 秒；iframe load 后再等待 100 ms，探针自身在 load 后等待 50 ms。

### 9.4 快照去重

浏览器 snapshot 的 JSON 字符串取 SHA-256，写入：

~~~text
wpt/output/browser/<sha256>.json
~~~

相同内容复用同一文件。results 中的 browser.snapshot 只保存文件名。

## 10. AUI 适配器

### 10.1 Node 批次

AUI adapter 创建临时 TSV：

~~~text
<case id>	<absolute source path>
~~~

然后执行：

~~~text
gradlew.bat runWptClient --console plain --no-daemon
~~~

环境变量：

| 环境变量 | 作用 | 当前值 |
| --- | --- | --- |
| AUI_WPT_CLIENT_INPUT | 输入 TSV | 临时路径 |
| AUI_WPT_CLIENT_OUTPUT | 输出 TSV | 临时路径 |
| AUI_WPT_CLIENT_EXIT_ON_FINISH | 完成后停止客户端 | true |
| AUI_WPT_CLIENT_TIMEOUT_SECONDS | 批次总超时 | 900 |
| AUI_WPT_CLIENT_STALL_TIMEOUT_SECONDS | 单用例卡住超时 | 15 |
| AUI_WPT_VIEWPORT_WIDTH | AUI viewport 宽 | 配置值 |
| AUI_WPT_VIEWPORT_HEIGHT | AUI viewport 高 | 配置值 |

每批最多 5000 个页面，批次串行运行，auiWorkers 当前没有作用。成功后读取输出 TSV，并删除 input/result 文件。

### 10.2 Java 客户端 runner

主流程使用：

~~~text
src/main/java/com/sighs/apricityui/instance/ClientWptSnapshotRunner.java
~~~

它是 Forge client tick 事件订阅器，不是 JUnit 测试。

行为：

1. input 和 output 环境变量同时存在时才启用。
2. 客户端启动后等待约 10 tick。
3. 设置 Size viewport override。
4. 每个 END tick 最多处理 10 个 case。
5. 用 HTML.putTemple 注册页面源码。
6. 创建 Document 并 refresh。
7. 查询所有元素，记录 tag、id 和 getBoundingClientRect。
8. 每条结果立即 flush 到 TSV。
9. 完成时清除 viewport override，并按环境变量停止 Minecraft。

AUI snapshot 当前结构：

~~~json
{
  "nodes": [
    {
      "tag": "div",
      "id": "panel",
      "rect": [10, 20, 120, 40]
    }
  ]
}
~~~

主客户端 runner 不写 browser snapshot 的 viewport、scroll、computed 字段；当前比较器也不使用这些字段。

### 10.3 watchdog 和重试

默认 watchdog：

- 批次总超时 900 秒。
- 单个 case 连续 15 秒没有完成则 stalled。

watchdog 会记录 `[AUI WPT] client watchdog stopped ...`，并以退出码 124 终止客户端。

Node 端如果进程失败且存在未回传 case，会把一个缺失 case 标记 timeout，并把其他缺失 case 放回队列重试。如果进程没有报错但仍有缺失，则这些 case 标记 aui-runtime-unsupported。

### 10.4 JUnit 辅助桥

`src/test/java/com/sighs/apricityui/wptlayout/AuiWptSnapshotTest.java` 是另一条独立 JSON 测试桥，使用系统属性 `aui.wpt.input` 和 `aui.wpt.output`。`run.mjs` 当前不调用它；主流程使用 Forge run 和 ClientWptSnapshotRunner。修改一条桥时不要假设另一条会同步改变。

## 11. 快照比较

比较器位于 `wpt/tools/run.mjs` 的 compareSnapshots。

### 11.1 节点过滤和身份

浏览器节点会过滤 tag 为 style、script、link 的节点；AUI 节点不做同样过滤。

过滤后节点数量必须相等。然后按数组顺序检查：

~~~text
tag 相同
id 相同；null 和空字符串按空字符串处理
~~~

不使用 selector、className 或 browser index 进行匹配。DOM 顺序差异会导致 node-count 或 identity mismatch。

### 11.2 矩形阈值

每个节点比较：

~~~text
x、y、width、height
~~~

绝对差不超过 0.25 才算相同。阈值硬编码在 run.mjs，不在 runner.json 中，也没有每页面 tolerance。

典型 reason：

~~~text
node-count browser=7 aui=14
node-0 identity differs
node-3 rect[2] browser=120 aui=119.75
~~~

当前不比较 viewport、滚动尺寸、computed style、颜色、字体像素和 PNG。

## 12. 输出格式

### 12.1 inventory.json

~~~json
{
  "schemaVersion": 1,
  "wptRevision": "a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3",
  "generatedAt": "2026-07-29T14:25:22.788Z",
  "cases": [
    {
      "id": "css/css-grid/example.html",
      "sourceHash": "...",
      "type": "layout-page",
      "requiresServer": false,
      "hasExternalDependency": false,
      "features": ["grid"]
    }
  ]
}
~~~

### 12.2 results.json

单个 case 结构：

~~~json
{
  "id": "css/css-grid/example.html",
  "sourceHash": "...",
  "status": "layout-mismatch",
  "reason": "node-3 rect[2] browser=120 aui=119.75",
  "updatedAt": "2026-07-29T14:25:22.788Z",
  "browser": {
    "id": "css/css-grid/example.html",
    "status": "pass",
    "reason": null,
    "snapshot": "<sha256>.json",
    "nodes": 12
  },
  "aui": {
    "id": "css/css-grid/example.html",
    "status": "pass",
    "snapshot": {
      "nodes": []
    }
  }
}
~~~

字段：

| 字段 | 含义 |
| --- | --- |
| id | 相对 corpus 根的 WPT 路径 |
| sourceHash | 源码 SHA-256 |
| status | 最终状态 |
| reason | 失败原因或首个比较差异 |
| updatedAt | 最近一次阶段结果时间 |
| browser | 浏览器阶段结果，可能不存在 |
| aui | AUI 阶段结果，可能不存在 |
| browser.snapshot | output/browser 中的快照文件名 |
| browser.nodes | 浏览器原始节点数量 |
| aui.snapshot | AUI 成功时内嵌的快照 |

### 12.3 run-summary.json

典型字段：

~~~json
{
  "mode": "incremental",
  "completedAt": "2026-07-29T14:25:22.788Z",
  "selectedCases": 19775,
  "totalCases": 19775,
  "adapters": {
    "browser": null,
    "aui": null
  },
  "browserExecuted": 16268,
  "browserPassed": 15552,
  "auiExecuted": 18880
}
~~~

当前 selectedCases 是 inventory 前 limit 个的数量，不是增量筛选后的真实请求数。判断真实执行规模应结合 browserExecuted、auiExecuted 和 results.json。

### 12.4 progress.md

包含 WPT revision、完成时间、运行模式、总数、各最终状态数量，以及按前两级路径统计的模块表。

模块表中的 Not passed 是除 pass 和 layout-mismatch 之外的合计，不是独立状态。

## 13. 状态含义

| 状态 | 含义 |
| --- | --- |
| pass | 浏览器和 AUI 都成功，节点和矩形比较通过 |
| layout-mismatch | 两端都有快照，但节点数量、tag/id 或 rect 超出阈值 |
| aui-runtime-unsupported | AUI 抛异常、未完成解析或没有回传结果 |
| browser-test-failed | Chromium batch 或 iframe 加载/探针失败 |
| infra-blocked | 检测到 server 或外部网络依赖，页面主动跳过 |
| timeout | Chrome、AUI 客户端或 watchdog 超时 |
| pending | 尚未执行、等待 AUI，或需要重新调度 |

常见 reason：

~~~text
not-run
awaiting-aui-snapshot
dependency-classification-changed
requires-server-or-external-network
layout probe did not produce a snapshot
iframe timeout
client did not report this case
~~~

`pass` 不是 WPT testharness pass；`infra-blocked` 也不是布局失败。

## 14. 推荐工作流

### 14.1 首次运行

~~~powershell
git -C wpt/corpus rev-parse HEAD
node wpt/tools/run.mjs --mode inventory
Get-Content -Encoding UTF8 wpt/progress.md
~~~

### 14.2 修改布局后的快速验证

~~~powershell
node wpt/tools/run.mjs --mode incremental --limit 1
node wpt/tools/run.mjs --mode incremental --limit 20
node wpt/tools/run.mjs --mode incremental --limit 200
~~~

`--limit 1` 是排序后集合中的第一项，不是指定某个 WPT 文件。

### 14.3 检查 mismatch

1. 从 progress 或 results 找到 case ID。
2. 查看 reason。
3. 读取 browser.snapshot 对应的 `wpt/output/browser/<name>.json`。
4. 对照 results 中的 aui.snapshot.nodes。
5. 按节点顺序、tag/id 和 rect 找第一处差异。
6. 如果是 node count 或 identity，先检查 HTML 解析、head 节点、隐藏节点和默认样式，而不是直接修改布局算法。

PowerShell 示例：

~~~powershell
$results = Get-Content -Raw -Encoding UTF8 wpt/output/results.json | ConvertFrom-Json
$case = $results.cases | Where-Object { $_.id -eq 'css/css-grid/example.html' }
$case | ConvertTo-Json -Depth 12

if ($case.browser.snapshot) {
    Get-Content -Raw -Encoding UTF8 (Join-Path wpt/output/browser $case.browser.snapshot) |
        ConvertFrom-Json | ConvertTo-Json -Depth 8
}
~~~

### 14.4 查看 AUI 日志

~~~powershell
Get-Content -Tail 200 -Encoding UTF8 wpt/runtime/logs/latest.log
Get-Content -Tail 200 -Encoding UTF8 wpt/runtime/logs/debug.log
~~~

可搜索：

~~~text
[AUI WPT]
Exception
Error
watchdog
~~~

## 15. 新增和接入页面

当前 runner 没有 reviewed manifest，也没有 `supported`、`blocked` 等接入状态文件。配置目录中的页面都会自动进入 inventory。

建议流程：

1. 确认页面来自固定 WPT revision，保留原始相对路径。
2. 运行 inventory，检查 type、依赖标志和 features。
3. 确认 Chromium 能够生成快照。
4. 用小 limit 运行 AUI。
5. 对 mismatch 保存两端快照并确认差异来源。
6. 修改 AUI 布局后使用 incremental 重跑。
7. 对 server、网络或 AUI 未实现 API 明确保留 infra-blocked 或 aui-runtime-unsupported，不要删除页面掩盖问题。

`wpt/README.md` 中的 WptLayoutCase、manifest 和 10-20 个 geometry fixture 是后续规划，不是当前 runner 的输入格式。

## 16. 常见问题

### 16.1 corpus 或 layout directory 不存在

检查配置解析路径和 sparse checkout：

~~~powershell
Resolve-Path wpt/corpus
Get-ChildItem wpt/corpus/css
git -C wpt/corpus sparse-checkout list
~~~

缺少配置目录时，runner 会报 Configured WPT layout directory is missing。

### 16.2 runner.json 解析失败

JSON 不能包含注释和多余逗号：

~~~powershell
Get-Content -Raw -Encoding UTF8 wpt/config/runner.json | ConvertFrom-Json
~~~

### 16.3 找不到浏览器

设置 `CHROME_PATH`，确认它指向实际可执行文件。浏览器路径不在 runner.json 中配置。

### 16.4 大量 infra-blocked

这是依赖分类结果，不是 Chrome 崩溃。检查 inventory 的 `requiresServer` 和 `hasExternalDependency`。当前实现不会自行启动 WPT server 或访问外部依赖。

### 16.5 browser-test-failed 或 timeout

按以下顺序缩小问题：

1. 用 `--limit 1` 或 `--limit 10` 重跑。
2. 检查页面是否无限加载、执行未结束脚本或等待字体。
3. 检查 Chrome 路径、权限和机器负载。
4. 确认没有多个 full runner 同时运行。
5. 进程全部停止后检查或清理 `wpt/output/aui/client-*.tsv`。

### 16.6 AUI 没有写结果

检查 Gradle 和日志：

~~~powershell
.gradlew.bat runWptClient --console plain --no-daemon
Get-Content -Tail 300 -Encoding UTF8 wpt/runtime/logs/latest.log
~~~

手工不设置 AUI_WPT input/output 环境变量时，客户端不会进入 WPT runner，这是预期行为；正常应由 aui-adapter 设置。

### 16.7 大量 aui-runtime-unsupported

结合 `results.json` 的 `aui.reason` 和 runtime 日志判断：

- AUI 尚未实现页面使用的 HTML/CSS。
- Document.refresh 抛出异常。
- 客户端批次中途终止。
- case 没有上报。
- watchdog 触发。

`client did not report this case` 与页面自身抛出的异常不是同一类问题。

### 16.8 mismatch 但视觉上看起来相同

当前比较按 DOM 顺序，AUI 端还没有过滤 style/script/link。HTML 解析恢复、head 节点、隐藏节点、默认样式、节点顺序或 0.25 阈值都可能造成 mismatch。先根据 node-count、identity differs 或 rect[...] 区分原因。

## 17. 性能、稳定性和安全

- 日常使用 incremental，首次准备使用 inventory。
- 调试时从 limit 1、20 逐步扩大。
- 浏览器外层最多 4 个 batch 进程，每 batch 内最多 8 个 iframe。
- AUI 每批最多 5000 个页面，并串行运行。
- 不要同时运行多个 full WPT 任务。
- 固定 WPT revision、viewport 和浏览器版本。
- output 是可重建缓存，progress 是视图，results 和 snapshot 才是诊断数据。
- 浏览器 server 只监听 127.0.0.1，并校验请求路径不能逃出 corpus。
- corpus 来自外部测试数据，执行前确认 revision 来源可信。
- runtime 可能包含日志、配置和世界数据，不要提交或分享。
- 清理 output 前确认没有正在运行的 Node、Gradle 或 Minecraft WPT 进程。

## 18. 参考文件

- [wpt/README.md](../wpt/README.md)：总体设计和未来规划。
- [wpt/config/runner.json](../wpt/config/runner.json)：默认配置。
- [wpt/tools/run.mjs](../wpt/tools/run.mjs)：主 runner 和比较器。
- [wpt/tools/browser-adapter.mjs](../wpt/tools/browser-adapter.mjs)：浏览器适配器。
- [wpt/tools/aui-adapter.mjs](../wpt/tools/aui-adapter.mjs)：AUI 批处理适配器。
- [src/main/java/com/sighs/apricityui/instance/ClientWptSnapshotRunner.java](../src/main/java/com/sighs/apricityui/instance/ClientWptSnapshotRunner.java)：Forge 客户端 runner。
- [src/test/java/com/sighs/apricityui/wptlayout/AuiWptSnapshotTest.java](../src/test/java/com/sighs/apricityui/wptlayout/AuiWptSnapshotTest.java)：独立 JUnit 桥。
- [build.gradle](../build.gradle)：wptClient Forge run 定义。

当本文档与代码行为不一致时，以代码和当前配置为准，并在修改行为的同一个变更中更新本文档。

