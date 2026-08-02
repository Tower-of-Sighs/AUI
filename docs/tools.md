# 附加工具使用说明

tools 目录存放 ApricityUI 的开发、外部调试和截图回归工具。这些程序不会随模组运行时自动执行，需要根据任务手动调用。

当前工具集：

~~~text
tools/
├── apricity-debug-client.mjs       Apricity 外部调试协议的 Node.js 客户端
├── apricity-mcp/                   MCP stdio 服务桥接
├── aui_compare.py                  PNG 视觉差异比较器
└── aui_preview_loop.ps1            启动客户端并循环比较截图
~~~

tools/apricity-mcp/node_modules 是 npm install 生成的依赖目录，不是项目源码，也不需要手工编辑。

## 1. 环境要求

| 工具 | 需要的环境 |
| --- | --- |
| apricity-debug-client.mjs | Node.js 22，或提供全局 WebSocket 的 Node.js 环境 |
| apricity-mcp | Node.js 20 以上；首次使用需要执行 npm install |
| aui_compare.py | Python 3 和 Pillow |
| aui_preview_loop.ps1 | Windows PowerShell、Java/Gradle 和可启动的 Minecraft 客户端 |

建议从仓库根目录 D:/work/AUI 执行命令。aui_preview_loop.ps1 会调用 ./gradlew.bat，不能从 tools 子目录直接启动。

安装 Python 依赖：

~~~powershell
python -m pip install Pillow
~~~

安装 MCP 依赖：

~~~powershell
cd tools/apricity-mcp
npm install
cd ../..
~~~

## 2. Apricity 外部调试链路

### 2.1 启动调试服务

Apricity 外部调试服务只监听本机回环地址：

~~~text
ws://127.0.0.1:25321/apricity
~~~

开发环境通常默认开启，也可以通过 JVM 参数显式控制：

~~~text
-Dapricityui.debug.enabled=true
-Dapricityui.debug.token=my-local-token
~~~

关闭服务：

~~~text
-Dapricityui.debug.enabled=false
~~~

游戏启动后，服务会在游戏目录下写入：

~~~text
run/apricity/debug.json
~~~

文件示例：

~~~json
{
  "protocolVersion": 1,
  "endpoint": "ws://127.0.0.1:25321/apricity",
  "token": "...",
  "pid": 12345
}
~~~

没有通过 -Dapricityui.debug.token 固定 token 时，每次启动会生成新的随机 token。发现文件包含访问凭据，不要提交到版本库或分享给不需要访问当前客户端的人。

调试请求会排入客户端主线程，并在游戏 tick 中处理。外部客户端不应该假设操作会在 WebSocket 线程立即完成，也不应该从外部线程直接访问 ApricityUI DOM 对象。

### 2.2 AUI 配置开关

MCP 本身没有单独的启用开关。它依赖 AUI 客户端先启动外部调试服务，因此需要开启客户端配置中的 debug.remoteDebug：

~~~toml
[debug]
remoteDebug = true
~~~

配置文件位于 Minecraft 实例的 config/apricityui-client.toml。在本项目的开发运行环境中通常是：

~~~text
run/config/apricityui-client.toml
~~~

开发环境默认开启，生产环境默认关闭。如果配置文件中没有该项，启动一次客户端后 Forge 会生成它。也可以用 JVM 参数覆盖配置值：

~~~text
-Dapricityui.debug.enabled=true
~~~

当 remoteDebug 未开启时，MCP 会因为找不到 run/apricity/debug.json 或无法连接 127.0.0.1:25321 而失败。确认 AUI 配置后重新启动客户端，或通过 DevTools 设置窗口切换远程调试开关；服务启动后应生成：

~~~text
run/apricity/debug.json
~~~

### 2.3 底层 Apricity Debug Protocol

`apricity-debug-client.mjs` 和 `apricity-mcp` 都只是这个协议的客户端封装。需要自己实现 IDE 插件、测试适配器或其他语言客户端时，可以直接连接 WebSocket 并发送 JSON-RPC 2.0 请求。

连接地址和认证方式：

~~~text
ws://127.0.0.1:25321/apricity?token=<debug-token>
~~~

Token 也可以放在 `Authorization: Bearer <debug-token>` 请求头中。服务只绑定 `127.0.0.1`，但 token 仍然是当前客户端 UI 的操作凭据，不要转发给不可信进程。WebSocket 消息是 UTF-8 JSON 文本；客户端帧必须使用标准 WebSocket mask。

最小请求和响应形状如下：

~~~json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "System.info",
  "params": {}
}
~~~

