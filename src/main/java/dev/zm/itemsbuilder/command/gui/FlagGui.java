package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.util.TextUtils;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class FlagGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final zMItemsBuilder plugin;
    private final dev.zm.itemsbuilder.command.zMItemsCommand command;
    private final org.bukkit.entity.Player player;
    private final ItemStack item;
    private final Inventory inventory;

    private static final int[] FLAG_SLOTS = { 21, 11, 12, 13, 14, 15, 23, 29, 30, 31, 32, 33 };
    private static final ItemBehaviorFlag[] FLAGS = ItemBehaviorFlag.values();

    public FlagGui(zMItemsBuilder plugin, dev.zm.itemsbuilder.command.zMItemsCommand command,
            org.bukkit.entity.Player player, ItemStack item) {
        this.plugin = plugin;
        this.command = command;
        this.player = player;
        this.item = item;
        this.inventory = Bukkit.createInventory(
                new FlagInventoryHolder(plugin, player, item),
                54,
                plugin.language().rawMessage("messages.flag-gui-title"));
        buildInventory();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void buildInventory() {
        inventory.clear();

        ItemStack border = createBorder();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, border);
        }

        inventory.setItem(49, createCloseButton());

        inventory.setItem(40, createItemInfo());

        Set<ItemBehaviorFlag> currentFlags = ItemFlagStore.read(plugin, item);

        for (int i = 0; i < FLAGS.length && i < FLAG_SLOTS.length; i++) {
            ItemBehaviorFlag flag = FLAGS[i];
            boolean active = currentFlags.contains(flag);
            inventory.setItem(FLAG_SLOTS[i], createFlagItem(flag, active));
        }
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
            meta.lore(plugin.language().rawMessageList("messages.button-close-lore", java.util.Map.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItemInfo() {
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            String itemName;
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null && itemMeta.hasDisplayName()) {
                itemName = LEGACY.serialize(itemMeta.displayName());
            } else {
                itemName = item.getType().name();
            }
            meta.displayName(plugin.language().rawMessage("messages.flag-gui-item-name",
                    java.util.Map.of("item", itemName)));

            Set<ItemBehaviorFlag> currentFlags = ItemFlagStore.read(plugin, item);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (currentFlags.isEmpty()) {
                lore.add(TextUtils.toItemComponent("  &7No flags active"));
            } else {
                for (ItemBehaviorFlag flag : currentFlags) {
                    lore.add(TextUtils.toItemComponent("  &#55FF55&l✔ &a" + formatFlagName(flag)));
                }
            }
            lore.add(Component.empty());
            meta.lore(lore);
            infoItem.setItemMeta(meta);
        }
        return infoItem;
    }

    private ItemStack createFlagItem(ItemBehaviorFlag flag, boolean active) {
        Material mat = active ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String prefix = active ? "&#55FF55&l✔ " : "&#F13713&l✘ ";
            String color = active ? "&a" : "&c";
            meta.displayName(TextUtils.toItemComponent(prefix + color + formatFlagName(flag)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtils.toItemComponent("  &7" + getFlagDescription(flag)));
            lore.add(Component.empty());
            lore.add(
                    TextUtils.toItemComponent(active ? "  &#F13713Click to &ldisable" : "  &#55FF55Click to &lenable"));
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatFlagName(ItemBehaviorFlag flag) {
        return switch (flag) {
            case NO_PLACE -> "No Place";
            case NO_CRAFT -> "No Craft";
            case NO_DROP -> "No Drop";
            case NO_USE -> "No Use";
            case NO_CONSUME -> "No Consume";
            case NO_EQUIP -> "No Equip";
            case NO_ANVIL -> "No Anvil";
            case NO_SMITHING -> "No Smithing";
            case NO_GRINDSTONE -> "No Grindstone";
            case NO_ENCHANT -> "No Enchant";
            case NO_BREWING -> "No Brewing";
            case NO_FURNACE -> "No Furnace";
        };
    }

    private String getFlagDescription(ItemBehaviorFlag flag) {
        return switch (flag) {
            case NO_PLACE -> "Prevents placing this item as a block";
            case NO_CRAFT -> "Prevents using in crafting recipes";
            case NO_DROP -> "Prevents dropping this item";
            case NO_USE -> "Prevents right-click interaction";
            case NO_CONSUME -> "Prevents consuming food or potions";
            case NO_EQUIP -> "Prevents equipping as armor";
            case NO_ANVIL -> "Prevents using in an anvil";
            case NO_SMITHING -> "Prevents using in a smithing table";
            case NO_GRINDSTONE -> "Prevents using in a grindstone";
            case NO_ENCHANT -> "Prevents enchanting at enchanting table";
            case NO_BREWING -> "Prevents using in a brewing stand";
            case NO_FURNACE -> "Prevents using in furnaces or smokers";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FlagInventoryHolder))
            return;
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize())
            return;

        if (rawSlot == 49) {
            player.closeInventory();
            return;
        }

        for (int i = 0; i < FLAG_SLOTS.length; i++) {
            if (rawSlot == FLAG_SLOTS[i] && i < FLAGS.length) {
                command.toggleFlag(player, item, FLAGS[i]);
                buildInventory();
                player.updateInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FlagInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FlagInventoryHolder) {
            HandlerList.unregisterAll(this);
        }
    }
}
