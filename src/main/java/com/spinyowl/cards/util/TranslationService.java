package com.spinyowl.cards.util;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

public class TranslationService {
    private final Path dir;
    private final Yaml yaml = new Yaml();

    public TranslationService(Path dir) {
        this.dir = dir;
    }

    public String get(String lang, String key) {
        try (InputStream in = Files.newInputStream(dir.resolve(lang + ".yml"))) {
            Map<String, Object> data = yaml.load(in);
            Object value = resolve(data, key.split("\\."));
            return value != null ? value.toString() : key;
        } catch (IOException e) {
            return key;
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolve(Map<String, Object> map, String[] parts) {
        Object val = map;
        for (String p : parts) {
            if (!(val instanceof Map)) return null;
            val = ((Map<String, Object>) val).get(p);
        }
        return val;
    }
}
