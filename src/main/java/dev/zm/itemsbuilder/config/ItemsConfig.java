package dev.zm.itemsbuilder.config;

import dev.zm.itemsbuilder.zMItemsBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ItemsConfig {
    private final zMItemsBuilder plugin;
    private final File itemsFile;
    private FileConfiguration itemsConfig;
    private boolean exists;

    public ItemsConfig(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.itemsFile = new File(plugin.getDataFolder(), "items.yml");
    }

    public void reload() {
        this.exists = itemsFile.exists();
        if (this.exists) {
            this.itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        } else {
            this.itemsConfig = null;
        }
    }

    private ConfigurationSection getFromItemsOrConfig(String itemsKey, String configKey) {
        if (exists && itemsConfig != null) {
            return itemsConfig.getConfigurationSection(itemsKey);
        }
        return plugin.getConfig().getConfigurationSection(configKey != null ? configKey : itemsKey);
    }

    public ConfigurationSection getItemsSection() {
        return getFromItemsOrConfig("items", "items");
    }

    public ConfigurationSection getGroupsSection() {
        return getFromItemsOrConfig("groups", "kits");
    }

    public ConfigurationSection getRaritysSection() {
        return getFromItemsOrConfig("raritys", "rarity");
    }

    public ConfigurationSection getEstheticSection() {
        return getFromItemsOrConfig("esthetic", "display");
    }

    public ConfigurationSection getHeadsTextureSection() {
        return getFromItemsOrConfig("heads-texture", "heads-texture");
    }

    public YamlConfiguration getItemsYaml() {
        if (itemsConfig instanceof YamlConfiguration yaml) {
            return yaml;
        }
        return null;
    }

    public synchronized boolean saveToDiskFromEditor() {
        if (!exists || itemsConfig == null) {
            plugin.getLogger().severe("Cannot save to items.yml because it doesn't exist yet.");
            return false;
        }
        return saveToDisk();
    }

    public boolean saveItemEntry(String id, Object data) {
        return saveEntryLocked("items." + id, data);
    }

    public boolean saveGroupEntry(String id, Object data) {
        return saveEntryLocked("groups." + id, data);
    }

    public synchronized boolean appendItemToGroup(String groupId, String itemId) {
        if (!exists || itemsConfig == null) {
            plugin.getLogger().severe("Cannot append to group in items.yml because it doesn't exist yet.");
            return false;
        }
        String groupPath = "groups." + groupId;
        if (!itemsConfig.isConfigurationSection(groupPath)) {
            itemsConfig.set(groupPath + ".rarity", "default");
            itemsConfig.set(groupPath + ".items", java.util.List.of(itemId));
        } else {
            java.util.List<String> items = new java.util.ArrayList<>(
                    itemsConfig.getStringList(groupPath + ".items"));
            if (!items.contains(itemId)) {
                items.add(itemId);
                itemsConfig.set(groupPath + ".items", items);
            }
        }
        return saveToDisk();
    }

    private synchronized boolean saveEntryLocked(String path, Object data) {
        if (!exists || itemsConfig == null) {
            plugin.getLogger().severe("Cannot save to items.yml because it doesn't exist yet.");
            return false;
        }
        itemsConfig.set(path, data);
        return saveToDisk();
    }

    private boolean saveToDisk() {
        try {
            File tmp = new File(itemsFile.getParentFile(), itemsFile.getName() + ".tmp");
            itemsConfig.save(tmp);
            try {
                Files.move(tmp.toPath(), itemsFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), itemsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save items.yml: " + e.getMessage());
            return false;
        }
    }
}
