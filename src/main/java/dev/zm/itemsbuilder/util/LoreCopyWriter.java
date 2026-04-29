package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class LoreCopyWriter {

    // Serializes Adventure components to &-coded legacy strings, with hex support
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    // Matches a single color token: either &#RRGGBB or &[0-9a-fA-F]
    private static final Pattern COLOR_TOKEN = Pattern.compile("(?i)(&#[A-Fa-f0-9]{6}|&[0-9a-fA-F])");

    // Matches a formatting code: &[k-oK-OrR]
    private static final Pattern FORMAT_TOKEN = Pattern.compile("(?i)(&[k-oK-OrR])");

    // Matches any color or format code
    private static final Pattern ANY_CODE = Pattern.compile("(?i)(&#[A-Fa-f0-9]{6}|&[0-9a-fA-Fk-oK-OrR])");

    public enum CopyResult {
        CREATED, UPDATED, FAILED, EXISTS
    }

    private LoreCopyWriter() {
    }

    /**
     * Copies the held item into {@code config.yml} under {@code items.<itemId>}.
     * Works with ANY item, not just plugin-built ones.
     *
     * <ul>
     * <li>If {@code items.<itemId>} already exists → returns EXISTS.</li>
     * <li>Otherwise → a full compatible entry is generated.</li>
     * <li>The two most frequent hex colors are replaced with
     * {@code {primary_color}} and {@code {secondary_color}} placeholders.</li>
     * <li>Character-by-character colored sequences become
     * {@code {gradient:Text}}.</li>
     * <li>If {@code kitId} is given, the item is added to that kit.</li>
     * </ul>
     */
    public static CopyResult copyItemToConfig(zMItemsBuilder plugin,
            ItemStack item, String itemId, String kitId) {

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        ItemMeta meta = item.getItemMeta();

        // 1. Extract raw &-coded lore and name from Adventure
        List<String> rawLore = extractRawLore(meta);
        String rawName = extractName(meta);

        // 2. Build combined text for color frequency analysis
        List<String> allText = new ArrayList<>(rawLore);
        if (rawName != null)
            allText.add(rawName);

        // 3. Detect the two dominant hex colors
        String[] dominant = detectDominantColors(allText);
        String primaryHex = dominant[0];
        String secondaryHex = dominant[1];

        // 4. Process each line: compress gradients, replace colors with placeholders
        rawLore = processLines(rawLore, primaryHex, secondaryHex);
        rawName = processLine(rawName, primaryHex, secondaryHex);

        String itemPath = "items." + itemId;
        if (config.isConfigurationSection(itemPath)) {
            return CopyResult.EXISTS;
        }

        buildFullEntry(plugin, config, itemPath, item, meta, rawLore, rawName);

        if (kitId != null && !kitId.isBlank()) {
            appendToKit(config, kitId, itemId);
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config.yml after lore copy: " + e.getMessage());
            return CopyResult.FAILED;
        }

        return CopyResult.CREATED;
    }

    /**
     * Returns the &-coded lore strings of {@code meta}, or an empty list if none.
     */
    public static List<String> getRawLore(ItemMeta meta) {
        return extractRawLore(meta);
    }

    /** Returns the internal source_key PDC tag, or null. */
    public static String resolveConfigKey(zMItemsBuilder plugin, ItemStack item) {
        return ItemIdentityStore.readSourceKey(plugin, item);
    }

    /**
     * Processes a list of raw legacy-coded lines:
     * 1. Compress character-by-character gradient sequences → {gradient:text}
     * 2. Replace primary/secondary hex with placeholders
     * 3. Strip all remaining hex codes that are not covered by placeholders
     */
    private static List<String> processLines(List<String> lines, String primary, String secondary) {
        if (lines == null || lines.isEmpty())
            return lines == null ? new ArrayList<>() : lines;
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(processLine(line, primary, secondary));
        }
        return result;
    }

    private static String processLine(String text, String primary, String secondary) {
        if (text == null)
            return null;

        // Step 1: compress character-by-character gradients into {gradient:...}
        text = compressGradients(text);

        // Step 2: replace primary/secondary hex with plugin placeholders
        // (case-insensitive)
        if (primary != null) {
            text = text.replaceAll("(?i)&#" + Pattern.quote(primary), "{primary_color}");
        }
        if (secondary != null) {
            text = text.replaceAll("(?i)&#" + Pattern.quote(secondary), "{secondary_color}");
        }

        // Step 3: strip ALL remaining hex codes (&#RRGGBB) that weren't placeholdered
        // We must NOT strip inside {gradient:...} or placeholder text, only bare codes
        text = stripRemainingHexOutsidePlaceholders(text);

        // Step 4: strip dangling vanilla color/format codes (those immediately
        // before another code, a space, or end-of-string) – keeps intentional ones
        text = stripDanglingCodes(text);

        return text;
    }

    /**
     * Strips bare &#RRGGBB codes that appear OUTSIDE of {gradient:...} or
     * {placeholder} blocks. This is done by tokenizing the string into
     * "inside-braces" and "outside-braces" segments.
     */
    private static String stripRemainingHexOutsidePlaceholders(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                // Find the matching closing brace
                int close = text.indexOf('}', i + 1);
                if (close != -1) {
                    // Preserve the placeholder/gradient block verbatim
                    sb.append(text, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }
            // We are outside a placeholder block — strip hex codes here
            if (c == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#') {
                String candidate = text.substring(i + 2, i + 8);
                if (candidate.matches("[A-Fa-f0-9]{6}")) {
                    // Skip the full &#RRGGBB token
                    i += 8;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Strips color/format codes that are "dangling" – i.e. immediately followed by
     * another code, a space, end-of-string, or a placeholder opening brace.
     * Only operates on standard legacy codes (&[0-9a-fk-or]), not hex (already
     * stripped).
     */
    private static String stripDanglingCodes(String text) {
        // Remove codes followed by: another &-code, space, end-of-string, or {
        return text.replaceAll("(?i)(&[0-9a-fA-Fk-oK-OrR])(?=\\s|$|&|\\{)", "");
    }

    /**
     * Detects runs of 3+ consecutive "color + 1 char" pairs (gradient sequences)
     * and compresses them into {gradient:[formats]plainText}.
     *
     * Example: &#FF0000H&#EE0000e&#DD0000l&#CC0000l&#BB0000o → {gradient:Hello}
     */
    private static String compressGradients(String text) {
        if (text == null)
            return null;

        // A "colored char" = one color code + optional format codes + exactly one non-&
        // non-space char
        String coloredCharRegex = "(?:&#[A-Fa-f0-9]{6}|&[0-9a-fA-F])(?:&[k-oK-OrR])*[^&\\s]";
        Pattern sequencePattern = Pattern.compile("((?:" + coloredCharRegex + "){3,})");

        Matcher matcher = sequencePattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group(1);

            // Extract the visible characters only
            String rawText = match.replaceAll("(?i)&#[A-Fa-f0-9]{6}|&[0-9a-fA-Fk-oK-OrR]", "");

            // Collect persistent formats used in the sequence
            StringBuilder formats = new StringBuilder();
            if (Pattern.compile("(?i)&l").matcher(match).find())
                formats.append("&l");
            if (Pattern.compile("(?i)&n").matcher(match).find())
                formats.append("&n");
            if (Pattern.compile("(?i)&o").matcher(match).find())
                formats.append("&o");
            if (Pattern.compile("(?i)&m").matcher(match).find())
                formats.append("&m");
            if (Pattern.compile("(?i)&k").matcher(match).find())
                formats.append("&k");

            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement("{gradient:" + formats + rawText + "}"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Detects the two most frequent &#RRGGBB hex colors across all provided lines.
     * Ignores colors that appear only inside {gradient:...} blocks (already
     * compressed).
     *
     * @return String[2]: [0] = primary (most frequent), [1] = secondary. Either may
     *         be null.
     */
    private static String[] detectDominantColors(List<String> lines) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        Pattern hexPattern = Pattern.compile("(?i)&#([A-Fa-f0-9]{6})");

        for (String line : lines) {
            if (line == null)
                continue;
            Matcher m = hexPattern.matcher(line);
            while (m.find()) {
                String hex = m.group(1).toUpperCase();
                freq.merge(hex, 1, Integer::sum);
            }
        }

        String primary = null, secondary = null;
        int primaryFreq = 0, secondaryFreq = 0;

        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            int count = e.getValue();
            if (count > primaryFreq) {
                secondary = primary;
                secondaryFreq = primaryFreq;
                primary = e.getKey();
                primaryFreq = count;
            } else if (count > secondaryFreq) {
                secondary = e.getKey();
                secondaryFreq = count;
            }
        }
        return new String[] { primary, secondary };
    }

    /** Writes a full compatible item entry to config under {@code path}. */
    private static void buildFullEntry(zMItemsBuilder plugin, FileConfiguration config,
            String path, ItemStack item, ItemMeta meta, List<String> rawLore, String rawName) {

        config.set(path + ".mode", "single");
        String matName = item.getType().name();
        config.set(path + ".material", matName);

        if (!isKnownItemType(matName)) {
            config.set(path + ".display-type", "\"Test\"");
        }

        if (rawName != null && !rawName.isBlank()) {
            config.set(path + ".name", "&f{item_type} &8▸ {prefix_kit}");
        }

        if (!rawLore.isEmpty()) {
            config.set(path + ".lore", rawLore);
        }

        if (meta == null)
            return;

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (!enchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                config.set(path + ".enchants." + e.getKey().getKey().getKey(), e.getValue());
            }
        }

        // Bukkit item-flags
        Set<ItemFlag> flags = meta.getItemFlags();
        if (!flags.isEmpty()) {
            List<String> flagNames = new ArrayList<>(flags.size());
            for (ItemFlag flag : flags) {
                flagNames.add(flag.name());
            }
            config.set(path + ".item-flags", flagNames);
        }

        // Behavior-flags stored in PDC (only present on plugin-built items)
        Set<ItemBehaviorFlag> behaviorFlags = ItemFlagStore.read(plugin, item);
        if (!behaviorFlags.isEmpty()) {
            List<String> bfNames = new ArrayList<>(behaviorFlags.size());
            for (ItemBehaviorFlag bf : behaviorFlags) {
                bfNames.add(bf.name());
            }
            config.set(path + ".behavior-flags", bfNames);
        }

        if (meta.hasCustomModelData()) {
            config.set(path + ".custom-model-data", meta.getCustomModelData());
        }

        if (meta.isUnbreakable()) {
            config.set(path + ".unbreakable", true);
        }
    }

    /**
     * Creates {@code kits.<kitId>} if it doesn't exist, then ensures {@code itemId}
     * is present in its {@code items} list.
     */
    private static void appendToKit(FileConfiguration config, String kitId, String itemId) {
        String kitPath = "kits." + kitId;

        if (!config.isConfigurationSection(kitPath)) {
            config.set(kitPath + ".rarity", itemId);
            config.set(kitPath + ".items", List.of(itemId));
            return;
        }

        ConfigurationSection kitSection = config.getConfigurationSection(kitPath);
        if (kitSection == null)
            return;

        if (!kitSection.contains("rarity")) {
            config.set(kitPath + ".rarity", itemId);
        }

        List<String> items = new ArrayList<>(kitSection.getStringList("items"));
        if (!items.contains(itemId)) {
            items.add(itemId);
            config.set(kitPath + ".items", items);
        }
    }

    private static List<String> extractRawLore(ItemMeta meta) {
        if (meta == null)
            return new ArrayList<>();
        List<Component> lines = meta.lore();
        if (lines == null || lines.isEmpty())
            return new ArrayList<>();
        List<String> raw = new ArrayList<>(lines.size());
        for (Component line : lines) {
            raw.add(LEGACY.serialize(line));
        }
        return raw;
    }

    private static String extractName(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName())
            return null;
        Component nameComp = meta.displayName();
        if (nameComp == null)
            return null;
        return LEGACY.serialize(nameComp);
    }

    private static boolean isKnownItemType(String materialName) {
        String mat = materialName.toUpperCase(java.util.Locale.ROOT);
        return mat.endsWith("_HELMET") || mat.endsWith("_CHESTPLATE")
                || mat.endsWith("_LEGGINGS") || mat.endsWith("_BOOTS")
                || mat.endsWith("_SWORD") || mat.endsWith("_PICKAXE")
                || mat.endsWith("_AXE") || mat.endsWith("_SHOVEL") || mat.endsWith("_HOE")
                || mat.equals("BOW") || mat.equals("CROSSBOW") || mat.equals("TRIDENT");
    }
}