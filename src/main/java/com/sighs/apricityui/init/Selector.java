package com.sighs.apricityui.init;

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

    private record CompiledSelector(List<Component> components, List<Combinator> combinators, Specificity specificity) {
    }


    public static HashMap<String, String> matchCSS(Element element) {
        List<MatchedRule> matched = new ArrayList<>();
        int order = 0;

        for (var entry : element.document.CSSCache.entrySet()) {
            String fullSelectorStr = entry.getKey();
            int finalOrder = order;
            List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(fullSelectorStr, s -> parseGroup(s, finalOrder));

            for (CompiledSelector sel : groups) {
                if (isMatch(element, sel)) {
                    matched.add(new MatchedRule(sel.specificity, entry.getValue()));
                    break; // 一个规则内如果有多个子选择器匹配，只取一次
                }
            }
            order++;
        }

        matched.sort(Comparator.comparing(m -> m.specificity));

        HashMap<String, String> finalStyles = new HashMap<>();
        for (MatchedRule rule : matched) {
            finalStyles.putAll(rule.styles);
        }
        return finalStyles;
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


    private static List<CompiledSelector> parseGroup(String fullSelector, int order) {
        String[] parts = fullSelector.split(",");
        List<CompiledSelector> results = new ArrayList<>();
        for (String p : parts) {
            results.add(parseSelector(p.trim(), order));
        }
        return results;
    }

    private static CompiledSelector parseSelector(String selector, int order) {
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
        return new CompiledSelector(components, combinators, new Specificity(ids, classesAndPseudos, tags, order));
    }

    private static Component parseAtom(String atom) {
        String tag;
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
        List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, s -> parseGroup(s, 0));
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
        List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, s -> parseGroup(s, 0));
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

    public static Map<String, Map<String, String>> getDebugStyles(Element element) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        record DebugMatch(String selector, Specificity specificity, Map<String, String> styles) {
        }
        List<DebugMatch> matches = new ArrayList<>();
        int order = 0;

        for (var entry : element.document.CSSCache.entrySet()) {
            String selectorStr = entry.getKey();
            int finalOrder = order++;
            List<CompiledSelector> groups = SELECTOR_CACHE.computeIfAbsent(selectorStr, s -> parseGroup(s, finalOrder));
            for (CompiledSelector sel : groups) {
                if (isMatch(element, sel)) {
                    matches.add(new DebugMatch(selectorStr, sel.specificity, entry.getValue()));
                    break;
                }
            }
        }

        matches.sort((a, b) -> b.specificity.compareTo(a.specificity));
        for (DebugMatch m : matches) {
            result.put(m.selector, m.styles);
        }

        return result;
    }

    private record MatchedRule(Specificity specificity, Map<String, String> styles) {
    }
}
