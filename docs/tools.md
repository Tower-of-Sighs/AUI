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

### 2.3 apricity-debug-client.mjs

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

### 2.4 apricity-mcp

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
