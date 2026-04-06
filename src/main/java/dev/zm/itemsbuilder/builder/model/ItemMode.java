package dev.zm.itemsbuilder.builder.model;

import java.util.Locale;

public enum ItemMode {
    SINGLE,
    ARMOR_SET,
    TOOL_SET;

    public static ItemMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SINGLE;
        }

        String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "armor", "armor_set", "armorset" -> ARMOR_SET;
            case "tool", "tools", "tool_set", "toolset" -> TOOL_SET;
            default -> SINGLE;
        };
    }

    public String configKey() {
        return switch (this) {
            case SINGLE -> "single";
            case ARMOR_SET -> "armor";
            case TOOL_SET -> "tools";
        };
    }
}
