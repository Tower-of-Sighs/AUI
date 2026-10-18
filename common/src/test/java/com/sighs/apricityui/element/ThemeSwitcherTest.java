package com.sighs.apricityui.element;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeSwitcherTest {
    private static final String SHEET_MARK = "data-theme-css";
    private static final String PANEL_SELECTOR = ".aui-theme-switcher-panel";
    private static final String ROW_SELECTOR = ".aui-theme-switcher-row";

    @AfterEach
    void clearViewportOverride() {
        Size.clearViewportOverride();
    }

    /** 标签名解析回来后要换成 ThemeSwitcher 实例，且自闭合标签不能吞掉后面的兄弟节点。 */
    @Test
    void tagUpgradesToThemeSwitcherWithoutSwallowingSiblings() {
        Element.register(ThemeSwitcher.TAG_NAME, (document, tagName) -> new ThemeSwitcher(document));
        String path = "test://theme-switcher-tag.html";
        HTML.putTemple(path, "<html><head></head><body class=\"t01-ore-theme\">"
                + "<theme-switcher/><p>after</p></body></html>");
        Document document = createDocument(path);

        assertEquals(2, document.body.children.size());
        Element switcher = document.body.children.get(0);
        assertTrue(switcher instanceof ThemeSwitcher);
        assertTrue(switcher.getAttribute("style").startsWith("position:fixed"));
        // 四颗主题色圆点
        assertEquals(4, switcher.children.size());
    }

    @Test
    void clickTogglesPanelListingBuiltInThemes() {
        Document document = TestDocumentFactory.createDocument();
        ThemeSwitcher switcher = attachSwitcher(document);

        assertNull(panelOf(document));

        click(switcher);
        Element panel = panelOf(document);
        assertNotNull(panel);
        assertTrue(panel.isTopLayer());
        assertEquals(ThemeSwitcher.allThemes().size(), rowsOf(document).size());
        assertNotNull(rowOf(document, "t01-ore"));
        assertNotNull(rowOf(document, "t15-mecha"));
        assertNotNull(rowOf(document, "ore"));

        click(switcher);
        assertNull(panelOf(document));
        assertTrue(rowsOf(document).isEmpty());
    }

    @Test
    void outsidePointerClosesPanel() {
        Document document = TestDocumentFactory.createDocument();
        ThemeSwitcher switcher = attachSwitcher(document);
        Element other = Element.init(document.createElement("div"));
        other.setAttribute("style", "width:40px;height:40px;");
        document.body.appendChild(other);

        click(switcher);
        assertNotNull(panelOf(document));

        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(2, 2), 0, false), document, other);
        assertNull(panelOf(document));
    }

    /** 换肤：挂上新表 → 换根元素作用域类 → 摘掉旧表和预览台留下的那条静态表 → 派发通知事件。 */
    @Test
    void switchingThemeSwapsScopeClassAndReplacesSheets() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "t01-ore-theme");
        Element pageSheet = appendThemeSheet(document, "t01-ore");
        ThemeSwitcher switcher = attachSwitcher(document);
        List<String> events = new ArrayList<>();
        document.body.addEventListener(ThemeSwitcher.THEME_CHANGE_EVENT,
                event -> events.add(String.valueOf(event.detail)));

        click(switcher);
        click(rowOf(document, "t07-modrinth-dark"));

        assertTrue(pump(document, () -> document.body.getClassNames().contains("t07-modrinth-dark-theme")),
                "新主题的样式表没加载落地");
        assertFalse(document.body.getClassNames().contains("t01-ore-theme"));
        assertEquals(List.of("t07-modrinth-dark"), events);
        assertFalse(document.head.children.contains(pageSheet), "旧的页面样式表应该被摘掉");
        assertTrue(matchedSheetHrefs(document).stream()
                .anyMatch(href -> href.contains("t07-modrinth-dark/t07-modrinth-dark.css")));
        // 表确实进了级联，不只是挂了个 <link>
        assertTrue(document.CSSCache.keySet().stream()
                .anyMatch(selector -> selector.contains(".t07-modrinth-dark-theme")));
    }

    /** 变体主题（t09 吃 t08）：基主题的表和作用域类都要一起上。 */
    @Test
    void variantThemeAlsoAppliesBaseTheme() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "t01-ore-theme");
        ThemeSwitcher switcher = attachSwitcher(document);

        click(switcher);
        click(rowOf(document, "t09-arwes-transparent"));

        assertTrue(pump(document, () -> document.body.getClassNames().contains("t09-arwes-transparent-theme")),
                "变体主题的样式表没加载落地");
        assertTrue(document.body.getClassNames().contains("t08-arwes-theme"));
        assertTrue(matchedSheetHrefs(document).stream()
                .anyMatch(href -> href.contains("t08-arwes/t08-arwes.css")));
        assertTrue(matchedSheetHrefs(document).stream()
                .anyMatch(href -> href.contains("t09-arwes-transparent/t09-arwes-transparent.css")));
        // 清单里高亮的应该是变体本身，不是它的基主题（两个类同时在场上）
        assertTrue(rowOf(document, "t09-arwes-transparent").getAttribute("style").contains("#3c8527"));
        assertTrue(rowOf(document, "t08-arwes").getAttribute("style").contains("#23252a"));
    }

    /** 样式表加载失败时整次作废：不动作用域类，也不留下半截表。 */
    @Test
    void failedSheetLeavesCurrentThemeAlone() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("class", "t01-ore-theme");
        ThemeSwitcher switcher = attachSwitcher(document);

        switcher.requestTheme(new ThemeSwitcher.Theme(
                "t99-missing", "T99 Missing", "t99-missing-theme", null, null));

        assertTrue(pump(document, () -> matchedSheetHrefs(document).isEmpty()),
                "失败的样式表没有被收回");
        assertTrue(document.body.getClassNames().contains("t01-ore-theme"));
        assertFalse(document.body.getClassNames().contains("t99-missing-theme"));
    }

    /** 拖动按钮：位置按指针增量走，并且被夹在视口内；拖动收尾不会当成点击弹开面板。 */
    @Test
    void dragMovesButtonWithinViewport() {
        Size.setViewportOverride(1280, 800);
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);
        ThemeSwitcher switcher = attachSwitcher(document);
        document.flushPendingStyleUpdates();
        Element.DOMRect rect = switcher.getBoundingClientRect();
        Assumptions.assumeTrue(rect.width > 0, "无头环境没提交布局，位移断言没有意义");

        double pressX = rect.x + rect.width / 2;
        double pressY = rect.y + rect.height / 2;
        mouse(document, switcher, "mousedown", pressX, pressY);
        mouse(document, switcher, "mousemove", pressX - 40, pressY - 60);
        double firstLeft = styleValue(switcher, "left");
        double firstTop = styleValue(switcher, "top");
        mouse(document, switcher, "mousemove", pressX - 100, pressY - 160);
        assertEquals(firstLeft - 60, styleValue(switcher, "left"), 0.01);
        assertEquals(firstTop - 100, styleValue(switcher, "top"), 0.01);

        // 拖到视口外要被夹住，而不是跟着指针跑出去
        mouse(document, switcher, "mousemove", -9999, -9999);
        assertEquals(0, styleValue(switcher, "left"), 0.01);
        assertEquals(0, styleValue(switcher, "top"), 0.01);
        assertTrue(switcher.getAttribute("style").contains("right:auto"));
        // right/bottom 被 left/top 压掉（后写的声明生效），否则视口一变按钮又会被拉回右下角
        assertEquals("auto", switcher.getComputedStyle().right);
        assertEquals("auto", switcher.getComputedStyle().bottom);

        // 拖动收尾：位移超过阈值，抬起那一下不该被当成点击
        mouse(document, switcher, "mouseup", -9999, -9999);
        assertNull(panelOf(document));
    }

    /** 按钮默认贴右下角，面板开在按钮上方且整体留在视口内。 */
    @Test
    void panelOpensNextToButtonInsideViewport() {
        Size.setViewportOverride(1280, 800);
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);
        ThemeSwitcher switcher = attachSwitcher(document);
        document.flushPendingStyleUpdates();
        Element.DOMRect button = switcher.getBoundingClientRect();
        Assumptions.assumeTrue(button.width > 0, "无头环境没提交布局，几何断言没有意义");

        double viewportWidth = document.getViewport().layoutWidth();
        double viewportHeight = document.getViewport().layoutHeight();
        // 默认 right/bottom 各 18px
        assertEquals(viewportWidth - 18, button.x + button.width, 1.0);
        assertEquals(viewportHeight - 18, button.y + button.height, 1.0);

        // 按下抬起同一点 = 一次点击
        double pressX = button.x + button.width / 2;
        double pressY = button.y + button.height / 2;
        mouse(document, switcher, "mousedown", pressX, pressY);
        mouse(document, switcher, "mouseup", pressX, pressY);

        Element panel = panelOf(document);
        assertNotNull(panel);
        Size panelSize = Size.of(panel);
        Position panelPosition = Position.of(panel);
        assertEquals(200, panelSize.width(), 1.0);
        assertTrue(panelPosition.x >= 8 && panelPosition.x + panelSize.width() <= viewportWidth - 8);
        assertTrue(panelPosition.y >= 8 && panelPosition.y + panelSize.height() <= viewportHeight - 8);
        // 按钮在视口下半部分、上方放得下 → 面板开在按钮上面
        assertEquals(button.y - 10, panelPosition.y + panelSize.height(), 1.0);
    }

    /** 预览台真的装了内置切换器：标签自成一个 body 子节点，没把后面的内容吞进去。 */
    @Test
    void previewBenchKeepsSwitcherAsItsOwnBodyChild() throws Exception {
        Element.register(ThemeSwitcher.TAG_NAME, (document, tagName) -> new ThemeSwitcher(document));
        String path = "apricityui/theme/t00-example.html";
        String html;
        try (InputStream stream = ClientLoader.getResourceStream(path)) {
            assertNotNull(stream, "找不到预览台页面");
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        HTML.putTemple(path, html);
        Document document = createDocument(path);

        List<Element> switchers = new ArrayList<>();
        for (Element child : document.body.children) {
            if (child instanceof ThemeSwitcher) switchers.add(child);
        }
        assertEquals(1, switchers.size());
        // 四颗圆点 = onInitFromDom 跑过了；内容没被这个标签吞进来
        assertEquals(4, switchers.get(0).children.size());
    }

    // ------------------------------------------------------------------

    private static Document createDocument(String path) {
        Document document = new Document(path, false);
        HTML.DocumentRoot root = HTML.create(document, path);
        assertNotNull(root);
        document.documentElement = root.documentElement();
        document.head = root.head();
        document.body = root.body();
        return document;
    }

    private static ThemeSwitcher attachSwitcher(Document document) {
        ThemeSwitcher switcher = new ThemeSwitcher(document);
        document.body.appendChild(switcher);
        return switcher;
    }

    /** 页面上静态声明的那条主题表（预览台就是这么写的），换肤时应该被一起摘掉。 */
    private static Element appendThemeSheet(Document document, String themeId) {
        Element link = Element.init(document.createElement("link"));
        link.setAttribute("rel", "stylesheet");
        link.setAttribute(SHEET_MARK, "1");
        link.setAttribute("href", "/apricityui/theme/" + themeId + "/" + themeId + ".css");
        document.head.appendChild(link);
        return link;
    }

    private static void click(Element target) {
        target.dispatchEvent(new Event(target, "click", true));
    }

    private static void mouse(Document document, Element target, String type, double x, double y) {
        MouseEvent.dispatchToTarget(new MouseEvent(type, new Position(x, y), 0, false), document, target);
    }

    private static Element panelOf(Document document) {
        return document.querySelector(PANEL_SELECTOR);
    }

    private static List<Element> rowsOf(Document document) {
        return document.querySelectorAll(ROW_SELECTOR);
    }

    private static Element rowOf(Document document, String themeId) {
        for (Element row : rowsOf(document)) {
            if (themeId.equals(row.getAttribute("data-theme-id"))) return row;
        }
        return null;
    }

    private static List<String> matchedSheetHrefs(Document document) {
        List<String> hrefs = new ArrayList<>();
        for (Element child : document.head.children) {
            if ("LINK".equals(child.tagName) && child.hasAttribute(SHEET_MARK)) {
                hrefs.add(child.getAttribute("href"));
            }
        }
        return hrefs;
    }

    private static double styleValue(Element element, String property) {
        String style = element.getAttribute("style");
        String marker = property + ":";
        int at = style == null ? -1 : style.lastIndexOf(marker);
        assertTrue(at >= 0, "内联样式里没有 " + property);
        int from = at + marker.length();
        int to = style.indexOf(';', from);
        String raw = (to < 0 ? style.substring(from) : style.substring(from, to)).trim();
        assertTrue(raw.endsWith("px"), property + " 不是像素值：" + raw);
        return Double.parseDouble(raw.substring(0, raw.length() - 2));
    }

    /** 样式表是工作线程读盘解析的，测试里手动推进 apply 队列直到条件成立。 */
    private static boolean pump(Document document, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (true) {
            StyleAsyncHandler.INSTANCE.tickApplyQueue();
            document.flushPendingStyleUpdates();
            if (condition.getAsBoolean()) return true;
            if (System.currentTimeMillis() >= deadline) return false;
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
