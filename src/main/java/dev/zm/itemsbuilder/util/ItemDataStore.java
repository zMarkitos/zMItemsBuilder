package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.zMItemsBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Manages data.yml – persistent runtime data for items:
 * primarily potion/passive effects with optional equipment slots.
 *
 * Structure:
 * 
 * <pre>
 * items:
 *   my_item:
 *     effects:
 *       speed:
 *         type: SPEED
 *         duration: 200
 *         amplifier: 1
 *         slot: MAIN_HAND   # omit for consumables; set for wearable/held items
 *     actions:
 *       0:
 *         type: player_command     # player_command | console_command | sound
 *         value: "say hello %player%"
 *         click: RIGHT_CLICK       # RIGHT_CLICK | LEFT_CLICK | SHIFT_RIGHT_CLICK | SHIFT_LEFT_CLICK
 *     cooldown: 5    # seconds, optional
 *     uses: 3        # max uses per item stack, optional
 * </pre>
 */
public class ItemDataStore {

    public record ItemEffectData(String type, int duration, int amplifier, String slot) {
        /** Whether this effect is slot-restricted (non-consumable passive effect). */
        public boolean isSlotBound() {
            return slot != null && !slot.isBlank() && !slot.equalsIgnoreCase("ANY");
        }
    }

    public record ItemActionData(String type, String value, String click) {
    }

    private final zMItemsBuilder plugin;
    private final File dataFile;
    private FileConfiguration data;

    public ItemDataStore(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void reload() {
        if (!dataFile.exists()) {
            try {
                plugin.saveResource("data.yml", false);
            } catch (IllegalArgumentException e) {
                // If data.yml is not embedded in the jar, just create empty
                plugin.getLogger().warning("data.yml not found in jar, creating empty.");
            }
        }
        if (dataFile.exists()) {
            this.data = YamlConfiguration.loadConfiguration(dataFile);
        } else {
            this.data = new YamlConfiguration();
        }
    }

    // ── Read ────────────────────────────────────────────────────────────────

    /** Returns all stored effects for the given item id, or an empty list. */
    public List<ItemEffectData> getEffects(String itemId) {
        if (data == null || itemId == null)
            return List.of();
        ConfigurationSection section = data.getConfigurationSection("items." + itemId.toLowerCase() + ".effects");
        if (section == null)
            return List.of();

        List<ItemEffectData> effects = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection eff = section.getConfigurationSection(key);
            if (eff == null)
                continue;
            String type = eff.getString("type", key);
            int duration = eff.getInt("duration", 200);
            int amplifier = eff.getInt("amplifier", 0);
            String slot = eff.getString("slot", "ANY");
            effects.add(new ItemEffectData(type, duration, amplifier, slot));
        }
        return List.copyOf(effects);
    }

    /** Returns all item ids that have entries in data.yml. */
    public Set<String> getItemIds() {
        if (data == null)
            return Set.of();
        org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection("items");
        if (section == null)
            return Set.of();
        return section.getKeys(false);
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    /** Returns all stored actions for the given item id, or an empty list. */
    public List<ItemActionData> getActions(String itemId) {
        if (data == null || itemId == null)
            return List.of();
        org.bukkit.configuration.ConfigurationSection section = data
                .getConfigurationSection("items." + itemId.toLowerCase() + ".actions");
        if (section == null)
            return List.of();
        List<ItemActionData> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection a = section.getConfigurationSection(key);
            if (a == null)
                continue;
            result.add(new ItemActionData(
                    a.getString("type", ""),
                    a.getString("value", ""),
                    a.getString("click", "RIGHT_CLICK")));
        }
        return List.copyOf(result);
    }

    /**
     * Appends one action to the item's action list in data.yml.
     * Actions are keyed by sequential index (0, 1, 2…).
     */
    public synchronized boolean addAction(String itemId, ItemActionData action) {
        if (data == null)
            reload();
        String base = "items." + itemId.toLowerCase() + ".actions";
        org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection(base);
        int idx = section == null ? 0 : section.getKeys(false).size();
        data.set(base + "." + idx + ".type", action.type());
        data.set(base + "." + idx + ".value", action.value());
        data.set(base + "." + idx + ".click", action.click());
        return saveToDisk();
    }

    /**
     * Removes all actions from the item's entry (but keeps effects, cooldown,
     * uses).
     */
    public synchronized boolean removeAllActions(String itemId) {
        if (data == null)
            reload();
        data.set("items." + itemId.toLowerCase() + ".actions", null);
        return saveToDisk();
    }

