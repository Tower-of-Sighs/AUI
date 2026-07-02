src/main/java/com/sighs/apricityui/event/Test.java这个是调试用的类，会默认打开run/apricity/apricityui/example3.html这个页面。
这个html在浏览器里打开的样子是run/apricity/apricityui/example3.png这张图，也就是目标效果图，html本身设计没有问题，我需要让它在本框架中也能有这个效果。
本框架用于模拟web，但底层设计不够完整，你需要从web标准的角度出发，补齐、修正相关的功能，让html能正常显示。需要注意，由于这是mc中的框架，根视口尺寸只有427x249，你可以调整html来适应，但除此之外不要改动html的内容，只能改动java，也禁止去调整整体缩放，我不需要正确的分辨率。
你用调试类进行自动化调试，先用./gradlew.bat runClient直接启动游戏，启动后三秒再自动关闭游戏，这期间本模组会每秒自动截图，存放在run/screenshots/aui目录中，你等游戏自动关闭后去看最新的截图，然后比对目标效果图run/apricity/apricityui/example3.png，判断样式是否正常了，如果仍有问题，就针对性修复，然后继续调试。
注意，你需要始终从浏览器标准的角度去看问题，因为本框架要尽可能向浏览器标准看齐，只有在适应性缩放到根视口尺寸时能调整html，否则只能改动java。
目前能看到的问题：元素拥挤、按钮中文字的渲染位置有的太靠上有的太靠下。
最终目标是让该页面与目标效果图完全一致，保证细节质量。