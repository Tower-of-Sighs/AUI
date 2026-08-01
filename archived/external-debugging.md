# Apricity 外部调试接口

Apricity Debug Protocol（ADP）让外部程序以类似 Playwright 的方式检查和操作正在运行的 Apricity UI。协议第一版使用 WebSocket 承载 JSON-RPC 2.0，不兼容 Chrome DevTools Protocol，也不执行任意 JavaScript。

## 启用与连接

开发环境默认启用，生产环境默认关闭。也可以通过 JVM 参数显式控制：

```text
-Dapricityui.debug.enabled=true
-Dapricityui.debug.enabled=false
```

服务只监听本机回环地址，端口固定为 `25321`：

```text
ws://127.0.0.1:25321/apricity?token=<token>
```

每次游戏启动都会生成新的随机 token。服务启动成功后，连接信息会写入：

```text
run/apricity/debug.json
```

文件内容示例：

```json
{
  "protocolVersion": 1,
  "endpoint": "ws://127.0.0.1:25321/apricity",
  "token": "...",
  "pid": 12345
}
```

自动化环境可以使用固定 token：

```text
-Dapricityui.debug.token=my-local-token
```

token 可以放在 URL 的 `token` 查询参数中，也可以通过 `Authorization: Bearer <token>` 请求头传递。未认证连接会在 WebSocket 握手前被拒绝。

## 线程模型

网络线程只负责解析请求和发送响应。所有 `Document`、DOM、布局和输入操作都会进入线程安全队列，并在游戏客户端 tick 的开始阶段执行。这保证外部调试不会与 Apricity 的布局或渲染并发访问同一棵节点树。

客户端停止响应时，请求会保持等待，不会转移到网络线程执行。

## JSON-RPC

请求：

```json
{"jsonrpc":"2.0","id":1,"method":"Target.list","params":{}}
```

成功响应：

```json
{"jsonrpc":"2.0","id":1,"result":{"targets":[]}}
```

错误响应：

```json
{"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"Node is detached"}}
```

支持标准错误码 `-32600`、`-32601`、`-32602`，以及协议错误码：

| 错误码 | 名称 | 含义 |
| --- | --- | --- |
| `-32001` | `TargetClosed` | document 已关闭或 session 已失效 |
| `-32002` | `NodeDetached` | 节点不存在、已脱离文档或不属于当前 session |
| `-32003` | `NotActionable` | 元素不可见、不可点击或类型不支持该操作 |
| `-32004` | `LimitExceeded` | snapshot 超过节点数量限制 |

## Target

一个 Target 对应一个运行中的 Apricity `Document`。同一路径可以同时存在多个 document，因此 `targetId` 始终使用完整 UUID；路径只用于展示和筛选。

| 方法 | 参数 | 结果 |
| --- | --- | --- |
| `System.info` | 无 | 协议版本、服务地址和能力 |
| `Target.list` | 无 | 当前 document 列表 |
| `Target.attach` | `targetId` | 新建 session |
| `Target.detach` | `sessionId` | 关闭 session |

```json
{"jsonrpc":"2.0","id":2,"method":"Target.attach","params":{"targetId":"document-uuid"}}
```

除 `System.info`、`Target.list` 和 `Target.attach` 外，DOM 方法都需要传入 `sessionId`。

## DOM 方法

