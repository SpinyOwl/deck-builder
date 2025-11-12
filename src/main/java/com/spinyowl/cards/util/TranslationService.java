package com.spinyowl.cards.util;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationService {
    private final Path dir;
    private final Yaml yaml = new Yaml();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public TranslationService(Path dir) {
        this.dir = dir;
    }

    public String get(String lang, String key) {
        return translate(buildCandidatesWithoutFallback(lang), key);
    }

    public String get(String lang, String key, String fallbackLanguage) {
        return translate(buildCandidatesWithFallback(lang, fallbackLanguage), key);
    }

    private String translate(List<String> candidates, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String[] parts = key.split("\\.");
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Map<String, Object> data = loadLanguage(candidate);
            if (data.isEmpty()) {
                continue;
            }

            Object value = resolve(data, parts);
            if (value != null) {
                return value.toString();
            }
        }
        return key;
    }

    private Map<String, Object> loadLanguage(String lang) {
        return cache.computeIfAbsent(lang, this::readLanguageFile);
    }

    private Map<String, Object> readLanguageFile(String lang) {
        if (lang == null || lang.isBlank()) {
            return Collections.emptyMap();
        }

        Path file = dir.resolve(lang + ".yml");
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return deepCopy(map);
            }
        } catch (IOException ignored) {
            // fall through to empty map
        }

        return Collections.emptyMap();
    }

    private List<String> buildCandidatesWithoutFallback(String lang) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addLanguageCandidates(candidates, lang);
        return new ArrayList<>(candidates);
    }

    private List<String> buildCandidatesWithFallback(String lang, String fallbackLanguage) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addLanguageCandidates(candidates, lang);

        String fallback = fallbackLanguage;
        if (fallback == null || fallback.isBlank()) {
            fallback = "en";
        }
        addLanguageCandidates(candidates, fallback);
        return new ArrayList<>(candidates);
    }

    private void addLanguageCandidates(Set<String> candidates, String lang) {
        if (lang == null) {
            return;
        }

        String trimmed = lang.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        candidates.add(trimmed);

        String underscore = trimmed.replace('-', '_');
        candidates.add(underscore);
        candidates.add(underscore.toLowerCase(Locale.ROOT));

        int underscoreIdx = underscore.indexOf('_');
        if (underscoreIdx > 0) {
            candidates.add(underscore.substring(0, underscoreIdx));
        }

        int dashIdx = trimmed.indexOf('-');
        if (dashIdx > 0) {
            candidates.add(trimmed.substring(0, dashIdx));
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolve(Map<String, Object> map, String[] parts) {
        Object val = map;
        for (String part : parts) {
            if (!(val instanceof Map<?, ?>)) {
                return null;
            }
            val = ((Map<String, Object>) val).get(part);
        }
        return val;
    }

    private Map<String, Object> deepCopy(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String strKey)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                value = deepCopy(nested);
            }

            copy.put(strKey, value);
        }
        return copy;
    }
}
