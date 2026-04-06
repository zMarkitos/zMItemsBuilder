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
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT));
        return Optional.ofNullable(Registry.ENCHANTMENT.get(namespacedKey));
    }
}
