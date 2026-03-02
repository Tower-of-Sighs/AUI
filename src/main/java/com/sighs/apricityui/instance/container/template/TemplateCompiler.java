package com.sighs.apricityui.instance.container.template;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.resource.HTML;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将模板编译为服务端可消费的容器声明。
 */
public final class TemplateCompiler {
    private static final Pattern TAG_PATTERN = Pattern.compile("<!--.*?-->|</?[a-zA-Z][a-zA-Z0-9:-]*(?:\\s+[^<>]*?)?/?>", Pattern.DOTALL);
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("<\\s*([a-zA-Z][a-zA-Z0-9:-]*)");
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:\\-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");

    public static TemplateSpec compile(String rawTemplatePath) {
        String templatePath = normalizeTemplatePath(rawTemplatePath);
        if (templatePath == null) return null;

        String rawHtml = HTML.getTemple(templatePath);
        if (rawHtml == null || rawHtml.isBlank()) {
            ApricityUI.LOGGER.warn("Template compile failed: HTML content not found, path={}", templatePath);
            return null;
        }

        List<ContainerDraft> drafts = parseContainerDrafts(rawHtml);
        if (drafts.isEmpty()) {
            return new TemplateSpec(templatePath, "", List.of());
        }

        String primaryContainerId = resolvePrimaryContainerId(drafts);
        ArrayList<TemplateSpec.ContainerSpec> containers = new ArrayList<>(drafts.size());
        for (ContainerDraft draft : drafts) {
            int explicitCapacity = draft.maxExplicitIndex() + 1;
            int playerCapacity = draft.bindType() == ContainerBindType.PLAYER
                    ? ContainerBindType.PLAYER_SLOT_COUNT
                    : 0;
            int requiredCapacity = Math.max(Math.max(draft.declaredSize(), explicitCapacity), playerCapacity);
            containers.add(new TemplateSpec.ContainerSpec(
                    draft.id(),
                    draft.bindType(),
                    draft.id().equals(primaryContainerId),
                    requiredCapacity,
                    draft.declaredSize(),
                    new ArrayList<>(draft.explicitIndices())
            ));
        }

        return new TemplateSpec(templatePath, primaryContainerId, containers);
    }

    private static String normalizeTemplatePath(String rawTemplatePath) {
        if (rawTemplatePath == null) return null;
        String path = rawTemplatePath.trim().replace('\\', '/');
        if (path.isEmpty()) return null;

        if (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("apricity/")) path = path.substring("apricity/".length());
        if (path.contains("..")) return null;
        if (!path.endsWith(".html")) return null;

        String[] segments = path.split("/");
        if (segments.length < 2) return null;
        return path;
    }

    private static List<ContainerDraft> parseContainerDrafts(String rawHtml) {
        ArrayList<ContainerDraft> drafts = new ArrayList<>();
        Deque<ContainerFrame> containerStack = new ArrayDeque<>();
        int topLevelIndex = 0;

        Matcher matcher = TAG_PATTERN.matcher(rawHtml);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("<!--")) continue;

            boolean closingTag = token.startsWith("</");
            if (closingTag) {
                String closingTagName = extractClosingTagName(token);
                if ("container".equals(closingTagName) && !containerStack.isEmpty()) {
                    containerStack.pop();
                }
                continue;
            }

            boolean selfClosing = token.endsWith("/>");
            TagData tagData = parseTag(token);
            if (tagData == null) continue;

            if ("container".equals(tagData.tagName())) {
                ContainerDraft activeTopLevel = activeTopLevelDraft(containerStack);
                if (activeTopLevel == null) {
                    String containerId = resolveContainerId(tagData.attributes().get("id"), topLevelIndex);
                    boolean primary = parsePrimary(tagData.attributes().get("primary"));
                    ContainerBindType bindType = resolveBindType(tagData.attributes().get("bind"), containerId);
                    int declaredSize = parsePositiveInt(tagData.attributes().get("size"), 0);
                    ContainerDraft draft = new ContainerDraft(containerId, bindType, primary, declaredSize);
                    drafts.add(draft);
                    topLevelIndex++;
                    containerStack.push(new ContainerFrame(true, draft));
                } else {
                    containerStack.push(new ContainerFrame(false, activeTopLevel));
                }

                if (selfClosing && !containerStack.isEmpty()) {
                    containerStack.pop();
                }
                continue;
            }

