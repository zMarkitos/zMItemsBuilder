package dev.zm.itemsbuilder.builder;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.EnchantLevelRule;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.builder.model.ItemMode;
import dev.zm.itemsbuilder.builder.model.ItemBundleDefinition;
import dev.zm.itemsbuilder.builder.model.AttributeSettings;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemRegistry {

    private static final Set<String> LEGACY_ITEM_KEYS = Set.of(
            "mode",
            "type",
            "material",
            "base-material",
            "material-base",
            "display-type",
            "item-type",
            "name",
            "lore",
            "amount",
            "unbreakable",
            "glow",
            "custom-model-data",
            "custom_model_data",
            "customModelData",
            "id_item",
            "id-item",
            "idItem",
            "item-flags",
            "behavior-flags",
            "template",
            "rarity",
            "level",
            "pieces",
            "enchants");

    private final zMItemsBuilder plugin;
    private final Map<String, ItemBundleDefinition> kits = new LinkedHashMap<>();
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();

    public ItemRegistry(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        kits.clear();
        items.clear();

        ConfigurationSection itemsRoot = plugin.getConfig().getConfigurationSection("items");
        if (itemsRoot != null) {
            for (String key : itemsRoot.getKeys(false)) {
                ConfigurationSection section = itemsRoot.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                ItemDefinition definition = parseItem(key.toLowerCase(Locale.ROOT), section);
                items.put(definition.id(), definition);
            }
        }

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ItemBundleDefinition definition = parseKit(key.toLowerCase(Locale.ROOT), section);
            kits.put(definition.id(), definition);
        }
    }

    public Optional<ItemBundleDefinition> getBundle(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ItemDefinition> getItem(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(id.toLowerCase(Locale.ROOT)));
    }

    public Set<String> getBundleIds() {
        return java.util.Collections.unmodifiableSet(kits.keySet());
    }

    public Set<String> getItemNames() {
        return java.util.Collections.unmodifiableSet(items.keySet());
    }

    private ItemBundleDefinition parseKit(String id, ConfigurationSection section) {
        String rarity = section.getString("rarity", "default");
        ArrayList<String> itemIds = new ArrayList<>();

        ConfigurationSection inlineItems = section.getConfigurationSection("items");
        if (inlineItems != null) {
            for (String itemKey : inlineItems.getKeys(false)) {
                ConfigurationSection itemSection = inlineItems.getConfigurationSection(itemKey);
                if (itemSection == null) {
                    continue;
                }
                String itemId = id + "." + itemKey.toLowerCase(Locale.ROOT);
                ItemDefinition definition = parseItem(itemId, itemSection);
                items.put(definition.id(), definition);
                itemIds.add(definition.id());
            }
        } else {
            itemIds.addAll(section.getStringList("items"));
        }

        if (itemIds.isEmpty()) {
            itemIds.addAll(parseLegacyItems(id, section));
        }

        int level = Math.max(1, section.getInt("level", 1));
        return new ItemBundleDefinition(id, rarity, level, itemIds);
    }

    private ArrayList<String> parseLegacyItems(String kitId, ConfigurationSection section) {
        ArrayList<String> itemIds = new ArrayList<>();

        registerLegacyItem(itemIds, kitId + ".armor", section.getConfigurationSection("armor"), ItemMode.ARMOR_SET);
        registerLegacyItem(itemIds, kitId + ".sword", section.getConfigurationSection("sword"), ItemMode.SINGLE);
        registerLegacyItem(itemIds, kitId + ".pickaxe", section.getConfigurationSection("pickaxe"), ItemMode.SINGLE);
        registerLegacyItem(itemIds, kitId + ".tools", section.getConfigurationSection("tools"), ItemMode.TOOL_SET);

        return itemIds;
    }

    private void registerLegacyItem(ArrayList<String> itemIds, String itemId, ConfigurationSection section,
            ItemMode mode) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return;
        }

        String material = section.getString("material");
        if (material == null || material.isBlank()) {
            return;
        }

        ItemDefinition definition = new ItemDefinition(
                itemId,
                mode,
                material.toUpperCase(Locale.ROOT),
                material.toUpperCase(Locale.ROOT),
                null,
                null,
                List.of(),
                false,
                parseLegacyEnchantments(section),
                1,
                false,
                false,
                null,
                itemId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        items.put(definition.id(), definition);
        itemIds.add(definition.id());
    }

    private ItemDefinition parseItem(String id, ConfigurationSection section) {
        ItemMode mode = ItemMode.from(section.getString("mode", section.getString("type", "single")));
        String material = section.getString("material");
        String baseMaterial = section.getString("base-material", section.getString("material-base"));
        String displayName = section.getString("name");
        String displayType = section.getString("display-type", section.getString("item-type"));
        boolean loreDefined = section.contains("lore");
        List<String> lore = section.getStringList("lore");
        Map<String, EnchantLevelRule> enchantments = parseEnchantments(section.getConfigurationSection("enchants"));
        if (enchantments.isEmpty()) {
            enchantments = parseLegacyEnchantments(section);
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        boolean unbreakable = section.getBoolean("unbreakable", false);
        boolean glow = section.getBoolean("glow", false);
        Integer customModelData = parseOptionalInteger(section, "custom-model-data", "custom_model_data",
                "customModelData");
        String itemIdentifier = resolveItemIdentifier(id, section);
        List<String> itemFlags = section.getStringList("item-flags");
        List<String> behaviorFlags = section.getStringList("behavior-flags");
        List<String> pieces = section.getStringList("pieces");
        List<PotionEffectSettings> customEffects = parsePotionEffects(
                section.getConfigurationSection("potion-effects"));
        List<AttributeSettings> attributes = parseAttributes(section.getConfigurationSection("attributes"));

        return new ItemDefinition(
                id,
                mode,
                material == null ? null : material.toUpperCase(Locale.ROOT),
                baseMaterial == null ? null : baseMaterial.toUpperCase(Locale.ROOT),
                displayName,
                displayType,
                lore,
                loreDefined,
                enchantments,
                amount,
                unbreakable,
                glow,
                customModelData,
                itemIdentifier,
                itemFlags,
                behaviorFlags,
                pieces,
                customEffects,
                attributes);
    }

    private String resolveItemIdentifier(String fallbackId, ConfigurationSection section) {
        String raw = firstNonBlank(section, fallbackId, "id_item", "id-item", "idItem");
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? fallbackId : normalized;
    }

    private Integer parseOptionalInteger(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (!section.contains(key)) {
                continue;
            }
            Object raw = section.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
            if (raw instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                try {
                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid numeric value for " + key + ": " + text);
                    return null;
                }
            }
            plugin.getLogger().warning("Unsupported value type for " + key + ": " + raw.getClass().getSimpleName());
            return null;
        }
        return null;
    }

    private String firstNonBlank(ConfigurationSection section, String fallback, String... keys) {
        for (String key : keys) {
            String raw = section.getString(key);
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return fallback;
    }

    private Map<String, EnchantLevelRule> parseEnchantments(ConfigurationSection section) {
        Map<String, EnchantLevelRule> enchantments = new LinkedHashMap<>();
        if (section == null) {
            return enchantments;
        }
        for (String key : section.getKeys(false)) {
            EnchantLevelRule rule = section.isConfigurationSection(key)
                    ? EnchantLevelRule.fromSection(section.getConfigurationSection(key))
                    : EnchantLevelRule.from(section.get(key));
            enchantments.put(key.toLowerCase(Locale.ROOT), rule);
        }
        return enchantments;
    }

    private Map<String, EnchantLevelRule> parseLegacyEnchantments(ConfigurationSection section) {
        Map<String, EnchantLevelRule> enchantments = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (LEGACY_ITEM_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (section.isConfigurationSection(key) || section.isList(key)) {
                continue;
            }
            int level = section.getInt(key, 0);
            if (level > 0) {
                enchantments.put(key.toLowerCase(Locale.ROOT), EnchantLevelRule.fixed(level));
            }
        }
        return enchantments;
    }

    private List<PotionEffectSettings> parsePotionEffects(ConfigurationSection section) {
        if (section == null)
            return List.of();
        List<PotionEffectSettings> effects = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection eff = section.getConfigurationSection(key);
            if (eff != null) {
                String type = eff.getString("type", key);
                int durationSeconds = eff.getInt("duration", 10);
                int amplifier = Math.max(1, eff.getInt("amplifier", 1)) - 1;
                effects.add(new PotionEffectSettings(type, durationSeconds * 20, amplifier));
            }
        }
        return effects;
    }

    private List<AttributeSettings> parseAttributes(ConfigurationSection section) {
        if (section == null)
            return List.of();
        List<AttributeSettings> attributes = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection attr = section.getConfigurationSection(key);
            if (attr != null) {
                String attribute = attr.getString("attribute", key);
                double amount = attr.getDouble("amount", 0.0);
                String operation = attr.getString("operation", "ADD_NUMBER");
                String slot = attr.getString("slot", "ALL");
                attributes.add(new AttributeSettings(attribute, amount, operation, slot));
            }
        }
        return attributes;
    }
}
