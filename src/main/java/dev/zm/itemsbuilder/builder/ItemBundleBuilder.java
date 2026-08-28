package dev.zm.itemsbuilder.builder;

import dev.zm.itemsbuilder.zMItemsBuilder;
import dev.zm.itemsbuilder.builder.model.ItemBundleDefinition;
import dev.zm.itemsbuilder.builder.model.ItemDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public final class ItemBundleBuilder {

    private final zMItemsBuilder plugin;
    private final ItemFactory itemFactory;

    public ItemBundleBuilder(zMItemsBuilder plugin, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    /**
     * Builds all items in the kit for the given player.
     *
     * @param player the player who triggered the build (used for PlaceholderAPI). May be null.
     */
    public List<ItemStack> build(
            ItemBundleDefinition kitDefinition,
            String prefixRaw,
            String prefixMiniMessage,
            String primaryHex,
            String secondaryHex,
            List<String> prefixGradientColors,
            OfflinePlayer player) {
        String rarity = plugin.getConfig().getString("rarity." + kitDefinition.rarity(), kitDefinition.rarity());
        ItemBuildContext context = new ItemBuildContext(
                kitDefinition.id(),
                rarity,
                kitDefinition.level(),
                kitDefinition.headTextureKey(),
                prefixRaw,
                prefixMiniMessage,
                primaryHex,
                secondaryHex,
                prefixGradientColors,
                player);

        List<ItemStack> items = new ArrayList<>();
        for (String itemId : kitDefinition.itemIds()) {
            Optional<ItemDefinition> itemDefinition = plugin.itemRegistry().getItem(itemId);
            if (itemDefinition.isEmpty()) {
                plugin.getLogger().warning("Item definition not found for kit '" + kitDefinition.id() + "': " + itemId);
                continue;
            }
            items.addAll(itemFactory.create(itemDefinition.get(), context));
        }
        return items;
    }

    /**
     * Convenience overload without a player – PAPI placeholders are not applied.
     */
    public List<ItemStack> build(
            ItemBundleDefinition kitDefinition,
            String prefixRaw,
            String prefixMiniMessage,
            String primaryHex,
            String secondaryHex,
            List<String> prefixGradientColors) {
        return build(kitDefinition, prefixRaw, prefixMiniMessage, primaryHex, secondaryHex, prefixGradientColors, null);
    }
}
