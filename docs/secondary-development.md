# ApricityUI 二次开发文档

最后更新：2026-08-02

本文面向希望为 ApricityUI 增加自定义元素、KubeJS 绑定或开发诊断能力的模组作者。页面侧的 DOM/Web API 见 [Web API 文档](web-api.md)，内置标签的使用见 [扩展元素文档](extension-elements.md)。

ApricityUI 的扩展点分成两类：

| 扩展点 | 运行位置 | 主要入口 |
| --- | --- | --- |
| 自定义 DOM 元素 | 客户端 Document 解析和渲染 | `@ElementRegister`、`ApricityUIRegistry` |
| KubeJS 全局绑定 | KubeJS 客户端或服务端脚本 | `@KJSBindings`、`KubeJS.scanPackage` |
| 帧耗时诊断 | 客户端渲染 | `debug.frameTimingHud` |

## 1. 开发边界

### 1.1 客户端线程

Document、Element、样式、布局、Screen、Overlay 和 WorldWindow 都属于客户端 UI 状态。扩展代码从网络回调、Future、文件监听器或异步资源任务返回时，应切回 Minecraft 客户端线程：

~~~java
Minecraft.getInstance().execute(() -> {
    Document document = ApricityUI.createDocument("overlays/status.html");
    if (document != null && document.body != null) {
        document.body.setTextContent("ready");
    }
});
~~~

不要在异步线程直接调用 `setAttribute`、`appendChild`、`refresh` 或 `WorldWindow` 的位置/显示 API。资源解码可以在异步线程执行，但纹理上传和 DOM 提交由框架切回客户端/渲染线程完成。

### 1.2 刷新代次和引用生命周期

`Document.refresh()` 会重建节点树、样式缓存、脚本状态和绘制列表。扩展保存的 `Element` 引用、事件监听器和布局对象在刷新后都应视为失效。需要处理异步回调时，记录创建时的刷新代次，并在回调中确认：

~~~java
long generation = document.getRefreshGeneration();

Minecraft.getInstance().execute(() -> {
    if (!document.isCurrentGeneration(generation)) return;
    Element element = document.getElementById("status");
    if (element != null) element.setTextContent("loaded");
});
~~~

自定义元素的 `onInitFromDom` 只在一次 DOM 实例转换时调用。构造函数执行时，HTML 属性还没有全部迁移到具体子类；需要读取初始属性时应放进 `onInitFromDom`，而不是依赖构造函数。

## 2. 注册第三方元素

### 2.1 最小实现

第三方元素需要继承 `com.sighs.apricityui.init.Element`，使用 `@ElementRegister` 声明标签，并提供一个 `public (Document)` 构造器：

~~~java
package com.example.mod.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;

@ElementRegister(MyPanel.TAG_NAME)
public final class MyPanel extends Element {
    public static final String TAG_NAME = "MY-PANEL";

    public MyPanel(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        // origin 的 attributes、children 和外部监听器已迁移到当前实例。
        String mode = getAttribute("mode");
        // 根据 mode 初始化扩展状态。
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        // 可以绘制自定义内容，也可以只使用 Element 的 CSS/子节点能力。
        super.drawPhase(poseStack, phase);
    }
}
~~~

HTML 中直接使用注册名，标签大小写不影响解析：

~~~html
<my-panel id="settings" mode="compact">
    <span>Custom element content</span>
</my-panel>
~~~

`@ElementRegister` 的值会按大写标签名写入元素工厂。推荐使用稳定的、带模组前缀的标签名，例如 `EXAMPLE-PANEL`，避免与其他模组或未来 AUI 标签冲突。

### 2.2 扫描包

注解不会自动让任意包立即生效。模组初始化阶段需要把自己的包加入 AUI 的 Forge 扫描范围：

~~~java
import com.sighs.apricityui.registry.ApricityUIRegistry;

