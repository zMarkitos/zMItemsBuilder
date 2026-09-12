package dev.zm.itemsbuilder.command.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HelpInventoryHolder implements InventoryHolder {
    private Inventory inventory;
    private final String section;

    public HelpInventoryHolder(String section) {
        this.section = section;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getSection() {
        return section;
    }
}
