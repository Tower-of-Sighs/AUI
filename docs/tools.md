# 附加工具

`tools/` 目录是开发用的外部调试和截图工具，不随模组运行，需要时手动调用：

```text
tools/
├── apricity-debug-client.mjs   外部调试协议的 Node.js 客户端
├── apricity-mcp/               MCP stdio 桥（让 MCP 工具直接操作运行中的页面）
├── aui_compare.py              PNG 视觉差异比较
└── aui_preview_loop.ps1        循环启动客户端并比较截图
```

环境：Node 客户端要 Node 22+；MCP 要 Node 20+ 并先 `npm install`；比较器要 Python 3 + Pillow（`python -m pip install Pillow`）。命令从仓库根目录跑（preview 脚本会调 `./gradlew.bat`）。

## 打开外部调试服务

工具链路都连游戏内的调试服务，地址固定 `ws://127.0.0.1:25321/apricity`。开启方式（开发环境默认开）：

```toml
# config/apricityui-client.toml
[debug]
remoteDebug = true
```

服务启动后会在游戏目录写 `run/apricity/debug.json`（endpoint + token + pid）。token 每次启动随机生成，可用 `-Dapricityui.debug.token=...` 固定，`-Dapricityui.debug.enabled=false` 关闭服务。

**debug.json 是访问凭据**——拿到它就能操作当前客户端的真实 UI。别提交、别分享。连不上时先查这个文件存不存在。

## apricity-debug-client.mjs

轻量 Node 客户端，封装协议调用：

```js
import { connect } from "./tools/apricity-debug-client.mjs";

const client = await connect();          // 默认读 run/apricity/debug.json
try {
    const targets = await client.documents();
    const page = await client.attach(targets[0].targetId);   // targetId 是完整 UUID
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
```

- `connect({ discoveryFile, endpoint, token, timeout })`：显式给 endpoint 时必须同时给 token；
- Client：`info()` / `documents()` / `attach(targetId)` / `call(method, params)` / `close()`；
- Page：`locator(selector)` / `snapshot({maxDepth, maxNodes})` / `detach()`；
- Locator：`count/attributes/text/computedStyle/boxModel/hover/click/fill(value)/waitFor({state})`，state 有 attached/detached/visible/hidden。Locator 每次操作重新查询选择器，页面刷新后不用重建；
- targetId 别写死——页面重载、关闭后 UUID 会变，每次先 `documents()`。

能力边界：只有 DOM 查询、样式/盒模型检查和有限输入操作。**没有 evaluate、任意 JS 执行、文件读写、截图**。要跑页面脚本就改测试页面或用页面日志。

## apricity-mcp

MCP stdio 桥，让支持 MCP 的工具直接查看和操作运行中的 AUI：

```powershell
cd tools/apricity-mcp
npm install
npm test
```

MCP 客户端配置：

```json
{
  "mcpServers": {
    "apricity": {
      "command": "node",
      "args": ["D:/work/AUI/tools/apricity-mcp/server.mjs"],
      "env": { "APRICITY_DEBUG_DISCOVERY": "D:/work/AUI/run/apricity/debug.json" }
    }
  }
}
```

也可以绕过发现文件，直接设 `APRICITY_DEBUG_ENDPOINT` + `APRICITY_DEBUG_TOKEN`（两个必须成对）。

提供的工具：`apricity_documents`（列 target）→ `apricity_snapshot` / `apricity_query` / `apricity_inspect`（查 DOM）→ `apricity_wait_for` / `apricity_hover` / `apricity_click` / `apricity_fill`（交互）。查询都有数量/深度上限（默认 snapshot 32 层 5000 节点、query 20 条），失败返回 `isError: true` 不会崩进程。

## aui_compare.py：截图比较

```powershell
python tools/aui_compare.py <reference.png> <actual.png>
```

actual 会被居中裁剪到 reference 的宽高比、Lanczos 缩放到同尺寸，然后算 RGB 均方根误差。默认判相似：`rms < 70` 且 `dark_ratio < 0.55` 且 `avg_luma > 40`（后两条用来筛掉全黑/空截图）。

输出是 JSON（`ok` + 各项指标），退出码 0=相似、1=不相似或文件不存在、2=参数错。它不是逐像素严格比对；要严格一致就额外要求输出里 `rms == 0`。

## aui_preview_loop.ps1：启动-截图循环

反复启动客户端、取最新截图和参考图比较，调启动时序或资源加载不稳的问题：

```powershell
pwsh -File tools/aui_preview_loop.ps1 -MaxAttempts 8 -AutoExitSeconds 8
```

- 参数：`MaxAttempts`（默认 5）、`AutoExitSeconds`（默认 5）、`ViewportWidth/Height`（默认 427×249）；
- 依赖：参考图 `run/apricity/apricityui/example.png`，截图目录 `run/screenshots/aui/`；
- 每次尝试跑 `./gradlew.bat runClient`（带自动退出和视口参数），取截图目录里最新的 PNG 比较；匹配成功立即退出 0，全部失败抛错。脚本会确认本次启动真的产出了新截图，不会拿上次的充数。

找不到参考图就先确认 example.png 存在；提示截图没更新就查客户端有没有按预期自动退出并写截图。

## 注意

- 调试接口能驱动真实 UI 输入，token 别暴露给不可信脚本；
- `run/apricity/debug.json` 和截图是本地产物，别提交；
- `tools/apricity-mcp/node_modules` 是 npm 产物，别手改；更新依赖改 package.json 后重新 `npm install`。
