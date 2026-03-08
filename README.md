# ApricityUI

Design UI with HTML, CSS, and maybe JavaScript along.

通过经典的H5三剑客构建Minecraft的UI。

### 计划：
- 简单的svelte
- 审查元素的调试台，能改attribute、innerText和行内css
- 能输入js的控制台，调用eval
- 网络通讯的轮子，简化发包
- 高优先级css属性，flex-wrap、text-shadow
- 伪元素
- transition 完全适配和transition非线性动画
- input溢出指示器（拓展功能，overflow-indicator属性）
- 冷门css属性如border-width-left
- 支持多重叠加的属性：多重背景、多重动画
- 生命周期
- svg？
- markdown会用到的标签，如h系列、p、ul、ol、a、hr
- 主页小图标

### 优化：
- 批处理
- 无圆角遮罩用便宜遮罩方案
- 精灵图字体，但老实说不太可能
- updateCSS的时候预先算好这个元素在hover的时候需不需要updateCSS，active、focus同理

### 待修：
- backdrop-filter
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