~~~json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "name": "Apricity Debug Protocol",
    "protocolVersion": 1,
    "endpoint": "ws://127.0.0.1:25321/apricity",
    "capabilities": ["target", "dom", "input"]
  }
}
~~~

请求的 `id` 可以是字符串、数字或 `null`。没有 `id` 的请求是 notification，服务端执行后不返回结果。协议版本目前为 `1`，方法名区分大小写。

#### Target 和 session

每个活动 Document 是一个 target。先调用 `Target.list`，再使用返回的完整 Document UUID 调用 `Target.attach`：

~~~json
{
  "jsonrpc": "2.0",
  "id": "attach-1",
  "method": "Target.attach",
  "params": {
    "targetId": "<document-uuid>"
  }
}
~~~

成功结果包含 `sessionId`、`targetId` 和 `path`。所有 `DOM.*` 方法都必须携带这个 `sessionId`；`Target.detach` 也使用 `sessionId`。同一路径可以同时存在多个 Document，不能使用路径代替 UUID。

`Target.list` 返回的 target 字段如下：

| 字段 | 含义 |
| --- | --- |
| `targetId` | Document UUID |
| `path` | 逻辑 HTML 路径 |
| `active` | Document 是否仍然活动 |
| `inWorld` | 是否是世界内 Document |
| `refreshGeneration` | 当前刷新代次 |

Document 刷新会重建 DOM。刷新后旧 `nodeId` 可能已经断开，即使 `sessionId` 仍存在，也应重新 `DOM.query` 或 `Target.list` 后获取新节点。

#### 方法参考

系统和目标方法：

| 方法 | 必要参数 | 结果 |
| --- | --- | --- |
| `System.info` | 无 | 协议版本、端点和能力列表 |
| `Target.list` | 无 | `{ targets: [...] }` |
| `Target.attach` | `targetId` | `{ sessionId, targetId, path }` |
| `Target.detach` | `sessionId` | `{ detached: boolean }` |

DOM 查询和检查方法都把 `sessionId` 放在参数对象中：

| 方法 | 其他参数 | 结果 |
| --- | --- | --- |
| `DOM.query` | `selector` | 第一个匹配节点的 `nodeId`，没有匹配时为 `null` |
| `DOM.queryAll` | `selector` | `{ nodeIds: [...] }` |
| `DOM.snapshot` | `maxDepth`、`maxNodes` | 从 `documentElement` 或 `body` 开始的树快照 |
| `DOM.getAttributes` | `nodeId` | 按名称排序的 `{ attributes: {...} }` |
| `DOM.getText` | `nodeId` | `{ text: "..." }` |
| `DOM.getComputedStyle` | `nodeId` | `{ cssText: "..." }` |
| `DOM.getBoxModel` | `nodeId` | `margin`、`border`、`padding`、`content` 四个屏幕坐标矩形 |

`DOM.snapshot` 的 `maxDepth` 默认是 `32`，允许 `0..128`；`maxNodes` 默认是 `5000`，允许 `1..20000`。参数超出范围会返回 `INVALID_PARAMS` 或 `LIMIT_EXCEEDED`，调试器不应无限制请求整个页面。

输入方法：

| 方法 | 参数 | 行为 |
| --- | --- | --- |
| `DOM.hover` | `nodeId` | 把鼠标移动到元素 border box 中心并触发 `mousemove` |
| `DOM.click` | `nodeId` | 在中心点依次触发 `mousemove`、`mousedown`、`mouseup` |
| `DOM.fill` | `nodeId`、`value` | 聚焦可编辑 `input`/`textarea`，全选并替换值 |

`hover` 和 `click` 要求元素可见且有正的布局尺寸。`click` 还要求元素允许 pointer events，且中心点没有被其他元素覆盖；`fill` 不能操作禁用控件或非可编辑元素。返回的输入结果通常包含实际使用的屏幕坐标 `point`，`fill` 返回写入后的 `value`。

#### 原始调用示例

~~~json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "DOM.query",
  "params": {
    "sessionId": "<session-uuid>",
    "selector": "#save"
  }
}
~~~

拿到 `nodeId` 后查询样式并点击：

~~~json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "DOM.getComputedStyle",
  "params": {
    "sessionId": "<session-uuid>",
    "nodeId": "<node-uuid>"
  }
}
~~~

~~~json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "DOM.click",
  "params": {
    "sessionId": "<session-uuid>",
    "nodeId": "<node-uuid>"
  }
}
~~~

#### 错误码和执行模型

错误响应使用标准 JSON-RPC 形状：

