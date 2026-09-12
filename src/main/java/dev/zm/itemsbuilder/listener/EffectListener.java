package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.util.ItemDataStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class EffectListener implements Listener {

    private final zMItemsBuilder plugin;
    private final Map<UUID, Map<PotionEffectType, Integer>> appliedEffects = new ConcurrentHashMap<>();
    private static final int INFINITE_DURATION = Integer.MAX_VALUE;

    public EffectListener(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    public void clearAllEffects() {
        appliedEffects.clear();
    }

    private void updateEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, Integer> desired = new HashMap<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = getItemInSlot(player, slot);
            if (item != null && !item.getType().isAir()) {
                collectEffectsFromItem(item, slot, desired);
            }
        }

        Map<PotionEffectType, Integer> current = appliedEffects.getOrDefault(uuid, new HashMap<>());

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = current.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PotionEffectType, Integer> entry = iterator.next();
            PotionEffectType type = entry.getKey();
            Integer oldLevel = entry.getValue();
            Integer newLevel = desired.get(type);
            if (newLevel == null || !newLevel.equals(oldLevel)) {
                player.removePotionEffect(type);
                iterator.remove();
            }
        }

        for (Map.Entry<PotionEffectType, Integer> entry : desired.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue();
            Integer oldLevel = current.get(type);
            if (oldLevel == null || !oldLevel.equals(level)) {
                player.addPotionEffect(new PotionEffect(type, INFINITE_DURATION, level, true, true));
                current.put(type, level);
            }
        }

        appliedEffects.put(uuid, current);
    }

    private void collectEffectsFromItem(ItemStack item, EquipmentSlot actualSlot,
            Map<PotionEffectType, Integer> desired) {
        String itemId = resolveItemId(item);
        if (itemId == null) {
            return;
        }

        String slotName = normalizeSlot(actualSlot);
        if (slotName == null)
            return;

        List<ItemDataStore.ItemEffectData> effects = plugin.itemDataStore().getEffects(itemId);
        if (effects.isEmpty()) {
            return;
        }

        for (ItemDataStore.ItemEffectData effect : effects) {
            String effectSlot = effect.slot() != null ? effect.slot() : "ANY";
            if (!effectSlot.equalsIgnoreCase("ANY") && !effectSlot.equalsIgnoreCase(slotName)) {
                continue;
            }

            PotionEffectType type = PotionEffectType.getByName(effect.type());
            if (type == null)
                continue;
            int level = effect.amplifier() + 1;
            desired.merge(type, level, Math::max);
        }
    }

    private String normalizeSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> "MAIN_HAND";
            case OFF_HAND -> "OFF_HAND";
            case HEAD -> "HELMET";
            case CHEST -> "CHESTPLATE";
            case LEGS -> "LEGGINGS";
            case FEET -> "BOOTS";
            default -> null;
        };
    }

    private String resolveItemId(ItemStack item) {
        String sourceKey = ItemIdentityStore.readSourceKey(plugin, item);
        if (sourceKey != null) {
            Optional<ItemDefinition> defOpt = plugin.itemRegistry().getItem(sourceKey);
            if (defOpt.isPresent()) {
                ItemDefinition def = defOpt.get();
                if (def.itemIdentifier() != null && !def.itemIdentifier().isBlank()) {
                    return def.itemIdentifier();
                }
                return sourceKey;
            }
        }

        String idItem = ItemIdentityStore.read(plugin, item);
        if (idItem != null) {
            return idItem;
        }

        return null;
    }

    private ItemStack getItemInSlot(Player player, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
            case HAND -> player.getInventory().getItemInMainHand();
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            default -> null;
        };
    }

    private void scheduleUpdate(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                try {
                    updateEffects(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error updating effects for " + player.getName(), e);
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleUpdate(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleUpdate(player);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        scheduleUpdate(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        appliedEffects.remove(event.getPlayer().getUniqueId());
    }
}