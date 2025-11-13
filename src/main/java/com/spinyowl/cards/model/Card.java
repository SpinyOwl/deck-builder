package com.spinyowl.cards.model;

import lombok.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single card record loaded from the project CSV file. A card may have an
 * arbitrary set of properties, but must always contain an {@code id} entry. Reserved
 * properties such as {@code name}, {@code template}, {@code width}, and {@code height}
 * are treated as standard string values but are not mandatory (except for {@code id}).
 */
public class Card {

    private final Map<String, String> properties;

    private Card(Map<String, String> properties) {
        this.properties = properties;
    }

    public static Card of(@NonNull Map<String, String> properties) {
        Map<String, String> copy = new LinkedHashMap<>();
        properties.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .forEach(e -> copy.put(e.getKey(), e.getValue()));

        String id = normalizeId(copy.get("id"));
        if (id == null) {
            throw new IllegalArgumentException("Card id is required");
        }
        copy.put("id", id);

        return new Card(Collections.unmodifiableMap(copy));
    }

    private static String normalizeId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String getId() {
        return properties.get("id");
    }

    public String getTemplate() {
        return properties.get("template");
    }

    public Map<String, String> asMap() {
        return properties;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

}
