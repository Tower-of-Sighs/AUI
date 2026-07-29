package com.sighs.apricityui.script;

import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import com.sighs.apricityui.init.Event;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class ApricityJS {
    // KubeJS 自带的 Rhino 版本不支持部分 ES6 语法（数组展开、默认参数等）。
    // 在把页面脚本交给 Rhino 求值前，先把这些语法点转写成 ES5 兼容的形式。
    private static final Pattern ARRAY_SPREAD_PATTERN =
            Pattern.compile("\\[\\.\\.\\.([A-Za-z_$][\\w$]*)\\]");
    private static final Pattern FUNCTION_HEAD_PATTERN =
            Pattern.compile("\\bfunction\\s+([A-Za-z_$][\\w$]*)\\s*\\(");

    // 框架目前只给元素桥接了 textContent，页面脚本常用 innerText 来设置文本。
    // 在页面脚本执行前，动态装饰器上补一个 innerText 的 getter/setter。
    public static void eval(String code) {
        eval(code, null);
    }

    public static void eval(String code, Event event) {
        if (!ModList.get().isLoaded("kubejs")) return;
        code = ARRAY_SPREAD_PATTERN.matcher(code).replaceAll("$1.slice()");
        code = rewriteDefaultParameters(code);
        code = Pattern.compile("\\.innerText\\b").matcher(code).replaceAll(".textContent");

        var manager = KubeJS.getClientScriptManager();
        var context = manager.context;
        var top = manager.topLevelScope;
        Object previousEvent = null;
        boolean hadEvent = false;
        if (event != null) {
            previousEvent = top.get(context, "event", top);
            hadEvent = previousEvent != Scriptable.NOT_FOUND;
            top.put(context, "event", top, event);
        }
        try {
            context.evaluateString(top, code, "eval", 1, null);
        } finally {
            if (event != null) {
                if (hadEvent) {
                    top.put(context, "event", top, previousEvent);
                } else {
                    top.delete(context, "event");
                }
            }
        }
    }

    public static void reload() {
        if (!ModList.get().isLoaded("kubejs")) return;
        KubeJS.PROXY.reloadClientInternal();
    }

    public static Consumer<Event> browserEventListener(Object listener, Object currentTarget) {
        if (!(listener instanceof Function function)) return null;
        return new RhinoEventListener(function, currentTarget);
    }

    private static final class RhinoEventListener implements Consumer<Event> {
        private final Function function;
        private final Object currentTarget;

        private RhinoEventListener(Function function, Object currentTarget) {
            this.function = function;
            this.currentTarget = currentTarget;
        }

        @Override
        public void accept(Event event) {
            var manager = KubeJS.getClientScriptManager();
            var context = manager.context;
            var scope = manager.topLevelScope;
            Object eventArgument = Context.javaToJS(context, event, scope);
            Scriptable scriptTarget = context.toObject(currentTarget, scope);
            Object previousCurrentTarget = event.currentTarget;
            event.currentTarget = scriptTarget;
            try {
                context.callSync(function, scope, scriptTarget, new Object[]{eventArgument});
            } finally {
                event.currentTarget = previousCurrentTarget;
            }
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof RhinoEventListener other
                    && function == other.function
                    && currentTarget == other.currentTarget;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(function) + System.identityHashCode(currentTarget);
        }
    }

    /**
     * 将函数声明中的默认参数改写为函数体内的 typeof 检查赋值。
     * 例如：function showToast(msg, isError = false) { ... }
     * 改写为：function showToast(msg, isError) { if (typeof isError === 'undefined') isError = false; ... }
     */
    private static String rewriteDefaultParameters(String code) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = FUNCTION_HEAD_PATTERN.matcher(code);
        int lastEnd = 0;

        while (matcher.find()) {
            int paramsStart = matcher.end();
            int paramsEnd = findMatchingParen(code, paramsStart);
            if (paramsEnd < 0) continue;

            int bracePos = paramsEnd + 1;
            while (bracePos < code.length() && Character.isWhitespace(code.charAt(bracePos))) bracePos++;
            if (bracePos >= code.length() || code.charAt(bracePos) != '{') continue;

            String params = code.substring(paramsStart, paramsEnd);
            List<String> parts = splitTopLevel(params, ',');
            List<String> newParams = new ArrayList<>(parts.size());
            StringBuilder assignments = new StringBuilder();
            boolean hasDefault = false;

            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                int eq = findTopLevelEquals(trimmed);
                if (eq >= 0) {
                    hasDefault = true;
                    String name = trimmed.substring(0, eq).trim();
                    String expr = trimmed.substring(eq + 1).trim();
                    newParams.add(name);
                    assignments.append("if (typeof ").append(name)
                            .append(" === 'undefined') ").append(name)
                            .append(" = ").append(expr).append(";");
                } else {
                    newParams.add(trimmed);
                }
            }

            if (!hasDefault) continue;

            out.append(code, lastEnd, matcher.start());
            out.append("function ").append(matcher.group(1))
                    .append("(").append(String.join(", ", newParams)).append(") {")
                    .append(assignments);
            lastEnd = bracePos + 1; // 跳过原开括号，后续正文保持不变
        }

        out.append(code.substring(lastEnd));
        return out.toString();
    }

    /**
     * 从 openPos（'(' 后的第一个字符）开始扫描，返回与之匹配的 ')' 的索引。
     * 会跳过字符串字面量以及嵌套的括号、方括号、花括号。
     */
    private static int findMatchingParen(String text, int openPos) {
        int depth = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (inTemplate) {
                if (c == '`') inTemplate = false;
                // 模板字符串里的 ${ 嵌套此处不展开，因为默认参数表达式里极少写复杂模板；
                // 若后续需要可再补全。
                continue;
            }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') { inTemplate = true; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') {
                depth--;
                if (depth == 0) return i;
                continue;
            }
        }
        return -1;
    }

    /**
     * 按顶层分隔符切分字符串，忽略引号与成对括号内的分隔符。
     */
    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escape = true;
                continue;
            }
            if (inSingle) {
                current.append(c);
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                current.append(c);
                if (c == '"') inDouble = false;
                continue;
            }
            if (inTemplate) {
                current.append(c);
                if (c == '`') inTemplate = false;
                continue;
            }
            if (c == '\'') { current.append(c); inSingle = true; continue; }
            if (c == '"') { current.append(c); inDouble = true; continue; }
            if (c == '`') { current.append(c); inTemplate = true; continue; }
            if (c == '(') { paren++; current.append(c); continue; }
            if (c == ')') { paren--; current.append(c); continue; }
            if (c == '[') { bracket++; current.append(c); continue; }
            if (c == ']') { bracket--; current.append(c); continue; }
            if (c == '{') { brace++; current.append(c); continue; }
            if (c == '}') { brace--; current.append(c); continue; }
            if (c == delimiter && paren == 0 && bracket == 0 && brace == 0) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    /**
     * 在字符串顶层查找第一个作为默认参数赋值的 '='。
     * 会跳过 ==、!=、===、!==、<=、>=、=> 等操作符以及引号、括号内的内容。
     */
    private static int findTopLevelEquals(String text) {
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (inSingle) { if (c == '\'') inSingle = false; continue; }
            if (inDouble) { if (c == '"') inDouble = false; continue; }
            if (inTemplate) { if (c == '`') inTemplate = false; continue; }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') { inTemplate = true; continue; }
            if (c == '(') { paren++; continue; }
            if (c == ')') { paren--; continue; }
            if (c == '[') { bracket++; continue; }
            if (c == ']') { bracket--; continue; }
            if (c == '{') { brace++; continue; }
            if (c == '}') { brace--; continue; }
            if (c == '=' && paren == 0 && bracket == 0 && brace == 0) {
                // 排除 ==、===、!=、!==、<=、>=、=>
                boolean prevIsOp = i > 0 && "=!<>".indexOf(text.charAt(i - 1)) >= 0;
                boolean nextIsOp = i + 1 < text.length() && text.charAt(i + 1) == '=';
                boolean nextIsArrow = i + 1 < text.length() && text.charAt(i + 1) == '>';
                if (!prevIsOp && !nextIsOp && !nextIsArrow) return i;
            }
        }
        return -1;
    }
}
