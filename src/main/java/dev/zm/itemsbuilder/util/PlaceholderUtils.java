package dev.zm.itemsbuilder.util;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderUtils {

    private static final Pattern GRADIENT_TOKEN = Pattern.compile("\\{(?:gradient|prefix_gradient):([^}]+)\\}");

    private PlaceholderUtils() {
    }

    public static String replace(String input, Map<String, String> placeholders) {
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    public static String replaceGradients(String input, List<String> gradientColors) {
        if (input == null || input.isBlank()) {
            return input;
        }

        Matcher matcher = GRADIENT_TOKEN.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String tokenText = matcher.group(1);
            String replacement = TextUtils.gradient(tokenText, gradientColors);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
