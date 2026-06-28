package dev.zm.itemsbuilder.util;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

public final class ItemResolver {

    private ItemResolver() {
    }

    public static Optional<Material> material(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Material.matchMaterial(key.toUpperCase(Locale.ROOT)));
    }

    public static Optional<Enchantment> enchantment(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            NamespacedKey namespacedKey = new NamespacedKey(parts[0], parts[1]);
            Enchantment direct = registryGet(namespacedKey);
            if (direct != null) {
                return Optional.of(direct);
            }
            return Optional.ofNullable(Enchantment.getByKey(namespacedKey));
        }

        NamespacedKey minecraftKey = NamespacedKey.minecraft(normalized);
        Enchantment vanilla = registryGet(minecraftKey);
        if (vanilla != null) {
            return Optional.of(vanilla);
        }

        try {
            for (Enchantment enchantment : Registry.ENCHANTMENT) {
                if (enchantment.getKey().getKey().equals(normalized)) {
                    return Optional.of(enchantment);
                }
            }
        } catch (Exception | NoSuchFieldError ignored) {
        }

        Enchantment legacy = Enchantment.getByKey(minecraftKey);
        if (legacy != null) {
            return Optional.of(legacy);
        }

        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.getKey().getKey().equalsIgnoreCase(normalized)) {
                return Optional.of(enchantment);
            }
        }

        return Optional.empty();
    }

    private static Enchantment registryGet(NamespacedKey key) {
        try {
            return Registry.ENCHANTMENT.get(key);
        } catch (Exception | NoSuchFieldError e) {
            return null;
        }
    }
}