package dev.zm.itemsbuilder.builder.model;

import java.util.Locale;

public enum ItemBehaviorFlag {
    NO_PLACE(1 << 0),
    NO_CRAFT(1 << 1),
    NO_DROP(1 << 2),
    NO_USE(1 << 3),
    NO_CONSUME(1 << 4),
    NO_EQUIP(1 << 5),
    NO_ANVIL(1 << 6),
    NO_SMITHING(1 << 7),
    NO_GRINDSTONE(1 << 8),
    NO_ENCHANT(1 << 9),
    NO_BREWING(1 << 10),
    NO_FURNACE(1 << 11);

    private final int mask;

    ItemBehaviorFlag(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    public static ItemBehaviorFlag from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "PLACE", "NO_PLACE", "BLOCK_PLACE", "CANNOT_PLACE", "CANT_PLACE" -> NO_PLACE;
            case "CRAFT", "CRAFTABLE", "NO_CRAFT", "BLOCK_CRAFT", "CANNOT_CRAFT", "CANT_CRAFT" -> NO_CRAFT;
            case "DROP", "NO_DROP", "BLOCK_DROP", "CANNOT_DROP", "CANT_DROP" -> NO_DROP;
            case "USE", "NO_USE", "INTERACT", "RIGHT_CLICK", "BLOCK_USE" -> NO_USE;
            case "CONSUME", "NO_CONSUME", "EAT", "DRINK", "BLOCK_CONSUME" -> NO_CONSUME;
            case "EQUIP", "NO_EQUIP", "ARMOR", "WEAR", "BLOCK_EQUIP" -> NO_EQUIP;
            case "ANVIL", "NO_ANVIL", "REPAIR", "BLOCK_ANVIL" -> NO_ANVIL;
            case "SMITHING", "NO_SMITHING", "BLOCK_SMITHING" -> NO_SMITHING;
            case "GRINDSTONE", "NO_GRINDSTONE", "DISENCHANT", "BLOCK_GRINDSTONE" -> NO_GRINDSTONE;
            case "ENCHANT", "NO_ENCHANT", "ENCHANTING", "BLOCK_ENCHANT" -> NO_ENCHANT;
            case "BREWING", "NO_BREWING", "POTION", "BLOCK_BREWING" -> NO_BREWING;
            case "FURNACE", "NO_FURNACE", "SMELT", "BLOCK_FURNACE" -> NO_FURNACE;
            default -> null;
        };
    }
}
