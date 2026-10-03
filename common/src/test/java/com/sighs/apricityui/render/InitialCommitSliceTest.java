package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiConfigService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 首次全量几何提交的分片路径（{@link LayoutCommit#commitInitialSlice}）覆盖：
 * 极小预算多次调用必须能推进并最终完成，完成后的提交几何必须与一次性同步
 * {@code LayoutCommit.commit} 完全一致；换代（refresh）必须丢弃旧分片。
 */
class InitialCommitSliceTest {

    private static final String PATH = "test://initial-commit-slice";
    private static final String SLICE_PROPERTY = "apricityui.layout.sliceInitialCommit";
    private static final String SLICE_MS_PROPERTY = "apricityui.layout.initialCommitSliceMs";

    private static final String HTML_SOURCE = """
            <html><head><style>
              body { margin: 0; padding: 0; }
              .row { display: block; padding: 4px; }
              .card { position: relative; width: 120px; height: 40px; margin: 3px; padding: 6px;
                      background: #123456; border: 2px solid #654321; }
              .inner { width: 50%; height: 12px; }
              .scroller { overflow: scroll; height: 24px; }
            </style></head><body>
              <div class="row">
                <div class="card" id="a"><div class="inner"></div></div>
                <div class="card" id="b"><div class="inner"></div></div>
                <div class="card" id="c"><div class="inner"></div></div>
              </div>
              <div class="row">
                <div class="card" id="d" style="transform: translate(5px, 7px);"><div class="inner"></div></div>
                <div class="card scroller" id="e"><div class="inner"></div></div>
              </div>
            </body></html>
            """;

    @Test
    void tinyBudgetSlicesRunToCompletionAndMatchSingleShotCommit() {
        Size.setViewportOverride(640, 480);
        String previousValue = System.getProperty(SLICE_PROPERTY);
        try {
            Document sliced = createDocument(PATH + "/sliced");
            Document singleShot = createDocument(PATH + "/single");

            // 极小预算（1ns）：一次调用只提交一个元素，状态机必须能跨多次调用续跑到完成。
            int calls = 0;
            while (!LayoutCommit.commitInitialSlice(sliced, 1L)) {
                calls++;
                assertTrue(calls < 10_000, "分片必须在有限次调用内完成");
                assertTrue(sliced.isInitialCommitSlicing(), "未完成时必须保留续跑状态");
                assertEquals(sliced.getRefreshGeneration(), sliced.getInitialCommitSlice().generation,
                        "续跑状态必须绑定当前 refreshGeneration");
            }
            calls++;
            assertTrue(calls > 1, "极小预算下不该一次调用就完成，实际 calls=" + calls);
            assertFalse(sliced.needsInitialFullCommit(), "完成后该代必须已做过全量提交");
            assertFalse(sliced.isInitialCommitSlicing(), "完成后不得残留分片状态");
            assertNull(sliced.getInitialCommitSlice());

            // 关掉分片，走原来的同步全量提交作为参照。
            System.setProperty(SLICE_PROPERTY, "false");
            assertFalse(LayoutCommit.isInitialCommitSlicingEnabled());
            LayoutCommit.commit(singleShot);
            assertFalse(singleShot.needsInitialFullCommit());
            assertFalse(singleShot.isInitialCommitSlicing(), "开关关闭时不得进入分片路径");

            Map<String, String> slicedGeometry = committedGeometryByPath(sliced);
            Map<String, String> singleShotGeometry = committedGeometryByPath(singleShot);
            assertFalse(singleShotGeometry.isEmpty(), "参照文档必须有已提交几何");
            assertEquals(singleShotGeometry.keySet(), slicedGeometry.keySet(),
                    "分片与一次性提交必须覆盖同一批元素");
            for (Map.Entry<String, String> entry : singleShotGeometry.entrySet()) {
                assertEquals(entry.getValue(), slicedGeometry.get(entry.getKey()),
                        "几何不一致：" + entry.getKey());
            }
        } finally {
            restoreProperty(SLICE_PROPERTY, previousValue);
            Size.clearViewportOverride();
        }
    }

    @Test
    void refreshDuringSlicingDiscardsProgressAndRestartsForNewGeneration() {
        Size.setViewportOverride(640, 480);
        try {
            Document document = createDocument(PATH + "/refresh");
            long generation = document.getRefreshGeneration();

            assertFalse(LayoutCommit.commitInitialSlice(document, 1L), "极小预算下第一片不该完成");
            assertTrue(document.isInitialCommitSlicing());
            assertEquals(generation, document.getInitialCommitSlice().generation);
            assertTrue(document.getInitialCommitSlice().cursor > 0, "第一片必须推进游标");

            document.refresh();

            assertTrue(document.getRefreshGeneration() > generation, "refresh 必须递增代");
            assertTrue(document.needsInitialFullCommit(), "换代后必须重新欠一次全量提交");
            assertFalse(document.isInitialCommitSlicing(), "换代必须丢弃旧分片状态");
            assertNull(document.getInitialCommitSlice());

            int calls = 0;
            while (!LayoutCommit.commitInitialSlice(document, 1L)) {
                calls++;
                assertTrue(calls < 10_000, "新一代分片必须能完成");
                assertEquals(document.getRefreshGeneration(),
                        document.getInitialCommitSlice().generation, "新一代分片必须绑定新代");
            }
            assertFalse(document.needsInitialFullCommit());
            assertFalse(document.isInitialCommitSlicing());
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void renderFrameDriverAdvancesAtMostOneSlicePerFrame() {
        Size.setViewportOverride(640, 480);
        String previousEnabled = System.getProperty(SLICE_PROPERTY);
        String previousBudget = System.getProperty(SLICE_MS_PROPERTY);
        try {
            System.clearProperty(SLICE_PROPERTY);
            assertTrue(LayoutCommit.isInitialCommitSlicingEnabled(), "默认必须开启分片");
            System.setProperty(SLICE_MS_PROPERTY, "0.000001");

            Document document = createDocument(PATH + "/frame-driver");

            // 渲染门控是分片的唯一启动点：第一帧留下未完成的分片，并要求跳过绘制。
            LayoutCommit.beginInitialCommitFrame(document);
            assertTrue(LayoutCommit.advanceInitialCommitForRender(document),
                    "极小预算下首帧必须报告几何未完成");
            assertTrue(document.needsInitialFullCommit());
            assertTrue(document.isInitialCommitSlicing());

            // 同一帧内重复推进不得再消耗一片预算。
            int cursorAfterFirstAdvance = document.getInitialCommitSlice().cursor;
            assertTrue(cursorAfterFirstAdvance > 0, "渲染帧驱动必须推进游标");
            assertTrue(LayoutCommit.advanceInitialCommitForRender(document));
            assertEquals(cursorAfterFirstAdvance, document.getInitialCommitSlice().cursor,
                    "同一帧最多推进一片");

            // 分片进行中，commit 入口（tick 路径会走它）也只允许顺带推进一片。
            LayoutCommit.commit(document);
            assertEquals(cursorAfterFirstAdvance, document.getInitialCommitSlice().cursor,
                    "同一帧内 commit 入口不得再推进一片");

            int frames = 0;
            boolean incomplete;
            do {
                LayoutCommit.beginInitialCommitFrame(document);
                incomplete = LayoutCommit.advanceInitialCommitForRender(document);
                frames++;
                assertTrue(frames < 10_000, "渲染帧驱动必须能跑到完成");
            } while (incomplete);

            assertFalse(document.needsInitialFullCommit(), "完成帧之后不得再报告未完成");
            assertFalse(document.isInitialCommitSlicing());
            assertFalse(LayoutCommit.advanceInitialCommitForRender(document),
                    "已完成后渲染门控不得再拦住绘制");
        } finally {
            restoreProperty(SLICE_PROPERTY, previousEnabled);
            restoreProperty(SLICE_MS_PROPERTY, previousBudget);
            Size.clearViewportOverride();
        }
    }

    /**
     * 没有分片在跑时，commit 入口必须保持原来的同步全量语义：tick / hitTest 等
     * 调用方依赖「commit 返回后几何必然完整」，只有渲染门控才能启动分片。
     */
    @Test
    void commitEntryStaysSynchronousUntilRenderGateStartsSlicing() {
        Size.setViewportOverride(640, 480);
        String previousEnabled = System.getProperty(SLICE_PROPERTY);
        String previousBudget = System.getProperty(SLICE_MS_PROPERTY);
        try {
            System.clearProperty(SLICE_PROPERTY);
            System.setProperty(SLICE_MS_PROPERTY, "0.000001");

            Document document = createDocument(PATH + "/commit-entry");
            LayoutCommit.commit(document);

            assertFalse(document.needsInitialFullCommit(),
                    "渲染门控尚未启动分片时，commit 必须同步完成整份几何");
            assertFalse(document.isInitialCommitSlicing(), "同步路径不得留下分片状态");
            assertFalse(committedGeometryByPath(document).isEmpty(), "同步路径必须提交几何");
        } finally {
            restoreProperty(SLICE_PROPERTY, previousEnabled);
            restoreProperty(SLICE_MS_PROPERTY, previousBudget);
            Size.clearViewportOverride();
        }
    }

    @Test
    void sliceBudgetHonoursSystemPropertyAndFallsBackOnGarbage() {
        String previousBudget = System.getProperty(SLICE_MS_PROPERTY);
        try {
            System.setProperty(SLICE_MS_PROPERTY, "12.5");
            assertEquals(12_500_000L, LayoutCommit.initialCommitSliceBudgetNs());

            System.setProperty(SLICE_MS_PROPERTY, "not-a-number");
            assertEquals(16_000_000L, LayoutCommit.initialCommitSliceBudgetNs());

            System.setProperty(SLICE_MS_PROPERTY, "-3");
            assertEquals(16_000_000L, LayoutCommit.initialCommitSliceBudgetNs());

            System.clearProperty(SLICE_MS_PROPERTY);
            assertEquals(16_000_000L, LayoutCommit.initialCommitSliceBudgetNs(), "默认预算 16ms");
        } finally {
            restoreProperty(SLICE_MS_PROPERTY, previousBudget);
        }
    }

    /**
     * 两个键搬进配置系统后：无系统属性时必须读配置服务的值；显式系统属性仍然优先；
     * 配置给出非法预算（0/负数/NaN）时退回内置默认 16ms，绝不产生 0 预算。
     */
    @Test
    void configServiceSuppliesSliceSettingsAndSystemPropertyStillWins() {
        String previousEnabled = System.getProperty(SLICE_PROPERTY);
        String previousBudget = System.getProperty(SLICE_MS_PROPERTY);
        try {
            System.clearProperty(SLICE_PROPERTY);
            System.clearProperty(SLICE_MS_PROPERTY);

            FakeConfig config = new FakeConfig();
            config.enabled = false;
            config.sliceMs = 24.0f;
            AuiServices.setConfig(config);

            assertFalse(LayoutCommit.isInitialCommitSlicingEnabled(), "无系统属性时必须读配置服务的开关");
            assertEquals(24_000_000L, LayoutCommit.initialCommitSliceBudgetNs(), "无系统属性时必须读配置服务的预算");

            // 显式系统属性（调试/测试覆盖）优先于配置服务。
            System.setProperty(SLICE_PROPERTY, "true");
            System.setProperty(SLICE_MS_PROPERTY, "4");
            assertTrue(LayoutCommit.isInitialCommitSlicingEnabled(), "系统属性必须优先于配置服务");
            assertEquals(4_000_000L, LayoutCommit.initialCommitSliceBudgetNs(), "系统属性预算必须优先于配置服务");

            // 配置预算非法时退回内置默认，绝不产生 0 预算。
            System.clearProperty(SLICE_MS_PROPERTY);
            config.sliceMs = 0.0f;
            assertEquals(16_000_000L, LayoutCommit.initialCommitSliceBudgetNs(), "非法配置预算必须退回内置默认 16ms");
            config.sliceMs = Float.NaN;
            assertEquals(16_000_000L, LayoutCommit.initialCommitSliceBudgetNs(), "NaN 配置预算必须退回内置默认 16ms");
        } finally {
            AuiServices.setConfig(null);
            restoreProperty(SLICE_PROPERTY, previousEnabled);
            restoreProperty(SLICE_MS_PROPERTY, previousBudget);
        }
    }

    private static Document createDocument(String path) {
        HTML.putTemple(path, HTML_SOURCE);
        Document document = new Document(path, false);
        document.refresh();
        assertNotNull(document.body, "refresh 必须产生 body");
        assertFalse(document.getPaintList().isEmpty(), "paint list 不能为空");
        return document;
    }

    /** 按 DOM 路径收集每个 paint-list 元素的已提交几何指纹（两个文档的元素实例不同，不能按实例比对）。 */
    private static Map<String, String> committedGeometryByPath(Document document) {
        Map<String, String> geometry = new LinkedHashMap<>();
        for (RenderNode node : document.getPaintList()) {
            Element target = RenderNode.getRenderNodeTarget(node);
            if (target == null || target.document != document) continue;
            String path = domPath(target);
            if (geometry.containsKey(path)) continue;
            Rect rect = target.getRenderer().getCommittedRect();
            geometry.put(path, rect == null ? "<uncommitted>" : describe(rect));
        }
        return geometry;
    }

    private static String domPath(Element element) {
        StringBuilder path = new StringBuilder();
        for (Element current = element; current != null; current = current.parentElement) {
            int index = 0;
            Element parent = current.parentElement;
            if (parent != null) {
                for (Element sibling : parent.getChildren()) {
                    if (sibling == current) break;
                    index++;
                }
            }
            path.insert(0, "/" + current.tagName + "[" + index + "]");
        }
        return path.toString();
    }

    private static String describe(Rect rect) {
        Position body = rect.getBodyRectPosition();
        Size bodySize = rect.getBodyRectSize();
        Size elementSize = rect.getElementSize();
        // joml 只在测试运行时可见、不在测试编译期可见，所以这里只比对 rect 本身。
        return "pos=" + rect.position.x + "," + rect.position.y
                + " size=" + elementSize.width() + "x" + elementSize.height()
                + " body=" + body.x + "," + body.y + " " + bodySize.width() + "x" + bodySize.height();
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    /** 只关心首次提交分片两个键的假配置服务，其余方法返回无害值。 */
    private static final class FakeConfig implements AuiConfigService {
        boolean enabled = true;
        float sliceMs = 8.0f;

        @Override
        public boolean initialCommitSliceEnabled() {
            return enabled;
        }

        @Override
        public void setInitialCommitSliceEnabled(boolean value) {
            enabled = value;
        }

        @Override
        public float initialCommitSliceMs() {
            return sliceMs;
        }

        @Override
        public void setInitialCommitSliceMs(double value) {
            sliceMs = (float) value;
        }

        @Override
        public boolean debugAutoReload() {
            return false;
        }

        @Override
        public void setDebugAutoReload(boolean value) {
        }

        @Override
        public boolean aiAutoScreenshot() {
            return false;
        }

        @Override
        public void setAiAutoScreenshot(boolean value) {
        }

        @Override
        public boolean frameTimingHud() {
            return false;
        }

        @Override
        public void setFrameTimingHud(boolean value) {
        }

        @Override
        public boolean remoteDebug() {
            return false;
        }

        @Override
        public void setRemoteDebug(boolean value) {
        }

        @Override
        public boolean viewportZoomPassThrough() {
            return true;
        }

        @Override
        public void setViewportZoomPassThrough(boolean value) {
        }

        @Override
        public boolean blockMouseEventsWhenCursorHidden() {
            return true;
        }

        @Override
        public void setBlockMouseEventsWhenCursorHidden(boolean value) {
        }

        @Override
        public float worldWindowDepthOffsetScale() {
            return 0.01f;
        }

        @Override
        public void setWorldWindowDepthOffsetScale(double value) {
        }

        @Override
        public int worldWindowMaxDisplayDistance() {
            return 128;
        }

        @Override
        public void setWorldWindowMaxDisplayDistance(int value) {
        }

        @Override
        public boolean worldWindowLodEnabled() {
            return false;
        }

        @Override
        public void setWorldWindowLodEnabled(boolean value) {
        }

        @Override
        public int worldWindowFullDetailDistance() {
            return 16;
        }

        @Override
        public void setWorldWindowFullDetailDistance(int value) {
        }

        @Override
        public int worldWindowReducedDetailDistance() {
            return 48;
        }

        @Override
        public void setWorldWindowReducedDetailDistance(int value) {
        }

        @Override
        public void save() {
        }

        @Override
        public void markClientReloadPending() {
        }

        @Override
        public boolean consumeClientReloadPending() {
            return false;
        }
    }
}
