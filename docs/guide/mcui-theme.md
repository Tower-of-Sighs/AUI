# McUI 主题

McUI 是独立的纯 CSS 主题，遵守[内置主题规范](../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/theme-spec.md)。它与 Ore 使用相同的通用组件类、状态类和 token 名称。业务页面切换主题时只需换样式表与根作用域类，DOM 结构和交互代码保持不变。

    <link rel="stylesheet" href="/apricityui/theme/mcui/mcui.css">
    <body class="mcui-theme">

主题入口是 apricityui/theme/mcui/mcui.css，字体随主题本地提供。主题本身不依赖 Vue，也不引入浏览器内核。样式表遵守 MPL-2.0；随附的 McUI 字体保留 MIT 归属，许可文件位于同目录。

游戏内打开 apricityui/theme/mcui/example.html 可查看七页纯 CSS 组件总览。另可打开 apricityui/theme/mcui/vue-example.html 查看固定于上游 2.0.0 的 68 组件视觉 Gallery；该页加载独立的 `/apricityui/runtime/mcui/` Vue 组件库和 `.mc-theme`，不是 `mcui.css` 的换肤示例。纯 CSS 页面只加载上面的主题文件。