~~~json
{
  "jsonrpc": "2.0",
  "id": 4,
  "error": {
    "code": -32003,
    "message": "Element is not visible"
  }
}
~~~

| 错误码 | 含义 |
| ---: | --- |
| `-32700` | JSON 解析失败 |
| `-32600` | JSON-RPC 请求格式无效 |
| `-32601` | 方法不存在 |
| `-32602` | 参数类型、必填字段或范围无效 |
| `-32603` | 服务端内部错误 |
| `-32000` | 调试服务已停止或命令队列已满 |
| `-32001` | target 已关闭，或 session 已 detach |
| `-32002` | node 已从当前 Document 脱离 |
| `-32003` | 元素不可操作，例如不可见、被覆盖或禁用 |
| `-32004` | 请求超过 DOM 深度/节点限制 |

请求先进入线程安全的命令队列，再由客户端 tick 在主线程执行。服务端每个 tick 最多处理 256 条命令，并有约 4 ms 的处理预算；连接线程不会直接访问 DOM。客户端应接受请求延迟、在超时后重新读取 target/node 状态，并避免批量发送无界查询。

当前协议明确不提供任意 JavaScript 执行、`evaluate`、文件读写、截图、网络代理或 Chrome DevTools Protocol 兼容层。需要执行页面脚本时，应修改测试页面或使用页面自身的日志 API；需要保存资源时，使用资源管理器或 DevTools 的可写入口。

传输层还有以下保护：单个 WebSocket 消息最大约 `1 MiB`，HTTP 握手头最大 `64 KiB`，同时最多保留 64 个连接。服务端命令队列上限为 4096 条；超过后会返回 `-32000`，不会无限积压到客户端 tick。

### 2.4 apricity-debug-client.mjs

这是一个不依赖 Playwright 的轻量 Node.js 客户端，封装 Apricity Debug Protocol 的 WebSocket 和 JSON-RPC 调用。

连接示例：

~~~js
import { connect } from "./tools/apricity-debug-client.mjs";

const client = await connect();
try {
  console.log(await client.info());
  console.log(await client.documents());
} finally {
  client.close();
}
~~~

默认读取 run/apricity/debug.json。也可以显式指定发现文件或 endpoint：

~~~js
const client = await connect({
  discoveryFile: "D:/work/AUI/run/apricity/debug.json",
  timeout: 10000,
});

// 自动化环境可以直接使用 endpoint 和 token。
// const direct = await connect({
//   endpoint: "ws://127.0.0.1:25321/apricity",
//   token: process.env.APRICITY_DEBUG_TOKEN,
// });
~~~

connect 选项：

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| discoveryFile | 工具文件上一级的 run/apricity/debug.json | 读取 endpoint 和 token 的 JSON 文件 |
| endpoint | 无 | 设置后直接连接该 WebSocket 地址 |
| token | 无 | 与显式 endpoint 一起使用 |
| timeout | 5000 | 建立连接的超时时间，单位为毫秒 |

提供 endpoint 时必须同时提供 token。客户端会把 token 放到 WebSocket URL 的 token 查询参数中。

ApricityDebugClient 方法：

| 方法 | 作用 | 返回值 |
| --- | --- | --- |
| info() | 获取协议版本、endpoint 和能力列表 | 服务端系统信息 |
| documents() | 列出当前存活的 Apricity Document | target 数组 |
| attach(targetId) | 绑定一个 document | Page 实例 |
| call(method, params) | 发送底层 JSON-RPC 请求 | Promise |
| close() | 关闭 WebSocket 连接 | 无 |

targetId 使用完整的 document UUID。相同资源路径可能同时存在多个 document，不能只使用路径作为 target 标识。

Page 方法：

| 方法 | 作用 |
| --- | --- |
| locator(selector) | 创建 CSS 选择器定位器 |
| snapshot(options) | 获取有深度和节点数上限的 DOM 快照 |
| call(method, params) | 自动加入当前 sessionId 后调用协议方法 |
| detach() | 结束当前 session |

snapshot 允许 maxDepth 0..128、maxNodes 1..20000，默认值分别为 32 和 5000：

~~~js
const snapshot = await page.snapshot({
  maxDepth: 12,
  maxNodes: 1000,
});
~~~

Locator 方法：

