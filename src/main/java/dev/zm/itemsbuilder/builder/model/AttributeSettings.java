package dev.zm.itemsbuilder.builder.model;

public record AttributeSettings(
    String attribute,
    double amount,
    String operation,
    String slot
) {}
