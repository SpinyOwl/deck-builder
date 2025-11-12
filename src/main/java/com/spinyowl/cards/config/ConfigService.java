package com.spinyowl.cards.config;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public class ConfigService {
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), "DeckBuilder");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private static final int MAX_RECENT_PROJECTS = 10;

    private static final ConfigService INSTANCE = new ConfigService();

    private final AppConfig config = new AppConfig();

    private ConfigService() {
        load();
    }

    public static ConfigService getInstance() {
        return INSTANCE;
    }

    public AppConfig getConfig() {
        return config;
    }

    public void markProjectOpened(Path projectDir) {
        if (projectDir == null) {
            return;
        }
        config.addRecentProject(projectDir.toAbsolutePath().toString(), MAX_RECENT_PROJECTS);
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

        Properties props = new Properties();
        if (config.getLastProjectsParent() != null) {
            props.setProperty("lastProjectsParent", config.getLastProjectsParent());
        }
        props.setProperty("windowWidth", Double.toString(config.getWindowWidth()));
        props.setProperty("windowHeight", Double.toString(config.getWindowHeight()));
        if (config.getWindowX() != null) {
            props.setProperty("windowX", Double.toString(config.getWindowX()));
        }
        if (config.getWindowY() != null) {
            props.setProperty("windowY", Double.toString(config.getWindowY()));
        }
        props.setProperty("windowMaximized", Boolean.toString(config.isWindowMaximized()));
        List<String> recent = config.getRecentProjects();
        for (int i = 0; i < recent.size(); i++) {
            props.setProperty("recentProject." + i, recent.get(i));
        }

        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            props.store(out, "DeckBuilder configuration");
        } catch (IOException e) {
            log.warn("Failed to write configuration to {}", CONFIG_FILE, e);
        }
    }

    private void load() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            log.warn("Failed to load configuration from {}", CONFIG_FILE, e);
            return;
        }

        config.setLastProjectsParent(props.getProperty("lastProjectsParent"));
        config.setWindowWidth(parseDouble(props.getProperty("windowWidth"), config.getWindowWidth()));
        config.setWindowHeight(parseDouble(props.getProperty("windowHeight"), config.getWindowHeight()));
        config.setWindowX(parseNullableDouble(props.getProperty("windowX")));
        config.setWindowY(parseNullableDouble(props.getProperty("windowY")));
        config.setWindowMaximized(parseBoolean(props.getProperty("windowMaximized"), config.isWindowMaximized()));

        List<String> recentProjects = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_PROJECTS; i++) {
            String value = props.getProperty("recentProject." + i);
            if (value == null) {
                break;
            }
            recentProjects.add(value);
        }
        config.setRecentProjects(recentProjects);
    }

    private double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Double parseNullableDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }
}
