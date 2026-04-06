package dev.zm.itemsbuilder.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_MAP = legacyMap();

    private TextUtils() {
    }

    public static Component toComponent(String raw) {
        return MINI_MESSAGE.deserialize(toMiniMessage(raw));
    }

    public static Component toItemComponent(String raw) {
        return toComponent(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static String toMiniMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String converted = replaceHex(raw);
        Matcher matcher = LEGACY_PATTERN.matcher(converted);
        StringBuilder output = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            output.append(converted, index, matcher.start());
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            output.append(LEGACY_MAP.getOrDefault(code, ""));
            index = matcher.end();
        }
        output.append(converted.substring(index));
        return output.toString();
    }

    public static String humanizeKey(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).split("[_\\- ]+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    public static String formatLevel(int level, boolean useRomanNumerals) {
        if (!useRomanNumerals) {
            return String.valueOf(level);
        }
        return toRomanNumeral(level);
    }

    public static String gradient(String text, java.util.List<String> colors) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (colors == null || colors.isEmpty()) {
            return text;
        }
        if (colors.size() == 1) {
            return "<#" + colors.get(0).toUpperCase(Locale.ROOT) + ">" + text;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<gradient:");
        for (int i = 0; i < colors.size(); i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append('#').append(colors.get(i).toUpperCase(Locale.ROOT));
        }
        builder.append('>').append(text).append("</gradient>");
        return builder.toString();
    }

    private static String replaceHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder output = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            output.append(input, index, matcher.start());
            output.append("<#").append(matcher.group(1)).append(">");
            index = matcher.end();
        }
        output.append(input.substring(index));
        return output.toString();
    }

    private static String toRomanNumeral(int value) {
        if (value <= 0) {
            return String.valueOf(value);
        }
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                builder.append(numerals[i]);
                remaining -= numbers[i];
            }
        }
        return builder.toString();
    }

    private static Map<Character, String> legacyMap() {
        Map<Character, String> map = new HashMap<>();
        map.put('0', "<black>");
        map.put('1', "<dark_blue>");
        map.put('2', "<dark_green>");
        map.put('3', "<dark_aqua>");
        map.put('4', "<dark_red>");
        map.put('5', "<dark_purple>");
        map.put('6', "<gold>");
        map.put('7', "<gray>");
        map.put('8', "<dark_gray>");
        map.put('9', "<blue>");
        map.put('a', "<green>");
        map.put('b', "<aqua>");
        map.put('c', "<red>");
        map.put('d', "<light_purple>");
        map.put('e', "<yellow>");
        map.put('f', "<white>");
        map.put('k', "<obfuscated>");
        map.put('l', "<bold>");
        map.put('m', "<strikethrough>");
        map.put('n', "<underlined>");
        map.put('o', "<italic>");
        map.put('r', "<reset>");
        return map;
    }
}
