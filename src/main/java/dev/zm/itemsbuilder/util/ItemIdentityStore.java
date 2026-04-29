package dev.zm.itemsbuilder.util;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemIdentityStore {

    // User-facing id — only written when id_item: is explicitly set in config.
    private static final String KEY_ITEM_ID = "item_id";
    // Internal config key — always written so the copy command can locate any plugin item.
    private static final String KEY_SOURCE = "source_key";

    private ItemIdentityStore() {}

    /** Writes the user-facing id_item PDC tag. Call only when id_item: is explicitly configured. */
    public static void write(JavaPlugin plugin, ItemMeta meta, String itemId) {
        if (plugin == null || meta == null) return;
        String normalized = normalize(itemId);
        if (normalized == null) return;
        meta.getPersistentDataContainer().set(key(plugin, KEY_ITEM_ID), PersistentDataType.STRING, normalized);
    }

    /** Always writes the internal source key (config key) so every plugin item is identifiable. */
    public static void writeSourceKey(JavaPlugin plugin, ItemMeta meta, String configKey) {
        if (plugin == null || meta == null) return;
        String normalized = normalize(configKey);
        if (normalized == null) return;
        meta.getPersistentDataContainer().set(key(plugin, KEY_SOURCE), PersistentDataType.STRING, normalized);
    }

    /** Reads the user-facing item_id PDC tag, or null if absent. */
    public static String read(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return normalize(pdc.get(key(plugin, KEY_ITEM_ID), PersistentDataType.STRING));
    }

    /** Reads the internal source key; falls back to the user-facing item_id if absent (legacy items). */
    public static String readSourceKey(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String sourceKey = normalize(pdc.get(key(plugin, KEY_SOURCE), PersistentDataType.STRING));
        if (sourceKey != null) return sourceKey;
        // Fallback: items built before this change only have the item_id key.
        return normalize(pdc.get(key(plugin, KEY_ITEM_ID), PersistentDataType.STRING));
    }

    public static boolean matches(JavaPlugin plugin, ItemStack item, String expectedItemId) {
        String stored = read(plugin, item);
        String expected = normalize(expectedItemId);
        return stored != null && stored.equals(expected);
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static NamespacedKey key(JavaPlugin plugin, String name) {
        return new NamespacedKey(plugin, name);
    }
}
