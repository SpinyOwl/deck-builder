package com.spinyowl.cards.ui;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.ConfigService;
import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;

/**
 * Applies persisted window configuration to the preview stage and keeps it in sync with the
 * configuration service while preview is displayed.
 */
public final class WindowStateHandler {

    private static final String PROPERTY_KEY = WindowStateHandler.class.getName();

    private final ChangeListener<Number> widthListener;
    private final ChangeListener<Number> heightListener;
    private final ChangeListener<Number> xListener;
    private final ChangeListener<Number> yListener;
    private final ChangeListener<Boolean> maximizedListener;

    private WindowStateHandler(Stage stage, ConfigService configService) {
        widthListener = (obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowSize(newVal.doubleValue(), stage.getHeight());
            }
        };
        heightListener = (obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowSize(stage.getWidth(), newVal.doubleValue());
            }
        };
        xListener = (obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowPosition(newVal.doubleValue(), stage.getY());
            }
        };
        yListener = (obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowPosition(stage.getX(), newVal.doubleValue());
            }
        };
        maximizedListener = (obs, oldVal, newVal) -> configService.setWindowMaximized(newVal);

        stage.widthProperty().addListener(widthListener);
        stage.heightProperty().addListener(heightListener);
        stage.xProperty().addListener(xListener);
        stage.yProperty().addListener(yListener);
        stage.maximizedProperty().addListener(maximizedListener);
    }

    private void dispose(Stage stage) {
        stage.widthProperty().removeListener(widthListener);
        stage.heightProperty().removeListener(heightListener);
        stage.xProperty().removeListener(xListener);
        stage.yProperty().removeListener(yListener);
        stage.maximizedProperty().removeListener(maximizedListener);
    }

    /**
     * Enables window state synchronization for the preview stage.
     */
    public static void enable(Stage stage, ConfigService configService) {
        disable(stage);

        applySavedBounds(stage, configService.getConfig());

        WindowStateHandler handler = new WindowStateHandler(stage, configService);
        stage.getProperties().put(PROPERTY_KEY, handler);

        stage.setMaximized(configService.getConfig().isWindowMaximized());
    }

    private static void applySavedBounds(Stage stage, AppConfig config) {
        stage.setWidth(config.getWindowWidth());
        stage.setHeight(config.getWindowHeight());
        if (config.getWindowX() != null && config.getWindowY() != null) {
            stage.setX(config.getWindowX());
            stage.setY(config.getWindowY());
        }
    }

    /**
     * Disables window state synchronization for the stage.
     */
    public static void disable(Stage stage) {
        Object existing = stage.getProperties().remove(PROPERTY_KEY);
        if (existing instanceof WindowStateHandler handler) {
            handler.dispose(stage);
        }
    }
}
