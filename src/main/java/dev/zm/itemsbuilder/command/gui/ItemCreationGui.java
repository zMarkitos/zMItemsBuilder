package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.ItemMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemCreationGui implements Listener {
    private static final Map<UUID, ItemCreationSession> sessions = new ConcurrentHashMap<>();
    private final zMItemsBuilder plugin;

    public ItemCreationGui(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public static void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public static void open(Player player, String id, zMItemsBuilder plugin) {
        ItemCreationSession session = new ItemCreationSession(player.getUniqueId(), id, false);
        sessions.put(player.getUniqueId(), session);
        openModeSelect(player, plugin);
    }

    private static void openModeSelect(Player player, zMItemsBuilder plugin) {
        CreationInventoryHolder holder = new CreationInventoryHolder("MODE_SELECT");
        String titleRaw = legacyText(plugin.language().rawMessage("messages.create-gui-select-mode-title"));
        if (titleRaw.isEmpty())
            titleRaw = "Select item mode";

        Inventory inventory = Bukkit.createInventory(holder, 27, titleRaw);
        holder.setInventory(inventory);

        inventory.setItem(11, createButton(Material.IRON_SWORD,
                legacyText(plugin.language().rawMessage("messages.create-gui-mode-single"))));
        inventory.setItem(13, createButton(Material.IRON_CHESTPLATE,
                legacyText(plugin.language().rawMessage("messages.create-gui-mode-armor"))));
        inventory.setItem(15, createButton(Material.IRON_PICKAXE,
                legacyText(plugin.language().rawMessage("messages.create-gui-mode-tools"))));

        player.openInventory(inventory);
    }

    private static ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String legacyText(Component component) {
        if (component == null)
            return "";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(component);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CreationInventoryHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        ItemCreationSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return;

        String menuType = holder.getMenuType();

        if (menuType.equals("MODE_SELECT")) {
            if (slot == 11) {
                session.mode = ItemMode.SINGLE;
                saveTemplateAsync(player, session, "single");
            } else if (slot == 13) {
                session.mode = ItemMode.ARMOR_SET;
                saveTemplateAsync(player, session, "armor");
            } else if (slot == 15) {
                session.mode = ItemMode.TOOL_SET;
                saveTemplateAsync(player, session, "tools");
            }
        }
    }

    private void saveTemplateAsync(Player player, ItemCreationSession session, String type) {
        player.closeInventory();
        sessions.remove(player.getUniqueId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("mode", session.mode.name().toLowerCase());

            if (type.equals("single")) {
                data.put("material", "DIAMOND_SWORD");
            } else {
                data.put("base-material", "DIAMOND");
                if (type.equals("armor")) {
                    data.put("pieces", List.of("helmet", "chestplate", "leggings", "boots"));
                } else {
                    data.put("pieces", List.of("axe", "hoe", "shovel", "pickaxe"));
                }
            }

            org.bukkit.configuration.ConfigurationSection esthetic = plugin.itemsConfig().getEstheticSection();
            String nameTemplate = esthetic != null
                    ? esthetic.getString("name-template", "&f{item_type} &8▸ {prefix_item}")
                    : "&f{item_type} &8▸ {prefix_item}";
            List<String> loreTemplate = esthetic != null ? esthetic.getStringList("lore-template") : List.of();
            if (loreTemplate.isEmpty()) {
                loreTemplate = List.of(
                        "",
                        " {gradient:🕮 Enchantments:}",
                        "{enchants}",
                        "",
                        " {gradient:⌛ Rarity:} {secondary_color}{rarity}");
            }

            data.put("name", nameTemplate);
            data.put("lore", loreTemplate);

            Map<String, Object> enchants = new java.util.LinkedHashMap<>();
            if (type.equals("tools") || type.equals("single")) {
                enchants.put("unbreaking", 3);
                if (type.equals("tools")) {
                    enchants.put("efficiency", 5);
                } else {
                    enchants.put("sharpness", 5);
                }
            } else {
                enchants.put("protection", 4);
                enchants.put("unbreaking", 3);
            }
            data.put("enchants", enchants);

            data.put("item-flags", List.of("HIDE_ATTRIBUTES", "HIDE_ENCHANTS"));
            data.put("behavior-flags", new ArrayList<>());

            boolean saved = plugin.itemsConfig().saveItemEntry(session.id, data);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (saved) {
                    plugin.reloadPluginState();
                    player.sendMessage(plugin.language().message(
                            "create-gui-saved", Map.of("name", session.id)));
                } else {
                    player.sendMessage(plugin.language().message("create-gui-save-failed"));
                }
            });
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CreationInventoryHolder))
            return;
        if (!(event.getPlayer() instanceof Player player))
            return;
        sessions.remove(player.getUniqueId());
    }
}
