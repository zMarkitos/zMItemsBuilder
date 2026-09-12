package dev.zm.itemsbuilder.command;

import dev.zm.itemsbuilder.command.gui.HelpGui;
import dev.zm.itemsbuilder.command.gui.ItemCreationGui;
import dev.zm.itemsbuilder.command.gui.ProgressionGui;

import dev.zm.itemsbuilder.builder.ItemBuildContext;
import dev.zm.itemsbuilder.builder.ItemFactory;
import dev.zm.itemsbuilder.builder.model.AttributeSettings;
import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.builder.model.ItemBundleDefinition;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import dev.zm.itemsbuilder.config.ItemsConfig;
import dev.zm.itemsbuilder.config.PluginSettings;
import dev.zm.itemsbuilder.util.ColorUtils;
import dev.zm.itemsbuilder.util.ItemDataStore;
import dev.zm.itemsbuilder.util.ItemEnchantLoreManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffectType;

public final class zMItemsCommand implements CommandExecutor, TabCompleter, Listener {

    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private final zMItemsBuilder plugin;
    private static final List<String> MATERIAL_SUGGESTIONS = buildMaterialSuggestions();
    private static final List<String> ENCHANT_SUGGESTIONS = buildEnchantSuggestions();
    private static final List<String> ENCHANT_AMOUNT_SUGGESTIONS = List.of("0", "1", "2", "3", "4", "5", "10");
    private static final List<String> LORE_SUB_ACTIONS = List.of("add", "remove", "set", "reset", "copy", "paste");
    private static final List<String> ITEM_SUB_ACTIONS = List.of("save", "show", "give", "remove", "update");
    private static final List<String> ACTION_TYPES = List.of("player_command", "console_command", "sound", "uses",
            "cooldown");
    private static final List<String> CLICK_TYPES = List.of("RIGHT_CLICK", "LEFT_CLICK", "SHIFT_RIGHT_CLICK",
            "SHIFT_LEFT_CLICK");
    private static final int SAVED_ITEMS_GUI_SIZE = 54;
    private static final List<Integer> SAVED_ITEMS_CONTENT_SLOTS = buildSavedItemsContentSlots();
    private static final int SAVED_ITEMS_PREV_SLOT = 45;
    private static final int SAVED_ITEMS_CLOSE_SLOT = 49;
    private static final int SAVED_ITEMS_NEXT_SLOT = 53;
    private static final int MIGRATE_GUI_SIZE = 54;
    private static final int MIGRATE_ACCEPT_SLOT = 49;
    private static final int MIGRATE_CANCEL_SLOT = 53;
    private final ItemEnchantLoreManager enchantLoreManager;
    // In-memory lore clipboard per player UUID
    private final java.util.Map<java.util.UUID, List<net.kyori.adventure.text.Component>> loreClipboard = new java.util.concurrent.ConcurrentHashMap<>();

