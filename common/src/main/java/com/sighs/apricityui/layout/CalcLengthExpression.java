package com.sighs.apricityui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Cached recursive-descent parser for CSS calc() arithmetic. */
final class CalcLengthExpression {
    private static final int CACHE_LIMIT = 1024;
    private static final Node INVALID = resolver -> Value.invalid();
    private static final Map<String, Node> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Node> eldest) {
                    return size() > CACHE_LIMIT;
                }
            }
    );

    private CalcLengthExpression() {
    }

    static Double evaluate(String expression, Function<String, Double> lengthResolver) {
        if (expression == null || expression.isBlank() || lengthResolver == null) return null;
        String source = expression.trim();
        Node node = CACHE.get(source);
        if (node == null) {
            node = new Parser(source).parse();
            CACHE.put(source, node == null ? INVALID : node);
        }
        if (node == INVALID) return null;
        Value result = node.evaluate(lengthResolver);
        return result.dimension() == Dimension.LENGTH && Double.isFinite(result.value())
                ? result.value() : null;
    }

    private enum Dimension {
        NUMBER,
        LENGTH,
        INVALID
    }

    private record Value(double value, Dimension dimension) {
        private static Value number(double value) {
            return new Value(value, Dimension.NUMBER);
        }

        private static Value length(double value) {
            return new Value(value, Dimension.LENGTH);
        }

        private static Value invalid() {
            return new Value(Double.NaN, Dimension.INVALID);
        }

        private boolean isValid() {
            return dimension != Dimension.INVALID && Double.isFinite(value);
        }
    }

    private interface Node {
        Value evaluate(Function<String, Double> lengthResolver);
    }

    private record LengthNode(String token) implements Node {
        @Override
        public Value evaluate(Function<String, Double> lengthResolver) {
            Double value = lengthResolver.apply(token);
            return value == null || !Double.isFinite(value) ? Value.invalid() : Value.length(value);
        }
    }

    private record NumberNode(double value) implements Node {
        @Override
        public Value evaluate(Function<String, Double> lengthResolver) {
            return Value.number(value);
        }
    }

    private record UnaryNode(char operator, Node value) implements Node {
        @Override
        public Value evaluate(Function<String, Double> lengthResolver) {
            Value resolved = value.evaluate(lengthResolver);
            if (!resolved.isValid()) return Value.invalid();
            double result = operator == '-' ? -resolved.value() : resolved.value();
            return finite(result) ? new Value(result, resolved.dimension()) : Value.invalid();
        }
    }

    private record BinaryNode(char operator, Node left, Node right) implements Node {
        @Override
        public Value evaluate(Function<String, Double> lengthResolver) {
            Value lhs = left.evaluate(lengthResolver);
            Value rhs = right.evaluate(lengthResolver);
            if (!lhs.isValid() || !rhs.isValid()) return Value.invalid();
            return switch (operator) {
                case '+', '-' -> lhs.dimension() != rhs.dimension() ? Value.invalid()
                        : result(operator == '+' ? lhs.value() + rhs.value() : lhs.value() - rhs.value(), lhs.dimension());
                case '*' -> multiply(lhs, rhs);
                case '/' -> rhs.dimension() != Dimension.NUMBER || rhs.value() == 0.0
                        ? Value.invalid() : result(lhs.value() / rhs.value(), lhs.dimension());
                default -> Value.invalid();
            };
        }

        private static Value multiply(Value lhs, Value rhs) {
            if (lhs.dimension() == Dimension.NUMBER && rhs.dimension() == Dimension.NUMBER) {
                return result(lhs.value() * rhs.value(), Dimension.NUMBER);
            }
            if (lhs.dimension() == Dimension.NUMBER || rhs.dimension() == Dimension.NUMBER) {
                return result(lhs.value() * rhs.value(), Dimension.LENGTH);
            }
            return Value.invalid();
        }
    }

    private record FunctionNode(String name, List<Node> arguments) implements Node {
        @Override
        public Value evaluate(Function<String, Double> lengthResolver) {
            if (arguments.isEmpty()) return Value.invalid();
            double[] values = new double[arguments.size()];
            Dimension dimension = null;
            for (int index = 0; index < arguments.size(); index++) {
                Value argument = arguments.get(index).evaluate(lengthResolver);
                if (!argument.isValid() || (dimension != null && dimension != argument.dimension())) {
                    return Value.invalid();
                }
                dimension = argument.dimension();
                values[index] = argument.value();
            }
            return switch (name) {
                case "min" -> result(minimum(values), dimension);
                case "max" -> result(maximum(values), dimension);
                case "clamp" -> values.length == 3
                        ? result(Math.max(values[0], Math.min(values[1], values[2])), dimension) : Value.invalid();
                default -> Value.invalid();
            };
        }

        private static double minimum(double[] values) {
            double result = values[0];
            for (int index = 1; index < values.length; index++) result = Math.min(result, values[index]);
            return result;
        }

        private static double maximum(double[] values) {
            double result = values[0];
            for (int index = 1; index < values.length; index++) result = Math.max(result, values[index]);
            return result;
        }
    }

    private static Value result(double value, Dimension dimension) {
        return finite(value) ? new Value(value, dimension) : Value.invalid();
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Node parse() {
            Node result = parseExpression();
            skipWhitespace();
            return result != null && index == source.length() ? result : null;
        }

        private Node parseExpression() {
            Node left = parseTerm();
            if (left == null) return null;
            while (true) {
                skipWhitespace();
                if (!peek('+') && !peek('-')) return left;
                char operator = source.charAt(index++);
                Node right = parseTerm();
                if (right == null) return null;
                left = new BinaryNode(operator, left, right);
            }
        }

        private Node parseTerm() {
            Node left = parseFactor();
            if (left == null) return null;
            while (true) {
                skipWhitespace();
                if (!peek('*') && !peek('/')) return left;
                char operator = source.charAt(index++);
                Node right = parseFactor();
                if (right == null) return null;
                left = new BinaryNode(operator, left, right);
            }
        }

        private Node parseFactor() {
            skipWhitespace();
            if (index >= source.length()) return null;
            if (peek('+') || peek('-')) {
                char operator = source.charAt(index++);
                Node value = parseFactor();
                return value == null ? null : new UnaryNode(operator, value);
            }
            if (peek('(')) {
                index++;
                Node nested = parseExpression();
                return consume(')') ? nested : null;
            }
            if (Character.isLetter(source.charAt(index))) {
                int nameStart = index;
                while (index < source.length() && Character.isLetter(source.charAt(index))) index++;
                String name = source.substring(nameStart, index).toLowerCase(java.util.Locale.ROOT);
                skipWhitespace();
                if (!consume('(')) return null;
                if ("calc".equals(name)) {
                    Node nested = parseExpression();
                    return consume(')') ? nested : null;
                }
                List<Node> arguments = new ArrayList<>();
                while (true) {
                    Node argument = parseExpression();
                    if (argument == null) return null;
                    arguments.add(argument);
                    skipWhitespace();
                    if (consume(')')) break;
                    if (!consume(',')) return null;
                }
                return switch (name) {
                    case "min", "max", "clamp" -> new FunctionNode(name, List.copyOf(arguments));
                    default -> null;
                };
            }
            int start = index;
            while (index < source.length()) {
                char character = source.charAt(index);
                if (Character.isWhitespace(character) || character == '+' || character == '-'
                        || character == '*' || character == '/' || character == '(' || character == ')'
                        || character == ',') {
                    break;
                }
                index++;
            }
            if (index == start) return null;
            String token = source.substring(start, index);
            Double number = parseBareNumber(token);
            if (number != null && Math.abs(number) > 1.0e-12d) return new NumberNode(number);
            return new LengthNode(token);
        }

        private static Double parseBareNumber(String token) {
            int length = token.length();
            int position = 0;
            if (position < length && (token.charAt(position) == '+' || token.charAt(position) == '-')) position++;
            boolean digit = false;
            boolean dot = false;
            while (position < length) {
                char character = token.charAt(position++);
                if (character >= '0' && character <= '9') {
                    digit = true;
                } else if (character == '.' && !dot) {
                    dot = true;
                } else {
                    return null;
                }
            }
            if (!digit) return null;
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (!peek(expected)) return false;
            index++;
            return true;
        }

        private boolean peek(char expected) {
            return index < source.length() && source.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }
    }
}
