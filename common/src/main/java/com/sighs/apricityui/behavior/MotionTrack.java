package com.sighs.apricityui.behavior;

import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.style.Transition;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.parser.CSS;

public final class MotionTrack {
    private static final int FLAG_TRANSITION = 1;
    private static final int FLAG_ANIMATION_SPEC = 1 << 1;
    private static final String[] LAYOUT_PROPS = {
            "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "boxSizing",
            "position", "top", "right", "bottom", "left",
            "margin", "marginTop", "marginBottom", "marginLeft", "marginRight",
            "padding", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight",
            "border", "borderTop", "borderBottom", "borderLeft", "borderRight"
    };
    private static final String[] VISUAL_BOX_PROPS = {
            "borderRadius", "boxShadow",
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition",
            "borderImage", "borderImageSource", "borderImageSlice", "borderImageWidth", "borderImageOutset", "borderImageRepeat"
    };
    private static final String[] BACKGROUND_PROPS = {
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition"
    };

    private final Document owner;
    private final ConcurrentHashMap<Element, Integer> flags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Element, MotionStyles> lastMotionStyles = new ConcurrentHashMap<>();
    private final Set<Element> hitTestRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Element> layoutRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Element> geometryRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean visualChanges = false;

    public MotionTrack(Document owner) {
        this.owner = owner;
    }

    public void clear() {
        flags.clear();
        lastMotionStyles.clear();
        hitTestRoots.clear();
        layoutRoots.clear();
        geometryRoots.clear();
        visualChanges = false;
    }

    public void removeElement(Element element) {
        if (element == null) return;
        flags.keySet().removeIf(e -> element.uuid.equals(e.uuid));
        lastMotionStyles.keySet().removeIf(e -> element.uuid.equals(e.uuid));
        hitTestRoots.remove(element);
        layoutRoots.remove(element);
        geometryRoots.remove(element);
    }

    public void setTransitionActive(Element element, boolean active) {
        setFlag(element, FLAG_TRANSITION, active);
    }

    public void setHasAnimationSpec(Element element, boolean hasSpec) {
        setFlag(element, FLAG_ANIMATION_SPEC, hasSpec);
    }

    public boolean stepRender() {
        hitTestRoots.clear();
        layoutRoots.clear();
        geometryRoots.clear();
        visualChanges = false;
        if (!StyleFrameCache.isActive()) return false;
        if (flags.isEmpty()) return false;

        boolean requiresGeometryCommit = false;
        for (Map.Entry<Element, Integer> entry : flags.entrySet()) {
            Element element = entry.getKey();
            if (element == null || element.document != owner) {
                flags.remove(element);
                continue;
            }

            int value = entry.getValue() == null ? 0 : entry.getValue();
            boolean hasTransition = (value & FLAG_TRANSITION) != 0;
            boolean hasAnimationSpec = (value & FLAG_ANIMATION_SPEC) != 0;
            if (!hasTransition && !hasAnimationSpec) {
                flags.remove(element);
                lastMotionStyles.remove(element);
                continue;
            }

            Style base = element.getRawComputedStyle();

            // 避免 tick 还没来得及同步 animation spec 时，render 侧重复做无意义工作。
            if (hasAnimationSpec && !Animation.hasAnimationSpec(base)) {
                setHasAnimationSpec(element, false);
                hasAnimationSpec = false;
            }

            if (!hasTransition && !hasAnimationSpec) continue;

            visualChanges = true;

            MotionStyles motionStyles = lastMotionStyles.computeIfAbsent(element, ignored -> new MotionStyles());
            Style previousMotionStyle = motionStyles.initialized ? motionStyles.last : null;
            Style animated = motionStyles.work;
            Style previousBuffer = motionStyles.last;
            animated.copyFrom(base);
            boolean completedLayoutTransition = false;
            boolean completedTransformTransition = false;
            boolean completedRectTransition = false;
            if (hasTransition) {
                completedLayoutTransition = Transition.affectsLayout(element);
                completedTransformTransition = Transition.affectsTransform(element);
                completedRectTransition = Transition.affectsRect(element);
                boolean stillActive = Transition.updateStyle(element, animated);
                if (!stillActive) {
                    // The final sampled transition value can be identical to the raw target style.
                    // It still must invalidate the half-way committed geometry from the prior frame.
                    requiresGeometryCommit |= invalidateCompletedTransitionCaches(
                            element,
                            completedLayoutTransition,
                            completedTransformTransition,
                            completedRectTransition
                    );
                    setTransitionActive(element, false);
                }
            }
            if (hasAnimationSpec) {
                Animation.updateStyle(element, animated);
            }
            // 为当帧提供“带 motion 的 computed style”
            requiresGeometryCommit |= invalidateMotionCaches(
                    element,
                    previousMotionStyle == null ? base : previousMotionStyle,
                    animated
            );
            StyleFrameCache.put(element, animated);
            motionStyles.last = animated;
            motionStyles.work = previousBuffer;
            motionStyles.initialized = true;

            // CSS color is inherited.  A parent's transition is composited after
            // its base style is resolved, so descendants must sample that frame
            // value too.  Leaving them with the value inherited while the
            // transition was primed freezes text such as <translation> at white
            // after :hover has ended.
            Style inheritedBefore = previousMotionStyle == null ? base : previousMotionStyle;
            if (!Objects.equals(inheritedBefore.color, animated.color)) {
                refreshInheritedColorSubtree(element);
            }

            if (!flags.containsKey(element)) {
                lastMotionStyles.remove(element);
            }
        }
        return requiresGeometryCommit;
    }

