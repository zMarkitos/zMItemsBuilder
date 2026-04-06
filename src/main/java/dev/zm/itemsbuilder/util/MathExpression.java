package dev.zm.itemsbuilder.util;

import java.util.Locale;

public final class MathExpression {

    private final String input;
    private final int level;
    private int index;

    private MathExpression(String input, int level) {
        this.input = input;
        this.level = level;
    }

    public static int evaluateInt(String expression, int level, int fallback) {
        if (expression == null || expression.isBlank()) {
            return fallback;
        }
        try {
            double result = new MathExpression(expression, level).parseExpression();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return fallback;
            }
            return Math.max(1, (int) Math.round(result));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                value += parseTerm();
            } else if (match('-')) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipWhitespace();
            if (match('*')) {
                value *= parseFactor();
            } else if (match('/')) {
                value /= parseFactor();
            } else if (match('%')) {
                value %= parseFactor();
            } else {
                return value;
            }
        }
    }

    private double parseFactor() {
        skipWhitespace();
        if (match('+')) {
            return parseFactor();
        }
        if (match('-')) {
            return -parseFactor();
        }
        if (match('(')) {
            double value = parseExpression();
            expect(')');
            return value;
        }
        if (isAlpha(peek())) {
            String identifier = parseIdentifier();
            skipWhitespace();
            if (match('(')) {
                return parseFunction(identifier);
            }
            return switch (identifier.toLowerCase(Locale.ROOT)) {
                case "level" -> level;
                default -> throw new IllegalArgumentException("Unknown variable: " + identifier);
            };
        }
        return parseNumber();
    }

    private double parseFunction(String name) {
        double first = parseExpression();
        skipWhitespace();
        if (match(',')) {
            double second = parseExpression();
            expect(')');
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "min" -> Math.min(first, second);
                case "max" -> Math.max(first, second);
                default -> throw new IllegalArgumentException("Unknown function: " + name);
            };
        }

        expect(')');
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "floor" -> Math.floor(first);
            case "ceil" -> Math.ceil(first);
            case "round" -> Math.round(first);
            default -> throw new IllegalArgumentException("Unknown function: " + name);
        };
    }

    private double parseNumber() {
        skipWhitespace();
        int start = index;
        while (index < input.length()) {
            char c = input.charAt(index);
            if ((c >= '0' && c <= '9') || c == '.') {
                index++;
                continue;
            }
            break;
        }
        if (start == index) {
            throw new IllegalArgumentException("Expected number at position " + index);
        }
        return Double.parseDouble(input.substring(start, index));
    }

    private String parseIdentifier() {
        int start = index;
        while (index < input.length() && (Character.isLetter(input.charAt(index)) || input.charAt(index) == '_')) {
            index++;
        }
        return input.substring(start, index);
    }

    private boolean match(char expected) {
        if (index < input.length() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!match(expected)) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
        }
    }

    private char peek() {
        return index < input.length() ? input.charAt(index) : '\0';
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }
}
