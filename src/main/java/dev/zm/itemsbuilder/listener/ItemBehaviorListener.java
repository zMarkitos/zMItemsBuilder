package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import dev.zm.itemsbuilder.util.ItemDataStore;
import dev.zm.itemsbuilder.util.ItemEffectsStore;
import dev.zm.itemsbuilder.util.ItemEnchantLoreManager;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ItemBehaviorListener implements Listener {

    /** Re-apply passive slot-bound effects every 4 seconds (80 ticks). */
    private static final int PASSIVE_EFFECT_INTERVAL_TICKS = 80;

    private final zMItemsBuilder plugin;
    private final ItemEnchantLoreManager enchantLoreManager;

    public ItemBehaviorListener(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.enchantLoreManager = new ItemEnchantLoreManager(plugin, plugin.language());
        // Schedule passive slot-based effect application
        plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::applyPassiveSlotEffects, 40L, PASSIVE_EFFECT_INTERVAL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (ItemFlagStore.hasAny(plugin, event.getItemInHand(), ItemBehaviorFlag.NO_PLACE)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.language().message("blocked-place"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (ItemFlagStore.hasAny(plugin, event.getItemDrop().getItemStack(), ItemBehaviorFlag.NO_DROP)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.language().message("blocked-drop"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && ItemFlagStore.hasAny(plugin, item, ItemBehaviorFlag.NO_EQUIP)
                && isEquippable(item) && isEquipAction(event.getAction())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.language().message("blocked-equip"));
            return;
        }
        if (item != null && ItemFlagStore.hasAny(plugin, item, ItemBehaviorFlag.NO_USE)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.language().message("blocked-use"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (ItemFlagStore.hasAny(plugin, event.getItem(), ItemBehaviorFlag.NO_CONSUME)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.language().message("blocked-consume"));
            return;
        }

        // If the item is a real potion with custom effects in meta, Minecraft handles
        // them.
        if (event.getItem() != null && event.getItem().hasItemMeta()
                && event.getItem().getItemMeta() instanceof PotionMeta potionMeta
                && potionMeta.hasCustomEffects()) {
            return;
        }

        List<PotionEffectSettings> storedEffects = ItemEffectsStore.read(plugin, event.getItem());
        if (!storedEffects.isEmpty()) {
            applyEffectsOnConsume(event, storedEffects);
            return;
        }

        // Fallback for items created before effects were stored in PDC.
        String itemId = ItemIdentityStore.read(plugin, event.getItem());
        if (itemId != null) {
            plugin.itemRegistry().getItem(itemId).ifPresent(definition -> {
                if (definition.customEffects() != null && !definition.customEffects().isEmpty()) {
                    List<PotionEffectSettings> resolved = definition.customEffects().stream()
                            .map(rule -> rule.resolve(1))
                            .toList();
                    applyEffectsOnConsume(event, resolved);
                }
            });
        }
    }

    private void applyEffectsOnConsume(PlayerItemConsumeEvent event, List<PotionEffectSettings> effects) {
        for (PotionEffectSettings eff : effects) {
            applyPotionEffect(event.getPlayer(), eff.type(), eff.durationTicks(), eff.amplifier());
        }
    }

    // ── Passive slot-bound effects (data.yml) ────────────────────────────────

    /**
     * Runs on a repeating timer. For each online player, checks every relevant
     * equipment slot and applies effects defined in data.yml that are bound to it.
     */
    private void applyPassiveSlotEffects() {
        ItemDataStore store = plugin.itemDataStore();
        if (store == null)
            return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            checkSlotEffects(player, player.getInventory().getItemInMainHand(), "MAIN_HAND", store);
            checkSlotEffects(player, player.getInventory().getItemInOffHand(), "OFF_HAND", store);
            checkSlotEffects(player, player.getInventory().getHelmet(), "HELMET", store);
            checkSlotEffects(player, player.getInventory().getChestplate(), "CHESTPLATE", store);
            checkSlotEffects(player, player.getInventory().getLeggings(), "LEGGINGS", store);
            checkSlotEffects(player, player.getInventory().getBoots(), "BOOTS", store);
        }
    }

    private void checkSlotEffects(Player player, ItemStack item, String slotName, ItemDataStore store) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return;
        String itemId = ItemIdentityStore.read(plugin, item);
        if (itemId == null)
            itemId = ItemIdentityStore.readSourceKey(plugin, item);
        if (itemId == null)
            return;

        for (ItemDataStore.ItemEffectData eff : store.getEffects(itemId)) {
            if (!eff.isSlotBound())
                continue;
            if (!eff.slot().equalsIgnoreCase(slotName))
                continue;
            // Duration slightly longer than interval to keep the effect active continuously
            int duration = eff.duration() > 0 ? eff.duration() : PASSIVE_EFFECT_INTERVAL_TICKS + 20;
            applyPotionEffect(player, eff.type(), duration, eff.amplifier());
        }
    }

    private void applyPotionEffect(Player player, String typeName, int durationTicks, int amplifier) {
        try {
            NamespacedKey key = NamespacedKey.minecraft(typeName.toLowerCase(Locale.ROOT));
            PotionEffectType type = null;
            try {
                type = Registry.POTION_EFFECT_TYPE.get(key);
            } catch (Exception | NoSuchFieldError ignored) {
            }
            if (type == null)
                type = PotionEffectType.getByKey(key);
            if (type == null)
                type = PotionEffectType.getByName(typeName.toUpperCase(Locale.ROOT));
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false));
            }
        } catch (Exception ignored) {
        }
    }

    // ── Workstation restrictions ─────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsFlag(event.getInventory().getMatrix(), ItemBehaviorFlag.NO_CRAFT)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (containsFlag(event.getInventory().getContents(), ItemBehaviorFlag.NO_ANVIL, ItemBehaviorFlag.NO_CRAFT)) {
            event.getInventory().setResult(null);
            return;
        }

        ItemStack currentResult = event.getResult();
        if (currentResult == null || currentResult.getType().isAir())
            return;
        ItemStack result = currentResult.clone();
        if (ItemIdentityStore.readSourceKey(plugin, result) == null)
            return;
        if (enchantLoreManager.syncEnchantLore(result)) {
            event.setResult(result);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (containsFlag(event.getInventory().getContents(), ItemBehaviorFlag.NO_SMITHING, ItemBehaviorFlag.NO_CRAFT)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (containsFlag(event.getInventory().getContents(), ItemBehaviorFlag.NO_GRINDSTONE)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (ItemFlagStore.hasAny(plugin, event.getItem(), ItemBehaviorFlag.NO_ENCHANT)) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage(plugin.language().message("blocked-enchant"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryType topType = top.getType();
        ItemBehaviorFlag restrictedFlag = inventoryFlag(topType);
        if (restrictedFlag != null) {
            if (ItemFlagStore.hasAny(plugin, event.getCurrentItem(), restrictedFlag, ItemBehaviorFlag.NO_CRAFT)) {
                event.setCancelled(true);
                sendInventoryMessage(event, restrictedFlag);
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), restrictedFlag)) {
                event.setCancelled(true);
                sendInventoryMessage(event, restrictedFlag);
                return;
            }
            if (ItemFlagStore.hasAny(plugin, event.getCursor(), restrictedFlag)) {
                event.setCancelled(true);
                sendInventoryMessage(event, restrictedFlag);
                return;
            }
        }

        if (isEquipmentSlot(event.getSlotType())
                && ItemFlagStore.hasAny(plugin, event.getCursor(), ItemBehaviorFlag.NO_EQUIP)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
            return;
        }

        if (isEquipmentSlot(event.getSlotType())
                && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), ItemBehaviorFlag.NO_EQUIP)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), ItemBehaviorFlag.NO_EQUIP)
                && isEquippable(event.getCurrentItem())) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryType topType = event.getView().getTopInventory().getType();
        ItemBehaviorFlag restrictedFlag = inventoryFlag(topType);
        if (restrictedFlag == null)
            return;
        ItemStack dragged = event.getOldCursor();
        if (dragged == null)
            return;
        if (hasTopInventorySlot(event, restrictedFlag, dragged)) {
            event.setCancelled(true);
            sendDragMessage(event, restrictedFlag);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean containsFlag(ItemStack[] items, ItemBehaviorFlag... flags) {
        if (items == null || items.length == 0)
            return false;
        for (ItemStack item : items) {
            if (ItemFlagStore.hasAny(plugin, item, flags))
                return true;
        }
        return false;
    }

    private ItemBehaviorFlag inventoryFlag(InventoryType type) {
        return switch (type) {
            case ANVIL -> ItemBehaviorFlag.NO_ANVIL;
            case SMITHING -> ItemBehaviorFlag.NO_SMITHING;
            case GRINDSTONE -> ItemBehaviorFlag.NO_GRINDSTONE;
            case ENCHANTING -> ItemBehaviorFlag.NO_ENCHANT;
            case BREWING -> ItemBehaviorFlag.NO_BREWING;
            case FURNACE, BLAST_FURNACE, SMOKER -> ItemBehaviorFlag.NO_FURNACE;
            default -> null;
        };
    }

    private boolean isEquipmentSlot(SlotType slotType) {
        return slotType == SlotType.ARMOR;
    }

    private boolean isEquipAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isEquippable(ItemStack item) {
        if (item == null)
            return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || "ELYTRA".equals(name) || "SHIELD".equals(name);
    }

    private void sendInventoryMessage(InventoryClickEvent event, ItemBehaviorFlag flag) {
        if (flag == ItemBehaviorFlag.NO_ANVIL) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-anvil"));
        } else if (flag == ItemBehaviorFlag.NO_SMITHING) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-smithing"));
        } else if (flag == ItemBehaviorFlag.NO_GRINDSTONE) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-grindstone"));
        } else if (flag == ItemBehaviorFlag.NO_ENCHANT) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-enchant"));
        } else if (flag == ItemBehaviorFlag.NO_BREWING) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-brewing"));
        } else if (flag == ItemBehaviorFlag.NO_FURNACE) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-furnace"));
        } else if (flag == ItemBehaviorFlag.NO_CRAFT) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-craft"));
        }
    }

    private boolean hasTopInventorySlot(InventoryDragEvent event, ItemBehaviorFlag restrictedFlag, ItemStack dragged) {
        Inventory top = event.getView().getTopInventory();
        int topSize = top.getSize();
        if (!ItemFlagStore.hasAny(plugin, dragged, restrictedFlag))
            return false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize)
                return true;
        }
        return false;
    }

    private void sendDragMessage(InventoryDragEvent event, ItemBehaviorFlag flag) {
        if (flag == ItemBehaviorFlag.NO_BREWING) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-brewing"));
        } else if (flag == ItemBehaviorFlag.NO_FURNACE) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-furnace"));
        } else if (flag == ItemBehaviorFlag.NO_ENCHANT) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-enchant"));
        } else if (flag == ItemBehaviorFlag.NO_CRAFT) {
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-craft"));
        }
    }
}
