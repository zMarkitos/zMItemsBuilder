package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.zMItemsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class FlagInventoryHolder implements InventoryHolder {

    private final zMItemsBuilder plugin;
    private final Player player;
    private final ItemStack item;

    public FlagInventoryHolder(zMItemsBuilder plugin, Player player, ItemStack item) {
        this.plugin = plugin;
        this.player = player;
        this.item = item;
    }

    public zMItemsBuilder plugin() {
        return plugin;
    }

    public Player player() {
        return player;
    }

    public ItemStack item() {
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
