package dev.zm.itemsbuilder.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextUtils {

    // Matches &#RRGGBB hex and &X legacy codes in a single pass
    private static final Pattern CODE_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})|&([0-9a-fk-orA-FK-OR])");

    // BungeeCord hex format &x&R&R&G&G&B&B → normalized to &#RRGGBB before the main pass
    private static final Pattern BUNGEE_HEX_PATTERN =
            Pattern.compile("(?i)&x&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])&([0-9A-Fa-f])");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_MAP = legacyMap();

    private TextUtils() {}

    public static Component toComponent(String raw) {
        return MINI_MESSAGE.deserialize(toMiniMessage(raw));
    }

    public static Component toItemComponent(String raw) {
        return toComponent(raw).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Converts a legacy &-coded string (including &#RRGGBB and BungeeCord &x format)
     * into a MiniMessage string.
     *
     * Decorations (bold, italic, etc.) are properly closed with their matching closing tag
     * when a color code resets them, matching Minecraft's legacy behavior where any color
     * code clears all active formatting.
     */
    public static String toMiniMessage(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // Normalize BungeeCord hex (&x&R&R&G&G&B&B) to &#RRGGBB
        String input = BUNGEE_HEX_PATTERN.matcher(raw)
                .replaceAll(m -> "&#" + m.group(1) + m.group(2) + m.group(3)
                        + m.group(4) + m.group(5) + m.group(6));

        Matcher matcher = CODE_PATTERN.matcher(input);
        StringBuilder output = new StringBuilder(input.length());
        int index = 0;
        // Tracks decoration tags currently open (e.g. "<bold>") so they can be closed
        List<String> activeDecorations = new ArrayList<>(4);

        while (matcher.find()) {
            output.append(input, index, matcher.start());

            String hexColor = matcher.group(1); // &#RRGGBB
            String legacyCode = matcher.group(2); // &X

            if (hexColor != null) {
                closeDecorations(output, activeDecorations);
                output.append("<#").append(hexColor.toUpperCase()).append(">");
            } else {
                char code = Character.toLowerCase(legacyCode.charAt(0));
                boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
                boolean isReset = code == 'r';

                if (isColor) {
                    // Color code resets all active decorations in legacy Minecraft
                    closeDecorations(output, activeDecorations);
                    output.append(LEGACY_MAP.getOrDefault(code, ""));
                } else if (isReset) {
                    closeDecorations(output, activeDecorations);
                    output.append("<reset>");
                } else {
                    // Decoration code (k/l/m/n/o)
                    String tag = LEGACY_MAP.getOrDefault(code, "");
                    if (!tag.isEmpty()) {
                        output.append(tag);
                        if (!activeDecorations.contains(tag)) {
                            activeDecorations.add(tag);
                        }
                    }
                }
            }
            index = matcher.end();
        }
        output.append(input.substring(index));
        return output.toString();
    }

    public static String humanizeKey(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).split("[_\\- ]+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) builder.append(word.substring(1));
        }
        return builder.toString();
    }

    public static String formatLevel(int level, boolean useRomanNumerals) {
        if (!useRomanNumerals) return String.valueOf(level);
        return toRomanNumeral(level);
    }

    public static String gradient(String text, List<String> colors) {
        if (text == null || text.isEmpty()) return "";
        if (colors == null || colors.isEmpty()) return text;
        if (colors.size() == 1) return "<#" + colors.get(0).toUpperCase(Locale.ROOT) + ">" + text;
        StringBuilder builder = new StringBuilder("<gradient:");
        for (int i = 0; i < colors.size(); i++) {
            if (i > 0) builder.append(':');
            builder.append('#').append(colors.get(i).toUpperCase(Locale.ROOT));
        }
        builder.append('>').append(text).append("</gradient>");
        return builder.toString();
    }

    // Closes all active decoration tags in reverse order (e.g. <bold> → </bold>)
    private static void closeDecorations(StringBuilder sb, List<String> active) {
        if (active.isEmpty()) return;
        for (int i = active.size() - 1; i >= 0; i--) {
            String tag = active.get(i); // e.g. "<bold>"
            sb.append("</").append(tag.substring(1)); // → "</bold>"
        }
        active.clear();
    }

    private static String toRomanNumeral(int value) {
        if (value <= 0) return String.valueOf(value);
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
