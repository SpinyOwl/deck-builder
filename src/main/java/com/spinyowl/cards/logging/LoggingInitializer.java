package com.spinyowl.cards.logging;

import com.spinyowl.cards.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LoggingInitializer {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private LoggingInitializer() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        Path logDir = AppPaths.getLogDirectory();
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            System.err.println("Failed to create log directory " + logDir + ": " + e.getMessage());
        }

        Path latestLogFile = AppPaths.getLatestLogFile();
        System.setProperty("deckbuilder.logDir", logDir.toAbsolutePath().toString());
        System.setProperty("deckbuilder.logFile", latestLogFile.toAbsolutePath().toString());
    }
}