    public boolean hasVisualChanges() {
        return visualChanges;
    }

    private void refreshInheritedColorSubtree(Element root) {
        if (root == null) return;
        ArrayDeque<Element> pending = new ArrayDeque<>(root.children);
        while (!pending.isEmpty()) {
            Element element = pending.removeFirst();
            if (element.document != owner) continue;
            element.recomputeStyleSelf();
            pending.addAll(element.children);
        }
    }

    public Set<Element> drainHitTestRoots() {
        return drainRoots(hitTestRoots);
    }

    public Set<Element> drainLayoutRoots() {
        return drainRoots(layoutRoots);
    }

    public Set<Element> drainGeometryRoots() {
        return drainRoots(geometryRoots);
    }

    private static Set<Element> drainRoots(Set<Element> roots) {
        if (roots.isEmpty()) return Set.of();
        Set<Element> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(roots);
        roots.clear();
        return result;
    }

    private boolean invalidateMotionCaches(Element element, Style base, Style animated) {
        RenderElement renderer = element.getRenderer();
        boolean requiresGeometryCommit = false;

        if (differsAny(base, animated, LAYOUT_PROPS)) {
            invalidateLayoutMotion(element, base, animated);
            requiresGeometryCommit = true;
        } else if (differsAny(base, animated, VISUAL_BOX_PROPS)) {
            renderer.clearVisualBoxCache();
            renderer.invalidateStyleVersion();
        }

        // Text.of() is cached independently from the computed style. A color
        // transition otherwise leaves the glyph cache pinned to its first frame:
        // black on hover enter and white on hover leave.
        if (differsAny(base, animated, Style.getTextProp())) {
            renderer.text.clear();
            renderer.wrappedText.clear();
            element.forEachRoute(routeElement -> routeElement.getRenderer().invalidateTextVersion());
        }

        if (!Objects.equals(base.transform, animated.transform)) {
            renderer.invalidateTransformVersion();
            renderer.transform.clear();
            geometryRoots.add(element);
            hitTestRoots.add(element);
            requiresGeometryCommit = true;
        }

        if (!Objects.equals(base.filter, animated.filter) || !Objects.equals(base.opacity, animated.opacity)) {
            renderer.filter.clear();
        }
        if (!Objects.equals(base.backdropFilter, animated.backdropFilter)) {
            renderer.backdropFilter.clear();
        }

        if (differsAny(base, animated, BACKGROUND_PROPS)) {
            renderer.background.clear();
            renderer.invalidateStyleVersion();
        }
        return requiresGeometryCommit;
    }

    private void invalidateLayoutMotion(Element element, Style base, Style animated) {
        RenderElement renderer = element.getRenderer();
        layoutRoots.add(element);
        boolean affectsNormalFlow = Layout.isInFlow(base) || Layout.isInFlow(animated);
        if (affectsNormalFlow) {
            element.forEachRoute(e -> {
                RenderElement routeRenderer = e.getRenderer();
                routeRenderer.invalidateLayoutVersion();
                routeRenderer.size.clear();
                routeRenderer.box.clear();
            });
            if (element.parentElement != null) {
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
                hitTestRoots.add(element.parentElement);
            } else {
                hitTestRoots.add(element);
            }
        } else {
            renderer.invalidateLayoutVersion();
            renderer.size.clear();
            renderer.box.clear();
            hitTestRoots.add(element);
        }
        renderer.invalidateLayoutSubtree();
    }

    private static boolean differsAny(Style base, Style animated, String[] props) {
        for (String prop : props) {
            if (!Objects.equals(base.get(prop), animated.get(prop))) {
                return true;
            }
        }
        return false;
    }

    private boolean invalidateCompletedTransitionCaches(Element element, boolean affectsLayout,
                                                        boolean affectsTransform, boolean affectsRect) {
        RenderElement renderer = element.getRenderer();
        boolean requiresGeometryCommit = false;
        if (affectsLayout) {
            Style style = element.getRawComputedStyle();
            invalidateLayoutMotion(element, style, style);
            requiresGeometryCommit = true;
        } else if (affectsRect) {
            renderer.clearVisualBoxCache();
            renderer.background.clear();
            renderer.invalidateStyleVersion();
        }
        if (affectsTransform) {
            renderer.invalidateTransformVersion();
            geometryRoots.add(element);
            hitTestRoots.add(element);
            requiresGeometryCommit = true;
        }
        renderer.transform.clear();
        renderer.filter.clear();
        renderer.backdropFilter.clear();
        renderer.background.clear();
        renderer.text.clear();
        renderer.wrappedText.clear();
        return requiresGeometryCommit;
    }

    private static boolean differsAny(Style base, Style animated, Iterable<String> props) {
        for (String prop : props) {
            if (!Objects.equals(base.get(prop), animated.get(prop))) {
                return true;
            }
        }
        return false;
    }

    private void setFlag(Element element, int flag, boolean enabled) {
        if (element == null || element.document != owner) return;
        flags.compute(element, (e, old) -> {
            int value = old == null ? 0 : old;
            if (enabled) value |= flag;
            else value &= ~flag;
            return value == 0 ? null : value;
        });
        if (!flags.containsKey(element)) {
            lastMotionStyles.remove(element);
        }
    }

    private static final class MotionStyles {
        Style last = new Style();
        Style work = new Style();
        boolean initialized;
    }
}
