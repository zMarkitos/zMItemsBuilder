package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.AttributeSettings;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.config.ItemsConfig;
import dev.zm.itemsbuilder.util.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * GUI manager for /zmitems progression.
 *
 * Flow:
 * openItemSelect (no args) → TYPE_SELECT (Enchantments / Attributes)
 * → click type → ITEM_SELECT (all items from items.yml, paginated)
 * → click item → KEY_SELECT (all server enchants/attrs, paginated)
 * → click key → MODE_SELECT (Per Level / Interval)
 * → click mode → EDITOR (field values editor)
 * → click field → AWAITING_INPUT (chat prompt)
 * → type value → EDITOR (back with value set)
 * → Save → save to items.yml, close session
 * → Cancel → return to MODE_SELECT
 *
 * Navigation (all "back" buttons live in the same bottom-row layout):
 * slot 45 = back, 47 = prev page, 49 = close, 51 = next page (paginated GUIs)
 * TYPE_SELECT / MODE_SELECT (27-slot menus) use their own small layout.
 *
 * Shortcuts (direct entry from command args):
 * openTypeSelect → skips TYPE_SELECT, goes straight to ITEM_SELECT... actually
 * pre-selects the item and opens TYPE_SELECT (kept for /zmitems progression
 * <item>)
 * openEnchantShortcut / openAttributeShortcut → skip straight to KEY_SELECT
 */
public class ProgressionGui implements Listener {

    // ─── Slot layout for 54-slot paginated GUIs ───────────────────────
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = CONTENT_SLOTS.length; // 28

    private static final int NAV_BACK_SLOT = 45;
    private static final int NAV_PREV_SLOT = 47;
    private static final int NAV_CLOSE_SLOT = 49;
    private static final int NAV_NEXT_SLOT = 51;

    private static final Map<UUID, ProgressionSession> sessions = new ConcurrentHashMap<>();

    private final zMItemsBuilder plugin;

    public ProgressionGui(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public static boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public static ProgressionSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public static void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public void openItemSelect(Player player) {
        ProgressionSession session = new ProgressionSession(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        buildTypeSelect(session);
        player.openInventory(session.getHolder().getInventory());
    }

    /**
     * Opens the type-select GUI with a pre-selected item.
     * Used when /zmitems progression &lt;item&gt; is given.
     */
    public void openTypeSelect(Player player, String itemKey, String itemDisplayName) {
        ProgressionSession session = new ProgressionSession(player.getUniqueId(), itemKey, itemDisplayName);
        sessions.put(player.getUniqueId(), session);
        buildTypeSelect(session);
        player.openInventory(session.getHolder().getInventory());
    }

    /**
     * Opens the enchantment key-select GUI directly.
     * Used when /zmitems progression &lt;item&gt; enchant is given.
     */
    public void openEnchantShortcut(Player player, String itemKey) {
        String displayName = resolveItemDisplayName(itemKey);
        ProgressionSession session = new ProgressionSession(player.getUniqueId(), itemKey, displayName);
        session.progressionType = ProgressionSession.ProgressionType.ENCHANTMENT;
        session.phase = ProgressionSession.GuiPhase.KEY_SELECT;
        sessions.put(player.getUniqueId(), session);
        buildKeySelect(session);
        player.openInventory(session.getHolder().getInventory());
    }

    /**
     * Opens the attribute key-select GUI directly.
     * Used when /zmitems progression &lt;item&gt; attribute is given.
     */
    public void openAttributeShortcut(Player player, String itemKey) {
        String displayName = resolveItemDisplayName(itemKey);
        ProgressionSession session = new ProgressionSession(player.getUniqueId(), itemKey, displayName);
        session.progressionType = ProgressionSession.ProgressionType.ATTRIBUTE;
        session.phase = ProgressionSession.GuiPhase.KEY_SELECT;
        sessions.put(player.getUniqueId(), session);
        buildKeySelect(session);
        player.openInventory(session.getHolder().getInventory());
    }

    /** ITEM_SELECT: 54-slot paginated list of all items in items.yml */
    private void buildItemSelect(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        Component title = plugin.language().rawMessage("messages.progression-item-select-title");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 45; i < 54; i++)
            inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        List<String> itemKeys = new ArrayList<>(plugin.itemRegistry().getItemNames());
        itemKeys.sort(String::compareTo);

        int totalPages = Math.max(1, (int) Math.ceil(itemKeys.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(session.itemPage, totalPages - 1));
        session.itemPage = page;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, itemKeys.size());

        for (int slot : CONTENT_SLOTS) {
            inv.setItem(slot, null);
        }

        for (int i = start; i < end; i++) {
            String key = itemKeys.get(i);
            inv.setItem(CONTENT_SLOTS[i - start], createSelectableItemEntry(key));
        }

        // Navigation: Back(45), Prev(47), Close(49), Next(51)
        inv.setItem(NAV_BACK_SLOT, createBackButton());
        if (page > 0) {
            inv.setItem(NAV_PREV_SLOT, createNavButton(
                    "messages.progression-gui-prev-page-name",
                    "messages.progression-gui-prev-page-lore",
                    Material.ARROW));
        }
        inv.setItem(NAV_CLOSE_SLOT, createCloseButton());
        if (page < totalPages - 1) {
            inv.setItem(NAV_NEXT_SLOT, createNavButton(
                    "messages.progression-gui-next-page-name",
                    "messages.progression-gui-next-page-lore",
                    Material.ARROW));
        }
    }

    /** TYPE_SELECT: 27-slot menu to choose Enchantments or Attributes */
    private void buildTypeSelect(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        Component title = plugin.language().rawMessage("messages.progression-gui-title");
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 18; i < 27; i++)
            inv.setItem(i, border);

        inv.setItem(4, createItemInfo(session));

        inv.setItem(11, createTypeItem(
                Material.ENCHANTED_BOOK,
                "messages.progression-type-enchant-name",
                "messages.progression-type-enchant-lore"));
        inv.setItem(15, createTypeItem(
                Material.NETHERITE_CHESTPLATE,
                "messages.progression-type-attribute-name",
                "messages.progression-type-attribute-lore"));

        // TYPE_SELECT is the root screen — no "back" state to return to, just close.
        inv.setItem(22, createCloseButton());
    }

