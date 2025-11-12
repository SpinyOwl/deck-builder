package com.spinyowl.cards.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ConfigService {
    private static final Path CONFIG_DIR = AppPaths.getConfigDirectory();
    private static final Path CONFIG_FILE = AppPaths.getConfigFile();
    private static final int MAX_RECENT_PROJECTS = 10;

    private static final ConfigService INSTANCE = new ConfigService();
    @Getter
    private final AppConfig config = new AppConfig();
    private final Yaml yaml;

    private ConfigService() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        yaml = new Yaml(options);
        load();
    }

    public static ConfigService getInstance() {
        return INSTANCE;
    }

    public void markProjectOpened(Path projectDir) {
        if (projectDir == null) {
            return;
        }
        config.addRecentProject(projectDir.toAbsolutePath().toString(), MAX_RECENT_PROJECTS);
        save();
    }

    public void removeRecentProject(String projectPath) {
        config.removeRecentProject(projectPath);
        save();
    }

    public void setLastProjectsParent(Path parentDir) {
        if (parentDir == null) {
            config.setLastProjectsParent(null);
        } else {
            config.setLastProjectsParent(parentDir.toAbsolutePath().toString());
        }
        save();
    }

    public File getLastProjectsParentDirectory() {
        String parent = config.getLastProjectsParent();
        if (parent == null || parent.isBlank()) {
            return null;
        }
        File dir = new File(parent);
        return dir.isDirectory() ? dir : null;
    }

    public File getMostRecentProjectDirectory() {
        if (config.getRecentProjects().isEmpty()) {
            return null;
        }
        String path = config.getRecentProjects().get(0);
        if (path == null || path.isBlank()) {
            return null;
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }

    public void setWindowSize(double width, double height) {
        if (!Double.isNaN(width) && !Double.isInfinite(width) && width > 0) {
            config.setWindowWidth(width);
        }
        if (!Double.isNaN(height) && !Double.isInfinite(height) && height > 0) {
            config.setWindowHeight(height);
        }
    }

    public void setWindowPosition(double x, double y) {
        if (!Double.isNaN(x) && !Double.isInfinite(x)) {
            config.setWindowX(x);
        }
        if (!Double.isNaN(y) && !Double.isInfinite(y)) {
            config.setWindowY(y);
        }
    }

    public void setWindowMaximized(boolean maximized) {
        config.setWindowMaximized(maximized);
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            log.warn("Failed to create config directory {}", CONFIG_DIR, e);
            return;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        if (config.getLastProjectsParent() != null) {
            root.put("lastProjectsParent", config.getLastProjectsParent());
        }

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("width", config.getWindowWidth());
        window.put("height", config.getWindowHeight());
        if (config.getWindowX() != null) {
            window.put("x", config.getWindowX());
        }
        if (config.getWindowY() != null) {
            window.put("y", config.getWindowY());
        }
        window.put("maximized", config.isWindowMaximized());
        root.put("window", window);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("consoleExpanded", config.isConsoleExpanded());
        preview.put("consoleDividerPosition", config.getConsoleDividerPosition());
        preview.put("projectTreeVisible", config.isProjectTreeVisible());
        preview.put("projectTreeDividerPosition", config.getProjectTreeDividerPosition());
        root.put("preview", preview);

        root.put("recentProjects", new ArrayList<>(config.getRecentProjects()));

        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE)) {
            yaml.dump(root, writer);
        } catch (IOException e) {
            log.warn("Failed to write configuration to {}", CONFIG_FILE, e);
        }
    }

    private void load() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> data)) {
                return;
            }

            config.setLastProjectsParent(asString(data.get("lastProjectsParent")));

            Object windowObj = data.get("window");
            if (windowObj instanceof Map<?, ?> window) {
                config.setWindowWidth(asDouble(window.get("width"), config.getWindowWidth()));
                config.setWindowHeight(asDouble(window.get("height"), config.getWindowHeight()));
                config.setWindowX(asNullableDouble(window.get("x")));
                config.setWindowY(asNullableDouble(window.get("y")));
                config.setWindowMaximized(asBoolean(window.get("maximized"), config.isWindowMaximized()));
            }

            Object previewObj = data.get("preview");
            if (previewObj instanceof Map<?, ?> preview) {
                config.setConsoleExpanded(asBoolean(preview.get("consoleExpanded"), config.isConsoleExpanded()));
                config.setConsoleDividerPosition(asDouble(preview.get("consoleDividerPosition"), config.getConsoleDividerPosition()));
                config.setProjectTreeVisible(asBoolean(preview.get("projectTreeVisible"), config.isProjectTreeVisible()));
                config.setProjectTreeDividerPosition(asDouble(preview.get("projectTreeDividerPosition"), config.getProjectTreeDividerPosition()));
            }

            Object recentObj = data.get("recentProjects");
            if (recentObj instanceof List<?> list) {
                List<String> recentProjects = new ArrayList<>();
                for (Object item : list) {
                    String value = asString(item);
                    if (value != null && !value.isBlank()) {
                        recentProjects.add(value);
                    }
                }
                if (recentProjects.size() > MAX_RECENT_PROJECTS) {
                    recentProjects = new ArrayList<>(recentProjects.subList(0, MAX_RECENT_PROJECTS));
                }
                config.setRecentProjects(recentProjects);
            }
        } catch (IOException e) {
            log.warn("Failed to load configuration from {}", CONFIG_FILE, e);
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return Objects.toString(value, null);
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            Double string1 = getADouble(string);
            if (string1 != null) return string1;
        }
        return fallback;
    }

    private Double asNullableDouble(Object value) {
        return switch (value) {
            case Number number -> number.doubleValue();
            case String string -> getADouble(string);
            case null, default -> null;
        };
    }

    private static Double getADouble(String string) {
        try {
            return Double.parseDouble(string);
        } catch (NumberFormatException ignored) {
            log.warn("Failed to convert {} to Double", string);
        }
        return null;
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }
}
