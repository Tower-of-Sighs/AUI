package com.sighs.apricityui.parser;

import com.sighs.apricityui.loader.Loader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 页面资源引用索引：记录每个模板直接引用的本地 CSS/JS，以及 CSS 之间的 @import 边。
 * 文件热重载时用它算出哪些模板受影响，只刷新对应的 Document。
 * 记录是覆盖式的（每次 refresh 重建该模板的条目）；@import 边只增不删，
 * 过时边只会导致多刷新，不会漏刷新。
 */
public final class ResourceUsageIndex {
    private static final Map<String, Set<String>> TEMPLATE_CSS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> TEMPLATE_JS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CSS_IMPORTS = new ConcurrentHashMap<>();

    private ResourceUsageIndex() {
    }

    public static void recordCss(String templatePath, List<String> hrefs) {
        record(TEMPLATE_CSS, templatePath, hrefs);
    }

    public static void recordJs(String templatePath, List<String> srcs) {
        record(TEMPLATE_JS, templatePath, srcs);
    }

    private static void record(Map<String, Set<String>> index, String templatePath, List<String> rawPaths) {
        if (templatePath == null || templatePath.isBlank()) return;
        Set<String> resolved = new HashSet<>();
        if (rawPaths != null) {
            for (String raw : rawPaths) {
                String path = resolveLocal(templatePath, raw);
                if (path != null) resolved.add(path);
            }
        }
        if (resolved.isEmpty()) index.remove(templatePath);
        else index.put(templatePath, resolved);
    }

    /** 记录一条 CSS @import 边：parent 导入了 imported。 */
    public static void recordImport(String parent, String imported) {
        if (parent == null || parent.isBlank() || imported == null || imported.isBlank()) return;
        if (Loader.isRemotePath(parent) || Loader.isRemotePath(imported)) return;
        CSS_IMPORTS.computeIfAbsent(parent, key -> ConcurrentHashMap.newKeySet()).add(imported);
    }

    /** 返回直接或间接（经 @import 链）引用该资源的模板路径集合。 */
    public static Set<String> affectedTemplates(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return Set.of();

        // 受影响的 CSS 闭包：自身 + 所有传递 import 了它的文件。
        Set<String> affectedCss = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(resourcePath);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!affectedCss.add(current)) continue;
            for (Map.Entry<String, Set<String>> entry : CSS_IMPORTS.entrySet()) {
                if (entry.getValue().contains(current)) queue.add(entry.getKey());
            }
        }

        Set<String> templates = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : TEMPLATE_CSS.entrySet()) {
            for (String css : entry.getValue()) {
                if (affectedCss.contains(css)) {
                    templates.add(entry.getKey());
                    break;
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : TEMPLATE_JS.entrySet()) {
            if (entry.getValue().contains(resourcePath)) templates.add(entry.getKey());
        }
        return templates;
    }

    private static String resolveLocal(String contextPath, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String resolved = Loader.resolve(contextPath, raw);
        if (resolved.isBlank() || Loader.isRemotePath(resolved)) return null;
        return resolved;
    }
}
