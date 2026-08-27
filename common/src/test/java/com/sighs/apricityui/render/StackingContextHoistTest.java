package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSS2.1 Appendix E：position:relative/absolute 且 z-index:auto 的元素【不】创建
 * 层叠上下文，其带 z-index 的后代必须参与最近的层叠上下文（在 AUI 中由 overflow
 * 裁剪边界约束）的排序。回归场景：ore 主题 Feedback 页打开的下拉菜单
 * （.dropdown 是 position:relative + z-index:auto，.dropdown-options 是
 * position:absolute + z-index:20）在浏览器里盖在后面的卡片之上，修复前游戏里
 * 被后面的卡片盖住。
 */
class StackingContextHoistTest {

    private static Element box(Document document, String style) {
        Element element = new Element(document, "div");
        if (style != null && !style.isBlank()) element.setAttribute("style", style);
        return element;
    }

    private static void rebuild(Document document) {
        document.markDirty(document.body, Drawer.REORDER);
        document.commitRenderState();
    }

    private static int firstPaintIndex(List<RenderNode> nodes, Element subtree) {
        for (int i = 0; i < nodes.size(); i++) {
            Element target = RenderNode.getRenderNodeTarget(nodes.get(i));
            if (target != null && RenderNode.isSameOrDescendant(target, subtree)) return i;
        }
        return -1;
    }

