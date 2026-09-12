package dev.zm.itemsbuilder.command.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ProgressionSession {

    public enum ProgressionType {
        ENCHANTMENT,
        ATTRIBUTE
    }

    public enum ProgressionMode {
        PER_LEVEL,
        INTERVAL
    }

    public enum GuiPhase {
        ITEM_SELECT,
        TYPE_SELECT,
        KEY_SELECT,
        MODE_SELECT,
        EDITOR,
        AWAITING_INPUT
    }

    public enum InputField {
        BASE("base", true),
        PER_LEVEL("per-level", true),
        EVERY("every", true),
        BONUS("bonus", true),
        MIN("min", false),
        MAX("max", false),
        EXPRESSION("expression", false);

        private final String configKey;
        private final boolean integerOnly;

        InputField(String configKey, boolean integerOnly) {
            this.configKey = configKey;
            this.integerOnly = integerOnly;
        }

        public String configKey() {
            return configKey;
        }

        public boolean integerOnly() {
            return integerOnly;
        }
    }

    public final UUID playerId;

    // Mutable so they can be set after item selection in ITEM_SELECT phase
    public String itemKey;
    public String itemDisplayName;

    public ProgressionType progressionType;
    public String selectedKey;
    public ProgressionMode mode;
    public GuiPhase phase;

    // Pagination state
    public int itemPage = 0;
    public int keyPage = 0;

    public final Map<String, Object> enchantValues = new LinkedHashMap<>();
    public final Map<String, Object> attributeValues = new LinkedHashMap<>();

    // NEW: needed to write a complete attribute entry
    // (attribute/operation/slot/amount)
    // matching what /zmitems attribute add produces.
    public String attributeOperation = "ADD_NUMBER";
    public String attributeSlot = "MAIN_HAND";

    public InputField awaitingField;

    private ProgressionInventoryHolder holder;

    public ProgressionSession(UUID playerId) {
        this.playerId = playerId;
        this.phase = GuiPhase.TYPE_SELECT;
    }

    public ProgressionSession(UUID playerId, String itemKey, String itemDisplayName) {
        this.playerId = playerId;
        this.itemKey = itemKey;
        this.itemDisplayName = itemDisplayName;
        this.phase = GuiPhase.TYPE_SELECT;
    }

    public Map<String, Object> currentValues() {
        return progressionType == ProgressionType.ENCHANTMENT ? enchantValues : attributeValues;
    }

    public void setFieldValue(InputField field, Object value) {
        currentValues().put(field.configKey(), value);
    }

    public Object getFieldValue(InputField field) {
        return currentValues().get(field.configKey());
    }

    public String getConfigKey() {
        return progressionType == ProgressionType.ENCHANTMENT
                ? "enchants." + selectedKey
                : "attributes." + (selectedKey != null ? selectedKey.toLowerCase(java.util.Locale.ROOT) : "")
                        + ".amount";
    }

    public ProgressionInventoryHolder getHolder() {
        return holder;
    }

    public void setHolder(ProgressionInventoryHolder holder) {
        this.holder = holder;
    }
}