package dev.zm.itemsbuilder.command.gui;

import dev.zm.itemsbuilder.builder.model.ItemMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;

public class ItemCreationSession {
    public final UUID playerId;
    public final String id;
    public final boolean isEdit;

    public ItemMode mode = ItemMode.SINGLE;
    public Material material = null;
    public String displayName = null;
    public List<String> lore = new ArrayList<>();
    public String rarity = "default";
    public Integer customModelData = null;
    public String idItem = null;
    public Map<String, Integer> enchants = new LinkedHashMap<>();
    public List<String> itemFlags = new ArrayList<>();
    public List<String> behaviorFlags = new ArrayList<>();
    public boolean unbreakable = false;
    public boolean glow = false;
    public boolean levelsEnabled = false;

    // Phase 5 requirements for effects
    public Map<String, EffectData> effects = new LinkedHashMap<>();
    public Map<String, Double> attributes = new LinkedHashMap<>();

    public enum AwaitingInput {
        NONE, DISPLAY_NAME, LORE, ID_ITEM, CUSTOM_MODEL_DATA, ENCHANT_LEVEL, ATTRIBUTE_LEVEL, EFFECT_DURATION,
        EFFECT_AMPLIFIER, EFFECT_SLOT
    }

    public AwaitingInput awaiting = AwaitingInput.NONE;
    public String awaitingContext = null;

    public ItemCreationSession(UUID playerId, String id, boolean isEdit) {
        this.playerId = playerId;
        this.id = id;
        this.isEdit = isEdit;
        if (!isEdit) {
            this.idItem = id;
        }
    }

    public static class EffectData {
        public String type;
        public int duration;
        public int amplifier;
        public String slot = "MAIN_HAND";

        public EffectData(String type, int duration, int amplifier) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
        }
    }
}