public ExampleMod() {
    ApricityUIRegistry.scanPackage("com.example.mod.ui");
}
~~~

多个包可以一次加入：

~~~java
ApricityUIRegistry.scanPackages(
        "com.example.mod.ui",
        "com.example.mod.client.element"
);
~~~

扫描范围是包前缀及其子包。实现使用 Forge 的 `ModFileScanData`，因此只会发现已经被 Forge 扫描到的模组类；它不是对任意运行时 Jar 的 classpath 全盘扫描。`scanPackage` 只登记过滤范围，不会立即创建页面元素。

包扫描应在 AUI 执行客户端元素注册之前完成，通常放在模组构造器或更早的模组初始化阶段。不要等到第一个 Document 创建后才调用，否则已经解析过的页面不会追溯地变成自定义子类。

### 2.3 注册规则和失败行为

- 类必须是 `Element` 的子类；否则记录 `[AUI]` 注册错误并忽略。
- 必须存在可访问的 `public (Document)` 构造器；签名不匹配时实例化失败。
- AUI 会把通用 `Element` 的属性、子节点、UUID、父子关系和外部监听器迁移到具体实例。
- 注册工厂在 HTML 解析阶段执行，不要在构造器中假设属性已经迁移。
- 同一个标签重复注册时，后执行的 `Element.register` 会覆盖前一个工厂；扫描顺序不应作为稳定优先级。
- 自定义元素构造器反射或实例化失败时，框架会记录错误并退回普通 `Element`；`onInitFromDom` 或绘制阶段的异常则应按页面初始化/渲染错误排查，不能依赖自动回退。
- 元素注册不是热重载机制。类加载后新增或改变注册逻辑，需要重新启动客户端；`END` 只重新扫描资源和重建 Document。

### 2.4 只用 DOM/CSS 的元素

如果扩展只需要语义、属性和 CSS，不需要新的绘制管线，可以不覆写 `drawPhase`：

~~~java
@ElementRegister(ExampleBadge.TAG_NAME)
public final class ExampleBadge extends Element {
    public static final String TAG_NAME = "EXAMPLE-BADGE";

    public ExampleBadge(Document document) {
        super(document, TAG_NAME);
    }
}
~~~

页面仍可通过 CSS 设置背景、边框、布局和伪元素；Java 子类负责新增属性解析、事件行为或 DOM 方法。频繁变化的值应通过已有元素属性/文本更新并标记必要的脏区域，不要每帧调用 `Document.refresh()`。

### 2.5 自定义绘制元素

需要原生绘制时覆写 `drawPhase`，遵循现有元素的三个阶段：`SHADOW`、`BODY`、`BORDER`。布局尺寸从 `Box.of(this)`、`getBoundingClientRect()` 或 `getIntrinsicSize()` 获取；绘制前先处理零尺寸和资源未就绪状态。

自定义纹理应复用 Minecraft 的 `ResourceLocation`、AUI 的 `ImageDrawer` 或 `Canvas` 机制，不要在每帧创建 DynamicTexture。资源异步完成后更新内部状态并调用 `document.markDirty(this, ...)`，纹理上传仍要在正确的客户端/渲染线程执行。

## 3. 注册 KubeJS 绑定

### 3.1 声明绑定类

KJS 绑定是静态 Java 类。使用 `@KJSBindings` 后，KubeJS 会把类作为一个全局对象加入脚本：

~~~java
package com.example.mod.kjs;

import com.sighs.apricityui.registry.annotation.KJSBindings;

@KJSBindings(
        value = "ExampleAui",
        modId = "examplemod",
        isClient = true
)
public final class ExampleAuiBindings {
    private ExampleAuiBindings() {
    }

    public static String hello(String name) {
        return "Hello, " + name;
    }
}
~~~

页面脚本中可写：

~~~javascript
console.log(ExampleAui.hello("Apricity"));
~~~

