## 快速开始

### UI类型

晴雪UI可以绘制小地图那样的叠加层(Overlay)、工作台那样的界面(Screen)，或是直接渲染在世界中的影像(Image)。

其中，叠加层是最简单的，你只需要在想要的时机创建或关闭Document即可，Java和JS都一样：
```javascript
Document ApricityUI.createDocument(String path)
Document ApricityUI.removeDocument(String path)
```
方法传入一个HTML文件的字符串路径，创建一个Document并返回，Document被创建完、加载完后就会立刻加入绘制队列。
最简单的例子，你可以在玩家手持弓时，将背包中所有类型的箭及其数量绘制在屏幕右下方。
此外，你也可以使用ApricityUI类中的其他方法来获取当前存在的Document，例如用于读取或修改其它模组提供的UI。
是的，同时可以存在任意数量的Document，只要不互相遮挡就没什么影响，就算有，你也可以手动调整他们的位置。
同一个路径的Document也可以同时存在多个，虽然并不推荐这么做。

而创建界面其实就是创建晴雪UI自带的空白Screen，并为这个Screen绑定一个Document，创建时一起创建，关闭时一起关闭。
你可以在客户端调用这些方法来管理界面：
```javascript
ApricityUI.openScreen(String path)
ApricityUI.closeScreen()
```

如果你只做 UI 预览（无服务端槽位绑定），直接使用上面的 `openScreen(path)` 即可。

如果你需要真实容器与数据源绑定，建议走服务端权威入口。容器信息由模板中的 `<container>` 元素声明，客户端 `openScreen`
会自动提取容器声明并发送到服务端：
```javascript
// 容器信息由模板中的 <container> 元素声明
// 客户端 openScreen 会自动提取并发送到服务端
ApricityUI.openScreen("demo/index.html")
```

其中 `main` / `player` 等容器名必须与模板里的顶层 `<container id="...">` 对应，容器声明由模板驱动。

其它常见容器声明示例（写在模板中）：

```html
<!-- 方块实体背包 -->
<container id="machine" bind="block_entity" size="9" primary="true"></container>
<container id="player" bind="player"></container>

<!-- 实体背包 -->
<container id="entity_inv" bind="entity" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

框架不内置触发器；右键物品、快捷键、右键方块、右键方块实体这些触发逻辑由你自己在事件中编写，再调用上述接口。

`bind="entity"` 需要传入实体 `uuid`；目标实体必须已提供可用物品能力（`ForgeCapabilities.ITEM_HANDLER`），否则绑定会失败。

`bind="player"` 的槽位策略为：

- 使用统一标签 `<slot>`；
- 容器内无 `bound` 槽位时，会隐式注入玩家 36 格（27 背包 + 9 快捷栏）；
- 槽位背景由 `slot` 的 CSS `background-image` 决定，未配置时保持透明。

`container` 没有内建标题机制：

- 不会读取 `title` 属性；
- 不会从首个子元素文本自动推断标题；
- 标题如有需要，请作为普通 DOM 节点自行编写和布局。

统一槽位语义（新模板推荐）：

- 顶层 `container` 内的 `<slot>` 会按索引绑定真实菜单槽位；运行时可通过 `Slot.isBound()` 区分绑定态；
- 不在 `container` 内，或位于 `<recipe>` 预览中的槽位为展示槽位，物品来源只读取 `slot.innerText`（不再读取
  `item/itemid/count/hover` 属性）；
- `mode` 属性仅用于旧模板兼容，新模板不建议依赖；
- `<recipe type="...">recipe_id</recipe>` 生成展示槽位，可放在 `container` 内或普通 HTML 区域；
- `recipe` 的配方 id 只读取 `innerText`（不再读取 `recipe-id` 属性）；
- `recipe.type` 必填并严格校验（不匹配则不渲染预览并写入 `data-recipe-error`）。

`global.css` 槽位变量（可在容器或 slot 层覆盖）：

- `--aui-slot-size`：槽位像素尺寸（整数）；
- `--aui-slot-icon-scale`：图标缩放（浮点）；
- 物品与背景的可见性使用 `render="all|bg|item|none"` 属性控制；
- `--aui-slot-z`：槽位层级（整数）；
- `--aui-slot-interactive`：交互能力 token（`tooltip`、`slot`、`none`；可组合 `tooltip slot`）；
- `--aui-slot-cycle` / `--aui-slot-cycle-interval`：展示槽位轮播开关与间隔；
- `--aui-container-columns`：可选，显式指定容器列数；未设置时由运行时按 `min(9, slotCount)` 注入默认列数。

示例可直接参考：

- `run/kubejs/server_scripts/example.js`（钻石/绿宝石/紫水晶碎片/下界之星触发示例）
- `run/apricity/test/index.html`（纯 UI 虚拟槽位，顶层可无 container）
- `run/apricity/test/saveddata_player.html`（saveddata + playerinv 双容器绑定）
- `run/apricity/test/virtual_container.html`（虚拟容器：有 container，但仅 virtual slot）
- `run/apricity/test/recipe_showcase.html`（recipe 预览展示）

未完待续……
