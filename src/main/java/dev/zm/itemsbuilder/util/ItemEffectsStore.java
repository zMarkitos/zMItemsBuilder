package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stores resolved potion effects on the built item so consumption is correct even when
 * effects were level-scaled at build time.
 */
public final class ItemEffectsStore {

    private static final String KEY_NAME = "custom_potion_effects";

    private ItemEffectsStore() {
    }

    public static void write(JavaPlugin plugin, ItemMeta meta, List<PotionEffectSettings> effects) {
        if (plugin == null || meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, KEY_NAME);
        if (effects == null || effects.isEmpty()) {
            container.remove(key);
            return;
        }
        container.set(key, PersistentDataType.STRING, encode(effects));
    }

    public static List<PotionEffectSettings> read(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || !item.hasItemMeta()) {
            return List.of();
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String raw = container.get(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return decode(raw);
    }

    private static String encode(List<PotionEffectSettings> effects) {
        // type;durationTicks;amplifier|type;durationTicks;amplifier
        StringBuilder builder = new StringBuilder();
        for (PotionEffectSettings eff : effects) {
            if (eff == null || eff.type() == null || eff.type().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('|');
            }
            builder.append(eff.type().toLowerCase(Locale.ROOT))
                    .append(';')
                    .append(Math.max(0, eff.durationTicks()))
                    .append(';')
                    .append(Math.max(0, eff.amplifier()));
        }
        return builder.toString();
    }

    private static List<PotionEffectSettings> decode(String raw) {
        List<PotionEffectSettings> effects = new ArrayList<>();
        for (String entry : raw.split("\\|")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(";");
            if (parts.length != 3) {
                continue;
            }
            String type = parts[0].trim();
            if (type.isBlank()) {
                continue;
            }
            try {
                int duration = Integer.parseInt(parts[1].trim());
                int amplifier = Integer.parseInt(parts[2].trim());
                effects.add(new PotionEffectSettings(type, Math.max(0, duration), Math.max(0, amplifier)));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(effects);
    }
}