| 方法 | 作用 |
| --- | --- |
| count() | 返回匹配元素数量 |
| attributes() | 获取第一个匹配元素的属性对象 |
| text() | 获取第一个匹配元素的 textContent |
| computedStyle() | 获取第一个匹配元素的计算样式 |
| boxModel() | 获取第一个匹配元素的屏幕坐标盒模型 |
| hover() | 将 Apricity 鼠标移动到元素 border box 中心 |
| click() | 对元素发送移动、按下和抬起事件 |
| fill(value) | 聚焦 input 或 textarea 并替换值 |
| waitFor(options) | 等待元素达到指定状态 |

Locator 不缓存节点 UUID，每次操作前都会根据 selector 重新查询，因此 document 重载或 DOM 重建后可以继续复用。

waitFor 支持：

- attached：selector 能匹配到元素。
- detached：selector 不再匹配元素。
- visible：元素存在且盒模型宽高都大于 0。
- hidden：元素不存在，或盒模型宽高至少有一个为 0。

完整操作示例：

~~~js
const client = await connect();
try {
  const targets = await client.documents();
  if (targets.length === 0) throw new Error("No Apricity documents");

  const page = await client.attach(targets[0].targetId);
  try {
    const button = page.locator("#save");
    await button.waitFor({ state: "visible", timeout: 5000 });
    console.log(await button.attributes());
    await button.click();
  } finally {
    await page.detach();
  }
} finally {
  client.close();
}
~~~

该客户端只提供 DOM 查询、样式/盒模型检查和有限的鼠标/文本操作，不提供 evaluate、任意 JavaScript 执行、文件读写或 Chrome DevTools Protocol 兼容层。

### 2.5 apricity-mcp

tools/apricity-mcp 是上述客户端的 MCP stdio 桥接，适合让支持 MCP 的开发工具直接查看和操作当前运行中的 ApricityUI。

安装并运行测试：

~~~powershell
cd tools/apricity-mcp
npm install
npm test
~~~

作为 MCP 服务启动：

~~~powershell
node tools/apricity-mcp/server.mjs
~~~

MCP 客户端配置示例：

~~~json
{
  "mcpServers": {
    "apricity": {
      "command": "node",
      "args": ["D:/work/AUI/tools/apricity-mcp/server.mjs"],
      "env": {
        "APRICITY_DEBUG_DISCOVERY": "D:/work/AUI/run/apricity/debug.json"
      }
    }
  }
}
~~~

也可以完全绕过发现文件：

~~~powershell
$env:APRICITY_DEBUG_ENDPOINT = "ws://127.0.0.1:25321/apricity"
$env:APRICITY_DEBUG_TOKEN = "my-local-token"
node tools/apricity-mcp/server.mjs
~~~

当设置 APRICITY_DEBUG_ENDPOINT 时，APRICITY_DEBUG_TOKEN 是必需的。

MCP 工具：

| 工具 | 参数 | 功能 |
| --- | --- | --- |
| apricity_documents | 无 | 列出当前存活的 document 和 target UUID |
| apricity_snapshot | targetId、maxDepth、maxNodes | 获取有上限的 DOM 快照；默认 32/5000 |
| apricity_query | targetId、selector、maxResults | 批量查询元素属性、文本和盒模型；默认最多返回 20 个 |
| apricity_inspect | targetId、selector、includeComputedStyle | 检查第一个匹配元素，并返回匹配总数、属性、文本、盒模型和可选计算样式 |
| apricity_wait_for | targetId、selector、state、timeout | 等待 attached、detached、visible 或 hidden；默认 5 秒 |
| apricity_hover | targetId、selector | 将鼠标移动到第一个匹配元素中心 |
| apricity_click | targetId、selector | 对第一个匹配元素执行点击事件序列 |
| apricity_fill | targetId、selector、value | 替换第一个 input 或 textarea 的值 |

参数约束：

| 参数 | 约束 |
| --- | --- |
| maxDepth | 整数，0..128，默认 32 |
| maxNodes | 整数，1..20000，默认 5000 |
| maxResults | 整数，1..100，默认 20 |
| timeout | 整数，0..60000 毫秒，默认 5000 |
| includeComputedStyle | 布尔值，默认 false |

文本摘要默认截断到 2000 个字符。apricity_query 仍会返回完整匹配数量和 truncated 标志。连接失败、目标不存在、元素不存在或操作不可执行时，服务返回 isError: true，不会让 MCP 进程直接崩溃。

## 3. 截图和视觉比较工具

### 3.1 aui_compare.py

这是通用的 PNG 视觉比较器：

~~~powershell
python tools/aui_compare.py <reference.png> <actual.png>
~~~

处理流程：

1. 读取 reference 和 actual 图片。
2. 将 actual 居中裁剪到与 reference 相同的宽高比。
3. 使用 Lanczos 算法把 actual 缩放到 reference 的像素尺寸。
4. 在 RGB 通道上计算均方根误差 rms。
5. 计算平均亮度 avg_luma 和暗像素比例 dark_ratio。

