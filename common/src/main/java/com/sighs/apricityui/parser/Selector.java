package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.util.AuiLog;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.init.Element;

public class Selector {
    private static final Map<String, List<CompiledSelector>> SELECTOR_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SELECTOR_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    /**
     * 支持的伪类列表，单一来源。新加伪类时需同步：{@link Pseudo#matches}（求值）、
     * {@link Pseudo#mayMatch}（候选预筛）。{@link #isSupportedPseudo} 与 Index 的
     * 候选收集均由此驱动。
     */
    private static final Set<String> SUPPORTED_PSEUDOS = Set.of(
            "root", "first-child", "last-child", "only-child", "nth-child", "nth-last-child",
            "first-of-type", "last-of-type", "only-of-type", "nth-of-type", "nth-last-of-type",
            "hover", "active", "focus", "focus-visible", "focus-within", "disabled", "enabled",
            "required", "optional", "valid", "invalid", "in-range", "out-of-range", "read-only",
            "read-write", "placeholder-shown", "empty", "checked", "not", "is", "where"
    );

    public static void clearCompiledCache() {
        SELECTOR_CACHE.clear();
    }

    public static void warmUp(Iterable<String> selectors) {
        if (selectors == null) return;
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) continue;
            SELECTOR_CACHE.computeIfAbsent(selector, Selector::parseGroup);
        }
    }

    static int compiledCacheSize() {
        return SELECTOR_CACHE.size();
    }

    public record Specificity(int ids, int classes, int tags, int order) implements Comparable<Specificity> {
        @Override
        public int compareTo(Specificity o) {
            if (this.ids != o.ids) return Integer.compare(this.ids, o.ids);
            if (this.classes != o.classes) return Integer.compare(this.classes, o.classes);
            if (this.tags != o.tags) return Integer.compare(this.tags, o.tags);
            return Integer.compare(this.order, o.order);
        }
    }

    public record DebugDeclaration(String value, boolean important, boolean overridden) {
        public String displayValue() {
            return value + (important ? " !important" : "");
        }
    }

    public record DebugStyleBlock(String sourcePath, String selector, int ruleOrder,
                                  Map<String, DebugDeclaration> declarations) {
        public Map<String, String> styles() {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            declarations.forEach((property, declaration) -> result.put(property, declaration.displayValue()));
            return result;
        }
    }

    public enum PseudoElement {
        BEFORE,
        AFTER
    }

    private enum Combinator {DESCENDANT, CHILD, ADJACENT_SIBLING, GENERAL_SIBLING}

    private record AttributeSelector(String name, String operator, String expected, boolean caseInsensitive) {
        public boolean matches(Element element) {
            String actual = null;
            boolean present = false;
            for (Map.Entry<String, String> entry : element.getAttributes().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    present = true;
                    actual = entry.getValue();
                    break;
                }
            }
            if (!present) return false;
            if (operator == null) return true;
            String value = actual == null ? "" : actual;
            String target = expected == null ? "" : expected;
            if (caseInsensitive) {
                value = value.toLowerCase(Locale.ROOT);
                target = target.toLowerCase(Locale.ROOT);
            }
            return switch (operator) {
                case "=" -> value.equals(target);
                case "~=" -> Arrays.stream(value.trim().split("\\s+")).anyMatch(target::equals);
                case "|=" -> value.equals(target) || value.startsWith(target + "-");
                case "^=" -> value.startsWith(target);
                case "$=" -> value.endsWith(target);
                case "*=" -> value.contains(target);
                default -> false;
            };
        }
    }

    /** The three selector-specificity columns, excluding source order. */
    private record SpecificityParts(int ids, int classes, int tags) implements Comparable<SpecificityParts> {
        private static final SpecificityParts ZERO = new SpecificityParts(0, 0, 0);

        public SpecificityParts plus(SpecificityParts other) {
            return new SpecificityParts(ids + other.ids, classes + other.classes, tags + other.tags);
        }

        @Override
        public int compareTo(SpecificityParts other) {
            if (ids != other.ids) return Integer.compare(ids, other.ids);
            if (classes != other.classes) return Integer.compare(classes, other.classes);
            return Integer.compare(tags, other.tags);
        }
    }

    private record Pseudo(String name, String expression) {
        /**
         * Index.match 的候选预筛：只把“该伪类可能命中”的规则拉进候选集，避免为无关伪类
         * 扫描全部规则。新加伪类时需与 {@link #matches} 同步。
         */
        public static boolean mayMatch(String name, Element e) {
            if (e == null) return false;
            return switch (name) {
                case "hover" -> e.isHover;
                case "active" -> e.isActive;
                case "focus", "focus-visible" -> e.isFocus;
                case "focus-within" -> isFocusWithin(e);
                case "disabled" -> e.isDisabled();
                case "enabled" -> !e.isDisabled();
                case "required" -> e.hasAttribute("required");
                case "optional" -> !e.hasAttribute("required");
                case "valid" -> e.isValid();
                case "invalid" -> e.isWillValidate() && !e.isValid();
                case "in-range" -> e.isWillValidate() && !e.getValidity().rangeUnderflow && !e.getValidity().rangeOverflow;
                case "out-of-range" -> e.isWillValidate() && (e.getValidity().rangeUnderflow || e.getValidity().rangeOverflow);
                case "read-only" -> e.hasAttribute("readonly");
                case "read-write" -> !e.hasAttribute("readonly") && e.isWillValidate();
                case "placeholder-shown" -> e.hasAttribute("placeholder") && e.getValue().isEmpty();
                case "empty" -> e.children.isEmpty();
                case "checked" -> e.getAttributes().containsKey("checked")
                        || "OPTION".equalsIgnoreCase(e.tagName)
                        || e.getAttributes().containsKey("selected");
                case "root" -> e.parentElement == null;
                case "first-child", "last-child", "nth-child", "nth-last-child", "only-child",
                     "first-of-type", "last-of-type", "only-of-type", "nth-of-type", "nth-last-of-type" -> e.parentElement != null;
                case "not", "is", "where" -> true;
                default -> false;
            };
        }

        public boolean matches(Element e) {
            if (e == null) return false;

            return switch (name) {
                case "root" -> e.parentElement == null;
                case "first-child" -> isSiblingIndex(e, 0);
                case "last-child" -> isSiblingIndex(e, -1);
                case "only-child" -> e.parentElement != null && e.parentElement.children.size() == 1;
                case "nth-child" -> matchNth(e, expression, false, false);
                case "nth-last-child" -> matchNth(e, expression, true, false);
                case "first-of-type" -> isTypeSiblingIndex(e, 0);
                case "last-of-type" -> isTypeSiblingIndex(e, -1);
                case "only-of-type" -> countSameTypeSiblings(e) == 1;
                case "nth-of-type" -> matchNth(e, expression, false, true);
                case "nth-last-of-type" -> matchNth(e, expression, true, true);
                case "hover" -> e.isHover;
                case "active" -> e.isActive;
                case "focus" -> e.isFocus;
                // Input-modality tracking does not exist yet; focus is the
                // closest safe baseline for :focus-visible.
                case "focus-visible" -> e.isFocus;
                case "focus-within" -> isFocusWithin(e);
                case "disabled" -> e.isDisabled();
                case "enabled" -> !e.isDisabled();
                case "required" -> e.hasAttribute("required");
                case "optional" -> !e.hasAttribute("required");
                case "valid" -> e.isValid();
                case "invalid" -> e.isWillValidate() && !e.isValid();
                case "in-range" -> e.isWillValidate() && !e.getValidity().rangeUnderflow && !e.getValidity().rangeOverflow;
                case "out-of-range" -> e.isWillValidate() && (e.getValidity().rangeUnderflow || e.getValidity().rangeOverflow);
                case "read-only" -> e.hasAttribute("readonly");
                case "read-write" -> !e.hasAttribute("readonly") && e.isWillValidate();
                case "placeholder-shown" -> e.hasAttribute("placeholder") && e.getValue().isEmpty();
                case "empty" -> e.children.isEmpty();
                case "checked" -> isChecked(e);
                case "not" -> !matchesAny(e, expression);
                case "is", "where" -> matchesAny(e, expression);
                default -> false;
            };
        }

        private boolean matchesAny(Element element, String selectorList) {
            if (selectorList == null || selectorList.isBlank()) return false;
            for (CompiledSelector selector : parseGroup(selectorList)) {
                if (selector.pseudoElement == null && isMatch(element, selector)) return true;
            }
            return false;
        }

        /**
         * Selectors Level 4 gives :is() and :not() the specificity of their
         * most-specific argument, while :where() is intentionally always zero.
         */
        public SpecificityParts specificity() {
            if ("where".equals(name)) return SpecificityParts.ZERO;
            if ("is".equals(name) || "not".equals(name)) {
                SpecificityParts result = SpecificityParts.ZERO;
                if (expression == null || expression.isBlank()) return result;
                for (CompiledSelector selector : parseGroup(expression)) {
                    SpecificityParts candidate = new SpecificityParts(
                            selector.ids, selector.classesAndPseudos, selector.tags);
                    if (candidate.compareTo(result) > 0) result = candidate;
                }
                return result;
            }
            return new SpecificityParts(0, 1, 0);
        }

        private boolean isChecked(Element e) {
            if ("INPUT".equalsIgnoreCase(e.tagName)) {
                String type = e.getAttribute("type");
                if ("checkbox".equalsIgnoreCase(type) || "radio".equalsIgnoreCase(type)) {
                    return e.isChecked();
                }
            }
            if (e.getAttributes().containsKey("checked")) {
                String v = e.getAttribute("checked");
                if (v == null || v.isBlank()) return true;
                return !("false".equalsIgnoreCase(v) || "0".equals(v));
            }

            if ("OPTION".equalsIgnoreCase(e.tagName)) {
                if (e.getAttributes().containsKey("selected")) return true;
                Element parent = e.parentElement;
                if (parent != null && "SELECT".equalsIgnoreCase(parent.tagName)) {
                    String pv = parent.getAttribute("value");
                    String ov = e.getAttribute("value");
                    return pv != null && pv.equals(ov);
                }
            }

            return false;
        }

        private boolean isSiblingIndex(Element e, int target) {
            if (e.parentElement == null) return false;
            List<Element> siblings = e.parentElement.children;
            int idx = siblings.indexOf(e);
            return target == -1 ? idx == siblings.size() - 1 : idx == target;
        }

        private boolean isTypeSiblingIndex(Element e, int target) {
            if (e.parentElement == null) return false;
            List<Element> siblings = sameTypeSiblings(e);
            int index = siblings.indexOf(e);
            return target == -1 ? index == siblings.size() - 1 : index == target;
        }

        private int countSameTypeSiblings(Element e) {
            return e.parentElement == null ? 0 : sameTypeSiblings(e).size();
        }

        private List<Element> sameTypeSiblings(Element e) {
            ArrayList<Element> result = new ArrayList<>();
            if (e.parentElement == null) return result;
            for (Element sibling : e.parentElement.children) {
                if (sibling.tagName.equalsIgnoreCase(e.tagName)) result.add(sibling);
            }
            return result;
        }

        private boolean matchNth(Element e, String expr, boolean fromEnd, boolean ofType) {
            if (e.parentElement == null) return false;
            List<Element> siblings = ofType ? sameTypeSiblings(e) : e.parentElement.children;
            int index = siblings.indexOf(e);
            if (index < 0) return false;
            int pos = fromEnd ? siblings.size() - index : index + 1;
            String normalized = expr == null ? "" : expr.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if ("odd".equals(normalized)) return pos % 2 != 0;
            if ("even".equals(normalized)) return pos % 2 == 0;
            try {
                return Integer.parseInt(normalized) == pos;
            } catch (NumberFormatException ignored) {
                Matcher matcher = Pattern.compile("([+-]?\\d*)n(?:([+-]\\d+))?").matcher(normalized);
                if (!matcher.matches()) return false;
                String coefficient = matcher.group(1);
                int a = coefficient.isEmpty() || "+".equals(coefficient) ? 1 : "-".equals(coefficient) ? -1 : Integer.parseInt(coefficient);
                int b = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                if (a == 0) return pos == b;
                int delta = pos - b;
                return a > 0 ? delta >= 0 && delta % a == 0 : delta <= 0 && delta % a == 0;
            }
        }
    }

    private record Component(String tag, String id, Set<String> classes,
                             List<AttributeSelector> attributeSelectors, List<Pseudo> pseudos) {
        public boolean matches(Element e) {
            if (e == null) return false;
            if (tag != null && !tag.equals("*") && !tag.equalsIgnoreCase(e.tagName)) return false;
            if (id != null && !id.equals(e.getAttribute("id"))) return false;
            if (classes != null && !e.getClassNames().containsAll(classes)) return false;
            if (attributeSelectors != null) {
                for (AttributeSelector attributeSelector : attributeSelectors) {
                    if (!attributeSelector.matches(e)) return false;
                }
            }
            if (pseudos != null) {
                for (Pseudo p : pseudos) if (!p.matches(e)) return false;
            }
            return true;
        }
    }

    private record CompiledSelector(List<Component> components, List<Combinator> combinators, PseudoElement pseudoElement,
                                    int ids, int classesAndPseudos, int tags) {
        public Specificity specificity(int order) {
            return new Specificity(ids, classesAndPseudos, tags, order);
        }
    }

    public static final class Index {
        private final Map<String, List<IndexedRule>> byId = new HashMap<>();
        private final Map<String, List<IndexedRule>> byClass = new HashMap<>();
        private final Map<String, List<IndexedRule>> byTag = new HashMap<>();
        private final Map<String, List<IndexedRule>> byPseudo = new HashMap<>();
        private final Map<String, List<IndexedRule>> byAttr = new HashMap<>();
        private final Set<String> pseudosAffectingDescendants = new HashSet<>();
        private final List<IndexedRule> always = new ArrayList<>();

        // 每次调用 match() 时复用的临时缓冲区（仅 tick 线程）
        private final ArrayList<IndexedRule> scratchCandidates = new ArrayList<>();
        private final IdentityHashMap<IndexedRule, Boolean> scratchSeen = new IdentityHashMap<>();

        private Index() {
        }

        public static Index build(Map<String, Map<String, CSS.Declaration>> cssCache) {
            Index index = new Index();
            if (cssCache == null || cssCache.isEmpty()) return index;

            int order = 0;
            for (Map.Entry<String, Map<String, CSS.Declaration>> entry : cssCache.entrySet()) {
                String selectorStr = entry.getKey();
                Map<String, CSS.Declaration> styles = entry.getValue();
                if (selectorStr == null || selectorStr.isBlank() || styles == null) {
                    order++;
                    continue;
                }

                List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
                for (CompiledSelector sel : groups) {
                    Specificity specificity = sel.specificity(order);
                    IndexedRule rule = new IndexedRule(selectorStr, sel, specificity, styles);
                    index.addRule(rule);
                }
                order++;
            }

            return index;
        }

        private void addRule(IndexedRule rule) {
            List<Component> components = rule.selector.components;
            recordAncestorPseudoDependencies(rule.selector);
            Component last = components.get(components.size() - 1);

            // 优先用最后一个 component 的 id/class/tag 作为候选索引键。
            if (last.id != null) {
                byId.computeIfAbsent(last.id, ignored -> new ArrayList<>()).add(rule);
                return;
            }
            if (last.classes != null && !last.classes.isEmpty()) {
                for (String cls : last.classes) {
                    if (cls == null || cls.isBlank()) continue;
                    byClass.computeIfAbsent(cls, ignored -> new ArrayList<>()).add(rule);
                }
                return;
            }
            if (last.tag != null && !last.tag.isBlank() && !last.tag.equals("*")) {
                byTag.computeIfAbsent(last.tag.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(rule);
                return;
            }
            if (last.pseudos != null && !last.pseudos.isEmpty()) {
                for (Pseudo p : last.pseudos) {
                    if (p == null || p.name == null || p.name.isBlank()) continue;
                    byPseudo.computeIfAbsent(p.name, ignored -> new ArrayList<>()).add(rule);
                }
                return;
            }
            if (last.attributeSelectors != null && !last.attributeSelectors.isEmpty()) {
                for (AttributeSelector attribute : last.attributeSelectors) {
                    if (attribute.name == null || attribute.name.isBlank()) continue;
                    byAttr.computeIfAbsent(attribute.name, ignored -> new ArrayList<>()).add(rule);
                }
                return;
            }

            always.add(rule);
        }

        private void recordAncestorPseudoDependencies(CompiledSelector selector) {
            if (selector == null || selector.components == null || selector.components.size() <= 1) return;
            for (int i = 0; i < selector.components.size() - 1; i++) {
                Component component = selector.components.get(i);
                if (component == null || component.pseudos == null || component.pseudos.isEmpty()) continue;
                for (Pseudo pseudo : component.pseudos) {
                    if (pseudo == null || pseudo.name == null || pseudo.name.isBlank()) continue;
                    pseudosAffectingDescendants.add(pseudo.name);
                }
            }
        }

        public boolean pseudoCanAffectDescendants(String pseudoName) {
            return pseudoName != null && pseudosAffectingDescendants.contains(pseudoName);
        }

        public HashMap<String, String> match(Element element) {
            scratchCandidates.clear();
            scratchSeen.clear();

            if (element == null || element.document == null) return new HashMap<>();

            addCandidates(always);

            String id = element.id;
            if (id != null && !id.isBlank()) {
                addCandidates(byId.get(id));
            }

            Set<String> classes = element.getClassNames();
            if (classes != null && !classes.isEmpty()) {
                for (String cls : classes) {
                    addCandidates(byClass.get(cls));
                }
            }

            String tag = element.tagName;
            if (tag != null && !tag.isBlank()) {
                addCandidates(byTag.get(tag.toLowerCase(Locale.ROOT)));
            }

            // 伪类候选：只把“可能命中”的伪类规则拉进候选集。预筛逻辑集中在 Pseudo.mayMatch。
            for (String pseudoName : SUPPORTED_PSEUDOS) {
                if (Pseudo.mayMatch(pseudoName, element)) {
                    addCandidates(byPseudo.get(pseudoName));
                }
            }

            // 属性选择器：按 attribute name 建候选集
            HashMap<String, String> attrs = element.getAttributes();
            if (attrs != null && !attrs.isEmpty()) {
                for (String name : attrs.keySet()) {
                    addCandidates(byAttr.get(name));
                }
            }

            List<MatchedRule> matched = new ArrayList<>();
            for (IndexedRule rule : scratchCandidates) {
                if (rule == null) continue;
                if (rule.selector.pseudoElement != null) continue;
                if (isMatch(element, rule.selector)) {
                    matched.add(new MatchedRule(rule.specificity, rule.styles));
                }
            }

            matched.sort(Comparator.comparing(m -> m.specificity));

            // 先应用普通声明，再应用 !important 声明。
            // 同一属性中 important 声明按 specificity 排序覆盖，符合 CSS 层叠规则。
            LinkedHashMap<String, String> finalStyles = new LinkedHashMap<>();
            List<MatchedRule> importantRules = new ArrayList<>();
            for (MatchedRule rule : matched) {
                boolean hasImportant = false;
                for (Map.Entry<String, CSS.Declaration> entry : rule.styles.entrySet()) {
                    CSS.Declaration declaration = entry.getValue();
                    if (declaration.important()) {
                        hasImportant = true;
                    } else {
                        finalStyles.put(entry.getKey(), declaration.value());
                    }
                }
                if (hasImportant) importantRules.add(rule);
            }
            for (MatchedRule rule : importantRules) {
                for (Map.Entry<String, CSS.Declaration> e : rule.styles.entrySet()) {
                    CSS.Declaration declaration = e.getValue();
                    if (declaration.important()) {
                        finalStyles.put(e.getKey(), declaration.value());
                    }
                }
            }
            return finalStyles;
        }

        public HashMap<String, String> matchPseudoElement(Element element, PseudoElement pseudoElement) {
            scratchCandidates.clear();
            scratchSeen.clear();

            if (element == null || element.document == null || pseudoElement == null) return new HashMap<>();

            addCandidates(always);

            String id = element.id;
            if (id != null && !id.isBlank()) {
                addCandidates(byId.get(id));
            }

            Set<String> classes = element.getClassNames();
            if (classes != null && !classes.isEmpty()) {
                for (String cls : classes) {
                    addCandidates(byClass.get(cls));
                }
            }

            String tag = element.tagName;
            if (tag != null && !tag.isBlank()) {
                addCandidates(byTag.get(tag.toLowerCase(Locale.ROOT)));
            }

            List<MatchedRule> matched = new ArrayList<>();
            for (IndexedRule rule : scratchCandidates) {
                if (rule == null) continue;
                if (rule.selector.pseudoElement != pseudoElement) continue;
                if (isMatch(element, rule.selector)) {
                    matched.add(new MatchedRule(rule.specificity, rule.styles));
                }
            }

            matched.sort(Comparator.comparing(m -> m.specificity));

            LinkedHashMap<String, String> finalStyles = new LinkedHashMap<>();
            List<MatchedRule> importantRules = new ArrayList<>();
            for (MatchedRule rule : matched) {
                boolean hasImportant = false;
                for (Map.Entry<String, CSS.Declaration> entry : rule.styles.entrySet()) {
                    CSS.Declaration declaration = entry.getValue();
                    if (declaration.important()) {
                        hasImportant = true;
                    } else {
                        finalStyles.put(entry.getKey(), declaration.value());
                    }
                }
                if (hasImportant) importantRules.add(rule);
            }
            for (MatchedRule rule : importantRules) {
                for (Map.Entry<String, CSS.Declaration> e : rule.styles.entrySet()) {
                    CSS.Declaration declaration = e.getValue();
                    if (declaration.important()) {
                        finalStyles.put(e.getKey(), declaration.value());
                    }
                }
            }
            return finalStyles;
        }

        private void addCandidates(List<IndexedRule> rules) {
            if (rules == null || rules.isEmpty()) return;
            for (IndexedRule rule : rules) {
                if (rule == null) continue;
                if (scratchSeen.put(rule, Boolean.TRUE) == null) {
                    scratchCandidates.add(rule);
                }
            }
        }

        private record IndexedRule(String selectorStr, CompiledSelector selector, Specificity specificity,
                                   Map<String, CSS.Declaration> styles) {
        }
    }


    public static HashMap<String, String> matchCSS(Element element) {
        if (element == null || element.document == null) return new HashMap<>();
        return element.document.getSelectorIndex().match(element);
    }

    private static boolean isFocusWithin(Element element) {
        Element focused = element == null || element.document == null
                ? null
                : element.document.getFocusedElement();
        while (focused != null) {
            if (focused == element) return true;
            focused = focused.parentElement;
        }
        return false;
    }

    public static HashMap<String, String> matchPseudoElementCSS(Element element, PseudoElement pseudoElement) {
        if (element == null || element.document == null || pseudoElement == null) return new HashMap<>();
        return element.document.getSelectorIndex().matchPseudoElement(element, pseudoElement);
    }

    public static boolean matches(Element element, String selectorStr) {
        if (element == null || selectorStr == null || selectorStr.isBlank()) return false;
        List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
        for (CompiledSelector selector : groups) {
            if (selector.pseudoElement != null) continue;
            if (isMatch(element, selector)) return true;
        }
        return false;
    }

    private static boolean isMatch(Element element, CompiledSelector selector) {
        List<Component> comps = selector.components;
        List<Combinator> combs = selector.combinators;

        int compIdx = comps.size() - 1;
        Element current = element;

        if (!comps.get(compIdx).matches(current)) return false;

        for (int i = combs.size() - 1; i >= 0; i--) {
            Combinator comb = combs.get(i);
            compIdx--;
            Component target = comps.get(compIdx);

            if (comb == Combinator.CHILD) {
                current = current.parentElement;
                if (!target.matches(current)) return false;
            } else if (comb == Combinator.ADJACENT_SIBLING) {
                current = previousElementSibling(current);
                if (!target.matches(current)) return false;
            } else if (comb == Combinator.GENERAL_SIBLING) {
                current = previousMatchingElementSibling(current, target);
                if (current == null) return false;
            } else if (comb == Combinator.DESCENDANT) {
                boolean found = false;
                while ((current = current.parentElement) != null) {
                    if (target.matches(current)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
        }
        return true;
    }

    private static Element previousElementSibling(Element element) {
        if (element == null || element.parentElement == null) return null;
        List<Element> siblings = element.parentElement.children;
        int index = siblings.indexOf(element);
        if (index <= 0) return null;
        return siblings.get(index - 1);
    }

    private static Element previousMatchingElementSibling(Element element, Component target) {
        if (element == null || target == null || element.parentElement == null) return null;
        List<Element> siblings = element.parentElement.children;
        int index = siblings.indexOf(element);
        for (int i = index - 1; i >= 0; i--) {
            Element candidate = siblings.get(i);
            if (target.matches(candidate)) return candidate;
        }
        return null;
    }

    private static List<CompiledSelector> parseGroup(String fullSelector) {
        List<CompiledSelector> results = new ArrayList<>();
        for (String p : splitSelectorList(fullSelector)) {
            if (!p.isBlank()) results.add(parseSelector(p.trim()));
        }
        return results;
    }

    /** Splits a selector list without treating commas inside [] or () as groups. */
    public static List<String> splitSelectorList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return CssString.splitTopLevel(value, ',');
    }

    private static CompiledSelector parseSelector(String selector) {
        List<String> tokens = splitSelectorTokens(selector);
        List<Component> components = new ArrayList<>();
        List<Combinator> combinators = new ArrayList<>();

        SpecificityParts specificity = SpecificityParts.ZERO;
        PseudoElement pseudoElement = null;

        for (String token : tokens) {
            token = token.trim();
            switch (token) {
                case "" -> {
                }
                case ">" -> combinators.add(Combinator.CHILD);
                case "+" -> combinators.add(Combinator.ADJACENT_SIBLING);
                case "~" -> combinators.add(Combinator.GENERAL_SIBLING);
                case " " -> combinators.add(Combinator.DESCENDANT);
                default -> {
                    if (components.size() > combinators.size()) {
                        combinators.add(Combinator.DESCENDANT);
                    }
                    ParsedAtom parsedAtom = parseAtom(token);
                    if (parsedAtom.pseudoElement() != null) {
                        pseudoElement = parsedAtom.pseudoElement();
                    }
                    Component comp = parsedAtom.component();
                    components.add(comp);

                    int ids = comp.id == null ? 0 : 1;
                    int classes = (comp.classes == null ? 0 : comp.classes.size())
                            + (comp.attributeSelectors == null ? 0 : comp.attributeSelectors.size());
                    int tags = comp.tag == null || comp.tag.equals("*") ? 0 : 1;
                    if (comp.pseudos != null) {
                        for (Pseudo pseudo : comp.pseudos) {
                            SpecificityParts pseudoSpecificity = pseudo.specificity();
                            ids += pseudoSpecificity.ids;
                            classes += pseudoSpecificity.classes;
                            tags += pseudoSpecificity.tags;
                        }
                    }
                    // ::before and ::after are pseudo-elements and therefore
                    // contribute to the type-selector specificity column.
                    if (parsedAtom.pseudoElement() != null) tags++;
                    specificity = specificity.plus(new SpecificityParts(ids, classes, tags));
                }
            }
        }
        if (components.isEmpty()) {
            logSelectorDiagnostic("empty", selector, "selector has no component");
        }
        if (combinators.size() >= components.size() && !components.isEmpty()) {
            logSelectorDiagnostic("combinator", selector, "selector has too many combinators");
        }
        return new CompiledSelector(components, combinators, pseudoElement,
                specificity.ids, specificity.classes, specificity.tags);
    }

    private static List<String> splitSelectorTokens(String selector) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder atom = new StringBuilder();
        int brackets = 0, parentheses = 0;
        char quote = 0;
        for (int i = 0; i < selector.length(); i++) {
            char ch = selector.charAt(i);
            if (quote != 0) {
                atom.append(ch);
                if (ch == quote && (i == 0 || selector.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') { quote = ch; atom.append(ch); continue; }
            if (ch == '[') { brackets++; atom.append(ch); continue; }
            if (ch == ']') { brackets = Math.max(0, brackets - 1); atom.append(ch); continue; }
            if (ch == '(') { parentheses++; atom.append(ch); continue; }
            if (ch == ')') { parentheses = Math.max(0, parentheses - 1); atom.append(ch); continue; }
            if (brackets == 0 && parentheses == 0 && Character.isWhitespace(ch)) {
                if (!atom.isEmpty()) { tokens.add(atom.toString()); atom.setLength(0); }
                continue;
            }
            if (brackets == 0 && parentheses == 0 && (ch == '>' || ch == '+' || ch == '~')) {
                if (!atom.isEmpty()) { tokens.add(atom.toString()); atom.setLength(0); }
                tokens.add(String.valueOf(ch));
                continue;
            }
            atom.append(ch);
        }
        if (!atom.isEmpty()) tokens.add(atom.toString());
        return tokens;
    }

    private record ParsedAtom(Component component, PseudoElement pseudoElement) {
    }

    private static ParsedAtom parseAtom(String atom) {
        String tag = null;
        String id = null;
        Set<String> classes = new HashSet<>();
        List<AttributeSelector> attributeSelectors = new ArrayList<>();
        List<Pseudo> pseudos = new ArrayList<>();
        PseudoElement pseudoElement = null;

        // 先解析 tag（如果存在）：tag 只能出现在最前面，遇到 # . [ : 即结束
        int firstSpecial = -1;
        for (char c : new char[]{'#', '.', '[', ':'}) {
            int idx = atom.indexOf(c);
            if (idx != -1 && (firstSpecial == -1 || idx < firstSpecial)) firstSpecial = idx;
        }
        String rest;
        if (firstSpecial == -1) {
            // 纯 tag 或者空
            tag = atom.isBlank() ? null : atom;
            rest = "";
        } else {
            String maybeTag = atom.substring(0, firstSpecial).trim();
            tag = maybeTag.isEmpty() ? null : maybeTag;
            rest = atom.substring(firstSpecial);
        }

        // (#(?<id>[\\w-]+)) - ID 选择器 - #id
        // (\\.(?<cls>[\\w-]+)) - 类选择器 - .class
        // (\\[(?<attrName>[\\w-]+)(?:\\s*=\\s*(?<attrValue>\"[^\"]*\"|'[^']*'|[^]]+))?]) - 属性选择器 - [attr] / [attr=value]
        // :(?<pseudoName>[\\w-]+)(?:\\((?<pseudoExpr>[^)]*)\\))? - 伪类 / 伪元素选择器 - :pseudo / :pseudo(expr)
        Pattern token = Pattern.compile(
                "(#(?<id>(?:\\\\.|[\\w-])+))" +
                        "|(\\.(?<cls>(?:\\\\.|[\\w-])+))" +
                        "|(\\[(?<attrName>[\\w-]+)(?:\\s*(?<attrOperator>~=|\\|=|\\^=|\\$=|\\*=|=)\\s*(?<attrValue>\"[^\"]*\"|'[^']*'|[^]]+))?])" +
                        "|(?<pseudoColon>::?)(?<pseudoName>[\\w-]+)(?:\\((?<pseudoExpr>[^)]*)\\))?"
        );

        Matcher m = token.matcher(rest);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor && !rest.substring(cursor, m.start()).isBlank()) {
                logSelectorDiagnostic(
                        "fragment",
                        atom,
                        "unrecognized selector fragment=" + AuiLog.compact(rest.substring(cursor, m.start()))
                );
            }
            cursor = m.end();
            String gid = m.group("id");
            if (gid != null) {
                id = unescapeCssIdentifier(gid);
                continue;
            }
            String gcls = m.group("cls");
            if (gcls != null) {
                classes.add(unescapeCssIdentifier(gcls));
                continue;
            }
            String attrName = m.group("attrName");
            if (attrName != null) {
                String v = m.group("attrValue");
                String operator = m.group("attrOperator");
                if (v != null) {
                    v = v.trim();
                    // 去掉引号
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                }
                // v == null 表示 [attr]，由 matches() 中的 presence 逻辑处理
                boolean insensitive = false;
                if (operator != null && v != null && v.matches("(?s).*\\s+[iI]$")) {
                    insensitive = true;
                    v = v.substring(0, v.length() - 1).trim();
                } else if (operator != null && v != null && v.matches("(?s).*\\s+[sS]$")) {
                    v = v.substring(0, v.length() - 1).trim();
                }
                attributeSelectors.add(new AttributeSelector(attrName, operator, v, insensitive));
                continue;
            }
            String pseudoName = m.group("pseudoName");
            if (pseudoName != null) {
                String normalized = pseudoName.toLowerCase(Locale.ROOT);
                if ("before".equals(normalized) || "after".equals(normalized)) {
                    pseudoElement = "before".equals(normalized) ? PseudoElement.BEFORE : PseudoElement.AFTER;
                    continue;
                }
                if (!isSupportedPseudo(normalized)) {
                    logSelectorDiagnostic("pseudo", atom, "unsupported pseudo-class=" + normalized);
                }
                pseudos.add(new Pseudo(normalized, m.group("pseudoExpr")));
            }
        }
        if (cursor < rest.length() && !rest.substring(cursor).isBlank()) {
            logSelectorDiagnostic(
                    "fragment",
                    atom,
                    "trailing selector fragment=" + AuiLog.compact(rest.substring(cursor))
            );
        }
        return new ParsedAtom(new Component(tag, id,
                classes.isEmpty() ? null : classes,
                attributeSelectors.isEmpty() ? null : attributeSelectors,
                pseudos.isEmpty() ? null : pseudos), pseudoElement);
    }

    private static boolean isSupportedPseudo(String name) {
        return SUPPORTED_PSEUDOS.contains(name);
    }

    private static void logSelectorDiagnostic(String kind, String selector, String detail) {
        String key = kind + "|" + selector + "|" + detail;
        if (!SELECTOR_DIAGNOSTICS.add(key)) return;
        ApricityUI.LOGGER.warn(
                "[AUI CSS] selector diagnostic kind={} selector={} detail={}",
                kind,
                AuiLog.compact(selector),
                detail
        );
    }

    private static String unescapeCssIdentifier(String value) {
        if (value == null || value.indexOf('\\') < 0) return value;
        StringBuilder builder = new StringBuilder(value.length());
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escape) {
                builder.append(ch);
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            builder.append(ch);
        }
        if (escape) {
            builder.append('\\');
        }
        return builder.toString();
    }


    public static List<Element> querySelectorAll(Element root, String selectorStr) {
        List<Element> results = new ArrayList<>();
        List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
        searchElements(root, groups, results);
        return results;
    }

    private static void searchElements(Element current, List<CompiledSelector> selectors, List<Element> results) {
        if (current == null) return;
        for (CompiledSelector sel : selectors) {
            if (isMatch(current, sel)) {
                results.add(current);
                break;
            }
        }
        for (Element child : current.children) {
            searchElements(child, selectors, results);
        }
    }

    public static Element querySelector(Element root, String selectorStr) {
        List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
        return findFirstMatch(root, groups);
    }

    private static Element findFirstMatch(Element current, List<CompiledSelector> selectors) {
        if (current == null) return null;
        for (CompiledSelector sel : selectors) {
            if (isMatch(current, sel)) return current;
        }
        for (Element child : current.children) {
            Element found = findFirstMatch(child, selectors);
            if (found != null) return found;
        }
        return null;
    }

    public static List<DebugStyleBlock> getDebugStyles(Element element) {
        record DebugMatch(CSS.DebugRule rule, Specificity specificity) {
        }
        List<DebugMatch> matches = new ArrayList<>();

        for (CSS.DebugRule rule : element.document.CSSDebugRules) {
            String selectorStr = rule.selector();
            int finalOrder = rule.order();
            List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
            Specificity matchedSpecificity = null;
            for (CompiledSelector sel : groups) {
                if (isMatch(element, sel)) {
                    Specificity specificity = sel.specificity(finalOrder);
                    if (matchedSpecificity == null || specificity.compareTo(matchedSpecificity) > 0) {
                        matchedSpecificity = specificity;
                    }
                }
            }
            if (matchedSpecificity != null) matches.add(new DebugMatch(rule, matchedSpecificity));
        }

        matches.sort((a, b) -> b.specificity.compareTo(a.specificity));
        record Winner(int ruleOrder, Specificity specificity, boolean important) {
        }
        Map<String, Winner> winners = new HashMap<>();
        for (DebugMatch match : matches) {
            for (Map.Entry<String, CSS.Declaration> entry : match.rule.properties().entrySet()) {
                String property = cascadePropertyKey(entry.getKey());
                CSS.Declaration declaration = entry.getValue();
                Winner current = winners.get(property);
                if (current == null
                        || declaration.important() && !current.important()
                        || declaration.important() == current.important()
                        && match.specificity.compareTo(current.specificity()) > 0) {
                    winners.put(property, new Winner(match.rule.order(), match.specificity, declaration.important()));
                }
            }
        }
        Set<String> inlineProperties = inlinePropertyNames(element.getAttribute("style"));
        List<DebugStyleBlock> result = new ArrayList<>();
        for (DebugMatch match : matches) {
            LinkedHashMap<String, DebugDeclaration> declarations = new LinkedHashMap<>();
            for (Map.Entry<String, CSS.Declaration> entry : match.rule.properties().entrySet()) {
                String property = entry.getKey();
                CSS.Declaration declaration = entry.getValue();
                Winner winner = winners.get(cascadePropertyKey(property));
                boolean overridden = inlineProperties.contains(cascadePropertyKey(property))
                        || winner == null || winner.ruleOrder() != match.rule.order();
                declarations.put(property, new DebugDeclaration(
                        declaration.value(), declaration.important(), overridden));
            }
            result.add(new DebugStyleBlock(match.rule.sourcePath(), match.rule.selector(),
                    match.rule.order(), declarations));
        }
        return result;
    }

    private static Set<String> inlinePropertyNames(String style) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (style == null || style.isBlank()) return result;
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) continue;
            String property = cascadePropertyKey(declaration.substring(0, colon));
            if (!property.isBlank()) result.add(property);
        }
        return result;
    }

    private static String cascadePropertyKey(String raw) {
        String property = raw == null ? "" : raw.trim();
        return property.startsWith("--") ? property : property.toLowerCase(Locale.ROOT);
    }

    private record MatchedRule(Specificity specificity, Map<String, CSS.Declaration> styles) {
    }
}
