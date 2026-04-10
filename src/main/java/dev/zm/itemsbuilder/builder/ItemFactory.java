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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Color;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import dev.zm.itemsbuilder.builder.model.AttributeSettings;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import java.util.UUID;

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
                        () -> plugin.getLogger().warning(
                                "Invalid material for item: " + definition.id() + " -> " + definition.material()));
        return items;
    }

    private ItemStack createConfiguredItem(Material material, ItemDefinition definition, ItemBuildContext context,
            String pieceKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        Map<String, Integer> validEnchantments = applyEnchantments(meta, definition.enchantments(), definition.glow(),
                context.level());
        applyBehaviorFlags(meta, definition.behaviorFlags());
        applyAttributes(meta, definition.attributes(), pieceKey, material);
        applyPotionEffects(meta, definition.customEffects());
        Map<String, String> placeholders = basePlaceholders(material, definition, context, pieceKey);
        String displayNameTemplate = definition.displayName();
        if (displayNameTemplate == null || displayNameTemplate.isBlank()) {
            displayNameTemplate = plugin.getConfig().getString(
                    "display.name-template",
                    plugin.getConfig().getString("esthetic.name-format", "{item_type}"));
        }
        String resolvedDisplayName = applyGradients(PlaceholderUtils.replace(displayNameTemplate, placeholders),
                context.prefixGradientColors());
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

    private void applyAttributes(ItemMeta meta, List<AttributeSettings> attributes, String pieceKey,
            Material material) {
        if (attributes == null || attributes.isEmpty())
            return;

        // First restore all default attributes of the material
        for (org.bukkit.inventory.EquipmentSlot defaultSlot : org.bukkit.inventory.EquipmentSlot.values()) {
            com.google.common.collect.Multimap<Attribute, AttributeModifier> defaultModifiers = material
                    .getDefaultAttributeModifiers(defaultSlot);
            for (java.util.Map.Entry<Attribute, AttributeModifier> entry : defaultModifiers.entries()) {
                meta.addAttributeModifier(entry.getKey(), entry.getValue());
            }
        }
        for (AttributeSettings attrConfig : attributes) {
            String attrName = attrConfig.attribute().toUpperCase(java.util.Locale.ROOT);
            Attribute attribute = null;
            try {
                attribute = Attribute.valueOf(attrName);
            } catch (IllegalArgumentException e1) {
                try {
                    attribute = Attribute.valueOf("GENERIC_" + attrName);
                } catch (IllegalArgumentException e2) {
                    plugin.getLogger().warning("Invalid attribute configuration: " + attrConfig.attribute());
                    continue;
                }
            }

            try {
                String opName = attrConfig.operation().toUpperCase(java.util.Locale.ROOT);
                AttributeModifier.Operation operation = null;
                try {
                    operation = AttributeModifier.Operation.valueOf(opName);
                } catch (IllegalArgumentException e) {
                    // Try mapping common legacy/new names
                    String mappedOp = switch (opName) {
                        case "ADD_NUMBER", "ADDITION" -> "ADD_VALUE";
                        case "ADD_SCALAR", "MULTIPLY_BASE" -> "ADD_MULTIPLIED_BASE";
                        case "MULTIPLY_SCALAR_1", "MULTIPLY_TOTAL" -> "ADD_MULTIPLIED_TOTAL";
                        default -> null;
                    };
                    if (mappedOp != null) {
                        try {
                            operation = AttributeModifier.Operation.valueOf(mappedOp);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                if (operation == null) {
                    java.util.StringJoiner joiner = new java.util.StringJoiner(", ");
                    for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
                        joiner.add(op.name());
                    }
                    throw new IllegalArgumentException(
                            "Unknown operation: " + opName + ". Valid names: " + joiner.toString());
                }

                EquipmentSlotGroup slotGroup = parseSlotGroup(attrConfig.slot(), pieceKey);

                // Filter attributes to only apply to the relevant piece
                if (pieceKey != null && attrConfig.slot() != null
                        && !attrConfig.slot().equalsIgnoreCase("ALL")
                        && !attrConfig.slot().equalsIgnoreCase("ANY")) {
                    if (!slotGroup.equals(parseSlotFromPiece(pieceKey))) {
                        continue;
                    }
                }

                NamespacedKey key = new NamespacedKey(plugin,
                        "custom_attr_" + UUID.randomUUID().toString().substring(0, 8));
                AttributeModifier modifier = new AttributeModifier(key, attrConfig.amount(), operation, slotGroup);
                meta.addAttributeModifier(attribute, modifier);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(e.getMessage());
            }
        }
    }

    private EquipmentSlotGroup parseSlotGroup(String rawSlot, String pieceKey) {
        if (rawSlot == null || rawSlot.isBlank() || rawSlot.equalsIgnoreCase("ALL")
                || rawSlot.equalsIgnoreCase("ANY")) {
            if (pieceKey != null) {
                return parseSlotFromPiece(pieceKey);
            }
            return EquipmentSlotGroup.ANY;
        }

        String slotUpper = rawSlot.toUpperCase(java.util.Locale.ROOT);
        // Map common intuitive aliases to official group names
        String mappedSlot = switch (slotUpper) {
            case "HELMET" -> "HEAD";
            case "CHESTPLATE" -> "CHEST";
            case "LEGGINGS" -> "LEGS";
            case "BOOTS" -> "FEET";
            default -> slotUpper;
        };

        try {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(mappedSlot);
            return group != null ? group : EquipmentSlotGroup.ANY;
        } catch (Exception e) {
            return EquipmentSlotGroup.ANY;
        }
    }

    private EquipmentSlotGroup parseSlotFromPiece(String pieceKey) {
        String key = pieceKey.toUpperCase(java.util.Locale.ROOT);
        return switch (key) {
            case "HELMET" -> EquipmentSlotGroup.HEAD;
            case "CHESTPLATE" -> EquipmentSlotGroup.CHEST;
            case "LEGGINGS" -> EquipmentSlotGroup.LEGS;
            case "BOOTS" -> EquipmentSlotGroup.FEET;
            case "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "TOOLS" -> EquipmentSlotGroup.MAINHAND;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private void applyPotionEffects(ItemMeta meta, List<PotionEffectSettings> effects) {
        if (effects == null || effects.isEmpty())
            return;
        if (meta instanceof PotionMeta potionMeta) {
            int r = 0, g = 0, b = 0, count = 0;
            for (PotionEffectSettings eff : effects) {
                try {
                    NamespacedKey key = NamespacedKey.minecraft(eff.type().toLowerCase(java.util.Locale.ROOT));
                    PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(key);
                    if (type != null) {
                        PotionEffect potionEffect = new PotionEffect(type, eff.durationTicks(), eff.amplifier());
                        potionMeta.addCustomEffect(potionEffect, true);

                        // Mix colors based on effects
                        Color effectColor = type.getColor();
                        r += effectColor.getRed();
                        g += effectColor.getGreen();
                        b += effectColor.getBlue();
                        count++;
                    } else {
                        plugin.getLogger().warning("Invalid potion effect type: " + eff.type());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error applying potion effect: " + eff.type());
                }
            }

            // Apply the blended color
            if (count > 0) {
                potionMeta.setColor(Color.fromRGB(r / count, g / count, b / count));
            }
        }
    }

    private Map<String, Integer> applyEnchantments(ItemMeta meta, Map<String, EnchantLevelRule> source, boolean glow,
            int level) {
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

    private List<Component> buildLore(ItemDefinition definition, ItemBuildContext context,
            Map<String, String> placeholders, Map<String, Integer> enchantments) {
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
                plugin.getConfig().getString("esthetic.enchant-format", "{enchant_name} {level}"));
        boolean useRomanNumerals = plugin.settings().useRomanNumerals();
        boolean useRarity = plugin.getConfig().getBoolean(
                "settings.use-rarity",
                plugin.getConfig().getBoolean("settings.use-raritys", true));
        List<Component> enchantLines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            Map<String, String> enchantPlaceholders = new LinkedHashMap<>(placeholders);
            enchantPlaceholders.put("enchant_name", languageManager.enchantName(entry.getKey()));
            enchantPlaceholders.put("level", TextUtils.formatLevel(entry.getValue(), useRomanNumerals));
            String resolvedEnchantLine = applyGradients(PlaceholderUtils.replace(enchantTemplate, enchantPlaceholders),
                    context.prefixGradientColors());
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
            String resolvedLine = applyGradients(PlaceholderUtils.replace(rawLine, placeholders),
                    context.prefixGradientColors());
            lore.add(TextUtils.toItemComponent(resolvedLine));
        }
        return lore;
    }

    private Map<String, String> basePlaceholders(Material material, ItemDefinition definition, ItemBuildContext context,
            String pieceKey) {
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
        placeholders.put("item_id",
                definition.itemIdentifier() == null ? definition.id() : definition.itemIdentifier());
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
