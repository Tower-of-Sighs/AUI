package com.sighs.apricityui.style;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.Locale;
import com.sighs.apricityui.init.Element;

/**
 * 表单控件的类型/约束解析纯函数（input type 归一化、数值/日期/时间解析、
 * 表单校验所需的静态判断）。从 Element 拆出，不持有状态。
 */
public final class ConstraintText {
    private ConstraintText() {
    }

    /** 把纯整数值的 "N.0" 归一化为 "N"，避免 count/page 显示不友好。 */
    public static String normalizeNumericText(String value) {
        if (value == null || value.isEmpty()) return "";
        int len = value.length();
        int i = 0;
        if (value.charAt(0) == '-') {
            if (len == 1) return value;
            i = 1;
        }
        boolean allDigits = true;
        for (int j = i; j < len - 2; j++) {
            char c = value.charAt(j);
            if (c < '0' || c > '9') {
                allDigits = false;
                break;
            }
        }
        if (allDigits && len >= i + 3 && value.charAt(len - 2) == '.' && value.charAt(len - 1) == '0') {
            return value.substring(0, len - 2);
        }
        return value;
    }

    public static String fileName(String value) {
        if (value == null || value.isEmpty()) return "";
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash < 0 ? value : value.substring(slash + 1);
    }

    public static String normalizedInputType(Element control) {
        String type = control.getType();
        return type == null || type.isBlank() ? "text" : type.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isSubmitButton(Element element) {
        if (element == null) return false;
        if ("BUTTON".equalsIgnoreCase(element.tagName)) {
            String type = element.getAttribute("type");
            return type == null || type.isBlank() || "submit".equalsIgnoreCase(type)
                    || "image".equalsIgnoreCase(type);
        }
        if (!"INPUT".equalsIgnoreCase(element.tagName)) return false;
        String type = normalizedInputType(element);
        return "submit".equals(type) || "image".equals(type);
    }

    /** Keeps interactive numeric values decimal-safe without preserving redundant trailing zeroes. */
    public static String serializeNumberValue(double number) {
        if (number == 0d) return "0";
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    public static boolean isSimpleEmail(String value) {
        return value != null && value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    public static boolean isNumericType(String type) {
        return switch (type) {
            case "number", "range", "date", "month", "week", "time", "datetime-local" -> true;
            default -> false;
        };
    }

    public static Double parseConstraintNumber(String type, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return switch (type) {
                case "number", "range" -> Double.parseDouble(raw);
                case "date" -> (double) LocalDate.parse(raw).toEpochDay();
                case "month" -> {
                    YearMonth month = YearMonth.parse(raw);
                    yield (double) (month.getYear() * 12L + month.getMonthValue() - 1);
                }
                case "time" -> (double) parseTimeSeconds(raw);
                case "datetime-local" -> LocalDateTime.parse(raw)
                        .toInstant(java.time.ZoneOffset.UTC).toEpochMilli() / 1_000d;
                case "week" -> {
                    if (!raw.matches("\\d{4}-W\\d{2}")) yield null;
                    String[] parts = raw.split("-W");
                    int year = Integer.parseInt(parts[0]);
                    int week = Integer.parseInt(parts[1]);
                    yield (double) LocalDate.of(year, 1, 4)
                            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                            .with(DayOfWeek.MONDAY).toEpochDay();
                }
                default -> null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static double parseTimeSeconds(String raw) {
        LocalTime time = LocalTime.parse(raw);
        return time.toNanoOfDay() / 1_000_000_000d;
    }

    public static String formatTime(double seconds) {
        long rounded = Math.max(0L, Math.round(seconds));
        return LocalTime.ofSecondOfDay(rounded % 86400L).toString();
    }

    public static double defaultStepBase(String type) {
        return switch (type) {
            case "date" -> 0d;
            case "month" -> 0d;
            case "datetime-local", "time" -> 0d;
            case "week" -> LocalDate.of(1969, 12, 29).toEpochDay();
            default -> 0d;
        };
    }

    public static String defaultStepText(String type) {
        return switch (type) {
            case "time", "datetime-local" -> "60";
            case "week" -> "7";
            default -> "1";
        };
    }

    public static int parseNonNegativeInt(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            int value = Integer.parseInt(raw);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
