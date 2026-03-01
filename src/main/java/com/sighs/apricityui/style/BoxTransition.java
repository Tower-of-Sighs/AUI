package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Style;

import java.util.List;

// 测试中，不保证能用
public class BoxTransition {
    public static void createTransition(Style startStyle, Style endStyle, List<Transition> result, String property, double duration, double delay) {
        long time = System.currentTimeMillis();

        if ("box-shadow".equals(property)) {
            Box.Shadow start = Box.parseShadow(startStyle.boxShadow);
            Box.Shadow end = Box.parseShadow(endStyle.boxShadow);

            if (!start.equals(end)) {
                result.add(new Transition("box-shadow-x", start.x(), end.x(), duration, delay, time));
                result.add(new Transition("box-shadow-y", start.y(), end.y(), duration, delay, time));
                result.add(new Transition("box-shadow-size", start.size(), end.size(), duration, delay, time));
                result.add(new Transition("box-shadow-color", start.color().getValue(), end.color().getValue(), duration, delay, time));
            }
            return;
        }

        if ("border-radius".equals(property)) {
            double[] startRadii = parseFourValues(startStyle.borderRadius);
            double[] endRadii = parseFourValues(endStyle.borderRadius);
            String[] corners = {"tl", "tr", "br", "bl"};

            for (int i = 0; i < 4; i++) {
                if (startRadii[i] != endRadii[i]) {
                    result.add(new Transition("border-radius-" + corners[i], startRadii[i], endRadii[i], duration, delay, time));
                }
            }
            return;
        }

        if ("margin".equals(property) || "padding".equals(property)) {
            for (String side : Box.SIDE) {
                String fullProp = property + "-" + side;

                double sVal = Size.parse(startStyle.get(fullProp));
                double eVal = Size.parse(endStyle.get(fullProp));
                if (sVal != eVal) {
                    result.add(new Transition(fullProp, sVal, eVal, duration, delay, time));
                }
            }
            return;
        }

        if (property.startsWith("border")) {
            if ("border".equals(property)) {
                for (String side : Box.SIDE) {
                    createSideBorderTransition(startStyle, endStyle, result, "border-" + side, duration, delay, time);
                }
            } else {
                createSideBorderTransition(startStyle, endStyle, result, property, duration, delay, time);
            }
            return;
        }

        if ("outline".equals(property) || "outline-width".equals(property) || "outline-color".equals(property) || "outline-offset".equals(property)) {
            createOutlineTransition(startStyle, endStyle, result, property, duration, delay, time);
        }
    }

    private static void createOutlineTransition(Style startStyle, Style endStyle, List<Transition> result, String property, double duration, double delay, long time) {
        Box.Outline start = Box.parseOutline(startStyle);
        Box.Outline end = Box.parseOutline(endStyle);
        if (start == null) start = new Box.Outline();
        if (end == null) end = new Box.Outline();

        if ("outline".equals(property) || "outline-width".equals(property)) {
            if (start.width != end.width) {
                result.add(new Transition("outline-width", start.width, end.width, duration, delay, time));
            }
        }
        if ("outline".equals(property) || "outline-color".equals(property)) {
            if (start.color.getValue() != end.color.getValue()) {
                result.add(new Transition("outline-color", start.color.getValue(), end.color.getValue(), duration, delay, time));
            }
        }
        if ("outline".equals(property) || "outline-offset".equals(property)) {
            if (start.offset != end.offset) {
                result.add(new Transition("outline-offset", start.offset, end.offset, duration, delay, time));
            }
        }
    }

    private static void createSideBorderTransition(Style start, Style end, List<Transition> result, String propName, double duration, double delay, long time) {
        Box.SideBorder s = Box.parseSideBorder(start.get(propName));
        Box.SideBorder e = Box.parseSideBorder(end.get(propName));

        if (s.size() != e.size()) {
            result.add(new Transition(propName + "-width", s.size(), e.size(), duration, delay, time));
        }
        if (!s.color().equals(e.color())) {
            result.add(new Transition(propName + "-color", s.color().getValue(), e.color().getValue(), duration, delay, time));
        }
    }

