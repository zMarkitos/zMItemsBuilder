package dev.zm.itemsbuilder.builder.model;

public record AttributeSettings(
    String id,
    String attribute,
    NumberRule amount,
    String operation,
    String slot
) {}
