package dev.zm.itemsbuilder.command.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CreationInventoryHolder implements InventoryHolder {
    private Inventory inventory;
    private final String menuType;
    
    public CreationInventoryHolder(String menuType) {
        this.menuType = menuType;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getMenuType() {
        return menuType;
    }
}
