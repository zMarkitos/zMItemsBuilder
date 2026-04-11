package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.builder.model.ItemBehaviorFlag;
import dev.zm.itemsbuilder.util.ItemFlagStore;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import dev.zm.itemsbuilder.builder.model.PotionEffectSettings;
import dev.zm.itemsbuilder.util.ItemEffectsStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;

public final class ItemBehaviorListener implements Listener {

    private final zMItemsBuilder plugin;

    public ItemBehaviorListener(zMItemsBuilder plugin) {
        this.plugin = plugin;
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
        if (item != null && ItemFlagStore.hasAny(plugin, item, ItemBehaviorFlag.NO_EQUIP) && isEquippable(item) && isEquipAction(event.getAction())) {
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

        // If the item is a real potion with custom effects in meta, Minecraft will apply them already.
        if (event.getItem() != null && event.getItem().hasItemMeta()
                && event.getItem().getItemMeta() instanceof PotionMeta potionMeta
                && potionMeta.hasCustomEffects()) {
            return;
        }

        List<PotionEffectSettings> storedEffects = ItemEffectsStore.read(plugin, event.getItem());
        if (!storedEffects.isEmpty()) {
            applyEffects(event, storedEffects);
            return;
        }

        // Fallback for very old items created before effects were stored in PDC.
        String itemId = ItemIdentityStore.read(plugin, event.getItem());
        if (itemId != null) {
            plugin.itemRegistry().getItem(itemId).ifPresent(definition -> {
                if (definition.customEffects() != null && !definition.customEffects().isEmpty()) {
                    List<PotionEffectSettings> resolved = definition.customEffects().stream()
                            .map(rule -> rule.resolve(1))
                            .toList();
                    applyEffects(event, resolved);
                }
            });
        }
    }

    private void applyEffects(PlayerItemConsumeEvent event, List<PotionEffectSettings> effects) {
        for (PotionEffectSettings eff : effects) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(eff.type().toLowerCase(java.util.Locale.ROOT));
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(key);
                if (type != null) {
                    event.getPlayer().addPotionEffect(new PotionEffect(type, eff.durationTicks(), eff.amplifier()));
                }
            } catch (Exception ignored) {
            }
        }
    }

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
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), restrictedFlag)) {
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

        if (isEquipmentSlot(event.getSlotType()) && ItemFlagStore.hasAny(plugin, event.getCursor(), ItemBehaviorFlag.NO_EQUIP)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
            return;
        }

        if (isEquipmentSlot(event.getSlotType()) && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), ItemBehaviorFlag.NO_EQUIP)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && ItemFlagStore.hasAny(plugin, event.getCurrentItem(), ItemBehaviorFlag.NO_EQUIP) && isEquippable(event.getCurrentItem())) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(plugin.language().message("blocked-equip"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryType topType = event.getView().getTopInventory().getType();
        ItemBehaviorFlag restrictedFlag = inventoryFlag(topType);
        if (restrictedFlag == null) {
            return;
        }
        ItemStack dragged = event.getOldCursor();
        if (dragged == null) {
            return;
        }
        if (hasTopInventorySlot(event, restrictedFlag, dragged)) {
            event.setCancelled(true);
            sendDragMessage(event, restrictedFlag);
        }
    }

    private boolean containsFlag(ItemStack[] items, ItemBehaviorFlag... flags) {
        if (items == null || items.length == 0) {
            return false;
        }
        for (ItemStack item : items) {
            if (ItemFlagStore.hasAny(plugin, item, flags)) {
                return true;
            }
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
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || "ELYTRA".equals(name)
            || "SHIELD".equals(name);
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
        if (!ItemFlagStore.hasAny(plugin, dragged, restrictedFlag)) {
            return false;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                return true;
            }
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
