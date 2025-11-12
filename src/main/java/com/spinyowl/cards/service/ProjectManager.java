package com.spinyowl.cards.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

@Slf4j
public class ProjectManager {

    @Getter
    private Path projectDir;

    private Map<String, Object> config;

    public void openProject(Path dir) throws IOException {
        this.projectDir = dir;
        Path configFile = dir.resolve("project.yml");
        if (!Files.exists(configFile))
            throw new IOException("project.yml not found in " + dir);

        try (InputStream in = Files.newInputStream(configFile)) {
            config = new Yaml().load(in);
        }
        log.info("Opened project: {}", getProjectName());
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
}
