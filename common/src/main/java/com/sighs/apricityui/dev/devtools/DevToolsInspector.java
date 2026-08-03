package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.ui.ColorPicker;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class DevToolsInspector {
    private final DevToolsController controller;

    DevToolsInspector(DevToolsController controller) {
        this.controller = controller;
    }

    void render(Document targetDocument, Element selected, DevToolsController.InspectorTab activeTab) {
        Document tool = controller.toolDocument();
        if (tool == null) return;
        Element attributes = tool.querySelector("#pane-attributes");
        Element styles = tool.querySelector("#pane-styles");
        Element boxModel = tool.querySelector("#pane-boxmodel");
        if (attributes == null || styles == null || boxModel == null) return;

        Element activePane = switch (activeTab) {
            case ATTRIBUTES -> attributes;
            case STYLES -> styles;
            case BOXMODEL -> boxModel;
        };
        DevToolsDom.clear(activePane);

        if (targetDocument == null || selected == null) {
            renderEmpty(activePane);
            return;
        }
        switch (activeTab) {
            case ATTRIBUTES -> renderAttributes(attributes, selected);
            case STYLES -> renderStyles(styles, selected);
            case BOXMODEL -> renderBoxModel(boxModel, selected);
        }
    }

    private void renderEmpty(Element pane) {
        Document tool = pane.document;
        Element empty = DevToolsDom.element(tool, "DIV", "empty-state");
        empty.append(DevToolsDom.text(tool, "DIV", "empty-state-icon", "<>"));
        empty.append(DevToolsDom.text(tool, "DIV", "empty-state-text",
                DevToolsTranslations.translate("devtools.apricityui.no_element_selected")));
        empty.append(DevToolsDom.text(tool, "DIV", "empty-state-sub",
                DevToolsTranslations.translate("devtools.apricityui.select_element_hint")));
        pane.append(empty);
    }

    private void renderAttributes(Element pane, Element target) {
        Document tool = pane.document;
        Element block = DevToolsDom.element(tool, "DIV", "attr-block");
        block.append(DevToolsDom.text(tool, "DIV", "attr-block-header",
                DevToolsTranslations.translate("devtools.apricityui.element_attributes", target.tagName.toUpperCase(Locale.ROOT))));

        if (target.getAttributes().isEmpty()) {
            Element empty = DevToolsDom.text(tool, "DIV", "attr-row",
                    DevToolsTranslations.translate("devtools.apricityui.no_attributes"));
            empty.setAttribute("style", "color:var(--gray);font-size:10px;letter-spacing:0.5px;");
            block.append(empty);
        } else {
            for (Map.Entry<String, String> attribute : target.getAttributes().entrySet()) {
                block.append(attributeRow(target, attribute.getKey(), attribute.getValue()));
            }
        }

        Element add = DevToolsDom.text(tool, "DIV", "attr-add",
                DevToolsTranslations.translate("devtools.apricityui.add_attribute"));
        add.addEventListener("click", event -> showAttributeAdder(block, add, target));
        block.append(add);
        pane.append(block);

        Element info = DevToolsDom.element(tool, "DIV", "attr-block");
        info.append(DevToolsDom.text(tool, "DIV", "attr-block-header",
                DevToolsTranslations.translate("devtools.apricityui.element_info")));
        info.append(infoRow(tool, "tag", target.tagName.toLowerCase(Locale.ROOT), "var(--tag)"));
        String id = target.getAttribute("id");
        info.append(infoRow(tool, "id", id == null || id.isBlank() ? shortUuid(target) : "#" + id, "var(--gray-dark)"));
        info.append(infoRow(tool, "children", Integer.toString(target.children.size()), "var(--num)"));
        pane.append(info);
    }

    private Element attributeRow(Element target, String key, String value) {
        Document tool = controller.toolDocument();
        Element row = DevToolsDom.element(tool, "DIV", "attr-row");
        row.append(DevToolsDom.text(tool, "SPAN", "attr-name", key));
        row.append(DevToolsDom.text(tool, "SPAN", "attr-eq", "="));
        Element editor = DevToolsDom.input(tool, "attr-value", value, "");
        editor.addEventListener("blur", event -> controller.updateAttribute(target, key, DevToolsDom.value(editor)));
        editor.addEventListener("keydown", event -> {
            if (!controller.isCommitKey(event)) return;
            controller.updateAttribute(target, key, DevToolsDom.value(editor));
            controller.clearToolFocus();
        });
        row.append(editor);
        Element remove = DevToolsDom.text(tool, "DIV", "attr-delete", "\u00d7");
        remove.setAttribute("title", DevToolsTranslations.translate("devtools.apricityui.delete"));
        remove.addEventListener("click", event -> controller.deleteAttribute(target, key));
        row.append(remove);
        return row;
    }

    private void showAttributeAdder(Element block, Element add, Element target) {
        if (!add.isConnected()) return;
        Document tool = block.document;
        Element row = DevToolsDom.element(tool, "DIV", "attr-row");
        Element name = DevToolsDom.input(tool, "attr-value", "", "name");
        Element value = DevToolsDom.input(tool, "attr-value", "", "value");
        Element save = DevToolsDom.text(tool, "DIV", "attr-delete", "+");
        save.setAttribute("style", "opacity:1;color:var(--purple);");
        Runnable commit = () -> controller.addAttribute(target, DevToolsDom.value(name), DevToolsDom.value(value));
        save.addEventListener("click", event -> commit.run());
        name.addEventListener("keydown", event -> commitOnEnter(event, commit));
        value.addEventListener("keydown", event -> commitOnEnter(event, commit));
        row.append(name);
        row.append(value);
        row.append(save);
        add.before(row);
        add.remove();
        DevToolsDom.markDirty(tool);
    }

    private Element infoRow(Document tool, String key, String value, String color) {
        Element row = DevToolsDom.element(tool, "DIV", "attr-row");
        Element name = DevToolsDom.text(tool, "SPAN", "attr-name", key);
        name.setAttribute("style", "color:var(--gray);");
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "attr-eq", ":"));
        Element result = DevToolsDom.text(tool, "SPAN", "", value);
        result.setAttribute("style", "color:" + color + ";font-weight:600;");
        row.append(result);
        return row;
    }

    private void renderStyles(Element pane, Element target) {
        Document tool = pane.document;
        Element inline = DevToolsDom.element(tool, "DIV", "style-rule");
        inline.append(DevToolsDom.text(tool, "DIV", "style-selector", selector(target)));
        Element body = DevToolsDom.element(tool, "DIV", "style-body");

        LinkedHashMap<String, String> declarations = controller.inlineStyles(target);
        controller.disabledStyleEntries(target).forEach(declarations::putIfAbsent);
        if (declarations.isEmpty()) {
            Element empty = DevToolsDom.text(tool, "DIV", "style-prop", "No inline styles");
            empty.setAttribute("style", "color:var(--gray);font-size:10px;");
            body.append(empty);
        } else {
            declarations.forEach((property, value) -> body.append(stylePropertyRow(target, property, value)));
        }
        Element add = DevToolsDom.text(tool, "DIV", "style-add",
                DevToolsTranslations.translate("devtools.apricityui.add_property"));
        add.addEventListener("click", event -> showStyleAdder(body, add, target));
        body.append(add);
        inline.append(body);
        pane.append(inline);
        fitStyleNameInputs(inline);

        Element computed = DevToolsDom.element(tool, "DIV", "style-rule");
        computed.append(DevToolsDom.text(tool, "DIV", "style-selector", "computed \u00b7 size"));
        Element computedBody = DevToolsDom.element(tool, "DIV", "style-body");
        Element.DOMRect rect = boundingRect(target);
        computedBody.append(readonlyStyleRow(tool, "width", px(rect.width)));
        computedBody.append(readonlyStyleRow(tool, "height", px(rect.height)));
        computed.append(computedBody);
        pane.append(computed);

        for (Selector.DebugStyleBlock matched : Selector.getDebugStyles(target)) {
            Element rule = DevToolsDom.element(tool, "DIV", "style-rule");
            rule.setAttribute("data-rule-order", Integer.toString(matched.ruleOrder()));
            String source = sourceName(matched.sourcePath());
            rule.append(DevToolsDom.text(tool, "DIV", "style-selector", matched.selector() + " \u00b7 " + source));
            Element ruleBody = DevToolsDom.element(tool, "DIV", "style-body");
            controller.stylesheetStyles(matched).forEach((property, declaration) ->
                    ruleBody.append(stylesheetPropertyRow(target, matched.ruleOrder(), property, declaration)));
            Element ruleAdd = DevToolsDom.text(tool, "DIV", "style-add",
                    DevToolsTranslations.translate("devtools.apricityui.add_property"));
            ruleAdd.addEventListener("click", event ->
                    showStylesheetAdder(ruleBody, ruleAdd, target, matched.ruleOrder()));
            ruleBody.append(ruleAdd);
            rule.append(ruleBody);
            pane.append(rule);
            fitStyleNameInputs(rule);
        }
    }

    private Element stylePropertyRow(Element target, String property, String value) {
        Document tool = controller.toolDocument();
        boolean disabled = controller.isStyleDisabled(target, property);
        Element row = DevToolsDom.element(tool, "DIV", disabled ? "style-prop disabled" : "style-prop");
        Element toggle = DevToolsDom.element(tool, "DIV", disabled ? "style-toggle" : "style-toggle on");
        toggle.addEventListener("click", event -> controller.toggleStyle(target, property));
        row.append(toggle);

        Element name = DevToolsDom.input(tool, "style-name", property, "");
        bindStyleNameSizing(name);
        name.addEventListener("blur", event -> controller.renameStyle(target, property, DevToolsDom.value(name)));
        name.addEventListener("keydown", event -> {
            if (!controller.isCommitKey(event)) return;
            controller.renameStyle(target, property, DevToolsDom.value(name));
            controller.clearToolFocus();
        });
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "style-colon", ":"));

        Element editor = DevToolsDom.input(tool, isColorValue(value) ? "style-value color-val" : "style-value", value, "");
        editor.addEventListener("blur", event -> controller.updateStyle(target, property, DevToolsDom.value(editor)));
        editor.addEventListener("keydown", event -> {
            if (!controller.isCommitKey(event)) return;
            controller.updateStyle(target, property, DevToolsDom.value(editor));
            controller.clearToolFocus();
        });
        row.append(editor);
        if (isColorValue(value)) {
            Element swatch = DevToolsDom.element(tool, "SPAN", "style-color-swatch");
            swatch.setAttribute("style", "background:" + value + ";");
            swatch.addEventListener("click", event -> ColorPicker.pickIn(tool, swatch, DevToolsDom.value(editor))
                    .thenAccept(selected -> selected.ifPresent(next -> controller.updateStyle(target, property, next))));
            row.append(swatch);
        }
        row.append(DevToolsDom.text(tool, "SPAN", "style-semicolon", ";"));
        Element remove = DevToolsDom.text(tool, "DIV", "style-prop-delete", "\u00d7");
        remove.addEventListener("click", event -> controller.deleteStyle(target, property));
        row.append(remove);
        return row;
    }

    private void showStyleAdder(Element body, Element add, Element target) {
        if (!add.isConnected()) return;
        Document tool = body.document;
        Element row = DevToolsDom.element(tool, "DIV", "style-prop");
        row.append(DevToolsDom.element(tool, "DIV", "style-toggle on"));
        Element name = DevToolsDom.input(tool, "style-name", "", "property");
        bindStyleNameSizing(name);
        Element value = DevToolsDom.input(tool, "style-value", "", "value");
        Element save = DevToolsDom.text(tool, "DIV", "style-prop-delete", "+");
        save.setAttribute("style", "opacity:1;color:var(--purple);");
        Runnable commit = () -> controller.updateStyle(target, DevToolsDom.value(name), DevToolsDom.value(value));
        save.addEventListener("click", event -> commit.run());
        name.addEventListener("keydown", event -> commitOnEnter(event, commit));
        value.addEventListener("keydown", event -> commitOnEnter(event, commit));
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "style-colon", ":"));
        row.append(value);
        row.append(DevToolsDom.text(tool, "SPAN", "style-semicolon", ";"));
        row.append(save);
        add.before(row);
        add.remove();
        fitStyleNameInput(name);
        DevToolsDom.markDirty(tool);
    }

    private Element readonlyStyleRow(Document tool, String property, String value) {
        Element row = DevToolsDom.element(tool, "DIV", "style-prop");
        Element name = DevToolsDom.text(tool, "SPAN", "style-name", property);
        name.setAttribute("style", "color:var(--gray);");
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "style-colon", ":"));
        row.append(DevToolsDom.text(tool, "SPAN", "style-value", value));
        row.append(DevToolsDom.text(tool, "SPAN", "style-semicolon", ";"));
        return row;
    }

    private Element stylesheetPropertyRow(Element target, int ruleOrder, String property,
                                          DevToolsController.RuleStyle declaration) {
        Document tool = controller.toolDocument();
        String rowClass = declaration.disabled() ? "style-prop disabled"
                : declaration.overridden() ? "style-prop overridden" : "style-prop";
        Element row = DevToolsDom.element(tool, "DIV", rowClass);
        row.setAttribute("data-property", property);
        Element toggle = DevToolsDom.element(tool, "DIV",
                declaration.disabled() ? "style-toggle" : "style-toggle on");
        toggle.addEventListener("click", event ->
                controller.toggleStylesheetStyle(target, ruleOrder, property));
        row.append(toggle);

        Element name = DevToolsDom.input(tool, "style-name", property, "");
        bindStyleNameSizing(name);
        name.addEventListener("blur", event -> controller.renameStylesheetStyle(
                target, ruleOrder, property, DevToolsDom.value(name)));
        name.addEventListener("keydown", event -> {
            if (!controller.isCommitKey(event)) return;
            controller.renameStylesheetStyle(target, ruleOrder, property, DevToolsDom.value(name));
            controller.clearToolFocus();
        });
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "style-colon", ":"));

        String displayValue = declaration.displayValue();
        Element value = DevToolsDom.input(tool,
                isColorValue(declaration.value()) ? "style-value color-val" : "style-value",
                displayValue, "");
        value.addEventListener("blur", event -> controller.updateStylesheetStyle(
                target, ruleOrder, property, DevToolsDom.value(value)));
        value.addEventListener("keydown", event -> {
            if (!controller.isCommitKey(event)) return;
            controller.updateStylesheetStyle(target, ruleOrder, property, DevToolsDom.value(value));
            controller.clearToolFocus();
        });
        row.append(value);
        if (isColorValue(declaration.value())) {
            Element swatch = DevToolsDom.element(tool, "SPAN", "style-color-swatch");
            swatch.setAttribute("style", "background:" + declaration.value() + ";");
            swatch.addEventListener("click", event -> ColorPicker.pickIn(tool, swatch, DevToolsDom.value(value))
                    .thenAccept(selected -> selected.ifPresent(next -> controller.updateStylesheetStyle(target, ruleOrder, property, next))));
            row.append(swatch);
        }
        row.append(DevToolsDom.text(tool, "SPAN", "style-semicolon", ";"));
        Element remove = DevToolsDom.text(tool, "DIV", "style-prop-delete", "\u00d7");
        remove.addEventListener("click", event ->
                controller.deleteStylesheetStyle(target, ruleOrder, property));
        row.append(remove);
        return row;
    }

    private void showStylesheetAdder(Element body, Element add, Element target, int ruleOrder) {
        if (!add.isConnected()) return;
        Document tool = body.document;
        Element row = DevToolsDom.element(tool, "DIV", "style-prop");
        row.append(DevToolsDom.element(tool, "DIV", "style-toggle on"));
        Element name = DevToolsDom.input(tool, "style-name", "", "property");
        bindStyleNameSizing(name);
        Element value = DevToolsDom.input(tool, "style-value", "", "value");
        Element save = DevToolsDom.text(tool, "DIV", "style-prop-delete", "+");
        save.setAttribute("style", "opacity:1;color:var(--purple);");
        Runnable commit = () -> controller.addStylesheetStyle(
                target, ruleOrder, DevToolsDom.value(name), DevToolsDom.value(value));
        save.addEventListener("click", event -> commit.run());
        name.addEventListener("keydown", event -> commitOnEnter(event, commit));
        value.addEventListener("keydown", event -> commitOnEnter(event, commit));
        row.append(name);
        row.append(DevToolsDom.text(tool, "SPAN", "style-colon", ":"));
        row.append(value);
        row.append(DevToolsDom.text(tool, "SPAN", "style-semicolon", ";"));
        row.append(save);
        add.before(row);
        add.remove();
        fitStyleNameInput(name);
        DevToolsDom.markDirty(tool);
    }

    private void bindStyleNameSizing(Element input) {
        input.addEventListener("input", event -> fitStyleNameInput(input));
    }

    private void fitStyleNameInputs(Element root) {
        for (Element input : root.querySelectorAll("input.style-name")) fitStyleNameInput(input);
    }

    private void fitStyleNameInput(Element input) {
        if (input == null) return;
        String content = DevToolsDom.value(input);
        if (content == null || content.isEmpty()) content = input.getAttribute("placeholder");
        double textWidth = Size.measureText(input, content == null ? "" : content);
        Box box = Box.of(input);
        double borderBoxWidth = textWidth + box.getPaddingHorizontal() + box.getBorderHorizontal() + 1;
        input.setAttribute("style", "width:" + Math.max(8, Math.ceil(borderBoxWidth)) + "px;");
    }

    private void renderBoxModel(Element pane, Element target) {
        Document tool = pane.document;
        Box box = Box.of(target);
        Element.DOMRect rect = boundingRect(target);
        double contentWidth = Math.max(0, rect.width - box.getPaddingHorizontal() - box.getBorderHorizontal());
        double contentHeight = Math.max(0, rect.height - box.getPaddingVertical() - box.getBorderVertical());

        Element model = DevToolsDom.element(tool, "DIV", "boxmodel");
        Element visual = DevToolsDom.element(tool, "DIV", "boxmodel-visual");
        Element margin = DevToolsDom.element(tool, "DIV", "bx-margin");
        margin.append(boxLabel(tool, "top", box.getMarginTop()));
        margin.append(boxLabel(tool, "bottom", box.getMarginBottom()));
        margin.append(boxLabel(tool, "left", box.getMarginLeft()));
        margin.append(boxLabel(tool, "right", box.getMarginRight()));

        Element border = DevToolsDom.element(tool, "DIV", "bx-border");
        border.append(boxLabel(tool, "top", box.getBorderTop()));
        border.append(boxLabel(tool, "bottom", box.getBorderBottom()));
        Element padding = DevToolsDom.element(tool, "DIV", "bx-padding");
        padding.append(boxLabel(tool, "top", box.getPaddingTop()));
        padding.append(boxLabel(tool, "bottom", box.getPaddingBottom()));
        Element content = DevToolsDom.text(tool, "DIV", "bx-content",
                number(contentWidth) + " \u00d7 " + number(contentHeight));
        padding.append(content);
        border.append(padding);
        margin.append(border);
        visual.append(margin);

        Element legend = DevToolsDom.element(tool, "DIV", "boxmodel-legend");
        legend.append(legend(tool, "rgba(249,115,22,0.3)", "#f97316", "dashed", DevToolsTranslations.translate("devtools.apricityui.margin")));
        legend.append(legend(tool, "rgba(139,92,246,0.3)", "#8b5cf6", "solid", DevToolsTranslations.translate("devtools.apricityui.border")));
        legend.append(legend(tool, "rgba(34,197,94,0.3)", "#22c55e", "dashed", DevToolsTranslations.translate("devtools.apricityui.padding")));
        legend.append(legend(tool, "rgba(59,130,246,0.3)", "#3b82f6", "solid", DevToolsTranslations.translate("devtools.apricityui.content")));
        visual.append(legend);
        model.append(visual);
        pane.append(model);
    }

    private Element boxLabel(Document tool, String side, double value) {
        return DevToolsDom.text(tool, "SPAN", "bx-label " + side, number(value));
    }

    private Element legend(Document tool, String background, String border, String borderStyle, String label) {
        Element item = DevToolsDom.element(tool, "DIV", "legend-item");
        Element swatch = DevToolsDom.element(tool, "SPAN", "legend-swatch");
        swatch.setAttribute("style", "background:" + background + ";border:1px " + borderStyle + " " + border + ";");
        item.append(swatch);
        item.append(DevToolsDom.text(tool, "SPAN", "", label));
        return item;
    }

    private void commitOnEnter(Event event, Runnable action) {
        if (!controller.isCommitKey(event)) return;
        action.run();
        controller.clearToolFocus();
    }

    private static String selector(Element element) {
        StringBuilder result = new StringBuilder(element.tagName.toLowerCase(Locale.ROOT));
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) result.append('#').append(id);
        String classes = element.getAttribute("class");
        if (classes != null && !classes.isBlank()) {
            for (String name : classes.trim().split("\\s+")) {
                if (!name.isBlank()) result.append('.').append(name);
            }
        }
        return result.toString();
    }

    private static String sourceName(String source) {
        if (source == null || source.isBlank()) return DevToolsTranslations.translate("devtools.apricityui.inline_stylesheet");
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        return slash >= 0 && slash < source.length() - 1 ? source.substring(slash + 1) : source;
    }

    private static boolean isColorValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("#") || normalized.startsWith("rgb") || normalized.startsWith("hsl")
                || normalized.startsWith("linear-gradient") || normalized.startsWith("radial-gradient");
    }

    private static String shortUuid(Element element) {
        String uuid = element.uuid.toString();
        return "#" + uuid.substring(0, Math.min(8, uuid.length()));
    }

    private static String px(double value) {
        return number(value) + "px";
    }

    private static Element.DOMRect boundingRect(Element element) {
        try {
            return element.getBoundingClientRect();
        } catch (NoClassDefFoundError error) {
            if (!isUnavailableClientLayoutRuntime(error)) throw error;
            return new Element.DOMRect(0, 0, 0, 0);
        }
    }

    private static boolean isUnavailableClientLayoutRuntime(NoClassDefFoundError error) {
        String missing = error.getMessage();
        if (missing == null) return false;
        String className = missing.replace('.', '/');
        return className.startsWith("net/minecraft/client/renderer/")
                || className.startsWith("net/minecraft/client/gui/")
                || className.startsWith("net/minecraft/network/chat/")
                || className.startsWith("com/mojang/blaze3d/");
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.01) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
