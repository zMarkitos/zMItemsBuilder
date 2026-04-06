package dev.zm.itemsbuilder.config;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.util.PlaceholderUtils;
import dev.zm.itemsbuilder.util.TextUtils;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LanguageManager {

    private final zMItemsBuilder plugin;
    private final Map<String, FileConfiguration> loadedLanguages = new LinkedHashMap<>();
    private String currentLanguageCode = "ES";
    private FileConfiguration languageConfig;
    private String messagePrefix = "";
    private Component messagePrefixComponent = Component.empty();

    public LanguageManager(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public void load(String languageCode) {
        this.currentLanguageCode = normalizeLanguageCode(languageCode);
        reload();
    }

    public void reload() {
        loadedLanguages.clear();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).startsWith("lang_") && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                String code = normalizeLanguageCode(name.substring("lang_".length(), name.length() - ".yml".length()));
                loadedLanguages.put(code, YamlConfiguration.loadConfiguration(file));
            }
        }
        this.languageConfig = loadedLanguages.getOrDefault(
            currentLanguageCode,
            loadedLanguages.getOrDefault("ES", new YamlConfiguration())
        );
        this.messagePrefix = this.languageConfig.getString("messages.prefix", "");
        this.messagePrefixComponent = messagePrefix == null || messagePrefix.isBlank()
            ? Component.empty()
            : TextUtils.toComponent(messagePrefix);
    }

    public Component message(String key) {
        return message(key, Collections.emptyMap());
    }

    public Component message(String key, Map<String, String> placeholders) {
        Component body = rawMessage("messages." + key, placeholders);
        return messagePrefix == null || messagePrefix.isBlank()
            ? body
            : messagePrefixComponent.append(body);
    }

    public Component rawMessage(String path, Map<String, String> placeholders) {
        String raw = languageConfig.getString(path, "&cMissing message: " + path);
        String replaced = PlaceholderUtils.replace(raw, placeholders);
        return TextUtils.toComponent(replaced);
    }

    public Component rawMessage(String path) {
        return rawMessage(path, Collections.emptyMap());
    }

    public String enchantName(String key) {
        return languageConfig.getString("enchantments." + key.toLowerCase(Locale.ROOT), TextUtils.humanizeKey(key));
    }

    public String itemTypeName(String key, String fallback) {
        return languageConfig.getString("item-types." + key.toLowerCase(Locale.ROOT), fallback);
    }

    private String normalizeLanguageCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ES";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EN", "ES" -> normalized;
            default -> "ES";
        };
    }
}