    /** Removes a specific action by index (0-based). */
    public synchronized boolean removeAction(String itemId, int index) {
        if (data == null)
            reload();
        String base = "items." + itemId.toLowerCase() + ".actions";
        org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection(base);
        if (section == null)
            return false;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        if (index < 0 || index >= keys.size())
            return false;
        // rebuild ordered actions without the removed index
        List<ItemActionData> remaining = getActions(itemId);
        remaining = new ArrayList<>(remaining);
        remaining.remove(index);
        data.set(base, null);
        for (int i = 0; i < remaining.size(); i++) {
            ItemActionData a = remaining.get(i);
            data.set(base + "." + i + ".type", a.type());
            data.set(base + "." + i + ".value", a.value());
            data.set(base + "." + i + ".click", a.click());
        }
        return saveToDisk();
    }

    // ── Cooldown / Uses ──────────────────────────────────────────────────────

    /** Returns the configured cooldown in seconds for this item, or 0 if none. */
    public int getCooldown(String itemId) {
        if (data == null || itemId == null)
            return 0;
        return data.getInt("items." + itemId.toLowerCase() + ".cooldown", 0);
    }

    /** Sets the cooldown (seconds) for this item in data.yml. */
    public synchronized boolean setCooldown(String itemId, int seconds) {
        if (data == null)
            reload();
        data.set("items." + itemId.toLowerCase() + ".cooldown", seconds);
        return saveToDisk();
    }

    /** Returns the configured max uses for this item, or 0 if unlimited. */
    public int getMaxUses(String itemId) {
        if (data == null || itemId == null)
            return 0;
        return data.getInt("items." + itemId.toLowerCase() + ".uses", 0);
    }

    /** Sets the max uses for this item in data.yml. */
    public synchronized boolean setMaxUses(String itemId, int uses) {
        if (data == null)
            reload();
        data.set("items." + itemId.toLowerCase() + ".uses", uses);
        return saveToDisk();
    }

    // ── Write ───────────────────────────────────────────────────────────────

    /** Saves or replaces the effect list for the given item id. */
    public synchronized boolean saveEffects(String itemId, List<ItemEffectData> effects) {
        if (data == null)
            reload();
        String base = "items." + itemId.toLowerCase() + ".effects";
        data.set(base, null); // clear existing effects
        for (ItemEffectData eff : effects) {
            String path = base + "." + eff.type().toLowerCase();
            data.set(path + ".type", eff.type());
            if (eff.duration() != Integer.MAX_VALUE) {
                data.set(path + ".duration", eff.duration());
            }
            data.set(path + ".amplifier", eff.amplifier());
            if (eff.isSlotBound()) {
                data.set(path + ".slot", eff.slot().toUpperCase());
            }
        }
        return saveToDisk();
    }

    /** Adds or updates a single effect for the item. */
    public synchronized boolean addEffect(String itemId, ItemEffectData effect) {
        List<ItemEffectData> effects = new ArrayList<>(getEffects(itemId));
        // Remove existing effect of the same type if present
        effects.removeIf(e -> e.type().equalsIgnoreCase(effect.type()));
        effects.add(effect);
        return saveEffects(itemId, effects);
    }

    /** Removes a single effect by type from the item. */
    public synchronized boolean removeEffect(String itemId, String effectType) {
        List<ItemEffectData> effects = new ArrayList<>(getEffects(itemId));
        boolean removed = effects.removeIf(e -> e.type().equalsIgnoreCase(effectType));
        if (removed) {
            // If it's empty, we should clean up the effects section entirely
            if (effects.isEmpty()) {
                if (data == null)
                    reload();
                data.set("items." + itemId.toLowerCase() + ".effects", null);
                // Also clean up items.<id> if it's completely empty now
                ConfigurationSection itemSection = data.getConfigurationSection("items." + itemId.toLowerCase());
                if (itemSection != null && itemSection.getKeys(false).isEmpty()) {
                    data.set("items." + itemId.toLowerCase(), null);
                }
                return saveToDisk();
            }
            return saveEffects(itemId, effects);
        }
        return false;
    }

    /** Removes the entire data entry for the given item id. */
    public synchronized boolean removeItem(String itemId) {
        if (data == null)
            reload();
        data.set("items." + itemId.toLowerCase(), null);
        return saveToDisk();
    }

    // ── Disk ────────────────────────────────────────────────────────────────

    private boolean saveToDisk() {
        try {
            File tmp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
            data.save(tmp);
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
            return false;
        }
    }
}
