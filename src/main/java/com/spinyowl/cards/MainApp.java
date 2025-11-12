package com.spinyowl.cards;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.ConfigService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final ConfigService configService = ConfigService.getInstance();

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));

        AppConfig config = configService.getConfig();
        Scene scene = new Scene(loader.load(), config.getWindowWidth(), config.getWindowHeight());

        stage.setTitle("SpinyOwl.DeckBuilder");
        stage.setScene(scene);
        if (config.getWindowX() != null && config.getWindowY() != null) {
            stage.setX(config.getWindowX());
            stage.setY(config.getWindowY());
        }
        stage.setMaximized(config.isWindowMaximized());

        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowSize(newVal.doubleValue(), stage.getHeight());
            }
        });
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowSize(stage.getWidth(), newVal.doubleValue());
            }
        });
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowPosition(newVal.doubleValue(), stage.getY());
            }
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (!stage.isMaximized()) {
                configService.setWindowPosition(stage.getX(), newVal.doubleValue());
            }
        });
        stage.maximizedProperty().addListener((obs, oldVal, newVal) ->
                configService.setWindowMaximized(newVal));

        stage.setOnCloseRequest(event -> configService.save());

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
