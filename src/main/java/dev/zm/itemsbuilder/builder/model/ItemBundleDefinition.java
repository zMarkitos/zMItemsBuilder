package dev.zm.itemsbuilder.builder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ItemBundleDefinition(
    String id,
    String rarity,
    int level,
    String headTextureKey,
    List<String> itemIds
) {

    public ItemBundleDefinition {
        itemIds = itemIds == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(itemIds));
    }
}
