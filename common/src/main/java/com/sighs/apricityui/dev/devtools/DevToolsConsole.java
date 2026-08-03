package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.dom.TextNode;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.parser.CSS;

/** Java event bridge for the console markup copied from devtools0.html. */
final class DevToolsConsole {
    private static final int MAX_LOG_ENTRIES = 2000;
    private static final int MAX_EXTERNAL_LOGS_PER_TICK = 128;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Pattern SELECT_PATTERN = Pattern.compile("^select\\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_PATTERN = Pattern.compile("^(\\$\\$?|querySelectorAll)\\((.+)\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARITHMETIC_PATTERN = Pattern.compile("^(\\d+)\\s*([+\\-*/])\\s*(\\d+)$");

    private enum Filter {
        ALL("all"), INFO("info"), WARN("warn"), ERROR("error");

        final String id;

        Filter(String id) {
            this.id = id;
        }

        static Filter parse(String value) {
            for (Filter filter : values()) {
                if (filter.id.equalsIgnoreCase(value)) return filter;
            }
            return ALL;
        }
    }

    private record LogEntry(String level, String text, String source, String stack, String time) {
    }

    private final DevToolsController controller;
    private final ArrayList<LogEntry> logs = new ArrayList<>();
    private final ArrayList<String> history = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private int historyIndex = -1;
    private boolean wrap;
    private Document boundDocument;
    private boolean initialized;
    private int renderedLogCount = -1;
    private Filter renderedFilter;
    private String renderedSearch = "";
    private boolean logWindowTruncated;
    private int pendingAppendedLogCount;
    private int pendingRemovedLogCount;

    DevToolsConsole(DevToolsController controller) {
        this.controller = controller;
    }

    void bind() {
        Document document = controller.toolDocument();
        if (document == null || document.body == null) return;
        if (boundDocument != document) {
            bindFilters(document);
            bindSearch(document);
            bindActions(document);
            bindInput(document);
            bindHints(document);
            boundDocument = document;
            renderedLogCount = -1;
            renderedFilter = null;
            renderedSearch = "";
            logWindowTruncated = false;
            pendingAppendedLogCount = 0;
            pendingRemovedLogCount = 0;
        }
        if (!initialized) {
            initialized = true;
            addLog("system", "PRISM//INSPECTOR v1.0.0 · Connected to page", "system", null);
            addLog("info", "Page loaded · " + countNodes(controller.targetDocument()) + " nodes parsed", "lifecycle", null);
            addLog("info", "DOM ready · CSS computed", "lifecycle", null);
            addLog("log", "Welcome! Type help to see available commands.", "system", null);
            addLog("warn", "Deprecated API usage detected at header.site-header", "compat",
                    "  at checkCompat (audit.js:128)\n  at onPageLoad (lifecycle.js:42)");
            addLog("info", "3 stylesheets loaded · 0 blocking", "network", null);
        }
        if (controller.isConsoleMode()) {
            drainExternalLogs();
            // The controller survives closing/reopening DevTools. Rebind the
            // existing console history to the newly-created tool document.
            render();
        }
    }

    void drainExternalLogs() {
        List<DevToolsLogBridge.ConsoleLog> externalLogs =
                DevToolsLogBridge.drain(MAX_EXTERNAL_LOGS_PER_TICK);
        if (externalLogs.isEmpty()) return;
        for (DevToolsLogBridge.ConsoleLog external : externalLogs) {
            appendLog(external.level(), external.text(), external.source(), external.stack(), external.time());
        }
        render();
    }

    private void bindFilters(Document document) {
        for (Element button : document.querySelectorAll(".console-filter")) {
            button.addEventListener("click", event -> setFilter(Filter.parse(button.getAttribute("data-level"))));
        }
    }

    private void bindSearch(Document document) {
        Element search = document.querySelector("#consoleSearch");
        if (search == null) return;
        search.addEventListener("input", event -> render());
        search.addEventListener("change", event -> render());
    }

    private void bindActions(Document document) {
        Element wrapButton = document.querySelector("#consoleWrapBtn");
        if (wrapButton != null) wrapButton.addEventListener("click", event -> toggleWrap());
        Element clearButton = document.querySelector("#consoleClearBtn");
        if (clearButton != null) clearButton.addEventListener("click", event -> clear());
    }

    private void bindInput(Document document) {
        Element input = document.querySelector("#consoleInput");
        if (input == null) return;
        input.addEventListener("keydown", this::handleInputKey);
    }

    private void bindHints(Document document) {
        for (Element hint : document.querySelectorAll(".hint-chip")) {
            hint.addEventListener("click", event -> {
                Element input = document.querySelector("#consoleInput");
                if (input == null) return;
                input.setValue(hint.getAttribute("data-hint"));
                input.focus();
            });
        }
    }

    private void handleInputKey(Event event) {
        if (!(event instanceof KeyEvent keyEvent)) return;
        Document document = controller.toolDocument();
        if (document == null) return;
        Element input = document.querySelector("#consoleInput");
        if (input == null) return;

        if ("Enter".equals(keyEvent.key)) {
            String command = DevToolsDom.value(input).trim();
            if (!command.isEmpty()) {
                history.remove(command);
                history.add(0, command);
                historyIndex = -1;
                execute(command);
                input.setValue("");
            }
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        if ("ArrowUp".equals(keyEvent.key)) {
            if (historyIndex < history.size() - 1) historyIndex++;
            input.setValue(historyIndex >= 0 ? history.get(historyIndex) : "");
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        if ("ArrowDown".equals(keyEvent.key)) {
            if (historyIndex > 0) {
                historyIndex--;
                input.setValue(history.get(historyIndex));
            } else {
                historyIndex = -1;
                input.setValue("");
            }
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        if ("KeyL".equals(keyEvent.code) && keyEvent.controlKey) {
            clear();
            event.preventDefault();
            event.stopPropagation();
        }
    }

    private void setFilter(Filter next) {
        filter = next == null ? Filter.ALL : next;
        Document document = controller.toolDocument();
        if (document == null) return;
        for (Element button : document.querySelectorAll(".console-filter")) {
            String classes = "console-filter";
            String level = button.getAttribute("data-level");
            if ("info".equals(level) || "warn".equals(level) || "error".equals(level)) classes += " " + level;
            if (filter.id.equals(level)) classes += " active";
            button.setAttribute("class", classes);
        }
        Element label = document.querySelector("#consoleStatusFilter");
        if (label != null) label.setTextContent("FILTER: " + filter.id.toUpperCase(Locale.ROOT));
        render();
    }

    private void toggleWrap() {
        wrap = !wrap;
        Document document = controller.toolDocument();
        Element container = document == null ? null : document.querySelector("#consoleLogs");
        if (container != null) {
            container.setAttribute("style", "white-space:" + (wrap ? "pre-wrap" : "pre") + ";");
            DevToolsDom.markDirty(document);
        }
        controller.showToast(wrap ? "Word wrap ON" : "Word wrap OFF");
    }

    private void clear() {
        logs.clear();
        logWindowTruncated = false;
        pendingAppendedLogCount = 0;
        pendingRemovedLogCount = 0;
        addLog("system", "Console cleared.", "system", null);
    }

    private void execute(String command) {
        addLog("command", command, "input", null);
        String trimmed = command.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String name = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (name) {
                case "help" -> addLog("info", helpText(), "system", null);
                case "clear", "cls" -> clear();
                case "select" -> select(trimmed);
                case "inspect" -> {
                    controller.togglePickModeFromConsole();
                    addLog("info", controller.isPickMode() ? "Inspect mode enabled" : "Inspect mode disabled", "command", null);
                }
                case "$", "$$", "queryselectorall" -> query(trimmed);
                case "copy" -> copy(trimmed);
                case "dir" -> dir(trimmed);
                case "table" -> addLog("info", "Table view (simulated)", "command", null);
                case "keys" -> keys(trimmed);
                case "count" -> addLog("info", "Total nodes: " + countNodes(controller.targetDocument()), "stats", null);
                case "tree" -> addLog("result", treeText(controller.targetDocument()), "tree", null);
                case "echo" -> addLog("log", argument(trimmed), "echo", null);
                case "warn" -> addLog("warn", argument(trimmed), "warn", null);
                case "error" -> addLog("error", argument(trimmed), "error",
                        "  at executeCommand (console.js:42)\n  at handleConsoleKey (console.js:18)");
                default -> evaluate(trimmed);
            }
        } catch (RuntimeException exception) {
            addLog("error", exception.getMessage() == null ? exception.toString() : exception.getMessage(), "error", null);
        }
    }

    private void select(String command) {
        Matcher matcher = SELECT_PATTERN.matcher(command);
        if (!matcher.matches()) {
            addLog("error", "Usage: select(<nodeId>)", "command", null);
            return;
        }
        int index = Integer.parseInt(matcher.group(1));
        Element selected = elementAt(controller.targetDocument(), index);
        if (selected == null) {
            addLog("error", "Node #" + index + " not found", "command", null);
            return;
        }
        controller.selectElement(selected);
        addLog("success", "Selected <" + selected.tagName.toLowerCase(Locale.ROOT) + "> #" + index, "command", null);
    }

    private void query(String command) {
        Matcher matcher = QUERY_PATTERN.matcher(command);
        if (!matcher.matches()) {
            addLog("error", "Usage: $(css) or $$(css)", "command", null);
            return;
        }
        String selector = matcher.group(2).trim();
        if ((selector.startsWith("\"") && selector.endsWith("\""))
                || (selector.startsWith("'") && selector.endsWith("'"))) {
            selector = selector.substring(1, selector.length() - 1);
        }
        Document target = controller.targetDocument();
        if (target == null) {
            addLog("warn", "No matching document", "query", null);
            return;
        }
        List<Element> matches = target.querySelectorAll(selector);
        if ("$".equals(matcher.group(1))) {
            addLog(matches.isEmpty() ? "warn" : "result",
                    matches.isEmpty() ? "No matching element" : "<" + matches.get(0).tagName.toLowerCase(Locale.ROOT) + ">",
                    "query", null);
            return;
        }
        StringBuilder result = new StringBuilder("Found ").append(matches.size()).append(" element(s):");
        for (Element match : matches) {
            result.append('\n').append("<").append(match.tagName.toLowerCase(Locale.ROOT)).append("> #")
                    .append(shortIndex(target, match));
        }
        addLog("result", result.toString(), "query", null);
    }

    private void copy(String command) {
        if (!command.matches("(?i)^copy\\(.+\\)$")) {
            addLog("error", "Usage: copy(value)", "command", null);
            return;
        }
        Operation.setClipboardText(command.substring(command.indexOf('(') + 1, command.length() - 1));
        addLog("success", "Copied to clipboard", "command", null);
    }

    private void dir(String command) {
        if (!command.matches("(?i)^dir\\(.+\\)$")) {
            addLog("error", "Usage: dir(object)", "command", null);
            return;
        }
        String expression = command.substring(command.indexOf('(') + 1, command.length() - 1).trim();
        if ("window".equals(expression) || "document".equals(expression)) {
            addLog("result", "{" + expression + ": true, nodeCount: " + countNodes(controller.targetDocument()) + "}", "dir", null);
        } else {
            addLog("result", "{ expression: \"" + expression + "\" }", "dir", null);
        }
    }

    private void keys(String command) {
        if (!command.matches("(?i)^keys\\(.+\\)$")) {
            addLog("error", "Usage: keys(object)", "command", null);
            return;
        }
        addLog("result", "[\"tag\", \"id\", \"class\", \"children\"]", "keys", null);
    }

    private void evaluate(String command) {
        Matcher matcher = ARITHMETIC_PATTERN.matcher(command);
        if (matcher.matches()) {
            double left = Double.parseDouble(matcher.group(1));
            double right = Double.parseDouble(matcher.group(3));
            double result = switch (matcher.group(2)) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> right == 0 ? Double.NaN : left / right;
                default -> Double.NaN;
            };
            addLog("result", formatNumber(result), "eval", null);
            return;
        }
        if ((command.startsWith("\"") && command.endsWith("\""))
                || (command.startsWith("'") && command.endsWith("'"))) {
            addLog("result", command.substring(1, command.length() - 1), "eval", null);
            return;
        }
        if (command.matches("\\d+")) {
            addLog("result", command, "eval", null);
            return;
        }
        if ("true".equals(command) || "false".equals(command) || "null".equals(command) || "undefined".equals(command)) {
            addLog("result", command, "eval", null);
            return;
        }
        addLog("error", "Uncaught ReferenceError: " + command + " is not defined", "eval", "  at <anonymous>:1:1");
    }

    private void addLog(String level, String text, String source, String stack) {
        appendLog(level, text, source, stack, LocalTime.now().format(TIME_FORMAT));
        render();
    }

    private void appendLog(String level, String text, String source, String stack, String time) {
        logs.add(new LogEntry(
                level == null || level.isBlank() ? "log" : level,
                text == null ? "" : text,
                source == null || source.isBlank() ? "page" : source,
                stack,
                time == null || time.isBlank() ? LocalTime.now().format(TIME_FORMAT) : time
        ));
        pendingAppendedLogCount++;
        int overflow = logs.size() - MAX_LOG_ENTRIES;
        if (overflow > 0) {
            logs.subList(0, overflow).clear();
            logWindowTruncated = true;
            pendingRemovedLogCount += overflow;
        }
    }

    private void render() {
        Document document = controller.toolDocument();
        if (document == null) return;
        Element container = document.querySelector("#consoleLogs");
        if (container == null) return;
        String search = DevToolsDom.value(document.querySelector("#consoleSearch")).toLowerCase(Locale.ROOT);

        int appendedStart = Math.max(0, logs.size() - pendingAppendedLogCount);
        boolean sameView = renderedLogCount >= 0
                && renderedFilter == filter
                && renderedSearch.equals(search)
                && renderedLogCount <= logs.size();
        boolean canAppend = sameView
                && !logWindowTruncated
                && appendedStart == renderedLogCount;
        boolean canTrimAndAppend = sameView
                && logWindowTruncated
                && filter == Filter.ALL
                && search.isBlank()
                && pendingRemovedLogCount > 0
                && pendingRemovedLogCount <= renderedLogCount
                && appendedStart == renderedLogCount - pendingRemovedLogCount
                && container.getChildElementCount() >= pendingRemovedLogCount;
        if (canAppend) {
            int visible = appendRenderedLogs(document, container, appendedStart, logs.size(), true, search);
            if (visible > 0) removeEmptyState(container);
            finishIncrementalRender(document, container, search);
            return;
        }
        if (canTrimAndAppend) {
            for (int i = 0; i < pendingRemovedLogCount; i++) {
                Element first = container.getFirstElementChild();
                if (first == null) break;
                first.remove();
            }
            appendRenderedLogs(document, container, appendedStart, logs.size(), false, search);
            finishIncrementalRender(document, container, search);
            return;
        }

        DevToolsDom.clear(container);
        Node fragment = document.createDocumentFragment();
        int visible = appendRenderedLogs(document, fragment, 0, logs.size(), true, search);
        if (visible == 0) {
            String empty = logs.isEmpty() ? "NO LOGS YET · TYPE \"help\" TO GET STARTED" : "NO MATCHING ENTRIES";
            fragment.appendChild(emptyState(document, empty));
        }
        container.appendChild(fragment);
        updateCounts(document);
        renderedLogCount = logs.size();
        renderedFilter = filter;
        renderedSearch = search;
        logWindowTruncated = false;
        pendingAppendedLogCount = 0;
        pendingRemovedLogCount = 0;
        container.scrollTop = container.scrollHeight;
        DevToolsDom.markDirty(document);
    }

    private int appendRenderedLogs(Document document, Node parent,
                                   int start, int end, boolean applyView, String search) {
        Node fragment = document.createDocumentFragment();
        int visible = 0;
        for (int i = start; i < end; i++) {
            LogEntry log = logs.get(i);
            if (applyView && (!matchesFilter(log)
                    || (!search.isBlank() && !log.text.toLowerCase(Locale.ROOT).contains(search)))) continue;
            fragment.appendChild(renderLog(document, log));
            visible++;
        }
        if (visible > 0) parent.appendChild(fragment);
        return visible;
    }

    private Element emptyState(Document document, String text) {
        Element state = DevToolsDom.text(document, "DIV", "console-empty-state", text);
        state.setAttribute("style", "padding:40px 20px;text-align:center;color:var(--gray);font-size:11px;letter-spacing:1px;");
        return state;
    }

    private void removeEmptyState(Element container) {
        Element state = container.querySelector(".console-empty-state");
        if (state != null) state.remove();
    }

    private void finishIncrementalRender(Document document, Element container, String search) {
        updateCounts(document);
        renderedLogCount = logs.size();
        renderedFilter = filter;
        renderedSearch = search;
        logWindowTruncated = false;
        pendingAppendedLogCount = 0;
        pendingRemovedLogCount = 0;
        container.scrollTop = container.scrollHeight;
        DevToolsDom.markDirty(document);
    }

    private Element renderLog(Document document, LogEntry log) {
        Element entry = DevToolsDom.element(document, "DIV", "log-entry " + log.level);
        entry.append(DevToolsDom.text(document, "DIV", "log-icon", icon(log.level)));
        Element body = DevToolsDom.element(document, "DIV", "log-body");
        Element meta = DevToolsDom.element(document, "DIV", "log-meta");
        meta.append(DevToolsDom.text(document, "SPAN", "log-time", log.time));
        meta.append(DevToolsDom.text(document, "SPAN", "log-source", log.source));
        body.append(meta);
        body.append(DevToolsDom.text(document, "DIV", "log-text", log.text));
        if (log.stack != null && !log.stack.isBlank()) body.append(DevToolsDom.text(document, "DIV", "log-stack", log.stack));
        entry.append(body);
        return entry;
    }

    private void updateCounts(Document document) {
        int info = 0;
        int warn = 0;
        int error = 0;
        for (LogEntry log : logs) {
            switch (log.level) {
                case "warn" -> warn++;
                case "error" -> error++;
                case "info", "log", "success", "system", "command", "result" -> info++;
                default -> {
                }
            }
        }
        setText(document, "#count-all", Integer.toString(logs.size()));
        setText(document, "#count-info", Integer.toString(info));
        setText(document, "#count-warn", Integer.toString(warn));
        setText(document, "#count-error", Integer.toString(error));
        setText(document, "#consoleStatusCount", logs.size() + " ENTRIES");
    }

    private boolean matchesFilter(LogEntry log) {
        return switch (filter) {
            case ALL -> true;
            case WARN -> "warn".equals(log.level);
            case ERROR -> "error".equals(log.level);
            case INFO -> "info".equals(log.level) || "log".equals(log.level) || "success".equals(log.level)
                    || "system".equals(log.level) || "command".equals(log.level) || "result".equals(log.level);
        };
    }

    private static String icon(String level) {
        return switch (level) {
            case "info" -> "ℹ";
            case "warn" -> "⚠";
            case "error" -> "✕";
            case "success" -> "✓";
            case "command" -> "›";
            case "result" -> "←";
            case "system" -> "◆";
            default -> "•";
        };
    }

    private static String helpText() {
        return "Available commands:\n"
                + "  help          Show this help\n"
                + "  clear         Clear console\n"
                + "  select(<id>)  Select element by ID\n"
                + "  inspect       Enter inspect mode\n"
                + "  $$(<css>)     Query elements\n"
                + "  $(<css>)      Query first element\n"
                + "  copy(<val>)   Copy to clipboard\n"
                + "  dir(<obj>)    Display object\n"
                + "  table(<arr>)  Display as table\n"
                + "  keys(<obj>)   Object keys\n"
                + "  count()       Count nodes\n"
                + "  tree          Show DOM tree\n"
                + "  echo <text>   Print text";
    }

    private static String argument(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? "" : command.substring(space + 1).trim();
    }

    private static String formatNumber(double value) {
        if (Double.isFinite(value) && value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    private static void setText(Document document, String selector, String value) {
        Element element = document.querySelector(selector);
        if (element != null) element.setTextContent(value);
    }

    private static Element elementAt(Document document, int index) {
        if (document == null || document.documentElement == null || index < 1) return null;
        int[] current = {0};
        return elementAt(document.documentElement, index, current);
    }

    private static Element elementAt(Element element, int index, int[] current) {
        if (element == null) return null;
        if (++current[0] == index) return element;
        for (Node child : element.getChildNodes()) {
            if (child instanceof Element childElement) {
                Element found = elementAt(childElement, index, current);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int shortIndex(Document document, Element target) {
        int[] current = {0};
        return shortIndex(document == null ? null : document.documentElement, target, current);
    }

    private static int shortIndex(Element element, Element target, int[] current) {
        if (element == null) return -1;
        if (element == target) return ++current[0];
        current[0]++;
        for (Node child : element.getChildNodes()) {
            if (child instanceof Element childElement) {
                int found = shortIndex(childElement, target, current);
                if (found >= 0) return found;
            }
        }
        return -1;
    }

    private static int countNodes(Document document) {
        return document == null || document.documentElement == null ? 0 : countNodes(document.documentElement);
    }

    private static int countNodes(Node node) {
        if (node == null) return 0;
        int count = 1;
        for (Node child : node.getChildNodes()) {
            if (child instanceof TextNode text && (text.getTextContent() == null || text.getTextContent().isBlank())) continue;
            count += countNodes(child);
        }
        return count;
    }

    private static String treeText(Document document) {
        if (document == null || document.documentElement == null) return "No debuggable document";
        StringBuilder text = new StringBuilder();
        appendTree(text, document.documentElement, 0);
        return text.toString();
    }

    private static void appendTree(StringBuilder text, Element element, int depth) {
        text.append("  ".repeat(Math.max(0, depth))).append('<')
                .append(element.tagName.toLowerCase(Locale.ROOT)).append('>').append('\n');
        for (Node child : element.getChildNodes()) {
            if (child instanceof Element childElement) appendTree(text, childElement, depth + 1);
        }
    }
}
