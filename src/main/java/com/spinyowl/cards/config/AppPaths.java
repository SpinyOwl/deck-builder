package com.spinyowl.cards.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class AppPaths {

    public static final String APPLICATION_ID = "SpinyOwl.DeckBuilder";

    private AppPaths() {
    }

    public static Path getConfigDirectory() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, APPLICATION_ID);
            }
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                return Paths.get(userHome, "AppData", "Roaming", APPLICATION_ID);
            }
        } else if (osName.contains("mac")) {
            String userHome = System.getProperty("user.home", "");
            return Paths.get(userHome, "Library", "Application Support", APPLICATION_ID);
        }
        String userHome = System.getProperty("user.home", "");
        return Paths.get(userHome, ".config", APPLICATION_ID);
    }

    public static Path getConfigFile() {
        return getConfigDirectory().resolve("config.yml");
    }

    public static Path getLogDirectory() {
        return getConfigDirectory().resolve("logs");
    }

    public static Path getLatestLogFile() {
        return getLogDirectory().resolve("latest.log");
    }
}
