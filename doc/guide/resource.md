### 静态资源

晴雪UI支持的静态资源有HTML、CSS、图片、字体等，未来还会想办法支持音频和视频。

最传统的静态资源加载方式是资源包，模组自带的资源包或手动打包的资源包。
路径：apricityui/apricity/...
例如apricityui/apricity/modid/index.html，可以通过ApricityUI.createDocument("modid/index.html")加载并创建Document。

但更推荐另一种方式，游戏实例文件夹下会自动生成一个apricity文件夹，apricity/modid/index.html可以做到同等的效果。
本地文件夹中的静态资源会资源包中覆盖等效路径静态资源，如apricity/modid/index.html会覆盖掉资源包中的apricityui/apricity/modid/index.html，这样设计是方便整合包作者魔改。
此外，通过快捷键END可以立即完全重载本地文件夹中的所有静态资源，并重载所有Document，因此本地文件夹更适合调试和魔改。

在开发环境下，默认还会读取src/main/resources/assets/apricityui/apricity路径，并且优先级最高。

静态资源之间的互相调用采取的是相对路径的形式，vsc或idea这类编辑器可以帮你补全可选文件列表，非常方便。
在HTML中加载同文件夹下图片的例子：
```html
<img src="loading.gif" />
```
在CSS中加载当前目录中apricityui文件夹下字体的例子：
```css
@font-face {
    font-family: "lxgw";
    src: url("apricityui/lxgw3500.ttf");
}
```

晴雪UI内置落霞孤鹜字体(font-family:lxgw)，开箱即用，不过为了缩小体积只提取了3500常用字和大部分常用字符。
如果你想问能不能多种字体并存，答案是当然可以！