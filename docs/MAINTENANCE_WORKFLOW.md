# 多版本日常维护工作流

> **AUI 适配说明（优先于下文框架原文）**
>
> 本文档来自 SighsTemple 框架。AUI 的现状与其中两条规则存在**有意偏差**：
>
> 1. AUI 的 `common/` **包含** `net.minecraft.*` / Forge 类型，并且引用 target 侧的类
>    （`loader.Loader`、`client.Client` 等）。因此 common 不是独立 Gradle 项目、
>    不能单独编译，而是通过 target `build.gradle` 中的
>    `sourceSets.main.java.srcDir '../../common/src/main/java'` 编译进 target。
>    下文"非协商边界 / common 禁止包含 Minecraft 类型"一条对 AUI 暂不适用；
>    若未来做依赖倒置重构（接口下沉），再恢复该边界。
> 2. AUI 目前只有 `targets/forge-1.20.1/` 一个 target，下文表格中的
>    fabric / neoforge target 行作为未来扩展的参考保留。
>
> 共享资源规则与框架一致：loader 无关资源放 `common/src/main/resources/`，
> 加载器 metadata（`META-INF/mods.toml`）留在 target。

## 目标

本规范用于多个维护者分别负责不同 Minecraft 版本或加载器时的日常开发。目标是：

1. target 维护者只下载、构建和评审自己负责的依赖。
2. `common` 保持可预测、无加载器依赖，不被单个 target 的 API 污染。
3. 跨版本行为变化有明确的评审和验证范围。
4. 每个发布 jar 的兼容范围、构建 JDK 和责任人可追溯。

## 目录所有权

| 路径 | 默认责任 | 可直接修改 | 额外评审 |
| --- | --- | --- | --- |
| `common/` | Core Maintainer | 共享业务逻辑、纯 Java 测试、稳定接口 | 至少一名受影响 target 维护者 |
| `targets/forge-1.20.1/` | Forge 1.20.1 Maintainer | Forge 入口、事件、资源、Mixin、适配器 | Core Maintainer 仅在 common 合约变化时参与 |
| `targets/fabric-1.20.1/` | Fabric 1.20.1 Maintainer | Fabric entrypoint、callback、资源、Mixin、适配器 | 同上 |
| `targets/neoforge-1.21.1/` | NeoForge 1.21.1 Maintainer | NeoForge 入口、事件、资源、Mixin、适配器 | 同上 |
| `targets/neoforge-26.1/` | NeoForge 26.1 Maintainer | Java 25、26.1 API、资源、渲染和适配器 | 同上 |
| 根 Gradle、CI、`docs/` | Build / Release Maintainer | 聚合入口、CI 矩阵、维护文档 | 受影响 target 维护者 |

一个人可以承担多个角色，但评审规则仍按路径执行。实际 GitHub 账号映射写入 `.github/CODEOWNERS`；仓库提供 `.github/CODEOWNERS.example` 作为起点。

## 非协商边界

### common

`common` 只能包含：

- Java 8 兼容的业务逻辑、数学、状态机、队列和不可变 DTO。
- 不引用 Minecraft 类型的扩展接口、事件语义和渲染计划。
- 无加载器依赖的单元测试。

`common` 禁止包含：

- `net.minecraft.*`、Forge、NeoForge、Fabric、Mixin、网络 buffer 或渲染 API。
- 运行时 loader/version 判断、反射分发和某个 target 的资源路径。
- 为解决单一 target 编译错误而加入的 Minecraft API 抽象泄漏。

### target

每个 target 是独立 Gradle 根工程，拥有自己的：

- 入口、注册、事件、网络和生命周期代码。
- Minecraft API 调用、Mixin、accessor、渲染后端和 client-only 代码。
- metadata、资源、数据生成和版本范围。
- JDK、Gradle wrapper、加载器和 mappings 配置。

不要把 target 专属类以同一全限定名复制到 `common`，也不要依赖 classpath 顺序覆盖 shared class。

## 变更分类

提交或 PR 必须先选择一种主类别。

| 类别 | 典型路径 | 最小评审 | 最小验证 |
| --- | --- | --- | --- |
| `target-only` | 一个 `targets/<name>/` | 对应 target 维护者 | 该 target `clean build` 与最小运行验证 |
| `common-internal` | `common/`，无接口变化 | Core Maintainer | common 测试 + 所有引用 common 的已支持 target build |
| `common-contract` | `common/` 接口、DTO、语义变化 | Core + 每个受影响 target 维护者 | 所有受影响 target build；行为改动应有测试 |
| `cross-target-feature` | common 加多个 target | Core + 每个改动 target 维护者 | 每个改动 target build；未实现 target 必须明确记录 |
| `build-or-ci` | 根 Gradle、wrapper、CI | Build / Release + 受影响 target 维护者 | 对应 JDK 的完整 job |
| `docs-only` | README、docs | 文档责任人 | 链接和命令检查 |