默认判定为相似的条件：

~~~text
rms < 70
dark_ratio < 0.55
avg_luma > 40
~~~

标准输出：

~~~json
{
  "ok": true,
  "rms": 0.0,
  "avg_luma": 128.4,
  "dark_ratio": 0.1234,
  "reference": "reference.png",
  "actual": "actual.png"
}
~~~

退出码：

| 退出码 | 含义 |
| --- | --- |
| 0 | 图片达到相似阈值 |
| 1 | 文件不存在，或图片不满足相似阈值 |
| 2 | 参数数量不是两个 |

该工具适合快速筛除截图为空、尺寸比例错误、页面整体偏黑或整体差异很大的结果。它不是逐像素完全相等检查；需要严格基线一致时，应额外检查输出里的 rms == 0。

### 3.2 aui_preview_loop.ps1

该脚本反复启动客户端、取得最新 AUI 截图并与参考图比较，适合调试启动时序或资源加载不稳定的问题。

~~~powershell
pwsh -File tools/aui_preview_loop.ps1
~~~

默认参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| MaxAttempts | 5 | 最多启动客户端的次数 |
| AutoExitSeconds | 5 | 传给客户端的自动退出时间 |
| ViewportWidth | 427 | 测试视口宽度 |
| ViewportHeight | 249 | 测试视口高度 |

依赖路径：

~~~text
run/apricity/apricityui/example.png   参考图
run/screenshots/aui/                   客户端截图目录
tools/aui_compare.py                   比较器
~~~

每次尝试执行：

~~~text
./gradlew.bat runClient
  -PauiAutoExitSeconds=<AutoExitSeconds>
  -PauiViewportWidth=<ViewportWidth>
  -PauiViewportHeight=<ViewportHeight>
  -PauiLogStyles=true
  --console=plain
~~~

脚本取截图目录中按修改时间最新的 PNG，调用 aui_compare.py。匹配成功立即以 0 退出；所有尝试都失败时抛出错误。脚本会检查本次启动确实产生了更新的截图，不会误用上一次运行的结果。

示例：

~~~powershell
pwsh -File tools/aui_preview_loop.ps1 -MaxAttempts 8 -AutoExitSeconds 8 -ViewportWidth 427 -ViewportHeight 249
~~~

## 4. 常用工作流

### 4.1 使用 Node 客户端检查当前页面

~~~js
import { connect } from "./tools/apricity-debug-client.mjs";

const client = await connect();
try {
  const documents = await client.documents();
  for (const document of documents) {
    console.log(document.targetId, document.path);
  }
  if (documents.length > 0) {
    const page = await client.attach(documents[0].targetId);
    try {
      console.log(await page.locator("body").boxModel());
      console.log(await page.snapshot({ maxDepth: 4, maxNodes: 200 }));
    } finally {
      await page.detach();
    }
  }
} finally {
  client.close();
}
~~~

### 4.2 通过 MCP 检查页面

1. 启动 Minecraft，并确认 run/apricity/debug.json 已生成。
2. 安装 tools/apricity-mcp 依赖。
3. 把 MCP 配置指向 tools/apricity-mcp/server.mjs。
4. 先调用 apricity_documents 获取 targetId。
5. 再用 apricity_snapshot、apricity_query 或 apricity_inspect 定位问题。
6. 需要交互时使用 apricity_hover、apricity_click 或 apricity_fill。

不要把 targetId 写死在长期脚本里。页面重载、关闭或重新创建后 UUID 会变化，应每次先重新列出 document。

### 4.3 验证客户端基线截图

~~~powershell
pwsh -File tools/aui_preview_loop.ps1 -MaxAttempts 5 -AutoExitSeconds 5 -ViewportWidth 427 -ViewportHeight 249
~~~

如果脚本提示找不到参考图，应先确认 run/apricity/apricityui/example.png 是否存在；如果提示截图没有更新，应检查客户端是否按预期自动退出并写入 run/screenshots/aui。

## 5. 安全和清理注意事项

- 外部调试接口虽然只监听 127.0.0.1，仍然可以操作当前客户端中的真实 UI 输入；不要在不可信脚本中暴露 token。
- run/apricity/debug.json 和截图输出属于本地运行产物，不应作为工具源码提交。
- 不要手工编辑 tools/apricity-mcp/node_modules；更新依赖时使用 package.json 和 package-lock.json，再重新执行 npm install。
