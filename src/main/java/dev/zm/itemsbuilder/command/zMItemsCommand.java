package dev.zm.itemsbuilder.command;

import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.builder.model.ItemBundleDefinition;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.util.ColorUtils;
import dev.zm.itemsbuilder.util.ItemEffectsStore;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.util.ItemResolver;
import dev.zm.itemsbuilder.util.LoreCopyWriter;
import dev.zm.itemsbuilder.util.SavedItemStore;
import dev.zm.itemsbuilder.util.TextUtils;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class zMItemsCommand implements CommandExecutor, TabCompleter, Listener {

    private final zMItemsBuilder plugin;
    private static final List<String> MATERIAL_SUGGESTIONS = buildMaterialSuggestions();
    private static final List<String> LORE_SUB_ACTIONS = List.of("add", "remove", "set", "reset", "copy");
    private static final List<String> ITEM_SUB_ACTIONS = List.of("save", "show", "give", "remove", "update");
    private static final int SAVED_ITEMS_GUI_SIZE = 54;
    private static final List<Integer> SAVED_ITEMS_CONTENT_SLOTS = buildSavedItemsContentSlots();
    private static final int SAVED_ITEMS_PREV_SLOT = 45;
    private static final int SAVED_ITEMS_CLOSE_SLOT = 49;
    private static final int SAVED_ITEMS_NEXT_SLOT = 53;

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
            case "item" -> handleItem(sender, args);
            default -> {
                sender.sendMessage(plugin.language().message("usage"));
                yield true;
            }
        };
    }

    private boolean handleItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.item")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-item"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "save" -> handleItemSave(sender, args);
            case "show" -> handleItemShow(sender);
            case "give" -> handleItemGive(sender, args);
            case "remove" -> handleItemRemove(sender, args);
            case "update" -> handleItemUpdate(sender, args);
            default -> {
                sender.sendMessage(plugin.language().message("usage-item"));
                yield true;
            }
        };
    }

    private boolean handleItemSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-item-save"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String itemKey = plugin.savedItemStore().normalizeKey(args[2]);
        SavedItemStore.SaveResult result = plugin.savedItemStore().saveItem(itemKey, inHand.clone());
        if (result == SavedItemStore.SaveResult.INVALID) {
            sender.sendMessage(plugin.language().message("item-invalid-name", Map.of("name", args[2])));
            return true;
        }
        if (result == SavedItemStore.SaveResult.FAILED) {
            sender.sendMessage(plugin.language().message("item-save-failed", Map.of("name", itemKey)));
            return true;
        }

        String messageKey = result == SavedItemStore.SaveResult.CREATED ? "item-saved" : "item-updated";
        sender.sendMessage(plugin.language().message(messageKey, Map.of("name", itemKey)));
        return true;
    }

    private boolean handleItemShow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        List<String> keys = plugin.savedItemStore().getKeys();
        if (keys.isEmpty()) {
            player.sendMessage(plugin.language().message("item-show-empty"));
            return true;
        }

        openSavedItemsPage(player, 0);
        return true;
    }

    private boolean handleItemGive(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(plugin.language().message("usage-item-give"));
            return true;
        }

        String targetName = args[2].replace("%", "");
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(plugin.language().message("item-player-not-found", Map.of("player", args[2])));
            return true;
        }

        String key = plugin.savedItemStore().normalizeKey(args[3]);
        Optional<ItemStack> baseItem = plugin.savedItemStore().getItem(key);
        if (baseItem.isEmpty()) {
            sender.sendMessage(plugin.language().message("item-not-found", Map.of("name", key)));
            return true;
        }

        int amount = parsePositiveInt(args[4]);
        if (amount < 1) {
            sender.sendMessage(plugin.language().message("item-invalid-amount", Map.of("amount", args[4])));
            return true;
        }

        ItemStack template = baseItem.get();
        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        int dropped = 0;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack give = template.clone();
            give.setAmount(stackAmount);

            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(give);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    dropped += leftover.getAmount();
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
            }
            remaining -= stackAmount;
        }

        sender.sendMessage(plugin.language().message("item-give-success",
                Map.of("player", target.getName(), "name", key, "amount", String.valueOf(amount), "dropped",
                        String.valueOf(dropped))));
        if (sender != target) {
            target.sendMessage(
                    plugin.language().message("item-received", Map.of("name", key, "amount", String.valueOf(amount))));
        }
        return true;
    }

    private boolean handleItemRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-item-remove"));
            return true;
        }

        String key = plugin.savedItemStore().normalizeKey(args[2]);
        if (!plugin.savedItemStore().isValidKey(key)) {
            sender.sendMessage(plugin.language().message("item-invalid-name", Map.of("name", args[2])));
            return true;
        }

        if (!plugin.savedItemStore().removeItem(key)) {
            sender.sendMessage(plugin.language().message("item-not-found", Map.of("name", key)));
            return true;
        }

        sender.sendMessage(plugin.language().message("item-removed", Map.of("name", key)));
        return true;
    }

    private boolean handleItemUpdate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-item-update"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String key = plugin.savedItemStore().normalizeKey(args[2]);
        if (!plugin.savedItemStore().isValidKey(key)) {
            sender.sendMessage(plugin.language().message("item-invalid-name", Map.of("name", args[2])));
            return true;
        }
        if (plugin.savedItemStore().getItem(key).isEmpty()) {
            sender.sendMessage(plugin.language().message("item-not-found", Map.of("name", key)));
            return true;
        }

        SavedItemStore.SaveResult result = plugin.savedItemStore().saveItem(key, inHand.clone());
        if (result == SavedItemStore.SaveResult.FAILED) {
            sender.sendMessage(plugin.language().message("item-save-failed", Map.of("name", key)));
            return true;
        }

        sender.sendMessage(plugin.language().message("item-updated", Map.of("name", key)));
        return true;
    }

    @EventHandler
    public void onSavedItemClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SavedItemsInventoryHolder holder)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT) {
            return;
        }

        if (event.getRawSlot() == SAVED_ITEMS_PREV_SLOT) {
            openSavedItemsPage(player, holder.page() - 1);
            return;
        }
        if (event.getRawSlot() == SAVED_ITEMS_NEXT_SLOT) {
            openSavedItemsPage(player, holder.page() + 1);
            return;
        }
        if (event.getRawSlot() == SAVED_ITEMS_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String key = holder.getKeyBySlot(event.getRawSlot());
        if (key == null) {
            return;
        }

        Optional<ItemStack> item = plugin.savedItemStore().getItem(key);
        if (item.isEmpty()) {
            player.sendMessage(plugin.language().message("item-not-found", Map.of("name", key)));
            return;
        }

        ItemStack give = item.get().clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(give);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        player.sendMessage(plugin.language().message("item-gui-give", Map.of("name", key)));
    }

    private void openSavedItemsPage(Player player, int requestedPage) {
        List<String> keys = plugin.savedItemStore().getKeys();
        if (keys.isEmpty()) {
            player.sendMessage(plugin.language().message("item-show-empty"));
            player.closeInventory();
            return;
        }

        int pageSize = SAVED_ITEMS_CONTENT_SLOTS.size();
        int totalPages = Math.max(1, (int) Math.ceil(keys.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        SavedItemsInventoryHolder holder = new SavedItemsInventoryHolder(page);
        String title = ChatColor.translateAlternateColorCodes('&',
                legacyText(plugin.language().rawMessage(
                        "messages.gui-saved-items-title",
                        Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(totalPages)))));
        Inventory inventory = Bukkit.createInventory(holder, SAVED_ITEMS_GUI_SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = createGuiButton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < SAVED_ITEMS_GUI_SIZE; slot++) {
            if (!SAVED_ITEMS_CONTENT_SLOTS.contains(slot)) {
                inventory.setItem(slot, border);
            }
        }

        int start = page * pageSize;
        int endExclusive = Math.min(keys.size(), start + pageSize);
        int contentIndex = 0;
        for (int i = start; i < endExclusive; i++) {
            String key = keys.get(i);
            Optional<ItemStack> stored = plugin.savedItemStore().getItem(key);
            if (stored.isEmpty()) {
                continue;
            }
            ItemStack display = stored.get().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                Map<String, String> placeholders = Map.of("name", key);
                if (meta.hasDisplayName() || meta.displayName() != null) {
                    Component displayName = meta.displayName();
                    if (displayName != null) {
                        meta.displayName(nonItalic(displayName));
                    }
                } else {
                    Component fallbackName = plugin.language().rawMessage("messages.item-gui-item-name", placeholders);
                    meta.displayName(nonItalic(fallbackName));
                }

                List<Component> lore = safeGetLore(meta);
                lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
                List<Component> guiLore = plugin.language().rawMessageList("messages.item-gui-item-lore", placeholders);
                for (Component line : guiLore) {
                    lore.add(nonItalic(line));
                }
                meta.lore(lore);
                display.setItemMeta(meta);
            }

            int targetSlot = SAVED_ITEMS_CONTENT_SLOTS.get(contentIndex++);
            inventory.setItem(targetSlot, display);
            holder.bindSlot(targetSlot, key);
        }

        if (page > 0) {
            inventory.setItem(SAVED_ITEMS_PREV_SLOT,
                    createGuiButton(
                            Material.ARROW,
                            plugin.language().rawMessage("messages.gui-prev-page-name"),
                            plugin.language().rawMessageList("messages.gui-prev-page-lore", Map.of())));
        }
        if (page < totalPages - 1) {
            inventory.setItem(SAVED_ITEMS_NEXT_SLOT,
                    createGuiButton(
                            Material.ARROW,
                            plugin.language().rawMessage("messages.gui-next-page-name"),
                            plugin.language().rawMessageList("messages.gui-next-page-lore", Map.of())));
        }
        inventory.setItem(SAVED_ITEMS_CLOSE_SLOT, createGuiCloseButton());

        player.openInventory(inventory);
    }

    private ItemStack createGuiButton(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiButton(Material material, Component displayName, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(nonItalic(displayName));
            if (lore != null && !lore.isEmpty()) {
                List<Component> fixedLore = new ArrayList<>(lore.size());
                for (Component line : lore) {
                    fixedLore.add(nonItalic(line));
                }
                meta.lore(fixedLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(nonItalic(plugin.language().rawMessage("messages.button-close-name")));
            List<Component> lore = plugin.language().rawMessageList("messages.button-close-lore", Map.of());
            if (!lore.isEmpty()) {
                List<Component> fixedLore = new ArrayList<>(lore.size());
                for (Component line : lore) {
                    fixedLore.add(nonItalic(line));
                }
                meta.lore(fixedLore);
            }
            item.setItemMeta(meta);
        }
        return item;
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

        List<Component> lore = safeGetLore(meta);
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

        List<Component> lore = safeGetLore(meta);
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

        List<Component> lore = safeGetLore(meta);
        while (lore.size() < lineNumber) {
            lore.add(Component.empty());
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
            return filter(List.of("create", "reload", "material", "info", "lore", "item"), args[0]);
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
        if (args.length == 2 && "item".equalsIgnoreCase(args[0])) {
            return filter(ITEM_SUB_ACTIONS, args[1]);
        }
        if ("item".equalsIgnoreCase(args[0])) {
            if (args.length == 3 && "give".equalsIgnoreCase(args[1])) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            if (args.length == 3 && (("save".equalsIgnoreCase(args[1])) || "remove".equalsIgnoreCase(args[1])
                    || "update".equalsIgnoreCase(args[1]))) {
                return filter(plugin.savedItemStore().getKeys(), args[2]);
            }
            if (args.length == 4 && "give".equalsIgnoreCase(args[1])) {
                return filter(plugin.savedItemStore().getKeys(), args[3]);
            }
            if (args.length == 5 && "give".equalsIgnoreCase(args[1])) {
                return filter(List.of("1", "16", "32", "64"), args[4]);
            }
        }
        if ("lore".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player))
                return Collections.emptyList();

            ItemStack inHand = player.getInventory().getItemInMainHand();
            ItemMeta meta = (inHand != null && !inHand.getType().isAir()) ? inHand.getItemMeta() : null;
            List<String> rawLore = (meta != null) ? LoreCopyWriter.getRawLore(meta) : Collections.emptyList();

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

            if (args.length == 4) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("set")) {
                    int lineNum = parsePositiveInt(args[2]);
                    if (lineNum > 0 && lineNum <= rawLore.size()) {
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

    private static List<Component> safeGetLore(ItemMeta meta) {
        List<Component> existing = meta.lore();
        return existing != null ? new ArrayList<>(existing) : new ArrayList<>();
    }

    private static String joinFrom(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex)
                sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static int parsePositiveInt(String raw) {
        try {
            int v = Integer.parseInt(raw);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static List<Integer> buildSavedItemsContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            int rowStart = row * 9;
            for (int col = 1; col <= 7; col++) {
                slots.add(rowStart + col);
            }
        }
        return List.copyOf(slots);
    }

    private String legacyText(Component component) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }

    private Component nonItalic(Component component) {
        if (component == null) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static final class SavedItemsInventoryHolder implements InventoryHolder {
        private final Map<Integer, String> keyBySlot = new HashMap<>();
        private final int page;
        private Inventory inventory;

        private SavedItemsInventoryHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public void bindSlot(int slot, String key) {
            keyBySlot.put(slot, key);
        }

        public String getKeyBySlot(int slot) {
            return keyBySlot.get(slot);
        }

        public int page() {
            return page;
        }
    }
}