一个 PR 若同时修改 `common` 和一个 target，不应标记为 `target-only`。必须说明该 common 改动是否影响其他 target。

## 分支与提交

### 分支命名

```text
target/forge-1.20.1/<topic>
target/fabric-1.20.1/<topic>
target/neoforge-1.21.1/<topic>
target/neoforge-26.1/<topic>
common/<topic>
build/<topic>
docs/<topic>
```

### 提交前缀

```text
forge-1.20.1: fix entity renderer registration
fabric-1.20.1: add client packet adapter
neoforge-1.21.1: update data generation
neoforge-26.1: migrate item template handling
common: expose render-plan hook
build: update neoforge-26.1 wrapper
docs: clarify release matrix
```

提交应尽量只覆盖一个责任边界。需要同步改动时，先提交 `common` 合约或 DTO，再提交各 target 适配；不要将不相关格式化混入功能改动。

## 开发流程

### target-only 改动

1. 只打开自己的 `targets/<name>/`。
2. 在该 Gradle 项目中编辑 target 和可见的 `common`。
3. 如果无需改 `common`，不要触碰其他 target。
4. 执行 target 自己的 `clean build`。
5. 在 PR 中说明 Minecraft 版本、加载器、验证 JDK 和实际测试场景。

### common 改动

1. 先写清楚新增或改变的业务语义，不以 Minecraft API 名称描述接口。
2. 为 shared logic 添加或更新无加载器单元测试。
3. 由每个受影响 target 维护者更新桥接实现。
4. 对每个受影响 target 独立构建；不要把根项目的单一构建结果当作全矩阵证明。
5. 对不立即适配的 target，禁止静默合并。必须明确选择：同 PR 适配、feature flag 禁用、或在 issue/roadmap 中记录阻塞。

### 跨 target 新功能

推荐按以下提交顺序：

```text
1. common: 新增纯业务模型、状态与稳定接口
2. target A: 实现加载器 / Minecraft 桥接
3. target B: 实现加载器 / Minecraft 桥接
4. target C: 实现加载器 / Minecraft 桥接
5. docs/tests: 更新支持矩阵与验证记录
```

不能为了“统一代码”在 `common` 放入 `ItemStack`、`PoseStack`、`Holder`、`StreamCodec`、Fabric callback 或 Forge/NeoForge event。共享的是语义与数据，不是 Minecraft 对象。

## 渲染与客户端变化

渲染改动默认属于 `target-only`，即使视觉效果在多个版本相同。

推荐责任划分：

```text
common/render/
  RenderInput, RenderPlan, RenderProfile, SharedRenderLogic

targets/<name>/.../client/
  RenderBackend, ClientBootstrap, target-specific RenderHooks
```

当只有 Forge 1.20.1 需要微调时：

- 参数不同：Forge target 提供自己的 profile。
- 多一两步：Forge target 提供 hook；其余 target 使用 no-op hook。
- buffer、shader、model、事件时机不同：Forge target 替换自己的 backend 或 bootstrap。

不得通过在 `common` 和 target 中创建同名 class 来“覆盖”实现。客户端类不得被服务端入口或 `common` 直接加载。

## 验证矩阵

| Job | 目录 | Gradle JVM | 必须执行 |
| --- | --- | --- | --- |
| Forge 1.20.1 | `targets/forge-1.20.1` | JDK 21 | `./gradlew clean build` |
| Fabric 1.20.1 | `targets/fabric-1.20.1` | JDK 21 | `./gradlew clean build` |
| NeoForge 1.21.1 | `targets/neoforge-1.21.1` | JDK 21 | `./gradlew clean build` |
| NeoForge 26.1 | `targets/neoforge-26.1` | JDK 25 | `./gradlew clean build` |

补充规则：

- 根 `-PallTargets=true build` 仅覆盖可由 JDK 21 同时构建的三个 target。它不是 NeoForge 26.1 的验证替代品。
- 任何改动 `common/` 的 PR，至少触发所有当前支持 target 的 build job。
- 任何改动一个 target 的资源、Mixin 或 metadata 的 PR，至少检查最终 jar 是否包含对应 metadata、配置文件和 common class。
- 构建成功不代替运行验证。涉及事件、网络、Mixin、注册、渲染或数据包时，应在 PR 中记录 client、dedicated server、reload 或 data generation 的实际验证范围。