            if ("slot".equals(tagData.tagName())) {
                ContainerDraft activeTopLevel = activeTopLevelDraft(containerStack);
                if (activeTopLevel != null) {
                    activeTopLevel.consumeSlot(tagData.attributes());
                }
            }
        }

        return drafts;
    }

    private static String resolvePrimaryContainerId(List<ContainerDraft> drafts) {
        for (ContainerDraft draft : drafts) {
            if (draft.primary()) return draft.id();
        }
        return drafts.isEmpty() ? "" : drafts.get(0).id();
    }

    private static ContainerDraft activeTopLevelDraft(Deque<ContainerFrame> stack) {
        if (stack.isEmpty()) return null;
        ContainerFrame frame = stack.peek();
        return frame == null ? null : frame.topLevelDraft();
    }

    private static String resolveContainerId(String rawId, int index) {
        String normalized = normalizeContainerId(rawId);
        if (normalized != null && !normalized.isBlank()) return normalized;
        return "c" + Math.max(0, index);
    }

    private static String normalizeContainerId(String rawContainerId) {
        if (rawContainerId == null || rawContainerId.isBlank()) return null;
        String containerId = rawContainerId.trim().toLowerCase(Locale.ROOT);
        if (!containerId.matches("^[a-z0-9_./-]+$")) return null;
        return containerId;
    }

    private static boolean parsePrimary(String rawPrimary) {
        if (rawPrimary == null || rawPrimary.isBlank()) return false;
        String normalized = rawPrimary.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private static ContainerBindType resolveBindType(String rawBindType, String containerId) {
        if (rawBindType == null || rawBindType.isBlank()) {
            return ContainerBindType.PLAYER;
        }
        ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
        if (bindType != null) return bindType;
        ApricityUI.LOGGER.warn("Template compile: invalid bindType='{}', fallback player, container={}", rawBindType, containerId);
        return ContainerBindType.PLAYER;
    }

    private static int parsePositiveInt(String rawNumber, int fallback) {
        if (rawNumber == null || rawNumber.isBlank()) return Math.max(0, fallback);
        try {
            int parsed = Integer.parseInt(rawNumber.trim());
            return parsed > 0 ? parsed : Math.max(0, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }

    private static boolean isBoundSlot(Map<String, String> attributes) {
        String rawMode = attributes.get("mode");
        if (rawMode == null || rawMode.isBlank()) return true;
        return "bound".equals(rawMode.trim().toLowerCase(Locale.ROOT));
    }

    private static int parseRepeatCount(String rawRepeat) {
        return Math.max(1, parsePositiveInt(rawRepeat, 1));
    }

    private static Integer parseSlotIndex(String rawSlotIndex) {
        if (rawSlotIndex == null || rawSlotIndex.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(rawSlotIndex.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String extractClosingTagName(String token) {
        if (token == null || token.length() < 4) return "";
        String middle = token.substring(2, token.length() - 1).trim();
        if (middle.isEmpty()) return "";
        int split = middle.indexOf(' ');
        String rawTag = split < 0 ? middle : middle.substring(0, split);
        return rawTag.toLowerCase(Locale.ROOT);
    }

    private static TagData parseTag(String token) {
        Matcher nameMatcher = TAG_NAME_PATTERN.matcher(token);
        if (!nameMatcher.find()) return null;

        String rawTagName = nameMatcher.group(1);
        if (rawTagName == null || rawTagName.isBlank()) return null;
        String tagName = rawTagName.toLowerCase(Locale.ROOT);

        int start = nameMatcher.end();
        int end = token.endsWith("/>") ? token.length() - 2 : token.length() - 1;
        if (end < start) end = start;
        String attrsPart = token.substring(start, end);
        return new TagData(tagName, parseAttributes(attrsPart));
    }

    private static Map<String, String> parseAttributes(String attrsPart) {
        LinkedHashMap<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(attrsPart);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || key.isBlank()) continue;
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            String value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            if (value == null) value = matcher.group(5);
            if (value == null) value = "";
            attrs.put(normalizedKey, value.trim());
        }
        return attrs;
    }

    private record TagData(String tagName, Map<String, String> attributes) {
    }

    private record ContainerFrame(boolean topLevel, ContainerDraft topLevelDraft) {
    }

    private static final class ContainerDraft {
        private final String id;
        private final ContainerBindType bindType;
        private final boolean primary;
        private final int declaredSize;
        private final LinkedHashSet<Integer> explicitIndices = new LinkedHashSet<>();
        private int nextImplicitIndex = 0;

        private ContainerDraft(String id,
                               ContainerBindType bindType,
                               boolean primary,
                               int declaredSize) {
            this.id = id;
            this.bindType = bindType;
            this.primary = primary;
            this.declaredSize = Math.max(0, declaredSize);
        }

        private String id() {
            return id;
        }

        private ContainerBindType bindType() {
            return bindType;
        }

        private boolean primary() {
            return primary;
        }

        private int declaredSize() {
            return declaredSize;
        }

        private List<Integer> explicitIndices() {
            return List.copyOf(explicitIndices);
        }

        private int maxExplicitIndex() {
            int max = -1;
            for (Integer index : explicitIndices) {
                if (index == null || index < 0) continue;
                if (index > max) max = index;
            }
            return max;
        }

        private void consumeSlot(Map<String, String> attributes) {
            if (bindType == ContainerBindType.VIRTUAL_UI) return;
            if (!isBoundSlot(attributes)) return;

            int repeat = parseRepeatCount(attributes.get("repeat"));
            Integer parsedSlotIndex = parseSlotIndex(attributes.get("slot-index"));
            int start = parsedSlotIndex == null ? nextImplicitIndex : parsedSlotIndex;
            for (int offset = 0; offset < repeat; offset++) {
                explicitIndices.add(start + offset);
            }
            int candidate = start + repeat;
            if (candidate > nextImplicitIndex) {
                nextImplicitIndex = candidate;
            }
        }
    }
}
