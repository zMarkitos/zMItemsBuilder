package dev.zm.itemsbuilder.builder.model;

/**
 * Potion effect configuration that can be level-scaled.
 *
 * Amplifier is treated as 1-based (I = 1) in config, and will be converted to
 * Bukkit's 0-based amplifier when applied.
 */
public record PotionEffectRule(
    String id,
    String type,
    EnchantLevelRule durationSeconds,
    EnchantLevelRule amplifierLevel
) {

    public PotionEffectSettings resolve(int level) {
        int seconds = Math.max(0, durationSeconds == null ? 0 : durationSeconds.resolve(level));
        int ampLevel = Math.max(1, amplifierLevel == null ? 1 : amplifierLevel.resolve(level));
        int amplifier = Math.max(0, ampLevel - 1);
        return new PotionEffectSettings(type, seconds * 20, amplifier);
    }
}

