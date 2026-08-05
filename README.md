# ApricityUI

Design UI with HTML, CSS, and maybe JavaScript along.

通过经典的 H5 三剑客（HTML / CSS / JS）构建 Minecraft 的 UI。

## 项目结构

本项目采用 `common + targets/<loader>-<minecraft-version>` 多加载器框架结构：

- `common/`：共享源码树（Java、测试、loader 无关资源）。可**单独编译并跑测试**
  （`gradlew -p common test`，见 `common/build.gradle`），不引用任何 target 侧类；
  loader 绑定能力经 `com.sighs.apricityui.spi.AuiServices` 由 target 侧实现注册
  （`ApricityUIForge` 为 @Mod 入口，`AuiServicesBootstrap` 负责注册服务）。
- `targets/forge-1.20.1/`：Forge 1.20.1 目标，独立 Gradle 工程（自己的 wrapper 与配置）。
  加载器 metadata（`META-INF/mods.toml`）、mixin 类与注册、@Mod 入口保留在 target 中。

共享资源放在 `common/src/main/resources/`，构建 target 时会合并进最终 jar；
target 自身 `libs/` 目录中的 `*.jar` 会自动作为 `implementation` 依赖。

## 开发须知（PR 规范）

1. 不可直接向 master 分支提交 commit。
2. 提交修改应该新建分支，分支名应体现对内核/MC 内容的关联，建议的分支命名规范：
    - 优化 Optimize：`opt(core)/html-parser`
    - 修复错误 Fix：`fix(neo26.1)/time-format`
    - 功能 Feature：`feat(core+neo21.1)/editor-markdown`
    - 重构 Refactor：`refactor(forge20.1+neo21.1)/textarea-render`
3. 从其他分支合并修改到 master 分支时请发起一个 PR (Pull Request)。
4. 分支合并前会有人来帮助你检查代码中的错误，通过审查后会合并到主线。
5. 如果你的分支（以下以 a 表示）与 master 分支冲突，解决方案如下：
    - 从 master 分支建立一个新分支（以下以 b 表示）
    - 通过 cherry-pick 指令将你的修改从 a 分支移动过来
    - 删除本地 a 分支
    - 将本地 b 分支重命名成 a
    - `git push (--force)`

## 构建与开发

IDEA：直接打开 `targets/forge-1.20.1/` 目录，IDE 会导入该 target 与 `../../common` 源码。

```powershell
# common 单独编译与测试（仓库根）
.\gradlew.bat -p common test

# 构建 target（在 target 目录下）
cd targets\forge-1.20.1
.\gradlew.bat clean build

# 或从仓库根编排构建
.\gradlew.bat build

# 运行客户端 / 测试矩阵（target 目录下）
.\gradlew.bat runClient
.\gradlew.bat testMatrix
```

## 开发规范

### 边界约定

`common/` 只能包含：

- 共享业务逻辑、状态机、队列和不可变 DTO。
- 不引用 target 侧类型的扩展接口、事件语义和渲染计划。
- loader 无关的单元测试。

`common/` 禁止包含：

- 任何 target 侧类的引用；loader 绑定能力一律通过 SPI（`AuiServices`）接口下沉到
  common，由 target 侧实现并注册。
- 运行时 loader/version 判断、反射分发和某个 target 的资源路径。
- 为解决单一 target 编译错误而加入的平台 API 抽象泄漏。

每个 target 是独立 Gradle 根工程，拥有自己的：

- 入口、注册、事件、网络和生命周期代码。
- Minecraft API 调用、Mixin、accessor、渲染后端和 client-only 代码。
- metadata、资源、数据生成和版本范围。
- JDK、Gradle wrapper、加载器和 mappings 配置。

不得把 target 专属类以同一全限定名复制到 `common` 来"覆盖"实现，
也不要依赖 classpath 顺序覆盖 shared class。共享的是语义与数据，不是 Minecraft 对象。

### 变更分类

提交或 PR 必须先选择一种主类别：

| 类别 | 典型路径 | 最小评审 | 最小验证 |
| --- | --- | --- | --- |
| `target-only` | 一个 `targets/<name>/` | 对应 target 维护者 | 该 target `clean build` 与最小运行验证 |
| `common-internal` | `common/`，无接口变化 | Core Maintainer | common 测试 + 所有引用 common 的 target build |
| `common-contract` | `common/` 接口、DTO、语义变化 | Core + 每个受影响 target 维护者 | 所有受影响 target build；行为改动应有测试 |
| `cross-target-feature` | common 加多个 target | Core + 每个改动 target 维护者 | 每个改动 target build；未实现 target 必须明确记录 |
| `build-or-ci` | 根 Gradle、wrapper、CI | Build / Release + 受影响 target 维护者 | 对应 JDK 的完整 job |
| `docs-only` | README、docs | 文档责任人 | 链接和命令检查 |

