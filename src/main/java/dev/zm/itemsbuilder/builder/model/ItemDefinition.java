package dev.zm.itemsbuilder.builder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ItemDefinition(
    String id,
    ItemMode mode,
    String material,
    String baseMaterial,
    String headTextureKey,
    String headBase64,
    String displayName,
    String displayType,
    List<String> lore,
    boolean loreDefined,
    Map<String, EnchantLevelRule> enchantments,
    int amount,
    boolean unbreakable,
    boolean glow,
    Integer customModelData,
    String itemIdentifier,
    List<String> itemFlags,
    List<String> behaviorFlags,
    List<String> pieces,
    List<PotionEffectRule> customEffects,
    List<AttributeSettings> attributes
) {

    public ItemDefinition {
        lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(lore));
        enchantments = enchantments == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
        itemIdentifier = normalizeIdentifier(itemIdentifier);
        itemFlags = itemFlags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(itemFlags));
        behaviorFlags = behaviorFlags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(behaviorFlags));
        pieces = pieces == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(pieces));
        customEffects = customEffects == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(customEffects));
        attributes = attributes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(attributes));
    }

    private static String normalizeIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
