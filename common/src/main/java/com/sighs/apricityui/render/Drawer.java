package com.sighs.apricityui.render;

import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.DynamicRangeLimit;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.style.Transition;

import java.util.*;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.parser.CSS;

public class Drawer {
    public static final int REPAINT = 1;
    public static final int REORDER = 2;
    public static final int RELAYOUT = 4;
    public static final int HITTEST = 8;
    public static final int COMMIT_LAYOUT = 16;

    public static void flushUpdates(Document document) {
        Set<Element> dirtyElements = document.getDirtyElements();
        if (dirtyElements.isEmpty()) return;

        List<Element> sortedDirty = consolidateDirtyElements(dirtyElements);
        Set<Element> reorderRoots = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Element e : sortedDirty) {
            // 如果标记了 RELAYOUT，通常意味着尺寸变化，这往往也会影响绘制顺序或边界
            if (e.hasDirtyFlag(RELAYOUT)) {
                e.forEachRoute(element -> element.getRenderer().invalidateLayoutVersion());
                e.forEachRoute(element -> element.getRenderer().size.clear());
                e.forEachRoute(element -> element.getRenderer().position.clear());
                // 布局变化通常需要重绘，但不一定需要重排队列（除非影响了层叠上下文）
                // 但为了安全起见，布局变动通常触发 REPAINT
                e.addDirtyFlags(REPAINT);
            }

            if (e.hasDirtyFlag(REORDER)) {
                // REORDER 通常会同时标记一批同层元素；按层叠上下文去重，避免同一帧重复 rebuild 大子树。
                Element contextRoot = findNearestStackingContext(e);
                if (contextRoot != null) {
                    reorderRoots.add(contextRoot);
                }
            }
        }

        if (!reorderRoots.isEmpty()) {
            updatePaintList(document, reorderRoots);
        }

