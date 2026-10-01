package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 布局一致性探针：把下面的小用例用引擎排一遍，把每个元素的几何写成 JSON，
 * 同时把 HTML 落盘，交给 {@code tools/page-diff/capture-probe.mjs} 在真实 Chromium
 * 里跑同一份 HTML，再用 {@code compare-dom.mjs} 对差。
 *
 * <p>用例本身刻意做到最小：每个文件只验证一条 CSS 行为，这样对差结果能直接
 * 指回某条规则，而不用在整页里猜。</p>
 *
 * <p>默认跳过（普通 {@code gradlew test} 不受影响）。开启方式：</p>
 * <pre>./gradlew -p targets/neoforge-1.21.1 test --tests '*LayoutParityProbeTest*' -PauiLayoutProbeOut=&lt;目录&gt;</pre>
 */
class LayoutParityProbeTest {
    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    /** 用例名 -> 页面源码。命名与 tools/page-diff/ 下的浏览器产物一一对应。 */
    static String source(String name) {
        for (Case testCase : cases()) {
            if (testCase.name().equals(name)) return testCase.source();
        }
        throw new IllegalArgumentException("unknown layout parity case: " + name);
    }

    /**
     * 用例名 -> 页面源码。命名与 tools/page-diff/ 下的浏览器产物一一对应。
     *
     * <p>刻意不写文字：两个渲染器的字体与行高不可能逐像素一致，掺进文字后几何对差
     * 就被字体度量噪声淹没，看不出真正的布局问题。需要文字的地方用固定尺寸的空盒子代替。</p>
     */
    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();

