晴雪UI可以绘制小地图那样的叠加层(Overlay)、工作台那样的界面(Screen)，或是直接渲染在世界中的影像(Image)。

### 渲染层

叠加层是最简单的，你只需要在想要的时机创建或关闭Document即可，Java和JS都一样：
```javascript
Document ApricityUI.createDocument(String path)
Document ApricityUI.removeDocument(String path)
```
方法传入一个HTML文件的字符串路径，创建一个Document并返回，Document被创建完、加载完后就会立刻加入绘制队列。
最简单的例子，你可以在玩家手持弓时，将背包中所有类型的箭及其数量绘制在屏幕右下方。
此外，你也可以使用ApricityUI类中的其他方法来获取当前存在的Document，例如用于读取或修改其它模组提供的UI。
是的，同时可以存在任意数量的Document，只要不互相遮挡就没什么影响，就算有，你也可以手动调整他们的位置。
同一个路径的Document也可以同时存在多个，虽然并不推荐这么做。

## 界面

而创建界面其实就是创建晴雪UI自带的空白Screen，并为这个Screen绑定一个Document，创建时一起创建，关闭时一起关闭。
你可以在客户端调用这些方法来管理界面：
```javascript
ApricityUI.openScreen(String path)
ApricityUI.closeScreen()
```

如果你只做 UI 预览（无服务端槽位绑定），直接使用上面的 `openScreen(path)` 即可。

如果你需要真实容器与数据源绑定，建议走服务端权威入口（Java 或 KubeJS 服务端事件中调用）：
```javascript
OpenBindPlan plan = ApricityUI.bind()
    .primaryBind("main").savedData("apricityui_demo", "demo_key", 27)
    .bind("player").player()
    .build()

ApricityUI.openScreen(ServerPlayer player, String path, OpenBindPlan plan)
```

其中 `main` / `player` 必须与模板里的顶层 `<container id="...">` 对应。

其它常见绑定方式：

```javascript
// 方块实体背包
OpenBindPlan blockEntityPlan = ApricityUI.bind()
    .primaryBind("machine").blockEntity(100, 64, 200, "up")
    .bind("player").player()
    .build()

// 实体背包（按 uuid）
OpenBindPlan entityPlan = ApricityUI.bind()
    .primaryBind("entity_inv").entity("00000000-0000-0000-0000-000000000000")
    .bind("player").player()
    .build()
```

框架不内置触发器；右键物品、快捷键、右键方块、右键方块实体这些触发逻辑由你自己在事件中编写，再调用上述接口。

`global.css` 默认变量（可在容器或 slot 层覆盖）：

- `--aui-slot-size`：槽位像素尺寸（整数）；
- `--aui-slot-render-bg`：是否渲染槽位背景（1/0）；
- `--aui-slot-render-item`：是否渲染物品（1/0）；
- `--aui-slot-icon-scale`：图标缩放（浮点）；
- `--aui-slot-padding`：图标内边距（整数）；
- `--aui-slot-z`：槽位层级（整数）；
- `--aui-slot-interactive`：是否允许交互（1/0）；
- `--aui-slot-cycle` / `--aui-slot-cycle-interval`：virtual 槽位轮播开关与间隔；
- `--aui-container-columns`：可选，显式指定容器列数；未设置时由运行时按 `min(9, slotCount)` 注入默认列数。

补充说明：

- 已配置背景图的 `slot` 默认会按当前槽位盒子尺寸拉伸背景，放大槽位时无需再额外补 `background-size`。

### 影像
