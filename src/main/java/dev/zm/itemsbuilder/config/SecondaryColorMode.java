package dev.zm.itemsbuilder.config;

import java.util.Locale;

public enum SecondaryColorMode {
    LIGHTER,
    DARKER,
    COMPLEMENTARY;

    public static SecondaryColorMode from(String raw) {
        if (raw == null) {
            return LIGHTER;
        }
        try {
            return SecondaryColorMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LIGHTER;
        }
    }
}
