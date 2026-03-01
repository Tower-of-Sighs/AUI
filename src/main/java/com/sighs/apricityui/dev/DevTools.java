package com.sighs.apricityui.dev;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Selector;

import java.util.Locale;

public class DevTools {
    private static Document document = null;
    private static Element selectedNode = null;
    private static final String PATH = "devtools/index.html";

    public static void toggle() {
        // if (Document.get(PATH).isEmpty()) {
        //     document = Document.create(PATH);
        //     load();
        //     // ApricityJS.eval("console.log(Client.screen.width)");
        // } else {
        //     document = null;
        //     Document.remove(PATH);
        // }
    }

    public static void load() {
        Element tree = document.querySelector(".tree");
        buildTree(tree, Document.getAll().get(0).body);
        buildStyle(Document.getAll().get(0).body);
        for (Element element : document.getElements()) {
            element.getRenderer().size.clear();
        }
//        document.body.getRenderer().position.clear();
    }

    public static void buildTree(Element tree, Element element) {
        String blank = "      ".repeat(element.getRoute().size() - 1);
        String tag = element.tagName.toLowerCase(Locale.ENGLISH);
        Element node = document.createElement("DIV");
        node.addEventListener("mousedown", event -> {
            selectedNode(node);
            buildStyle(element);
        });
        node.setAttribute("bindUUID", element.uuid.toString());
        node.append(text(blank + "<" + tag));

        element.getAttributes().forEach((key, value) -> {
            if (value.isEmpty()) return;
            Element prop = document.createElement("DIV");
            prop.append(text(key + "=\""));
            Element input = document.createElement("INPUT");
            input.value = value;
            input.addEventListener("blur", event -> element.setAttribute(key, value));
            prop.append(input);
            prop.append(text("\""));
            node.append(prop);
        });
        node.append(text(">"));
        if (element.children.isEmpty()) {
            Element input = document.createElement("INPUT");
            input.setAttribute("value", element.innerText);
            input.addEventListener("blur", event -> element.innerText = input.value);
            node.append(input);
            node.append(text("</" + tag + ">"));
            tree.append(node);
        } else {
            tree.append(node);
            element.children.forEach(child -> buildTree(tree, child));
            Element end = document.createElement("DIV");
            end.append(text(blank + "</" + tag + ">"));
            end.addEventListener("mousedown", event -> {
                selectedNode(end);
                buildStyle(element);
            });
            tree.append(end);
        }
    }

    private static void selectedNode(Element element) {
        if (selectedNode != null) {
            selectedNode.setAttribute("class", "none");
        }
        element.setAttribute("class", "selected");
        selectedNode = element;
    }

    private static void buildStyle(Element element) {
        Element style = document.querySelector(".style");
        style.children.forEach(Element::remove);
        System.out.println(Selector.getDebugStyles(element).size());
        Selector.getDebugStyles(element).forEach((selector, list) -> {
            Element area = document.createElement("DIV");
            Element title = document.createElement("DIV");
            title.append(text(selector));
            area.append(title);
            list.forEach((key, value) -> {
                Element entry = document.createElement("DIV");
                entry.append(text(key + ":  "));
                Element input = document.createElement("INPUT");
                input.setAttribute("value", value);
                entry.append(input);
                area.append(entry);
            });
            style.append(area);
        });
    }

    private static Element text(String str) {
        Element span = document.createElement("SPAN");
        span.innerText = str;
        return span;
    }
}
