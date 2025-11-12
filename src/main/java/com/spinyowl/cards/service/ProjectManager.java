package com.spinyowl.cards.service;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

public class ProjectManager {

    private Path projectDir;
    private Map<String, Object> config;

    public void openProject(Path dir) throws IOException {
        this.projectDir = dir;
        try (InputStream in = Files.newInputStream(dir.resolve("project.yml"))) {
            Yaml yaml = new Yaml();
            config = yaml.load(in);
        }
    }

    public Path getProjectDir() {
        return projectDir;
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
