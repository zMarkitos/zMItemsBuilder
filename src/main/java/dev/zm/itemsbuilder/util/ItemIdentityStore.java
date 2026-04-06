package dev.zm.itemsbuilder.util;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemIdentityStore {

    private static final String KEY_NAME = "item_id";

    private ItemIdentityStore() {
    }

    public static void write(JavaPlugin plugin, ItemMeta meta, String itemId) {
        if (plugin == null || meta == null) {
            return;
        }
        String normalized = normalize(itemId);
        if (normalized == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, normalized);
    }

    public static String read(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return normalize(container.get(key(plugin), PersistentDataType.STRING));
    }

    public static boolean matches(JavaPlugin plugin, ItemStack item, String expectedItemId) {
        String stored = read(plugin, item);
        String expected = normalize(expectedItemId);
        return stored != null && stored.equals(expected);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }
}
