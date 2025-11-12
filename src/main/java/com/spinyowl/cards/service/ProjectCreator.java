package com.spinyowl.cards.service;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@Slf4j
public class ProjectCreator {

    public static void createDefaultProject(Path projectDir, String name) throws IOException {
        Files.createDirectories(projectDir);
        Files.createDirectories(projectDir.resolve("templates"));
        Files.createDirectories(projectDir.resolve("i18n"));

        log.info("Creating new project at {}", projectDir);

        // project.yml
        Yaml yaml = new Yaml();
        Map<String, Object> config = Map.of(
                "name", name,
                "default_language", "en",
                "default_template", "default.html",
                "cards_file", "cards.csv"
        );
        Files.writeString(projectDir.resolve("project.yml"), yaml.dump(config));

        // cards.csv
        Files.writeString(projectDir.resolve("cards.csv"),
                "id,name,description,image,template\n" +
                        "1,Sword,Sharp weapon,,\n");

        // default.html
        Files.writeString(projectDir.resolve("templates/default.html"), """
                <html>
                  <body style="width:2.5in;height:3.5in;border:1px solid black;
                               display:flex;flex-direction:column;align-items:center;
                               justify-content:center;font-family:Arial;">
                    <img src="{{ image }}" style="width:80%;height:auto;margin-bottom:8px;">
                    <div><b>{{ t('name') }}:</b> {{ name }}</div>
                    <div><b>{{ t('description') }}:</b> {{ description }}</div>
                  </body>
                </html>
                """);

        // i18n/en.yml
        Files.writeString(projectDir.resolve("i18n/en.yml"),
                "name: Name\n" +
                        "description: Description\n");

        log.info("Default project created successfully.");
    }
}
