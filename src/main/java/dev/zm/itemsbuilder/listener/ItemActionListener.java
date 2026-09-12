package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import dev.zm.itemsbuilder.util.ItemDataStore;
import dev.zm.itemsbuilder.util.ItemIdentityStore;
import dev.zm.itemsbuilder.util.TextUtils;
import dev.zm.itemsbuilder.zMItemsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Executes actions configured in data.yml for plugin items:
 * - player_command / console_command (with %player% placeholder)
 * - sound
 * - uses (remaining uses stored in the item's PDC)
 * - cooldown (in-memory per player per itemId)
 */
public final class ItemActionListener implements Listener {

    /** PDC key: remaining uses stored on the item instance. */
    private static final String KEY_USES = "item_uses";

    /**
     * PDC key: lore template data for lines containing {uses} or {max_uses}.
     * Format: {@code "lineIndex:rawTemplate\nlineIndex:rawTemplate\n..."}.
     * Only present when the item definition lore contains those placeholders.
     */
    private static final String KEY_USES_LORE = "item_uses_lore";

    private static final String PLACEHOLDER_USES = "{uses}";
    private static final String PLACEHOLDER_MAX_USES = "{max_uses}";

    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /** in-memory cooldown store: UUID -> (itemId -> expire-timestamp ms) */
    private final Map<UUID, Map<String, Long>> cooldownMap = new ConcurrentHashMap<>();

    private final zMItemsBuilder plugin;
    private final NamespacedKey usesKey;
    private final NamespacedKey usesLoreKey;

    public ItemActionListener(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.usesKey = new NamespacedKey(plugin, KEY_USES);
        this.usesLoreKey = new NamespacedKey(plugin, KEY_USES_LORE);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Stamps the item with the configured max-uses from data.yml.
     * Call this right after building/giving an item if {@code maxUses > 0}.
     */
    public void stampUses(ItemStack item, int maxUses) {
        if (item == null || maxUses <= 0)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, maxUses);
        item.setItemMeta(meta);
    }

    /**
     * Stamps uses on the item and, if the lore contains {@code {uses}} or
     * {@code {max_uses}} placeholders, stores the per-line templates in PDC and
     * resolves the initial display values in the lore.
     *
     * <p>The raw lore template strings must be provided as a list of
     * {@code (lineIndex, rawTemplate)} pairs — where {@code rawTemplate} is the
     * original {@code &}-coded string before any uses substitution.
     *
     * @param item         the item to stamp
     * @param maxUses      configured max uses (must be &gt; 0)
     * @param loreTemplates list of lore lines (index, raw &-coded template) that
     *                      contain {uses} or {max_uses}; may be empty
     */
    public void stampUsesWithLore(ItemStack item, int maxUses, List<LoreTemplateLine> loreTemplates) {
        if (item == null || maxUses <= 0)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(usesKey, PersistentDataType.INTEGER, maxUses);

        if (!loreTemplates.isEmpty()) {
            pdc.set(usesLoreKey, PersistentDataType.STRING, encodeLoreTemplates(loreTemplates));
            applyUsesLore(meta, loreTemplates, maxUses, maxUses);
        }

        item.setItemMeta(meta);
    }

    /**
     * Returns the remaining uses stored in the item, or -1 if the key is absent.
     */
    public int getRemainingUses(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return -1;
        Integer v = meta.getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    /**
     * Scans the item's current lore for lines that contain {@code {uses}} or
     * {@code {max_uses}} as literal text (unresolved at build time) and returns
     * the corresponding template entries.
     *
     * <p>This is used by {@link dev.zm.itemsbuilder.builder.ItemFactory} to
     * discover which lore lines need uses-tracking without duplicating the
     * placeholder-detection logic.
     *
     * @param item the already-built item whose lore may contain the placeholders
     * @return discovered template lines, or an empty list if none
     */
    public List<LoreTemplateLine> detectLoreTemplates(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return List.of();
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return List.of();
        List<Component> loreCmps = meta.lore();
        if (loreCmps == null || loreCmps.isEmpty())
            return List.of();

        List<LoreTemplateLine> result = new ArrayList<>();
        for (int i = 0; i < loreCmps.size(); i++) {
            String raw = LEGACY_AMP.serialize(loreCmps.get(i));
            if (raw.contains(PLACEHOLDER_USES) || raw.contains(PLACEHOLDER_MAX_USES)) {
                result.add(new LoreTemplateLine(i, raw));
            }
        }
        return List.copyOf(result);
    }

    /** Immutable record carrying a lore line index and its raw &-coded template. */
    public record LoreTemplateLine(int index, String rawTemplate) {}

    // ── Event ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir())
            return;

        // Determine which click type occurred
        String clickType = resolveClickType(event.getAction(), player.isSneaking());
        if (clickType == null)
            return; // not a supported action click

        // Identify the plugin item key from PDC
        String rawKey = ItemIdentityStore.read(plugin, item);
        if (rawKey == null) {
            rawKey = ItemIdentityStore.readSourceKey(plugin, item);
        }
        if (rawKey == null)
            return;

        // Resolve rawKey to actual id_item (itemIdentifier)
        String itemId = null;
        ItemDefinition def = plugin.itemRegistry().getItem(rawKey).orElse(null);
        if (def != null && def.itemIdentifier() != null) {
            itemId = def.itemIdentifier();
        } else {
            for (String key : plugin.itemRegistry().getItemNames()) {
                ItemDefinition d = plugin.itemRegistry().getItem(key).orElse(null);
                if (d != null && d.itemIdentifier() != null && d.itemIdentifier().equalsIgnoreCase(rawKey)) {
                    itemId = d.itemIdentifier();
                    break;
                }
            }
        }

        if (itemId == null) {
            itemId = rawKey; // Fallback
        }

        ItemDataStore store = plugin.itemDataStore();
        List<ItemDataStore.ItemActionData> actions = store.getActions(itemId);
        if (actions.isEmpty())
            return;

        // Filter actions matching this click type
        final String finalItemId = itemId;
        List<ItemDataStore.ItemActionData> matching = actions.stream()
                .filter(a -> a.click().equalsIgnoreCase(clickType))
                .toList();
        if (matching.isEmpty())
            return;

        int cooldownSecs = store.getCooldown(itemId);
        if (cooldownSecs > 0) {
            long now = System.currentTimeMillis();
            Map<String, Long> playerCds = cooldownMap.computeIfAbsent(player.getUniqueId(),
                    k -> new ConcurrentHashMap<>());
            Long expiry = playerCds.get(itemId);
            if (expiry != null && now < expiry) {
                long remaining = (expiry - now + 999) / 1000;
                player.sendMessage(plugin.language().message("action-cooldown",
                        Map.of("seconds", String.valueOf(remaining), "item", itemId)));
                event.setCancelled(true);
                return;
            }
            playerCds.put(itemId, now + cooldownSecs * 1000L);
        }

        int maxUses = store.getMaxUses(itemId);
        if (maxUses > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null)
                return;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int remaining = pdc.getOrDefault(usesKey, PersistentDataType.INTEGER, maxUses);
            if (remaining <= 0) {
                consumeItem(player, item);
                event.setCancelled(true);
                return;
            }
            remaining--;
            if (remaining <= 0) {
                consumeItem(player, item);
            } else {
                pdc.set(usesKey, PersistentDataType.INTEGER, remaining);
                updateUsesLore(meta, pdc, remaining, maxUses);
                item.setItemMeta(meta);
            }
        }

        for (ItemDataStore.ItemActionData action : matching) {
            executeAction(player, action, finalItemId);
        }

        event.setCancelled(true);
    }

    private void executeAction(Player player, ItemDataStore.ItemActionData action, String itemId) {
        String type = action.type().toLowerCase(Locale.ROOT);
        String value = replacePlaceholders(action.value(), player);

        switch (type) {
            case "player_command" -> {
                // Remove leading slash if present to avoid double-slash
                String cmd = value.startsWith("/") ? value.substring(1) : value;
                player.performCommand(cmd);
            }
            case "console_command" -> {
                String cmd = value.startsWith("/") ? value.substring(1) : value;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            case "sound" -> {
                // value format: "SOUND_NAME" or "SOUND_NAME:VOLUME:PITCH"
                String[] parts = value.split(":");
                String soundName = parts[0].toUpperCase(Locale.ROOT);
                float volume = parts.length > 1 ? parseFloat(parts[1], 1.0f) : 1.0f;
                float pitch = parts.length > 2 ? parseFloat(parts[2], 1.0f) : 1.0f;
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown sound in action for item '" + itemId + "': " + soundName);
                }
            }
            default -> plugin.getLogger().warning(
                    "Unknown action type '" + type + "' for item '" + itemId + "'.");
        }
    }

    private String replacePlaceholders(String input, Player player) {
        if (input == null)
            return "";
        String result = input
                .replace("%player%", player.getName())
                .replace("%displayname%", player.getDisplayName())
                .replace("%world%", player.getWorld().getName())
                .replace("%x%", String.valueOf((int) player.getLocation().getX()))
                .replace("%y%", String.valueOf((int) player.getLocation().getY()))
                .replace("%z%", String.valueOf((int) player.getLocation().getZ()));
        // PlaceholderAPI support
        if (plugin.papiHook().isEnabled()) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    private void consumeItem(Player player, ItemStack item) {
        int newAmount = item.getAmount() - 1;
        if (newAmount <= 0) {
            item.setAmount(0);
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().remove(usesKey);
                meta.getPersistentDataContainer().remove(usesLoreKey);
                item.setItemMeta(meta);
            }
            item.setAmount(newAmount);
        }
    }

    // ── Uses-lore helpers ─────────────────────────────────────────────────────

    /**
     * If the item has stored lore templates, rewrites only those lore lines with
     * the current {@code remaining}/{@code maxUses} values.
     * No-op when no lore template is stored.
     */
    private void updateUsesLore(ItemMeta meta, PersistentDataContainer pdc, int remaining, int maxUses) {
        String encoded = pdc.get(usesLoreKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank())
            return;
        List<LoreTemplateLine> templates = decodeLoreTemplates(encoded);
        if (templates.isEmpty())
            return;
        applyUsesLore(meta, templates, remaining, maxUses);
    }

    /**
     * Mutates the lore on {@code meta} by replacing only the indexed template lines
     * with their resolved values ({uses} → remaining, {max_uses} → maxUses).
     */
    private void applyUsesLore(ItemMeta meta, List<LoreTemplateLine> templates, int remaining, int maxUses) {
        List<Component> lore = meta.lore();
        if (lore == null)
            return;
        List<Component> updated = new ArrayList<>(lore);
        boolean changed = false;
        for (LoreTemplateLine tpl : templates) {
            int idx = tpl.index();
            if (idx < 0 || idx >= updated.size())
                continue;
            String resolved = tpl.rawTemplate()
                    .replace(PLACEHOLDER_USES, String.valueOf(remaining))
                    .replace(PLACEHOLDER_MAX_USES, String.valueOf(maxUses));
            updated.set(idx, TextUtils.toItemComponent(resolved));
            changed = true;
        }
        if (changed) {
            meta.lore(updated);
        }
    }

    /**
     * Encodes a list of lore template lines to a compact string for PDC storage.
     * Format: {@code "index\u001Ftemplate\u001Eindex\u001Ftemplate"}.
     * Uses ASCII unit/record separators to avoid collisions with any user text.
     */
    private String encodeLoreTemplates(List<LoreTemplateLine> templates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < templates.size(); i++) {
            if (i > 0) sb.append('\u001E');
            LoreTemplateLine t = templates.get(i);
            sb.append(t.index()).append('\u001F').append(t.rawTemplate());
        }
        return sb.toString();
    }

    /**
     * Decodes the compact PDC string back to a list of lore template lines.
     */
    private List<LoreTemplateLine> decodeLoreTemplates(String encoded) {
        if (encoded == null || encoded.isBlank())
            return List.of();
        String[] entries = encoded.split("\u001E", -1);
        List<LoreTemplateLine> result = new ArrayList<>(entries.length);
        for (String entry : entries) {
            int sep = entry.indexOf('\u001F');
            if (sep <= 0)
                continue;
            try {
                int idx = Integer.parseInt(entry.substring(0, sep));
                String template = entry.substring(sep + 1);
                result.add(new LoreTemplateLine(idx, template));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(result);
    }

    /**
     * Maps a Bukkit Action + sneak state to our click type string.
     * Returns null for actions we don't handle (e.g. block breaks).
     */
    private String resolveClickType(Action action, boolean sneaking) {
        return switch (action) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> sneaking ? "SHIFT_RIGHT_CLICK" : "RIGHT_CLICK";
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> sneaking ? "SHIFT_LEFT_CLICK" : "LEFT_CLICK";
            default -> null;
        };
    }

    private float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}