package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.hook.PapiHook;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.OfflinePlayer;

public final class PlaceholderUtils {

    private static final Pattern GRADIENT_TOKEN = Pattern.compile("\\{(?:gradient|prefix_gradient):([^}]+)\\}");

    private PlaceholderUtils() {
    }

    /**
     * Replaces internal {@code {key}} placeholders only (no PAPI).
     * Used for messages / contexts that have no player.
     */
    public static String replace(String input, Map<String, String> placeholders) {
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    /**
     * Replaces internal {@code {key}} placeholders first, then applies
     * PlaceholderAPI {@code %placeholder%} tokens for the given player.
     *
     * @param input        source string (may contain both kinds of placeholders)
     * @param placeholders internal key-value replacements
     * @param papiHook     the PAPI hook (may be {@code null} – PAPI is skipped)
     * @param player       the player context for PAPI (may be {@code null} – PAPI is skipped)
     * @return the fully resolved string
     */
    public static String replace(String input, Map<String, String> placeholders,
            PapiHook papiHook, OfflinePlayer player) {
        // 1. internal {key} substitution
        String output = replace(input, placeholders);
        // 2. PlaceholderAPI %placeholder% substitution (only when applicable)
        if (papiHook != null && papiHook.isEnabled() && player != null) {
            output = papiHook.setPlaceholders(output, player);
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
