# 二次开发：自定义元素与 KubeJS 绑定

面向想给 AUI 加东西的模组作者。三个扩展点：自定义 DOM 元素、KubeJS 全局绑定、帧耗时 HUD。页面侧 API 见 [Web API 文档](web-api.md)，内置扩展标签的用法见[扩展元素文档](extension-elements.md)。

## 先守住的两条边界

**客户端线程**：Document、Element、布局、Screen、WorldWindow 全是客户端 UI 状态。从网络回调、Future、异步任务回来时先切线程：

```java
Minecraft.getInstance().execute(() -> {
    Document document = ApricityUI.createDocument("overlays/status.html");
    if (document != null && document.body != null) {
        document.body.setTextContent("ready");
    }
});
```

资源解码可以异步，DOM 提交和纹理上传必须在客户端/渲染线程。

**刷新代次**：`refresh()` 重建整棵树，你存的 Element 引用、监听器全部失效。异步回调存代次、回来验证：

```java
long generation = document.getRefreshGeneration();
Minecraft.getInstance().execute(() -> {
    if (!document.isCurrentGeneration(generation)) return;
    Element element = document.getElementById("status");
    if (element != null) element.setTextContent("loaded");
});
```

## 注册自定义元素

继承 `Element`，加 `@ElementRegister`，提供 `public (Document)` 构造器：

```java
@ElementRegister(MyPanel.TAG_NAME)
public final class MyPanel extends Element {
    public static final String TAG_NAME = "MY-PANEL";

    public MyPanel(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        // 到这里属性、子节点、监听器才迁移完成；构造函数里读不到初始属性
        String mode = getAttribute("mode");
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        // 自定义绘制；阶段分 SHADOW / BODY / BORDER
        super.drawPhase(poseStack, phase);
    }
}
```

然后在模组初始化阶段（构造器或更早）登记扫描包：

```java
ApricityUIRegistry.scanPackage("com.example.mod.ui");
// 或 scanPackages("com.example.mod.ui", "com.example.mod.client.element");
```

要点和坑：

- 标签名按大写注册、大小写不敏感，**带模组前缀**（`EXAMPLE-PANEL`），避免撞车；同标签重复注册后者覆盖前者，扫描顺序不是稳定优先级；
- 扫描靠 Forge `ModFileScanData`，只覆盖 Forge 扫到的模组类；必须在 AUI 元素注册前调用——第一个 Document 创建后再登记，已解析的页面不会追溯转换；
- 构造器里别读属性，初始化放 `onInitFromDom`；实例化失败会退回普通 Element（页面还在，扩展行为没了），但 `onInitFromDom` 和绘制里的异常没有这种兜底；
- 元素注册不是热重载，改注册逻辑要重启客户端，END 只重扫资源；
- 不需要自定义绘制的元素不用覆写 `drawPhase`，CSS 照常生效；
- 自定义绘制时：尺寸从 `Box.of(this)` / `getBoundingClientRect()` 取；先处理零尺寸和资源未就绪；别每帧创建 DynamicTexture、解析字符串、触发布局；资源异步就绪后更新内部状态并 `document.markDirty(this, ...)`。

## 注册 KubeJS 绑定

静态方法类加 `@KJSBindings`，类作为全局对象进脚本：

```java
@KJSBindings(value = "ExampleAui", modId = "examplemod", isClient = true)
public final class ExampleAuiBindings {
    private ExampleAuiBindings() {}

    public static String hello(String name) {
        return "Hello, " + name;
    }
}
```

```javascript
// 页面脚本里
console.log(ExampleAui.hello("Apricity"));
```

模组初始化时登记：`KubeJS.scanPackage("com.example.mod.kjs")`。

- `value` 空则用简单类名；`modId` 填了则该模组加载时才注册；
- `isClient = true` 进客户端脚本（Document/Toast/WorldWindow 这类），`false` 进服务端脚本（容器、玩家数据）——它是注册过滤条件，**不是**侧安全保证：客户端绑定类引用了 MC 客户端类的话，别把它注册到服务端；
- 全局名带模组前缀；返回值用 null/Optional 表达失败并在文档里写清楚；
- 绑定方法保持公开静态，参数和返回值用 Rhino 能稳定转换的类型；别把复杂 DOM 遍历塞进脚本每帧调用。

改注解或扫描包后要重启 KubeJS/客户端，END 只重载页面资源。

## frameTimingHud：帧耗时 HUD

`config/apricityui-client.toml`：

```toml
[debug]
frameTimingHud = true
```

左上角显示最近 120 个 AUI 帧样本：

```text
max 2.31 ms  min 0.42 ms  avg 0.88 ms  g 12 img 3 imm 1
```

`max/min/avg` 是 AUI 文档渲染耗时；`g`/`img`/`imm` 是最近一帧 Graph 批次、图片批次、即时绘制的 flush 次数。它只统计 AUI 绘制段，不等于总帧时间或 FPS，也不含脚本执行成本。

用法：保持页面稳定等窗口填满 → 记 `avg`/`max` 和批次计数 → 只改一个变量 → 再比。`g/img/imm` 偏高说明批次被打断或没合批，是定位线索不是结论。扩展元素要缓存不变的几何/纹理状态，别在 `drawPhase` 里做重活。

## 常见故障

**自定义标签还是普通 Element**：类在扫描包及其子包里吗？`public (Document)` 构造器有吗？scanPackage 调得够早吗？改完重启客户端验证。

**注册后页面初始化报错**：看带路径和标签名的 AUI 错误日志。属性初始化是不是写构造器里了——挪到 `onInitFromDom`。绘制时检查尺寸、资源句柄、document 有效性。

**KJS 全局对象不存在**：KubeJS 加载了吗？scanPackage 调了吗？modId 对应模组在吗？脚本运行侧和 `isClient` 匹配吗？改完重启。

**HUD 数值忽高忽低**：DevTools、资源管理器这类内置页面本身会改变批次。用连续 120 帧的 avg 看趋势、max 找尖峰，别拿单帧波动当回归。