一个 PR 若同时修改 `common` 和一个 target，不应标记为 `target-only`，
必须说明该 common 改动是否影响其他 target。

### 分支与提交

分支命名：

```text
target/forge-1.20.1/<topic>
common/<topic>
build/<topic>
docs/<topic>
```

提交前缀：

```text
forge-1.20.1: fix entity renderer registration
common: expose render-plan hook
build: update wrapper
docs: clarify release matrix
```

提交应尽量只覆盖一个责任边界。需要同步改动时，先提交 `common` 合约或 DTO，
再提交各 target 适配；不要将不相关格式化混入功能改动。

### 开发流程

**target-only 改动**：只打开自己的 `targets/<name>/`，无需改 common 就不触碰其他 target，
执行该 target 的 `clean build`，PR 中说明 MC 版本、加载器、验证 JDK 和实际测试场景。

**common 改动**：先写清业务语义（不以 Minecraft API 名称描述接口），为 shared logic
添加无 loader 单元测试，由每个受影响 target 维护者更新桥接实现并独立构建。
对不立即适配的 target 禁止静默合并——必须明确选择：同 PR 适配、feature flag 禁用、
或在 issue/roadmap 中记录阻塞。

**跨 target 新功能**推荐提交顺序：

```text
1. common: 新增纯业务模型、状态与稳定接口
2. target A: 实现加载器 / Minecraft 桥接
3. target B: 实现加载器 / Minecraft 桥接
4. docs/tests: 更新支持矩阵与验证记录
```

**渲染改动**默认属于 `target-only`，即使视觉效果在多个版本相同。参数差异用 target
自己的 profile，步骤差异用 hook（其余 target 用 no-op），buffer/shader/事件时机差异
由 target 替换自己的 backend。

### CI 与验证

- CI 不写死版本矩阵：当 common 或构建定义变更触发工作流时，自动扫描 `targets/` 下
  所有目录并构建每个启用的 target。
- 每个 target 必须包含 `gradlew.bat` 和 `ci.properties`（声明 `ci.enabled`、`ci.java`、
  可选 `ci.attempts` 重试次数），二者缺一即发现失败，新增 target 无法绕过验证门禁。
- 执行顺序：先跑 common 单元测试（JDK 21），再并行构建各 target（各用其
  `ci.properties` 声明的 JDK），成功或失败都会上传 `build/libs/*.jar` 供检查。
- 本地命令：

```powershell
# 打印 CI 将使用的矩阵
.\scripts\discover-targets.ps1

# 本地构建单个 target
.\scripts\build-target.ps1 -Target forge-1.20.1
```

- 任何改动 `common/` 的 PR，至少触发所有当前支持 target 的 build job。
- 改动 target 的资源、Mixin 或 metadata 的 PR，至少检查最终 jar 是否包含对应
  metadata、配置文件和 common class。
- 构建成功不代替运行验证：涉及事件、网络、Mixin、注册、渲染或数据包时，应在 PR 中
  记录 client、dedicated server、reload 或 data generation 的实际验证范围。

### 新增与弃用 target

新增 target 必须：

1. 新建 `targets/<loader>-<minecraft-version>/`，不在旧 target 添加运行时版本分支。
2. 提供独立 wrapper、`settings.gradle`、`ci.properties` 和 `../../common` 源码映射。
3. 指定责任人、最低 JDK、Gradle 版本、加载器版本和 CI job。
4. 完成独立构建、client/server 最小验证和最终 jar 检查后，才标记为支持。

弃用 target 前应发布最后一个维护版本，写明最后支持版本、停止接收功能或修复的条件、
迁移目标版本；先从日常 CI 或支持矩阵移除（`ci.enabled=false`），不删除到无法重现历史发布。

### 发布

- 一个发布物只对应一个 loader 和一个 MC 版本，不发布混合 loader 的 universal jar。
- 每个 target 应用共享的 `publish.gradle`，Maven 坐标由 target 推导：

```text
groupId:    mod_group_id
artifactId: <mod_name>-<loader>-<minecraft-version>
version:    mod_version
```

- 版本号含 `SNAPSHOT` 发往 `maven-snapshots`，其余发往 `maven-releases`。
- 凭据只放在发布 shell 或 CI secret 中：

```powershell
$env:SIGHS_PUBLISH_USER = '<username>'
$env:SIGHS_PUBLISH_PASSWORD = '<password>'
```

- 在 target 目录下以其 `ci.properties` 声明的 JDK 执行发布：

```powershell
cd targets\forge-1.20.1
.\gradlew.bat publish
```

- 生成的 POM 刻意省略 target 构建类路径——Minecraft、加载器、mappings 和 common
  由运行时提供，不作为 Maven 依赖暴露。
- 版本可以不同步发布：某个 target 未准备好时不应阻止其他 target 发布，
  但发布说明必须准确表达覆盖范围。
