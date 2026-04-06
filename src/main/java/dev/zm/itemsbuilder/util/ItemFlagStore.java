package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemFlagStore {

    private static final String KEY_NAME_V2 = "behavior_flags_mask";
    private static final String KEY_NAME_V1 = "behavior_flags";

    private ItemFlagStore() {
    }

    public static void write(JavaPlugin plugin, ItemMeta meta, Set<ItemBehaviorFlag> flags) {
        if (plugin == null || meta == null || flags == null || flags.isEmpty()) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key(plugin, KEY_NAME_V2), PersistentDataType.INTEGER, toMask(flags));
    }

    public static boolean hasAny(JavaPlugin plugin, ItemStack item, ItemBehaviorFlag... flags) {
        int mask = readMask(plugin, item);
        if (mask == 0) {
            return false;
        }
        for (ItemBehaviorFlag flag : flags) {
            if ((mask & flag.mask()) != 0) {
                return true;
            }
        }
        return false;
    }

    public static Set<ItemBehaviorFlag> read(JavaPlugin plugin, ItemStack item) {
        int mask = readMask(plugin, item);
        if (mask == 0) {
            return Set.of();
        }
        EnumSet<ItemBehaviorFlag> result = EnumSet.noneOf(ItemBehaviorFlag.class);
        for (ItemBehaviorFlag flag : ItemBehaviorFlag.values()) {
            if ((mask & flag.mask()) != 0) {
                result.add(flag);
            }
        }
        return result;
    }

    private static int readMask(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || !item.hasItemMeta()) {
            return 0;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Integer mask = container.get(key(plugin, KEY_NAME_V2), PersistentDataType.INTEGER);
        if (mask != null) {
            return mask;
        }
        String raw = container.get(key(plugin, KEY_NAME_V1), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return 0;
        }

        int parsed = 0;
        for (String token : raw.split(",")) {
            if (token.isBlank()) {
                continue;
            }
            try {
                parsed |= ItemBehaviorFlag.valueOf(token.trim().toUpperCase(java.util.Locale.ROOT)).mask();
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown flags.
            }
        }
        return parsed;
    }

    private static int toMask(Set<ItemBehaviorFlag> flags) {
        int mask = 0;
        for (ItemBehaviorFlag flag : flags) {
            mask |= flag.mask();
        }
        return mask;
    }

    private static NamespacedKey key(JavaPlugin plugin, String name) {
        return new NamespacedKey(plugin, name);
    }
}