`value` 为空时使用绑定类的简单类名。`modId` 为空表示不检查额外模组；填写后，只有该模组已经加载时才会注册。绑定方法应保持为公开静态方法，并尽量使用 KubeJS/Rhino 能稳定转换的参数和返回值。

### 3.2 扫描包和客户端/服务端隔离

在模组初始化阶段加入绑定包：

~~~java
import com.sighs.apricityui.script.KubeJS;

public ExampleMod() {
    KubeJS.scanPackage("com.example.mod.kjs");
}
~~~

也可以使用：

~~~java
KubeJS.scanPackages(
        "com.example.mod.kjs",
        "com.example.mod.script.bindings"
);
~~~

`isClient` 决定绑定进入哪一侧脚本：

| 注解配置 | 可见脚本 | 适合内容 |
| --- | --- | --- |
| `isClient = true` | 非服务端脚本，通常是客户端脚本 | Document、Screen、Toast、WorldWindow、客户端输入 |
| `isClient = false` | 非客户端脚本，通常是服务端脚本 | 玩家、容器菜单、服务端数据绑定 |

客户端绑定类不要在服务端路径中引用 Minecraft 客户端类。`isClient` 是 KubeJS 注册过滤条件，不是把错误的类依赖变成服务端安全类的替代品；需要侧隔离的实现仍应使用正确的 Forge 客户端包和加载边界。

### 3.3 绑定设计建议

- 绑定类只负责把脚本参数转换为明确的 Java API，不要把复杂 DOM 遍历塞进每帧脚本。
- 创建或修改 Document 前确认运行在客户端线程；服务端绑定只负责发起合法的网络/菜单操作。
- 对资源缺失、Document 已销毁、元素查询为空和刷新后失效引用返回明确的空值或错误。
- 不要把一个需要客户端状态的绑定同时注册为服务端全局对象。
- 绑定全局名应带模组前缀，避免不同模组使用相同 `value`。
- 对外发布的绑定应在文档中标明返回 `null`、`Optional`、数组和异步 Promise 的实际行为。

## 4. 与页面资源和容器的集成

自定义元素可以直接放入普通 Screen、Overlay、Container Screen 或 WorldWindow。宿主不变，元素仍然使用同一个 Document 生命周期：

~~~java
Document document = Document.create("screens/example.html");
if (document == null) return;
Element panel = document.querySelector("example-panel");
if (panel != null) {
    panel.setAttribute("mode", "active");
}
~~~

资源路径继续使用逻辑路径；自定义元素文档中的本地图片、CSS 和字体引用见 [资源管理文档](resource-manager.md)。如果元素使用 `Sprite`、`Texture` 或 `Canvas` 这样的 AUI 扩展标签，见 [扩展元素文档](extension-elements.md)。容器中的 `CONTAINER`、`SLOT` 和 `RECIPE` 还需要服务端声明与菜单绑定，不能只靠自定义元素在客户端伪造槽位，具体见 [容器文档](container.md)。

## 5. frameTimingHud 帧耗时 HUD

### 5.1 开启方式

这是客户端调试配置，默认关闭：

~~~toml
[debug]
frameTimingHud = true
~~~

配置文件通常位于：

~~~text
<Minecraft实例目录>/config/apricityui-client.toml
~~~

也可以在内置 DevTools 的设置对话框中切换 `Frame timing HUD`；修改后的客户端配置会在后续客户端 tick 协调。生产环境不建议长期打开。

### 5.2 HUD 的字段

HUD 固定绘制在左上角，显示最近最多 120 个 AUI 帧样本：

~~~text
max 2.31 ms  min 0.42 ms  avg 0.88 ms  g 12 img 3 imm 1
~~~

