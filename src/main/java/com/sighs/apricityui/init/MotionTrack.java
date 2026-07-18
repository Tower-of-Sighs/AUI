package com.sighs.apricityui.init;

import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Transition;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class MotionTrack {
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

    MotionTrack(Document owner) {
        this.owner = owner;
    }

    void clear() {
        flags.clear();
    }

    void removeElement(Element element) {
        if (element == null) return;
        flags.keySet().removeIf(e -> element.uuid.equals(e.uuid));
    }

    void setTransitionActive(Element element, boolean active) {
        setFlag(element, FLAG_TRANSITION, active);
    }

    void setHasAnimationSpec(Element element, boolean hasSpec) {
        setFlag(element, FLAG_ANIMATION_SPEC, hasSpec);
    }

    boolean stepRender() {
        if (!StyleFrameCache.isActive()) return false;
        if (flags.isEmpty()) return false;

        boolean changed = false;
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
                continue;
            }

            Style base = element.getRawComputedStyle();

            // 避免 tick 还没来得及同步 animation spec 时，render 侧重复做无意义工作。
            if (hasAnimationSpec && !Animation.hasAnimationSpec(base)) {
                setHasAnimationSpec(element, false);
                hasAnimationSpec = false;
            }

            if (!hasTransition && !hasAnimationSpec) continue;

            Style animated = base.clone();
            if (hasTransition) {
                boolean stillActive = Transition.updateStyle(element, animated);
                if (!stillActive) {
                    setTransitionActive(element, false);
                }
            }
            if (hasAnimationSpec) {
                Animation.updateStyle(element, animated);
            }
            // 为当帧提供“带 motion 的 computed style”
            invalidateMotionCaches(element, base, animated);
            StyleFrameCache.put(element, animated);
            changed = true;

            // motion 可能改变 transform/filter/opacity 等渲染关键字段，需要确保对应缓存不会跨帧黏住旧值
            if (!Objects.equals(animated.transform, base.transform)
                    || (hasAnimationSpec && Animation.affectsTransform(base))) {
                element.getRenderer().transform.clear();
            }
            if (!Objects.equals(animated.filter, base.filter) || !Objects.equals(animated.opacity, base.opacity)) {
                element.getRenderer().filter.clear();
            }
            if (!Objects.equals(animated.backdropFilter, base.backdropFilter)) {
                element.getRenderer().backdropFilter.clear();
            }
        }
        return changed;
    }

    private static void invalidateMotionCaches(Element element, Style base, Style animated) {
        RenderElement renderer = element.getRenderer();

        if (differsAny(base, animated, LAYOUT_PROPS)) {
            element.forEachRoute(e -> e.getRenderer().invalidateLayoutVersion());
            element.forEachRoute(e -> e.getRenderer().size.clear());
            element.forEachRoute(e -> e.getRenderer().box.clear());
            renderer.position.clear();
            if (element.parentElement != null) {
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            } else {
                renderer.position.clear();
            }
        } else if (differsAny(base, animated, VISUAL_BOX_PROPS)) {
            renderer.box.clear();
        }

        // Text.of() is cached independently from the computed style. A color
        // transition otherwise leaves the glyph cache pinned to its first frame:
        // black on hover enter and white on hover leave.
        if (differsAny(base, animated, Style.getTextProp())) {
            renderer.text.clear();
            renderer.wrappedText.clear();
        }

        if (!Objects.equals(base.transform, animated.transform)) {
            renderer.invalidateTransformVersion();
        }

        if (differsAny(base, animated, BACKGROUND_PROPS)) {
            renderer.background.clear();
        }
    }

    private static boolean differsAny(Style base, Style animated, String[] props) {
        for (String prop : props) {
            if (!Objects.equals(base.get(prop), animated.get(prop))) {
                return true;
            }
        }
        return false;
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
    }
}
