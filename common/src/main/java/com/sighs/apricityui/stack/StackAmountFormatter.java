package com.sighs.apricityui.stack;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class StackAmountFormatter {
    private static final int SLOT_WIDTH = 4;
    private static final char[] SUFFIXES = "KMGTPE".toCharArray();

    private StackAmountFormatter() {
    }

    static String format(long amount, long amountPerUnit) {
        return amountPerUnit > 1L
                ? format(amount / (double) amountPerUnit, SLOT_WIDTH)
                : format(amount, SLOT_WIDTH);
    }

    private static String format(long number, int width) {
        String full = Long.toString(number);
        if (number < 1000L && full.length() <= width) return full;

        long base = number;
        long last;
        int exponent = -1;
        int size;
        do {
            last = base;
            base /= 1000L;
            exponent++;
            size = Long.toString(base).length() + 1;
        } while (size > width && exponent + 1 < SUFFIXES.length);

        String suffix = String.valueOf(SUFFIXES[exponent]);
        String compact = base + suffix;
        if (last % 1000L == 0L) return compact;
        String precise = decimalFormat().format(last / 1000.0D) + suffix;
        return precise.length() <= width ? precise : compact;
    }

    private static String format(double number, int width) {
        int integerDigits = (int) Math.max(0.0D, Math.log10(number) + 1.0D);
        int fractionalDigits = width - integerDigits - 1;
        double minimumFraction = Math.pow(10.0D, -fractionalDigits);
        double fractional = number - Math.floor(number);

        if (fractional < 1.0E-9D || integerDigits > width - 1) {
            return format((long) number, width);
        }
        if (fractional + 1.0E-9D < minimumFraction && integerDigits - 1 <= width) {
            return "~" + format((long) number, width - 1);
        }

        DecimalFormat format = decimalFormat();
        format.setMaximumFractionDigits(fractionalDigits);
        return format.format(number);
    }

    private static DecimalFormat decimalFormat() {
        DecimalFormat format = new DecimalFormat(".#;0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setDecimalSeparatorAlwaysShown(false);
        format.setRoundingMode(RoundingMode.DOWN);
        return format;
    }
}
