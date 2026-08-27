package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reproduces the ore-theme indeterminate progress bar escape headlessly: the
 * animated bar (transform: translateX well past the track edge) must be drawn
 * only while the GL scissor equals the track's clip rect. The test walks the
 * real paint list with a real PoseStack, records every mesh submission together
 * with the active scissor (via {@link Mask#testScissorSink}), and flags any
 * bar vertex that would rasterize outside the track.
 *
 * Minecraft/JOML types exist only on the test runtime classpath, so they are
 * accessed reflectively (same approach as ProgressBarBackgroundPaintTest).
 */
class OverflowClipRenderWalkTest {

    private static final int GREEN_R = 60, GREEN_G = 133, GREEN_B = 39; // #3c8527
    // Unique marker painted over the real indeterminate bar's background color
    // so its quads can be told apart from other #3c8527 elements on the page.
    private static final int MARKER_R = 12, MARKER_G = 34, MARKER_B = 56; // #0c2238

    // ------------------------------------------------------------------
    // Reflective MC/JOML access
    // ------------------------------------------------------------------

    private static Class<?> poseStackClass() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> matrix4fClass() {
        try {
            return Class.forName("org.joml.Matrix4f");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPoseStack() {
        try {
            return poseStackClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object lastPoseMatrix(Object poseStack) {
        try {
            Object pose = poseStackClass().getMethod("last").invoke(poseStack);
            return pose.getClass().getMethod("pose").invoke(pose);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> matrix4fcClass() {
        try {
            return Class.forName("org.joml.Matrix4fc");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Object copyMatrix(Object matrix) {
        try {
            Object copy = matrix4fClass().getDeclaredConstructor().newInstance();
            setMatrix(copy, matrix);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setMatrix(Object matrix, Object source) {
        try {
            matrix4fClass().getMethod("set", matrix4fcClass()).invoke(matrix, source);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static float matEntry(Object matrix, String name) {
        try {
            return (float) matrix4fClass().getMethod(name).invoke(matrix);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // Recording render service
    // ------------------------------------------------------------------

    private static final class Vertex {
        final float x, y;
        final int r, g, b, a;

        Vertex(float x, float y, int r, int g, int b, int a) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        boolean isGreen() {
            return r == GREEN_R && g == GREEN_G && b == GREEN_B && a > 0;
        }

        boolean isMarker() {
            return r == MARKER_R && g == MARKER_G && b == MARKER_B && a > 0;
        }

        /** Any pixel belonging to the bar: green background or its inset shadows. */
        boolean isBarFamily() {
            if (isGreen()) return true;
            // inset 0 -4px #2a641c / inset 3px 3px rgba(255,255,255,0.3) /
            // inset -3px -6px rgba(255,255,255,0.12)
            if (r == 42 && g == 100 && b == 28 && a == 255) return true;
            if (r == 255 && g == 255 && b == 255 && (a == 76 || a == 30)) return true;
            return false;
        }
    }

    private static final class Submission {
        final List<Vertex> vertices;
        final AABB scissor; // CSS space; null = scissor disabled at submit time

        Submission(List<Vertex> vertices, AABB scissor) {
            this.vertices = vertices;
            this.scissor = scissor;
        }
    }

    private static final class Recording {
        final Map<Object, List<Vertex>> meshVertices = new IdentityHashMap<>();
        final List<Submission> submissions = new ArrayList<>();
        final AtomicReference<AABB> activeScissor;

        Recording(AtomicReference<AABB> activeScissor) {
            this.activeScissor = activeScissor;
        }

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "beginMesh": {
                                Object token = new Object();
                                meshVertices.put(token, new ArrayList<>());
                                return MeshBuilder.of(token);
                            }
                            case "emitVertex": {
                                Object mesh = args[0];
                                Object mat = args[1];
                                float x = (float) args[2];
                                float y = (float) args[3];
                                int r = (int) args[5];
                                int g = (int) args[6];
                                int b = (int) args[7];
                                int a = (int) args[8];
                                // Apply the pose matrix exactly like the GPU would
                                // (axis-aligned subset is enough for these docs).
                                float sx = matEntry(mat, "m00") * x + matEntry(mat, "m30");
                                float sy = matEntry(mat, "m11") * y + matEntry(mat, "m31");
                                List<Vertex> verts = meshVertices.get(mesh);
                                if (verts != null) verts.add(new Vertex(sx, sy, r, g, b, a));
                                return null;
                            }
                            case "submitMesh": {
                                List<Vertex> verts = meshVertices.get(args[0]);
                                submissions.add(new Submission(
                                        verts == null ? List.of() : List.copyOf(verts),
                                        activeScissor.get()));
                                return null;
                            }
                            case "getProjectionMatrix":
                                try {
                                    return matrix4fClass().getDeclaredConstructor().newInstance();
                                } catch (ReflectiveOperationException e) {
                                    throw new AssertionError(e);
                                }
                            case "getGLVersionString":
                                return "";
                            case "isOnRenderThread":
                                return true;
                            case "recordRenderCall":
                                ((Runnable) args[0]).run();
                                return null;
                            case "beginTextureBatch":
                                return new Object();
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return false;
                                if (rt == int.class) return 0;
                                if (rt == float.class) return 0f;
                                return null;
                        }
                    });
        }

        List<Submission> barSubmissions() {
            List<Submission> result = new ArrayList<>();
            for (Submission s : submissions) {
                if (s.vertices.stream().anyMatch(Vertex::isBarFamily)) result.add(s);
            }
            return result;
        }
    }

    /**
     * A scissor protects out-of-track pixels when it is either contained in the
     * track rect, or empty — a zero-area scissor clips everything, which is the
     * correct outcome when the bar is fully outside the track.
     */
    private static boolean scissorProtects(AABB sc, float tx0, float ty0, float tx1, float ty1) {
        if (sc == null) return false;
        if (sc.width() <= 0 || sc.height() <= 0) return true;
        return sc.isValid()
                && sc.x() >= tx0 - 1.5f && sc.y() >= ty0 - 1.5f
                && sc.x() + sc.width() <= tx1 + 1.5f
                && sc.y() + sc.height() <= ty1 + 1.5f;
    }

    // ------------------------------------------------------------------
    // Paint-list walk (mirrors Base's per-node pose save/restore)
    // ------------------------------------------------------------------

    private static final Method SHOULD_SKIP_SUBTREE = findShouldSkipSubtree();
    private static final Method NODE_RENDER = findNodeRender();

    private static Method findShouldSkipSubtree() {
        try {
            Method m = Base.class.getDeclaredMethod("shouldSkipSubtree", Element.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Method findNodeRender() {
        try {
            return RenderNode.class.getMethod("render", poseStackClass());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void walkPaintList(Document document, Object poseStack) {
        RectFrameCache.begin();
        TransformFrameCache.begin();
        Mask.resetDepth(800, 600);
        try {
            walkNodes(document, poseStack);
        } finally {
            Mask.testScissorSink = null;
            TransformFrameCache.end();
            RectFrameCache.end();
        }
    }

    /** Walks the paint list assuming the caller already began the frame caches. */
    private static void walkNodes(Document document, Object poseStack) {
        Element skippedSubtree = null;
        Set<Element> enteredSubtrees = new HashSet<>();
        for (RenderNode node : document.getPaintList()) {
            Element target = RenderNode.getRenderNodeTarget(node);
            if (skippedSubtree != null) {
                if (target != null && RenderNode.isSameOrDescendant(target, skippedSubtree)) {
                    continue;
                }
                skippedSubtree = null;
            }
            if (target != null && enteredSubtrees.add(target)) {
                boolean skip;
                try {
                    skip = (boolean) SHOULD_SKIP_SUBTREE.invoke(null, target);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                if (skip) {
                    skippedSubtree = target;
                    continue;
                }
            }
            Object saved = copyMatrix(lastPoseMatrix(poseStack));
            try {
                NODE_RENDER.invoke(node, poseStack);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("render failed for " + node, e);
            } finally {
                setMatrix(lastPoseMatrix(poseStack), saved);
            }
        }
    }

    /**
     * Replicates {@code Base.drawDocumentInContext}'s per-frame prologue: frame
     * caches, pending style/layout commits, the motion (animation) step with its
     * geometry-commit decision tree, then the paint-list walk. The real animation
     * clock is wall time, so callers sleep between frames.
     */
    private static void renderFrame(Document document, Object poseStack, int width, int height) {
        renderFrame(document, poseStack, width, height, null);
    }

    private static void renderFrame(Document document, Object poseStack, int width, int height,
                                    Runnable midFrameProbe) {
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        Mask.resetDepth(width, height);
        try {
            boolean styleChanged = document.commitPendingStyleRecalcForRender();
            boolean styleNeedsGeometryCommit = false;
            if (styleChanged) {
                styleNeedsGeometryCommit = document.commitRenderStateForMotion();
            } else if (document.hasPendingRenderState()) {
                document.commitRenderState();
            }
            boolean motionNeedsGeometryCommit = document.stepMotionRender();
            boolean scrollChanged = document.stepScrollRender();
            if (styleNeedsGeometryCommit) {
                LayoutCommit.commit(document);
                document.discardScrollShifts();
                document.commitMotionHitTest();
            } else if (scrollChanged) {
                Set<Element> layoutRoots = document.drainMotionLayoutRoots();
                Set<Element> geometryRoots = document.drainMotionGeometryRoots();
                if (!layoutRoots.isEmpty() || (motionNeedsGeometryCommit && geometryRoots.isEmpty())) {
                    LayoutCommit.commit(document);
                    document.discardScrollShifts();
                } else {
                    LayoutCommit.commitScrollTranslation(document, document.getScrollShifts());
                    if (!geometryRoots.isEmpty()) LayoutCommit.commitTransforms(document, geometryRoots);
                }
                document.commitMotionHitTest();
            } else if (motionNeedsGeometryCommit) {
                Set<Element> layoutRoots = document.drainMotionLayoutRoots();
                Set<Element> geometryRoots = document.drainMotionGeometryRoots();
                if (!layoutRoots.isEmpty()) {
                    LayoutCommit.commit(document);
                } else if (!geometryRoots.isEmpty()) {
                    LayoutCommit.commitTransforms(document, geometryRoots);
                } else {
                    LayoutCommit.commit(document);
                }
                document.commitMotionHitTest();
            }
            if (midFrameProbe != null) midFrameProbe.run();
            walkNodes(document, poseStack);
            Graph.endBatch();
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
        }
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    private static void assertBarNeverEscapesTrack(Recording recording, Element track, Element bar) {
        Rect trackRect = Rect.of(track);
        float tx0 = (float) trackRect.getBodyRectPosition().x;
        float ty0 = (float) trackRect.getBodyRectPosition().y;
        float tx1 = tx0 + (float) trackRect.getBodyRectSize().width();
        float ty1 = ty0 + (float) trackRect.getBodyRectSize().height();
        AABB trackClip = new AABB(tx0, ty0, tx1 - tx0, ty1 - ty0);
        System.out.println("[clip-walk] trackClip=" + trackClip);

        List<Submission> barSubs = recording.barSubmissions();
        assertTrue(!barSubs.isEmpty(), "the bar background must be submitted at least once");

        StringBuilder failures = new StringBuilder();
        for (Submission s : barSubs) {
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Vertex v : s.vertices) {
                if (!v.isBarFamily()) continue;
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
            boolean outside = minX < tx0 - 0.5f || maxX > tx1 + 0.5f
                    || minY < ty0 - 0.5f || maxY > ty1 + 0.5f;
            if (!outside) continue;
            // Vertices outside the track only rasterize harmlessly when the
            // active scissor clips them away.
            if (!scissorProtects(s.scissor, tx0, ty0, tx1, ty1)) {
                failures.append("bar verts [x ")
                        .append(minX).append("..").append(maxX)
                        .append(" y ").append(minY).append("..").append(maxY)
                        .append("] submitted with scissor=").append(s.scissor).append('\n');
            }
        }
        if (failures.length() > 0) {
            fail("bar pixels would escape the overflow:hidden track:\n" + failures);
        }
    }

    private static void assertPaintListMaskStructure(Document document, Element track, Element bar) {
        List<RenderNode> nodes = document.getPaintList();
        int push = -1, pop = -1;
        List<Integer> barBodies = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);
            if (node instanceof RenderNode.MaskPushNode m && m.target() == track) push = i;
            if (node instanceof RenderNode.MaskPopNode m && m.target() == track) pop = i;
            if (node instanceof RenderNode.ElementPhaseNode p
                    && p.target() == bar && p.phase() == Base.RenderPhase.BODY) {
                barBodies.add(i);
            }
        }
        assertTrue(push >= 0, "track mask push must exist");
        assertTrue(pop > push, "track mask pop must follow the push");
        assertEquals(1, barBodies.size(),
                "the bar must have exactly one BODY paint node, found at " + barBodies);
        assertTrue(barBodies.get(0) > push && barBodies.get(0) < pop,
                "bar BODY node must sit between track mask push (" + push + ") and pop (" + pop
                        + ") but is at " + barBodies.get(0));
    }

    // ------------------------------------------------------------------
    // Scenarios
    // ------------------------------------------------------------------

    private static Document buildDocument(boolean pageHidden) {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 800px; height: 600px;");

        // position:relative makes the wrapper a stacking context so the page
        // display toggle exercises the incremental paint-list splice.
        Element wrapper = document.createElement("div");
        wrapper.setAttribute("style", "position: relative;");
        document.body.appendChild(wrapper);

        Element page = document.createElement("div");
        page.setAttribute("style", "width: 800px; height: 400px; overflow-x: hidden; overflow-y: auto;"
                + (pageHidden ? " display: none;" : ""));
        wrapper.appendChild(page);

        Element spacer = document.createElement("div");
        spacer.setAttribute("style", "height: 60px;");
        page.appendChild(spacer);

        Element track = document.createElement("div");
        track.setAttribute("class", "progress-2");
        track.setAttribute("style", "width: 300px; height: 22px; padding: 3px;"
                + " border: 2px solid #1e1e1f; background: #252628; overflow: hidden;");
        page.appendChild(track);

        Element bar = document.createElement("div");
        bar.setAttribute("class", "progress-2-bar");
        // Frozen at the animation's exit extreme: fully past the track's right edge.
        // The inset shadows are the real ore.css trio — they push a mask at the
        // bar's own rect while the animated translate is on the pose, which is
        // exactly where the empty-scissor-intersection bug lived.
        bar.setAttribute("style", "width: 45%; height: 100%; background: #3c8527;"
                + " box-shadow: inset 0 -4px #2a641c, inset 3px 3px rgba(255,255,255,0.3),"
                + " inset -3px -6px rgba(255,255,255,0.12);"
                + " transform: translateX(600px);");
        track.appendChild(bar);

        return document;
    }

    private static Element findByClass(Element root, String className) {
        if (root.getClassNames().contains(className)) return root;
        for (Element child : root.getChildren()) {
            Element found = findByClass(child, className);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void translatedBarStaysInsideTrackScissor() {
        AtomicReference<AABB> scissor = new AtomicReference<>();
        Mask.testScissorSink = scissor::set;
        Recording recording = new Recording(scissor);
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Document document = buildDocument(false);
            document.commitRenderState();

            Element track = findByClass(document.body, "progress-2");
            Element bar = findByClass(document.body, "progress-2-bar");
            assertNotNull(track);
            assertNotNull(bar);

            assertPaintListMaskStructure(document, track, bar);

            Object poseStack = newPoseStack();
            walkPaintList(document, poseStack);
            Graph.endBatch();

            assertBarNeverEscapesTrack(recording, track, bar);
        } finally {
            Mask.testScissorSink = null;
            AuiServices.setRender(previous);
        }
    }

    @Test
    void translatedBarStaysInsideTrackScissorAfterPageSwitchSplice() {
        AtomicReference<AABB> scissor = new AtomicReference<>();
        Mask.testScissorSink = scissor::set;
        Recording recording = new Recording(scissor);
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Document document = buildDocument(true);
            document.commitRenderState();

            Element page = findByClass(document.body, "progress-2").parentElement;
            Element track = findByClass(document.body, "progress-2");
            Element bar = findByClass(document.body, "progress-2-bar");

            // Simulate the ore page switch: display none -> block triggers a
            // REORDER that goes through the incremental paint-list splice.
            page.setAttribute("style", "width: 800px; height: 400px; overflow-x: hidden; overflow-y: auto;");
            document.commitRenderState();

            assertPaintListMaskStructure(document, track, bar);

            Object poseStack = newPoseStack();
            walkPaintList(document, poseStack);
            Graph.endBatch();

            assertBarNeverEscapesTrack(recording, track, bar);
        } finally {
            Mask.testScissorSink = null;
            AuiServices.setRender(previous);
        }
    }

    // ------------------------------------------------------------------
    // Full-fidelity scenario: real example.html + real ore.css
    // ------------------------------------------------------------------

    private static final Path ORE_DIR = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore");

    private static Document loadRealOreDocument() throws Exception {
        String html = Files.readString(ORE_DIR.resolve("example.html"));
        String css = Files.readString(ORE_DIR.resolve("ore.css"));
        // Inline the stylesheet so the headless run does not depend on the
        // external resource loader; the CSS text itself is untouched.
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

    /** Switches the showcase to the feedback page like the navbar radios do. */
    private static void showFeedbackPage(Document document) {
        Element foundations = findById(document.body, "ore-page-foundations");
        Element feedback = findById(document.body, "ore-page-feedback");
        assertNotNull(foundations, "foundations radio missing");
        assertNotNull(feedback, "feedback radio missing");
        // Mirror the real click path: setChecked maintains the checked state and
        // (unlike the raw attribute) re-recalcs the whole document because
        // :checked ~ sibling selectors may depend on it.
        foundations.setChecked(false);
        feedback.setChecked(true);
    }

    private static Element findIndeterminateTrack(Document document) {
        return findByClass(document.body, "progress-2-indeterminate");
    }

    /**
     * Same clip assertion as {@link #assertBarNeverEscapesTrack} but keyed on the
     * marker color the test stamped onto the real bar, so other green widgets on
     * the page cannot trigger false positives.
     */
    private static String findEscapes(Recording recording, Element track) {
        Rect trackRect = Rect.of(track);
        float tx0 = (float) trackRect.getBodyRectPosition().x;
        float ty0 = (float) trackRect.getBodyRectPosition().y;
        float tx1 = tx0 + (float) trackRect.getBodyRectSize().width();
        float ty1 = ty0 + (float) trackRect.getBodyRectSize().height();

        StringBuilder failures = new StringBuilder();
        for (Submission s : recording.submissions) {
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Vertex v : s.vertices) {
                if (!v.isMarker()) continue;
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
            if (minX > maxX) continue;
            boolean outside = minX < tx0 - 0.5f || maxX > tx1 + 0.5f
                    || minY < ty0 - 0.5f || maxY > ty1 + 0.5f;
            if (!outside) continue;
            if (!scissorProtects(s.scissor, tx0, ty0, tx1, ty1)) {
                failures.append("marker verts [x ").append(minX).append("..").append(maxX)
                        .append(" y ").append(minY).append("..").append(maxY)
                        .append("] track=[x ").append(tx0).append("..").append(tx1)
                        .append(" y ").append(ty0).append("..").append(ty1)
                        .append("] scissor=").append(s.scissor).append('\n');
            }
        }
        return failures.toString();
    }

    /**
     * Per-frame structural check: the bar must appear exactly once per phase and
     * every bar node must sit between the track's mask push/pop. A duplicated or
     * misplaced bar node is the signature of a corrupt incremental splice.
     */
    private static String findPaintListAnomalies(Document document, Element track, Element bar) {
        List<RenderNode> nodes = document.getPaintList();
        int push = -1, pop = -1;
        List<Integer> barNodes = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);
            if (node instanceof RenderNode.MaskPushNode m && m.target() == track) push = i;
            if (node instanceof RenderNode.MaskPopNode m && m.target() == track) pop = i;
            if (RenderNode.getRenderNodeTarget(node) == bar) barNodes.add(i);
        }
        StringBuilder out = new StringBuilder();
        if (push < 0 || pop <= push) {
            out.append("track mask push/pop missing or inverted: push=").append(push)
                    .append(" pop=").append(pop).append('\n');
        }
        long bodies = nodes.stream()
                .filter(n -> n instanceof RenderNode.ElementPhaseNode p
                        && p.target() == bar && p.phase() == Base.RenderPhase.BODY)
                .count();
        if (bodies != 1) {
            out.append("bar BODY node count=").append(bodies).append(" (expected 1)\n");
        }
        if (push >= 0 && pop > push) {
            for (int index : barNodes) {
                if (index <= push || index >= pop) {
                    RenderNode node = nodes.get(index);
                    out.append("bar node outside track mask range: index=").append(index)
                            .append(" node=").append(node.getClass().getSimpleName())
                            .append(node instanceof RenderNode.ElementPhaseNode p ? "/" + p.phase() : "")
                            .append(" push=").append(push).append(" pop=").append(pop).append('\n');
                }
            }
        }
        return out.toString();
    }

    @Test
    void realPageIndeterminateBarStaysClippedAcrossAnimationCycle() throws Exception {
        AtomicReference<AABB> scissor = new AtomicReference<>();
        Mask.testScissorSink = scissor::set;
        Recording recording = new Recording(scissor);
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Document document = loadRealOreDocument();
            Object poseStack = newPoseStack();

            // First frame: initial page (foundations) commits.
            renderFrame(document, poseStack, 1920, 1080);

            // Switch to the feedback page (display none -> block splice).
            showFeedbackPage(document);
            renderFrame(document, poseStack, 1920, 1080);

            Element track = findIndeterminateTrack(document);
            assertNotNull(track, "indeterminate track missing on feedback page");
            Element bar = findByClass(track, "progress-2-bar");
            assertNotNull(bar, "indeterminate bar missing");
            // Marker-color the background AND the inset shadows: the escape the
            // user saw was the shadow layers drawing unclipped, so both must be
            // tracked. The animation (transform) comes from the class and is
            // unaffected by these inline overrides.
            bar.setAttribute("style", "background-color: #0c2238;"
                    + " box-shadow: inset 0 -4px #0c2238, inset 3px 3px #0c2238, inset -3px -6px #0c2238;");
            renderFrame(document, poseStack, 1920, 1080);

            Set<String> observedTransforms = new HashSet<>();
            StringBuilder allFailures = new StringBuilder();
            for (int frame = 0; frame < 48; frame++) {
                Thread.sleep(25); // real wall clock drives the 1.1s keyframe cycle
                recording.submissions.clear();
                renderFrame(document, poseStack, 1920, 1080,
                        () -> observedTransforms.add(bar.getComputedStyle().transform));
                String transform = bar.getComputedStyle().transform;
                String failures = findEscapes(recording, track);
                if (!failures.isEmpty()) {
                    allFailures.append("frame ").append(frame)
                            .append(" transform=").append(transform).append('\n')
                            .append(failures);
                }
                String structural = findPaintListAnomalies(document, track, bar);
                if (!structural.isEmpty()) {
                    allFailures.append("frame ").append(frame)
                            .append(" transform=").append(transform)
                            .append(" paint-list anomaly:\n").append(structural);
                }
            }
            assertTrue(observedTransforms.size() > 1,
                    "the indeterminate animation must actually advance, saw: " + observedTransforms);
            if (allFailures.length() > 0) {
                fail("indeterminate bar escaped the overflow:hidden track:\n" + allFailures);
            }
        } finally {
            Mask.testScissorSink = null;
            AuiServices.setRender(previous);
        }
    }
}
