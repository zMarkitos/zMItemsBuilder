package dev.zm.itemsbuilder.migration;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.config.LanguageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;

public final class MigrationManager {

    private final zMItemsBuilder plugin;
    private final LanguageManager languageManager;

    public MigrationManager(zMItemsBuilder plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    public boolean performMigrations() {
        boolean anyMigrated = false;

        if (migrateConfig()) {
            anyMigrated = true;
        }

        if (migrateLanguageFiles()) {
            anyMigrated = true;
        }

        if (anyMigrated) {
            plugin.reloadConfig();
            languageManager.reload();
            plugin.getLogger().info("All configurations reloaded after migration.");
        }

        return anyMigrated;
    }

    private boolean migrateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        int currentVersion = config.getInt("config-version", 1);

        if (currentVersion >= 2) {
            return false;
        }

        createBackup(configFile);

        if (currentVersion == 1) {
            config.set("config-version", 2);
        }

        try {
            config.save(configFile);
            plugin.getLogger().info("config.yml migrated to version 2.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save migrated config.yml", e);
            return false;
        }
    }

    private boolean migrateLanguageFiles() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() || !langFolder.isDirectory()) {
            return false;
        }

        boolean anyMigrated = false;
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).startsWith("lang_")
                && name.toLowerCase(Locale.ROOT).endsWith(".yml"));

        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (migrateLanguageFile(file)) {
                anyMigrated = true;
            }
        }
        return anyMigrated;
    }

    private boolean migrateLanguageFile(File file) {
        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(file);
        int currentVersion = langConfig.getInt("version", 0);

        if (currentVersion >= 4) {
            return false;
        }

        createBackup(file);

        String fileName = file.getName();
        String langCode = fileName.substring(5, fileName.length() - 4);
        addMissingKeysFromResource(langConfig, langCode);

        langConfig.set("version", 4);

        try {
            langConfig.save(file);
            plugin.getLogger().info("Language file " + fileName + " migrated to version 4.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save migrated language file: " + fileName, e);
            return false;
        }
    }

    private void addMissingKeysFromResource(FileConfiguration target, String langCode) {
        String resourcePath = "lang/lang_" + langCode + ".yml";
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) {
                plugin.getLogger().warning("Reference language file not found in JAR: " + resourcePath);
                return;
            }
            YamlConfiguration reference = YamlConfiguration.loadConfiguration(new InputStreamReader(is));
            for (String key : reference.getKeys(true)) {
                if (!target.contains(key)) {
                    target.set(key, reference.get(key));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read reference language file: " + resourcePath, e);
        }
    }

    private void createBackup(File file) {
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String backupName = file.getName() + "_" + timestamp + ".backup";
        File backupFile = new File(backupFolder, backupName);
        try {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backup created: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create backup for " + file.getName(), e);
        }
    }
}