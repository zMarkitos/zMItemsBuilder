package dev.zm.itemsbuilder.command.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ProgressionInventoryHolder implements InventoryHolder {

    private final ProgressionSession session;
    private Inventory inventory;

    public ProgressionInventoryHolder(ProgressionSession session) {
        this.session = session;
    }

    public ProgressionSession session() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
