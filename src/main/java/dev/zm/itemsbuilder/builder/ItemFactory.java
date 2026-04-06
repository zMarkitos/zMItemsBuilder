package dev.zm.itemsbuilder.builder;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.config.LanguageManager;
import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.builder.model.EnchantLevelRule;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.builder.model.ItemMode;
import dev.zm.itemsbuilder.util.ItemResolver;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.util.MathExpression;
import dev.zm.itemsbuilder.util.PlaceholderUtils;
import dev.zm.itemsbuilder.util.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemFactory {

    private final zMItemsBuilder plugin;
    private final LanguageManager languageManager;

    public ItemFactory(zMItemsBuilder plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    public List<ItemStack> create(ItemDefinition definition, ItemBuildContext context) {
        List<ItemStack> items = new ArrayList<>();
        ItemMode mode = definition.mode();
        if (mode == ItemMode.ARMOR_SET) {
            if (definition.baseMaterial() == null || definition.baseMaterial().isBlank()) {
                plugin.getLogger().warning("Missing base-material for armor set item: " + definition.id());
                return items;
            }
            for (ArmorPiece piece : selectedArmorPieces(definition)) {
                String fullName = definition.baseMaterial() + "_" + piece.suffix();
                ItemResolver.material(fullName)
                    .map(material -> createConfiguredItem(material, definition, context, piece.name()))
                    .ifPresent(items::add);
            }
            return items;
        }
        if (mode == ItemMode.TOOL_SET) {
            if (definition.baseMaterial() == null || definition.baseMaterial().isBlank()) {
                plugin.getLogger().warning("Missing base-material for tool set item: " + definition.id());
                return items;
            }
            for (ToolPiece piece : selectedToolPieces(definition)) {
                String fullName = definition.baseMaterial() + "_" + piece.suffix();
                ItemResolver.material(fullName)
                    .map(material -> createConfiguredItem(material, definition, context, piece.name()))
                    .ifPresent(items::add);
            }
            return items;
        }

        if (definition.material() == null || definition.material().isBlank()) {
            plugin.getLogger().warning("Missing material for single item: " + definition.id());
            return items;
        }

        ItemResolver.material(definition.material())
            .map(material -> createConfiguredItem(material, definition, context, null))
            .ifPresentOrElse(
                items::add,
                () -> plugin.getLogger().warning("Invalid material for item: " + definition.id() + " -> " + definition.material())
            );
        return items;
    }

    private ItemStack createConfiguredItem(Material material, ItemDefinition definition, ItemBuildContext context, String pieceKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        Map<String, Integer> validEnchantments = applyEnchantments(meta, definition.enchantments(), definition.glow(), context.level());
        applyBehaviorFlags(meta, definition.behaviorFlags());
        Map<String, String> placeholders = basePlaceholders(material, definition, context, pieceKey);
        String displayNameTemplate = definition.displayName();
        if (displayNameTemplate == null || displayNameTemplate.isBlank()) {
            displayNameTemplate = plugin.getConfig().getString(
                "display.name-template",
                plugin.getConfig().getString("esthetic.name-format", "{item_type}")
            );
        }
        String resolvedDisplayName = applyGradients(PlaceholderUtils.replace(displayNameTemplate, placeholders), context.prefixGradientColors());
        meta.displayName(TextUtils.toItemComponent(resolvedDisplayName));
        meta.lore(buildLore(definition, context, placeholders, validEnchantments));

        if (definition.amount() > 1) {
            item.setAmount(definition.amount());
        }
        if (definition.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (definition.customModelData() != null) {
            meta.setCustomModelData(definition.customModelData());
        }
        ItemIdentityStore.write(plugin, meta, definition.itemIdentifier());
        applyItemFlags(meta, definition.itemFlags());

        item.setItemMeta(meta);
        return item;
    }

    private Map<String, Integer> applyEnchantments(ItemMeta meta, Map<String, EnchantLevelRule> source, boolean glow, int level) {
        Map<String, Integer> validEnchantments = new LinkedHashMap<>();
        for (Map.Entry<String, EnchantLevelRule> entry : source.entrySet()) {
            int enchantLevel = Math.max(1, entry.getValue().resolve(level));
            Optional<Enchantment> enchantment = ItemResolver.enchantment(entry.getKey());
            if (enchantment.isPresent()) {
                meta.addEnchant(enchantment.get(), enchantLevel, true);
                validEnchantments.put(entry.getKey(), enchantLevel);
            }
        }
        if (glow && validEnchantments.isEmpty()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        return validEnchantments;
    }

    private List<Component> buildLore(ItemDefinition definition, ItemBuildContext context, Map<String, String> placeholders, Map<String, Integer> enchantments) {
        if (definition.loreDefined() && definition.lore().isEmpty()) {
            return List.of();
        }

        List<String> template = definition.loreDefined()
            ? definition.lore()
            : plugin.getConfig().getStringList("display.lore-template");
        if (template.isEmpty()) {
            template = plugin.getConfig().getStringList("esthetic.lore-template");
        }
        String enchantTemplate = plugin.getConfig().getString(
            "display.enchant-format",
            plugin.getConfig().getString("esthetic.enchant-format", "{enchant_name} {level}")
        );
        boolean useRomanNumerals = plugin.settings().useRomanNumerals();
        boolean useRarity = plugin.getConfig().getBoolean(
            "settings.use-rarity",
            plugin.getConfig().getBoolean("settings.use-raritys", true)
        );
        List<Component> enchantLines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            Map<String, String> enchantPlaceholders = new LinkedHashMap<>(placeholders);
            enchantPlaceholders.put("enchant_name", languageManager.enchantName(entry.getKey()));
            enchantPlaceholders.put("level", TextUtils.formatLevel(entry.getValue(), useRomanNumerals));
            String resolvedEnchantLine = applyGradients(PlaceholderUtils.replace(enchantTemplate, enchantPlaceholders), context.prefixGradientColors());
            enchantLines.add(TextUtils.toItemComponent(resolvedEnchantLine));
        }

        List<Component> lore = new ArrayList<>();
        for (String rawLine : template) {
            if (rawLine.contains("{rarity}") && !useRarity) {
                continue;
            }
            if (rawLine.contains("{enchants}")) {
                lore.addAll(enchantLines);
                continue;
            }
            String resolvedLine = applyGradients(PlaceholderUtils.replace(rawLine, placeholders), context.prefixGradientColors());
            lore.add(TextUtils.toItemComponent(resolvedLine));
        }
        return lore;
    }

    private Map<String, String> basePlaceholders(Material material, ItemDefinition definition, ItemBuildContext context, String pieceKey) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("kit", context.kitId());
        placeholders.put("level", TextUtils.formatLevel(context.level(), plugin.settings().useRomanNumerals()));
        placeholders.put("prefix_item", context.prefixMiniMessage());
        placeholders.put("prefix_kit", context.prefixMiniMessage());
        placeholders.put("primary_color", "<#" + context.primaryHex() + ">");
        placeholders.put("secondary_color", "<#" + context.secondaryHex() + ">");
        placeholders.put("color_principal", "<#" + context.primaryHex() + ">");
        placeholders.put("color_secundario", "<#" + context.secondaryHex() + ">");
        placeholders.put("rarity", TextUtils.toMiniMessage(context.rarityText()));
        placeholders.put("item_id", definition.itemIdentifier() == null ? definition.id() : definition.itemIdentifier());
        placeholders.put("item_mode", definition.mode().configKey());
        if (pieceKey != null) {
            placeholders.put("piece", pieceKey.toLowerCase());
        }
        placeholders.put("item_type", itemTypeName(material, definition));
        return placeholders;
    }

    private String itemTypeName(Material material, ItemDefinition definition) {
        if (definition.displayType() != null && !definition.displayType().isBlank()) {
            return definition.displayType();
        }
        String key = material.getKey().getKey();
        String itemTypeKey = resolveItemTypeKey(key);
        return languageManager.itemTypeName(itemTypeKey, TextUtils.humanizeKey(key));
    }

    private String resolveItemTypeKey(String materialKey) {
        if (materialKey.endsWith("_helmet")) {
            return "helmet";
        }
        if (materialKey.endsWith("_chestplate")) {
            return "chestplate";
        }
        if (materialKey.endsWith("_leggings")) {
            return "leggings";
        }
        if (materialKey.endsWith("_boots")) {
            return "boots";
        }
        if (materialKey.endsWith("_sword")) {
            return "sword";
        }
        if (materialKey.endsWith("_pickaxe")) {
            return "pickaxe";
        }
        if (materialKey.endsWith("_axe")) {
            return "axe";
        }
        if (materialKey.endsWith("_shovel")) {
            return "shovel";
        }
        if (materialKey.endsWith("_hoe")) {
            return "hoe";
        }
        if (materialKey.endsWith("_crossbow")) {
            return "crossbow";
        }
        if (materialKey.endsWith("_bow")) {
            return "bow";
        }
        if (materialKey.endsWith("_trident")) {
            return "trident";
        }
        return materialKey;
    }

    private String applyGradients(String input, List<String> gradientColors) {
        return PlaceholderUtils.replaceGradients(input, gradientColors);
    }

    private void applyItemFlags(ItemMeta meta, List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return;
        }
        List<ItemFlag> parsedFlags = new ArrayList<>();
        for (String flag : flags) {
            if (flag == null || flag.isBlank()) {
                continue;
            }
            try {
                parsedFlags.add(ItemFlag.valueOf(flag.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown flags.
            }
        }
        if (!parsedFlags.isEmpty()) {
            meta.addItemFlags(parsedFlags.toArray(new ItemFlag[0]));
        }
    }

    private void applyBehaviorFlags(ItemMeta meta, List<String> rawFlags) {
        if (rawFlags == null || rawFlags.isEmpty()) {
            return;
        }
        EnumSet<ItemBehaviorFlag> parsed = EnumSet.noneOf(ItemBehaviorFlag.class);
        for (String raw : rawFlags) {
            ItemBehaviorFlag flag = ItemBehaviorFlag.from(raw);
            if (flag != null) {
                parsed.add(flag);
            }
        }
        if (!parsed.isEmpty()) {
            ItemFlagStore.write(plugin, meta, parsed);
        }
    }

    private List<ArmorPiece> selectedArmorPieces(ItemDefinition definition) {
        if (definition.pieces().isEmpty()) {
            return Arrays.asList(ArmorPiece.values());
        }
        List<ArmorPiece> pieces = new ArrayList<>();
        for (String piece : definition.pieces()) {
            ArmorPiece.from(piece).ifPresent(pieces::add);
        }
        return pieces.isEmpty() ? Arrays.asList(ArmorPiece.values()) : pieces;
    }

    private List<ToolPiece> selectedToolPieces(ItemDefinition definition) {
        if (definition.pieces().isEmpty()) {
            return Arrays.asList(ToolPiece.values());
        }
        List<ToolPiece> pieces = new ArrayList<>();
        for (String piece : definition.pieces()) {
            ToolPiece.from(piece).ifPresent(pieces::add);
        }
        return pieces.isEmpty() ? Arrays.asList(ToolPiece.values()) : pieces;
    }

    private enum ArmorPiece {
        HELMET("HELMET", "helmet"),
        CHESTPLATE("CHESTPLATE", "chestplate"),
        LEGGINGS("LEGGINGS", "leggings"),
        BOOTS("BOOTS", "boots");

        private final String suffix;
        private final String key;

        ArmorPiece(String suffix, String key) {
            this.suffix = suffix;
            this.key = key;
        }

        String suffix() {
            return suffix;
        }

        static Optional<ArmorPiece> from(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return Arrays.stream(values())
                .filter(piece -> piece.key.equalsIgnoreCase(raw) || piece.name().equalsIgnoreCase(raw))
                .findFirst();
        }
    }

    private enum ToolPiece {
        AXE("AXE", "axe"),
        HOE("HOE", "hoe"),
        SHOVEL("SHOVEL", "shovel");

        private final String suffix;
        private final String key;

        ToolPiece(String suffix, String key) {
            this.suffix = suffix;
            this.key = key;
        }

        String suffix() {
            return suffix;
        }

        static Optional<ToolPiece> from(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return Arrays.stream(values())
                .filter(piece -> piece.key.equalsIgnoreCase(raw) || piece.name().equalsIgnoreCase(raw))
                .findFirst();
        }
    }
}
