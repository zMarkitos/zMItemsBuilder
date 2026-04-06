package dev.zm.itemsbuilder;

import dev.zm.itemsbuilder.command.zMItemsCommand;
import dev.zm.itemsbuilder.config.LanguageManager;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.builder.ItemFactory;
import dev.zm.itemsbuilder.builder.ItemBundleBuilder;
import dev.zm.itemsbuilder.builder.ItemRegistry;
import dev.zm.itemsbuilder.listener.ItemBehaviorListener;
import dev.zm.itemsbuilder.listener.UpdateNotificationListener;
import dev.zm.itemsbuilder.util.VersionChecker;
import java.io.File;
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

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        log("&7&m----------------------------------------");
        log("&b&lzMItemsBuilder &7» &fStarting plugin...");
        log("&7&m----------------------------------------");

        saveDefaultConfig();
        ensureLanguageFile("lang/lang_ES.yml");
        ensureLanguageFile("lang/lang_EN.yml");

        reloadPluginState();

        PluginCommand command = Objects.requireNonNull(getCommand("zmitemsbuilder"), "Command zmitemsbuilder not found in plugin.yml");
        zMItemsCommand executor = new zMItemsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ItemBehaviorListener(this), this);
        pluginManager.registerEvents(new UpdateNotificationListener(this), this);

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
}
