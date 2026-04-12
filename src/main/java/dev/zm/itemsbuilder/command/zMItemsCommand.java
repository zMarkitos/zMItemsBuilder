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
            default -> {
                sender.sendMessage(plugin.language().message("usage"));
                yield true;
            }
        };
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
            gradientColors
        );
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
                "dropped", String.valueOf(leftovers.size())
            )
        ));
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
            sender.sendMessage(plugin.language().message("info-cmd", Map.of("cmd", String.valueOf(meta.getCustomModelData()))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "reload", "material", "info"), args[0]);
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return filter(plugin.itemRegistry().getBundleIds(), args[1]);
        }
        if (args.length == 2 && "material".equalsIgnoreCase(args[0])) {
            return filter(MATERIAL_SUGGESTIONS, args[1]);
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
}
