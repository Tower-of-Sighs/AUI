package com.sighs.apricityui.canvas;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

final class CanvasSvgPathParser {
    private final String source;
    private int index = 0;

    private CanvasSvgPathParser(String source) {
        this.source = source == null ? "" : source;
    }

    static void parseInto(String source, CanvasPath2D path) {
        if (path == null || source == null || source.isBlank()) return;
        new CanvasSvgPathParser(source).parse(path);
    }

    private void parse(CanvasPath2D path) {
        char command = ' ';
        double currentX = 0;
        double currentY = 0;
        double subPathX = 0;
        double subPathY = 0;
        double lastCpx = 0;
        double lastCpy = 0;
        double lastQpx = 0;
        double lastQpy = 0;
        char previousCommand = ' ';

        while (true) {
            skipSeparators();
            if (index >= source.length()) return;
            char ch = source.charAt(index);
            if (isCommand(ch)) {
                command = ch;
                index++;
            } else if (command == ' ') {
                return;
            }

            switch (command) {
                case 'M', 'm' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 1 < values.size(); i += 2) {
                        double x = values.get(i);
                        double y = values.get(i + 1);
                        if (command == 'm') {
                            x += currentX;
                            y += currentY;
                        }
                        if (i == 0) path.moveTo(x, y);
                        else path.lineTo(x, y);
                        currentX = subPathX = x;
                        currentY = subPathY = y;
                    }
                    previousCommand = command;
                }
                case 'L', 'l' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 1 < values.size(); i += 2) {
                        double x = values.get(i);
                        double y = values.get(i + 1);
                        if (command == 'l') {
                            x += currentX;
                            y += currentY;
                        }
                        path.lineTo(x, y);
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'H', 'h' -> {
                    List<Double> values = readNumberSequence();
                    for (double value : values) {
                        double x = command == 'h' ? currentX + value : value;
                        path.lineTo(x, currentY);
                        currentX = x;
                    }
                    previousCommand = command;
                }
                case 'V', 'v' -> {
                    List<Double> values = readNumberSequence();
                    for (double value : values) {
                        double y = command == 'v' ? currentY + value : value;
                        path.lineTo(currentX, y);
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'C', 'c' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 5 < values.size(); i += 6) {
                        double cp1x = values.get(i);
                        double cp1y = values.get(i + 1);
                        double cp2x = values.get(i + 2);
                        double cp2y = values.get(i + 3);
                        double x = values.get(i + 4);
                        double y = values.get(i + 5);
                        if (command == 'c') {
                            cp1x += currentX;
                            cp1y += currentY;
                            cp2x += currentX;
                            cp2y += currentY;
                            x += currentX;
                            y += currentY;
                        }
                        path.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y);
                        lastCpx = cp2x;
                        lastCpy = cp2y;
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'S', 's' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 3 < values.size(); i += 4) {
                        double cp1x;
                        double cp1y;
                        if (previousCommand == 'C' || previousCommand == 'c' || previousCommand == 'S' || previousCommand == 's') {
                            cp1x = 2 * currentX - lastCpx;
                            cp1y = 2 * currentY - lastCpy;
                        } else {
                            cp1x = currentX;
                            cp1y = currentY;
                        }
                        double cp2x = values.get(i);
                        double cp2y = values.get(i + 1);
                        double x = values.get(i + 2);
                        double y = values.get(i + 3);
                        if (command == 's') {
                            cp2x += currentX;
                            cp2y += currentY;
                            x += currentX;
                            y += currentY;
                        }
                        path.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y);
                        lastCpx = cp2x;
                        lastCpy = cp2y;
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'Q', 'q' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 3 < values.size(); i += 4) {
                        double cpx = values.get(i);
                        double cpy = values.get(i + 1);
                        double x = values.get(i + 2);
                        double y = values.get(i + 3);
                        if (command == 'q') {
                            cpx += currentX;
                            cpy += currentY;
                            x += currentX;
                            y += currentY;
                        }
                        path.quadraticCurveTo(cpx, cpy, x, y);
                        lastQpx = cpx;
                        lastQpy = cpy;
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'T', 't' -> {
                    List<Double> values = readNumberSequence();
                    for (int i = 0; i + 1 < values.size(); i += 2) {
                        double cpx;
                        double cpy;
                        if (previousCommand == 'Q' || previousCommand == 'q' || previousCommand == 'T' || previousCommand == 't') {
                            cpx = 2 * currentX - lastQpx;
                            cpy = 2 * currentY - lastQpy;
                        } else {
                            cpx = currentX;
                            cpy = currentY;
                        }
                        double x = values.get(i);
                        double y = values.get(i + 1);
                        if (command == 't') {
                            x += currentX;
                            y += currentY;
                        }
                        path.quadraticCurveTo(cpx, cpy, x, y);
                        lastQpx = cpx;
                        lastQpy = cpy;
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'A', 'a' -> {
                    while (true) {
                        skipSeparators();
                        if (index >= source.length() || isCommand(source.charAt(index))) break;
                        Double rxValue = readNumber();
                        Double ryValue = readNumber();
                        Double angleValue = readNumber();
                        Integer largeArcFlag = readArcFlag();
                        Integer sweepFlag = readArcFlag();
                        Double xValue = readNumber();
                        Double yValue = readNumber();
                        if (rxValue == null || ryValue == null || angleValue == null
                                || largeArcFlag == null || sweepFlag == null || xValue == null || yValue == null) {
                            break;
                        }
                        double rx = rxValue;
                        double ry = ryValue;
                        double angle = angleValue;
                        boolean largeArc = largeArcFlag != 0;
                        boolean sweep = sweepFlag != 0;
                        double x = xValue;
                        double y = yValue;
                        if (command == 'a') {
                            x += currentX;
                            y += currentY;
                        }
                        appendSvgArc(path, currentX, currentY, rx, ry, angle, largeArc, sweep, x, y);
                        currentX = x;
                        currentY = y;
                    }
                    previousCommand = command;
                }
                case 'Z', 'z' -> {
                    path.closePath();
                    currentX = subPathX;
                    currentY = subPathY;
                    previousCommand = command;
                }
                default -> index++;
            }
        }
    }

