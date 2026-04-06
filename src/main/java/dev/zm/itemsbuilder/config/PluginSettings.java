package dev.zm.itemsbuilder.config;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    SecondaryColorMode secondaryColorMode,
    String languageCode,
    boolean useRomanNumerals,
    SoundSettings soundSettings,
    UpdateSettings updateSettings
) {

    public static PluginSettings fromConfig(FileConfiguration config) {
        String language = normalizeLanguage(config.getString("settings.language", config.getString("settings.languaje", "ES")));
        SecondaryColorMode mode = SecondaryColorMode.from(config.getString("settings.secondary-color-mode", "LIGHTER"));
        boolean useRomanNumerals = config.getBoolean("settings.use-roman-numerals", false);
        Sound sound = parseSound(config.getString("settings.sound.type", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        SoundSettings soundSettings = new SoundSettings(
            config.getBoolean("settings.sound.enabled", true),
            sound,
            (float) config.getDouble("settings.sound.volume", 1.0D),
            (float) config.getDouble("settings.sound.pitch", 1.0D)
        );
        UpdateSettings updateSettings = new UpdateSettings(config.getBoolean("update-check.enabled", true));
        return new PluginSettings(mode, language, useRomanNumerals, soundSettings, updateSettings);
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ES";
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "EN", "ES" -> normalized;
            default -> "ES";
        };
    }

    private static Sound parseSound(String raw) {
        try {
            return Sound.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }

    public record SoundSettings(
        boolean enabled,
        Sound type,
        float volume,
        float pitch
    ) {
    }

    public record UpdateSettings(boolean enabled) {
    }
}
