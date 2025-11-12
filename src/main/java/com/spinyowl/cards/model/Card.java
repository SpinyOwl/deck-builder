package com.spinyowl.cards.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class Card {
    private String id;
    private String name;
    private String description;
    private String image;
    private String template;

    public Map<String, Object> asMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("image", image);
        m.put("template", template);
        return m;
    }
}