| 方法 | 必填参数 | 说明 |
| --- | --- | --- |
| `DOM.query` | `sessionId`, `selector` | 返回第一个匹配元素的 `nodeId`，没有匹配时为 `null` |
| `DOM.queryAll` | `sessionId`, `selector` | 返回所有匹配元素的 `nodeId` |
| `DOM.snapshot` | `sessionId` | 返回 DOM 快照；可选 `maxDepth`、`maxNodes` |
| `DOM.getAttributes` | `sessionId`, `nodeId` | 返回属性对象 |
| `DOM.getText` | `sessionId`, `nodeId` | 返回 `textContent` |
| `DOM.getComputedStyle` | `sessionId`, `nodeId` | 返回完整计算样式 `cssText` |
| `DOM.getBoxModel` | `sessionId`, `nodeId` | 返回屏幕坐标下的 margin、border、padding、content 矩形 |
| `DOM.hover` | `sessionId`, `nodeId` | 将鼠标移动到元素 border box 中心 |
| `DOM.click` | `sessionId`, `nodeId` | 在元素中心发送 mousemove、mousedown、mouseup |
| `DOM.fill` | `sessionId`, `nodeId`, `value` | 聚焦 input/textarea，全选并替换文本 |

`nodeId` 是节点的完整 UUID，但必须与创建它的 `sessionId` 一起使用。节点刷新、被删除或移动到其他 document 后，操作会返回 `NodeDetached`。

快照默认最大深度为 `32`、最大节点数为 `5000`。上限分别为 `128` 和 `20000`，用于避免外部请求在一帧内制造无界工作量。

## Locator 客户端

仓库中的 `tools/apricity-debug-client.mjs` 提供轻量的 Playwright 风格包装。Node.js 22 可以直接运行，不需要安装依赖：

```js
import { connect } from "./tools/apricity-debug-client.mjs";

const client = await connect();
const documents = await client.documents();
const page = await client.attach(documents[0].targetId);

const card = page.locator(".file-card");
await card.waitFor({ state: "visible" });
await card.click();
console.log(await card.boxModel());

await page.detach();
client.close();
```

Locator 不缓存节点 UUID。每次操作前都会重新解析 selector，因此 document 内部刷新和 DOM 重建不会使 Locator 永久失效。`waitFor` 在外部客户端轮询，不阻塞游戏线程。

也可以直接发送 JSON-RPC：

```json
{"jsonrpc":"2.0","id":3,"method":"DOM.query","params":{"sessionId":"session-uuid","selector":"#submit"}}
```

## MCP 服务

`tools/apricity-mcp` 提供基于官方 MCP SDK 的 stdio 服务。首次使用时安装依赖：

```powershell
cd tools/apricity-mcp
npm install
```

MCP 客户端配置示例：

```json
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
```

MCP 服务提供以下工具：

| 工具 | 用途 |
| --- | --- |
| `apricity_documents` | 列出当前运行的 document 及完整 UUID |
| `apricity_snapshot` | 获取有深度和节点数限制的 DOM 快照 |
| `apricity_query` | 批量查询元素摘要 |
| `apricity_inspect` | 检查首个匹配元素的属性、文本、盒模型和可选计算样式 |
| `apricity_wait_for` | 等待元素 attached、detached、visible 或 hidden |
| `apricity_hover` | 移动鼠标到元素中心 |
| `apricity_click` | 点击元素 |
| `apricity_fill` | 填写 input 或 textarea |

默认从 discovery 文件读取 endpoint 和 token。也可以同时设置 `APRICITY_DEBUG_ENDPOINT` 与 `APRICITY_DEBUG_TOKEN` 显式连接。MCP 进程只使用 stderr 输出自身错误，stdout 专用于 MCP stdio 消息。

## 安全边界

- 服务只绑定 `127.0.0.1`，不能通过局域网访问。
- 每次启动使用随机 token，除非 JVM 参数显式覆盖。
- discovery 文件包含访问凭证，不应提交到版本控制或分享给其他用户。
- 协议不提供文件读写、Java 反射或任意脚本执行。
- 调试接口可以发送真实 UI 输入，启用后应视为拥有当前游戏 UI 的控制权。

## 第一版限制

- 不支持 `evaluate`。
- 不支持同步截图。现有截图管线是异步落盘模型，后续会以异步事件或独立二进制传输实现。
- 不提供 CDP 或 Playwright wire protocol 兼容性。
- 不等待动画稳定；操作只检查调用时的可见性、尺寸、pointer-events 和 hit test。