        cases.add(new Case("flex-row-wrap-order", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .card { display: flex; flex-direction: row; flex-wrap: wrap; gap: 5px 8px;
                        width: 340px; padding: 10px 12px; border: 3px solid #444; }
                .head { order: 1; flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 8px; }
                .head .title { flex: 1 1 auto; min-width: 0; height: 18px; }
                .badge { flex: 0 0 auto; width: 20px; height: 18px; }
                .clock { order: 2; flex: 0 0 auto; width: 70px; height: 18px; }
                .meta { order: 3; flex: 1 0 100%; margin: 0; height: 14px; }
                </style></head><body>
                <article class="card" id="card">
                  <div class="head" id="head"><span class="badge"></span><div class="title"></div></div>
                  <p class="meta" id="meta"></p>
                  <span class="clock" id="clock"></span>
                </article>
                </body></html>
                """));

        cases.add(new Case("flex-basis-percent", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .outer { width: 700px; }
                .box { display: flex; flex-wrap: wrap; width: 300px; }
                .a { width: 40px; height: 16px; }
                .b { flex: 1 0 100%; height: 16px; }
                .c { width: 40px; height: 16px; }
                </style></head><body>
                <div class="outer"><div class="box">
                  <div class="a" id="a"></div><div class="b" id="b"></div><div class="c" id="c"></div>
                </div></div>
                </body></html>
                """));

        cases.add(new Case("flex-basis-auto-grow", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .box { display: flex; width: 300px; }
                .auto { flex: 1 1 auto; min-width: 0; height: 16px; }
                .fixed { flex: 0 0 auto; width: 80px; height: 16px; }
                </style></head><body>
                <div class="box"><div class="auto" id="auto"></div><div class="fixed" id="fixed"></div></div>
                </body></html>
                """));

        cases.add(new Case("pseudo-tree-lines", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .tree-chart { display: flex; padding: 8px 0; }
                .tree-branch { margin: 0; padding: 0; list-style: none; display: flex; }
                .tree-node { position: relative; display: flex; flex-direction: column; align-items: center;
                             padding: 24px 0 0; }
                .tree-node::before { content: ""; position: absolute; top: 0; right: 50%; width: 50%; height: 4px; background: #4b4d51; }
                .tree-node::after { content: ""; position: absolute; top: 0; left: 50%; width: 50%; height: 24px;
                                    border-top: 4px solid #4b4d51; border-left: 4px solid #4b4d51; }
                .tree-node:first-child::before { display: none; }
                .tree-node:last-child::after { border-top: 0; }
                .tree-node > .tree-branch { margin-top: 24px; }
                .tree-node > .tree-branch::before { content: ""; position: absolute; left: 50%; top: -24px; width: 4px; height: 24px; background: #4b4d51; }
                .tree-chart > .tree-branch > .tree-node { padding-top: 0; }
                .tree-chart > .tree-branch > .tree-node::before,
                .tree-chart > .tree-branch > .tree-node::after { display: none; }
                .leaf { width: 100px; height: 24px; background: #333; }
                </style></head><body>
                <div class="tree-chart"><ul class="tree-branch">
                  <li class="tree-node"><div class="leaf"></div>
                    <ul class="tree-branch">
                      <li class="tree-node"><div class="leaf"></div></li>
                      <li class="tree-node"><div class="leaf"></div></li>
                      <li class="tree-node"><div class="leaf"></div></li>
                    </ul>
                  </li>
                </ul></div>
                </body></html>
                """));

        cases.add(new Case("transform-scale-centering", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .canvas { position: relative; width: 400px; height: 200px; overflow: hidden; border: 3px solid #444; }
                .world { position: absolute; left: 0; top: 0; transform-origin: 0 0; width: 800px; height: 400px; }
                .fill { width: 100%; height: 100%; background: #333; }
                </style></head><body>
                <div class="canvas" id="canvas">
                  <div class="world" id="world" style="transform: translate(20px,20px) scale(0.5)"><div class="fill"></div></div>
                </div>
                </body></html>
                """));

        cases.add(new Case("overflow-hidden-clip", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .clip { position: relative; width: 200px; height: 100px; overflow: hidden; border: 2px solid #444; }
                .inner { position: absolute; left: 150px; top: 50px; width: 100px; height: 100px; background: #333; }
                </style></head><body>
                <div class="clip"><div class="inner" id="inner"></div></div>
                </body></html>
                """));

        cases.add(new Case("box-metrics", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .pad { padding: 10px; border: 3px solid #444; width: 200px; height: 100px; }
                .inner { width: 50px; height: 50px; }
                </style></head><body>
                <div class="pad" id="pad"><div class="inner" id="inner"></div></div>
                </body></html>
                """));

        // 同上，但标记里不留空白文本节点：用来区分"空白文本成了匿名 flex 项"这类差异。
        cases.add(new Case("flex-row-wrap-order-tight", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .card { display: flex; flex-direction: row; flex-wrap: wrap; gap: 5px 8px;
                        width: 340px; padding: 10px 12px; border: 3px solid #444; }
                .head { order: 1; flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 8px; }
                .head .title { flex: 1 1 auto; min-width: 0; height: 18px; }
                .badge { flex: 0 0 auto; width: 20px; height: 18px; }
                .clock { order: 2; flex: 0 0 auto; width: 70px; height: 18px; }
                .meta { order: 3; flex: 1 0 100%; margin: 0; height: 14px; }
                </style></head><body><article class="card"><div class="head"><span class="badge"></span><div class="title"></div></div><p class="meta"></p><span class="clock"></span></article></body></html>
                """));

        cases.add(new Case("text-nowrap", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; font-size: 12px; line-height: 20px; }
                .nowrap { width: 100px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                .clip { width: 100px; overflow: hidden; text-overflow: ellipsis; }
                </style></head><body>
                <div class="nowrap" id="nowrap">a b c d e f g h i j k l m n o p q r</div>
                <div class="clip" id="clip">a b c d e f g h i j k l m n o p q r</div>
                </body></html>
                """));

        // 行高与 overflow 组合：除 html/body 的视口高度外应当全部一致。
        // 注意 h4 那一条是本引擎 global.css（内置 UA 表）给标题写了 line-height:1.2，
        // Chromium 的 UA 表不写 line-height，所以"作者只给了 font-size、行高靠继承"时
        // 两边必然不同——直接命中标题的 UA 规则优先于继承来的作者值，这是标准行为。
        // 页面只要像 ore 主题那样显式给标题 line-height，两边就一致。
        cases.add(new Case("text-line-height", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; font-size: 12px; line-height: 20px; }
                div { width: 200px; }
                .b { white-space: nowrap; }
                .c { white-space: nowrap; overflow: hidden; }
                .d { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                .e { overflow: hidden; }
                h4 { margin: 0; font-size: 18px; }
                .big { font-size: 18px; }
                .mid { font-size: 18px; line-height: 20px; }
                </style></head><body>
                <div class="a" id="a">meta</div>
                <div class="b" id="b">meta</div>
                <div class="c" id="c">meta</div>
                <div class="d" id="d">meta</div>
                <div class="e" id="e">meta</div>
                <div id="f">meta</div>
                <h4 id="h">h4</h4>
                <h4 id="h2" style="line-height:20px">h4</h4>
                <div class="big" id="big">big</div>
                <div class="mid" id="mid">mid</div>
                </body></html>
                """));

        // 文本项的换行行内布局：.tree-card 的形状（head + clock 一行，meta 独占一行）。
        cases.add(new Case("tree-card-text", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; font-size: 12px; line-height: 20px; }
                .card { display: flex; flex-direction: row; flex-wrap: wrap; gap: 5px 8px;
                        width: 340px; padding: 10px 12px; border: 3px solid #444; }
                .head { order: 1; flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 8px; }
                .head .title { flex: 1 1 auto; min-width: 0; margin: 0; font-size: 18px; }
                .badge { flex: 0 0 auto; }
                .clock { order: 2; flex: 0 0 auto; }
                .meta { order: 3; flex: 1 0 100%; margin: 0; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                </style></head><body>
                <article class="card">
                  <div class="head"><span class="badge">B</span><h4 class="title">Title</h4></div>
                  <p class="meta">meta meta meta meta meta meta</p>
                  <span class="clock">clock</span>
                </article>
                </body></html>
                """));

        // 定宽的行向 flex 容器里，auto 宽度的项按 flex:1 1 auto 撑满剩余空间。
        cases.add(new Case("nest-flex-row-grow", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .row { display: flex; gap: 8px; width: 232px; }
                .b { flex: 0 0 auto; width: 20px; height: 18px; }
                .c { flex: 1 1 auto; min-width: 0; height: 18px; }
                </style></head><body>
                <div class="row"><span class="b" id="b"></span><div class="c" id="c"></div></div>
                </body></html>
                """));

        // 同上，但内层容器的宽度由外层 flex 行分配（.tree-card 里 .tree-head 的形状）。
        cases.add(new Case("nest-flex-auto-width-row", """
                <html><head><style>
                * { box-sizing: border-box; }
                body { margin: 0; }
                .outer { display: flex; gap: 8px; width: 300px; }
                .row { display: flex; gap: 8px; flex: 1 1 auto; min-width: 0; }
                .b { flex: 0 0 auto; width: 20px; height: 18px; }
                .c { flex: 1 1 auto; min-width: 0; height: 18px; }
                .d { flex: 0 0 auto; width: 60px; height: 18px; }
                </style></head><body>
                <div class="outer">
                  <div class="row" id="row"><span class="b" id="b"></span><div class="c" id="c"></div></div>
                  <div class="d" id="d"></div>
                </div>
                </body></html>
                """));

        return cases;
    }

    @Test
    void dumpLayoutProbe() throws IOException {
        // 不设 -PauiLayoutProbeOut 时落到 targets/<target>/build/reports/aui/layout-probe/。
        // 这个用例**始终执行**（不 assumeTrue 跳过）：它既给浏览器对差提供素材，也是一次
        // 走过引擎的冒烟；而 forge-1.20.1 的 verifyAuiTestMatrix 会拒绝任何"没说明理由的跳过"。
        String out = System.getProperty("aui.layoutProbe.out", "build/reports/aui/layout-probe");

        Path outDir = Path.of(out);
        Files.createDirectories(outDir);
        Size.setViewportOverride(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        try {
            for (Case testCase : cases()) {
                String path = "probe://" + testCase.name();
                HTML.putTemple(path, testCase.source());
                Document document = new Document(path, false);
                document.refresh();
                Files.writeString(outDir.resolve(testCase.name() + ".json"), snapshot(document), StandardCharsets.UTF_8);
                Files.writeString(outDir.resolve(testCase.name() + ".html"), testCase.source(), StandardCharsets.UTF_8);
            }
        } finally {
            Size.clearViewportOverride();
        }
    }

    private static String snapshot(Document document) {
        StringBuilder json = new StringBuilder("{\n  \"viewport\": { \"width\": ")
                .append(VIEWPORT_WIDTH).append(", \"height\": ").append(VIEWPORT_HEIGHT).append(" },\n  \"nodes\": [\n");
        List<Element> elements = document.querySelectorAll("*");
        for (int i = 0; i < elements.size(); i++) {
            Element element = elements.get(i);
            Element.DOMRect rect = element.getBoundingClientRect();
            if (i > 0) json.append(",\n");
            json.append("    {")
                    .append("\"i\": ").append(i)
                    .append(", \"tag\": \"").append(element.getNodeName().toLowerCase(Locale.ROOT)).append('"')
                    .append(", \"id\": \"").append(escape(element.getAttribute("id"))).append('"')
                    .append(", \"class\": \"").append(escape(element.getClassName())).append('"')
                    .append(", \"x\": ").append(number(rect.x))
                    .append(", \"y\": ").append(number(rect.y))
                    .append(", \"w\": ").append(number(rect.width))
                    .append(", \"h\": ").append(number(rect.height))
                    .append(", \"cw\": ").append(number(element.getClientWidth()))
                    .append(", \"ch\": ").append(number(element.getClientHeight()))
                    .append(", \"ow\": ").append(number(element.getOffsetWidth()))
                    .append(", \"oh\": ").append(number(element.getOffsetHeight()))
                    // 引擎特有的诊断量：max-content（天然）尺寸。浏览器侧没有对应字段，
                    // 只在定位"flex 换行行高/主轴基准"这类差异时用来看引擎自己的中间结论。
                    .append(", \"nw\": ").append(number(Size.natural(element).width()))
                    .append(", \"nh\": ").append(number(Size.natural(element).height()))
                    .append('}');
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    record Case(String name, String source) {
    }
}
