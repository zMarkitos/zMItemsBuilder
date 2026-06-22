package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.config.LanguageManager;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemEnchantLoreManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final zMItemsBuilder plugin;
    private final LanguageManager languageManager;

    public ItemEnchantLoreManager(zMItemsBuilder plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    public boolean applyEnchantChange(ItemStack item, String enchantKey, int level) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Optional<Enchantment> optionalEnchant = ItemResolver.enchantment(enchantKey);
        if (optionalEnchant.isEmpty()) {
            return false;
        }

        Enchantment enchantment = optionalEnchant.get();
        String normalizedKey = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
        int safeLevel = Math.max(0, level);

        if (safeLevel == 0) {
            meta.removeEnchant(enchantment);
        } else {
            meta.addEnchant(enchantment, safeLevel, true);
        }

        if (resolveCustomSourceKey(item) == null) {
            item.setItemMeta(meta);
            return true;
        }

        List<Component> lore = safeLore(meta);
        boolean loreChanged = reconcileSingleEnchantLine(item, meta, lore, normalizedKey, safeLevel);
        if (loreChanged) {
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return true;
    }

    public boolean syncEnchantLore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        if (resolveCustomSourceKey(item) == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getEnchants().isEmpty()) {
            return false;
        }

        List<Component> lore = safeLore(meta);
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment == null) {
                continue;
            }
            String normalizedKey = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
            if (reconcileSingleEnchantLine(item, meta, lore, normalizedKey, Math.max(0, entry.getValue()))) {
                changed = true;
            }
        }

        if (changed) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return changed;
    }

    private boolean reconcileSingleEnchantLine(ItemStack item, ItemMeta meta, List<Component> lore, String enchantKey,
            int level) {
        if (lore == null) {
            return false;
        }

        String enchantDisplayName = normalizePlain(languageManager.enchantName(enchantKey));
        List<Integer> matchingIndices = findMatchingIndices(lore, enchantDisplayName, item);
        boolean changed = false;

        if (level == 0) {
            for (int i = matchingIndices.size() - 1; i >= 0; i--) {
                lore.remove((int) matchingIndices.get(i));
                changed = true;
            }
            return changed;
        }

        if (!matchingIndices.isEmpty()) {
            int firstIndex = matchingIndices.get(0);
            Component resolvedLine = preserveExistingEnchantColors(
                    lore.get(firstIndex),
                    languageManager.enchantName(enchantKey),
                    enchantKey,
                    level);
            if (resolvedLine == null) {
                resolvedLine = replaceExistingEnchantLevel(lore.get(firstIndex), level);
            }
            if (resolvedLine == null) {
                resolvedLine = renderEnchantLine(meta, enchantKey, level);
            }
            lore.set(firstIndex, resolvedLine);
            changed = true;
            for (int i = matchingIndices.size() - 1; i >= 1; i--) {
                lore.remove((int) matchingIndices.get(i));
            }
            return changed;
        }

        int insertIndex = determineInsertIndex(item, meta, lore);
        insertIndex = Math.max(0, Math.min(insertIndex, lore.size()));
        Component resolvedLine = renderEnchantLine(meta, enchantKey, level);
        lore.add(insertIndex, resolvedLine);
        return true;
    }

    private Component renderEnchantLine(ItemMeta meta, String enchantKey, int level) {
        String enchantTemplate = plugin.getConfig().getString(
                "display.enchant-format",
                plugin.getConfig().getString("esthetic.enchant-format", "{enchant_name} {level}"));
        String primaryHex = resolvePrefixPrimaryHex(meta);
        String secondaryHex = resolvePrefixSecondaryHex(meta);

        Map<String, String> placeholders = Map.of(
                "enchant_name", languageManager.enchantName(enchantKey),
                "level", TextUtils.formatLevel(level, plugin.settings().useRomanNumerals()),
                "primary_color", "<#" + primaryHex + ">",
                "secondary_color", "<#" + secondaryHex + ">",
                "color_principal", "<#" + primaryHex + ">",
                "color_secundario", "<#" + secondaryHex + ">");

        String replaced = PlaceholderUtils.replace(enchantTemplate, placeholders);
        replaced = PlaceholderUtils.replaceGradients(replaced, resolvePrefixGradientColors(meta));
        return TextUtils.toItemComponent(replaced);
    }

    private Component preserveExistingEnchantColors(Component existingLine, String currentEnchantName, String enchantKey,
            int newLevel) {
        if (existingLine == null || currentEnchantName == null || currentEnchantName.isBlank()) {
            return null;
        }

        String raw = LEGACY_HEX.serialize(existingLine);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        TextView view = buildTextView(raw);
        String plainLower = view.plain.toLowerCase(Locale.ROOT);
        String enchantLower = normalizePlain(currentEnchantName);
        int nameStart = plainLower.indexOf(enchantLower);
        if (nameStart < 0) {
            return null;
        }
        int nameEnd = nameStart + enchantLower.length();
        int levelStart = nameEnd;
        while (levelStart < view.plain.length() && Character.isWhitespace(view.plain.charAt(levelStart))) {
            levelStart++;
        }
        if (levelStart >= view.plain.length()) {
            return null;
        }

        int rawNameStart = view.rawIndexAt(nameStart);
        int rawNameEnd = view.rawIndexAt(nameEnd);
        int rawLevelStart = view.rawIndexAt(levelStart);
        int rawLevelEnd = view.rawIndexAt(view.plain.length());
        if (rawNameStart < 0 || rawNameEnd < 0 || rawLevelStart < 0 || rawLevelEnd < 0) {
            return null;
        }

        String newName = languageManager.enchantName(enchantKey);
        String newLevelText = TextUtils.formatLevel(newLevel, plugin.settings().useRomanNumerals());
        StringBuilder rebuilt = new StringBuilder(raw.length() + newName.length() + newLevelText.length());
        rebuilt.append(raw, 0, rawNameStart);
        rebuilt.append(newName);
        rebuilt.append(raw, rawNameEnd, rawLevelStart);
        rebuilt.append(newLevelText);
        rebuilt.append(raw.substring(rawLevelEnd));
        return TextUtils.toItemComponent(rebuilt.toString());
    }

    private Component replaceExistingEnchantLevel(Component existingLine, int newLevel) {
        if (existingLine == null) {
            return null;
        }

        String raw = LEGACY_HEX.serialize(existingLine);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        TextView view = buildTextView(raw);
        int levelStart = findTrailingTokenStart(view.plain);
        if (levelStart < 0) {
            return null;
        }

        int rawLevelStart = view.rawIndexAt(levelStart);
        int rawLevelEnd = view.rawIndexAt(view.plain.length());
        if (rawLevelStart < 0 || rawLevelEnd < 0) {
            return null;
        }

        String newLevelText = TextUtils.formatLevel(newLevel, plugin.settings().useRomanNumerals());
        StringBuilder rebuilt = new StringBuilder(raw.length() + newLevelText.length());
        rebuilt.append(raw, 0, rawLevelStart);
        rebuilt.append(newLevelText);
        rebuilt.append(raw.substring(rawLevelEnd));
        return TextUtils.toItemComponent(rebuilt.toString());
    }

    private int determineInsertIndex(ItemStack item, ItemMeta meta, List<Component> lore) {
        int templateIndex = resolveTemplateInsertIndex(item);
        int lastEnchantIndex = -1;
        Set<String> currentEnchantNames = currentEnchantNames(meta);
        if (currentEnchantNames.isEmpty()) {
            return templateIndex >= 0 ? templateIndex : lore.size();
        }

        int searchStart = Math.max(0, templateIndex);
        for (int i = 0; i < lore.size(); i++) {
            if (i < searchStart) {
                continue;
            }
            String plainLine = normalizePlain(lore.get(i));
            for (String enchantName : currentEnchantNames) {
                if (!enchantName.isBlank() && plainLine.contains(enchantName)) {
                    lastEnchantIndex = i;
                    break;
                }
            }
        }

        if (templateIndex >= 0) {
            return lastEnchantIndex >= templateIndex ? lastEnchantIndex + 1 : templateIndex;
        }
        return lastEnchantIndex >= 0 ? lastEnchantIndex + 1 : lore.size();
    }

    private int resolveTemplateInsertIndex(ItemStack item) {
        String sourceKey = resolveCustomSourceKey(item);
        if (sourceKey == null || sourceKey.isBlank()) {
            return -1;
        }

        Optional<ItemDefinition> definition = plugin.itemRegistry().getItem(sourceKey);
        if (definition.isEmpty()) {
            return -1;
        }

        List<String> template = definition.get().loreDefined()
                ? definition.get().lore()
                : plugin.getConfig().getStringList("display.lore-template");
        if (template.isEmpty()) {
            template = plugin.getConfig().getStringList("esthetic.lore-template");
        }
        if (template.isEmpty()) {
            return -1;
        }

        int index = 0;
        for (String rawLine : template) {
            if (rawLine != null && rawLine.contains("{enchants}")) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private String resolveCustomSourceKey(ItemStack item) {
        String sourceKey = ItemIdentityStore.readSourceKey(plugin, item);
        if (sourceKey != null && plugin.itemRegistry().getItem(sourceKey).isPresent()) {
            return sourceKey;
        }

        String legacyKey = ItemIdentityStore.read(plugin, item);
        if (legacyKey != null && plugin.itemRegistry().getItem(legacyKey).isPresent()) {
            return legacyKey;
        }
        return null;
    }

    private List<Integer> findMatchingIndices(List<Component> lore, String enchantDisplayName, ItemStack item) {
        if (lore.isEmpty() || enchantDisplayName.isBlank()) {
            return List.of();
        }

        int startIndex = Math.max(0, resolveTemplateInsertIndex(item));
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < lore.size(); i++) {
            if (i < startIndex) {
                continue;
            }
            String plainLine = normalizePlain(lore.get(i));
            if (plainLine.contains(enchantDisplayName)) {
                indices.add(i);
            }
        }
        if (!indices.isEmpty()) {
            return indices;
        }

        if (startIndex <= 0) {
            return indices;
        }

        for (int i = 0; i < lore.size(); i++) {
            String plainLine = normalizePlain(lore.get(i));
            if (plainLine.contains(enchantDisplayName)) {
                indices.add(i);
            }
        }
        return indices;
    }

    private Set<String> currentEnchantNames(ItemMeta meta) {
        Set<String> names = new LinkedHashSet<>();
        for (Enchantment enchantment : meta.getEnchants().keySet()) {
            if (enchantment == null) {
                continue;
            }
            names.add(normalizePlain(languageManager.enchantName(enchantment.getKey().getKey())));
        }
        return names;
    }

    private List<Component> safeLore(ItemMeta meta) {
        List<Component> existing = meta.lore();
        return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
    }

    private List<String> resolvePrefixGradientColors(ItemMeta meta) {
        LinkedHashSet<String> colors = new LinkedHashSet<>();
        collectColors(meta.displayName(), colors);

        if (colors.isEmpty()) {
            colors.add("FFFFFF");
        }
        if (colors.size() == 1) {
            String primary = colors.iterator().next();
            colors.add(ColorUtils.secondaryFrom(primary, plugin.settings().secondaryColorMode()));
        }
        return List.copyOf(colors);
    }

    private void collectColors(Component component, Set<String> colors) {
        if (component == null || colors == null) {
            return;
        }
        String serialized = LEGACY_HEX.serialize(component);
        for (String hex : ColorUtils.extractHexColors(serialized)) {
            colors.add(hex);
        }
    }

    private String resolvePrefixPrimaryHex(ItemMeta meta) {
        List<String> colors = resolvePrefixGradientColors(meta);
        return colors.isEmpty() ? "FFFFFF" : colors.get(0);
    }

    private String resolvePrefixSecondaryHex(ItemMeta meta) {
        List<String> colors = resolvePrefixGradientColors(meta);
        if (colors.size() >= 2) {
            return colors.get(1);
        }
        String primary = colors.isEmpty() ? "FFFFFF" : colors.get(0);
        return ColorUtils.secondaryFrom(primary, plugin.settings().secondaryColorMode());
    }

    private String normalizePlain(Component component) {
        if (component == null) {
            return "";
        }
        return normalizePlain(PLAIN.serialize(component));
    }

    private String normalizePlain(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private TextView buildTextView(String raw) {
        StringBuilder plain = new StringBuilder(raw.length());
        List<Integer> rawIndices = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '#') {
                    if (i + 7 < raw.length()) {
                        String hex = raw.substring(i + 2, i + 8);
                        if (hex.matches("[A-Fa-f0-9]{6}")) {
                            i += 7;
                            continue;
                        }
                    }
                }
                if (isLegacyCode(next)) {
                    i++;
                    continue;
                }
            }
            plain.append(c);
            rawIndices.add(i);
        }
        rawIndices.add(raw.length());
        return new TextView(plain.toString(), rawIndices);
    }

    private int findTrailingTokenStart(String plain) {
        if (plain == null || plain.isBlank()) {
            return -1;
        }

        int end = plain.length();
        while (end > 0 && Character.isWhitespace(plain.charAt(end - 1))) {
            end--;
        }
        if (end <= 0) {
            return -1;
        }

        int start = end;
        while (start > 0 && !Character.isWhitespace(plain.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    private boolean isLegacyCode(char code) {
        char lower = Character.toLowerCase(code);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || lower == 'k'
                || lower == 'l'
                || lower == 'm'
                || lower == 'n'
                || lower == 'o'
                || lower == 'r';
    }

    private static final class TextView {
        private final String plain;
        private final List<Integer> rawIndices;

        private TextView(String plain, List<Integer> rawIndices) {
            this.plain = plain;
            this.rawIndices = rawIndices;
        }

        private int rawIndexAt(int plainIndex) {
            if (plainIndex < 0 || plainIndex >= rawIndices.size()) {
                return -1;
            }
            return rawIndices.get(plainIndex);
        }
    }
}
