package com.spinyowl.cards.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single card record loaded from the project CSV file. A card may have an
 * arbitrary set of properties, but must always contain an {@code id} entry. Reserved
 * properties such as {@code name}, {@code template}, {@code width}, and {@code height}
 * are treated as standard string values but are not mandatory (except for {@code id}).
 */
public class Card {

    private final Map<String, Object> properties;

    public Card(Map<String, ?> properties) {
        Objects.requireNonNull(properties, "properties");

        Map<String, Object> copy = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String strKey = key.toString();
            Object normalizedValue = value;
            if (value instanceof String str) {
                normalizedValue = str;
            }
            copy.put(strKey, normalizedValue);
        });

        String id = normalizeId(copy.get("id"));
        if (id == null) {
            throw new IllegalArgumentException("Card id is required");
        }
        copy.put("id", id);

        this.properties = Collections.unmodifiableMap(copy);
    }

    private String normalizeId(Object rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String getId() {
        return (String) properties.get("id");
    }

    public String getTemplate() {
        return asString("template");
    }

    public Map<String, Object> asMap() {
        return properties;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public String getPropertyAsString(String key) {
        return asString(key);
    }

    private String asString(String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }
}