        for (Element e : sortedDirty) {
            e.clearDirtyFlags();
        }
        dirtyElements.clear();
    }

    private static List<Element> consolidateDirtyElements(Set<Element> dirtyElements) {
        List<Element> list = new ArrayList<>(dirtyElements);
        list.sort(Comparator.comparingInt(Element::getDepth));
        return list;
    }

    public static ArrayList<RenderNode> createPaintList(Element body) {
        ArrayList<RenderNode> paintList = new ArrayList<>();
        // 同一次构建共享的“已提升”集合：被子作用域跳过的元素记入其中，
        // 递归到它们原本的 DOM 父级时据此跳过，保证每个元素只画一次。
        Set<Element> hoisted = Collections.newSetFromMap(new IdentityHashMap<>());
        processStackingContext(body, paintList, hoisted);
        for (Element topLayer : collectTopLayerRoots(body)) {
            processStackingContext(topLayer, paintList, hoisted);
        }
        return paintList;
    }

    private static List<Element> collectTopLayerRoots(Element root) {
        if (root == null) return List.of();
        ArrayList<Element> result = new ArrayList<>();
        collectTopLayerRoots(root, result);
        return result;
    }

    private static void collectTopLayerRoots(Element parent, List<Element> result) {
        for (Element child : parent.getRenderChildren()) {
            if (child.isTopLayer()) result.add(child);
            collectTopLayerRoots(child, result);
        }
    }

    private static void updatePaintList(Document document, Set<Element> reorderRoots) {
        List<RenderNode> globalList = document.getPaintList();
        Element paintRoot = getDocumentPaintRoot(document);
        if (paintRoot == null) {
            globalList.clear();
            return;
        }

        // Detached nodes have already lost the ancestry used to find an incremental subtree boundary.
        // Remove them before replacing a connected subtree so stale paint nodes cannot survive the splice.
        globalList.removeIf(node -> {
            Element target = RenderNode.getRenderNodeTarget(node);
            return target != null && !target.isConnected();
        });

        List<Element> roots = minimizeRoots(reorderRoots);
        boolean rebuildAll = globalList.isEmpty();

        for (Element root : roots) {
            if (root == null || !root.isConnected() || root == paintRoot) {
                rebuildAll = true;
                break;
            }

            ArrayList<RenderNode> rebuiltSubtree = createPaintList(root);
            if (!updateGlobalPaintList(globalList, root, rebuiltSubtree)) {
                rebuildAll = true;
                break;
            }
        }

        if (rebuildAll) {
            ArrayList<RenderNode> rebuilt = createPaintList(paintRoot);
            globalList.clear();
            globalList.addAll(rebuilt);
        }
    }

    private static Element getDocumentPaintRoot(Document document) {
        if (document == null) return null;
        if (document.documentElement != null) return document.documentElement;
        return document.body;
    }

    /**
     * 注意：paintList 构建阶段不做裁剪/视锥剔除。
     * <p>
     * 过去这里用 {@code Rect.of(...).getVisualBounds().intersects(...)} 做剔除，
     * 但该路径需要计算完整 Rect（Position/Box/Background），在频繁 REORDER 时会造成极高分配。
     * 实际上渲染节点在 {@link RenderNode.ElementPhaseNode#render} 中已经会做 clip 检查，
     * hitTest 也有自己的 mask stack，因此这里保持“只负责顺序”，把“是否可见”交给渲染阶段处理。
     */
    private static void processStackingContext(Element contextRoot, List<RenderNode> paintList,
                                               Set<Element> hoisted) {
        Style rootStyle = contextRoot.getRawComputedStyle();
        if ("none".equals(rootStyle.display)) {
            // CSS display:none should suppress the entire subtree, not just the node itself.
            return;
        }

        boolean hasMaskImage = com.sighs.apricityui.style.MaskImage.hasMask(contextRoot);
        if (hasMaskImage) {
            paintList.add(new RenderNode.MaskImagePushNode(contextRoot));
        }

        boolean hasClipPath = !"none".equals(rootStyle.clipPath);
        if (hasClipPath) {
            paintList.add(new RenderNode.ClipPathPushNode(contextRoot));
        }

        String backdropFilterStr = rootStyle.backdropFilter;
        if (backdropFilterStr != null && !backdropFilterStr.equals("none")) {
            Filter.FilterState bfState = Filter.getBackdropFilterOf(contextRoot);
            if (!bfState.isEmpty()) {
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[Drawer] Add BackdropFilterNode target={} style='{}' state={}",
//                        contextRoot.uuid, backdropFilterStr, bfState
//                );
                paintList.add(new RenderNode.BackdropFilterNode(contextRoot));
            } else {
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[Drawer] Skip BackdropFilterNode target={} style='{}' -> empty",
//                        contextRoot.uuid, backdropFilterStr
//                );
            }
        }

        boolean hasFilter = hasCompositedFilter(contextRoot, rootStyle);
        boolean hasBlend = hasMixBlendMode(rootStyle);
        boolean hasIsolation = hasIsolation(rootStyle);
        if (hasIsolation) paintList.add(new RenderNode.IsolationPushNode(contextRoot));
        if (hasBlend) paintList.add(new RenderNode.BlendPushNode(contextRoot));
        if (hasFilter) {
            paintList.add(new RenderNode.FilterPushNode(contextRoot));
        }

        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.SHADOW));

        List<Element> children = contextRoot.getRenderChildren();
        if (children.isEmpty()) {
            boolean needsMask = Interaction.clipsOverflow(rootStyle);
            if (needsMask) {
                // CSS overflow clips an element's own content, but not its
                // shadow or border. The padding box is the overflow clip edge.
                paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
                paintList.add(new RenderNode.MaskPushNode(contextRoot));
                appendBodyRenderNodes(contextRoot, paintList);
                appendForegroundRenderNodes(contextRoot, paintList);
                paintList.add(new RenderNode.MaskPopNode(contextRoot));
            } else {
                appendBodyRenderNodes(contextRoot, paintList);
                paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
                appendForegroundRenderNodes(contextRoot, paintList);
            }
            if (contextRoot.mayRenderScrollbar()) {
                paintList.add(new RenderNode.ScrollbarNode(contextRoot));
            }
            if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot));
            if (hasBlend) paintList.add(new RenderNode.BlendPopNode(contextRoot));
            if (hasIsolation) paintList.add(new RenderNode.IsolationPopNode(contextRoot));
            if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
            if (hasMaskImage) paintList.add(new RenderNode.MaskImagePopNode(contextRoot));
            return;
        }

        List<Paintable> negativeZ = new ArrayList<>();
        List<Element> normalFlow = new ArrayList<>();
        List<Paintable> autoOrZeroContext = new ArrayList<>();
        List<Paintable> positiveZ = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            // Top-layer boxes retain their DOM parent but paint in a separate
            // root after the normal document, outside ancestor overflow clips.
            if (child.isTopLayer()) continue;
            Style style = child.getRawComputedStyle();
            if ("none".equals(style.display)) {
                continue;
            }
            // 已被外层作用域提升排序的元素（见 hoistPaintedDescendants）只画一次。
            if (hoisted.contains(child)) continue;
            String zIndexStr = style.zIndex;
            int zValue = "auto".equals(zIndexStr) ? 0 : Size.parse(zIndexStr);
            double translateZ = Transform.getTranslateZ(style.transform);

            // 按照规范，z-index≠auto 的 positioned 元素、filter/opacity/transform 等才会触发层叠上下文
            boolean createsContext = createsPaintStackingContext(child, style);

            if (createsContext) {
                Paintable p = new Paintable(child, zValue, translateZ, i);
                if (zValue < 0) {
                    negativeZ.add(p);
                } else if (zValue == 0) {
                    autoOrZeroContext.add(p);
                } else {
                    positiveZ.add(p);
                }
                continue;
            }

            // 关键：保持 CSS 的大体绘制顺序
            // - 普通流（static, 不创建层叠上下文）应当先绘制
            // - position:relative/absolute 且 z-index:auto 的节点不创建层叠上下文，
            //   但自身应当排在普通流之后绘制（CSS2.1 Appendix E 第 6 层），
            //   否则会出现典型问题：后面的普通节点覆盖前面的 relative 节点
            // - 这类节点对 z-index 排序是“透明”的：它们带 z-index 的后代按规范
            //   提升到最近的分组作用域（层叠上下文或裁剪边界）排序，
            //   否则下拉菜单这类浮层会被后续普通流内容盖住
            if (isTransparentGroupingScope(child, style)) {
                hoistPaintedDescendants(child, negativeZ, autoOrZeroContext, positiveZ, hoisted);
            }
            String position = style.position == null ? "static" : style.position;
            if (!"static".equals(position)) {
                autoOrZeroContext.add(new Paintable(child, 0, translateZ, i));
            } else {
                normalFlow.add(child);
            }
        }

        if (negativeZ.size() > 1) negativeZ.sort(PAINTABLE_ORDER);
        // auto/0 组内允许 translateZ 影响前后关系；同值再回落到 DOM 顺序
        if (autoOrZeroContext.size() > 1) autoOrZeroContext.sort(PAINTABLE_ORDER);
        if (positiveZ.size() > 1) positiveZ.sort(PAINTABLE_ORDER);

        boolean needsMask = Interaction.clipsOverflow(rootStyle);
        boolean splitContentForNegativeZ = !negativeZ.isEmpty();

        if (needsMask) {
            if (splitContentForNegativeZ) {
                paintList.add(new RenderNode.ElementBackgroundNode(contextRoot));
            }
            paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
            paintList.add(new RenderNode.MaskPushNode(contextRoot));
            if (!splitContentForNegativeZ) appendBodyRenderNodes(contextRoot, paintList);
        } else {
            if (splitContentForNegativeZ) {
                paintList.add(new RenderNode.ElementBackgroundNode(contextRoot));
            } else {
                appendBodyRenderNodes(contextRoot, paintList);
            }
            paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
        }

        for (Paintable p : negativeZ) processStackingContext(p.element, paintList, hoisted);
        if (splitContentForNegativeZ) {
            appendContentRenderNodes(contextRoot, paintList);
        }
        for (Element e : normalFlow) processStackingContext(e, paintList, hoisted);
        for (Paintable p : autoOrZeroContext) processStackingContext(p.element, paintList, hoisted);
        for (Paintable p : positiveZ) processStackingContext(p.element, paintList, hoisted);
        appendForegroundRenderNodes(contextRoot, paintList);

        if (needsMask) paintList.add(new RenderNode.MaskPopNode(contextRoot));
        if (contextRoot.mayRenderScrollbar()) {
            paintList.add(new RenderNode.ScrollbarNode(contextRoot));
        }
        if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot));
        if (hasBlend) paintList.add(new RenderNode.BlendPopNode(contextRoot));
        if (hasIsolation) paintList.add(new RenderNode.IsolationPopNode(contextRoot));
        if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
        if (hasMaskImage) paintList.add(new RenderNode.MaskImagePopNode(contextRoot));
    }

    /**
     * 组内排序：先 z-index，再 translateZ。同值时依赖 List.sort 的稳定性保持插入序——
     * 分组循环按 DFS 树序插入，因此同值元素按规范的“树序”排列，
     * 这对跨层级提升进来的元素（domOrder 只在兄弟间有意义）尤其重要。
     */
    private static final Comparator<Paintable> PAINTABLE_ORDER = Comparator
            .comparingInt(Paintable::zValue)
            .thenComparingDouble(Paintable::translateZ);

    private record Paintable(Element element, int zValue, double translateZ, int domOrder) {
    }

    /**
     * 将透明分组容器（见 {@link #isTransparentGroupingScope}）内、按 CSS 应参与
     * 当前作用域排序的后代提升到当前分组：z-index≠auto 的层叠上下文进入负/正 z 组，
     * z-index:auto/0 的层叠上下文进入 autoOrZero 组。提升只穿过透明容器，
     * 遇到层叠上下文或裁剪/遮罩边界即停止（否则 overflow 裁剪会失效）。
     * 被提升的元素记入 {@code hoisted}，递归到它们原本的 DOM 父级时跳过，保证只画一次。
     */
    private static void hoistPaintedDescendants(Element parent, List<Paintable> negativeZ,
                                                List<Paintable> autoOrZeroContext, List<Paintable> positiveZ,
                                                Set<Element> hoisted) {
        List<Element> children = parent.getRenderChildren();
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            if (child.isTopLayer()) continue;
            Style style = child.getRawComputedStyle();
            if ("none".equals(style.display)) continue;
            if (hoisted.contains(child)) continue;
            if (createsPaintStackingContext(child, style)) {
                String zIndexStr = style.zIndex;
                int zValue = "auto".equals(zIndexStr) ? 0 : Size.parse(zIndexStr);
                double translateZ = Transform.getTranslateZ(style.transform);
                Paintable p = new Paintable(child, zValue, translateZ, i);
                if (zValue < 0) {
                    negativeZ.add(p);
                } else if (zValue == 0) {
                    autoOrZeroContext.add(p);
                } else {
                    positiveZ.add(p);
                }
                hoisted.add(child);
                continue;
            }
            if (!isTransparentGroupingScope(child, style)) continue;
            hoistPaintedDescendants(child, negativeZ, autoOrZeroContext, positiveZ, hoisted);
        }
    }

    /**
     * “透明分组容器”：自身不创建层叠上下文（如 position:relative + z-index:auto），
     * 也不产生裁剪/遮罩边界。它的盒子和普通内容原地绘制，但带 z-index 的后代
     * 按 CSS2.1 Appendix E 提升到外层作用域参与排序。
     */
    private static boolean isTransparentGroupingScope(Element element, Style style) {
        if (createsPaintStackingContext(element, style)) return false;
        if (Interaction.clipsOverflow(style)) return false;
        if (!"none".equals(style.clipPath)) return false;
        return !com.sighs.apricityui.style.MaskImage.hasMask(element);
    }

    private static void appendBodyRenderNodes(Element contextRoot, List<RenderNode> paintList) {
        if (contextRoot instanceof BodyRenderNodeProvider provider) {
            List<RenderNode> nodes = provider.createBodyRenderNodes();
            if (nodes != null && !nodes.isEmpty()) {
                paintList.addAll(nodes);
                return;
            }
        }
        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BODY));
    }

    /**
     * Paints only the content portion of BODY after negative z-index descendants.
     * The host background has already been emitted before those descendants, as
     * required by the CSS stacking order, so it must not be emitted a second time.
     */
    private static void appendContentRenderNodes(Element contextRoot, List<RenderNode> paintList) {
        if (contextRoot instanceof BodyRenderNodeProvider provider) {
            List<RenderNode> nodes = provider.createBodyRenderNodes();
            if (nodes != null && !nodes.isEmpty()) {
                for (RenderNode node : nodes) {
                    if (node instanceof RenderNode.ElementBackgroundNode background
                            && background.target() == contextRoot) {
                        continue;
                    }
                    paintList.add(node);
                }
                return;
            }
        }
        paintList.add(new RenderNode.ElementContentNode(contextRoot));
    }

    private static void appendForegroundRenderNodes(Element contextRoot, List<RenderNode> paintList) {
        if (!(contextRoot instanceof ForegroundRenderNodeProvider provider)) return;
        List<RenderNode> nodes = provider.createForegroundRenderNodes();
        if (nodes != null && !nodes.isEmpty()) paintList.addAll(nodes);
    }

    private static List<Element> minimizeRoots(Set<Element> roots) {
        ArrayList<Element> list = new ArrayList<>(roots);
        list.sort(Comparator.comparingInt(Element::getDepth));

        ArrayList<Element> result = new ArrayList<>();
        for (Element candidate : list) {
            boolean covered = false;
            for (Element selected : result) {
                if (RenderNode.isSameOrDescendant(candidate, selected)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.add(candidate);
            }
        }
        return result;
    }


    private static Element findNearestStackingContext(Element e) {
        Element paintRoot = getDocumentPaintRoot(e == null ? null : e.document);
        if (e == null) return paintRoot;
        Element current = e.parentElement;
        while (current != null) {
            Style style = current.getRawComputedStyle();
            // 层叠上下文与裁剪边界都是分组作用域：z-index 提升不会越过它们，
            // 其子树在绘制列表中是连续区间，因此是最小的合法增量重建单位。
            if (current == paintRoot
                    || createsPaintStackingContext(current, style)
                    || Interaction.clipsOverflow(style)) {
                return current;
            }
            current = current.parentElement;
        }
        return paintRoot;
    }

    private static boolean updateGlobalPaintList(List<RenderNode> globalList, Element root, List<RenderNode> newSubtree) {
        int startIndex = -1;
        for (int i = 0; i < globalList.size(); i++) {
            if (RenderNode.getRenderNodeTarget(globalList.get(i)) == root) {
                startIndex = i;
                break;
            }
        }

        if (startIndex == -1) {
            return false;
        }

        int endIndex = startIndex + 1;
        while (endIndex < globalList.size()) {
            RenderNode node = globalList.get(endIndex);
            if (isNodeRelatedTo(node, root)) {
                endIndex++;
            } else {
                break;
            }
        }

        globalList.subList(startIndex, endIndex).clear();
        globalList.addAll(startIndex, newSubtree);
        return true;
    }

    private static boolean isNodeRelatedTo(RenderNode node, Element potentialParent) {
        Element target = RenderNode.getRenderNodeTarget(node);
        if (target != null) {
            return RenderNode.isSameOrDescendant(target, potentialParent);
        }
        return false;
    }


    private static boolean hasCompositedFilter(Element element, Style style) {
        if (!Filter.isDisabled(style.filter, style.opacity)) return true;
        if (DynamicRangeLimit.isActive(style.dynamicRangeLimit)) return true;
        if (Transition.affectsFilter(element)) return true;
        return Animation.affectsFilter(style);
    }

    private static boolean hasMixBlendMode(Style style) {
        if (style == null) return false;
        return CssBlendMode.parse(style.mixBlendMode) != CssBlendMode.Mode.NORMAL;
    }

    private static boolean hasIsolation(Style style) {
        return style != null && "isolate".equalsIgnoreCase(style.isolation == null ? "auto" : style.isolation.trim());
    }

    private static boolean createsPaintStackingContext(Element element, Style style) {
        if (style == null) return false;
        String zIndex = style.zIndex == null ? "auto" : style.zIndex;
        String position = style.position == null ? "static" : style.position;
        boolean hasBackdrop = style.backdropFilter != null && !style.backdropFilter.equals("none");
        // CSS2.1：仅 position ≠ static 而 z-index:auto 时【不】创建层叠上下文；
        // 只有 z-index ≠ auto 的 positioned 元素（或 flex/grid 子项）才因 z-index 创建。
        boolean zIndexedContext = !zIndex.equals("auto")
                && (!position.equals("static") || isFlexOrGridItem(element));
        return zIndexedContext
                || hasCompositedFilter(element, style)
                || hasBackdrop
                || hasMixBlendMode(style)
                || hasIsolation(style)
                || Transform.createsStackingContext(style.transform);
    }

    private static boolean isFlexOrGridItem(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return false;
        String display = parent.getRawComputedStyle().display;
        if (display == null) return false;
        return display.equals("flex") || display.equals("inline-flex")
                || display.equals("grid") || display.equals("inline-grid");
    }

}
