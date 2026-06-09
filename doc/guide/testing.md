# ApricityUI Web API 测试流程

这份文档用于说明当前 Web API 相关测试的完整执行流程，包括：

- 单元测试如何覆盖 Web API 行为
- 真实客户端自测如何验证生产环境路径
- 覆盖率报告如何查看
- 当前测试结果该如何解读

## 1. 测试分层

当前测试分成两层：

### 1.1 单元测试

位置：

- `src/test/java/com/sighs/apricityui/webapi/`

主要测试类：

- `ElementBindingTest`
- `DomSemanticsTest`
- `WindowApiTest`
- `GlobalJsBootstrapTest`
- `ResourcePipelineTest`
- `LoaderIntegrationTest`
- `DocumentLifecycleTest`
- `TestDocumentFactory`

这层主要验证：

- `Document` / `Element` / `Window` 的 Web 风格接口语义
- 事件分发、焦点、滚动、表单、选择器、MutationObserver 等 DOM 行为
- `global.js` 资源内容是否包含预期 bootstrap 能力
- 资源加载路径、脚本注入、文档生命周期等 Java 侧逻辑

优点：

- 跑得快
- 易定位回归
- 适合做大面积接口覆盖

局限：

- 不能完全代表 Rhino + Forge + Client 运行时的真实行为
- JS 包装 Java 对象时的属性桥接差异，只有生产路径才能暴露

### 1.2 真实客户端自测

位置：

- Java 驱动：`src/main/java/com/sighs/apricityui/instance/ClientRuntimeSelfTest.java`
- 页面资源：`src/main/resources/assets/apricityui/apricity/tests/client-runtime-self-test.html`
- 生命周期页面：`src/main/resources/assets/apricityui/apricity/tests/lifecycle-event-test.html`

这层主要验证：

- `global.js` 在真实 Rhino 环境中是否按预期工作
- `document.location`、`setTimeout`、生命周期事件等是否真的能在客户端跑通
- 单元测试无法覆盖的“脚本层访问 Java 对象”路径

优点：

- 最接近生产环境
- 能发现单元测试通过但真实运行失败的问题

局限：

- 启动慢
- 日志诊断成本更高
- 不适合作为高频细粒度回归入口

## 2. 推荐执行顺序

推荐每次按下面顺序跑：

1. 先跑 Web API 单元测试
2. 再看 JaCoCo 覆盖率
3. 最后跑真实客户端自测

原因：

- 单元测试先挡住明显回归
- 覆盖率能快速发现新增接口是否完全没测到
- 真实客户端自测用于兜底验证“生产路径是否真的能用”

## 3. 单元测试执行方式

### 3.1 跑全部 test

```powershell
.\gradlew.bat test --console plain --no-daemon
```

### 3.2 只跑 Web API 测试

```powershell
.\gradlew.bat test `
  --tests com.sighs.apricityui.webapi.GlobalJsBootstrapTest `
  --tests com.sighs.apricityui.webapi.DomSemanticsTest `
  --tests com.sighs.apricityui.webapi.WindowApiTest `
  --tests com.sighs.apricityui.webapi.DocumentLifecycleTest `
  --tests com.sighs.apricityui.webapi.LoaderIntegrationTest `
  --tests com.sighs.apricityui.webapi.ResourcePipelineTest `
  --tests com.sighs.apricityui.webapi.ElementBindingTest `
  --console plain --no-daemon
```

### 3.3 结果判定

满足以下条件才算通过：

- Gradle `BUILD SUCCESSFUL`
- 没有 JUnit failed test
- `jacocoTestReport` 正常生成

## 4. 覆盖率查看方式

项目已接入 JaCoCo。

执行 `test` 后会自动生成报告：

- HTML 报告：`build/reports/jacoco/test/html/index.html`
- XML 报告：`build/reports/jacoco/test/jacocoTestReport.xml`

推荐优先看 HTML 报告。

重点关注：

