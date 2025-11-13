package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.AppPaths;
import com.spinyowl.cards.config.ConfigService;
import com.spinyowl.cards.util.FileUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConsoleLogController {

    private static final int MAX_LOG_CHARACTERS = 20_000;

    private final ConfigService configService;
    private final AppConfig appConfig;
    private final TitledPane consolePane;
    private final TextArea consoleTextArea;
    private final SplitPane mainVerticalSplit;

    private ChangeListener<Number> verticalDividerListener;
    private SplitPane.Divider verticalDivider;

    private ScheduledExecutorService logUpdateExecutor;
    private Path latestLogFile;
    private volatile long lastLogModified = -1L;
    private volatile long lastLogSize = -1L;
    private volatile String lastDisplayedLog;

    public ConsoleLogController(ConfigService configService,
                                AppConfig appConfig,
                                TitledPane consolePane,
                                TextArea consoleTextArea,
                                SplitPane mainVerticalSplit) {
        this.configService = configService;
        this.appConfig = appConfig;
        this.consolePane = consolePane;
        this.consoleTextArea = consoleTextArea;
        this.mainVerticalSplit = mainVerticalSplit;
    }

    public void initialize() {
        if (consolePane != null) {
            consolePane.setExpanded(appConfig.isConsoleExpanded());
            consolePane.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) ->
                updateConsoleExpandedState(isNowExpanded, true)
            );
        }
    }

    public void applyInitialState() {
        updateConsoleExpandedState(consolePane != null && consolePane.isExpanded(), false);
    }

    public void onToggleConsole(boolean expanded) {
        updateConsoleExpandedState(expanded, true);
    }

    public void startLogUpdates() {
        stopLogUpdates();
        latestLogFile = AppPaths.getLatestLogFile();
        lastLogModified = -1L;
        lastLogSize = -1L;
        lastDisplayedLog = null;
        logUpdateExecutor = Executors.newSingleThreadScheduledExecutor(new LogWatcherThreadFactory());
        logUpdateExecutor.scheduleWithFixedDelay(this::refreshConsoleFromLog, 0, 1, TimeUnit.SECONDS);
    }

    public void stopLogUpdates() {
        if (logUpdateExecutor != null) {
            logUpdateExecutor.shutdownNow();
            logUpdateExecutor = null;
        }
        latestLogFile = null;
        lastDisplayedLog = null;
        lastLogModified = -1L;
        lastLogSize = -1L;
    }

    public void expandConsole() {
        if (consolePane != null) {
            Platform.runLater(() -> consolePane.setExpanded(true));
        }
    }

    private void updateConsoleExpandedState(boolean expanded, boolean persist) {
        if (consolePane == null || mainVerticalSplit == null) {
            appConfig.setConsoleExpanded(expanded);
            if (persist) {
                configService.save();
            }
            return;
        }

        SplitPane.setResizableWithParent(consolePane, false);

        if (!expanded && !mainVerticalSplit.getDividers().isEmpty()) {
            double position = clamp(mainVerticalSplit.getDividers().get(0).getPosition());
            appConfig.setConsoleDividerPosition(position);
        }

        appConfig.setConsoleExpanded(expanded);

        if (expanded) {
            Platform.runLater(() -> {
                mainVerticalSplit.setDividerPositions(clamp(appConfig.getConsoleDividerPosition()));
                updateVerticalDividerDraggable(true);
                ensureVerticalDividerListener();
            });
        } else {
            Platform.runLater(() -> {
                mainVerticalSplit.setDividerPositions(1.0);
                updateVerticalDividerDraggable(false);
            });
            removeVerticalDividerListener();
        }

        if (persist) {
            configService.save();
        }
    }

    private double clamp(double pos) {
        if (pos < 0.0) return 0.0;
        if (pos > 1.0) return 1.0;
        return pos;
    }

    private void updateVerticalDividerDraggable(boolean draggable) {
        if (mainVerticalSplit == null) return;
        Platform.runLater(() -> {
            for (Node n : mainVerticalSplit.lookupAll(".split-pane-divider")) {
                n.setMouseTransparent(!draggable);
            }
        });
    }

    private void ensureVerticalDividerListener() {
        if (mainVerticalSplit == null || mainVerticalSplit.getDividers().isEmpty()) {
            removeVerticalDividerListener();
            return;
        }

        SplitPane.Divider divider = mainVerticalSplit.getDividers().get(0);
        if (divider == verticalDivider && verticalDividerListener != null) {
            return;
        }

        removeVerticalDividerListener();

        verticalDivider = divider;
        verticalDividerListener = (obs, oldVal, newVal) -> {
            if (consolePane != null && consolePane.isExpanded()) {
                double position = clamp(newVal.doubleValue());
                appConfig.setConsoleDividerPosition(position);
            }
        };
        divider.positionProperty().addListener(verticalDividerListener);
    }

    private void removeVerticalDividerListener() {
        if (verticalDivider != null && verticalDividerListener != null) {
            verticalDivider.positionProperty().removeListener(verticalDividerListener);
        }
        verticalDivider = null;
        verticalDividerListener = null;
    }

    private void refreshConsoleFromLog() {
        if (consoleTextArea == null) {
            return;
        }

        Path logFile = latestLogFile;
        if (!FileUtils.isReadableFile(logFile)) {
            if (lastDisplayedLog != null && !lastDisplayedLog.isEmpty()) {
                lastDisplayedLog = "";
                Platform.runLater(() -> {
                    consoleTextArea.clear();
                    consoleTextArea.positionCaret(0);
                });
            }
            lastLogModified = -1L;
            lastLogSize = -1L;
            return;
        }

        try {
            long modified = Files.getLastModifiedTime(logFile).toMillis();
            long size = Files.size(logFile);
            if (modified == lastLogModified && size == lastLogSize) {
                return;
            }
            lastLogModified = modified;
            lastLogSize = size;

            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            if (content.length() > MAX_LOG_CHARACTERS) {
                content = content.substring(content.length() - MAX_LOG_CHARACTERS);
            }
            if (content.equals(lastDisplayedLog)) {
                return;
            }
            lastDisplayedLog = content;
            String finalContent = content;
            Platform.runLater(() -> {
                consoleTextArea.setText(finalContent);
                consoleTextArea.positionCaret(finalContent.length());
            });
        } catch (Exception e) {
            log.warn("Failed to read log file {}", logFile, e);
        }
    }

    private static class LogWatcherThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "log-viewer");
            thread.setDaemon(true);
            return thread;
        }
    }
}

