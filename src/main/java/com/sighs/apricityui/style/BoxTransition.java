package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

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

        List<Transition.Change> shadowChanges = changeList.stream().filter(c -> c.name().startsWith("box-shadow-")).collect(Collectors.toList());
        if (!shadowChanges.isEmpty()) {
            Box.Shadow current = Box.parseShadow(originStyle.boxShadow);

            double x = current.x();
            double y = current.y();
            double size = current.size();
            Color color = current.color();

            for (Transition.Change change : shadowChanges) {
                switch (change.name()) {
                    case "box-shadow-x":
                        x = change.value();
                        break;
                    case "box-shadow-y":
                        y = change.value();
                        break;
                    case "box-shadow-size":
                        size = change.value();
                        break;
                    case "box-shadow-color":
                        color = new Color(change.value());
                        break;
                }
            }

            originStyle.boxShadow = new Box.Shadow((int) x, (int) y, (int) size, color).toString();
            changeList.removeAll(shadowChanges);
        }

        List<Transition.Change> radiusChanges = changeList.stream().filter(c -> c.name().startsWith("border-radius-")).collect(Collectors.toList());
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
                    .filter(c -> c.name().startsWith(prefix)).collect(Collectors.toList());

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
    }

    private static double[] parseFourValues(String val) {
        double[] res = new double[4];
        if (StringUtils.isNullOrEmptyEx(val)) return res;
        String[] parts = val.trim().split("\\s+");
        double v1 = Size.parse(parts[0]);
        double v2 = parts.length > 1 ? Size.parse(parts[1]) : v1;
        double v3 = parts.length > 2 ? Size.parse(parts[2]) : v1;
        double v4 = parts.length > 3 ? Size.parse(parts[3]) : v2;
        return new double[]{v1, v2, v3, v4};
    }
}