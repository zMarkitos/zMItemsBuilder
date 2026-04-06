package dev.zm.itemsbuilder.builder.model;

import dev.zm.itemsbuilder.util.MathExpression;
import org.bukkit.configuration.ConfigurationSection;

public final class EnchantLevelRule {

    private final Integer fixedValue;
    private final String expression;
    private final Integer base;
    private final Integer perLevel;
    private final Integer every;
    private final Integer bonus;
    private final Integer min;
    private final Integer max;

    private EnchantLevelRule(
        Integer fixedValue,
        String expression,
        Integer base,
        Integer perLevel,
        Integer every,
        Integer bonus,
        Integer min,
        Integer max
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

    public static EnchantLevelRule fixed(int value) {
        return new EnchantLevelRule(value, null, null, null, null, null, null, null);
    }

    public static EnchantLevelRule expression(String expression) {
        return new EnchantLevelRule(null, expression, null, null, null, null, null, null);
    }

    public static EnchantLevelRule progressive(int base, int perLevel, Integer min, Integer max) {
        return new EnchantLevelRule(null, null, base, perLevel, null, null, min, max);
    }

    public static EnchantLevelRule stepped(int base, int every, int bonus, Integer min, Integer max) {
        return new EnchantLevelRule(null, null, base, null, every, bonus, min, max);
    }

    public static EnchantLevelRule from(Object raw) {
        if (raw == null) {
            return fixed(1);
        }
        if (raw instanceof Number number) {
            return fixed(number.intValue());
        }
        if (raw instanceof ConfigurationSection section) {
            return fromSection(section);
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return fixed(1);
        }
        if (isInteger(value)) {
            return fixed(Integer.parseInt(value));
        }
        return expression(value);
    }

    public static EnchantLevelRule fromSection(ConfigurationSection section) {
        if (section == null) {
            return fixed(1);
        }

        if (section.contains("value")) {
            return from(section.get("value"));
        }
        if (section.contains("formula")) {
            return expression(String.valueOf(section.get("formula")));
        }
        if (section.contains("expression")) {
            return expression(String.valueOf(section.get("expression")));
        }

        Integer min = readInteger(section, "min");
        Integer max = readInteger(section, "max");
        Integer base = readInteger(section, "base");

        if (section.contains("per-level")) {
            int perLevel = Math.max(0, section.getInt("per-level", 0));
            return progressive(base != null ? base : 1, perLevel, min, max);
        }
        if (section.contains("every") || section.contains("bonus")) {
            int every = Math.max(1, section.getInt("every", 1));
            int bonus = Math.max(0, section.getInt("bonus", 1));
            return stepped(base != null ? base : 1, every, bonus, min, max);
        }
        if (base != null) {
            return fixed(base);
        }
        return fixed(1);
    }

    public int resolve(int level) {
        int value;
        if (expression != null) {
            value = MathExpression.evaluateInt(expression, level, 1);
        } else if (perLevel != null) {
            int start = base != null ? base : 1;
            value = start + Math.max(0, level - 1) * Math.max(0, perLevel);
        } else if (every != null) {
            int start = base != null ? base : 1;
            int interval = Math.max(1, every);
            int increment = bonus != null ? Math.max(0, bonus) : 1;
            value = start + (Math.max(0, level) / interval) * increment;
        } else if (fixedValue != null) {
            value = fixedValue;
        } else if (base != null) {
            value = base;
        } else {
            value = 1;
        }

        if (min != null) {
            value = Math.max(min, value);
        }
        if (max != null) {
            value = Math.min(max, value);
        }
        return Math.max(1, value);
    }

    private static Integer readInteger(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        Object raw = section.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return null;
        }
        if (isInteger(value)) {
            return Integer.parseInt(value);
        }
        return null;
    }

    private static boolean isInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        int start = raw.charAt(0) == '-' ? 1 : 0;
        if (start == raw.length()) {
            return false;
        }
        for (int i = start; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
