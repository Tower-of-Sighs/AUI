package com.sighs.apricityui.init;

import com.sighs.apricityui.resource.CSS;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Selector {
    private static final Map<String, List<CompiledSelector>> SELECTOR_CACHE = new HashMap<>();

    public record Specificity(int ids, int classes, int tags, int order) implements Comparable<Specificity> {
        @Override
        public int compareTo(Specificity o) {
            if (this.ids != o.ids) return Integer.compare(this.ids, o.ids);
            if (this.classes != o.classes) return Integer.compare(this.classes, o.classes);
            if (this.tags != o.tags) return Integer.compare(this.tags, o.tags);
            return Integer.compare(this.order, o.order);
        }
    }

    public record DebugStyleBlock(String sourcePath, String selector, Map<String, String> styles) {
    }

    private enum Combinator {DESCENDANT, CHILD}

    private record Pseudo(String name, String expression) {
        boolean matches(Element e) {
            if (e == null) return false;

            return switch (name) {
                case "first-child" -> isSiblingIndex(e, 0);
                case "last-child" -> isSiblingIndex(e, -1);
                case "nth-child" -> matchNth(e, expression);
                case "hover" -> e.isHover;
                case "active" -> e.isActive;
                case "focus" -> e.isFocus;
                case "empty" -> e.children.isEmpty();
                case "checked" -> isChecked(e);
                default -> false;
            };
        }

        private boolean isChecked(Element e) {
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

        private boolean matchNth(Element e, String expr) {
            if (e.parentElement == null) return false;
            int pos = e.parentElement.children.indexOf(e) + 1;
            if ("odd".equals(expr)) return pos % 2 != 0;
            if ("even".equals(expr)) return pos % 2 == 0;
            try {
                return Integer.parseInt(expr) == pos;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private record Component(String tag, String id, Set<String> classes, Map<String, String> attrs,
                             List<Pseudo> pseudos) {
        boolean matches(Element e) {
            if (e == null) return false;
            if (tag != null && !tag.equals("*") && !tag.equalsIgnoreCase(e.tagName)) return false;
            if (id != null && !id.equals(e.getAttribute("id"))) return false;
            if (classes != null && !e.getClassNames().containsAll(classes)) return false;
            if (attrs != null) {
                for (var entry : attrs.entrySet()) {
                    String expected = entry.getValue();

                    // CSS 属性选择器：
                    // - [attr]：只要求属性存在
                    // - [attr=value]：要求属性存在且值相等
                    String key = entry.getKey();
                    String actual = e.getAttribute(key);
                    boolean present = e.getAttributes().containsKey(key);

                    if (expected == null) {
                        if (!present) return false;
                        continue;
                    }

                    if (!present) return false;
                    if (!expected.equals(actual)) return false;
                }
            }
            if (pseudos != null) {
                for (Pseudo p : pseudos) if (!p.matches(e)) return false;
            }
            return true;
        }
    }

    private record CompiledSelector(List<Component> components, List<Combinator> combinators,
                                    int ids, int classesAndPseudos, int tags) {
        Specificity specificity(int order) {
            return new Specificity(ids, classesAndPseudos, tags, order);
        }
    }

    public static final class Index {
        private final Map<String, List<IndexedRule>> byId = new HashMap<>();
        private final Map<String, List<IndexedRule>> byClass = new HashMap<>();
        private final Map<String, List<IndexedRule>> byTag = new HashMap<>();
        private final Map<String, List<IndexedRule>> byPseudo = new HashMap<>();
        private final Map<String, List<IndexedRule>> byAttr = new HashMap<>();
        private final List<IndexedRule> always = new ArrayList<>();

        // 每次调用 match() 时复用的临时缓冲区（仅 tick 线程）
        private final ArrayList<IndexedRule> scratchCandidates = new ArrayList<>();
        private final IdentityHashMap<IndexedRule, Boolean> scratchSeen = new IdentityHashMap<>();

        private Index() {
        }

        public static Index build(Map<String, Map<String, String>> cssCache) {
            Index index = new Index();
            if (cssCache == null || cssCache.isEmpty()) return index;

            int order = 0;
            for (Map.Entry<String, Map<String, String>> entry : cssCache.entrySet()) {
                String selectorStr = entry.getKey();
                Map<String, String> styles = entry.getValue();
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
            Component last = rule.selector.components.getLast();

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
            if (last.attrs != null && !last.attrs.isEmpty()) {
                for (String name : last.attrs.keySet()) {
                    if (name == null || name.isBlank()) continue;
                    byAttr.computeIfAbsent(name, ignored -> new ArrayList<>()).add(rule);
                }
                return;
            }

            always.add(rule);
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

            // 常见伪类：只在“可能满足”的情况下加入候选，避免 :first-child/:nth-child 等无谓扩大候选集。
            if (element.isHover) addCandidates(byPseudo.get("hover"));
            if (element.isActive) addCandidates(byPseudo.get("active"));
            if (element.isFocus) addCandidates(byPseudo.get("focus"));
            if (element.children.isEmpty()) addCandidates(byPseudo.get("empty"));
            if (element.parentElement != null) {
                addCandidates(byPseudo.get("first-child"));
                addCandidates(byPseudo.get("last-child"));
                addCandidates(byPseudo.get("nth-child"));
            }
            if (element.getAttributes().containsKey("checked")
                    || "OPTION".equalsIgnoreCase(element.tagName)
                    || element.getAttributes().containsKey("selected")) {
                addCandidates(byPseudo.get("checked"));
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
                if (isMatch(element, rule.selector)) {
                    matched.add(new MatchedRule(rule.specificity, rule.styles));
                }
            }

            matched.sort(Comparator.comparing(m -> m.specificity));

            HashMap<String, String> finalStyles = new HashMap<>();
            for (MatchedRule rule : matched) {
                finalStyles.putAll(rule.styles);
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
                                   Map<String, String> styles) {
        }
    }


    public static HashMap<String, String> matchCSS(Element element) {
        if (element == null || element.document == null) return new HashMap<>();
        return element.document.getSelectorIndex().match(element);
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


    private static List<CompiledSelector> parseGroup(String fullSelector) {
        String[] parts = fullSelector.split(",");
        List<CompiledSelector> results = new ArrayList<>();
        for (String p : parts) {
            results.add(parseSelector(p.trim()));
        }
        return results;
    }

    private static CompiledSelector parseSelector(String selector) {
        String[] tokens = selector.split("(?<=[> ])|(?=[> ])");
        List<Component> components = new ArrayList<>();
        List<Combinator> combinators = new ArrayList<>();

        int ids = 0, classesAndPseudos = 0, tags = 0;

        for (String token : tokens) {
            token = token.trim();
            switch (token) {
                case "" -> {
                }
                case ">" -> combinators.add(Combinator.CHILD);
                case " " -> combinators.add(Combinator.DESCENDANT);
                default -> {
                    if (components.size() > combinators.size()) {
                        combinators.add(Combinator.DESCENDANT);
                    }
                    Component comp = parseAtom(token);
                    components.add(comp);

                    if (comp.id != null) ids++;
                    if (comp.classes != null) classesAndPseudos += comp.classes.size();
                    if (comp.pseudos != null) classesAndPseudos += comp.pseudos.size();
                    if (comp.tag != null && !comp.tag.equals("*")) tags++;
                }
            }
        }
        return new CompiledSelector(components, combinators, ids, classesAndPseudos, tags);
    }

    private static Component parseAtom(String atom) {
        String tag = null;
        String id = null;
        Set<String> classes = new HashSet<>();
        Map<String, String> attrs = new HashMap<>();
        List<Pseudo> pseudos = new ArrayList<>();

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
                "(#(?<id>[\\w-]+))" +
                        "|(\\.(?<cls>[\\w-]+))" +
                        "|(\\[(?<attrName>[\\w-]+)(?:\\s*=\\s*(?<attrValue>\"[^\"]*\"|'[^']*'|[^]]+))?])" +
                        "|:(?<pseudoName>[\\w-]+)(?:\\((?<pseudoExpr>[^)]*)\\))?"
        );

        Matcher m = token.matcher(rest);
        while (m.find()) {
            String gid = m.group("id");
            if (gid != null) {
                id = gid;
                continue;
            }
            String gcls = m.group("cls");
            if (gcls != null) {
                classes.add(gcls);
                continue;
            }
            String attrName = m.group("attrName");
            if (attrName != null) {
                String v = m.group("attrValue");
                if (v != null) {
                    v = v.trim();
                    // 去掉引号
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                }
                // v == null 表示 [attr]，由 matches() 中的 presence 逻辑处理
                attrs.put(attrName, v);
                continue;
            }
            String pseudoName = m.group("pseudoName");
            if (pseudoName != null) {
                pseudos.add(new Pseudo(pseudoName, m.group("pseudoExpr")));
            }
        }
        return new Component(tag, id,
                classes.isEmpty() ? null : classes,
                attrs.isEmpty() ? null : attrs,
                pseudos.isEmpty() ? null : pseudos);
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
        record DebugMatch(String sourcePath, String selector, Specificity specificity, Map<String, String> styles) {
        }
        List<DebugMatch> matches = new ArrayList<>();

        for (CSS.DebugRule rule : element.document.CSSDebugRules) {
            String selectorStr = rule.selector();
            int finalOrder = rule.order();
            List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, Selector::parseGroup);
            for (CompiledSelector sel : groups) {
                if (isMatch(element, sel)) {
                    matches.add(new DebugMatch(rule.sourcePath(), selectorStr, sel.specificity(finalOrder), rule.properties()));
                    break;
                }
            }
        }

        matches.sort((a, b) -> b.specificity.compareTo(a.specificity));
        List<DebugStyleBlock> result = new ArrayList<>();
        for (DebugMatch match : matches) {
            result.add(new DebugStyleBlock(match.sourcePath, match.selector, match.styles));
        }
        return result;
    }

    private record MatchedRule(Specificity specificity, Map<String, String> styles) {
    }
}
