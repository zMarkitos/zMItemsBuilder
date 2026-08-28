package dev.zm.itemsbuilder.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.OfflinePlayer;

public record ItemBuildContext(
    String kitId,
    String rarityText,
    int level,
    String headTextureKey,
    String prefixRaw,
    String prefixMiniMessage,
    String primaryHex,
    String secondaryHex,
    List<String> prefixGradientColors,
    /** The player who triggered the build – used to resolve PlaceholderAPI tokens. May be null. */
    OfflinePlayer player
) {
    public ItemBuildContext {
        prefixGradientColors = prefixGradientColors == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(prefixGradientColors));
    }
}