    private static int lastPaintIndex(List<RenderNode> nodes, Element subtree) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Element target = RenderNode.getRenderNodeTarget(nodes.get(i));
            if (target != null && RenderNode.isSameOrDescendant(target, subtree)) return i;
        }
        return -1;
    }

    private static int firstExactIndex(List<RenderNode> nodes, Element element) {
        for (int i = 0; i < nodes.size(); i++) {
            if (RenderNode.getRenderNodeTarget(nodes.get(i)) == element) return i;
        }
        return -1;
    }

    private static int lastExactIndex(List<RenderNode> nodes, Element element) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (RenderNode.getRenderNodeTarget(nodes.get(i)) == element) return i;
        }
        return -1;
    }

    private static long countPhaseNodes(List<RenderNode> nodes, Element target, Base.RenderPhase phase) {
        return nodes.stream()
                .filter(n -> n instanceof RenderNode.ElementPhaseNode p
                        && p.target() == target && p.phase() == phase)
                .count();
    }

    private static void assertWithinMask(List<RenderNode> nodes, Element clipper, Element painted) {
        int push = -1, pop = -1;
        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);
            if (node instanceof RenderNode.MaskPushNode m && m.target() == clipper) push = i;
            if (node instanceof RenderNode.MaskPopNode m && m.target() == clipper) pop = i;
        }
        assertTrue(push >= 0 && pop > push,
                "clipper mask push/pop missing: push=" + push + " pop=" + pop);
        int first = firstPaintIndex(nodes, painted);
        int last = lastPaintIndex(nodes, painted);
        assertTrue(first > push && last < pop,
                "hoisted nodes must stay inside the clip boundary: first=" + first
                        + " last=" + last + " push=" + push + " pop=" + pop);
    }

    @Test
    void zIndexedDescendantOfPositionedAutoAncestorPaintsAboveLaterContent() {
        Document document = TestDocumentFactory.createDocument();
        Element page = box(document, "overflow: hidden;");
        // 镜像真实页面结构：下拉藏在前一张静态卡片里，后面还有一张静态卡片。
        Element cardA = box(document, "background-color: #111111;");
        Element dropdown = box(document, "position: relative;");
        Element options = box(document, "position: absolute; z-index: 20;");
        Element cardB = box(document, "background-color: #123456;");
        dropdown.appendChild(options);
        cardA.appendChild(dropdown);
        page.appendChild(cardA);
        page.appendChild(cardB);
        document.body.appendChild(page);
        rebuild(document);

        List<RenderNode> paintList = document.getPaintList();
        // 浏览器行为：z-index:20 的 options 参与外层作用域排序，画在后面的普通流内容之上。
        assertTrue(firstPaintIndex(paintList, options) > lastPaintIndex(paintList, cardB),
                "z-index:20 options must paint above the later in-flow card; first(options)="
                        + firstPaintIndex(paintList, options) + " last(cardB)="
                        + lastPaintIndex(paintList, cardB));
        // 但提升不能越过 overflow 裁剪边界。
        assertWithinMask(paintList, page, options);
    }

    @Test
    void hoistingStopsAtNearestOverflowClipBoundary() {
        Document document = TestDocumentFactory.createDocument();
        Element page = box(document, "overflow: hidden;");
        Element clipBox = box(document, "overflow: hidden;");
        Element innerCard = box(document, "background-color: #111111;");
        Element dropdown = box(document, "position: relative;");
        Element options = box(document, "position: absolute; z-index: 20;");
        Element innerFollower = box(document, "background-color: #222222;");
        Element outerFollower = box(document, "background-color: #333333;");
        dropdown.appendChild(options);
        innerCard.appendChild(dropdown);
        clipBox.appendChild(innerCard);
        clipBox.appendChild(innerFollower);
        page.appendChild(clipBox);
        page.appendChild(outerFollower);
        document.body.appendChild(page);
        rebuild(document);

        List<RenderNode> paintList = document.getPaintList();
        // 提升到 clipBox 的作用域：盖过 clipBox 内后面的内容……
        assertTrue(firstPaintIndex(paintList, options) > lastPaintIndex(paintList, innerFollower),
                "options must paint above later content inside the same clip boundary; first(options)="
                        + firstPaintIndex(paintList, options) + " last(innerFollower)="
                        + lastPaintIndex(paintList, innerFollower));
        // ……但不能越出 clipBox（否则会被裁掉的内容反而画到裁剪区外）。
        assertTrue(lastPaintIndex(paintList, options) < firstPaintIndex(paintList, outerFollower),
                "options must not escape the clip boundary into later outer content");
        assertWithinMask(paintList, clipBox, options);
    }

    @Test
    void negativeZDescendantPaintsBehindPositionedAncestorBackground() {
        Document document = TestDocumentFactory.createDocument();
        Element host = box(document, "position: relative; background-color: #336699;");
        Element behind = box(document, "position: absolute; z-index: -1;");
        host.appendChild(behind);
        document.body.appendChild(host);
        rebuild(document);

        List<RenderNode> paintList = document.getPaintList();
        // CSS2.1 E.2：负 z-index 层画在层叠上下文背景之后、普通流之前——
        // 包括排在 position:relative 祖先自身的盒绘制之前。
        // 注意必须用精确目标比较：behind 是 host 的后代，子树匹配会把提升后的
        // behind 节点也算进 host 的范围。
        assertTrue(lastExactIndex(paintList, behind) < firstExactIndex(paintList, host),
                "z-index:-1 child must paint behind the positioned ancestor's background; last(behind)="
                        + lastExactIndex(paintList, behind) + " first(host)="
                        + firstExactIndex(paintList, host));
    }

    @Test
    void incrementalRebuildKeepsHoistedNodesExactlyOnce() {
        Document document = TestDocumentFactory.createDocument();
        Element page = box(document, "overflow: hidden;");
        Element cardA = box(document, "background-color: #111111;");
        Element dropdown = box(document, "position: relative;");
        Element options = box(document, "position: absolute; z-index: 20;");
        Element cardB = box(document, "background-color: #123456;");
        dropdown.appendChild(options);
        cardA.appendChild(dropdown);
        page.appendChild(cardA);
        page.appendChild(cardB);
        document.body.appendChild(page);
        rebuild(document);

        // 模拟 .open 切换之类的局部 REORDER：拼接必须整段替换提升范围，不能留残影。
        document.markDirty(dropdown, Drawer.REORDER);
        document.commitRenderState();

        List<RenderNode> paintList = document.getPaintList();
        assertEquals(1, countPhaseNodes(paintList, options, Base.RenderPhase.BODY),
                "incremental splice must not duplicate or lose the hoisted options");
        assertTrue(firstPaintIndex(paintList, options) > lastPaintIndex(paintList, cardB),
                "paint order must survive the incremental splice");
        assertWithinMask(paintList, page, options);
    }

    // ------------------------------------------------------------------
    // 全保真场景：真实 example.html + 真实 ore.css
    // ------------------------------------------------------------------

    private static final Path ORE_DIR = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore");

    private static Document loadRealOreDocument() throws Exception {
        String html = Files.readString(ORE_DIR.resolve("example.html"));
        String css = Files.readString(ORE_DIR.resolve("ore.css"));
        html = html.replace("<link rel=\"stylesheet\" href=\"ore.css\">",
                "<style>\n" + css + "\n</style>");
        HTML.putTemple("test://ore-example", html);
        Document document = new Document("test://ore-example", false);
        document.refresh();
        return document;
    }

    private static Element findById(Element root, String id) {
        if (id.equals(root.getAttribute("id"))) return root;
        for (Element child : root.getChildren()) {
            Element found = findById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static void showFeedbackPage(Document document) {
        Element foundations = findById(document.body, "ore-page-foundations");
        Element feedback = findById(document.body, "ore-page-feedback");
        assertNotNull(foundations, "foundations radio missing");
        assertNotNull(feedback, "feedback radio missing");
        foundations.setChecked(false);
        feedback.setChecked(true);
    }

    @Test
    void realPageOpenDropdownPaintsAboveFollowingSections() throws Exception {
        Document document = loadRealOreDocument();
        document.commitPendingStyleRecalcForRender();
        rebuild(document);

        showFeedbackPage(document);
        document.commitPendingStyleRecalcForRender();
        document.commitRenderState();

        Element options = document.querySelector(".dropdown-2.open .dropdown-options");
        assertNotNull(options, "open dark dropdown options missing on feedback page");

        Element loadingHeader = null;
        for (Element header : document.getElementsByClassName("card-header")) {
            if (header.getTextContent().contains("Loading (spinner sizes")) {
                loadingHeader = header;
                break;
            }
        }
        assertNotNull(loadingHeader, "loading card header missing on feedback page");

        List<RenderNode> paintList = document.getPaintList();
        assertTrue(firstPaintIndex(paintList, options) >= 0, "open options must be painted");
        // 浏览器里展开的下拉选项盖在后面的 Loading 卡片之上。
        assertTrue(firstPaintIndex(paintList, options) > lastPaintIndex(paintList, loadingHeader),
                "open dropdown options must paint above the following Loading card; first(options)="
                        + firstPaintIndex(paintList, options) + " last(loadingHeader)="
                        + lastPaintIndex(paintList, loadingHeader));

        // 同时不能逃出滚动页（.ore-page overflow auto/hidden）的裁剪范围。
        Element clipper = options.parentElement;
        while (clipper != null && !Interaction.clipsOverflow(clipper.getRawComputedStyle())) {
            clipper = clipper.parentElement;
        }
        assertNotNull(clipper, "options must have a clipping ancestor (the scrolling page)");
        assertWithinMask(paintList, clipper, options);
    }
}