    /**
     * KEY_SELECT: 54-slot paginated list of ALL server enchants/attributes.
     */
    private void buildKeySelect(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        boolean isEnchant = session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT;
        String titleKey = isEnchant
                ? "messages.progression-key-enchant-title"
                : "messages.progression-key-attribute-title";
        Component title = plugin.language().rawMessage(titleKey);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 45; i < 54; i++)
            inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        inv.setItem(4, createItemInfo(session));

        List<String> allKeys = isEnchant ? getAllEnchantKeys() : getAllAttributeKeys();

        int totalPages = Math.max(1, (int) Math.ceil(allKeys.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(session.keyPage, totalPages - 1));
        session.keyPage = page;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allKeys.size());

        for (int slot : CONTENT_SLOTS) {
            inv.setItem(slot, null);
        }

        for (int i = start; i < end; i++) {
            String key = allKeys.get(i);
            inv.setItem(CONTENT_SLOTS[i - start], createKeyItem(session, key));
        }

        // Navigation: Back(45) → ITEM_SELECT, Prev(47), Close(49), Next(51)
        inv.setItem(NAV_BACK_SLOT, createBackButton());
        if (page > 0) {
            inv.setItem(NAV_PREV_SLOT, createNavButton(
                    "messages.progression-gui-prev-page-name",
                    "messages.progression-gui-prev-page-lore",
                    Material.SPECTRAL_ARROW));
        }
        inv.setItem(NAV_CLOSE_SLOT, createCloseButton());
        if (page < totalPages - 1) {
            inv.setItem(NAV_NEXT_SLOT, createNavButton(
                    "messages.progression-gui-next-page-name",
                    "messages.progression-gui-next-page-lore",
                    Material.SPECTRAL_ARROW));
        }
    }

    /** MODE_SELECT: 27-slot menu to choose Per Level or Interval */
    private void buildModeSelect(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        Component title = plugin.language().rawMessage("messages.progression-mode-title");
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 18; i < 27; i++)
            inv.setItem(i, border);

        inv.setItem(4, createKeyInfo(session));

        inv.setItem(11, createModeItem(
                Material.CLOCK,
                "messages.progression-mode-perlevel-name",
                "messages.progression-mode-perlevel-lore"));
        inv.setItem(15, createModeItem(
                Material.COMPARATOR,
                "messages.progression-mode-interval-name",
                "messages.progression-mode-interval-lore"));

        inv.setItem(9, createBackButton());
        inv.setItem(22, createCloseButton());
    }

    private void buildEditor(ProgressionSession session) {
        if (session.mode == ProgressionSession.ProgressionMode.PER_LEVEL) {
            buildPerLevelEditor(session);
        } else {
            buildIntervalEditor(session);
        }
    }

    private void buildPerLevelEditor(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        Component title = plugin.language().rawMessage("messages.progression-editor-title");
        Inventory inv = Bukkit.createInventory(holder, 45, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 36; i < 45; i++)
            inv.setItem(i, border);

        inv.setItem(4, createItemInfo(session));

        inv.setItem(12, createFieldItem(session, ProgressionSession.InputField.BASE,
                "messages.progression-field-base-name", "messages.progression-field-base-lore"));
        inv.setItem(14, createFieldItem(session, ProgressionSession.InputField.PER_LEVEL,
                "messages.progression-field-perlevel-name", "messages.progression-field-perlevel-lore"));
        // MIN eliminado (slot 20)
        inv.setItem(20, border); // antes era MIN
        inv.setItem(24, createFieldItem(session, ProgressionSession.InputField.MAX,
                "messages.progression-field-max-name", "messages.progression-field-max-lore"));

        if (session.progressionType == ProgressionSession.ProgressionType.ATTRIBUTE) {
            inv.setItem(30, createAttrOperationItem(session));
            inv.setItem(32, createAttrSlotItem(session));
        }

        inv.setItem(29, createSaveButton());
        inv.setItem(33, createCancelButton());
    }

    private void buildIntervalEditor(ProgressionSession session) {
        ProgressionInventoryHolder holder = new ProgressionInventoryHolder(session);
        Component title = plugin.language().rawMessage("messages.progression-editor-title");
        Inventory inv = Bukkit.createInventory(holder, 45, title);
        holder.setInventory(inv);
        session.setHolder(holder);

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++)
            inv.setItem(i, border);
        for (int i = 36; i < 45; i++)
            inv.setItem(i, border);

        inv.setItem(4, createItemInfo(session));

        inv.setItem(11, createFieldItem(session, ProgressionSession.InputField.BASE,
                "messages.progression-field-base-name", "messages.progression-field-base-lore"));
        inv.setItem(13, createFieldItem(session, ProgressionSession.InputField.EVERY,
                "messages.progression-field-every-name", "messages.progression-field-every-lore"));
        inv.setItem(15, createFieldItem(session, ProgressionSession.InputField.BONUS,
                "messages.progression-field-bonus-name", "messages.progression-field-bonus-lore"));
        inv.setItem(20, border);
        inv.setItem(24, createFieldItem(session, ProgressionSession.InputField.MAX,
                "messages.progression-field-max-name", "messages.progression-field-max-lore"));

        if (session.progressionType == ProgressionSession.ProgressionType.ATTRIBUTE) {
            inv.setItem(30, createAttrOperationItem(session));
            inv.setItem(32, createAttrSlotItem(session));
        }

        inv.setItem(29, createSaveButton());
        inv.setItem(33, createCancelButton());
    }

    private ItemStack createSelectableItemEntry(String itemKey) {
        Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(itemKey);
        Material mat = Material.PAPER;
        if (defOpt.isPresent()) {
            ItemDefinition def = defOpt.get();
            if (def.material() != null) {
                try {
                    mat = Material.valueOf(def.material().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    mat = Material.PAPER;
                }
            }
        }

        String displayName = resolveItemDisplayName(itemKey);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.toItemComponent("&#F13713&l" + displayName));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &8» &fID: &7" + itemKey));
            lore.add(Component.empty());
            lore.add(plugin.language().rawMessage("messages.progression-item-select-click-lore"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTypeItem(Material mat, String nameKey, String loreKey) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage(nameKey));
            meta.lore(plugin.language().rawMessageList(loreKey, Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createKeyItem(ProgressionSession session, String key) {
        boolean isEnchant = session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT;
        Material mat = isEnchant ? Material.ENCHANTED_BOOK : Material.NETHERITE_INGOT;

        boolean isConfigured = isKeyConfigured(session, key);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = isEnchant
                    ? plugin.language().enchantName(key)
                    : formatAttributeName(key);
            meta.displayName(TextUtils.toItemComponent("&#F13713&l" + displayName));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &8» &7Key: &f" + key));

            if (isConfigured) {
                lore.add(Component.empty());
                lore.add(TextUtils.toItemComponent("  &#55FF55✔ &fYa configurado"));
            }

            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &#55FF55■ &fClick para configurar"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createKeyInfo(ProgressionSession session) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage("messages.progression-gui-item-name",
                    Map.of("item", session.itemDisplayName != null ? session.itemDisplayName : "")));

            String keyName;
            if (session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT) {
                keyName = plugin.language().enchantName(session.selectedKey);
            } else {
                keyName = formatAttributeName(session.selectedKey);
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &7" + keyName));
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createModeItem(Material mat, String nameKey, String loreKey) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage(nameKey));
            meta.lore(plugin.language().rawMessageList(loreKey, Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFieldItem(ProgressionSession session, ProgressionSession.InputField field,
            String nameKey, String loreKey) {
        Material mat = switch (field) {
            case BASE -> Material.EMERALD;
            case PER_LEVEL, EVERY -> Material.REDSTONE;
            case BONUS -> Material.DIAMOND;
            default -> Material.IRON_INGOT;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Object val = session.getFieldValue(field);
            Component valueComponent;
            if (val == null) {
                valueComponent = plugin.language().rawMessage("messages.progression-not-set");
            } else {
                String valueStr = String.valueOf(val);
                String valueWithAmpersand = valueStr.replace('§', '&');
                valueComponent = TextUtils.toItemComponent(valueWithAmpersand);
            }

            Component baseName = plugin.language().rawMessage(nameKey);
            Component finalDisplay = baseName
                    .replaceText(builder -> builder.matchLiteral("{value}").replacement(valueComponent));

            meta.displayName(finalDisplay);
            meta.lore(plugin.language().rawMessageList(loreKey, Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAttrOperationItem(ProgressionSession session) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.toItemComponent("&#F13713&lOperation: &f" + session.attributeOperation));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &7Click to cycle: ADD_NUMBER → ADD_SCALAR → MULTIPLY_SCALAR_1"));
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAttrSlotItem(ProgressionSession session) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.toItemComponent("&#F13713&lSlot: &f" + session.attributeSlot));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &7Click to cycle slot"));
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItemInfo(ProgressionSession session) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = session.itemDisplayName != null ? session.itemDisplayName : "";
            meta.displayName(plugin.language().rawMessage("messages.progression-gui-item-name",
                    Map.of("item", displayName)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (session.progressionType != null) {
                Component typeName = session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT
                        ? plugin.language().rawMessage("messages.progression-type-enchant-name")
                        : plugin.language().rawMessage("messages.progression-type-attribute-name");
                lore.add(Component.empty().append(TextUtils.toItemComponent("  &7")).append(typeName));
            }
            if (session.selectedKey != null) {
                String keyName = session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT
                        ? plugin.language().enchantName(session.selectedKey)
                        : formatAttributeName(session.selectedKey);
                lore.add(TextUtils.toItemComponent("  &7Key: &f" + keyName));
            }
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBorder() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage("messages.button-close-name"));
            meta.lore(plugin.language().rawMessageList("messages.button-close-lore", Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage("messages.help-gui-back-name"));
            List<Component> lore = plugin.language().rawMessageList("messages.help-gui-back-lore", Map.of());
            if (!lore.isEmpty())
                meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavButton(String nameKey, String loreKey, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage(nameKey));
            meta.lore(plugin.language().rawMessageList(loreKey, Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSaveButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage("messages.progression-save-name"));
            meta.lore(plugin.language().rawMessageList("messages.progression-save-lore", Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.language().rawMessage("messages.progression-cancel-name"));
            meta.lore(plugin.language().rawMessageList("messages.progression-cancel-lore", Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProgressionInventoryHolder holder))
            return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;
        ProgressionSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getHolder() != holder)
            return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize())
            return;

        switch (session.phase) {
            case ITEM_SELECT -> handleItemSelectClick(player, session, rawSlot);
            case TYPE_SELECT -> handleTypeSelectClick(player, session, rawSlot);
            case KEY_SELECT -> handleKeySelectClick(player, session, rawSlot);
            case MODE_SELECT -> handleModeSelectClick(player, session, rawSlot);
            case EDITOR -> handleEditorClick(player, session, rawSlot);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProgressionInventoryHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * If the player is in AWAITING_INPUT, the close was triggered by our own code
     * (player.closeInventory() to show the chat prompt), so we keep the session
     * alive. In all other cases, the player closed the GUI intentionally → remove
     * session.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProgressionInventoryHolder holder))
            return;
        Player player = (Player) event.getPlayer();
        ProgressionSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        if (session.phase == ProgressionSession.GuiPhase.AWAITING_INPUT) {
            return;
        }
        if (session.getHolder() != holder) {
            return;
        }

        sessions.remove(player.getUniqueId());
    }

    private void handleItemSelectClick(Player player, ProgressionSession session, int slot) {
        List<String> keys = new ArrayList<>(plugin.itemRegistry().getItemNames());
        keys.sort(String::compareTo);
        int totalPages = Math.max(1, (int) Math.ceil(keys.size() / (double) ITEMS_PER_PAGE));

        if (slot == NAV_BACK_SLOT) {
            session.phase = ProgressionSession.GuiPhase.TYPE_SELECT;
            session.itemKey = null;
            session.itemDisplayName = null;
            buildTypeSelect(session);
            openInventory(player, session);
            return;
        }
        if (slot == NAV_PREV_SLOT) {
            if (session.itemPage > 0) {
                session.itemPage--;
                buildItemSelect(session);
                openInventory(player, session);
            }
            return;
        }
        if (slot == NAV_CLOSE_SLOT) {
            close(player);
            return;
        }
        if (slot == NAV_NEXT_SLOT) {
            if (session.itemPage < totalPages - 1) {
                session.itemPage++;
                buildItemSelect(session);
                openInventory(player, session);
            }
            return;
        }

        int contentIndex = slotToContentIndex(slot);
        if (contentIndex < 0)
            return;

        int globalIndex = session.itemPage * ITEMS_PER_PAGE + contentIndex;
        if (globalIndex >= keys.size())
            return;

        String selectedItemKey = keys.get(globalIndex);
        session.itemKey = selectedItemKey;
        session.itemDisplayName = resolveItemDisplayName(selectedItemKey);
        session.phase = ProgressionSession.GuiPhase.KEY_SELECT;
        session.keyPage = 0;
        buildKeySelect(session);
        openInventory(player, session);
    }

    private void handleTypeSelectClick(Player player, ProgressionSession session, int slot) {
        if (slot == 11) {
            session.progressionType = ProgressionSession.ProgressionType.ENCHANTMENT;
            session.phase = ProgressionSession.GuiPhase.ITEM_SELECT;
            session.itemPage = 0;
            buildItemSelect(session);
            openInventory(player, session);
        } else if (slot == 15) {
            session.progressionType = ProgressionSession.ProgressionType.ATTRIBUTE;
            session.phase = ProgressionSession.GuiPhase.ITEM_SELECT;
            session.itemPage = 0;
            buildItemSelect(session);
            openInventory(player, session);
        } else if (slot == 22) {
            // TYPE_SELECT is the root screen — just close.
            close(player);
        }
    }

    private void handleKeySelectClick(Player player, ProgressionSession session, int slot) {
        boolean isEnchant = session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT;
        List<String> allKeys = isEnchant ? getAllEnchantKeys() : getAllAttributeKeys();
        int totalPages = Math.max(1, (int) Math.ceil(allKeys.size() / (double) ITEMS_PER_PAGE));

        if (slot == NAV_BACK_SLOT) {
            // Back → ITEM_SELECT (keeps progressionType intact)
            session.phase = ProgressionSession.GuiPhase.ITEM_SELECT;
            session.selectedKey = null;
            buildItemSelect(session);
            openInventory(player, session);
            return;
        }
        if (slot == NAV_PREV_SLOT) {
            if (session.keyPage > 0) {
                session.keyPage--;
                buildKeySelect(session);
                openInventory(player, session);
            }
            return;
        }
        if (slot == NAV_CLOSE_SLOT) {
            close(player);
            return;
        }
        if (slot == NAV_NEXT_SLOT) {
            if (session.keyPage < totalPages - 1) {
                session.keyPage++;
                buildKeySelect(session);
                openInventory(player, session);
            }
            return;
        }

        int contentIndex = slotToContentIndex(slot);
        if (contentIndex < 0)
            return;

        int globalIndex = session.keyPage * ITEMS_PER_PAGE + contentIndex;
        if (globalIndex >= allKeys.size())
            return;

        session.selectedKey = allKeys.get(globalIndex);
        loadCurrentValues(session);
        session.phase = ProgressionSession.GuiPhase.MODE_SELECT;
        buildModeSelect(session);
        openInventory(player, session);
    }

    private void handleModeSelectClick(Player player, ProgressionSession session, int slot) {
        if (slot == 11) {
            session.mode = ProgressionSession.ProgressionMode.PER_LEVEL;
            ensureDefaultValues(session);
            session.phase = ProgressionSession.GuiPhase.EDITOR;
            buildEditor(session);
            openInventory(player, session);
        } else if (slot == 15) {
            session.mode = ProgressionSession.ProgressionMode.INTERVAL;
            ensureDefaultValues(session);
            session.phase = ProgressionSession.GuiPhase.EDITOR;
            buildEditor(session);
            openInventory(player, session);
        } else if (slot == 9) {
            // Back → KEY_SELECT
            session.phase = ProgressionSession.GuiPhase.KEY_SELECT;
            session.selectedKey = null;
            session.mode = null;
            buildKeySelect(session);
            openInventory(player, session);
        } else if (slot == 22) {
            close(player);
        }
    }

    private static final List<String> ATTR_OPERATIONS = List.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1");
    private static final List<String> ATTR_SLOTS = List.of(
            "MAIN_HAND", "OFF_HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ANY");

    private void handleEditorClick(Player player, ProgressionSession session, int slot) {
        boolean isPerLevel = session.mode == ProgressionSession.ProgressionMode.PER_LEVEL;

        ProgressionSession.InputField field = null;
        if (isPerLevel) {
            field = switch (slot) {
                case 12 -> ProgressionSession.InputField.BASE;
                case 14 -> ProgressionSession.InputField.PER_LEVEL;
                case 24 -> ProgressionSession.InputField.MAX;
                default -> null;
            };
        } else {
            field = switch (slot) {
                case 11 -> ProgressionSession.InputField.BASE;
                case 13 -> ProgressionSession.InputField.EVERY;
                case 15 -> ProgressionSession.InputField.BONUS;
                case 24 -> ProgressionSession.InputField.MAX;
                default -> null;
            };
        }

        if (field != null) {
            session.awaitingField = field;
            session.phase = ProgressionSession.GuiPhase.AWAITING_INPUT;
            player.closeInventory();
            sendInputPrompt(player, session, field);
            return;
        }

        if (session.progressionType == ProgressionSession.ProgressionType.ATTRIBUTE) {
            if (slot == 30) {
                int idx = ATTR_OPERATIONS.indexOf(session.attributeOperation);
                session.attributeOperation = ATTR_OPERATIONS.get((idx + 1) % ATTR_OPERATIONS.size());
                buildEditor(session);
                openInventory(player, session);
                return;
            }
            if (slot == 32) {
                int idx = ATTR_SLOTS.indexOf(session.attributeSlot);
                session.attributeSlot = ATTR_SLOTS.get((idx + 1) % ATTR_SLOTS.size());
                buildEditor(session);
                openInventory(player, session);
                return;
            }
        }

        if (slot == 29) {
            saveProgression(player, session);
        } else if (slot == 33) {
            session.phase = ProgressionSession.GuiPhase.MODE_SELECT;
            buildModeSelect(session);
            openInventory(player, session);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ProgressionSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase != ProgressionSession.GuiPhase.AWAITING_INPUT)
            return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.awaitingField = null;
                session.phase = ProgressionSession.GuiPhase.EDITOR;
                buildEditor(session);
                openInventory(player, session);
            });
            return;
        }

        ProgressionSession.InputField field = session.awaitingField;
        if (field == null)
            return;

        if (field == ProgressionSession.InputField.EXPRESSION) {
            if (input.isBlank()) {
                sendError(player, session, "progression-invalid-expression");
                return;
            }
            session.setFieldValue(field, input);
        } else if (field.integerOnly()) {
            try {
                int val = Integer.parseInt(input);
                if (val < 0) {
                    sendError(player, session, "progression-invalid-number");
                    return;
                }
                session.setFieldValue(field, val);
            } catch (NumberFormatException e) {
                sendError(player, session, "progression-invalid-integer");
                return;
            }
        } else {
            try {
                double val = Double.parseDouble(input);
                session.setFieldValue(field, val);
            } catch (NumberFormatException e) {
                sendError(player, session, "progression-invalid-number");
                return;
            }
        }

        session.awaitingField = null;
        session.phase = ProgressionSession.GuiPhase.EDITOR;
        Bukkit.getScheduler().runTask(plugin, () -> {
            buildEditor(session);
            openInventory(player, session);
        });
    }

    private void sendInputPrompt(Player player, ProgressionSession session, ProgressionSession.InputField field) {
        String messageKey = switch (field) {
            case BASE -> "progression-input-base";
            case PER_LEVEL -> "progression-input-perlevel";
            case EVERY -> "progression-input-every";
            case BONUS -> "progression-input-bonus";
            case MIN -> "progression-input-min";
            case MAX -> "progression-input-max";
            case EXPRESSION -> "progression-input-expression";
        };
        player.sendMessage(plugin.language().message(messageKey, Map.of()));
    }

    private void sendError(Player player, ProgressionSession session, String messageKey) {
        player.sendMessage(plugin.language().message(messageKey, Map.of()));
        session.awaitingField = null;
        session.phase = ProgressionSession.GuiPhase.EDITOR;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.containsKey(player.getUniqueId())) {
                buildEditor(session);
                openInventory(player, session);
            }
        });
    }

    private void loadCurrentValues(ProgressionSession session) {
        if (session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT) {
            session.enchantValues.clear();
        } else {
            session.attributeValues.clear();
            session.attributeOperation = "ADD_NUMBER";
            session.attributeSlot = "MAIN_HAND";
        }

        if (session.itemKey == null)
            return;

        ItemsConfig itemsConfig = plugin.itemsConfig();
        ConfigurationSection itemsSection = itemsConfig.getItemsSection();
        if (itemsSection == null)
            return;

        ConfigurationSection itemSection = itemsSection.getConfigurationSection(session.itemKey);
        if (itemSection == null)
            return;

        if (session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT) {
            ConfigurationSection enchants = itemSection.getConfigurationSection("enchants");
            if (enchants != null && enchants.isConfigurationSection(session.selectedKey)) {
                ConfigurationSection rule = enchants.getConfigurationSection(session.selectedKey);
                if (rule != null) {
                    copyRuleToMap(rule, session.enchantValues);
                }
            }
        } else {
            ConfigurationSection attrs = itemSection.getConfigurationSection("attributes");
            String attrId = session.selectedKey.toLowerCase(Locale.ROOT);
            if (attrs != null && attrs.isConfigurationSection(attrId)) {
                ConfigurationSection attrSection = attrs.getConfigurationSection(attrId);
                if (attrSection != null) {
                    session.attributeOperation = attrSection.getString("operation", "ADD_NUMBER");
                    session.attributeSlot = attrSection.getString("slot", "MAIN_HAND");
                    Object amountRaw = attrSection.get("amount");
                    if (amountRaw instanceof ConfigurationSection amountSection) {
                        copyRuleToMap(amountSection, session.attributeValues);
                    }
                }
            }
        }
    }

    private void copyRuleToMap(ConfigurationSection section, Map<String, Object> target) {
        for (String key : List.of("base", "per-level", "every", "bonus", "min", "max", "expression")) {
            if (section.contains(key))
                target.put(key, section.get(key));
        }
    }

    private void ensureDefaultValues(ProgressionSession session) {
        Map<String, Object> values = session.currentValues();
        if (!values.containsKey("base"))
            values.put("base", 1);
        if (session.mode == ProgressionSession.ProgressionMode.PER_LEVEL) {
            if (!values.containsKey("per-level"))
                values.put("per-level", 1);
        } else {
            if (!values.containsKey("every"))
                values.put("every", 1);
            if (!values.containsKey("bonus"))
                values.put("bonus", 1);
        }
        if (!values.containsKey("max"))
            values.put("max", 10.0);
    }

    private void saveProgression(Player player, ProgressionSession session) {
        Map<String, Object> values = session.currentValues();
        boolean isAttribute = session.progressionType == ProgressionSession.ProgressionType.ATTRIBUTE;

        Map<String, Object> amount = new LinkedHashMap<>();
        if (session.mode == ProgressionSession.ProgressionMode.PER_LEVEL) {
            putIfPresent(amount, "base", values.get("base"));
            putIfPresent(amount, "per-level", values.get("per-level"));
            putIfPresent(amount, "min", values.get("min"));
            putIfPresent(amount, "max", values.get("max"));
        } else {
            putIfPresent(amount, "base", values.get("base"));
            putIfPresent(amount, "every", values.get("every"));
            putIfPresent(amount, "bonus", values.get("bonus"));
            putIfPresent(amount, "min", values.get("min"));
            putIfPresent(amount, "max", values.get("max"));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ItemsConfig itemsConfig = plugin.itemsConfig();
            YamlConfiguration itemsYaml = itemsConfig.getItemsYaml();
            if (itemsYaml == null) {
                player.sendMessage(plugin.language().message("progression-save-failed", Map.of()));
                sessions.remove(player.getUniqueId());
                return;
            }

            if (!isAttribute) {
                String path = "items." + session.itemKey + ".enchants." + session.selectedKey;
                itemsYaml.set(path, amount);
            } else {
                // Write a COMPLETE attribute entry: attribute / operation / slot / amount,
                // using the same lowercase id convention as /zmitems attribute add so
                // both entry points read/write the same YAML node.
                String attrId = session.selectedKey.toLowerCase(Locale.ROOT);
                Map<String, Object> attrEntry = new LinkedHashMap<>();
                attrEntry.put("attribute", session.selectedKey);
                attrEntry.put("operation", session.attributeOperation);
                attrEntry.put("slot", session.attributeSlot);
                attrEntry.put("amount", amount);
                itemsYaml.set("items." + session.itemKey + ".attributes." + attrId, attrEntry);
            }

            boolean saved = itemsConfig.saveToDiskFromEditor();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (saved) {
                    plugin.reloadPluginState();
                    player.sendMessage(plugin.language().message("progression-save-success",
                            Map.of("item",
                                    session.itemDisplayName != null ? session.itemDisplayName : session.itemKey)));
                } else {
                    player.sendMessage(plugin.language().message("progression-save-failed", Map.of()));
                }
                sessions.remove(player.getUniqueId());
            });
        });
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null)
            map.put(key, value);
    }

    // Server key lists
    @SuppressWarnings("deprecation")
    private List<String> getAllEnchantKeys() {
        List<String> keys = new ArrayList<>();
        try {
            Registry.ENCHANTMENT.stream()
                    .map(Enchantment::getKey)
                    .map(key -> key.getKey().toLowerCase(Locale.ROOT))
                    .distinct()
                    .sorted()
                    .forEach(keys::add);
        } catch (Exception ignored) {
            for (Enchantment e : Enchantment.values()) {
                if (e != null && e.getKey() != null) {
                    keys.add(e.getKey().getKey().toLowerCase(Locale.ROOT));
                }
            }
            keys.sort(String::compareTo);
        }
        return keys;
    }

    @SuppressWarnings("deprecation")
    private List<String> getAllAttributeKeys() {
        List<String> keys = new ArrayList<>();
        try {
            Registry.ATTRIBUTE.stream()
                    .map(Attribute::getKey)
                    .map(key -> key.getKey().toUpperCase(Locale.ROOT))
                    .filter(name -> !name.startsWith("GENERIC_"))
                    .distinct()
                    .sorted()
                    .forEach(keys::add);
        } catch (Exception ignored) {
            keys.addAll(List.of(
                    "ARMOR", "ARMOR_TOUGHNESS", "ATTACK_DAMAGE", "ATTACK_KNOCKBACK", "ATTACK_SPEED",
                    "BLOCK_INTERACTION_RANGE", "ENTITY_INTERACTION_RANGE", "FLYING_SPEED",
                    "GRAVITY", "JUMP_STRENGTH", "KNOCKBACK_RESISTANCE", "LUCK",
                    "MAX_ABSORPTION", "MAX_HEALTH", "MOVEMENT_SPEED", "SAFE_FALL_DISTANCE",
                    "SCALE", "STEP_HEIGHT"));
            keys.sort(String::compareTo);
        }
        return keys;
    }

    private boolean isKeyConfigured(ProgressionSession session, String key) {

        if (session.itemKey == null)
            return false;
        Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(session.itemKey);
        if (defOpt.isEmpty())
            return false;
        ItemDefinition def = defOpt.get();

        if (session.progressionType == ProgressionSession.ProgressionType.ENCHANTMENT) {
            return def.enchantments().containsKey(key);
        } else {
            for (AttributeSettings attr : def.attributes()) {
                if (attr.id().equalsIgnoreCase(key) || attr.attribute().equalsIgnoreCase(key))
                    return true;
            }
            return false;
        }
    }

    private String resolveItemDisplayName(String itemKey) {
        Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(itemKey);
        if (defOpt.isPresent()) {
            ItemDefinition def = defOpt.get();
            if (def.displayName() != null && !def.displayName().isBlank()) {
                return def.displayName().replaceAll("&[0-9a-fk-orA-FK-OR]|&#([A-Fa-f0-9]{6})", "");
            }
            if (def.material() != null)
                return def.material();
        }
        return itemKey;
    }

    private String formatAttributeName(String key) {
        if (key == null)
            return "";
        return java.util.Arrays.stream(key.split("_"))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private int slotToContentIndex(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot)
                return i;
        }
        return -1;
    }

    private void openInventory(Player player, ProgressionSession session) {
        Inventory inv = session.getHolder() != null ? session.getHolder().getInventory() : null;
        if (inv != null)
            player.openInventory(inv);
    }

    private void close(Player player) {
        sessions.remove(player.getUniqueId());
        player.closeInventory();
    }
}