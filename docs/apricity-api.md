# 模组专属 API（KJS / Java 入口）

AUI 在页面脚本 API 之外提供两层模组接口：KubeJS 绑定（全局 `ApricityUI`）和 Java 统一入口（`com.sighs.apricityui.ApricityUI`）。页面内的 DOM、事件、fetch、Canvas 见 [Web API 文档](web-api.md)；各页面宿主的语义见对应专题文档，本文不重复。

## 先搞清楚的三件事

**路径**：所有 API 用逻辑路径（`screens/example.html`），不写 `assets/apricityui/apricity/` 前缀，更不写磁盘路径。规则见[资源管理文档](resource-manager.md)。

**运行侧**：KJS 的 `ApricityUI` 在客户端脚本和服务端脚本里注册的是两组不同方法，方法名相同也不能跨侧调用：

| 脚本位置 | 能用 | 不能碰 |
| --- | --- | --- |
| 客户端脚本 | Document、Toast、screen、WorldWindow | 服务端容器绑定 |
| 服务端脚本 | `menu(player, path).bind(...)` | Document、Toast、WorldWindow |

**创建 ≠ 显示**：`createDocument(path)` 只是创建并注册一个 Document（Overlay 会自动画），不会打开 Screen；要 Screen 用 `new ApricityScreen(path)` 或 `ApricityUI.screen(path)`，要容器用 `menu(...).bind(...)`，要世界窗口用 `createWorldWindow(...)`。`createInWorldDocument(path)` 只创建世界 Document，不会自己显示成窗口。

## KJS 客户端 API

**Document**：

```javascript
var doc = ApricityUI.createDocument("overlays/status.html");   // 资源缺失返回 null
ApricityUI.getDocument("overlays/status.html");                // 同路径全部实例，返回列表
ApricityUI.getDocumentByUUID(uuid);
ApricityUI.getAllDocument();
ApricityUI.getCurrentScreenDocument();   // 只有当前真是 ApricityScreen 才有值
ApricityUI.removeDocument("overlays/status.html");             // 移除同路径全部实例
ApricityUI.getWindow();
```

同路径可以建多个实例、UUID 各不相同，要管单个实例就保存返回的 Document 对象。`getCurrentScreenDocument()` 对容器 Screen 返回 null 是正常的——`screen(path)` 打开的就是容器 Screen。

**Toast**：

```javascript
var id = ApricityUI.toast("加载完成");
var id = ApricityUI.toast("保存失败", 5000);
var id = ApricityUI.toast("资源已更新", 4200, "#20242b", "#ffffff", "#6fb4d6", true, "font-size: 14px;");
//                        message        时长(0=不自动关) 背景     文字       边框       点击关闭  自定义样式
ApricityUI.dismissToast(id);
ApricityUI.clearToasts();
```

返回的是 Toast ID，不是元素 ID。

**Screen**：

```javascript
ApricityUI.screen("screens/settings.html");   // 走服务端打开 UI-only 容器 Screen
ApricityUI.closeScreen();
```

`screen(path)` 不是客户端直接开 ApricityScreen——区别见 [ApricityScreen 文档](apricity-screen.md)。旧的 `openScreen(path)` 已废弃。

**WorldWindow**：

```javascript
var win = ApricityUI.createWorldWindow("world/notice.html", 10.5, 64.0, -3.5, 64);
// 可再加 maxDisplayDistance，或 yaw, pitch[, roll]（单位度）
win.setFacing(true);
win.setFollow(true);
win.setFollowFactor(0.35);
win.document.getElementById("title").setTextContent("基地");

ApricityUI.removeWorldWindow(win);
ApricityUI.clearWorldWindows();
```

创建即注册，移除连带销毁 Document。完整的距离、LOD、遮挡语义见 [WorldWindow 文档](world-window.md)。

## KJS 服务端 API

就一个入口——容器：

```javascript
ApricityUI.menu(player, "screens/machine.html")
    .bind(function (binding) {
        binding.blockEntity(pos).player();
    });
```

