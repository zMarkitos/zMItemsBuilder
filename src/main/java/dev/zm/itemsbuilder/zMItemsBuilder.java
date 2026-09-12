package dev.zm.itemsbuilder;

import dev.zm.itemsbuilder.command.gui.HelpGui;
import dev.zm.itemsbuilder.command.gui.ItemCreationGui;
import dev.zm.itemsbuilder.command.gui.ProgressionGui;
import dev.zm.itemsbuilder.command.zMItemsCommand;
import dev.zm.itemsbuilder.config.ItemsConfig;
import dev.zm.itemsbuilder.config.LanguageManager;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.builder.ItemFactory;
import dev.zm.itemsbuilder.builder.ItemBundleBuilder;
import dev.zm.itemsbuilder.builder.ItemRegistry;
import dev.zm.itemsbuilder.hook.PapiHook;
import dev.zm.itemsbuilder.listener.EffectListener;
import dev.zm.itemsbuilder.listener.ItemActionListener;
import dev.zm.itemsbuilder.listener.ItemBehaviorListener;
import dev.zm.itemsbuilder.listener.UpdateNotificationListener;
import dev.zm.itemsbuilder.migration.MigrationManager;
import dev.zm.itemsbuilder.util.SavedItemStore;
import dev.zm.itemsbuilder.util.ItemDataStore;
import dev.zm.itemsbuilder.util.VersionChecker;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class zMItemsBuilder extends JavaPlugin {

    private PluginSettings settings;
    private LanguageManager languageManager;
    private ItemRegistry itemRegistry;
    private ItemBundleBuilder itemBundleBuilder;
    private VersionChecker versionChecker;
    private SavedItemStore savedItemStore;
    private ItemsConfig itemsConfig;
    private ItemDataStore itemDataStore;
    private PapiHook papiHook;
    private ItemActionListener itemActionListener;
    private EffectListener effectListener;

    private void ensureDataFiles() {
        String[] files = { "items.yml", "data.yml" };
        for (String fileName : files) {
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                boolean copied = saveResourceIfExists(fileName, false);
                if (!copied) {
                    try {
                        file.createNewFile();
                        java.nio.file.Files.write(file.toPath(),
                                "# File generated automatically by zMItemsBuilder\n".getBytes());
                    } catch (IOException e) {
                        getLogger().warning("Failed to create file " + fileName + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private boolean saveResourceIfExists(String resourcePath, boolean replace) {
        try {
            java.net.URL url = getClass().getClassLoader().getResource(resourcePath);
            if (url != null) {
                saveResource(resourcePath, replace);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        log("&7&m----------------------------------------");
        log("&b&lzMItemsBuilder &7» &fStarting plugin...");
        log("&7&m----------------------------------------");

        saveDefaultConfig();
        ensureLanguageFile("lang/lang_ES.yml");
        ensureLanguageFile("lang/lang_EN.yml");
        ensureDataFiles();
        this.papiHook = new PapiHook(this);

        reloadPluginState();

        MigrationManager migrationManager = new MigrationManager(this, languageManager);
        boolean anyMigrated = migrationManager.performMigrations();

        PluginCommand command = Objects.requireNonNull(getCommand("zmitemsbuilder"),
                "Command zmitemsbuilder not found in plugin.yml");
        zMItemsCommand executor = new zMItemsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        PluginCommand renameCommand = Objects.requireNonNull(getCommand("irename"),
                "Command irename not found in plugin.yml");
        renameCommand.setExecutor(executor);
        renameCommand.setTabCompleter(executor);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ItemBehaviorListener(this), this);
        pluginManager.registerEvents(new UpdateNotificationListener(this), this);
        pluginManager.registerEvents(executor, this);
        pluginManager.registerEvents(new HelpGui(this), this);
        pluginManager.registerEvents(new ItemCreationGui(this), this);
        pluginManager.registerEvents(new ProgressionGui(this), this);
        this.itemActionListener = new ItemActionListener(this);
        pluginManager.registerEvents(this.itemActionListener, this);
        this.effectListener = new EffectListener(this);
        getServer().getPluginManager().registerEvents(new EffectListener(this), this);

        try {
            int pluginId = 32937;
            org.bstats.bukkit.Metrics metrics = new org.bstats.bukkit.Metrics(this, pluginId);
        } catch (Exception e) {
            getLogger().warning("Could not enable bStats metrics: " + e.getMessage());
        }

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

        this.itemsConfig = new ItemsConfig(this);
        this.itemsConfig.reload();

        if (this.itemDataStore == null) {
            this.itemDataStore = new ItemDataStore(this);
        }
        this.itemDataStore.reload();

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

        if (this.effectListener != null) {
            this.effectListener.clearAllEffects();
        }
    }

    private void ensureLanguageFile(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
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

    public ItemsConfig itemsConfig() {
        return itemsConfig;
    }

    public ItemDataStore itemDataStore() {
        return itemDataStore;
    }

    public ItemActionListener itemActionListener() {
        return itemActionListener;
    }

    public PapiHook papiHook() {
        return papiHook;
    }
}