## Common 自动验证

`.github/workflows/verify-common.yml` 是 `common` 的 CI 门禁。它在 push、pull request 或手动触发时，若检测到以下路径变更，则执行完整矩阵：

```text
common/**
build.gradle, settings.gradle, gradle.properties, gradle/**
scripts/build-target.ps1
.github/workflows/verify-common.yml
```

执行顺序：

1. `common-tests` 使用 JDK 21 执行 `common` 的 `clean test`。
2. 四个 target job 并行执行各自 wrapper 的 `clean build`。`build` 包含 target 和 common 的测试任务。
3. 每个 target 使用对应 JDK：Forge/Fabric 1.20.1、NeoForge 1.21.1 使用 JDK 21；NeoForge 26.1 使用 JDK 25。
4. 成功或失败时上传已生成的 `build/libs/*.jar`，便于检查发布物。

`scripts/build-target.ps1` 对 target build 最多重试三次，专门处理首次解析 mappings、NeoForm 或 Maven 时的短暂网络失败。编译错误、测试错误和持续依赖错误仍会使 job 失败。

建议在仓库分支保护中把以下检查设为合并必需条件：

```text
Common Unit Tests
forge-1.20.1 (JDK 21)
fabric-1.20.1 (JDK 21)
neoforge-1.21.1 (JDK 21)
neoforge-26.1 (JDK 25)
```

target-only 的快速验证工作流可以后续按同一模式增加，但不得替代 common 的全矩阵工作流。

## PR 评审要求

每个 PR 必须包含：

1. 变更类别与受影响 target。
2. 是否修改 `common`，以及 common 合约是否变化。
3. 使用的 JDK、实际执行的构建命令和结果。
4. 运行时验证说明，或明确说明为何不适用。
5. 不支持/未验证 target 的影响说明。

评审者检查：

- 是否将平台 API 泄漏进 `common`。
- 是否把 1.20.1、1.21.1、26.1 的资源、Mixin 或客户端代码混入同一 target。
- 是否更新了所有受影响的桥接实现。
- 是否因工具链不兼容而错误使用根聚合替代独立构建。
- 是否新增了重复 class、运行时版本判断或无说明的反射兼容层。

## 发布流程

### 发布单元

一个发布物只能对应一个 loader 与一个 Minecraft 版本：

```text
SighsTemple-forge-1.20.1-<version>.jar
SighsTemple-fabric-1.20.1-<version>.jar
SighsTemple-neoforge-1.21.1-<version>.jar
SighsTemple-neoforge-26.1-<version>.jar
```

不要发布混合 loader 的 universal jar。

### 发布候选

1. 冻结本次发布涉及的 `common` 合约。
2. 对每个承诺发布的 target 用其要求的 JDK 执行 `clean build`。
3. 检查 jar 名称、metadata、Mixin / access widener、common class 和版本范围。
4. 在干净的 client 与 dedicated server 环境做最小启动验证。
5. 发布说明按 target 列出新增、修复、已知限制和不包含的版本。

版本可以不同步发布。某个 target 未准备好时，不应阻止其他 target 发布，但发布说明必须准确表达覆盖范围。

## 支持、弃用与新增版本

### 新增 target

新增版本必须：

1. 新建 `targets/<loader>-<minecraft-version>/`，不在旧 target 添加运行时版本分支。
2. 提供独立 wrapper、`settings.gradle` 和 `../../common` 源码映射。
3. 指定责任人、最低 JDK、Gradle 版本、加载器版本和 CI job。
4. 完成独立构建、client / server 最小验证和最终 jar 检查后，才标记为支持。

### 弃用 target

弃用前应发布最后一个维护版本，并在 README/发布说明写明：

- 最后支持版本。
- 停止接收功能或修复的日期/条件。
- 是否只保留构建修复或完全冻结。
- 迁移目标版本。

弃用 target 不应被删除到无法重现历史发布；先从日常 CI 或支持矩阵移除，再按仓库归档策略处理。

## 例外处理

紧急安全修复可以缩短评审，但不能跳过：

- 所有受影响 target 的构建。
- 对 `common` 改动的影响说明。
- 发布后补充的回归测试或 issue 记录。

上游 Maven、Mappings 或工具链暂时不可用时，PR 可以合并为“结构已就绪、验证受外部阻塞”，但必须在 PR 和追踪 issue 中记录失败命令、失败时间、上游依赖和恢复条件。不得把未验证 target 标记为已支持。