    public zMItemsCommand(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.enchantLoreManager = new ItemEnchantLoreManager(plugin, plugin.language());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ("irename".equals(commandName) || "rename".equals(label.toLowerCase(Locale.ROOT))) {
            return handleRename(sender, args, 0);
        }

        if (args.length == 0) {
            return handleHelp(sender);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "help" -> handleHelp(sender);
            case "give" -> handleGive(sender, args);
            case "create" -> handleCreate(sender, args);
            case "reload" -> handleReload(sender);
            case "material" -> handleMaterial(sender, args);
            case "info" -> handleInfo(sender);
            case "lore" -> handleLore(sender, args);
            case "enchant" -> handleEnchant(sender, args);
            case "rename" -> handleRename(sender, args, 1);
            case "migrate" -> handleMigrate(sender);
            case "item" -> handleItem(sender, args);
            case "glow" -> handleGlow(sender);
            case "hide" -> handleHide(sender, args);
            case "flag" -> handleFlag(sender, args);
            case "unbreakable" -> handleUnbreakable(sender);
            case "armor_trim" -> handleArmorTrim(sender, args);
            case "clone" -> handleClone(sender, args);
            case "action" -> handleAction(sender, args);
            case "effect" -> handleEffect(sender, args);
            case "progression" -> handleProgression(sender, args);
            case "attribute" -> handleAttribute(sender, args);
            default -> handleHelp(sender);
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

        // Optional 6th argument: true/false whether to notify the receiver (default
        // true)
        boolean notify = args.length < 6 || !"false".equalsIgnoreCase(args[5]);

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
        if (notify && sender != target) {
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        loreClipboard.remove(uuid);
        ProgressionGui.removeSession(uuid);
        ItemCreationGui.removeSession(uuid);
    }

    @EventHandler
    public void onSavedItemClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SavedItemsInventoryHolder holder)) {
            if (event.getView().getTopInventory().getHolder() instanceof MigrationInventoryHolder migrationHolder) {
                handleMigrationClick(event, migrationHolder);
            }
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
        player.closeInventory();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMigrationDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MigrationInventoryHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onMigrationClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MigrationInventoryHolder holder)) {
            return;
        }
        if (holder.hasReturned()) {
            return;
        }

        List<ItemStack> toReturn = holder.consumeReturnItems();
        if ((toReturn == null || toReturn.isEmpty()) && event.getInventory() != null) {
            toReturn = collectMigrationItems(event.getInventory(), false);
            holder.setReturnItems(toReturn);
        }

        if (toReturn == null || toReturn.isEmpty() || !(event.getPlayer() instanceof Player player)) {
            holder.markReturned();
            return;
        }

        List<ItemStack> finalReturn = List.copyOf(toReturn);
        holder.markReturned();
        Bukkit.getScheduler().runTask(plugin, () -> returnItems(player, finalReturn));
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

    private void openMigrationMenu(Player player) {
        MigrationInventoryHolder holder = new MigrationInventoryHolder();
        String title = legacyText(plugin.language().rawMessage("messages.migrate-title"));
        Inventory inventory = Bukkit.createInventory(holder, MIGRATE_GUI_SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = createGuiButton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = MIGRATE_GUI_SIZE - 9; slot < MIGRATE_GUI_SIZE; slot++) {
            inventory.setItem(slot, border);
        }

        inventory.setItem(MIGRATE_ACCEPT_SLOT, createGuiButton(
                Material.LIME_CONCRETE,
                plugin.language().rawMessage("messages.migrate-accept-name"),
                plugin.language().rawMessageList("messages.migrate-accept-lore", Map.of())));
        inventory.setItem(MIGRATE_CANCEL_SLOT, createGuiButton(
                Material.BARRIER,
                plugin.language().rawMessage("messages.migrate-cancel-name"),
                plugin.language().rawMessageList("messages.migrate-cancel-lore", Map.of())));

        player.sendMessage(plugin.language().message("migrate-open"));
        player.openInventory(inventory);
    }

    private void handleMigrationClick(InventoryClickEvent event, MigrationInventoryHolder holder) {
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        int acceptSlot = MIGRATE_ACCEPT_SLOT;
        int cancelSlot = MIGRATE_CANCEL_SLOT;
        int protectedRowStart = topSize - 9;

        if (event.getRawSlot() >= protectedRowStart) {
            if (event.getRawSlot() == acceptSlot) {
                event.setCancelled(true);
                finalizeMigration(event, holder, true);
                return;
            }
            if (event.getRawSlot() == cancelSlot) {
                event.setCancelled(true);
                finalizeMigration(event, holder, false);
                return;
            }
            event.setCancelled(true);
            return;
        }
    }

    private void finalizeMigration(InventoryClickEvent event, MigrationInventoryHolder holder, boolean migrate) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getView().getTopInventory();
        List<ItemStack> items = collectMigrationItems(inventory, migrate);
        holder.setReturnItems(items);
        inventory.clear();
        player.closeInventory();
        if (migrate) {
            player.sendMessage(plugin.language().message("migrate-accepted",
                    Map.of("migrated", String.valueOf(holder.getMigratedCount()),
                            "total", String.valueOf(holder.getTotalCount()))));
        } else {
            player.sendMessage(plugin.language().message("migrate-cancelled"));
        }
    }

    private List<ItemStack> collectMigrationItems(Inventory inventory, boolean migrate) {
        List<ItemStack> items = new ArrayList<>();
        int migrated = 0;
        int total = 0;
        int protectedRowStart = inventory.getSize() - 9;
        for (int slot = 0; slot < protectedRowStart; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            total++;
            ItemStack copy = stack.clone();
            if (migrate && applyLegacySourceKey(copy)) {
                migrated++;
            }
            items.add(copy);
        }

        if (inventory.getHolder() instanceof MigrationInventoryHolder holder) {
            holder.setMigrationStats(total, migrated);
        }
        return items;
    }

    private boolean applyLegacySourceKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        if (ItemIdentityStore.readRawSourceKey(plugin, item) != null) {
            return false;
        }

        String legacyKey = ItemIdentityStore.read(plugin, item);
        if (legacyKey == null || legacyKey.isBlank()) {
            return false;
        }
        if (plugin.itemRegistry().getItem(legacyKey).isEmpty()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        ItemIdentityStore.writeSourceKey(plugin, meta, legacyKey);
        item.setItemMeta(meta);
        return true;
    }

    private void returnItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<ItemStack> leftoverDrops = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftoverDrops.addAll(leftovers.values());
            }
        }
        for (ItemStack leftover : leftoverDrops) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
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
            case "copy" -> handleLoreCopy(player, inHand);
            case "paste" -> handleLorePaste(player, inHand);
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

        String raw = unwrapQuotedText(joinFrom(args, 2));
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

        String raw = unwrapQuotedText(joinFrom(args, 3));
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

    private boolean handleEnchant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.enchant")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-enchant"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String enchantKey = args[1].trim().toLowerCase(Locale.ROOT);
        Optional<Enchantment> enchantment = ItemResolver.enchantment(enchantKey);
        if (enchantment.isEmpty()) {
            sender.sendMessage(plugin.language().message("enchant-invalid-name", Map.of("enchant", args[1])));
            return true;
        }

        int amount = parseNonNegativeInt(args[2]);
        if (amount < 0) {
            sender.sendMessage(plugin.language().message("enchant-invalid-amount", Map.of("amount", args[2])));
            return true;
        }

        if (!enchantLoreManager.applyEnchantChange(inHand, enchantment.get().getKey().getKey(), amount)) {
            sender.sendMessage(plugin.language().message("enchant-update-failed", Map.of("enchant", enchantKey)));
            return true;
        }

        String enchantName = plugin.language().enchantName(enchantment.get().getKey().getKey());
        if (amount == 0) {
            player.sendMessage(plugin.language().message("enchant-removed", Map.of("enchant", enchantName)));
        } else {
            player.sendMessage(plugin.language().message("enchant-updated",
                    Map.of("enchant", enchantName, "level", String.valueOf(amount))));
        }
        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args, int textStartIndex) {
        if (!sender.hasPermission("zmitemsbuilder.rename")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < textStartIndex + 1) {
            sender.sendMessage(plugin.language().message("usage-rename"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String raw = unwrapQuotedText(joinFrom(args, textStartIndex));
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null) {
            return true;
        }

        if (raw == null || raw.isBlank()) {
            meta.setDisplayName(null);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("rename-removed"));
            return true;
        }

        meta.displayName(TextUtils.toItemComponent(raw));
        inHand.setItemMeta(meta);
        player.sendMessage(plugin.language().message("rename-success", Map.of("text", raw)));
        return true;
    }

    private boolean handleGlow(CommandSender sender) {
        if (!sender.hasPermission("zmitemsbuilder.glow")) {
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

        ItemMeta meta = inHand.getItemMeta();
        if (meta == null)
            return true;

        boolean isGlowing = meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) && meta.hasEnchants();

        if (isGlowing) {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getEnchants().keySet().forEach(meta::removeEnchant);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("glow-removed"));
        } else {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if (!meta.hasEnchants()) {
                try {
                    meta.addEnchant(Enchantment.LUCK, 1, true);
                } catch (IllegalArgumentException ignored) {
                    inHand.addUnsafeEnchantment(Enchantment.LUCK, 1);
                    meta = inHand.getItemMeta();
                    if (meta != null) {
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                }
            }
            if (meta != null) {
                inHand.setItemMeta(meta);
            }
            player.sendMessage(plugin.language().message("glow-added"));
        }

        player.updateInventory();
        return true;
    }

    private boolean handleHide(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.hide")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-hide"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        ItemMeta meta = inHand.getItemMeta();
        if (meta == null)
            return true;

        String rawFlag = args[1].toUpperCase(Locale.ROOT);
        ItemFlag flag;
        try {
            flag = ItemFlag.valueOf(rawFlag);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(plugin.language().message("invalid-flag", Map.of("flag", args[1])));
            return true;
        }

        if (meta.hasItemFlag(flag)) {
            meta.removeItemFlags(flag);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("hide-removed", Map.of("flag", flag.name())));
        } else {
            meta.addItemFlags(flag);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("hide-added", Map.of("flag", flag.name())));
        }
        return true;
    }

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.flag")) {
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

        if (args.length < 2) {
            new dev.zm.itemsbuilder.command.gui.FlagGui(plugin, this, player, inHand).open();
            return true;
        }

        ItemBehaviorFlag flag = ItemBehaviorFlag.from(args[1]);
        if (flag == null) {
            sender.sendMessage(plugin.language().message("invalid-behavior-flag", Map.of("flag", args[1])));
            return true;
        }

        toggleFlag(player, inHand, flag);
        return true;
    }

    public void toggleFlag(Player player, ItemStack item, ItemBehaviorFlag flag) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        Set<ItemBehaviorFlag> flags = ItemFlagStore.read(plugin, item);
        boolean hadFlag = flags.contains(flag);
        if (hadFlag) {
            flags.remove(flag);
        } else {
            flags.add(flag);
        }
        ItemFlagStore.write(plugin, meta, flags);
        item.setItemMeta(meta);

        if (hadFlag) {
            player.sendMessage(plugin.language().message("flag-removed", Map.of("flag", flag.name())));
        } else {
            player.sendMessage(plugin.language().message("flag-added", Map.of("flag", flag.name())));
        }
    }

    private boolean handleUnbreakable(CommandSender sender) {
        if (!sender.hasPermission("zmitemsbuilder.unbreakable")) {
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

        ItemMeta meta = inHand.getItemMeta();
        if (meta == null)
            return true;

        if (meta.isUnbreakable()) {
            meta.setUnbreakable(false);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("unbreakable-removed"));
        } else {
            meta.setUnbreakable(true);
            inHand.setItemMeta(meta);
            player.sendMessage(plugin.language().message("unbreakable-added"));
        }
        return true;
    }

    private boolean handleArmorTrim(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.armortrim")) {
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

        if (!(inHand.getItemMeta() instanceof ArmorMeta armorMeta)) {
            sender.sendMessage(plugin.language().message("not-armor"));
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("remove")) {
            armorMeta.setTrim(null);
            inHand.setItemMeta(armorMeta);
            player.sendMessage(plugin.language().message("armor-trim-removed"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-armor-trim"));
            return true;
        }

        String materialKey = args[1].toLowerCase(Locale.ROOT);
        String patternKey = args[2].toLowerCase(Locale.ROOT);

        TrimMaterial material = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(materialKey));
        if (material == null) {
            sender.sendMessage(plugin.language().message("invalid-trim-material", Map.of("material", args[1])));
            return true;
        }

        TrimPattern pattern = Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(patternKey));
        if (pattern == null) {
            sender.sendMessage(plugin.language().message("invalid-trim-pattern", Map.of("pattern", args[2])));
            return true;
        }

        armorMeta.setTrim(new ArmorTrim(material, pattern));
        inHand.setItemMeta(armorMeta);
        player.sendMessage(plugin.language().message("armor-trim-added"));
        return true;
    }

    private boolean handleMigrate(CommandSender sender) {
        if (!sender.hasPermission("zmitemsbuilder.migrate")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        openMigrationMenu(player);
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

    private boolean handleLoreCopy(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta != null ? meta.lore() : null;
        if (lore == null)
            lore = List.of();
        loreClipboard.put(player.getUniqueId(), new java.util.ArrayList<>(lore));
        player.sendMessage(plugin.language().message("lore-copy-success",
                Map.of("lines", String.valueOf(lore.size()))));
        return true;
    }

    private boolean handleLorePaste(Player player, ItemStack item) {
        List<net.kyori.adventure.text.Component> clipboard = loreClipboard.get(player.getUniqueId());
        if (clipboard == null) {
            player.sendMessage(plugin.language().message("lore-paste-empty"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return true;
        meta.lore(new java.util.ArrayList<>(clipboard));
        item.setItemMeta(meta);
        player.sendMessage(plugin.language().message("lore-paste-success",
                Map.of("lines", String.valueOf(clipboard.size()))));
        return true;
    }

    private boolean handleClone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.clone")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-clone"));
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            sender.sendMessage(plugin.language().message("no-item-in-hand"));
            return true;
        }

        String itemId = args[1].toLowerCase(java.util.Locale.ROOT);
        String groupId = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : null;

        LoreCopyWriter.CopyResult result = LoreCopyWriter.cloneItemToItemsYml(plugin, inHand, itemId, groupId);

        switch (result) {
            case EXISTS -> player.sendMessage(plugin.language().message("clone-exists",
                    Map.of("key", itemId)));
            case FAILED -> player.sendMessage(plugin.language().message("clone-failed",
                    Map.of("key", itemId)));
            case CREATED -> {
                plugin.reloadPluginState();
                ItemMeta meta = inHand.getItemMeta();
                int lines = LoreCopyWriter.getRawLore(meta).size();
                player.sendMessage(plugin.language().message("clone-created",
                        Map.of("key", itemId, "lines", String.valueOf(lines))));
                if (groupId != null) {
                    player.sendMessage(plugin.language().message("clone-kit",
                            Map.of("key", itemId, "kit", groupId)));
                }
            }
        }
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.action")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }

        // /zmitems action <add|remove> <id_item> ...
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-action"));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String inputId = args[2].toLowerCase(Locale.ROOT);

        String itemId = null;
        for (String key : plugin.itemRegistry().getItemNames()) {
            ItemDefinition def = plugin.itemRegistry().getItem(key).orElse(null);
            if (def != null && def.itemIdentifier() != null && def.itemIdentifier().equalsIgnoreCase(inputId)) {
                itemId = def.itemIdentifier();
                break;
            }
        }

        if (itemId == null) {
            ItemDefinition def = plugin.itemRegistry().getItem(inputId).orElse(null);
            if (def != null) {
                itemId = def.itemIdentifier() != null ? def.itemIdentifier() : inputId;
            }
        }

        if (itemId == null) {
            sender.sendMessage(plugin.language().message("give-item-not-found", Map.of("item", inputId)));
            return true;
        }

        ItemDataStore store = plugin.itemDataStore();

        if (sub.equals("add")) {
            // /zmitems action add <id_item> <type> [value...] [click]
            if (args.length < 5) {
                sender.sendMessage(plugin.language().message("usage-action-add"));
                return true;
            }

            String type = args[3].toLowerCase(Locale.ROOT);

            // Handle scalar types: uses and cooldown
            if (type.equals("uses") || type.equals("cooldown")) {
                int val = parsePositiveInt(args[4]);
                if (val < 1) {
                    sender.sendMessage(plugin.language().message("action-invalid-number", Map.of("value", args[4])));
                    return true;
                }
                boolean ok;
                if (type.equals("uses")) {
                    ok = store.setMaxUses(itemId, val);
                } else {
                    ok = store.setCooldown(itemId, val);
                }
                if (ok) {
                    sender.sendMessage(plugin.language().message("action-scalar-set",
                            Map.of("item", itemId, "type", type, "value", String.valueOf(val))));
                } else {
                    sender.sendMessage(plugin.language().message("action-save-failed"));
                }
                return true;
            }

            // Command / sound types need value and optional click
            // args: action add <id_item> <type> <click> <value...>
            if (!type.equals("player_command") && !type.equals("console_command") && !type.equals("sound")) {
                sender.sendMessage(plugin.language().message("action-invalid-type", Map.of("type", type)));
                return true;
            }

            // arg[4] = click type, arg[5..] = value
            if (args.length < 6) {
                sender.sendMessage(plugin.language().message("usage-action-add"));
                return true;
            }
            String click = args[4].toUpperCase(Locale.ROOT);
            if (!CLICK_TYPES.contains(click)) {
                sender.sendMessage(plugin.language().message("action-invalid-click", Map.of("click", click)));
                return true;
            }
            // Join remaining args as the value
            String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));

            boolean ok = store.addAction(itemId,
                    new ItemDataStore.ItemActionData(type, value, click));
            if (ok) {
                sender.sendMessage(plugin.language().message("action-add-success",
                        Map.of("item", itemId, "type", type, "click", click, "value", value)));
            } else {
                sender.sendMessage(plugin.language().message("action-save-failed"));
            }
            return true;
        }

        if (sub.equals("remove")) {
            // /zmitems action remove <id_item> — opens an inventory GUI with the actions
            // listed
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.language().message("player-only"));
                return true;
            }
            List<ItemDataStore.ItemActionData> actions = store.getActions(itemId);
            if (actions.isEmpty()) {
                player.sendMessage(plugin.language().message("action-list-empty", Map.of("item", itemId)));
                return true;
            }
            openActionRemoveGui(player, itemId, actions, store);
            return true;
        }

        sender.sendMessage(plugin.language().message("usage-action"));
        return true;
    }

    /**
     * Opens a simple inventory GUI listing all actions for the item so the player
     * can click to remove one.
     */
    private void openActionRemoveGui(Player player, String itemId,
            java.util.List<dev.zm.itemsbuilder.util.ItemDataStore.ItemActionData> actions,
            dev.zm.itemsbuilder.util.ItemDataStore store) {
        int size = Math.max(27, Math.min(54, ((actions.size() / 7) + 2) * 9));
        net.kyori.adventure.text.Component title = plugin.language().rawMessage("messages.action-gui-title",
                Map.of("item", itemId));
        ActionRemoveHolder holder = new ActionRemoveHolder(itemId);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(holder, size, title);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.empty());
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        for (int i = size - 9; i < size; i++) {
            inv.setItem(i, border);
        }

        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(plugin.language().rawMessage("messages.button-close-name"));
            closeMeta.lore(plugin.language().rawMessageList("messages.button-close-lore", Map.of()));
            closeBtn.setItemMeta(closeMeta);
        }
        inv.setItem(size - 5, closeBtn);

        int slot = 10;
        for (int i = 0; i < actions.size() && slot < size - 9; i++) {
            dev.zm.itemsbuilder.util.ItemDataStore.ItemActionData a = actions.get(i);

            Material mat = switch (a.type().toLowerCase(Locale.ROOT)) {
                case "player_command" -> Material.COMMAND_BLOCK;
                case "console_command" -> Material.REDSTONE_BLOCK;
                case "sound" -> Material.NOTE_BLOCK;
                case "uses" -> Material.TRIPWIRE_HOOK;
                case "cooldown" -> Material.CLOCK;
                default -> Material.PAPER;
            };

            ItemStack btn = new ItemStack(mat);
            ItemMeta m = btn.getItemMeta();
            if (m != null) {
                String typeName = formatActionType(a.type());
                m.displayName(TextUtils.toItemComponent(
                        "&#F13713&l■ &e" + typeName + " &8— &7" + a.click()));

                java.util.List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(TextUtils.toItemComponent("  &8Value: &f" + truncateValue(a.value(), 40)));
                lore.add(Component.empty());
                lore.add(TextUtils.toItemComponent("  &#F13713Click to &cremove"));
                lore.add(Component.empty());
                m.lore(lore);
                btn.setItemMeta(m);
            }
            inv.setItem(slot, btn);
            slot++;
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
        }

        final int actionCount = actions.size();
        final int invSize = size;
        player.openInventory(inv);

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
                if (!(event.getWhoClicked() instanceof Player clicker))
                    return;
                if (!clicker.equals(player))
                    return;
                if (!(event.getView().getTopInventory().getHolder() instanceof ActionRemoveHolder))
                    return;
                event.setCancelled(true);
                int rawSlot = event.getRawSlot();
                if (rawSlot == invSize - 5) {
                    clicker.closeInventory();
                    return;
                }
                if (rawSlot < 9 || rawSlot >= invSize - 9)
                    return;
                int col = rawSlot % 9;
                if (col == 0 || col == 8)
                    return;
                int actionIndex = -1;
                int currentSlot = 10;
                int currentAction = 0;
                while (currentSlot < invSize - 9 && currentAction < actionCount) {
                    if (currentSlot == rawSlot) {
                        actionIndex = currentAction;
                        break;
                    }
                    currentSlot++;
                    currentAction++;
                    if (currentSlot % 9 == 0 || currentSlot % 9 == 8) {
                        currentSlot++;
                    }
                }
                if (actionIndex < 0 || actionIndex >= actionCount)
                    return;
                clicker.closeInventory();
                boolean ok = store.removeAction(itemId, actionIndex);
                if (ok) {
                    clicker.sendMessage(plugin.language().message("action-remove-success",
                            Map.of("item", itemId, "index", String.valueOf(actionIndex))));
                } else {
                    clicker.sendMessage(plugin.language().message("action-save-failed"));
                }
                org.bukkit.event.HandlerList.unregisterAll(this);
            }

            @org.bukkit.event.EventHandler
            public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
                if (!(event.getView().getTopInventory().getHolder() instanceof ActionRemoveHolder))
                    return;
                org.bukkit.event.HandlerList.unregisterAll(this);
            }

            @org.bukkit.event.EventHandler
            public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
                if (event.getView().getTopInventory().getHolder() instanceof ActionRemoveHolder) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }

    private static final class ActionRemoveHolder implements org.bukkit.inventory.InventoryHolder {
        private final String itemId;
        private org.bukkit.inventory.Inventory inventory;

        ActionRemoveHolder(String itemId) {
            this.itemId = itemId;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inventory;
        }

        public void setInventory(org.bukkit.inventory.Inventory inventory) {
            this.inventory = inventory;
        }

        public String getItemId() {
            return itemId;
        }
    }

    private String formatActionType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "player_command" -> "Player Command";
            case "console_command" -> "Console Command";
            case "sound" -> "Sound";
            case "uses" -> "Max Uses";
            case "cooldown" -> "Cooldown";
            default -> type;
        };
    }

    private String truncateValue(String value, int maxLen) {
        if (value == null)
            return "";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }

    private boolean handleEffect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.effect")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }

        // /zmitems effect <add|remove|list> <id_item> ...
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("usage-effect"));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        String idItemRaw = args[2].toLowerCase(Locale.ROOT);

        // Find the item definition that has this explicit id_item
        dev.zm.itemsbuilder.builder.model.ItemDefinition itemDef = null;
        for (String key : plugin.itemRegistry().getItemNames()) {
            plugin.itemRegistry().getItem(key).ifPresent(def -> {
                if (def.itemIdentifier() != null && def.itemIdentifier().equalsIgnoreCase(idItemRaw)) {
                    // found it, but we can't assign to non-final local variable from lambda easily
                    // so we do it in a traditional loop below
                }
            });
        }

        for (String key : plugin.itemRegistry().getItemNames()) {
            dev.zm.itemsbuilder.builder.model.ItemDefinition def = plugin.itemRegistry().getItem(key).orElse(null);
            if (def != null && def.itemIdentifier() != null && def.itemIdentifier().equalsIgnoreCase(idItemRaw)) {
                itemDef = def;
                break;
            }
        }

        if (itemDef == null) {
            // Check if it's a valid config key that just lacks an id_item
            if (plugin.itemRegistry().getItem(idItemRaw).isPresent()) {
                sender.sendMessage(plugin.language().message("item-requires-id-item", Map.of("item", idItemRaw)));
            } else {
                sender.sendMessage(plugin.language().message("give-item-not-found", Map.of("item", idItemRaw)));
            }
            return true;
        }

        String actualId = itemDef.itemIdentifier();
        dev.zm.itemsbuilder.util.ItemDataStore store = plugin.itemDataStore();

        if (sub.equals("list")) {
            java.util.List<dev.zm.itemsbuilder.util.ItemDataStore.ItemEffectData> effects = store.getEffects(actualId);
            if (effects.isEmpty()) {
                sender.sendMessage(plugin.language().message("effect-list-empty", Map.of("item", actualId)));
                return true;
            }
            sender.sendMessage(plugin.language().message("effect-list-header", Map.of("item", actualId)));
            for (dev.zm.itemsbuilder.util.ItemDataStore.ItemEffectData eff : effects) {
                sender.sendMessage(plugin.language().message("effect-list-item", Map.of(
                        "type", eff.type(),
                        "duration", formatDuration(eff.duration()),
                        "amplifier", String.valueOf(eff.amplifier() + 1),
                        "slot", eff.slot() != null ? eff.slot() : "ANY")));
            }
            return true;
        }

        if (sub.equals("add")) {
            // /zmitems effect add <id_item> <effect> <amplifier> <slot>
            if (args.length < 6) {
                sender.sendMessage(plugin.language().message("usage-effect-add"));
                return true;
            }

            String effectTypeStr = args[3].toUpperCase(Locale.ROOT);
            PotionEffectType effectType = PotionEffectType.getByName(effectTypeStr);
            if (effectType == null) {
                sender.sendMessage(plugin.language().message("effect-invalid-type", Map.of("type", effectTypeStr)));
                return true;
            }

            int inputLevel = parsePositiveInt(args[4]);
            if (inputLevel < 1) {
                sender.sendMessage(plugin.language().message("effect-invalid-level", Map.of("value", args[4])));
                return true;
            }
            int amplifier = inputLevel - 1;

            String rawSlot = args[5].toUpperCase(Locale.ROOT);
            Set<String> validSlots = Set.of("MAIN_HAND", "OFF_HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
                    "ANY");
            if (!validSlots.contains(rawSlot)) {
                sender.sendMessage(plugin.language().message("effect-invalid-slot", Map.of("slot", args[5])));
                return true;
            }
            String slot = rawSlot;

            // Duración fija infinita (no configurable)
            int duration = Integer.MAX_VALUE;

            ItemDataStore.ItemEffectData newEff = new ItemDataStore.ItemEffectData(
                    effectType.getName(), duration, amplifier, slot);

            if (store.addEffect(actualId, newEff)) {
                plugin.reloadPluginState();
                sender.sendMessage(plugin.language().message("effect-add-success", Map.of(
                        "type", effectType.getName(),
                        "amplifier", String.valueOf(amplifier + 1),
                        "slot", slot,
                        "item", actualId)));
            } else {
                sender.sendMessage(plugin.language().message("action-save-failed"));
            }
            return true;
        }

        if (sub.equals("remove")) {
            // /zmitems effect remove <id_item> <effect>
            if (args.length < 4) {
                sender.sendMessage(plugin.language().message("usage-effect-remove"));
                return true;
            }
            String effectTypeStr = args[3].toUpperCase(Locale.ROOT);

            // Check if exists first
            boolean exists = store.getEffects(actualId).stream()
                    .anyMatch(e -> e.type().equalsIgnoreCase(effectTypeStr));
            if (!exists) {
                sender.sendMessage(
                        plugin.language().message("effect-not-found", Map.of("type", effectTypeStr, "item", actualId)));
                return true;
            }

            if (store.removeEffect(actualId, effectTypeStr)) {
                plugin.reloadPluginState();
                sender.sendMessage(plugin.language().message("effect-remove-success", Map.of(
                        "type", effectTypeStr, "item", actualId)));
            } else {
                sender.sendMessage(plugin.language().message("action-save-failed"));
            }
            return true;
        }

        sender.sendMessage(plugin.language().message("usage-effect"));
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

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasAnyPermission(sender, "zmitemsbuilder.give", "zmkits.give")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(plugin.language().message("usage-give"));
            return true;
        }

        String type = args[1].toUpperCase(Locale.ROOT);
        String targetName = args[2];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(plugin.language().message("player-not-found", Map.of("player", targetName)));
            return true;
        }

        if (type.equals("NORMAL")) {
            String itemId = args[3].toLowerCase(Locale.ROOT);
            Optional<ItemDefinition> optionalItem = plugin.itemRegistry().getItem(itemId);
            if (optionalItem.isEmpty()) {
                sender.sendMessage(plugin.language().message("give-item-not-found", Map.of("item", itemId)));
                return true;
            }

            int amount = 1;
            String prefix = "";
            int prefixStartIndex = 4;

            if (args.length >= 5) {
                try {
                    amount = Integer.parseInt(args[4]);
                    if (amount < 1)
                        amount = 1;
                    prefixStartIndex = 5;
                } catch (NumberFormatException e) {
                    amount = 1;
                    prefixStartIndex = 4;
                }

                if (args.length > prefixStartIndex) {
                    prefix = String.join(" ", List.of(args).subList(prefixStartIndex, args.length));
                }
            }

            String primaryHex = "FDFF5C";
            String secondaryHex = "FEFF91";
            List<String> gradientColors = List.of(primaryHex);
            String prefixMiniMessage = "";

            if (!prefix.isEmpty()) {
                java.util.Optional<String> extractedHex = dev.zm.itemsbuilder.util.ColorUtils.extractHex(prefix);
                if (extractedHex.isPresent()) {
                    primaryHex = extractedHex.get();
                    secondaryHex = dev.zm.itemsbuilder.util.ColorUtils.secondaryFrom(primaryHex,
                            plugin.settings().secondaryColorMode());
                    gradientColors = dev.zm.itemsbuilder.util.ColorUtils.extractHexColors(prefix);
                    if (gradientColors.isEmpty())
                        gradientColors = List.of(primaryHex);
                    prefixMiniMessage = dev.zm.itemsbuilder.util.TextUtils.toMiniMessage(prefix);
                } else {
                    prefixMiniMessage = prefix;
                }
            }

            ItemDefinition def = optionalItem.get();
            dev.zm.itemsbuilder.builder.ItemBuildContext ctx = new dev.zm.itemsbuilder.builder.ItemBuildContext(
                    itemId, prefix, 1, null, "", prefixMiniMessage, primaryHex, secondaryHex, gradientColors, target);
            List<ItemStack> built = plugin.itemBundleBuilder().itemFactory().create(def, ctx);
            if (built.isEmpty()) {
                sender.sendMessage(plugin.language().message("give-item-not-found", Map.of("item", itemId)));
                return true;
            }
            List<ItemStack> toGive = new java.util.ArrayList<>();
            for (ItemStack piece : built) {
                ItemStack copy = piece.clone();
                copy.setAmount(amount);
                toGive.add(copy);
            }

            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(toGive.toArray(new ItemStack[0]));
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

            sender.sendMessage(plugin.language().message("give-success",
                    Map.of("player", target.getName(), "item", itemId, "amount", String.valueOf(amount))));
            return true;
        } else if (type.equals("GROUP")) {
            String groupId = args[3].toLowerCase(Locale.ROOT);
            Optional<ItemBundleDefinition> optionalGroup = plugin.itemRegistry().getBundle(groupId);
            if (optionalGroup.isEmpty()) {
                sender.sendMessage(plugin.language().message("kit-not-found", Map.of("kit", groupId)));
                return true;
            }

            String prefix = "";
            if (args.length >= 5) {
                prefix = String.join(" ", List.of(args).subList(4, args.length));
            }

            String primaryHex = "FDFF5C";
            String secondaryHex = "FEFF91";
            List<String> gradientColors = List.of(primaryHex);
            String prefixMiniMessage = "";

            if (!prefix.isEmpty()) {
                Optional<String> extractedHex = ColorUtils.extractHex(prefix);
                if (extractedHex.isPresent()) {
                    primaryHex = extractedHex.get();
                    secondaryHex = ColorUtils.secondaryFrom(primaryHex, plugin.settings().secondaryColorMode());
                    gradientColors = ColorUtils.extractHexColors(prefix);
                    if (gradientColors.isEmpty())
                        gradientColors = List.of(primaryHex);
                    prefixMiniMessage = TextUtils.toMiniMessage(prefix);
                } else {
                    prefixMiniMessage = prefix;
                }
            }

            List<ItemStack> builtItems = plugin.itemBundleBuilder().build(
                    optionalGroup.get(),
                    prefix,
                    prefixMiniMessage,
                    primaryHex,
                    secondaryHex,
                    gradientColors,
                    target);

            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(builtItems.toArray(new ItemStack[0]));
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

            PluginSettings.SoundSettings sound = plugin.settings().soundSettings();
            if (sound.enabled()) {
                target.playSound(target.getLocation(), sound.type(), sound.volume(), sound.pitch());
            }

            sender.sendMessage(plugin.language().message("give-success-group",
                    Map.of("player", target.getName(), "group", groupId)));
            return true;
        }

        sender.sendMessage(plugin.language().message("usage-give"));
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
        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-create")); // Need usage update
            return true;
        }

        // Backwards compatibility for /zmitems create <kit> <prefix>
        if (args.length >= 3 && args[2].contains("#")) {
            return handleItemGive(sender, new String[] { "give", "GROUP", args[1], args[2] });
        }

        String itemName = args[1].toLowerCase(Locale.ROOT);
        if (plugin.itemRegistry().getItem(itemName).isPresent()) {
            player.sendMessage(plugin.language().message("create-gui-name-exists", Map.of("name", itemName)));
            return true;
        }

        ItemCreationGui.open(player, itemName, plugin);
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
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        String label = alias.toLowerCase(Locale.ROOT);
        if ("irename".equals(commandName) || "rename".equals(label)) {
            return completeRename(sender, args);
        }

        if (args.length == 1) {
            return filter(
                    List.of("help", "give", "create", "reload", "material", "info", "lore", "enchant",
                            "rename", "migrate", "item", "glow", "hide", "flag", "unbreakable", "armor_trim",
                            "clone", "action", "effect", "progression", "attribute"),
                    args[0]);
        }
        // /zmitems give <NORMAL|GROUP> <player> <id> [amount|prefix]
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.of("NORMAL", "GROUP"), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            if (args[1].equalsIgnoreCase("NORMAL")) {
                return filter(new ArrayList<>(plugin.itemRegistry().getItemNames()), args[3]);
            } else if (args[1].equalsIgnoreCase("GROUP")) {
                return filter(new ArrayList<>(plugin.itemRegistry().getBundleIds()), args[3]);
            }
        }
        // /zmitems clone <name> [group]
        if (args.length == 2 && "clone".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.itemRegistry().getItemNames()), args[1]);
        }
        if (args.length == 3 && "clone".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.itemRegistry().getBundleIds()), args[2]);
        }
        // /zmitems action <add|remove> <id_item> [type] [click/value] [value...]
        if ("action".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("add", "remove"), args[1]);
            }

            if (args.length == 3) {
                List<String> validIds = new ArrayList<>();
                for (String key : plugin.itemRegistry().getItemNames()) {
                    plugin.itemRegistry().getItem(key).ifPresent(def -> {
                        if (def.itemIdentifier() != null) {
                            validIds.add(def.itemIdentifier());
                        }
                    });
                }
                return filter(validIds, args[2]);
            }

            String sub = args[1].toLowerCase(Locale.ROOT);

            if ("add".equals(sub)) {
                if (args.length == 4) {
                    return filter(ACTION_TYPES, args[3]);
                }
                if (args.length == 5) {
                    String type = args[3].toLowerCase(Locale.ROOT);
                    if (type.equals("uses") || type.equals("cooldown")) {
                        return filter(List.of("1", "3", "5", "10", "30", "60"), args[4]);
                    }
                    return filter(CLICK_TYPES, args[4]);
                }
                if (args.length >= 6) {
                    String type = args[3].toLowerCase(Locale.ROOT);
                    if (type.equals("player_command") || type.equals("console_command")) {
                        return filter(List.of("say %player%", "eco give %player% 100"), args[5]);
                    }
                    if (type.equals("sound")) {
                        return filter(List.of(
                                "ENTITY_PLAYER_LEVELUP", "UI_BUTTON_CLICK", "BLOCK_ANVIL_USE",
                                "ENTITY_VILLAGER_YES", "BLOCK_NOTE_BLOCK_PLING",
                                "ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_PLAYER_BURP",
                                "ENTITY_ITEM_PICKUP", "BLOCK_CHEST_OPEN", "BLOCK_CHEST_CLOSE",
                                "ITEM_ARMOR_EQUIP_DIAMOND", "ITEM_ARMOR_EQUIP_GOLD",
                                "ITEM_ARMOR_EQUIP_IRON", "ITEM_ARMOR_EQUIP_LEATHER",
                                "ENTITY_PLAYER_ATTACK_STRONG", "ENTITY_PLAYER_ATTACK_WEAK",
                                "ENTITY_ARROW_HIT_PLAYER", "ENTITY_SPLASH_POTION_BREAK",
                                "BLOCK_GLASS_BREAK", "BLOCK_STONE_BREAK",
                                "ENTITY_ENDERMAN_TELEPORT", "ENTITY_ENDERMAN_SCREAM",
                                "ENTITY_CREEPER_HURT", "ENTITY_CREEPER_PRIMED",
                                "ENTITY_LIGHTNING_BOLT_THUNDER", "ENTITY_LIGHTNING_BOLT_IMPACT",
                                "WEATHER_RAIN", "WEATHER_RAIN_ABOVE",
                                "BLOCK_LAVA_POP", "BLOCK_WATER_AMBIENT",
                                "ENTITY_HORSE_SADDLE", "ENTITY_PIG_SADDLE",
                                "BLOCK_SIGN_POST", "BLOCK_WOOL_BREAK",
                                "ITEM_SHIELD_BLOCK", "ITEM_SHIELD_BREAK"), args[5]);
                    }
                }
            }

            return Collections.emptyList();
        }
        // /zmitems effect <add|remove|list> <id_item> <effect> <amplifier>
        // <slot>
        if ("effect".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("add", "remove", "list"), args[1]);
            }

            if (args.length == 3) {
                List<String> validIds = new ArrayList<>();
                for (String key : plugin.itemRegistry().getItemNames()) {
                    plugin.itemRegistry().getItem(key).ifPresent(def -> {
                        if (def.itemIdentifier() != null) {
                            validIds.add(def.itemIdentifier());
                        }
                    });
                }
                return filter(validIds, args[2]);
            }

            String sub = args[1].toLowerCase(Locale.ROOT);

            if ("add".equals(sub)) {
                if (args.length == 4) {
                    return filter(Arrays.stream(org.bukkit.potion.PotionEffectType.values())
                            .filter(java.util.Objects::nonNull)
                            .map(org.bukkit.potion.PotionEffectType::getName)
                            .toList(), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("1", "2", "3", "4", "5"), args[4]);
                }
                if (args.length == 6) {
                    return filter(List.of("MAIN_HAND", "OFF_HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ANY"),
                            args[5]);
                }
            } else if ("remove".equals(sub)) {
                if (args.length == 4) {
                    String idItemRaw = args[2].toLowerCase(Locale.ROOT);
                    String actualId = null;
                    for (String key : plugin.itemRegistry().getItemNames()) {
                        ItemDefinition def = plugin.itemRegistry().getItem(key).orElse(null);
                        if (def != null && def.itemIdentifier() != null
                                && def.itemIdentifier().equalsIgnoreCase(idItemRaw)) {
                            actualId = def.itemIdentifier();
                            break;
                        }
                    }
                    if (actualId != null) {
                        return filter(plugin.itemDataStore().getEffects(actualId).stream()
                                .map(ItemDataStore.ItemEffectData::type)
                                .toList(), args[3]);
                    }
                }
            }

            return Collections.emptyList();
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return filter(plugin.itemRegistry().getBundleIds(), args[1]);
        }
        if (args.length == 2 && "material".equalsIgnoreCase(args[0])) {
            return filter(MATERIAL_SUGGESTIONS, args[1]);
        }
        if (args.length == 2 && "enchant".equalsIgnoreCase(args[0])) {
            return filter(ENCHANT_SUGGESTIONS, args[1]);
        }
        if (args.length == 3 && "enchant".equalsIgnoreCase(args[0])) {
            return filter(ENCHANT_AMOUNT_SUGGESTIONS, args[2]);
        }
        if (args.length == 2 && "rename".equalsIgnoreCase(args[0])) {
            return completeRename(sender, new String[] { args[1] });
        }
        if (args.length == 2 && "lore".equalsIgnoreCase(args[0])) {
            return filter(LORE_SUB_ACTIONS, args[1]);
        }
        if (args.length == 2 && "hide".equalsIgnoreCase(args[0])) {
            return filter(Arrays.stream(ItemFlag.values()).map(Enum::name).toList(), args[1]);
        }
        if (args.length == 2 && "flag".equalsIgnoreCase(args[0])) {
            return filter(Arrays.stream(ItemBehaviorFlag.values()).map(Enum::name).toList(), args[1]);
        }
        if (args.length == 2 && "armor_trim".equalsIgnoreCase(args[0])) {
            List<String> list = new ArrayList<>();
            list.add("remove");
            Registry.TRIM_MATERIAL.forEach(m -> list.add(m.getKey().getKey()));
            return filter(list, args[1]);
        }
        if (args.length == 3 && "armor_trim".equalsIgnoreCase(args[0]) && !args[1].equalsIgnoreCase("remove")) {
            List<String> list = new ArrayList<>();
            Registry.TRIM_PATTERN.forEach(p -> list.add(p.getKey().getKey()));
            return filter(list, args[2]);
        }
        if ("progression".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(new ArrayList<>(plugin.itemRegistry().getItemNames()), args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("enchant", "attribute"), args[2]);
            }
            return Collections.emptyList();
        }
        if ("attribute".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("add", "remove"), args[1]);
            }
            String sub = args[1].toLowerCase(Locale.ROOT);
            if ("add".equals(sub)) {
                if (args.length == 3) {
                    List<String> list = new ArrayList<>();
                    list.add("hand");
                    list.addAll(plugin.itemRegistry().getItemNames());
                    return filter(list, args[2]);
                }
                if (args.length == 4) {
                    return filter(getAttributeSuggestions(), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("1", "0.5", "2", "5", "10"), args[4]);
                }
                if (args.length == 6) {
                    return filter(List.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1", "ADD_PERCENTAGE"), args[5]);
                }
                if (args.length == 7) {
                    return filter(List.of("MAIN_HAND", "OFF_HAND", "HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
                            "ANY", "ALL"), args[6]);
                }
            } else if ("remove".equals(sub)) {
                if (args.length == 3) {
                    return filter(getAttributeSuggestions(), args[2]);
                }
                if (args.length == 4) {
                    List<String> list = new ArrayList<>();
                    list.add("hand");
                    list.addAll(plugin.itemRegistry().getItemNames());
                    return filter(list, args[3]);
                }
            }
            return Collections.emptyList();
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
            if (args.length == 6 && "give".equalsIgnoreCase(args[1])) {
                return filter(List.of("true", "false"), args[5]);
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
                if (action.equals("add")) {
                    return filter(quoteLoreSuggestions(rawLore), args[2]);
                }
            }

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
                        return Collections.singletonList(quoteLoreLineForCompletion(rawLore.get(lineNum - 1)));
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private boolean handleProgression(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.progression")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        ProgressionGui gui = new ProgressionGui(plugin);

        if (args.length == 1) {
            gui.openItemSelect(player);
            return true;
        }

        String itemKey = args[1].toLowerCase(Locale.ROOT);
        Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(itemKey);

        if (defOpt.isEmpty()) {
            sender.sendMessage(plugin.language().message("attribute-item-not-found", Map.of("item", itemKey)));
            return true;
        }

        String displayName = itemKey;
        if (defOpt.get().displayName() != null) {
            displayName = dev.zm.itemsbuilder.util.ColorUtils.stripColorCodes(defOpt.get().displayName());
        }

        if (args.length >= 3) {
            String shortcut = args[2].toLowerCase(Locale.ROOT);
            if ("enchant".equals(shortcut)) {
                gui.openEnchantShortcut(player, itemKey);
                return true;
            } else if ("attribute".equals(shortcut)) {
                gui.openAttributeShortcut(player, itemKey);
                return true;
            }
        }

        gui.openTypeSelect(player, itemKey, displayName);
        return true;
    }

    private String resolveItemKeyForProgression(ItemStack item) {
        String sourceKey = dev.zm.itemsbuilder.util.ItemIdentityStore.readSourceKey(plugin, item);
        if (sourceKey != null && plugin.itemRegistry().getItem(sourceKey).isPresent()) {
            return sourceKey;
        }

        String idItem = dev.zm.itemsbuilder.util.ItemIdentityStore.read(plugin, item);
        if (idItem != null) {
            for (String key : plugin.itemRegistry().getItemNames()) {
                Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(key);
                if (defOpt.isPresent() && idItem.equals(defOpt.get().itemIdentifier())) {
                    return key;
                }
            }
        }

        return null;
    }

    private boolean handleAttribute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmitemsbuilder.attribute")) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.language().message("usage-attribute"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> handleAttributeAdd(sender, args);
            case "remove" -> handleAttributeRemove(sender, args);
            default -> {
                sender.sendMessage(plugin.language().message("usage-attribute"));
                yield true;
            }
        };
    }

    private boolean handleAttributeAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        if (args.length < 7) {
            sender.sendMessage(plugin.language().message("usage-attribute-add"));
            return true;
        }

        String targetType = args[2].toLowerCase(Locale.ROOT);
        String attributeName = args[3].toUpperCase(Locale.ROOT);
        double value;
        try {
            value = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.language().message("attribute-invalid-value", Map.of("value", args[4])));
            return true;
        }
        String operation = args[5].toUpperCase(Locale.ROOT);
        String slot = args[6].toUpperCase(Locale.ROOT);

        if (!isValidAttributeName(attributeName)) {
            sender.sendMessage(plugin.language().message("attribute-invalid-name", Map.of("attribute", attributeName)));
            return true;
        }

        if (!isValidAttributeOperation(operation)) {
            sender.sendMessage(
                    plugin.language().message("attribute-invalid-operation", Map.of("operation", operation)));
            return true;
        }

        if (!isValidAttributeSlot(slot)) {
            sender.sendMessage(plugin.language().message("attribute-invalid-slot", Map.of("slot", slot)));
            return true;
        }

        if ("hand".equals(targetType)) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) {
                sender.sendMessage(plugin.language().message("no-item-in-hand"));
                return true;
            }
            return applyAttributeToItemStack(player, inHand, attributeName, value, operation, sender);
        }

        String itemKey = targetType;
        if (!plugin.itemRegistry().getItem(itemKey).isPresent()) {
            sender.sendMessage(plugin.language().message("attribute-item-not-found", Map.of("item", itemKey)));
            return true;
        }
        return modifyItemAttribute(player, itemKey, attributeName, value, operation, slot, null, true, sender);
    }

    private boolean applyAttributeToItemStack(Player player, ItemStack item, String attributeName,
            double value, String operation, CommandSender sender) {
        try {
            Attribute attr = Attribute.valueOf(attributeName);
            AttributeModifier.Operation op = convertOperation(operation);
            if (op == null) {
                sender.sendMessage(
                        plugin.language().message("attribute-invalid-operation", Map.of("operation", operation)));
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null)
                return true;

            meta.removeAttributeModifier(attr);

            UUID uuid = UUID.randomUUID();
            AttributeModifier modifier = new AttributeModifier(uuid, "custom_" + attributeName.toLowerCase(), value,
                    op);
            meta.addAttributeModifier(attr, modifier);

            item.setItemMeta(meta);
            player.getInventory().setItemInMainHand(item);
            sender.sendMessage(plugin.language().message("attribute-add-success",
                    Map.of("attribute", attributeName, "item", "hand")));
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(plugin.language().message("attribute-invalid-name", Map.of("attribute", attributeName)));
            return true;
        }
    }

    private boolean handleAttributeRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.language().message("usage-attribute-remove"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().message("player-only"));
            return true;
        }

        String attributeName = args[2].toUpperCase(Locale.ROOT);
        String targetType = args[3].toLowerCase(Locale.ROOT);

        if (!isValidAttributeName(attributeName)) {
            sender.sendMessage(plugin.language().message("attribute-invalid-name", Map.of("attribute", attributeName)));
            return true;
        }

        if ("hand".equals(targetType)) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) {
                sender.sendMessage(plugin.language().message("no-item-in-hand"));
                return true;
            }
            return removeAttributeFromItemStack(player, inHand, attributeName, sender);
        }

        String itemKey = targetType;
        if (!plugin.itemRegistry().getItem(itemKey).isPresent()) {
            sender.sendMessage(plugin.language().message("attribute-item-not-found", Map.of("item", itemKey)));
            return true;
        }
        return modifyItemAttribute(player, itemKey, attributeName, 0, null, null, null, false, sender);
    }

    private boolean removeAttributeFromItemStack(Player player, ItemStack item, String attributeName,
            CommandSender sender) {
        try {
            Attribute attr = Attribute.valueOf(attributeName);
            ItemMeta meta = item.getItemMeta();
            if (meta == null)
                return true;

            meta.removeAttributeModifier(attr);
            item.setItemMeta(meta);
            player.getInventory().setItemInMainHand(item);
            sender.sendMessage(plugin.language().message("attribute-remove-success",
                    Map.of("attribute", attributeName, "item", "hand")));
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(plugin.language().message("attribute-invalid-name", Map.of("attribute", attributeName)));
            return true;
        }
    }

    private AttributeModifier.Operation convertOperation(String operation) {
        if (operation == null)
            return null;
        switch (operation.toUpperCase(Locale.ROOT)) {
            case "ADD_NUMBER":
                return AttributeModifier.Operation.ADD_NUMBER;
            case "ADD_SCALAR":
                return AttributeModifier.Operation.ADD_SCALAR;
            case "MULTIPLY_SCALAR_1":
                return AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            case "ADD_PERCENTAGE":
                return AttributeModifier.Operation.ADD_SCALAR;
            default:
                return null;
        }
    }

    private String resolveItemKeyForAttribute(ItemStack item) {
        String sourceKey = dev.zm.itemsbuilder.util.ItemIdentityStore.readSourceKey(plugin, item);
        if (sourceKey != null && plugin.itemRegistry().getItem(sourceKey).isPresent()) {
            return sourceKey;
        }

        String idItem = dev.zm.itemsbuilder.util.ItemIdentityStore.read(plugin, item);
        if (idItem != null) {
            for (String key : plugin.itemRegistry().getItemNames()) {
                Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(key);
                if (defOpt.isPresent() && idItem.equals(defOpt.get().itemIdentifier())) {
                    return key;
                }
            }
        }

        return null;
    }

    private boolean isValidAttributeName(String name) {
        try {
            org.bukkit.attribute.Attribute.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidAttributeOperation(String operation) {
        return switch (operation) {
            case "ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1", "ADD_PERCENTAGE" -> true;
            default -> false;
        };
    }

    private boolean isValidAttributeSlot(String slot) {
        return switch (slot) {
            case "MAIN_HAND", "OFF_HAND", "HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ANY", "ALL" -> true;
            default -> false;
        };
    }

    private boolean modifyItemAttribute(Player player, String itemKey, String attributeName, double value,
            String operation, String slot, ItemStack targetItem, boolean isAdd, CommandSender sender) {
        Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(itemKey);
        if (defOpt.isEmpty()) {
            sender.sendMessage(plugin.language().message("attribute-item-not-found", Map.of("item", itemKey)));
            return true;
        }

        ItemDefinition def = defOpt.get();
        List<AttributeSettings> currentAttributes = new ArrayList<>(def.attributes());
        boolean found = false;
        List<AttributeSettings> updatedAttributes = new ArrayList<>();

        for (AttributeSettings attr : currentAttributes) {
            if (attr.attribute().equalsIgnoreCase(attributeName)) {
                found = true;
                if (isAdd) {
                    String finalSlot = "HAND".equals(slot) ? "MAIN_HAND" : slot;
                    AttributeSettings newAttr = new AttributeSettings(
                            attr.id(),
                            attr.attribute(),
                            dev.zm.itemsbuilder.builder.model.NumberRule.fixed(value),
                            operation,
                            finalSlot);
                    updatedAttributes.add(newAttr);
                }
            } else {
                updatedAttributes.add(attr);
            }
        }

        if (isAdd && !found) {
            String attrId = attributeName.toLowerCase(Locale.ROOT);
            String finalSlot = "HAND".equals(slot) ? "MAIN_HAND" : slot;
            AttributeSettings newAttr = new AttributeSettings(
                    attrId,
                    attributeName,
                    dev.zm.itemsbuilder.builder.model.NumberRule.fixed(value),
                    operation,
                    finalSlot);
            updatedAttributes.add(newAttr);
        }

        if (!isAdd && !found) {
            sender.sendMessage(plugin.language().message("attribute-not-found",
                    Map.of("attribute", attributeName, "item", itemKey)));
            return true;
        }

        return saveItemAttributes(player, itemKey, updatedAttributes, targetItem, isAdd, attributeName, sender);
    }

    private boolean saveItemAttributes(Player player, String itemKey, List<AttributeSettings> attributes,
            ItemStack targetItem, boolean isAdd, String attributeName, CommandSender sender) {
        ItemsConfig itemsConfig = plugin.itemsConfig();
        YamlConfiguration itemsYaml = itemsConfig.getItemsYaml();
        if (itemsYaml == null) {
            sender.sendMessage(plugin.language().message("attribute-save-failed"));
            return true;
        }

        String path = "items." + itemKey + ".attributes";
        Map<String, Object> attrsMap = new LinkedHashMap<>();

        for (AttributeSettings attr : attributes) {
            Map<String, Object> attrMap = new LinkedHashMap<>();
            attrMap.put("attribute", attr.attribute());
            attrMap.put("amount", serializeAmount(attr.amount()));
            attrMap.put("operation", attr.operation());
            attrMap.put("slot", attr.slot());
            attrsMap.put(attr.id(), attrMap);
        }

        itemsYaml.set(path, attrsMap);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean saved = itemsConfig.saveToDiskFromEditor();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (saved) {
                    plugin.reloadPluginState();
                    sender.sendMessage(
                            plugin.language().message(isAdd ? "attribute-add-success" : "attribute-remove-success",
                                    Map.of("attribute", attributeName, "item", itemKey)));
                } else {
                    sender.sendMessage(plugin.language().message("attribute-save-failed"));
                }
            });
        });

        return true;
    }

    private Object serializeAmount(dev.zm.itemsbuilder.builder.model.NumberRule rule) {
        if (rule == null)
            return 0.0;
        if (rule.expression() != null)
            return rule.expression();
        if (rule.fixedValue() != null)
            return rule.fixedValue();
        if (rule.base() != null && rule.perLevel() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("base", rule.base());
            m.put("per-level", rule.perLevel());
            if (rule.min() != null)
                m.put("min", rule.min());
            if (rule.max() != null)
                m.put("max", rule.max());
            return m;
        }
        if (rule.base() != null && rule.every() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("base", rule.base());
            m.put("every", rule.every());
            m.put("bonus", rule.bonus());
            if (rule.min() != null)
                m.put("min", rule.min());
            if (rule.max() != null)
                m.put("max", rule.max());
            return m;
        }
        if (rule.base() != null)
            return rule.base();
        return 0.0;
    }

    private ItemDefinition definitionFromItemKey(String itemKey) {
        return plugin.itemRegistry().getItem(itemKey).orElse(null);
    }

    private boolean handleHelp(CommandSender sender) {
        if (sender instanceof Player player) {
            HelpGui.open(player, plugin);
        } else {
            List<Component> lines = plugin.language().rawMessageList("messages.help-lines", Map.of());
            for (Component line : lines) {
                sender.sendMessage(line);
            }
        }
        return true;
    }

    private List<String> filter(Collection<String> source, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .sorted()
                .toList();
    }

    private List<String> completeRename(CommandSender sender, String[] partialArgs) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        ItemMeta meta = (inHand != null && !inHand.getType().isAir()) ? inHand.getItemMeta() : null;
        String rawSuggestion = currentRenameSuggestionRaw(meta);
        if (rawSuggestion == null) {
            return Collections.emptyList();
        }

        String input = partialArgs.length == 0 ? "" : partialArgs[0];
        String quoted = quoteInput(rawSuggestion);
        if (input == null || input.isBlank()) {
            return List.of(quoted);
        }

        String lowered = input.toLowerCase(Locale.ROOT);
        String plain = ColorUtils.stripColorCodes(rawSuggestion).toLowerCase(Locale.ROOT);
        String quotedLower = quoted.toLowerCase(Locale.ROOT);
        if (quotedLower.startsWith(lowered) || rawSuggestion.toLowerCase(Locale.ROOT).startsWith(lowered)
                || plain.startsWith(lowered)) {
            return List.of(quoted);
        }
        return Collections.emptyList();
    }

    private String currentRenameSuggestionRaw(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        Component displayName = meta.displayName();
        if (displayName == null) {
            return null;
        }
        String raw = LEGACY_AMP.serialize(displayName);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
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

    private static List<String> buildEnchantSuggestions() {
        List<String> enchants = new ArrayList<>();
        try {
            Registry.ENCHANTMENT.stream()
                    .map(Enchantment::getKey)
                    .map(key -> key.getKey().toLowerCase(Locale.ROOT))
                    .distinct()
                    .sorted()
                    .forEach(enchants::add);
        } catch (Exception ignored) {
            for (Enchantment enchantment : Enchantment.values()) {
                if (enchantment != null && enchantment.getKey() != null) {
                    enchants.add(enchantment.getKey().getKey().toLowerCase(Locale.ROOT));
                }
            }
            enchants.sort(String::compareTo);
        }
        return List.copyOf(enchants);
    }

    private static List<String> getAttributeSuggestions() {
        List<String> attributes = new ArrayList<>();
        try {
            Registry.ATTRIBUTE.stream()
                    .map(Attribute::getKey)
                    .map(key -> key.getKey().toUpperCase(Locale.ROOT))
                    .filter(name -> !name.startsWith("GENERIC_"))
                    .distinct()
                    .sorted()
                    .forEach(attributes::add);
        } catch (Exception ignored) {
            attributes.addAll(List.of(
                    "ATTACK_DAMAGE", "ATTACK_SPEED", "ARMOR", "ARMOR_TOUGHNESS",
                    "MAX_HEALTH", "MOVEMENT_SPEED", "FLYING_SPEED", "KNOCKBACK_RESISTANCE",
                    "ATTACK_KNOCKBACK", "MAX_ABSORPTION", "SAFE_FALL_DISTANCE",
                    "GRAVITY", "JUMP_STRENGTH", "STEP_HEIGHT", "BLOCK_INTERACTION_RANGE",
                    "ENTITY_INTERACTION_RANGE", "SCALE", "LUCK"));
            attributes.sort(String::compareTo);
        }
        return List.copyOf(attributes);
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

    private static int parseNonNegativeInt(String raw) {
        try {
            int v = Integer.parseInt(raw);
            return v >= 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseDuration(String input) {
        if (input == null || input.isEmpty())
            return -1;
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("s")) {
                int val = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
                return val > 0 ? val * 20 : -1;
            } else if (trimmed.endsWith("m")) {
                int val = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
                return val > 0 ? val * 1200 : -1;
            } else if (trimmed.endsWith("h")) {
                int val = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
                return val > 0 ? val * 72000 : -1;
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatDuration(int ticks) {
        if (ticks <= 0)
            return "0";
        if (ticks % 72000 == 0) {
            int h = ticks / 72000;
            return h + "h";
        }
        if (ticks % 1200 == 0) {
            int m = ticks / 1200;
            return m + "m";
        }
        if (ticks % 20 == 0) {
            int s = ticks / 20;
            return s + "s";
        }
        return String.valueOf(ticks);
    }

    private static List<String> quoteLoreSuggestions(List<String> rawLore) {
        if (rawLore == null || rawLore.isEmpty()) {
            return List.of();
        }
        List<String> quoted = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            quoted.add(quoteLoreLineForCompletion(line));
        }
        return List.copyOf(quoted);
    }

    private static String quoteLoreLine(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String quoteLoreLineForCompletion(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        int leadingSpaces = 0;
        while (leadingSpaces < raw.length() && raw.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }

        StringBuilder builder = new StringBuilder(raw.length() + leadingSpaces);
        builder.append('"');
        for (int i = 0; i < leadingSpaces; i++) {
            builder.append("\\s");
        }
        builder.append(raw.substring(leadingSpaces)
                .replace("\\", "\\\\")
                .replace("\"", "\\\""));
        builder.append('"');
        return builder.toString();
    }

    private static String quoteInput(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String unwrapQuotedText(String raw) {
        if (raw == null || raw.length() < 2) {
            return raw;
        }
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            String inner = raw.substring(1, raw.length() - 1);
            return inner.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\s", " ");
        }
        return raw;
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
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(component);
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

    private static final class MigrationInventoryHolder implements InventoryHolder {
        private Inventory inventory;
        private List<ItemStack> returnItems = List.of();
        private boolean returned;
        private int totalCount;
        private int migratedCount;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public void setReturnItems(List<ItemStack> returnItems) {
            this.returnItems = returnItems == null ? List.of() : List.copyOf(returnItems);
        }

        public List<ItemStack> consumeReturnItems() {
            return returnItems;
        }

        public void markReturned() {
            this.returned = true;
        }

        public boolean hasReturned() {
            return returned;
        }

        public void setMigrationStats(int totalCount, int migratedCount) {
            this.totalCount = totalCount;
            this.migratedCount = migratedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getMigratedCount() {
            return migratedCount;
        }
    }
}