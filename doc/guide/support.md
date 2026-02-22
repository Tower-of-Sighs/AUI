# ApricityUI 使用说明

让AI跑的，但写得还行，先留着。

特别注意：本框架当前支持 Flex 与 Grid 布局，Slot 网格布局已切换为 CSS Grid 语义。

## 一、CSS 属性说明

```css
div {
  /* 常见布局属性 */
  width: 200px;          /* 设置宽度，支持 px, %, auto 等 */
  height: 100px;         /* 设置高度 */
  margin: 10px;          /* 外边距，支持单值（全部方向）或 4 值（上右下左） */
  padding: 15px;         /* 内边距 */
  border: 1px solid #000; /* 边框，支持样式、宽度、颜色 */
  border-radius: 5px;    /* 圆角 */
  position: relative;    /* 位置模式：static, relative, absolute, fixed */
  top: 20px;             /* 与顶部的距离 */
  left: 20px;            /* 与左侧的距离 */
  z-index: 10;           /* 层叠顺序 */

  /* 布局方式 */
  display: flex;         /* 布局方式：flex, grid, none */
  flex-direction: row;   /* flex 布局方向：row, column */
  align-content: center; /* flex 布局内容对齐：flex-start, center, flex-end */
  justify-content: center; /* flex 布局主轴对齐：flex-start, center, flex-end */
  align-items: center;   /* flex 布局交叉轴对齐：flex-start, center, flex-end */
  grid-template-columns: 9; /* grid 列轨道：数字（自动等宽列）或 px/auto 列表 */
  grid-template-rows: auto; /* grid 行轨道：数字或 px/auto 列表 */
  gap: 2px;              /* grid 行列间距简写 */
  row-gap: 2px;          /* grid 行间距 */
  column-gap: 2px;       /* grid 列间距 */
  justify-items: stretch; /* grid 容器内子项横向对齐 */
  justify-self: center;  /* grid 子项横向自对齐 */
  align-self: center;    /* grid 子项纵向自对齐 */
  grid-column: 1 / span 2; /* grid 子项列定位 */
  grid-row: 1;           /* grid 子项行定位 */

  /* 文本属性 */
  color: #333;           /* 文本颜色 */
  font-size: 16px;       /* 字体大小 */
  font-family: "Arial", sans-serif; /* 字体 */
  line-height: 1.5;      /* 行高 */
  text-align: center;    /* 文本对齐：left, center, right, justify */

  /* 背景属性 */
  background-color: #f0f0f0; /* 背景颜色 */
  background-image: url("image.png"); /* 背景图片 */
  background-repeat: no-repeat; /* 背景重复：repeat, no-repeat, repeat-x, repeat-y */
  background-size: cover; /* 背景大小：auto, cover, contain */
  background-position: center; /* 背景位置：top, left, center, right, bottom */

  /* 其他属性 */
  opacity: 0.8;          /* 透明度（0-1） */
  box-shadow: 0 2px 4px rgba(0,0,0,0.2); /* 阴影：x-offset y-offset size color */
  pointer-events: auto;  /* 指针事件：auto, none */
  visibility: visible;   /* 可见性：visible, hidden */
  overflow: hidden;      /* 溢出处理：visible, hidden, scroll, auto */
  transition: all 0.3s;  /* 过渡效果：属性 时长 时延 缓动函数 */
  transform: translateX(10px); /* 变换：translate, rotate, scale */
  clip-path: polygon(50% 0%, 0% 100%, 100% 100%); /* 裁剪路径：polygon, circle, ellipse, inset */
  filter: blur(2px);     /* 滤镜：blur, brightness, contrast, grayscale, invert, opacity, huerotate */
}
```

## 二、Element 类常用接口

### 1. 创建和设置元素

```java
// 创建元素
Element div = document.createElement("div");

// 设置属性
div.setAttribute("id", "myDiv");
div.setAttribute("class", "container");
div.setAttribute("style", "width: 200px; height: 100px; background-color: #f0f0f0;");

// 设置文本内容
div.innerText = "Hello, World!"; // 或者 div.setAttribute("innerText", "Hello, World!");

// 设置值（用于输入元素）
if (div.tagName.equals("INPUT")) {
    div.setValue("Input value");
}
```

### 2. 事件处理

```java
// 添加事件监听
div.addEventListener("click", event -> {
    System.out.println("Div clicked!");
    event.stopPropagation(); // 阻止事件冒泡
});

// 移除事件监听
div.removeEventListener("click", event -> { /* ... */ }, false);
```

### 3. 操作元素

```java
// 添加子元素
Element child = document.createElement("div");
child.innerText = "Child";
div.appendChild(child); // 添加到末尾

Element firstChild = document.createElement("div");
firstChild.innerText = "First Child";
div.prepend(firstChild); // 添加到开头

// 移除元素
div.removeChild(child);

// 选择子元素
Element childElement = div.querySelector(".child");
List<Element> allChildren = div.querySelectorAll("div");
```

### 4. 获取和修改样式

```java
// 获取计算样式
Style style = div.getComputedStyle();

// 修改样式
div.setAttribute("style", "color: red; font-size: 20px;");

// 通过计算样式修改
style.color = "red";
style.fontSize = "20px";
div.updateCSS(); // 应用修改
```

## 三、Document 类常用接口

### 1. 创建和加载文档

```java
// 创建新文档
Document document = Document.create("my_page.html");

// 从 HTML 字符串创建元素
Element element = document.createHTML("<div class='container'>Hello</div>");

// 创建世界中的文档（用于游戏内 UI）
Document inWorldDocument = Document.createInWorld("ui.html");
```

### 2. 文档操作

```java
// 获取文档元素
Element body = document.body; // 文档根元素
Element div = document.querySelector("div"); // 选择第一个 div
List<Element> allDivs = document.querySelectorAll("div"); // 选择所有 div

// 添加元素到文档
Element newDiv = document.createElement("div");
newDiv.innerText = "New element";
document.body.appendChild(newDiv);

// 刷新文档（基本用不到）
document.refresh();
```

### 3. 本地存储

```java
// 本地存储（类似 localStorage）
Window.localStorage.localStorage.putString("username", "john_doe");
String username = Window.localStorage.localStorage.getString("username", "guest");
```

## 四、Window 类常用接口

### 1. 本地存储

```java
// 本地存储
Window.localStorage.localStorage.putString("theme", "dark");
String theme = Window.localStorage.localStorage.getString("theme", "light");
```

### 2. 定时器

```java
// 设置定时器（2秒后执行）
Window.setTimeout(cancellable -> {
    System.out.println("This is a timeout!");
}, 2000);

// 设置间隔定时器（每 1 秒执行）
Window.setInterval(cancellable -> {
    System.out.println("This is an interval!");
}, 1000);
```

## 五、基本使用示例

```java
// 创建文档
Document document = Document.create("main.html");

// 创建根元素
Element body = document.body;
body.setAttribute("style", "display: flex; justify-content: center; align-items: center; height: 100vh;");

// 创建一个按钮
Element button = document.createElement("button");
button.innerText = "Click Me!";
button.setAttribute("style", "padding: 10px 20px; background-color: #4CAF50; color: white; border-radius: 4px;");

// 添加点击事件
button.addEventListener("click", event -> {
    System.out.println("Button clicked!");
    button.setAttribute("style", "background-color: #45a049;"); // 修改样式
    Window.setTimeout(cancellable -> {
        button.setAttribute("style", "background-color: #4CAF50;"); // 恢复样式
    }, 500);
});

// 添加按钮到文档
body.appendChild(button);
```
