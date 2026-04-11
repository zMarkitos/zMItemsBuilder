package dev.zm.itemsbuilder.builder.model;

import dev.zm.itemsbuilder.util.MathExpression;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Numeric rule that can be a fixed value, an expression, or a level-scaled formula.
 *
 * Used for attribute amounts and other non-integer config fields.
 */
public final class NumberRule {

    private final Double fixedValue;
    private final String expression;
    private final Double base;
    private final Double perLevel;
    private final Integer every;
    private final Double bonus;
    private final Double min;
    private final Double max;

    private NumberRule(
        Double fixedValue,
        String expression,
        Double base,
        Double perLevel,
        Integer every,
        Double bonus,
        Double min,
        Double max
    ) {
        this.fixedValue = fixedValue;
        this.expression = expression;
        this.base = base;
        this.perLevel = perLevel;
        this.every = every;
        this.bonus = bonus;
        this.min = min;
        this.max = max;
    }

    public static NumberRule fixed(double value) {
        return new NumberRule(value, null, null, null, null, null, null, null);
    }

    public static NumberRule expression(String expression) {
        return new NumberRule(null, expression, null, null, null, null, null, null);
    }

    public static NumberRule progressive(double base, double perLevel, Double min, Double max) {
        return new NumberRule(null, null, base, perLevel, null, null, min, max);
    }

    public static NumberRule stepped(double base, int every, double bonus, Double min, Double max) {
        return new NumberRule(null, null, base, null, every, bonus, min, max);
    }

    public static NumberRule from(Object raw, double fallback) {
        if (raw == null) {
            return fixed(fallback);
        }
        if (raw instanceof Number number) {
            return fixed(number.doubleValue());
        }
        if (raw instanceof ConfigurationSection section) {
            return fromSection(section, fallback);
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return fixed(fallback);
        }
        if (isNumber(value)) {
            return fixed(Double.parseDouble(value));
        }
        return expression(value);
    }

    public static NumberRule fromSection(ConfigurationSection section, double fallback) {
        if (section == null) {
            return fixed(fallback);
        }

        if (section.contains("value")) {
            return from(section.get("value"), fallback);
        }
        if (section.contains("formula")) {
            return expression(String.valueOf(section.get("formula")));
        }
        if (section.contains("expression")) {
            return expression(String.valueOf(section.get("expression")));
        }

        Double min = readDouble(section, "min");
        Double max = readDouble(section, "max");
        Double base = readDouble(section, "base");

        if (section.contains("per-level")) {
            double perLevel = section.getDouble("per-level", 0.0D);
            return progressive(base != null ? base : fallback, perLevel, min, max);
        }
        if (section.contains("every") || section.contains("bonus")) {
            int every = Math.max(1, section.getInt("every", 1));
            double bonus = section.getDouble("bonus", 0.0D);
            return stepped(base != null ? base : fallback, every, bonus, min, max);
        }
        if (base != null) {
            return fixed(base);
        }
        return fixed(fallback);
    }

    public double resolve(int level, double fallback) {
        double value;
        if (expression != null) {
            value = MathExpression.evaluateDouble(expression, level, fallback);
        } else if (perLevel != null) {
            double start = base != null ? base : fallback;
            value = start + Math.max(0, level - 1) * perLevel;
        } else if (every != null) {
            double start = base != null ? base : fallback;
            int interval = Math.max(1, every);
            double increment = bonus != null ? bonus : 0.0D;
            value = start + (Math.floor(Math.max(0, level - 1) / (double) interval) * increment);
        } else if (fixedValue != null) {
            value = fixedValue;
        } else if (base != null) {
            value = base;
        } else {
            value = fallback;
        }

        if (min != null) {
            value = Math.max(min, value);
        }
        if (max != null) {
            value = Math.min(max, value);
        }
        return value;
    }

    private static Double readDouble(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        Object raw = section.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return null;
        }
        if (isNumber(value)) {
            return Double.parseDouble(value);
        }
        return null;
    }

    private static boolean isNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("nan".equals(value) || "infinity".equals(value) || "-infinity".equals(value)) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        boolean dotSeen = false;
        if (start == value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                if (dotSeen) {
                    return false;
                }
                dotSeen = true;
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}

