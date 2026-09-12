package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.config.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class HelpGui implements Listener {

    private static final int GUI_SIZE = 54;
    private final zMItemsBuilder plugin;

    public HelpGui(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public static void open(Player player, zMItemsBuilder plugin) {
        LanguageManager lang = plugin.language();

        Component title = lang.rawMessage("messages.help-gui-title");
        Inventory inventory = Bukkit.createInventory(
                new HelpInventoryHolder(),
                GUI_SIZE,
                legacyText(title));

        ItemStack border = createButton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, border);
        }

        inventory.setItem(20, createButtonFromLang(lang, Material.CRAFTING_TABLE, "help-gui-btn-create"));
        inventory.setItem(21, createButtonFromLang(lang, Material.CHEST, "help-gui-btn-give"));
        inventory.setItem(22, createButtonFromLang(lang, Material.CAMPFIRE, "help-gui-btn-groups"));
        inventory.setItem(23, createButtonFromLang(lang, Material.BOOK, "help-gui-btn-commands"));
        inventory.setItem(24, createButtonFromLang(lang, Material.ENCHANTED_BOOK, "help-gui-btn-enchants"));
        inventory.setItem(30, createButtonFromLang(lang, Material.POTION, "help-gui-btn-effects"));
        inventory.setItem(31, createButtonFromLang(lang, Material.REDSTONE_TORCH, "help-gui-btn-flags"));
        inventory.setItem(32, createButtonFromLang(lang, Material.NETHER_STAR, "help-gui-btn-attributes"));

        inventory.setItem(49, createButtonFromLang(lang, Material.BARRIER, "button-close"));

        player.openInventory(inventory);
    }

    private static ItemStack createButtonFromLang(LanguageManager lang, Material mat, String langKeyBase) {
        Component name = lang.rawMessage("messages." + langKeyBase + "-name");
        List<Component> lore = lang.rawMessageList("messages." + langKeyBase + "-lore", Map.of());

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> noItalicLore = lore.stream()
                        .map(c -> c.decoration(TextDecoration.ITALIC, false))
                        .toList();
                meta.lore(noItalicLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String legacyText(Component component) {
        if (component == null)
            return "";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection()
                .serialize(component);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HelpInventoryHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        player.closeInventory();
    }

    private static class HelpInventoryHolder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}