| 字段 | 含义 |
| --- | --- |
| `max` | 样本窗口内最大的 AUI 渲染耗时 |
| `min` | 样本窗口内最小的 AUI 渲染耗时 |
| `avg` | 样本窗口内平均的 AUI 渲染耗时 |
| `g` | 最近一帧 Graph 批次 flush 次数 |
| `img` | 最近一帧普通图片批次 flush 次数 |
| `imm` | 最近一帧即时图片绘制次数 |

耗时以毫秒显示。它统计 AUI 文档绘制相关的代码段，不等于 Minecraft 总帧时间、GPU 时间或 FPS；HUD 自身也不报告脚本执行、服务器 tick、网络和其他模组的完整成本。应结合 Minecraft 性能分析器、日志和页面级操作做判断。

### 5.3 用于扩展开发的诊断方法

可以用 HUD 比较“打开页面前/后”“静态页面/动态页面”和“单个扩展元素开关前/后”的变化：

1. 先保持页面状态稳定，等待 HUD 的 120 帧窗口填满；
2. 记录 `avg`、`max` 以及 `g/img/imm`；
3. 只修改一个元素或一个 CSS/动画因素；
4. 再等待一个完整窗口，比较相同交互下的数据。

`g`、`img` 或 `imm` 偏高通常表示批次被频繁打断、图片绘制未合批或使用了即时绘制路径；它们是定位线索，不是单独的性能结论。扩展元素应缓存不变的几何/纹理状态，避免在 `drawPhase` 中解析字符串、创建图片或触发完整布局。

实现类为 `FrameTimingHud`，样本和批次统计由 `RenderBatchStats` 管理。除非扩展正在实现新的渲染宿主，不要直接调用 HUD 的 `beginFrame`/`endFrame`；普通扩展只需要让自己的绘制路径正确标记 Document 脏状态。

## 6. 常见故障

### 自定义标签仍然是普通 Element

检查 `@ElementRegister` 的类是否位于扫描包及其子包、是否提供 `public (Document)` 构造器，以及扫描调用是否早于 AUI 元素注册。已经创建的 Document 不会因为后续加入扫描包而自动转换；重新启动客户端并重新解析页面验证。

### 注册后页面初始化报错

先查看带路径和标签名的 AUI 错误日志。构造器中不要读取初始属性，初始化逻辑放到 `onInitFromDom`；绘制时检查尺寸、资源句柄和 `document` 是否仍然有效。实例化失败时框架会退回普通 Element，页面能出现但扩展行为会缺失。

### KJS 全局对象不存在

确认 KubeJS 已加载、调用了 `KubeJS.scanPackage`、`modId` 对应模组已加载，并且脚本运行侧符合 `isClient`。修改注解或扫描包后需要重新启动 KubeJS/客户端，单纯按 `END` 只重载页面资源。

### HUD 数值忽高忽低

检查是否在同一窗口内打开了 DevTools、资源管理器或大型图片；这些页面会改变 AUI 的绘制批次。使用连续 120 帧的 `avg` 判断趋势，用 `max` 定位尖峰，不要把单帧波动当成固定回归。

## 7. 源码和相关文档

核心注册实现：

~~~text
src/main/java/com/sighs/apricityui/registry/ApricityUIRegistry.java
src/main/java/com/sighs/apricityui/registry/annotation/ElementRegister.java
src/main/java/com/sighs/apricityui/util/ReflectionUtils.java
src/main/java/com/sighs/apricityui/script/KubeJS.java
src/main/java/com/sighs/apricityui/registry/annotation/KJSBindings.java
~~~

渲染诊断实现：

~~~text
src/main/java/com/sighs/apricityui/render/FrameTimingHud.java
src/main/java/com/sighs/apricityui/render/RenderBatchStats.java
src/main/java/com/sighs/apricityui/instance/ApricityUIConfig.java
~~~

相关文档：

- [扩展元素文档](extension-elements.md)
- [ApricityUI 专属 API](apricity-api.md)
- [Web API](web-api.md)
- [资源管理](resource-manager.md)
- [内置 DevTools](devtools.md)
