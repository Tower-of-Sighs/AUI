# ApricityUI

Design UI with HTML, CSS, and maybe JavaScript along.

通过经典的H5三剑客构建Minecraft的UI。

## 构建（SighsTemple 框架）

本项目采用 `common + targets/<loader>-<minecraft-version>` 多加载器框架结构：

- `common/`：共享源码树（Java、测试、loader 无关资源）。可**单独编译并跑测试**
  （`gradlew -p common test`，见 `common/build.gradle`），不引用任何 target 侧类；
  loader 绑定能力经 `com.sighs.apricityui.spi.AuiServices` 由 target 侧实现注册。
- `targets/forge-1.20.1/`：Forge 1.20.1 目标，独立 Gradle 工程（自己的 wrapper 与配置）。
  加载器 metadata（`META-INF/mods.toml`）、mixin 类与注册、@Mod 入口（`ApricityUIForge`）
  保留在 target 中。

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

共享资源放在 `common/src/main/resources/`，构建 target 时会合并进最终 jar。
target 自身 `libs/` 目录中的 `*.jar` 会自动作为 `implementation` 依赖。
发布（Maven / CurseForge / Modrinth）见 `docs/PUBLISHING.md`。

### 计划：
- 简单的svelte
- 审查元素的调试台，能改attribute、innerText和行内css
- 能输入js的控制台，调用eval
- 高优先级css属性，flex-wrap、text-shadow
- 伪元素
- transition 完全适配和transition非线性动画
- input溢出指示器（拓展功能，overflow-indicator属性）
- 生命周期
- svg？
- markdown会用到的标签，如ul、ol、a
- 完整弹性布局
- 资源管理器预览

### 优化：
- 精灵图字体，但老实说不太可能
- updateCSS的时候预先算好这个元素在hover的时候需不需要updateCSS，active、focus同理

### 待修：
- active不支持背景图片

## 开发须知
1. 不可直接向 master 分支提交 commit.
2. 提交修改应该新建分支，分支名应提现对内核/MC内容的关联，建议的分支命名规范：
  - 优化 Optimize：opt(core)/html-parser
  - 修复错误 Fix：fix(mc)/time-format
  - 功能 Feature：feat(core+mc)/editor-markdown
  - 重构 Refactor：refactor(core)/textarea-render
3. 从其他分支合并修改到 master 分支时请发起一个 PR (Pull Request).
4. 分支合并前会有人来帮助你检查代码中的错误，通过审查后会合并到主线。
5. 如果你的分支（以下以 a 表示）与 master 分支冲突，解决方案如下：
  - 从 master 分支建立一个新分支（以下以 b 表示）
  - 通过 cherry-pick 指令将你的修改从 a 分支移动过来
  - 删除本地 a 分支
  - 将 本地 b 分支重命名成 a
  - git push (--force)
