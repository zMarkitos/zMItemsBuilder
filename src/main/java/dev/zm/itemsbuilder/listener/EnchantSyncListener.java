package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.util.ItemEnchantLoreManager;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class EnchantSyncListener implements Listener {

    private final zMItemsBuilder plugin;
    private final ItemEnchantLoreManager enchantLoreManager;
    private final Map<UUID, Map<Enchantment, Integer>> enchantSnapshot = new HashMap<>();

    public EnchantSyncListener(zMItemsBuilder plugin, ItemEnchantLoreManager enchantLoreManager) {
        this.plugin = plugin;
        this.enchantLoreManager = enchantLoreManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack next = player.getInventory().getItem(event.getNewSlot());

        syncIfChanged(player, previous);
        if (next != null && isPluginItem(next)) {
            enchantSnapshot.put(player.getUniqueId(), snapshotEnchants(next));
        } else {
            enchantSnapshot.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryType type = event.getView().getTopInventory().getType();
        if (type != InventoryType.ANVIL && type != InventoryType.GRINDSTONE) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        scheduleSync(player, cursor);
        scheduleSync(player, current);
    }

    private void syncIfChanged(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !isPluginItem(item)) {
            return;
        }

        Map<Enchantment, Integer> snapshot = enchantSnapshot.get(player.getUniqueId());
        if (snapshot == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Map<Enchantment, Integer> current = meta.getEnchants();
        if (enchantsChanged(snapshot, current)) {
            enchantLoreManager.syncEnchantLore(item);
        }
    }

    private void scheduleSync(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !isPluginItem(item)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.isSimilar(item)) {
                enchantLoreManager.syncEnchantLore(inHand);
            }
        }, 1L);
    }

    private boolean isPluginItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String sourceKey = ItemIdentityStore.readSourceKey(plugin, item);
        if (sourceKey != null && plugin.itemRegistry().getItem(sourceKey).isPresent()) {
            return true;
        }
        String legacyKey = ItemIdentityStore.read(plugin, item);
        return legacyKey != null && plugin.itemRegistry().getItem(legacyKey).isPresent();
    }

    private boolean enchantsChanged(Map<Enchantment, Integer> snapshot, Map<Enchantment, Integer> current) {
        if (snapshot.size() != current.size()) {
            return true;
        }
        for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
            Integer snapshotLevel = snapshot.get(entry.getKey());
            if (snapshotLevel == null || !snapshotLevel.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private Map<Enchantment, Integer> snapshotEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Map.of();
        }
        return Map.copyOf(meta.getEnchants());
    }
}