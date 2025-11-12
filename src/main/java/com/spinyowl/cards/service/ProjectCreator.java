package com.spinyowl.cards.service;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

@Slf4j
public class ProjectCreator {

    public static void createDefaultProject(Path projectDir, String name) throws IOException {
        Files.createDirectories(projectDir);
        Files.createDirectories(projectDir.resolve("templates"));
        Files.createDirectories(projectDir.resolve("i18n"));
        Files.createDirectories(projectDir.resolve("i18n/cards"));

        log.info("Creating new project at {}", projectDir);

        // project.yml
        Yaml yaml = new Yaml();
        Map<String, Object> config = loadProjectConfig();
        config.put("name", name);
        Files.writeString(projectDir.resolve("project.yml"), yaml.dump(config));

        // cards.csv
        copyResource("default_project/cards.csv", projectDir.resolve("cards.csv"));

        // default.html
        copyResource("default_project/templates/default.html", projectDir.resolve("templates/default.html"));

        // i18n/en.yml
        copyResource("default_project/i18n/en.yml", projectDir.resolve("i18n/en.yml"));

        // i18n/cards/en.yml
        copyResource("default_project/i18n/cards/en.yml", projectDir.resolve("i18n/cards/en.yml"));

        log.info("Default project created successfully.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadProjectConfig() throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = ProjectCreator.class.getClassLoader()
                .getResourceAsStream("default_project/project.yml")) {
            if (in == null) {
                throw new IOException("Default project configuration template not found");
            }
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IOException("Invalid default project configuration template");
            }
            return (Map<String, Object>) map;
        }
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = ProjectCreator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
