package dev.zm.itemsbuilder;

import dev.zm.itemsbuilder.command.zMItemsCommand;
import dev.zm.itemsbuilder.config.LanguageManager;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.builder.ItemFactory;
import dev.zm.itemsbuilder.builder.ItemBundleBuilder;
import dev.zm.itemsbuilder.builder.ItemRegistry;
import dev.zm.itemsbuilder.listener.ItemBehaviorListener;
import dev.zm.itemsbuilder.listener.UpdateNotificationListener;
import dev.zm.itemsbuilder.util.SavedItemStore;
import dev.zm.itemsbuilder.util.VersionChecker;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class zMItemsBuilder extends JavaPlugin {
    private static final int CURRENT_CONFIG_VERSION = 2;
    private static final int CURRENT_LANG_VERSION = 2;
    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PluginSettings settings;
    private LanguageManager languageManager;
    private ItemRegistry itemRegistry;
    private ItemBundleBuilder itemBundleBuilder;
    private VersionChecker versionChecker;
    private SavedItemStore savedItemStore;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        log("&7&m----------------------------------------");
        log("&b&lzMItemsBuilder &7» &fStarting plugin...");
        log("&7&m----------------------------------------");

        saveDefaultConfig();
        ensureLanguageFile("lang/lang_ES.yml");
        ensureLanguageFile("lang/lang_EN.yml");
        migrateManagedFilesIfNeeded();

        reloadPluginState();

        PluginCommand command = Objects.requireNonNull(getCommand("zmitemsbuilder"),
                "Command zmitemsbuilder not found in plugin.yml");
        zMItemsCommand executor = new zMItemsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ItemBehaviorListener(this), this);
        pluginManager.registerEvents(new UpdateNotificationListener(this), this);
        pluginManager.registerEvents(executor, this);

        long time = System.currentTimeMillis() - start;

        log("&7");
        log("&a✔ &fPlugin enabled successfully");
        log("&7• &fVersion: &b" + getDescription().getVersion());
        log("&7• &fLanguage: &b" + settings.languageCode());
        log("&7• &fLoad time: &b" + time + "ms");
        log("&7");
        log("&7&m----------------------------------------");
    }

    @Override
    public void onDisable() {
        log("&c✘ &fPlugin disabled.");
    }

    public void reloadPluginState() {
        reloadConfig();
        PluginSettings newSettings = PluginSettings.fromConfig(getConfig());
        this.settings = newSettings;

        if (this.languageManager == null) {
            this.languageManager = new LanguageManager(this);
        }
        this.languageManager.load(newSettings.languageCode());

        this.itemRegistry = new ItemRegistry(this);
        this.itemRegistry.reload();
        this.itemBundleBuilder = new ItemBundleBuilder(this, new ItemFactory(this, this.languageManager));
        if (this.savedItemStore == null) {
            this.savedItemStore = new SavedItemStore(this);
        }
        this.savedItemStore.reload();

        if (this.versionChecker == null) {
            this.versionChecker = new VersionChecker(this);
        }
        this.versionChecker.refresh();
    }

    private void ensureLanguageFile(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    private void migrateManagedFilesIfNeeded() {
        File backupDir = null;

        File configFile = new File(getDataFolder(), "config.yml");
        int configVersion = readVersion(configFile, "config-version", -1);
        if (configVersion < CURRENT_CONFIG_VERSION) {
            backupDir = ensureBackupDirectory(backupDir);
            if (backupDir != null && backupFile(configFile, backupDir)) {
                saveResource("config.yml", true);
                log("&eMigrated config.yml to config-version " + CURRENT_CONFIG_VERSION + ".");
            } else {
                getLogger().warning("Skipped config.yml migration because backup could not be created safely.");
            }
        }

        for (String langPath : new String[] { "lang/lang_ES.yml", "lang/lang_EN.yml" }) {
            File langFile = new File(getDataFolder(), langPath);
            int langVersion = readVersion(langFile, "version", -1);
            if (langVersion < CURRENT_LANG_VERSION) {
                backupDir = ensureBackupDirectory(backupDir);
                if (backupDir != null && backupFile(langFile, backupDir)) {
                    saveResource(langPath, true);
                    log("&eMigrated " + langPath + " to version " + CURRENT_LANG_VERSION + ".");
                } else {
                    getLogger().warning("Skipped " + langPath + " migration because backup could not be created safely.");
                }
            }
        }
    }

    private int readVersion(File file, String key, int fallback) {
        if (file == null || !file.exists()) {
            return fallback;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            return yaml.getInt(key, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private File ensureBackupDirectory(File existingBackupDir) {
        File backupDir = existingBackupDir;
        if (backupDir == null) {
            backupDir = new File(getDataFolder(), "backups/" + LocalDateTime.now().format(BACKUP_TS));
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                getLogger().warning("Could not create backup directory: " + backupDir.getAbsolutePath());
                return null;
            }
        }
        return backupDir;
    }

    private boolean backupFile(File source, File backupDir) {
        if (source == null || !source.exists() || backupDir == null) {
            return false;
        }
        try {
            Path dataRoot = getDataFolder().toPath().toAbsolutePath().normalize();
            Path sourcePath = source.toPath().toAbsolutePath().normalize();
            Path relative = dataRoot.relativize(sourcePath);
            File target = new File(backupDir, relative.toString());
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                getLogger().warning("Could not create backup subdirectory: " + parent.getAbsolutePath());
                return false;
            }
            Files.copy(sourcePath, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            getLogger().warning("Failed to backup " + source.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void log(String message) {
        Bukkit.getConsoleSender().sendMessage(color(message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public PluginSettings settings() {
        return settings;
    }

    public LanguageManager language() {
        return languageManager;
    }

    public ItemRegistry itemRegistry() {
        return itemRegistry;
    }

    public ItemBundleBuilder itemBundleBuilder() {
        return itemBundleBuilder;
    }

    public VersionChecker versionChecker() {
        return versionChecker;
    }

    public SavedItemStore savedItemStore() {
        return savedItemStore;
    }
}
