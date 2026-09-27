# McUI 主题

入口为 `mcui.css`，页面根节点使用 `.mcui-theme`。本主题实现
`../theme-spec.md` 的共享组件类、状态、变量和两个响应式断点；切换主题时，
业务页面仅替换样式表与根作用域类，不依赖 Vue 或专用元素。

```html
<link rel="stylesheet" href="/apricityui/theme/mcui/mcui.css">
<body class="mcui-theme">
```

正文和标题分别使用随主题提供的 Minecraft Seven 与 Minecraft Ten 字体。
样式表从项目的 Ore 主题契约样式改编，适用 MPL-2.0（`license.txt`）；
字体来自 mcui-oreui，保留 MIT 归属（`third-party-license.txt`）。

`example.html` 是七页纯 CSS 主题总览，和 Ore 展示页使用同一组件结构；
只需替换样式表和根作用域类即可切换主题。
