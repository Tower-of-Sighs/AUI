## 目前支持的内容

这些是目前支持的内容，使用这些内容就好了，不要使用太复杂的东西。

### CSS属性

只有以下属性是可以使用的，其它的属性都还未得到支持，请勿使用。

width
height
overflow
opacity
box-shadow
z-index
background-color

border
border-top
border-bottom
border-left
border-right
margin
margin-top
margin-bottom
margin-left
margin-right
padding
padding-top
padding-bottom
padding-left
padding-right
border-radius

color
font-size
font-family
line-height

display(仅支持flex和none)
flex-direction
align-content
justify-content
align-items

top
bottom
left
right
position

pointer-events
visibility
transition
transform

animation
animation-name
animation-duration
animation-delay
animation-iteration-count
animation-direction
animation-fill-mode

@font-face
@keyframes

### Document类

body: Body - 文档的body元素（特殊的根元素）

static create(String path) - 静态方法，创建新文档并添加到文档列表
static remove(String path) - 静态方法，移除指定路径的文档
refresh()- 重新加载和刷新文档内容
remove() - 实例方法，移除当前文档
static refreshAll() - 静态方法，刷新所有文档
static get(String path) - 静态方法，获取指定路径的所有文档
static getAll() - 静态方法，获取所有文档
getPath() - 获取文档路径
createElement(String tagName) - 创建新元素
getElementById(String id) - 通过ID获取元素
getElements() - 获取文档中所有元素的列表

### Element类

uuid: UUID - 元素的唯一标识符
tagName: String - 元素标签名（大写）
innerText: String - 元素文本内容
id: String - 元素ID
document: Document - 所属文档对象
parentElement: Element - 父元素
children: ArrayList<Element> - 子元素列表
style: Style - 内联样式
scrollWidth: double - 滚动宽度
scrollHeight: double - 滚动高度
scrollLeft: double - 水平滚动位置
scrollTop: double - 垂直滚动位置

getRoute() - 获取该元素到根元素的路径
prepend(Element) - 在头部插入子元素
append(Element) - 在末尾添加子元素
querySelector(String) - 查找匹配的第一个子孙元素
querySelectorAll(String) - 查找所有匹配的子孙元素
getAttribute(String) - 获取属性值
setAttribute(String, String) - 设置属性
addEventListener(String, Consumer<Event>) - 添加事件监听器
addEventListener(String, Consumer<Event>, boolean) - 添加事件监听器（带捕获参数）
removeEventListener(String, Consumer<Event>, boolean) - 移除事件监听器

getComputedStyle() - 获取完整样式

### 事件

mousedown
mouseup
mouseenter
mouseout
mousemove

load
unload

focus
blur