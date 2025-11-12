package com.spinyowl.cards.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ProjectManager {

    @Getter
    private Path projectDir;

    private static final String DEFAULT_CARD_WIDTH = "2.5in";
    private static final String DEFAULT_CARD_HEIGHT = "3.5in";
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)(px|in|cm|mm)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)$");

    private Map<String, Object> config = Collections.emptyMap();

    public void openProject(Path dir) throws IOException {
        this.projectDir = dir;
        loadConfiguration();
        log.info("Opened project: {}", getProjectName());
    }

    public void reloadProject() throws IOException {
        if (projectDir == null) {
            throw new IOException("Project directory is not set");
        }
        loadConfiguration();
        log.info("Reloaded project configuration for {}", projectDir);
    }

    public String getProjectName() {
        return (String) config.getOrDefault("name", "Unnamed Project");
    }

    public String getDefaultTemplate() {
        return (String) config.getOrDefault("default_template", "templates/default.html");
    }

    public String getDefaultLanguage() {
        return (String) config.getOrDefault("default_language", "en");
    }

    public Path resolve(String rel) {
        return projectDir.resolve(rel);
    }

    public Map<String, Object> getProjectProperties() {
        Map<String, Object> view = new LinkedHashMap<>();
        if (config != null) {
            view.putAll(config);
        }

        Map<String, Object> card = new LinkedHashMap<>();
        Object cardConfig = config == null ? null : config.get("card");
        if (cardConfig instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key instanceof String strKey) {
                    card.put(strKey, value);
                }
            });
        }

        card.put("width", normalizeDimension(card.get("width"), DEFAULT_CARD_WIDTH));
        card.put("height", normalizeDimension(card.get("height"), DEFAULT_CARD_HEIGHT));

        view.put("card", Collections.unmodifiableMap(card));

        return Collections.unmodifiableMap(view);
    }

    private String normalizeDimension(Object value, String fallback) {
        if (value instanceof Number number) {
            return stripTrailingZeros(number) + "px";
        }

        if (value instanceof CharSequence seq) {
            String normalized = seq.toString().trim();
            if (normalized.isEmpty()) {
                return fallback;
            }

            normalized = normalized.replaceAll("\\s+", "");
            Matcher matcher = DIMENSION_PATTERN.matcher(normalized);
            if (matcher.matches()) {
                return matcher.group(1) + matcher.group(2).toLowerCase();
            }

            Matcher numericOnly = NUMERIC_PATTERN.matcher(normalized);
            if (numericOnly.matches()) {
                return numericOnly.group(1) + "px";
            }

            log.warn("Unrecognized dimension value '{}' - using fallback {}", value, fallback);
            return fallback;
        }

        if (value != null) {
            log.warn("Unsupported dimension value type {} - using fallback {}", value.getClass(), fallback);
        }

        return fallback;
    }

    private String stripTrailingZeros(Number number) {
        BigDecimal decimal = new BigDecimal(number.toString());
        decimal = decimal.stripTrailingZeros();
        return decimal.toPlainString();
    }

    private void loadConfiguration() throws IOException {
        Path dir = projectDir;
        if (dir == null) {
            throw new IOException("Project directory is not set");
        }

        Path configFile = dir.resolve("project.yml");
        if (!Files.exists(configFile))
            throw new IOException("project.yml not found in " + dir);

        try (InputStream in = Files.newInputStream(configFile)) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> cleaned = new LinkedHashMap<>();
                map.forEach((key, value) -> {
                    if (key instanceof String strKey) {
                        cleaned.put(strKey, value);
                    }
                });
                config = cleaned;
            } else {
                config = new LinkedHashMap<>();
            }
        }
    }
}
