package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.config.SecondaryColorMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Pattern HEX_COLOR = Pattern.compile("(?:&#|<#)([A-Fa-f0-9]{6})>?");
    private static final Pattern COLOR_TOKEN = Pattern.compile("(?:&#|<#)[A-Fa-f0-9]{6}>?|&[0-9a-fk-orA-FK-OR]|<[^>]+>");

    private ColorUtils() {
    }

    public static Optional<String> extractHex(String input) {
        if (input == null) {
            return Optional.empty();
        }
        Matcher matcher = HEX_COLOR.matcher(input);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    public static List<String> extractHexColors(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        Matcher matcher = HEX_COLOR.matcher(input);
        LinkedHashSet<String> colors = new LinkedHashSet<>();
        while (matcher.find()) {
            colors.add(matcher.group(1).toUpperCase(Locale.ROOT));
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
}
