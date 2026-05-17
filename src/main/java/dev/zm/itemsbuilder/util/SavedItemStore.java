package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.zMItemsBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class SavedItemStore {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{1,48}$");

    private final zMItemsBuilder plugin;
    private final File file;
    private final Object lock = new Object();
    private final Map<String, ItemStack> cache = new LinkedHashMap<>();

    public SavedItemStore(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "saved_items.yml");
    }

    public void reload() {
        synchronized (lock) {
            cache.clear();
            if (!file.exists()) {
                saveToDiskLocked();
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("items");
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(false)) {
                ItemStack item = section.getItemStack(key);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                cache.put(normalizeKey(key), item.clone());
            }
        }
    }

    public boolean isValidKey(String rawKey) {
        return KEY_PATTERN.matcher(normalizeKey(rawKey)).matches();
    }

    public String normalizeKey(String rawKey) {
        return rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
    }

    public SaveResult saveItem(String rawKey, ItemStack item) {
        String key = normalizeKey(rawKey);
        if (!isValidKey(key) || item == null || item.getType().isAir()) {
            return SaveResult.INVALID;
        }

        synchronized (lock) {
            SaveResult result = cache.containsKey(key) ? SaveResult.UPDATED : SaveResult.CREATED;
            cache.put(key, item.clone());
            if (!saveToDiskLocked()) {
                return SaveResult.FAILED;
            }
            return result;
        }
    }

    public boolean removeItem(String rawKey) {
        String key = normalizeKey(rawKey);
        synchronized (lock) {
            if (!cache.containsKey(key)) {
                return false;
            }
            cache.remove(key);
            if (!saveToDiskLocked()) {
                return false;
            }
            return true;
        }
    }

    public Optional<ItemStack> getItem(String rawKey) {
        String key = normalizeKey(rawKey);
        synchronized (lock) {
            ItemStack item = cache.get(key);
            return item == null ? Optional.empty() : Optional.of(item.clone());
        }
    }

    public List<String> getKeys() {
        synchronized (lock) {
            List<String> keys = new ArrayList<>(cache.keySet());
            keys.sort(Comparator.naturalOrder());
            return Collections.unmodifiableList(keys);
        }
    }

    public Map<String, ItemStack> getAllItems() {
        synchronized (lock) {
            Map<String, ItemStack> copy = new LinkedHashMap<>();
            for (Map.Entry<String, ItemStack> entry : cache.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().clone());
            }
            return copy;
        }
    }

    private boolean saveToDiskLocked() {
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                return false;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, ItemStack> entry : cache.entrySet()) {
                yaml.set("items." + entry.getKey(), entry.getValue());
            }
            yaml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save saved_items.yml: " + e.getMessage());
            return false;
        }
    }

    public enum SaveResult {
        CREATED,
        UPDATED,
        INVALID,
        FAILED
    }
}