    private List<Double> readNumberSequence() {
        ArrayList<Double> values = new ArrayList<>();
        while (true) {
            skipSeparators();
            if (index >= source.length()) break;
            char ch = source.charAt(index);
            if (isCommand(ch)) break;
            Double value = readNumber();
            if (value == null) break;
            values.add(value);
        }
        return values;
    }

    private Double readNumber() {
        skipSeparators();
        if (index >= source.length()) return null;
        int start = index;
        boolean hasDigits = false;
        if (source.charAt(index) == '+' || source.charAt(index) == '-') index++;
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
            hasDigits = true;
        }
        if (index < source.length() && source.charAt(index) == '.') {
            index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
                hasDigits = true;
            }
        }
        if (!hasDigits) return null;
        if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            int expStart = index++;
            if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
            boolean expDigits = false;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
                expDigits = true;
            }
            if (!expDigits) index = expStart;
        }
        return Double.parseDouble(source.substring(start, index));
    }

    private Integer readArcFlag() {
        skipSeparators();
        if (index >= source.length()) return null;
        char ch = source.charAt(index);
        if (ch == '0' || ch == '1') {
            index++;
            return ch - '0';
        }
        Double value = readNumber();
        return value == null ? null : (value == 0 ? 0 : 1);
    }

    private void skipSeparators() {
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (Character.isWhitespace(ch) || ch == ',') index++;
            else break;
        }
    }

    private static boolean isCommand(char ch) {
        return "MmLlHhVvCcSsQqTtAaZz".indexOf(ch) >= 0;
    }

    private static void appendSvgArc(CanvasPath2D path, double x1, double y1, double rx, double ry, double angleDeg,
                                     boolean largeArc, boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0 || (x1 == x2 && y1 == y2)) {
            path.lineTo(x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double angle = Math.toRadians(angleDeg % 360.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dx2 = (x1 - x2) / 2.0;
        double dy2 = (y1 - y2) / 2.0;
        double x1p = cos * dx2 + sin * dy2;
        double y1p = -sin * dx2 + cos * dy2;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }

        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double x1p2 = x1p * x1p;
        double y1p2 = y1p * y1p;
        double numerator = rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2;
        double denominator = rx2 * y1p2 + ry2 * x1p2;
        double factor = denominator == 0 ? 0 : Math.sqrt(Math.max(0, numerator / denominator));
        if (largeArc == sweep) factor = -factor;

        double cxp = factor * (rx * y1p / ry);
        double cyp = factor * (-ry * x1p / rx);
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0;
        double cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;

        double theta1 = vectorAngle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double deltaTheta = vectorAngle(
                (x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry
        );
        if (!sweep && deltaTheta > 0) deltaTheta -= Math.PI * 2;
        else if (sweep && deltaTheta < 0) deltaTheta += Math.PI * 2;

        int segments = Math.max(1, (int) Math.ceil(Math.abs(deltaTheta) / (Math.PI / 2.0)));
        double step = deltaTheta / segments;
        for (int i = 0; i < segments; i++) {
            double start = theta1 + i * step;
            double end = start + step;
            appendArcSegment(path, cx, cy, rx, ry, angle, start, end);
        }
    }

    private static void appendArcSegment(CanvasPath2D path, double cx, double cy, double rx, double ry,
                                         double phi, double start, double end) {
        double delta = end - start;
        double t = Math.tan(delta / 4.0);
        double alpha = Math.sin(delta) * (Math.sqrt(4 + 3 * t * t) - 1) / 3.0;

        Point2D.Double p0 = mapEllipsePoint(cx, cy, rx, ry, phi, start);
        Point2D.Double p3 = mapEllipsePoint(cx, cy, rx, ry, phi, end);
        Point2D.Double d0 = mapEllipseDerivative(rx, ry, phi, start);
        Point2D.Double d3 = mapEllipseDerivative(rx, ry, phi, end);

        double cp1x = p0.x + alpha * d0.x;
        double cp1y = p0.y + alpha * d0.y;
        double cp2x = p3.x - alpha * d3.x;
        double cp2y = p3.y - alpha * d3.y;

        path.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p3.x, p3.y);
    }

    private static Point2D.Double mapEllipsePoint(double cx, double cy, double rx, double ry, double phi, double theta) {
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        return new Point2D.Double(
                cx + rx * cosPhi * cosTheta - ry * sinPhi * sinTheta,
                cy + rx * sinPhi * cosTheta + ry * cosPhi * sinTheta
        );
    }

    private static Point2D.Double mapEllipseDerivative(double rx, double ry, double phi, double theta) {
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        return new Point2D.Double(
                -rx * cosPhi * sinTheta - ry * sinPhi * cosTheta,
                -rx * sinPhi * sinTheta + ry * cosPhi * cosTheta
        );
    }

    private static double vectorAngle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (len == 0) return 0;
        double angle = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        return (ux * vy - uy * vx) < 0 ? -angle : angle;
    }
}