    public static void readTransition(List<Transition.Change> changeList, Style originStyle) {

        List<Transition.Change> shadowChanges = changeList.stream().filter(c -> c.name().startsWith("box-shadow-")).toList();
        if (!shadowChanges.isEmpty()) {
            Box.Shadow current = Box.parseShadow(originStyle.boxShadow);

            double x = current.x();
            double y = current.y();
            double size = current.size();
            Color color = current.color();

            for (Transition.Change change : shadowChanges) {
                switch (change.name()) {
                    case "box-shadow-x" -> x = change.value();
                    case "box-shadow-y" -> y = change.value();
                    case "box-shadow-size" -> size = change.value();
                    case "box-shadow-color" -> color = new Color(change.value());
                }
            }

            originStyle.boxShadow = new Box.Shadow((int) x, (int) y, (int) size, 0, color).toString();
            changeList.removeAll(shadowChanges);
        }

        List<Transition.Change> radiusChanges = changeList.stream().filter(c -> c.name().startsWith("border-radius-")).toList();
        if (!radiusChanges.isEmpty()) {
            double[] radii = parseFourValues(originStyle.borderRadius);

            for (Transition.Change change : radiusChanges) {
                if (change.name().endsWith("tl")) radii[0] = change.value();
                if (change.name().endsWith("tr")) radii[1] = change.value();
                if (change.name().endsWith("br")) radii[2] = change.value();
                if (change.name().endsWith("bl")) radii[3] = change.value();
            }

            originStyle.borderRadius = (int) radii[0] + "px " + (int) radii[1] + "px " + (int) radii[2] + "px " + (int) radii[3] + "px";
            changeList.removeAll(radiusChanges);
        }

        for (String side : Box.SIDE) {
            String prefix = "border-" + side;
            List<Transition.Change> sideChanges = changeList.stream()
                    .filter(c -> c.name().startsWith(prefix)).toList();

            if (!sideChanges.isEmpty()) {
                Box.SideBorder current = Box.parseSideBorder(originStyle.get(prefix));
                double width = current.size();
                Color color = current.color();

                for (Transition.Change c : sideChanges) {
                    if (c.name().endsWith("-width")) width = c.value();
                    if (c.name().endsWith("-color")) color = new Color(c.value());
                }

                originStyle.update(prefix, new Box.SideBorder((int) width, current.type(), color).toString());
                changeList.removeAll(sideChanges);
            }
        }

        for (String side : Box.SIDE) {
            String marginProp = "margin-" + side;
            List<Transition.Change> mChanges = changeList.stream().filter(c -> c.name().equals(marginProp)).toList();
            if (!mChanges.isEmpty()) {
                originStyle.update(marginProp, (int) mChanges.get(0).value() + "px");
                changeList.removeAll(mChanges);
            }
            String paddingProp = "padding-" + side;
            List<Transition.Change> pChanges = changeList.stream().filter(c -> c.name().equals(paddingProp)).toList();
            if (!pChanges.isEmpty()) {
                originStyle.update(paddingProp, (int) pChanges.get(0).value() + "px");
                changeList.removeAll(pChanges);
            }
        }

        List<Transition.Change> outlineChanges = changeList.stream()
                .filter(c -> c.name().equals("outline-width") || c.name().equals("outline-color") || c.name().equals("outline-offset"))
                .toList();
        if (!outlineChanges.isEmpty()) {
            int width = Box.isStyleValid(originStyle.outlineWidth) ? Size.parse(originStyle.outlineWidth) : 0;
            int offset = Box.isStyleValid(originStyle.outlineOffset) ? Size.parse(originStyle.outlineOffset) : 0;
            Color color = Box.isStyleValid(originStyle.outlineColor) ? new Color(originStyle.outlineColor) : new Color("#000000");
            for (Transition.Change c : outlineChanges) {
                switch (c.name()) {
                    case "outline-width" -> width = (int) c.value();
                    case "outline-color" -> color = new Color(c.value());
                    case "outline-offset" -> offset = (int) c.value();
                }
            }
            originStyle.outlineWidth = width + "px";
            originStyle.outlineColor = color.toHexString();
            originStyle.outlineOffset = offset + "px";
            changeList.removeAll(outlineChanges);
        }
    }

    private static double[] parseFourValues(String val) {
        double[] res = new double[4];
        if (val == null || val.isBlank()) return res;
        String[] parts = val.trim().split("\\s+");
        double v1 = Size.parse(parts[0]);
        double v2 = parts.length > 1 ? Size.parse(parts[1]) : v1;
        double v3 = parts.length > 2 ? Size.parse(parts[2]) : v1;
        double v4 = parts.length > 3 ? Size.parse(parts[3]) : v2;
        return new double[]{v1, v2, v3, v4};
    }
}