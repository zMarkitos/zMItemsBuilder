package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.config.SecondaryColorMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Pattern HEX_COLOR = Pattern.compile(
            "(?:&#|<#)([A-Fa-f0-9]{6})>?|(?i)&x(?:&([0-9A-Fa-f])){6}");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)&x&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])");
    private static final Pattern COLOR_TOKEN = Pattern.compile(
            "(?:&#|<#)[A-Fa-f0-9]{6}>?|(?i)&x(?:&[0-9A-Fa-f]){6}|&[0-9a-fk-orA-FK-OR]|<[^>]+>");

    private ColorUtils() {
    }

    public static Optional<String> extractHex(String input) {
        if (input == null) {
            return Optional.empty();
        }
        Matcher matcher = HEX_COLOR.matcher(input);
        if (matcher.find()) {
            String direct = matcher.group(1);
            if (direct != null) {
                return Optional.of(direct.toUpperCase(Locale.ROOT));
            }
            return Optional.of(legacyHexToRgb(input, matcher.start()).toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    public static List<String> extractHexColors(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> colors = new LinkedHashSet<>();
        Matcher legacyMatcher = LEGACY_HEX.matcher(input);
        while (legacyMatcher.find()) {
            colors.add((legacyMatcher.group(1) + legacyMatcher.group(2) + legacyMatcher.group(3)
                    + legacyMatcher.group(4) + legacyMatcher.group(5) + legacyMatcher.group(6))
                    .toUpperCase(Locale.ROOT));
        }

        Matcher matcher = HEX_COLOR.matcher(input);
        while (matcher.find()) {
            String direct = matcher.group(1);
            if (direct != null) {
                colors.add(direct.toUpperCase(Locale.ROOT));
                continue;
            }
            String hex = legacyHexToRgb(input, matcher.start());
            if (!hex.isBlank()) {
                colors.add(hex.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(colors);
    }

    public static String stripColorCodes(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return COLOR_TOKEN.matcher(input).replaceAll("");
    }

    public static String secondaryFrom(String primaryHex, SecondaryColorMode mode) {
        int[] rgb = hexToRgb(primaryHex);
        return switch (mode) {
            case LIGHTER -> rgbToHex(shift(rgb[0], 36), shift(rgb[1], 36), shift(rgb[2], 36));
            case DARKER -> rgbToHex(shift(rgb[0], -36), shift(rgb[1], -36), shift(rgb[2], -36));
            case COMPLEMENTARY -> rgbToHex(255 - rgb[0], 255 - rgb[1], 255 - rgb[2]);
        };
    }

    private static int[] hexToRgb(String hex) {
        int parsed = Integer.parseInt(hex, 16);
        int r = (parsed >> 16) & 0xFF;
        int g = (parsed >> 8) & 0xFF;
        int b = parsed & 0xFF;
        return new int[]{r, g, b};
    }

    private static String rgbToHex(int r, int g, int b) {
        return String.format("%02X%02X%02X", r, g, b);
    }

    private static int shift(int value, int delta) {
        return Math.max(0, Math.min(255, value + delta));
    }

    private static String legacyHexToRgb(String input, int start) {
        if (input == null || start < 0 || start + 13 >= input.length()) {
            return "";
        }
        if (Character.toLowerCase(input.charAt(start)) != '&'
                || Character.toLowerCase(input.charAt(start + 1)) != 'x') {
            return "";
        }

        StringBuilder hex = new StringBuilder(6);
        int index = start + 2;
        for (int i = 0; i < 6; i++) {
            if (index + 1 >= input.length() || input.charAt(index) != '&') {
                return "";
            }
            char digit = input.charAt(index + 1);
            if (!isHexDigit(digit)) {
                return "";
            }
            hex.append(Character.toUpperCase(digit));
            index += 2;
        }
        return hex.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }
}
