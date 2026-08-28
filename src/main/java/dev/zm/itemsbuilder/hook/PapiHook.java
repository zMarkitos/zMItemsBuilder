package dev.zm.itemsbuilder.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soft-dependency hook for PlaceholderAPI.
 * <p>
 * The class is loaded only when PlaceholderAPI is present on the server.
 * All calls are guarded with {@link #isEnabled()} so the rest of the plugin
 * never has to catch {@link ClassNotFoundException} or similar errors.
 * </p>
 */
public final class PapiHook {

    private final boolean enabled;

    public PapiHook(JavaPlugin plugin) {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (this.enabled) {
            plugin.getLogger().info("[PlaceholderAPI] Hook enabled – placeholder support is active.");
        }
    }

    /** Returns {@code true} if PlaceholderAPI is loaded and active. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies PlaceholderAPI placeholders to {@code text} for the given player.
     * <p>
     * If PAPI is not enabled, or {@code player} is {@code null}, or {@code text}
     * is blank, the original string is returned unchanged.
     * </p>
     *
     * @param text   the text that may contain {@code %placeholder%} tokens
     * @param player the offline player context (may be null)
     * @return the processed string with placeholders replaced
     */
    public String setPlaceholders(String text, OfflinePlayer player) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        // Avoid importing PAPI at class-load time: the method is only reached
        // when PAPI is confirmed present, so the class will be available.
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }
}
