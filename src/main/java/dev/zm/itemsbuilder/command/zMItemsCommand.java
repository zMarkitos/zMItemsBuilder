package dev.zm.itemsbuilder.command;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.builder.model.ItemBundleDefinition;
import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import dev.zm.itemsbuilder.util.ColorUtils;
import dev.zm.itemsbuilder.util.ItemEffectsStore;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.util.ItemResolver;
import dev.zm.itemsbuilder.util.LoreCopyWriter;
import dev.zm.itemsbuilder.util.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class zMItemsCommand implements CommandExecutor, TabCompleter {

    private final zMItemsBuilder plugin;
    private static final List<String> MATERIAL_SUGGESTIONS = buildMaterialSuggestions();
    private static final List<String> LORE_SUB_ACTIONS = List.of("add", "remove", "set", "reset", "copy");

    public zMItemsCommand(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.language().message("usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "create" -> handleCreate(sender, args);
            case "reload" -> handleReload(sender);
            case "material" -> handleMaterial(sender, args);
            case "info" -> handleInfo(sender);
            case "lore" -> handleLore(sender, args);
            default -> {
                sender.sendMessage(plugin.language().message("usage"));
                yield true;
            }
        };
    }

    private boolean handleLore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.lore")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-lore"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> handleLoreAdd(player, inHand, args);
            case "remove" -> handleLoreRemove(player, inHand, args);
            case "set" -> handleLoreSet(player, inHand, args);
            case "reset" -> handleLoreReset(player, inHand);
            case "copy" -> handleLoreCopy(player, inHand, args);
            default -> {
                sender.sendMessage(plugin.language().message("usage-lore"));
                yield true;
            }
        };
    }

    private boolean handleLoreAdd(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.language().message("usage-lore-add"));
            return true;
        }

        String raw = joinFrom(args, 2);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return true;

        List<net.kyori.adventure.text.Component> lore = safeGetLore(meta);
        lore.add(TextUtils.toItemComponent(raw));
        meta.lore(lore);
        item.setItemMeta(meta);

        player.sendMessage(plugin.language().message("lore-add-success",
                Map.of("line", String.valueOf(lore.size()), "text", raw)));
        return true;
    }

    private boolean handleLoreRemove(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.language().message("usage-lore-remove"));
            return true;
        }

        int lineNumber = parsePositiveInt(args[2]);
        if (lineNumber < 1) {
            player.sendMessage(plugin.language().message("lore-invalid-line",
                    Map.of("line", args[2])));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return true;

        List<net.kyori.adventure.text.Component> lore = safeGetLore(meta);
        if (lineNumber > lore.size()) {
            player.sendMessage(plugin.language().message("lore-line-out-of-range",
                    Map.of("line", String.valueOf(lineNumber), "size", String.valueOf(lore.size()))));
            return true;
        }

        lore.remove(lineNumber - 1);
        meta.lore(lore);
        item.setItemMeta(meta);

        player.sendMessage(plugin.language().message("lore-remove-success",
                Map.of("line", String.valueOf(lineNumber))));
        return true;
    }

    private boolean handleLoreSet(Player player, ItemStack item, String[] args) {
        if (args.length < 4) {
            player.sendMessage(plugin.language().message("usage-lore-set"));
            return true;
        }

        int lineNumber = parsePositiveInt(args[2]);
        if (lineNumber < 1) {
            player.sendMessage(plugin.language().message("lore-invalid-line",
                    Map.of("line", args[2])));
            return true;
        }

        String raw = joinFrom(args, 3);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return true;

        List<net.kyori.adventure.text.Component> lore = safeGetLore(meta);

        // Expand lore if the target line is beyond the current size
        while (lore.size() < lineNumber) {
            lore.add(net.kyori.adventure.text.Component.empty());
        }
        lore.set(lineNumber - 1, TextUtils.toItemComponent(raw));
        meta.lore(lore);
        item.setItemMeta(meta);

        player.sendMessage(plugin.language().message("lore-set-success",
                Map.of("line", String.valueOf(lineNumber), "text", raw)));
        return true;
    }

    private boolean handleLoreReset(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return true;

        meta.lore(List.of());
        item.setItemMeta(meta);

        player.sendMessage(plugin.language().message("lore-reset-success"));
        return true;
    }

    private boolean handleLoreCopy(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.language().message("usage-lore-copy"));
            return true;
        }

        String itemId = args[2].toLowerCase(Locale.ROOT);
        String kitId = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : null;

        LoreCopyWriter.CopyResult result = LoreCopyWriter.copyItemToConfig(plugin, item, itemId, kitId);

        if (result == LoreCopyWriter.CopyResult.EXISTS) {
            player.sendMessage(plugin.language().message("lore-copy-exists", Map.of("key", itemId)));
            return true;
        }

        if (result == LoreCopyWriter.CopyResult.FAILED) {
            player.sendMessage(plugin.language().message("lore-copy-failed", Map.of("key", itemId)));
            return true;
        }

        plugin.reloadPluginState();

        ItemMeta meta = item.getItemMeta();
        int lines = LoreCopyWriter.getRawLore(meta).size();
        player.sendMessage(plugin.language().message("lore-copy-created",
                Map.of("key", itemId, "lines", String.valueOf(lines))));

        if (kitId != null) {
            player.sendMessage(plugin.language().message("lore-copy-kit",
                    Map.of("kit", kitId, "key", itemId)));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAnyPermission(sender, "zmitemsbuilder.reload", "zmkits.reload")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        plugin.reloadPluginState();
        sender.sendMessage(plugin.language().message("reloaded"));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!hasAnyPermission(sender, "zmitemsbuilder.create", "zmkits.create")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-create"));
            return true;
        }

        String kitId = args[1].toLowerCase(Locale.ROOT);
        Optional<ItemBundleDefinition> optionalKit = plugin.itemRegistry().getBundle(kitId);
        if (optionalKit.isEmpty()) {
            sender.sendMessage(plugin.language().message("kit-not-found", Map.of("kit", kitId)));
            return true;
        }

        String prefixInput = String.join(" ", List.of(args).subList(2, args.length));
        Optional<String> primaryHex = ColorUtils.extractHex(prefixInput);
        if (primaryHex.isEmpty()) {
            sender.sendMessage(plugin.language().message("invalid-hex-prefix"));
            return true;
        }
        PluginSettings settings = plugin.settings();
        String secondaryHex = ColorUtils.secondaryFrom(primaryHex.get(), settings.secondaryColorMode());
        List<String> gradientColors = ColorUtils.extractHexColors(prefixInput);
        if (gradientColors.isEmpty()) {
            gradientColors = List.of(primaryHex.get());
        }
        String prefixMiniMessage = TextUtils.toMiniMessage(prefixInput);
        if (prefixMiniMessage.isBlank()) {
            prefixMiniMessage = "<#" + primaryHex.get() + ">" + kitId.toUpperCase(Locale.ROOT);
        }

        List<ItemStack> builtItems = plugin.itemBundleBuilder().build(
                optionalKit.get(),
                prefixInput,
                prefixMiniMessage,
                primaryHex.get(),
                secondaryHex,
                gradientColors);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(builtItems.toArray(new ItemStack[0]));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        PluginSettings.SoundSettings sound = settings.soundSettings();
        if (sound.enabled()) {
            player.playSound(player.getLocation(), sound.type(), sound.volume(), sound.pitch());
        }

        player.sendMessage(plugin.language().message(
                "success",
                Map.of(
                        "kit", kitId,
                        "count", String.valueOf(builtItems.size()),
                        "dropped", String.valueOf(leftovers.size()))));
        return true;
    }

    private boolean handleMaterial(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.material")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-material"));
            return true;
        }

        Optional<Material> targetMaterial = ItemResolver.material(args[1]);
        if (targetMaterial.isEmpty() || targetMaterial.get().isAir()) {
            sender.sendMessage(plugin.language().message("invalid-material", Map.of("material", args[1])));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        Material material = targetMaterial.get();
        ItemStack updated = changeMaterialPreservingData(inHand, material);
        player.getInventory().setItemInMainHand(updated);
        sender.sendMessage(plugin.language().message("material-updated", Map.of("material", material.name())));
        return true;
    }

    private ItemStack changeMaterialPreservingData(ItemStack original, Material newMaterial) {
        String itemId = ItemIdentityStore.read(plugin, original);
        Set<ItemBehaviorFlag> flags = ItemFlagStore.read(plugin, original);
        List<PotionEffectSettings> effects = ItemEffectsStore.read(plugin, original);

        ItemStack updated = original.clone();
        updated.setType(newMaterial);

        ItemMeta originalMeta = original.getItemMeta();
        if (originalMeta != null) {
            ItemMeta converted = Bukkit.getItemFactory().asMetaFor(originalMeta, newMaterial);
            if (converted != null) {
                updated.setItemMeta(converted);
            }
        }

        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }
        ItemIdentityStore.write(plugin, meta, itemId);
        ItemFlagStore.write(plugin, meta, flags);
        ItemEffectsStore.write(plugin, meta, effects);
        updated.setItemMeta(meta);
        return updated;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission("zmitemsbuilder.info")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String itemId = ItemIdentityStore.read(plugin, inHand);
        ItemMeta meta = inHand.getItemMeta();
        boolean hasCustomModelData = meta != null && meta.hasCustomModelData();
        String material = inHand.getType().name();

        if (itemId == null && !hasCustomModelData) {
            sender.sendMessage(plugin.language().message("info-none", Map.of("material", material)));
            return true;
        }

        sender.sendMessage(plugin.language().message("info-header"));
        sender.sendMessage(plugin.language().message("info-material", Map.of("material", material)));
        if (itemId != null) {
            sender.sendMessage(plugin.language().message("info-id", Map.of("id", itemId)));
        }
        if (hasCustomModelData) {
            sender.sendMessage(
                    plugin.language().message("info-cmd", Map.of("cmd", String.valueOf(meta.getCustomModelData()))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "reload", "material", "info", "lore"), args[0]);
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return filter(plugin.itemRegistry().getBundleIds(), args[1]);
        }
        if (args.length == 2 && "material".equalsIgnoreCase(args[0])) {
            return filter(MATERIAL_SUGGESTIONS, args[1]);
        }
        if (args.length == 2 && "lore".equalsIgnoreCase(args[0])) {
            return filter(LORE_SUB_ACTIONS, args[1]);
        }
        // Context-aware lore tab completion
        if ("lore".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player))
                return Collections.emptyList();

            ItemStack inHand = player.getInventory().getItemInMainHand();
            ItemMeta meta = (inHand != null && !inHand.getType().isAir()) ? inHand.getItemMeta() : null;
            List<String> rawLore = (meta != null) ? LoreCopyWriter.getRawLore(meta) : Collections.emptyList();

            // lore <add|remove|set|reset|copy> <line>
            if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("remove") || action.equals("set")) {
                    List<String> lines = new ArrayList<>();
                    for (int i = 1; i <= rawLore.size(); i++)
                        lines.add(String.valueOf(i));
                    return filter(lines, args[2]);
                }
                return Collections.emptyList();
            }

            // lore set <line> <text...> | lore copy <id> <kit>
            if (args.length == 4) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("set")) {
                    int lineNum = parsePositiveInt(args[2]);
                    if (lineNum > 0 && lineNum <= rawLore.size()) {
                        // Suggest current text of the line (preserving hex colors)
                        return Collections.singletonList(rawLore.get(lineNum - 1));
                    }
                }
                if (action.equals("copy")) {
                    List<String> kits = new ArrayList<>(plugin.itemRegistry().getBundleIds());
                    if (args[3].isEmpty()) {
                        kits.add("<new_or_existing_kit>");
                    } else if (kits.stream().noneMatch(k -> k.equalsIgnoreCase(args[3]))) {
                        kits.add(args[3]);
                    }
                    return filter(kits, args[3]);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(Collection<String> source, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .sorted()
                .toList();
    }

    private boolean hasAnyPermission(CommandSender sender, String primary, String legacy) {
        return sender.hasPermission(primary) || sender.hasPermission(legacy);
    }

    private static List<String> buildMaterialSuggestions() {
        List<String> materials = new ArrayList<>(Material.values().length);
        Arrays.stream(Material.values())
                .filter(mat -> mat != null && !mat.isAir())
                .map(mat -> mat.name().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .forEach(materials::add);
        return List.copyOf(materials);
    }

    /** Returns a mutable copy of the item's lore list, never null. */
    private static List<net.kyori.adventure.text.Component> safeGetLore(ItemMeta meta) {
        List<net.kyori.adventure.text.Component> existing = meta.lore();
        return existing != null ? new ArrayList<>(existing) : new ArrayList<>();
    }

    /** Joins args from startIndex onwards with spaces. */
    private static String joinFrom(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex)
                sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** Parses a positive integer; returns -1 on invalid input. */
    private static int parsePositiveInt(String raw) {
        try {
            int v = Integer.parseInt(raw);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
