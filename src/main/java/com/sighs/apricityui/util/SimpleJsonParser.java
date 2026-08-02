package com.sighs.apricityui.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sighs.apricityui.init.Window;

/**
 * 极简 JSON 解析器（fetch 响应体专用）。从 Window 拆出，纯函数、无状态依赖。
 * 只支持标准 JSON 值：对象/数组/字符串/数字/true/false/null。
 */
public final class SimpleJsonParser {
    private final String source;
    private int index = 0;

    public SimpleJsonParser(String source) {
        this.source = source == null ? "" : source;
    }

    public Object parse() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (index != source.length()) {
            throw new IllegalArgumentException("Unexpected trailing JSON content at index " + index);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (index >= source.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        char c = source.charAt(index);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (peek('}')) {
            index++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        ArrayList<Object> result = new ArrayList<>();
        index++;
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            result.add(parseValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < source.length()) {
            char c = source.charAt(index++);
            if (c == '"') {
                return builder.toString();
            }
            if (c != '\\') {
                builder.append(c);
                continue;
            }
            if (index >= source.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON string escape");
            }
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 > source.length()) {
                        throw new IllegalArgumentException("Invalid unicode escape in JSON string");
                    }
                    String hex = source.substring(index, index + 4);
                    builder.append((char) Integer.parseInt(hex, 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Unsupported JSON escape: \\" + escaped);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private Object parseLiteral(String literal, Object value) {
        if (!source.startsWith(literal, index)) {
            throw new IllegalArgumentException("Invalid JSON literal at index " + index);
        }
        index += literal.length();
        return value;
    }

    private Double parseNumber() {
        int start = index;
        if (peek('-')) index++;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        if (peek('.')) {
            index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        if (peek('e') || peek('E')) {
            index++;
            if (peek('+') || peek('-')) index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        String token = source.substring(start, index);
        if (token.isEmpty() || "-".equals(token)) {
            throw new IllegalArgumentException("Invalid JSON number at index " + start);
        }
        return Double.parseDouble(token);
    }

    private void skipWhitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (!peek(expected)) {
            throw new IllegalArgumentException("Expected '" + expected + "' at index " + index);
        }
        index++;
    }

    private boolean peek(char expected) {
        return index < source.length() && source.charAt(index) == expected;
    }
}