- `Document`
- `Element`
- `Window`
- `Loader`
- `ClientLoader`
- `HTML`
- `JS$Extractor`

### 4.1 覆盖率解读原则

不要只看总覆盖率，要看“关键生产路径有没有测到”：

- `Document` / `Element` / `Window` 覆盖率高，说明 DOM 接口层较完整
- `Loader` / `ClientLoader` 覆盖率低，说明真实资源加载路径仍有盲区
- 资源类或 bootstrap 类覆盖率高，不代表真实 Rhino 行为就一定正确

## 5. 真实客户端自测执行方式

### 5.1 启动命令

```powershell
$env:JAVA_TOOL_OPTIONS='-Dapricityui.clientSelfTest=true -Dapricityui.clientSelfTest.exitOnFinish=true'
.\gradlew.bat runClient --console plain --no-daemon
```

说明：

- `apricityui.clientSelfTest=true` 会启用客户端自测
- `apricityui.clientSelfTest.exitOnFinish=true` 会在自测结束后自动退出客户端

### 5.2 成功标志

日志中出现：

```text
[AUI SelfTest] PASS client runtime self-test
```

### 5.3 失败标志

日志中出现：

```text
[AUI SelfTest] FAIL client runtime self-test: ...
```

失败时要直接看这条日志后面的具体断言内容。

## 6. 当前测试覆盖的重点能力

已经有测试的核心方向：

- DOM 基础接口与节点关系
- 属性 / dataset / classList 同步
- focus / blur / scroll / submit / input / change
- pointer / mouse / dblclick / contextmenu
- MutationObserver / ResizeObserver
- `URLSearchParams`
- `FormData`
- `window.fetch`
- `requestAnimationFrame`
- `setTimeout` / `setInterval`
- `document.readyState`
- `DOMContentLoaded` / `load`
- `document.location` / `window.location`
- `global.js` 资源注入
- 资源加载与 bootstrap 组装

## 7. 当前结果解读

截至目前，结论应当这样看：

### 7.1 可以认为已经比较可靠的部分

- Java 侧 DOM 接口语义
- 事件系统的大部分基础行为
- 生命周期状态推进
- `setTimeout` 在客户端线程上的基本执行
- `location` 注入与读取

### 7.2 仍然不能完全放心的部分

- `FormData` 的 `multiple select` 真实客户端路径
- 任何依赖 Rhino 对 Java 对象属性分发的边缘行为
- `ClientLoader` / 资源包加载的更多生产分支

## 8. 当前已知真实问题

目前真实客户端自测仍有一个已知失败项：

- `FormData` 在 `multiple select` 场景下，生产环境仍只序列化出 `alpha=1`

最新自测诊断已经确认：

- `select.multiple=true`
- `option` 数量为 `2`
- 两个 `option` 的 `selected` 标记都存在

这说明问题不是测试页写错，而是生产运行时里 `FormData` 对多选 `select` 的脚本桥接仍有缺口。

因此，当前测试体系的结论不是“全部通过”，而是：

- 单元测试覆盖已经较完整
- 真实客户端自测已经能稳定暴露生产问题
- 还剩一个明确的生产缺口待修

## 9. 推荐日常流程

日常开发建议按这个节奏走：

1. 改接口后先跑 Web API 单元测试
2. 看 JaCoCo 是否把新增接口覆盖到了
3. 改到脚本桥、资源加载、生命周期时，必须再跑一次 `runClient` 自测
4. 只有单测通过且客户端自测通过，才认为“接近可用于生产”

## 10. 什么时候必须补新测试

出现下面任一情况时，应该补测试：

- 新增浏览器接口
- 新增浏览器事件
- 调整 `global.js`
- 调整 `Document.refresh()`、`Loader`、`ClientLoader`
- 调整事件传播、焦点、滚动、表单行为
- 修复 Rhino/客户端专属 bug

新增测试时的原则：

- 先补单元测试，覆盖语义
- 如果改动涉及 JS 访问 Java 对象，再补或更新客户端自测