| BindingBuilder 方法 | 对应 HTML 容器 id |
| --- | --- |
| `player()` | `player` |
| `saveddata()` / `saveddata(name)` / `saveddata(name, cap)` | `saved_data` |
| `blockEntity(pos)` / `blockEntity(pos, cap)` | `block_entity` |
| `entity(id)` / `entity(id, cap)` | `entity` |

`saveddata("machine_data")` 的参数是服务端数据名，不是 HTML 的 id。容器 id、槽位、数据源的完整规则见[容器文档](container.md)。旧的 `openScreen(player, ...)` 已废弃。

## Java API

统一入口 `com.sighs.apricityui.ApricityUI`，KJS 绑定能做的事它都能做：

```java
// Document / Overlay
Document doc = ApricityUI.createDocument("overlays/status.html");
ApricityUI.getDocument(path);  ApricityUI.removeDocument(path);
ApricityUI.getDocumentByUUID(uuid);  ApricityUI.getAllDocument();

// Screen / 容器
ApricityUI.screen("screens/settings.html");                    // 客户端请求，UI-only
ApricityUI.menu(serverPlayer, "screens/machine.html")          // 服务端，真实容器
        .bind(binding -> binding.blockEntity(pos).player());
ApricityUI.closeScreen();

// WorldWindow
WorldWindow win = ApricityUI.createWorldWindow("world/notice.html", position, 64);
ApricityUI.removeWorldWindow(win);
```

细节分散在各专题文档里，别在这篇里找：

- Document 的生命周期、刷新代次、DOM 操作 → [Overlay 文档](overlay-document.md)和 [Web API 文档](web-api.md)
- `new ApricityScreen(path)`、pause/背景/缩放 → [ApricityScreen 文档](apricity-screen.md)
- 容器声明、高级 `ApricityScreenNetworkHandler.openScreen(...)` → [容器文档](container.md)
- WorldWindow 的旋转、Follow/Facing、LOD、坐标转换 → [WorldWindow 文档](world-window.md)
- Loader / ClientLoader / HTML 的资源读取 → [资源管理文档](resource-manager.md)
- 自定义元素注册（`@ElementRegister`、扫描包）→ [二次开发文档](secondary-development.md)
- DialogWindow、ContextMenu、ToastManager、ColorPicker 等内置组件 → [内置 UI 库](ui-library.md)

## 线程、空值、刷新

**线程**：创建 Document、改 DOM、开关 Screen、操作 WorldWindow 都得在客户端线程；网络回调和 Future 里先 `Minecraft.getInstance().execute(...)`。服务端 `menu` 在服务端线程调。

**空值**：这些 API 都用 null 表达失败，别拿 try-catch 代替判空——`createDocument`（模板缺失）、`getElementById`（元素不存在或引用已失效）、`getCurrentScreenDocument`（Screen 类型不对）、WorldWindow 的投影/命中（不可见、被挡、超距）。

**刷新**：`refresh()` 是整页重建，不是更新手段。高频数据改现有元素的 textContent/属性。刷新后旧 Element、监听器、Observer 全部失效；异步回调先存 `getRefreshGeneration()`，回来用 `isCurrentGeneration(gen)` 验证再写。

Java 侧更新 DOM 时如果会触发脚本辅助逻辑，包一层 `Document.runWithContext(document, () -> ...)` 建立当前 Document 上下文。

## 客户端配置键

配置文件 `config/apricityui-client.toml`，Java 侧从 `ApricityUIConfig.CLIENT` 读：

| 键 | 作用 |
| --- | --- |
| `debug.autoReload` | 开发目录变化时自动重载 |
| `debug.frameTimingHud` | 帧耗时 HUD |
| `debug.remoteDebug` | 本地外部调试器 |
| `debug.resourceManagerWorldWindow` | 资源管理器以世界窗口打开 |
| `input.viewportZoomPassThrough` | Ctrl+滚轮缩放穿透未拦截的 Overlay |
| `worldWindow.maxDisplayDistance` | 世界窗口默认显示距离 |
| `worldWindow.lodEnabled` / `fullDetailDistance` / `reducedDetailDistance` | 世界窗口 LOD |
| `worldWindow.depthOffsetScale` | 世界窗口深度偏移比例 |
